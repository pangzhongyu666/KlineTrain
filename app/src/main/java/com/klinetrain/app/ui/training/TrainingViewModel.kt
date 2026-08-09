package com.klinetrain.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.KlineUtils
import com.klinetrain.app.data.db.FormulaEntity
import com.klinetrain.app.data.db.StrategyEntity
import com.klinetrain.app.data.db.TradeEntity
import com.klinetrain.app.data.db.TrainingRecordEntity
import com.klinetrain.app.data.model.Direction
import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.MainOverlay
import com.klinetrain.app.data.model.MarketType
import com.klinetrain.app.data.model.SessionResult
import com.klinetrain.app.data.model.Stock
import com.klinetrain.app.data.model.SubIndicator
import com.klinetrain.app.data.model.TimeFrame
import com.klinetrain.app.data.model.TradeMarker
import com.klinetrain.app.data.model.TrainingConfig
import com.klinetrain.app.data.model.TrainingMode
import com.klinetrain.app.engine.TradingEngine
import com.klinetrain.app.formula.FormulaEngine
import com.klinetrain.app.formula.FormulaException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/** 自动播放速度 */
enum class PlaySpeed(val label: String, val delayMs: Long) {
    X1("1x", 800L), X2("2x", 400L), X5("5x", 160L)
}

/** 训练页 UI 状态 */
data class TrainingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    // 标的与回放窗口
    val stockName: String = "",
    val stockCode: String = "",
    val isBlind: Boolean = false,
    val windowKlines: List<Kline> = emptyList(),  // warmup + session 完整窗口
    val warmupBars: Int = 0,
    val sessionBars: Int = 0,
    val currentIndex: Int = 0,                    // 当前回放到窗口内的下标(含)
    val finished: Boolean = false,
    // 图表展示
    val timeframe: TimeFrame = TimeFrame.DAY,
    val displayKlines: List<Kline> = emptyList(),
    val displayMarkers: List<TradeMarker> = emptyList(),
    val prevCloseForPanel: Double? = null,        // 仅分时模式传给图表
    // 图表配置
    val mainOverlay: MainOverlay = MainOverlay.MA,
    val subIndicators: List<SubIndicator> = listOf(SubIndicator.VOL, SubIndicator.MACD),
    val showChips: Boolean = false,
    val formulas: List<FormulaEntity> = emptyList(),
    val selectedFormulaId: Long? = null,
    val formulaSeries: Map<String, List<Double?>> = emptyMap(),
    val formulaOnMain: Boolean = false,
    // 回放控制
    val autoPlay: Boolean = false,
    val speed: PlaySpeed = PlaySpeed.X1,
    // 本局交易配置(引擎已固定)
    val leverage: Double = 1.0,
    val allowShort: Boolean = true,
    // 引擎快照
    val usedSlots: Int = 0,
    val freeSlots: Int = 5,
    val direction: Direction? = null,             // 当前持仓方向
    val positionPct: Double = 0.0,                // 仓位%
    val maxDrawdownPct: Double = 0.0,
    val plRatio: Double = 0.0,                    // 盈亏比
    val sessionReturnPct: Double = 0.0,           // 本局收益%
    val floatingPnl: Double = 0.0,                // 开仓浮动盈亏(金额)
    val firecrackers: Double = 0.0,
    // 弹窗
    val showExitConfirm: Boolean = false,
    val result: SessionResult? = null,
    val reviewLines: List<String> = emptyList(),
    val strategies: List<StrategyEntity> = emptyList(),
    // 计时
    val startTimeMs: Long = 0L,
    // 设置面板显示值(写回 SettingsStore, 下一局生效)
    val settingLeverage: Double = 1.0,
    val settingSessionBars: Int = 120,
    val settingAllowShort: Boolean = true
) {
    val remainingBars: Int get() = (windowKlines.lastIndex - currentIndex).coerceAtLeast(0)
    val currentBar: Kline? get() = windowKlines.getOrNull(currentIndex)
    val prevDayClose: Double
        get() {
            val cur = currentBar ?: return 0.0
            return if (currentIndex > 0) windowKlines[currentIndex - 1].close else cur.open
        }
}

