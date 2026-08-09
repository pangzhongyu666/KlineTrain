package com.klinetrain.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 我的页 UI 状态 */
data class ProfileUiState(
    val totalSessions: Int = 0,
    val totalWinRate: Double = 0.0,        // 总胜率(%)
    val totalRichCount: Int = 0,
    val totalBankruptCount: Int = 0,
    val totalHoldBars: Int = 0,            // 总持仓时间(交易日)
    val totalDurationSec: Long = 0L,       // 总训练消耗(秒)
    val totalOpenCount: Int = 0,
    val totalOpenWinRate: Double = 0.0,    // 总开仓胜率(%): 按开仓次数加权
    val growthCurve: List<Double> = emptyList()  // 按时间序的累计收益率(%)
)

class ProfileViewModel : ViewModel() {

    private val app = KlineTrainApp.instance
    private val recordDao = app.database.trainingRecordDao()

    private val _nickname = MutableStateFlow(app.settings.nickname)
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    val uiState: StateFlow<ProfileUiState> = recordDao.observeAll()
        .map { all ->
            val count = all.size
            val winRate = if (count == 0) 0.0 else all.count { it.returnPct > 0 } * 100.0 / count
            val totalOpen = all.sumOf { it.openCount }
            val openWinRate = if (totalOpen == 0) 0.0
            else all.sumOf { it.openCount * it.openWinRate } / totalOpen
            // 成长曲线：按创建时间升序的累计收益率
            var cum = 0.0
            val curve = all.sortedBy { it.createdAt }.map { r ->
                cum += r.returnPct
                cum
            }
            ProfileUiState(
                totalSessions = count,
                totalWinRate = winRate,
                totalRichCount = all.count { it.richOnce },
                totalBankruptCount = all.count { it.bankrupt },
                totalHoldBars = all.sumOf { it.holdBars },
                totalDurationSec = all.sumOf { it.durationSec },
                totalOpenCount = totalOpen,
                totalOpenWinRate = openWinRate,
                growthCurve = curve
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun updateNickname(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        app.settings.nickname = trimmed
        _nickname.value = trimmed
    }

    companion object {
        /** 秒数 -> "X小时X分X秒" */
        fun formatDuration(sec: Long): String {
            val s = sec.coerceAtLeast(0)
            val h = s / 3600
            val m = s % 3600 / 60
            val r = s % 60
            return buildString {
                if (h > 0) append("${h}小时")
                if (m > 0) append("${m}分")
                append("${r}秒")
            }
        }

        /** 持仓 bar 数 -> "X年X个月X天" */
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
