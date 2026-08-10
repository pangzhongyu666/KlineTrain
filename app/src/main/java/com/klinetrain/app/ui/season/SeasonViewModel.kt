package com.klinetrain.app.ui.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.TrainingRecordEntity
import com.klinetrain.app.data.model.TrainingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单赛季汇总统计。null 字段表示无数据(界面显示 "--"，避免 NaN) */
data class SeasonStats(
    val sessions: Int = 0,               // 训练场次
    val winRate: Double? = null,         // 训练胜率% (returnPct>0 比例)
    val outperformRate: Double? = null,  // 跑赢区间率% (outperformPct>0 比例)
    val plRatio: Double? = null,         // 训练盈亏比 (去掉999后平均)
    val totalHoldBars: Int = 0,          // 持仓时间(交易日)
    val avgHoldRatio: Double? = null,    // 持仓率%(平均)
    val avgHeavyRatio: Double? = null,   // 重仓率%(平均)
    val totalDurationSec: Long = 0L,     // 训练耗时(秒)
    val totalOpenCount: Int = 0,         // 开仓次数
    val openWinRate: Double? = null,     // 开仓胜率%(按openCount加权)
    val maxProfitPct: Double? = null,    // 最大盈利% (max returnPct)
    val maxDrawdownPct: Double? = null   // 最大回撤% (max maxDrawdownPct)
)

/** 详细数据页 UI 状态 */
data class SeasonUiState(
    val nickname: String = "",
    val currentSeason: Int = 1,                              // settings.seasonIndex
    val seasons: List<Int> = emptyList(),                    // 降序赛季列表
    val selectedSeason: Int = 1,
    val recordsAsc: List<TrainingRecordEntity> = emptyList(),// 该赛季按 createdAt 升序
    val curve: List<Double> = emptyList(),                   // 爆竹数量序列(含起点)
    val stats: SeasonStats = SeasonStats(),
    val firecrackers: Double = 10000.0                       // 当前爆竹数
)

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonViewModel : ViewModel() {

    private val app = KlineTrainApp.instance
    private val settings = app.settings
    private val recordDao = app.database.trainingRecordDao()

    private val _selectedSeason = MutableStateFlow(settings.seasonIndex)

    /** 选中赛季与其记录成对发射，避免切换瞬间赛季与数据错位 */
    private val seasonRecords = _selectedSeason.flatMapLatest { season ->
        recordDao.observeBySeason(season).map { season to it }
    }

    val uiState: StateFlow<SeasonUiState> = combine(
        recordDao.observeSeasons(),
        seasonRecords
    ) { dbSeasons, (selected, records) ->
        val current = settings.seasonIndex
        SeasonUiState(
            nickname = settings.nickname,
            currentSeason = current,
            seasons = (dbSeasons + current).distinct().sortedDescending(),
            selectedSeason = selected,
            recordsAsc = records,
            curve = buildCurve(selected, records),
            stats = computeStats(records),
            firecrackers = settings.firecrackers
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SeasonUiState(
            nickname = settings.nickname,
            currentSeason = settings.seasonIndex,
            seasons = listOf(settings.seasonIndex),
            selectedSeason = settings.seasonIndex,
            firecrackers = settings.firecrackers
        )
    )

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }

    fun toggleLiked(record: TrainingRecordEntity) {
        viewModelScope.launch { recordDao.update(record.copy(liked = !record.liked)) }
    }

    fun toggleFavorite(record: TrainingRecordEntity) {
        viewModelScope.launch { recordDao.update(record.copy(favorite = !record.favorite)) }
    }

    /** 爆竹曲线：起点=基准初始金额，之后为每局结束爆竹；空赛季只有当前爆竹一个点(画水平线) */
    private fun buildCurve(season: Int, records: List<TrainingRecordEntity>): List<Double> {
        if (records.isEmpty()) {
            val v = if (season == settings.seasonIndex) settings.firecrackers else settings.initialCash
            return listOf(v)
        }
        return listOf(settings.initialCash) + records.map { it.endFirecrackers }
    }

    /** 生成当前所选赛季的 CSV 文本(按时间升序) */
    fun buildCsv(records: List<TrainingRecordEntity>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val sb = StringBuilder()
        sb.append("时间,模式,标的,代码,本局收益%,区间涨跌%,跑赢%,开仓次数,开仓胜率%,盈亏比,最大回撤%,持仓率%,结束爆竹\n")
        records.forEach { r ->
            val mode = TrainingMode.entries.firstOrNull { it.name == r.mode }?.label ?: r.mode
            sb.append(sdf.format(Date(r.createdAt))).append(',')
                .append(mode.replace(',', ' ')).append(',')
                .append(r.stockName.replace(',', ' ')).append(',')
                .append(r.stockCode.replace(',', ' ')).append(',')
                .append(f2(r.returnPct)).append(',')
                .append(f2(r.intervalChangePct)).append(',')
                .append(f2(r.outperformPct)).append(',')
                .append(r.openCount).append(',')
                .append(f2(r.openWinRate)).append(',')
                .append(f2(r.profitLossRatio)).append(',')
                .append(f2(r.maxDrawdownPct)).append(',')
                .append(f2(r.holdRatio)).append(',')
                .append(f2(r.endFirecrackers)).append('\n')
        }
        return sb.toString()
    }

    companion object {
        private fun f2(v: Double): String =
            if (v.isNaN() || v.isInfinite()) "0.00" else String.format(Locale.US, "%.2f", v)

        /** 汇总一个赛季的训练数据(全部除0防御，空列表返回默认值) */
        fun computeStats(records: List<TrainingRecordEntity>): SeasonStats {
            if (records.isEmpty()) return SeasonStats()
            val n = records.size
            val plList = records.map { it.profitLossRatio }
                .filter { !it.isNaN() && !it.isInfinite() && it < 999.0 }
            val totalOpen = records.sumOf { it.openCount }
            return SeasonStats(
                sessions = n,
                winRate = records.count { it.returnPct > 0 } * 100.0 / n,
                outperformRate = records.count { it.outperformPct > 0 } * 100.0 / n,
                plRatio = if (plList.isEmpty()) null else plList.average(),
                totalHoldBars = records.sumOf { it.holdBars },
                avgHoldRatio = records.map { it.holdRatio }.average(),
                avgHeavyRatio = records.map { it.heavyRatio }.average(),
                totalDurationSec = records.sumOf { it.durationSec },
                totalOpenCount = totalOpen,
                openWinRate = if (totalOpen == 0) null
                else records.sumOf { it.openCount * it.openWinRate } / totalOpen,
                maxProfitPct = records.maxOfOrNull { it.returnPct },
                maxDrawdownPct = records.maxOfOrNull { it.maxDrawdownPct }
            )
        }
    }
}
