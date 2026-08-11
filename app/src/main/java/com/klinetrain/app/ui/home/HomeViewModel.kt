package com.klinetrain.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.TrainingRecordEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 首页 UI 状态 */
data class HomeUiState(
    val firecrackers: Double = 10000.0,
    val sessionCount: Int = 0,
    val totalWinRate: Double = 0.0,      // 总胜率(%)：returnPct>0 的场次占比
    val richCount: Int = 0,
    val bankruptCount: Int = 0,
    val totalHoldBars: Int = 0,
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
            recentRecords = recent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    companion object {
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
