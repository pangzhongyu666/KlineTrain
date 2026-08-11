package com.klinetrain.app.data

import com.klinetrain.app.data.model.Kline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 确定性离线合成日K数据（网络与缓存均失败时的兜底）。
 * 以 symbol.hashCode() 为随机种子，同一 symbol 每次生成的数据完全一致。
 * 特征：几何随机游走 + 牛/熊/震荡区间切换 + 偶尔跳空 + 连续涨停趋势段，
 * 涨跌幅受 ±10% 涨跌停约束，成交量与波动正相关。
 */
object SyntheticData {

    private val TZ = TimeZone.getTimeZone("Asia/Shanghai")

    fun generate(symbol: String, count: Int = 1600): List<Kline> {
        if (count <= 0) return emptyList()
        val rnd = Random(symbol.hashCode().toLong())
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TZ }
        val cal = Calendar.getInstance(TZ).apply {
            clear()
            set(2022, Calendar.JANUARY, 4, 0, 0, 0)
        }

        var prevClose = r2(5.0 + rnd.nextDouble() * 75.0)   // 起始价 5~80
        var drift = 0.0
        var vol = 0.016
        var regimeLeft = 0
        var limitStreak = 0                                  // 剩余一字涨停天数
        val baseVolume = 3_000_000.0 + rnd.nextDouble() * 20_000_000.0

        val out = ArrayList<Kline>(count)
        while (out.size < count) {
            // 跳过周末
            while (true) {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                } else break
            }

            // 趋势区间切换：牛/熊/震荡，每 30~80 根切换一次
            if (regimeLeft <= 0) {
                when (rnd.nextInt(3)) {
                    0 -> { // 牛
                        drift = 0.0035 + rnd.nextDouble() * 0.003
                        vol = 0.016 + rnd.nextDouble() * 0.010
                    }
                    1 -> { // 熊
                        drift = -(0.0030 + rnd.nextDouble() * 0.003)
                        vol = 0.018 + rnd.nextDouble() * 0.012
                    }
                    else -> { // 震荡
                        drift = (rnd.nextDouble() - 0.5) * 0.0012
                        vol = 0.010 + rnd.nextDouble() * 0.008
                    }
                }
                regimeLeft = 30 + rnd.nextInt(51)
            }
            regimeLeft--

            val open: Double
            val close: Double
            val high: Double
            val low: Double
            val volume: Double

            if (limitStreak > 0) {
                // 连板：一字涨停
                limitStreak--
                val c = r2(prevClose * 1.10)
                close = c
                open = c
                high = c
                low = if (rnd.nextDouble() < 0.25) r2(max(0.05, c * 0.992)) else c
                volume = r2(baseVolume * (0.15 + rnd.nextDouble() * 0.35))
            } else if (drift > 0.002 && rnd.nextDouble() < 0.012) {
                // 首板涨停：放量拉板，之后连板 1~3 天
                limitStreak = 1 + rnd.nextInt(3)
                val c = r2(prevClose * 1.10)
                var o = prevClose * (1.005 + rnd.nextDouble() * 0.04)
                o = o.coerceIn(prevClose * 0.902, c)
                open = r2(o)
                close = c
                high = c
                low = r2(max(0.05, min(open, prevClose * (1 - rnd.nextDouble() * 0.02))))
                volume = r2(baseVolume * (1.8 + rnd.nextDouble() * 1.5))
            } else {
                // 普通交易日
                val gap = if (rnd.nextDouble() < 0.08) rnd.nextGaussian() * 0.012 else 0.0
                var o = prevClose * (1 + gap + rnd.nextGaussian() * 0.002)
                o = o.coerceIn(prevClose * 0.902, prevClose * 1.098)
                val ret = drift + rnd.nextGaussian() * vol
                var c = prevClose * (1 + ret)
                c = c.coerceIn(prevClose * 0.90, prevClose * 1.10)
                val range = prevClose * vol * (0.4 + rnd.nextDouble() * 1.2)
                var h = max(o, c) + rnd.nextDouble() * range * 0.6
                var l = min(o, c) - rnd.nextDouble() * range * 0.6
                h = min(h, prevClose * 1.10)
                l = max(l, prevClose * 0.90)
                l = max(l, 0.05)
                open = r2(o)
                close = r2(c)
                high = r2(max(h, max(open, close)))
                low = r2(min(l, min(open, close)))
                volume = r2(
                    baseVolume * (0.5 + rnd.nextDouble()) *
                        (1 + 12 * abs(close / prevClose - 1))
                )
            }

