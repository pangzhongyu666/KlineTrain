package com.klinetrain.app.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.StrategyEntity
import com.klinetrain.app.data.db.TradeEntity
import com.klinetrain.app.data.db.TrainingRecordEntity
import com.klinetrain.app.data.model.Direction
import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.MarketType
import com.klinetrain.app.data.model.Stock
import com.klinetrain.app.data.model.TradeMarker
import com.klinetrain.app.data.model.TrainingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

/** 详情页K线图状态 */
data class DetailChartState(
    val loading: Boolean = true,
    val available: Boolean = false,          // false且loading=false时显示"历史行情不可用"
    val klines: List<Kline> = emptyList(),   // 训练区间(前置30根) 日K
    val markers: List<TradeMarker> = emptyList()
)

/** 训练记录列表 + 详情共用 ViewModel */
class RecordsViewModel : ViewModel() {

    private val app = KlineTrainApp.instance
    private val recordDao = app.database.trainingRecordDao()
    private val tradeDao = app.database.tradeDao()
    private val strategyDao = app.database.strategyDao()

    // ---------- 列表 ----------

    /** 模式筛选，null=全部 */
    private val _filter = MutableStateFlow<TrainingMode?>(null)
    val filter: StateFlow<TrainingMode?> = _filter.asStateFlow()

