package com.klinetrain.app.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klinetrain.app.KlineTrainApp
import com.klinetrain.app.data.db.StrategyEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 战法 + 该战法下记录的统计 */
data class StrategyWithStats(
    val strategy: StrategyEntity,
    val sessionCount: Int,       // 场次
    val winRate: Double,         // 胜率(%): returnPct>0 的比例
    val avgReturnPct: Double,    // 平均收益(%)
    val avgProfitLossRatio: Double // 盈亏比均值
)

class StrategyViewModel : ViewModel() {

    private val app = KlineTrainApp.instance
    private val strategyDao = app.database.strategyDao()
    private val recordDao = app.database.trainingRecordDao()

    val strategies: StateFlow<List<StrategyWithStats>> = combine(
        strategyDao.observeAll(),
        recordDao.observeAll()
    ) { strategies, records ->
        strategies.map { s ->
            val list = records.filter { it.strategyId == s.id }
            val count = list.size
            StrategyWithStats(
                strategy = s,
                sessionCount = count,
                winRate = if (count == 0) 0.0 else list.count { it.returnPct > 0 } * 100.0 / count,
                avgReturnPct = if (count == 0) 0.0 else list.sumOf { it.returnPct } / count,
                avgProfitLossRatio = if (count == 0) 0.0 else list.sumOf { it.profitLossRatio } / count
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addStrategy(name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            strategyDao.insert(StrategyEntity(name = trimmed, description = description.trim()))
        }
    }

    fun updateStrategy(strategy: StrategyEntity, name: String, description: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            strategyDao.update(strategy.copy(name = trimmed, description = description.trim()))
        }
    }

    fun deleteStrategy(strategy: StrategyEntity) {
        viewModelScope.launch { strategyDao.delete(strategy) }
    }
}