            out.add(
                Kline(
                    time = cal.timeInMillis,
                    label = fmt.format(cal.time),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    amount = r2(volume * (open + close) / 2.0)
                )
            )
            prevClose = close
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    /**
     * 确定性合成加密货币日K（种子=symbol.hashCode()，同一 symbol 结果恒定）。
     * 特征：几何随机游走 σ≈4%/日 + 牛/熊/震荡区间切换 + 偶发±10%~25%极端日，
     * 无涨跌停限制；7天连续(不跳周末)；日期从 2022-01-01 起；成交量与波动正相关。
     * @param symbol 如 "BTCUSDT" 或 "BTC"（自动识别BTC/ETH起价区间）
     */
    fun generateCrypto(symbol: String, count: Int = 1600): List<Kline> {
        if (count <= 0) return emptyList()
        val rnd = Random(symbol.hashCode().toLong())
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TZ }
        val cal = Calendar.getInstance(TZ).apply {
            clear()
            set(2022, Calendar.JANUARY, 1, 0, 0, 0)
        }

        val base = symbol.trim().uppercase(Locale.US).removeSuffix("USDT")
        var prevClose = when (base) {
            "BTC" -> 20000.0 + rnd.nextDouble() * 60000.0            // 2万~8万
            "ETH" -> 1000.0 + rnd.nextDouble() * 3000.0              // 1千~4千
            else -> {                                                 // 对数均匀 0.05~2000
                val logMin = Math.log(0.05)
                val logMax = Math.log(2000.0)
                Math.exp(logMin + (logMax - logMin) * rnd.nextDouble())
            }
        }
        prevClose = rp(prevClose)
        // 基准成交量(币)：按名义额约2亿~20亿USDT/日反推，避免高价币量纲失真
        val baseVolume = (2e8 + rnd.nextDouble() * 1.8e9) / max(prevClose, 1e-6)

        var drift = 0.0
        var vol = 0.04
        var regimeLeft = 0

        val out = ArrayList<Kline>(count)
        while (out.size < count) {
            // 牛/熊/震荡区间切换，每 30~80 根切换一次
            if (regimeLeft <= 0) {
                when (rnd.nextInt(3)) {
                    0 -> { // 牛
                        drift = 0.004 + rnd.nextDouble() * 0.006
                        vol = 0.030 + rnd.nextDouble() * 0.020
                    }
                    1 -> { // 熊
                        drift = -(0.004 + rnd.nextDouble() * 0.006)
                        vol = 0.035 + rnd.nextDouble() * 0.025
                    }
                    else -> { // 震荡
                        drift = (rnd.nextDouble() - 0.5) * 0.002
                        vol = 0.025 + rnd.nextDouble() * 0.015
                    }
                }
                regimeLeft = 30 + rnd.nextInt(51)
            }
            regimeLeft--

            // 日收益：几何随机游走，偶发极端日(±10%~25%)，无涨跌停
            var ret = drift + rnd.nextGaussian() * vol
            if (rnd.nextDouble() < 0.015) {
                val sign = if (rnd.nextDouble() < 0.45) 1.0 else -1.0
                ret = sign * (0.10 + rnd.nextDouble() * 0.15)
            }
            ret = ret.coerceIn(-0.25, 0.25)

            // 7x24连续交易：开盘≈昨收(微小误差)
            val o = max(prevClose * (1 + rnd.nextGaussian() * 0.003), 1e-6)
            val c = max(prevClose * (1 + ret), 1e-6)
            val open = rp(o)
            val close = rp(c)

            // 影线：与当日波动同量级，下影不超过实体低点的25%
            val range = prevClose * vol * (0.5 + rnd.nextDouble() * 1.5)
            val hRaw = max(open, close) + rnd.nextDouble() * range * 0.6
            var lRaw = min(open, close) - rnd.nextDouble() * range * 0.6
            lRaw = max(lRaw, min(open, close) * 0.75)
            lRaw = max(lRaw, 1e-6)
            val high = max(rp(hRaw), max(open, close))
            val low = min(rp(lRaw), min(open, close))

            val volume = r2(
                baseVolume * (0.5 + rnd.nextDouble()) *
                    (1 + 8 * abs(close / max(prevClose, 1e-6) - 1))
            )

            out.add(
                Kline(
                    time = cal.timeInMillis,
                    label = fmt.format(cal.time),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    amount = r2(volume * (open + close) / 2.0)
                )
            )
            prevClose = close
            cal.add(Calendar.DAY_OF_MONTH, 1)   // 不跳周末，7天连续
        }
        return out
    }

    /** 加密价格取整：高价2位、中价4位、低价6位小数，保留低价币精度 */
    private fun rp(x: Double): Double = when {
        x >= 100 -> Math.round(x * 100.0) / 100.0
        x >= 1 -> Math.round(x * 10000.0) / 10000.0
        else -> Math.round(x * 1_000_000.0) / 1_000_000.0
    }

    private fun r2(x: Double): Double = Math.round(x * 100.0) / 100.0
}
