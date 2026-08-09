package com.klinetrain.app.engine

import com.klinetrain.app.data.model.Direction
import com.klinetrain.app.data.model.SessionResult
import com.klinetrain.app.data.model.TrainingConfig
import kotlin.math.abs
import kotlin.math.max

/** 一笔持仓(一仓) */
data class Lot(
    val direction: Direction,
    val entryPrice: Double,
    val entryBarIndex: Int,
    val margin: Double,     // 占用保证金
    val shares: Double      // 持有股数(含杠杆敞口)
) {
    fun pnl(price: Double): Double = when (direction) {
        Direction.LONG -> shares * (price - entryPrice)
        Direction.SHORT -> shares * (entryPrice - price)
    }
}

/** 已完成的一次开平记录(用于统计) */
data class ClosedTrade(
    val direction: Direction,
    val entryPrice: Double,
    val exitPrice: Double,
    val entryBarIndex: Int,
    val exitBarIndex: Int,
    val pnl: Double
)

/**
 * 分仓交易引擎：5仓分仓、多空双向、杠杆、手续费。
 * 价格推进通过 [onBarClose] 通知，每根bar调用一次。
 */
class TradingEngine(private val config: TrainingConfig) {

    var cash: Double = config.initialCash
        private set
    val lots = mutableListOf<Lot>()
    val closedTrades = mutableListOf<ClosedTrade>()

    private val equityCurve = mutableListOf(config.initialCash)
    private var peakEquity = config.initialCash
    var maxDrawdownPct = 0.0
        private set
    private var barsElapsed = 0
    private var holdBars = 0
    private var heavyBars = 0
    var bankrupt = false
        private set

    val usedSlots: Int get() = lots.size
    val freeSlots: Int get() = config.totalSlots - lots.size
    val currentDirection: Direction? get() = lots.firstOrNull()?.direction

    fun equity(price: Double): Double = cash + lots.sumOf { it.margin + it.pnl(price) }

    fun positionRatio(price: Double): Double {
        val e = equity(price)
        return if (e <= 0) 1.0 else lots.sumOf { it.margin + it.pnl(price) } / e
    }

    fun equityCurveSnapshot(): List<Double> = equityCurve.toList()

    /** 开一仓。同一时刻只允许一个方向。返回是否成功 */
    fun open(direction: Direction, price: Double, barIndex: Int): Boolean {
        if (bankrupt || price <= 0) return false
        if (freeSlots <= 0) return false
        if (lots.isNotEmpty() && lots[0].direction != direction) return false
        if (!config.allowShort && direction == Direction.SHORT) return false
        // 手续费会让现金略少于 equity/slots，最后一仓按剩余现金开，避免永远开不满
        val margin = minOf(equity(price) / config.totalSlots, cash)
        if (margin <= 0) return false
        val exposure = margin * config.leverage
        val fee = exposure * config.feeRate
        if (fee >= margin) return false
        cash -= margin
        cash -= fee
        lots.add(Lot(direction, price, barIndex, margin, exposure / price))
        return true
    }

    /** 平最早的一仓(FIFO)。返回是否成功 */
    fun closeOne(price: Double, barIndex: Int): Boolean {
        if (lots.isEmpty() || price <= 0) return false
        val lot = lots.removeAt(0)
        val pnl = lot.pnl(price)
        val fee = lot.shares * price * config.feeRate
        cash += lot.margin + pnl - fee
        closedTrades.add(
            ClosedTrade(lot.direction, lot.entryPrice, price, lot.entryBarIndex, barIndex, pnl - fee)
        )
        return true
    }

    fun closeAll(price: Double, barIndex: Int) {
        while (lots.isNotEmpty()) closeOne(price, barIndex)
    }

    /** 每根bar收盘时调用，记录权益曲线与统计 */
    fun onBarClose(closePrice: Double) {
        barsElapsed++
        if (lots.isNotEmpty()) {
            holdBars++
            if (lots.size >= 3) heavyBars++
        }
        val e = equity(closePrice)
        equityCurve.add(e)
        peakEquity = max(peakEquity, e)
        if (peakEquity > 0) {
            val dd = (peakEquity - e) / peakEquity * 100
            maxDrawdownPct = max(maxDrawdownPct, dd)
        }
        if (e <= config.initialCash * 0.05) {
            bankrupt = true
            closeAll(closePrice, barsElapsed)
        }
    }

    /** 结束训练并结算(强制平仓) */
    fun settle(lastPrice: Double, barIndex: Int, intervalChangePct: Double): SessionResult {
        closeAll(lastPrice, barIndex)
        val finalEquity = equity(lastPrice)
        val returnPct = (finalEquity - config.initialCash) / config.initialCash * 100
        val wins = closedTrades.filter { it.pnl > 0 }
        val losses = closedTrades.filter { it.pnl < 0 }
        val avgWin = if (wins.isNotEmpty()) wins.sumOf { it.pnl } / wins.size else 0.0
        val avgLoss = if (losses.isNotEmpty()) abs(losses.sumOf { it.pnl }) / losses.size else 0.0
        val plRatio = when {
            losses.isEmpty() && wins.isEmpty() -> 0.0
            losses.isEmpty() -> 999.0
            avgLoss == 0.0 -> 999.0
            else -> avgWin / avgLoss
        }
        return SessionResult(
            returnPct = returnPct,
            intervalChangePct = intervalChangePct,
            outperformPct = returnPct - intervalChangePct,
            openCount = closedTrades.size,
            openWinRate = if (closedTrades.isEmpty()) 0.0 else wins.size * 100.0 / closedTrades.size,
            profitLossRatio = plRatio,
            maxDrawdownPct = maxDrawdownPct,
            holdRatio = if (barsElapsed == 0) 0.0 else holdBars * 100.0 / barsElapsed,
            heavyRatio = if (barsElapsed == 0) 0.0 else heavyBars * 100.0 / barsElapsed,
            holdBars = holdBars,
            bankrupt = bankrupt,
            richOnce = returnPct >= 50.0
        )
    }
}