class TrainingViewModel(private val mode: TrainingMode) : ViewModel() {

    private val app get() = KlineTrainApp.instance

    private val _uiState = MutableStateFlow(TrainingUiState(isBlind = mode == TrainingMode.BLIND))
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var stock: Stock? = null
    private var config: TrainingConfig = TrainingConfig(mode)
    private var engine: TradingEngine? = null
    private var baselineClose: Double = 0.0     // warmup 末收盘, 区间涨跌幅基准
    private var startTimeMs: Long = 0L
    private var selectedFormula: FormulaEntity? = null
    private val dayMarkers = mutableListOf<TradeMarker>()
    private val pendingTrades = mutableListOf<TradeEntity>()
    private var playJob: Job? = null
    private var saving = false

    init {
        viewModelScope.launch {
            app.database.strategyDao().observeAll().collect { list ->
                _uiState.update { it.copy(strategies = list) }
            }
        }
        viewModelScope.launch {
            runCatching { app.database.formulaDao().getAll() }.onSuccess { fs ->
                _uiState.update { it.copy(formulas = fs) }
            }
        }
        load()
    }

    // ---------------- 开局加载 ----------------

    private data class Prepared(
        val stock: Stock,
        val window: List<Kline>,
        val warmup: Int,
        val session: Int,
        val full: Boolean
    )

    fun retry() = load()

