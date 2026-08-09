package com.klinetrain.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.TrainingRecordEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 段位信息 */
data class RankInfo(
    val name: String,
    val stars: Int      // 1~5
)

/** 首页 UI 状态 */
data class HomeUiState(
    val firecrackers: Double = 10000.0,
    val sessionCount: Int = 0,
    val totalWinRate: Double = 0.0,      // 总胜率(%)：returnPct>0 的场次占比
    val richCount: Int = 0,
    val bankruptCount: Int = 0,
    val totalHoldBars: Int = 0,
    val rank: RankInfo = RankInfo("青铜韭菜", 1),
    val recentRecords: List<TrainingRecordEntity> = emptyList()
)

class HomeViewModel : ViewModel() {

    private val app = KlineTrainApp.instance
    private val recordDao = app.database.trainingRecordDao()

    val uiState: StateFlow<HomeUiState> = combine(
        recordDao.observeAll(),
        recordDao.observeRecent(10)
    ) { all, recent ->
        val count = all.size
        val winRate = if (count == 0) 0.0 else all.count { it.returnPct > 0 } * 100.0 / count
        HomeUiState(
            firecrackers = app.settings.firecrackers,
            sessionCount = count,
            totalWinRate = winRate,
            richCount = all.count { it.richOnce },
            bankruptCount = all.count { it.bankrupt },
            totalHoldBars = all.sumOf { it.holdBars },
            rank = rankOf(count, winRate),
            recentRecords = recent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleLiked(record: TrainingRecordEntity) {
        viewModelScope.launch { recordDao.update(record.copy(liked = !record.liked)) }
    }

    fun toggleFavorite(record: TrainingRecordEntity) {
        viewModelScope.launch { recordDao.update(record.copy(favorite = !record.favorite)) }
    }

    companion object {
        /**
         * 段位规则：
         * 场次<20 青铜韭菜；≥20 白银小散户；≥50且胜率≥50% 黄金老股民；
         * ≥100且≥55% 铂金操盘手；≥200且≥60% 钻石游资；≥500且≥65% 王者庄家。
         * 星数 = 场次在当前段位区间内的进度五等分(1~5星)。
         */
        fun rankOf(sessions: Int, winRatePct: Double): RankInfo {
            // 各段位: (场次下限, 胜率下限, 名称)
            val tiers = listOf(
                Triple(0, 0.0, "青铜韭菜"),
                Triple(20, 0.0, "白银小散户"),
                Triple(50, 50.0, "黄金老股民"),
                Triple(100, 55.0, "铂金操盘手"),
                Triple(200, 60.0, "钻石游资"),
                Triple(500, 65.0, "王者庄家")
            )
            var tierIndex = 0
            for (i in tiers.indices) {
                if (sessions >= tiers[i].first && winRatePct >= tiers[i].second) tierIndex = i
            }
            val lo = tiers[tierIndex].first
            val hi = if (tierIndex + 1 < tiers.size) tiers[tierIndex + 1].first else -1
            val stars = if (hi <= lo) {
                5 // 最高段位满星
            } else {
                val progress = (sessions - lo).toDouble() / (hi - lo).toDouble()
                (progress * 5).toInt().coerceIn(0, 4) + 1
            }
            return RankInfo(tiers[tierIndex].third, stars)
        }

        /** 将持仓 bar 数(按交易日)换算为 "X年X个月X天" */
        fun formatHoldDays(bars: Int): String {
            val total = bars.coerceAtLeast(0)
            val years = total / 365
            val months = total % 365 / 30
            val days = total % 365 % 30
            return buildString {
                if (years > 0) append("${years}年")
                if (months > 0) append("${months}个月")
                append("${days}天")
            }
        }
    }
}