    val records: StateFlow<List<TrainingRecordEntity>> = combine(
        recordDao.observeAll(),
        _filter
    ) { all, f ->
        if (f == null) all else all.filter { it.mode == f.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(mode: TrainingMode?) {
        _filter.value = mode
    }

    // ---------- 详情 ----------

    private val _detailRecord = MutableStateFlow<TrainingRecordEntity?>(null)
    val detailRecord: StateFlow<TrainingRecordEntity?> = _detailRecord.asStateFlow()

    private val _detailTrades = MutableStateFlow<List<TradeEntity>>(emptyList())
    val detailTrades: StateFlow<List<TradeEntity>> = _detailTrades.asStateFlow()

    private val _detailChart = MutableStateFlow(DetailChartState())
    val detailChart: StateFlow<DetailChartState> = _detailChart.asStateFlow()

    val strategies: StateFlow<List<StrategyEntity>> = strategyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadDetail(recordId: Long) {
        viewModelScope.launch {
            _detailChart.value = DetailChartState(loading = true)
            val record = recordDao.getById(recordId)
            val trades = runCatching { tradeDao.getByRecord(recordId) }.getOrDefault(emptyList())
            _detailRecord.value = record
            _detailTrades.value = trades
            _detailChart.value = if (record == null) {
                DetailChartState(loading = false, available = false)
            } else {
                buildChartState(record, trades)
            }
        }
    }

    /** 拉取该记录标的的日K，截取训练区间(前置最多30根热身)，并把交易映射为买卖点标记 */
    private suspend fun buildChartState(
        record: TrainingRecordEntity,
        trades: List<TradeEntity>
    ): DetailChartState {
        val type = when (record.mode) {
            TrainingMode.INDEX.name -> MarketType.INDEX
            TrainingMode.ETF.name -> MarketType.ETF
            TrainingMode.CRYPTO.name -> MarketType.CRYPTO
            else -> MarketType.STOCK    // BLIND / LIMIT_UP
        }
        val stock = Stock(
            code = record.stockCode,
            name = record.stockName,
            market = record.market,
            type = type
        )
        val klines = runCatching { app.repository.getDailyKlines(stock) }.getOrDefault(emptyList())
        if (klines.isEmpty() || record.startLabel.isEmpty() || record.endLabel.isEmpty()) {
            return DetailChartState(loading = false, available = false)
        }
        val startIdx = klines.indexOfFirst { it.label == record.startLabel }
        val endIdx = klines.indexOfFirst { it.label == record.endLabel }
        if (startIdx < 0 || endIdx < 0 || startIdx > endIdx) {
            return DetailChartState(loading = false, available = false)
        }
        val display = klines.subList(max(0, startIdx - 30), endIdx + 1).toList()
        val markers = trades.mapNotNull { t ->
            // 止盈/止损单的 barLabel 带前缀，先去掉再匹配日期标签
            val label = t.barLabel.removePrefix("止盈").removePrefix("止损")
            val idx = display.indexOfFirst { k -> k.label == label }
            if (idx < 0) null else TradeMarker(
                barIndex = idx,
                direction = Direction.entries.firstOrNull { it.name == t.direction } ?: Direction.LONG,
                isOpen = t.isOpen
            )
        }
        return DetailChartState(loading = false, available = true, klines = display, markers = markers)
    }

    fun saveNote(note: String) {
        val record = _detailRecord.value ?: return
        viewModelScope.launch {
            recordDao.update(record.copy(note = note))
            _detailRecord.value = recordDao.getById(record.id)
        }
    }

    fun setStrategy(strategyId: Long?) {
        val record = _detailRecord.value ?: return
        viewModelScope.launch {
            recordDao.update(record.copy(strategyId = strategyId))
            _detailRecord.value = recordDao.getById(record.id)
        }
    }

    fun deleteRecord(onDeleted: () -> Unit) {
        val record = _detailRecord.value ?: return
        viewModelScope.launch {
            recordDao.delete(record)
            _detailRecord.value = null
            onDeleted()
        }
    }

    companion object {
        /** 根据记录数据生成 3~5 条智能复盘评语 */
        fun buildReviewComments(r: TrainingRecordEntity): List<String> {
            val comments = mutableListOf<String>()
            if (r.bankrupt) {
                comments.add("本局爆仓破产，务必反思仓位与止损纪律，控制单次亏损是活下来的前提。")
            }
            if (r.richOnce) {
                comments.add("本局收益超过50%实现暴富，抓住了主升浪，注意总结成功经验形成可复制的战法。")
            }
            comments.add(
                when {
                    r.outperformPct > 0 -> "本局跑赢区间 ${fmt(r.outperformPct)}%，主动操作创造了超额收益。"
                    else -> "本局跑输区间 ${fmt(-r.outperformPct)}%，若只做持有反而更好，反思是否存在过度交易。"
                }
            )
            if (r.openCount > 0) {
                comments.add(
                    when {
                        r.openWinRate >= 60 -> "开仓胜率 ${fmt(r.openWinRate)}%，入场时机把握较好，可尝试提高单次仓位利用率。"
                        r.openWinRate >= 40 -> "开仓胜率 ${fmt(r.openWinRate)}%，处于中等水平，注意提炼入场信号提高确定性。"
                        else -> "开仓胜率仅 ${fmt(r.openWinRate)}%，入场信号可靠性不足，建议减少随手开仓。"
                    }
                )
                comments.add(
                    when {
                        r.profitLossRatio >= 2.0 -> "盈亏比 ${fmt(r.profitLossRatio)}，赚大亏小，风格健康，坚持让利润奔跑。"
                        r.profitLossRatio >= 1.0 -> "盈亏比 ${fmt(r.profitLossRatio)}，尚可，可尝试更耐心持有盈利单、更果断砍掉亏损单。"
                        else -> "盈亏比仅 ${fmt(r.profitLossRatio)}，赚小亏大，存在拿不住盈利、死扛亏损的倾向。"
                    }
                )
            } else {
                comments.add("本局未进行任何开仓操作，空仓观望也是策略，但训练价值有限，建议大胆尝试。")
            }
            if (r.maxDrawdownPct > 20) {
                comments.add("最大回撤达 ${fmt(r.maxDrawdownPct)}%，波动过大，注意分批建仓与及时止损。")
            } else if (r.heavyRatio > 60 && r.openCount > 0) {
                comments.add("重仓率 ${fmt(r.heavyRatio)}%，长期重仓运行，需警惕黑天鹅风险。")
            }
            return comments.take(5)
        }

        private fun fmt(v: Double): String = String.format(java.util.Locale.CHINA, "%.2f", v)
    }
}