    private fun load() {
        playJob?.cancel()
        dayMarkers.clear()
        pendingTrades.clear()
        selectedFormula = null
        engine = null
        _uiState.update {
            TrainingUiState(
                loading = true,
                isBlind = mode == TrainingMode.BLIND,
                strategies = it.strategies,
                formulas = it.formulas
            )
        }
        viewModelScope.launch {
            try {
                val type = when (mode) {
                    TrainingMode.BLIND, TrainingMode.LIMIT_UP -> MarketType.STOCK
                    TrainingMode.INDEX -> MarketType.INDEX
                    TrainingMode.ETF -> MarketType.ETF
                }
                val pool = app.repository.getStockList(type)
                if (pool.isEmpty()) throw IllegalStateException("标的池为空")
                val wantSession = if (mode == TrainingMode.LIMIT_UP) 50 else app.settings.sessionBars
                val wantWarmup = 90
                var chosen: Prepared? = null
                var fallback: Prepared? = null
                for (attempt in 0 until 5) {
                    val s = pool[Random.nextInt(pool.size)]
                    val daily = runCatching { app.repository.getDailyKlines(s) }.getOrDefault(emptyList())
                    if (daily.size < 40) continue
                    val prep = prepareWindow(s, daily, wantWarmup, wantSession) ?: continue
                    if (prep.full) { chosen = prep; break }
                    if (fallback == null || prep.window.size > fallback.window.size) fallback = prep
                }
                val prep = chosen ?: fallback ?: throw IllegalStateException("未能找到符合条件的行情数据")
                setupSession(prep)
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    private fun prepareWindow(s: Stock, daily: List<Kline>, wantWarmup: Int, wantSession: Int): Prepared? {
        if (mode == TrainingMode.LIMIT_UP) {
            // 找涨停日: close/prevClose - 1 >= 9.5%
            val candidates = ArrayList<Int>()
            for (i in 1 until daily.size) {
                val prev = daily[i - 1].close
                if (prev > 0 && daily[i].close / prev - 1 >= 0.095 && i >= 30 && daily.size - 1 - i >= 10) {
                    candidates.add(i)
                }
            }
            if (candidates.isEmpty()) return null
            val fullOnes = candidates.filter { daily.size - 1 - it >= wantSession }
            val idx = if (fullOnes.isNotEmpty()) fullOnes[Random.nextInt(fullOnes.size)]
            else candidates[Random.nextInt(candidates.size)]
            val session = minOf(wantSession, daily.size - 1 - idx)
            val warmup = minOf(wantWarmup, idx + 1)
            val start = idx + 1 - warmup
            val window = daily.subList(start, idx + 1 + session).toList()
            return Prepared(s, window, warmup, session, session >= wantSession)
        }
        val total = wantWarmup + wantSession
        if (daily.size >= total) {
            val start = Random.nextInt(daily.size - total + 1)
            return Prepared(s, daily.subList(start, start + total).toList(), wantWarmup, wantSession, true)
        }
        // 数据不足的兜底方案
        val warmup = minOf(wantWarmup, daily.size / 3)
        val session = daily.size - warmup
        if (warmup < 10 || session < 20) return null
        return Prepared(s, daily.toList(), warmup, session, false)
    }

    private fun setupSession(p: Prepared) {
        stock = p.stock
        config = TrainingConfig(
            mode = mode,
            sessionBars = p.session,
            warmupBars = p.warmup,
            leverage = app.settings.leverage.coerceIn(1.0, 10.0),
            feeRate = app.settings.feeRate,
            allowShort = app.settings.allowShort
        )
        engine = TradingEngine(config)
        baselineClose = p.window[p.warmup - 1].close
        startTimeMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                loading = false,
                error = null,
                stockName = p.stock.name,
                stockCode = p.stock.code,
                windowKlines = p.window,
                warmupBars = p.warmup,
                sessionBars = p.session,
                currentIndex = p.warmup - 1,
                startTimeMs = startTimeMs,
                leverage = config.leverage,
                allowShort = config.allowShort,
                settingLeverage = app.settings.leverage,
                settingSessionBars = app.settings.sessionBars,
                settingAllowShort = app.settings.allowShort,
                firecrackers = app.settings.firecrackers
            )
        }
        rebuild()
    }

    // ---------------- 回放 ----------------

    fun advance() {
        val s = _uiState.value
        val eng = engine ?: return
        if (s.loading || s.result != null || s.showExitConfirm || s.windowKlines.isEmpty()) return
        if (s.currentIndex >= s.windowKlines.lastIndex) {
            finishSession()
            return
        }
        val newIndex = s.currentIndex + 1
        eng.onBarClose(s.windowKlines[newIndex].close)
        _uiState.update { it.copy(currentIndex = newIndex) }
        rebuild()
        if (newIndex >= s.windowKlines.lastIndex || eng.bankrupt) finishSession()
    }

    fun setAutoPlay(on: Boolean) {
        _uiState.update { it.copy(autoPlay = on) }
        playJob?.cancel()
        if (on) {
            playJob = viewModelScope.launch {
                while (isActive) {
                    delay(_uiState.value.speed.delayMs)
                    val s = _uiState.value
                    if (s.result != null || s.finished) break
                    if (!s.showExitConfirm) advance()
                }
                _uiState.update { it.copy(autoPlay = false) }
            }
        }
    }

    fun setSpeed(speed: PlaySpeed) = _uiState.update { it.copy(speed = speed) }

    // ---------------- 交易 ----------------

    fun onBuy() {
        val s = _uiState.value
        val eng = engine ?: return
        if (s.loading || s.result != null) return
        val bar = s.currentBar ?: return
        val idx = s.currentIndex
        if (eng.currentDirection == Direction.SHORT) {
            val ok = eng.closeOne(bar.close, idx)
            if (ok) {
                val pnl = eng.closedTrades.lastOrNull()?.pnl
                recordTrade(bar, Direction.SHORT, isOpen = false, price = bar.close, pnl = pnl)
                dayMarkers.add(TradeMarker(idx, Direction.SHORT, isOpen = false))
                toast("平空1仓 @ ${String.format("%.2f", bar.close)}")
            } else toast("平仓失败")
        } else {
            val ok = eng.open(Direction.LONG, bar.close, idx)
            if (ok) {
                recordTrade(bar, Direction.LONG, isOpen = true, price = bar.close, pnl = null)
                dayMarkers.add(TradeMarker(idx, Direction.LONG, isOpen = true))
                toast("买入1仓 @ ${String.format("%.2f", bar.close)}")
            } else toast(if (eng.freeSlots <= 0) "已满仓，无法再买入" else "买入失败")
        }
        rebuild()
    }

    fun onSell() {
        val s = _uiState.value
        val eng = engine ?: return
        if (s.loading || s.result != null) return
        val bar = s.currentBar ?: return
        val idx = s.currentIndex
        if (eng.currentDirection == Direction.LONG) {
            val ok = eng.closeOne(bar.close, idx)
            if (ok) {
                val pnl = eng.closedTrades.lastOrNull()?.pnl
                recordTrade(bar, Direction.LONG, isOpen = false, price = bar.close, pnl = pnl)
                dayMarkers.add(TradeMarker(idx, Direction.LONG, isOpen = false))
                toast("卖出1仓 @ ${String.format("%.2f", bar.close)}")
            } else toast("卖出失败")
        } else {
            if (!config.allowShort) {
                toast("当前未开启做空，请先买入")
                return
            }
            val ok = eng.open(Direction.SHORT, bar.close, idx)
            if (ok) {
                recordTrade(bar, Direction.SHORT, isOpen = true, price = bar.close, pnl = null)
                dayMarkers.add(TradeMarker(idx, Direction.SHORT, isOpen = true))
                toast("做空1仓 @ ${String.format("%.2f", bar.close)}")
            } else toast(if (eng.freeSlots <= 0) "已满仓，无法再做空" else "做空失败")
        }
        rebuild()
    }

    private fun recordTrade(bar: Kline, direction: Direction, isOpen: Boolean, price: Double, pnl: Double?) {
        pendingTrades.add(
            TradeEntity(
                recordId = 0L,
                barLabel = bar.label,
                direction = direction.name,
                isOpen = isOpen,
                price = price,
                slots = 1,
                pnl = pnl
            )
        )
    }

    // ---------------- 结束与结算 ----------------

    fun requestExit() {
        val s = _uiState.value
        if (s.result != null) return
        if (s.loading || s.error != null || s.windowKlines.isEmpty()) {
            _saved.value = true // 直接退出
            return
        }
        _uiState.update { it.copy(showExitConfirm = true) }
    }

    fun dismissExitConfirm() = _uiState.update { it.copy(showExitConfirm = false) }

    fun confirmExit() {
        _uiState.update { it.copy(showExitConfirm = false) }
        finishSession()
    }

    private fun finishSession() {
        val s = _uiState.value
        val eng = engine ?: return
        if (s.result != null) return
        playJob?.cancel()
        val last = s.currentBar ?: return
        val interval = if (baselineClose > 0) (last.close - baselineClose) / baselineClose * 100 else 0.0
        val result = eng.settle(last.close, s.currentIndex, interval)
        _uiState.update {
            it.copy(
                result = result,
                finished = true,
                autoPlay = false,
                reviewLines = smartReview(result)
            )
        }
        rebuild()
    }

    private fun smartReview(r: SessionResult): List<String> {
        val lines = ArrayList<String>()
        if (r.bankrupt) lines.add("本局爆仓破产，务必敬畏杠杆、控制单笔风险。")
        if (r.richOnce) lines.add("单局收益超过50%，抓住了主升浪，暴富一把！")
        if (r.openCount > 15) lines.add("开仓${r.openCount}次，交易过于频繁，减少无效操作。")
        if (r.holdRatio < 20) lines.add("持仓率仅${String.format("%.2f", r.holdRatio)}%，过于谨慎，大部分时间空仓。")
        if (r.profitLossRatio < 1 && r.openWinRate < 50 && r.openCount > 0) {
            lines.add("胜率与盈亏比双低，亏损单未及时止损。")
        }
        if (r.outperformPct > 0) lines.add("跑赢区间${String.format("%.2f", r.outperformPct)}%，节奏不错。")
        else if (r.openCount > 0) lines.add("跑输区间${String.format("%.2f", -r.outperformPct)}%，操作反而拖累了收益。")
        if (r.maxDrawdownPct > 20) lines.add("最大回撤${String.format("%.2f", r.maxDrawdownPct)}%偏大，注意仓位与止损纪律。")
        if (r.heavyRatio > 60) lines.add("重仓时间占比${String.format("%.2f", r.heavyRatio)}%，长期重仓风险较高。")
        if (lines.isEmpty()) lines.add("表现平稳，保持训练节奏，逐步打磨自己的交易系统。")
        return lines.take(5)
    }

    /** 保存战绩: 训练记录 + 交易明细 + 爆竹结算 */
    fun saveResult(strategyId: Long?, note: String) {
        val s = _uiState.value
        val r = s.result ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val st = stock
                val record = TrainingRecordEntity(
                    mode = mode.name,
                    stockCode = st?.code ?: s.stockCode,
                    stockName = st?.name ?: s.stockName,
                    market = st?.market ?: "",
                    startLabel = s.windowKlines.firstOrNull()?.label ?: "",
                    endLabel = s.currentBar?.label ?: "",
                    returnPct = r.returnPct,
                    intervalChangePct = r.intervalChangePct,
                    outperformPct = r.outperformPct,
                    openCount = r.openCount,
                    openWinRate = r.openWinRate,
                    profitLossRatio = r.profitLossRatio,
                    maxDrawdownPct = r.maxDrawdownPct,
                    holdRatio = r.holdRatio,
                    heavyRatio = r.heavyRatio,
                    holdBars = r.holdBars,
                    bankrupt = r.bankrupt,
                    richOnce = r.richOnce,
                    durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000).coerceAtLeast(0),
                    leverage = config.leverage,
                    strategyId = strategyId,
                    note = note
                )
                val recordId = app.database.trainingRecordDao().insert(record)
                if (pendingTrades.isNotEmpty()) {
                    app.database.tradeDao().insertAll(pendingTrades.map { it.copy(recordId = recordId) })
                }
                val pnlAmount = r.returnPct / 100.0 * config.initialCash
                app.settings.firecrackers = app.settings.firecrackers + pnlAmount
                toast("战绩已保存")
                _saved.value = true
            } catch (e: Exception) {
                saving = false
                toast("保存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    /** 不保存直接退出 */
    fun abandon() {
        _saved.value = true
    }

    // ---------------- 图表配置 ----------------

    fun setTimeframe(tf: TimeFrame) {
        _uiState.update { it.copy(timeframe = tf) }
        rebuild()
    }

    fun setMainOverlay(overlay: MainOverlay) {
        _uiState.update { it.copy(mainOverlay = overlay) }
    }

    fun toggleSubIndicator(sub: SubIndicator) {
        val cur = _uiState.value.subIndicators
        if (cur.contains(sub)) {
            if (cur.size <= 1) {
                toast("至少保留一个副图指标")
                return
            }
            _uiState.update { it.copy(subIndicators = cur - sub) }
        } else {
            if (cur.size >= 3) {
                toast("副图指标最多选3个")
                return
            }
            _uiState.update { it.copy(subIndicators = cur + sub) }
        }
    }

    fun setShowChips(show: Boolean) = _uiState.update { it.copy(showChips = show) }

    fun selectFormula(formula: FormulaEntity?) {
        selectedFormula = formula
        _uiState.update {
            it.copy(
                selectedFormulaId = formula?.id,
                formulaOnMain = formula?.onMainChart ?: false,
                formulaSeries = emptyMap()
            )
        }
        rebuild()
    }

    // ---------------- 训练设置(写回 SettingsStore, 下一局生效) ----------------

    fun setSettingLeverage(x: Double) {
        val v = x.coerceIn(1.0, 10.0)
        app.settings.leverage = v
        _uiState.update { it.copy(settingLeverage = v) }
    }

    fun setSettingSessionBars(bars: Int) {
        app.settings.sessionBars = bars
        _uiState.update { it.copy(settingSessionBars = bars) }
    }

    fun setSettingAllowShort(allow: Boolean) {
        app.settings.allowShort = allow
        _uiState.update { it.copy(settingAllowShort = allow) }
    }

    // ---------------- 内部 ----------------

    /** 根据当前进度/周期/公式重算展示序列与引擎快照 */
    private fun rebuild() {
        val s = _uiState.value
        val eng = engine ?: return
        if (s.windowKlines.isEmpty()) return
        val idx = s.currentIndex.coerceIn(0, s.windowKlines.lastIndex)
        val visible = s.windowKlines.subList(0, idx + 1).toList()
        val curBar = s.windowKlines[idx]
        val prevClose = if (idx > 0) s.windowKlines[idx - 1].close else curBar.open
        val display: List<Kline>
        val markers: List<TradeMarker>
        var panelPrevClose: Double? = null
        when (s.timeframe) {
            TimeFrame.DAY -> {
                display = visible
                markers = dayMarkers.toList()
            }
            TimeFrame.WEEK, TimeFrame.MONTH -> {
                display = KlineUtils.aggregate(visible, s.timeframe)
                markers = emptyList()
            }
            else -> {
                display = KlineUtils.synthesizeMinuteBars(curBar, prevClose, s.timeframe.minutes)
                markers = emptyList()
                if (s.timeframe == TimeFrame.MIN_RT) panelPrevClose = prevClose
            }
        }
        var series: Map<String, List<Double?>> = emptyMap()
        val formula = selectedFormula
        if (formula != null && display.isNotEmpty()) {
            try {
                series = FormulaEngine.evaluate(formula.source, display)
            } catch (e: FormulaException) {
                selectedFormula = null
                toast("公式出错: ${e.message ?: "计算失败"}")
            } catch (e: Exception) {
                selectedFormula = null
                toast("公式执行失败")
            }
        }
        val price = curBar.close
        val equity = eng.equity(price)
        _uiState.update {
            it.copy(
                displayKlines = display,
                displayMarkers = markers,
                prevCloseForPanel = panelPrevClose,
                formulaSeries = series,
                selectedFormulaId = selectedFormula?.id,
                formulaOnMain = selectedFormula?.onMainChart ?: false,
                usedSlots = eng.usedSlots,
                freeSlots = eng.freeSlots,
                direction = eng.currentDirection,
                positionPct = eng.positionRatio(price) * 100,
                maxDrawdownPct = eng.maxDrawdownPct,
                plRatio = currentPlRatio(eng),
                sessionReturnPct = (equity - config.initialCash) / config.initialCash * 100,
                floatingPnl = eng.lots.sumOf { lot -> lot.pnl(price) },
                firecrackers = app.settings.firecrackers
            )
        }
    }

    private fun currentPlRatio(eng: TradingEngine): Double {
        val trades = eng.closedTrades
        if (trades.isEmpty()) return 0.0
        val wins = trades.filter { it.pnl > 0 }
        val losses = trades.filter { it.pnl < 0 }
        if (wins.isEmpty()) return 0.0
        if (losses.isEmpty()) return 999.0
        val avgWin = wins.sumOf { it.pnl } / wins.size
        val avgLoss = abs(losses.sumOf { it.pnl }) / losses.size
        return if (avgLoss == 0.0) 999.0 else avgWin / avgLoss
    }

    private fun toast(msg: String) {
        _toasts.tryEmit(msg)
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(mode: TrainingMode): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TrainingViewModel(mode) as T
        }
    }
}
