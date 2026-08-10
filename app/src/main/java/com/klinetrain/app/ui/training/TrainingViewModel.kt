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
import com.klinetrain.app.data.model.MarketMeta
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
    val totalSlots: Int = 5,
    val slotsPerTrade: Int = 1,
    val baseInitial: Double = 10000.0,            // 基准初始金额(破产/暴富基准)
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
    val turnoverPct: Double? = null,              // 当前bar换手率%(不可用为null)
    val costPrice: Double? = null,                // 持仓加权平均入场价
    // 弹窗
    val showExitConfirm: Boolean = false,
    val result: SessionResult? = null,
    val reviewLines: List<String> = emptyList(),
    val strategies: List<StrategyEntity> = emptyList(),
    // 计时
    val startTimeMs: Long = 0L
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

    /** 交易成功事件(用于震动反馈, 已按 vibrateMode 过滤) */
    private val _haptics = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val haptics: SharedFlow<Unit> = _haptics.asSharedFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var stock: Stock? = null
    private var config: TrainingConfig = TrainingConfig(mode)
    private var engine: TradingEngine? = null
    private var baselineClose: Double = 0.0     // warmup 末收盘, 区间涨跌幅基准
    private var startTimeMs: Long = 0L
    private var finalEquity: Double = 0.0       // 结算后最终权益(=结算后爆竹数)
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
        finalEquity = 0.0
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
                    TrainingMode.CRYPTO -> MarketType.CRYPTO
                }
                var pool = app.repository.getStockList(type)
                if (type == MarketType.STOCK) pool = applyStockFilters(pool)
                if (mode == TrainingMode.CRYPTO && pool.isNotEmpty()) {
                    // 币圈训练: 固定选中币种(找不到用第一个)
                    val want = app.settings.selectedCoin.trim().uppercase() + "USDT"
                    val coin = pool.firstOrNull { it.code.equals(want, ignoreCase = true) } ?: pool.first()
                    pool = listOf(coin)
                }
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

    /** 股票池筛选: 市场板块 + 去ST。筛完为空则忽略筛选保证可玩 */
    private fun applyStockFilters(pool: List<Stock>): List<Stock> {
        val mf = app.settings.marketFilter
        val noSt = app.settings.excludeSt
        if (mf == 0 && !noSt) return pool
        val filtered = pool.filter { st ->
            val code = st.code
            val okMarket = when (mf) {
                1 -> (code.startsWith("60") || code.startsWith("00")) &&
                    !code.startsWith("30") && !code.startsWith("688")
                2 -> code.startsWith("30")
                3 -> code.startsWith("688")
                else -> true
            }
            val okSt = !noSt || !st.name.uppercase().contains("ST")
            okMarket && okSt
        }
        return filtered.ifEmpty { pool }
    }

    /** 时间段筛选下限(epoch millis)。0不限返回null */
    private fun timeCutoff(): Long? {
        val years = when (app.settings.timeRangeFilter) {
            1 -> 5
            2 -> 10
            else -> return null
        }
        return System.currentTimeMillis() - years * 365L * 24 * 3600 * 1000
    }

    private fun prepareWindow(s: Stock, daily: List<Kline>, wantWarmup: Int, wantSession: Int): Prepared? {
        val cutoff = timeCutoff()
        if (mode == TrainingMode.LIMIT_UP) {
            // 找涨停日: close/prevClose - 1 >= 9.5%
            var candidates = ArrayList<Int>()
            for (i in 1 until daily.size) {
                val prev = daily[i - 1].close
                if (prev > 0 && daily[i].close / prev - 1 >= 0.095 && i >= 30 && daily.size - 1 - i >= 10) {
                    candidates.add(i)
                }
            }
            if (candidates.isEmpty()) return null
            // 时间段筛选: 窗口起点须落在最近N年内; 无满足者则忽略筛选
            if (cutoff != null) {
                val inRange = candidates.filter { i ->
                    val warm = minOf(wantWarmup, i + 1)
                    daily.getOrNull(i + 1 - warm)?.let { it.time >= cutoff } == true
                }
                if (inRange.isNotEmpty()) candidates = ArrayList(inRange)
            }
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
            // 时间段筛选: 随机窗口必须完全落在最近N年内; 数据不足则忽略
            var minStart = 0
            if (cutoff != null) {
                val firstIn = daily.indexOfFirst { it.time >= cutoff }
                if (firstIn in 0..(daily.size - total)) minStart = firstIn
            }
            val span = daily.size - total - minStart + 1
            val start = if (span > 0) minStart + Random.nextInt(span) else 0
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
        val settings = app.settings
        val totalSlots = settings.totalSlots.coerceAtLeast(1)
        // 爆竹已低于基准破产线(如用户上调了初始金额)：上一赛季视为已破产结束，重置后再开局，
        // 避免入场资金低于破产线导致第一根K线即破产
        var fire = settings.firecrackers
        if (fire <= settings.initialCash * 0.05) {
            if (fire > 0) settings.seasonIndex = settings.seasonIndex + 1
            settings.firecrackers = settings.initialCash
            fire = settings.initialCash
        }
        config = TrainingConfig(
            mode = mode,
            sessionBars = p.session,
            warmupBars = p.warmup,
            leverage = (if (settings.useLeverage) settings.leverage else 1.0).coerceIn(1.0, 10.0),
            feeRate = settings.feeRate,
            allowShort = settings.allowShort,
            initialCash = if (fire > 0) fire else settings.initialCash,  // 每局以当前爆竹全额入场
            baseInitial = settings.initialCash,
            totalSlots = totalSlots,
            slotsPerTrade = if (settings.splitMode) settings.slotsPerTrade.coerceIn(1, totalSlots) else totalSlots,
            splitMode = settings.splitMode,
            stopProfitPct = if (settings.stopEnabled) settings.stopProfitPct else null,
            stopLossPct = if (settings.stopEnabled) settings.stopLossPct else null
        )
        engine = TradingEngine(config)
        baselineClose = p.window.getOrNull(p.warmup - 1)?.close ?: p.window.first().close
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
                currentIndex = (p.warmup - 1).coerceAtLeast(0),
                startTimeMs = startTimeMs,
                leverage = config.leverage,
                allowShort = config.allowShort,
                totalSlots = config.totalSlots,
                slotsPerTrade = config.slotsPerTrade,
                baseInitial = config.baseInitial,
                freeSlots = config.totalSlots,
                firecrackers = settings.firecrackers
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
        val newBar = s.windowKlines[newIndex]
        // 先按新bar收盘价检查止盈止损, 再推进引擎统计
        checkStops(newBar, newIndex)
        eng.onBarClose(newBar.close)
        _uiState.update { it.copy(currentIndex = newIndex) }
        rebuild()
        if (newIndex >= s.windowKlines.lastIndex || eng.bankrupt) finishSession()
    }

    /** 止盈止损: 对每个持仓lot按价格涨跌幅判断, 倒序平仓 */
    private fun checkStops(bar: Kline, barIndex: Int) {
        val eng = engine ?: return
        val tp = config.stopProfitPct
        val sl = config.stopLossPct
        if (tp == null && sl == null) return
        val close = bar.close
        if (close <= 0) return
        for (i in eng.lots.indices.reversed()) {
            val lot = eng.lots.getOrNull(i) ?: continue
            if (lot.entryPrice <= 0) continue
            val sign = if (lot.direction == Direction.LONG) 1.0 else -1.0
            val pct = (close - lot.entryPrice) / lot.entryPrice * 100 * sign
            val isTp = tp != null && pct >= tp
            val isSl = !isTp && sl != null && pct <= -sl
            if (!isTp && !isSl) continue
            val dir = lot.direction
            if (eng.closeLotAt(i, close, barIndex)) {
                val pnl = eng.closedTrades.lastOrNull()?.pnl
                val prefix = if (isTp) "止盈" else "止损"
                pendingTrades.add(
                    TradeEntity(
                        recordId = 0L,
                        barLabel = prefix + bar.label,
                        direction = dir.name,
                        isOpen = false,
                        price = close,
                        slots = 1,
                        pnl = pnl
                    )
                )
                dayMarkers.add(TradeMarker(barIndex, dir, isOpen = false))
                toast("第${i + 1}仓触发$prefix")
            }
        }
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
        val per = config.slotsPerTrade.coerceAtLeast(1)
        if (eng.currentDirection == Direction.SHORT) {
            var n = 0
            while (n < per && eng.closeOne(bar.close, idx)) n++
            if (n > 0) {
                val pnl = eng.closedTrades.takeLast(n).sumOf { it.pnl }
                recordTrade(bar.label, Direction.SHORT, isOpen = false, price = bar.close, slots = n, pnl = pnl)
                dayMarkers.add(TradeMarker(idx, Direction.SHORT, isOpen = false))
                hapticOnTrade()
                toast("平空${n}仓 @ ${String.format("%.2f", bar.close)}")
            } else toast("平仓失败")
        } else {
            var n = 0
            while (n < per && eng.open(Direction.LONG, bar.close, idx)) n++
            if (n > 0) {
                recordTrade(bar.label, Direction.LONG, isOpen = true, price = bar.close, slots = n, pnl = null)
                dayMarkers.add(TradeMarker(idx, Direction.LONG, isOpen = true))
                hapticOnTrade()
                toast("买入${n}仓 @ ${String.format("%.2f", bar.close)}")
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
        val per = config.slotsPerTrade.coerceAtLeast(1)
        if (eng.currentDirection == Direction.LONG) {
            var n = 0
            while (n < per && eng.closeOne(bar.close, idx)) n++
            if (n > 0) {
                val pnl = eng.closedTrades.takeLast(n).sumOf { it.pnl }
                recordTrade(bar.label, Direction.LONG, isOpen = false, price = bar.close, slots = n, pnl = pnl)
                dayMarkers.add(TradeMarker(idx, Direction.LONG, isOpen = false))
                hapticOnTrade()
                toast("卖出${n}仓 @ ${String.format("%.2f", bar.close)}")
            } else toast("卖出失败")
        } else {
            if (!config.allowShort) {
                toast("当前未开启做空，请先买入")
                return
            }
            var n = 0
            while (n < per && eng.open(Direction.SHORT, bar.close, idx)) n++
            if (n > 0) {
                recordTrade(bar.label, Direction.SHORT, isOpen = true, price = bar.close, slots = n, pnl = null)
                dayMarkers.add(TradeMarker(idx, Direction.SHORT, isOpen = true))
                hapticOnTrade()
                toast("做空${n}仓 @ ${String.format("%.2f", bar.close)}")
            } else toast(if (eng.freeSlots <= 0) "已满仓，无法再做空" else "做空失败")
        }
        rebuild()
    }

    private fun recordTrade(
        barLabel: String,
        direction: Direction,
        isOpen: Boolean,
        price: Double,
        slots: Int,
        pnl: Double?
    ) {
        pendingTrades.add(
            TradeEntity(
                recordId = 0L,
                barLabel = barLabel,
                direction = direction.name,
                isOpen = isOpen,
                price = price,
                slots = slots,
                pnl = pnl
            )
        )
    }

    private fun hapticOnTrade() {
        if (app.settings.vibrateMode) _haptics.tryEmit(Unit)
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
        finalEquity = eng.equity(last.close)   // 结算后全部平仓, 即最终爆竹数
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
        if (r.richOnce) lines.add("爆竹达到基准初始100倍，抓住了主升浪，暴富一把！")
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

    /** 保存战绩: 训练记录 + 交易明细 + 爆竹赛季结算 */
    fun saveResult(strategyId: Long?, note: String) {
        val s = _uiState.value
        val r = s.result ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val st = stock
                val endFire = finalEquity
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
                    note = note,
                    seasonIndex = app.settings.seasonIndex,
                    endFirecrackers = endFire
                )
                val recordId = app.database.trainingRecordDao().insert(record)
                if (pendingTrades.isNotEmpty()) {
                    app.database.tradeDao().insertAll(pendingTrades.map { it.copy(recordId = recordId) })
                }
                if (settleFirecrackers()) {
                    toast("破产！进入第${app.settings.seasonIndex}赛季，爆竹已重置")
                } else {
                    toast("战绩已保存")
                }
                _saved.value = true
            } catch (e: Exception) {
                saving = false
                toast("保存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    /** 不保存直接退出。爆竹赛季结算依然执行(破产惩罚不能靠放弃保存绕过) */
    fun abandon() {
        if (_uiState.value.result != null) settleFirecrackers()
        _saved.value = true
    }

    /**
     * 爆竹赛季结算(保存与放弃都会执行，幂等)：
     * 最终权益≤基准5% → 破产，赛季+1并重置爆竹；否则爆竹=最终权益。
     * @return 是否触发破产重置
     */
    private var fireSettled = false
    private fun settleFirecrackers(): Boolean {
        if (fireSettled) return false
        fireSettled = true
        val base = app.settings.initialCash
        return if (finalEquity <= base * 0.05) {
            app.settings.seasonIndex = app.settings.seasonIndex + 1
            app.settings.firecrackers = base
            true
        } else {
            app.settings.firecrackers = finalEquity
            false
        }
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
        // 持仓加权平均入场价
        val totalShares = eng.lots.sumOf { it.shares }
        val cost = if (totalShares > 0) eng.lots.sumOf { it.entryPrice * it.shares } / totalShares else null
        // 换手率
        val turnover = stock?.let { MarketMeta.turnoverPct(it, curBar.volume) }
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
                sessionReturnPct = if (config.initialCash > 0)
                    (equity - config.initialCash) / config.initialCash * 100 else 0.0,
                floatingPnl = eng.lots.sumOf { lot -> lot.pnl(price) },
                firecrackers = app.settings.firecrackers,
                turnoverPct = turnover,
                costPrice = cost
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
