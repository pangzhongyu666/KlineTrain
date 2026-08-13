package com.klinetrain.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一局训练记录 */
@Entity(tableName = "training_records")
data class TrainingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,               // TrainingMode.name
    val stockCode: String,
    val stockName: String,
    val market: String,
    val startLabel: String,         // 训练区间起始日期
    val endLabel: String,           // 训练区间结束日期
    val returnPct: Double,          // 本局收益%
    val intervalChangePct: Double,  // 区间涨跌幅%
    val outperformPct: Double,      // 跑赢区间%
    val openCount: Int,
    val openWinRate: Double,
    val profitLossRatio: Double,
    val maxDrawdownPct: Double,
    val holdRatio: Double,
    val heavyRatio: Double,
    val holdBars: Int,
    val bankrupt: Boolean,
    val richOnce: Boolean,
    val durationSec: Long,          // 训练耗时
    val leverage: Double,
    val strategyId: Long? = null,   // 归属战法
    val note: String = "",          // 复盘笔记
    val favorite: Boolean = false,
    val liked: Boolean = false,
    val seasonIndex: Int = 1,          // 所属赛季
    val seasonBase: Double = 0.0,      // 该局所属赛季的基准初始金额(爆竹曲线起点; 旧记录为0=未知)
    val endFirecrackers: Double = 0.0, // 本局结束后的爆竹数(重置前)
    val createdAt: Long = System.currentTimeMillis()
)

/** 一笔交易(开或平) */
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val barLabel: String,       // 交易发生的bar标签(日期或编号)
    val direction: String,      // Direction.name
    val isOpen: Boolean,        // 开仓/平仓
    val price: Double,
    val slots: Int,             // 本次操作仓数
    val pnl: Double? = null,    // 平仓盈亏
    val createdAt: Long = System.currentTimeMillis()
)

/** 战法 */
@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** K线网络缓存 */
@Entity(tableName = "kline_cache")
data class KlineCacheEntity(
    @PrimaryKey val symbol: String,   // sh600519
    val json: String,                 // List<Kline> 的JSON
    val updatedAt: Long
)

/** 自定义公式指标 */
@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,           // 公式文本
    val onMainChart: Boolean = false, // 叠加主图还是副图
    val createdAt: Long = System.currentTimeMillis()
)
