package com.klinetrain.app.data

import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.TimeFrame
import java.util.Calendar
import java.util.Random
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** K线聚合与分时合成工具。 */
object KlineUtils {

    private val TZ = TimeZone.getTimeZone("Asia/Shanghai")
    private const val DAY_MS = 86_400_000L
    private const val MINUTES = 240 // A股全天240分钟

    /**
     * 日K聚合：WEEK 按自然周(ISO, 周一起始)、MONTH 按自然月聚合；
     * DAY 原样返回；其他周期返回 daily。
     * 聚合规则：open取首根、close取末根、high/low取极值、volume/amount求和、label取末根日期。
     */
    fun aggregate(daily: List<Kline>, tf: TimeFrame): List<Kline> {
        if (daily.isEmpty()) return daily
        return when (tf) {
            TimeFrame.WEEK -> mergeBy(daily) { weekKey(it.time) }
            TimeFrame.MONTH -> mergeBy(daily) { monthKey(it.time) }
            else -> daily
        }
    }

    private fun weekKey(time: Long): Long {
        val cal = Calendar.getInstance(TZ)
        cal.timeInMillis = time
        // Calendar.MONDAY=2 -> 0, ..., SUNDAY=1 -> 6
        val idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        // 同一自然周内所有交易日回退到同一个周一(中国无夏令时，整天数平移安全)
        return Math.floorDiv(time - idx * DAY_MS, DAY_MS)
    }

    private fun monthKey(time: Long): Long {
        val cal = Calendar.getInstance(TZ)
        cal.timeInMillis = time
        return cal.get(Calendar.YEAR) * 100L + cal.get(Calendar.MONTH)
    }

    private fun mergeBy(daily: List<Kline>, key: (Kline) -> Long): List<Kline> {
        return daily.sortedBy { it.time }
            .groupBy(key)            // LinkedHashMap，保持时间顺序
            .values
            .map { merge(it) }
    }

    private fun merge(group: List<Kline>): Kline {
        val first = group.first()
        val last = group.last()
        return Kline(
            time = first.time,
            label = last.label,
            open = first.open,
            high = group.maxOf { it.high },
            low = group.minOf { it.low },
            close = last.close,
            volume = group.sumOf { it.volume },
            amount = group.sumOf { it.amount }
        )
    }

    /**
     * 由一根日K确定性地合成分时bar（以 day.time xor seedSalt 为随机种子）。
     * stepMinutes=1 时返回240根1分钟bar，label为 09:31..11:30, 13:01..15:00；
     * 5/15/30/60 按步长聚合。
     * 保证：首bar open == day.open，末bar close == day.close，
     * max(high) == day.high，min(low) == day.low，OHLC 自洽。
     * @param prevClose 昨收，用于展示涨跌参考，路径合成不依赖它。
     * @param seedSalt  调用方盐值。同一(day, salt)下完全确定；训练时每局换盐，
     *                  防止用公开算法离线重放 Random(day.time) 反解当日高低点(未来函数)。
     */
    fun synthesizeMinuteBars(day: Kline, prevClose: Double, stepMinutes: Int, seedSalt: Long = 0L): List<Kline> {
        val rnd = Random(day.time xor seedSalt)
        val hi = maxOf(day.high, day.open, day.close)
        val lo = minOf(day.low, day.open, day.close)
        val open = day.open.coerceIn(lo, hi)
        val close = day.close.coerceIn(lo, hi)

        // 1) 生成 241 个价格路径点 p[0..240]，p[0]=open, p[240]=close，且触及 hi/lo
        val p = DoubleArray(MINUTES + 1)
        if (hi - lo < 1e-12) {
            java.util.Arrays.fill(p, open)
        } else {
            // 随机选择触及高/低点的锚点位置（上涨日先低后高，下跌日先高后低）
            val a = 15 + rnd.nextInt(200)
            var b = 15 + rnd.nextInt(200)
            if (abs(a - b) < 10) b = if (a < 120) a + 30 else a - 30
            val early = min(a, b)
            val late = max(a, b)
            val iLow: Int
            val iHigh: Int
            if (close >= open) {
                iLow = early; iHigh = late
            } else {
                iHigh = early; iLow = late
            }
            val anchors = listOf(0 to open, iLow to lo, iHigh to hi, MINUTES to close)
                .sortedBy { it.first }
            val stepSigma = (hi - lo) * 0.8 / sqrt(MINUTES.toDouble())
            for (s in 0 until anchors.size - 1) {
                val (ia, va) = anchors[s]
                val (ib, vb) = anchors[s + 1]
                p[ia] = va
                p[ib] = vb
                val m = ib - ia
                if (m <= 1) continue
                // 布朗桥：随机游走减去线性趋势，端点归零
                val w = DoubleArray(m + 1)
                for (j in 1..m) w[j] = w[j - 1] + rnd.nextGaussian()
                for (j in 1 until m) {
                    val t = j.toDouble() / m
                    val bridge = w[j] - w[m] * t
                    val base = va + (vb - va) * t
                    // 越界时向区间内反射而非饱和截断: coerceIn会把连续多分钟钉死在
                    // 日内最高/最低价上, 形成价格与成交量完全不变的水平段
                    var v = base + bridge * stepSigma
                    if (v > hi) v = hi - (v - hi)
                    if (v < lo) v = lo + (lo - v)
                    p[ia + j] = v.coerceIn(lo, hi)
                }
            }
        }

        // 2) 成交量按 |价格变动| 加权分配。
        //    加入随机底噪与开收盘活跃的U型曲线: 平坦段(一字板/触及日内高低点)不再均分成交量
        val weights = DoubleArray(MINUTES)
        var sumW = 0.0
        val noiseBase = (hi - lo) + open * 0.002
        for (k in 0 until MINUTES) {
            val x = (k - MINUTES / 2.0) / (MINUTES / 2.0)   // -1..1
            val uShape = 0.6 + 1.4 * x * x
            weights[k] = (abs(p[k + 1] - p[k]) +
                noiseBase * (0.02 + 0.08 * rnd.nextDouble())) * uShape + 1e-9
            sumW += weights[k]
        }
        val dayVol = max(day.volume, 0.0)

        // 3) 组装240根1分钟bar
        val bars = ArrayList<Kline>(MINUTES)
        for (k in 1..MINUTES) {
            val o = p[k - 1]
            val c = p[k]
            var h = max(o, c)
            var l = min(o, c)
            // 小影线，严格不越过日内 hi/lo
            val wick = abs(c - o) * 0.25
            h = min(hi, h + rnd.nextDouble() * wick)
            l = max(lo, l - rnd.nextDouble() * wick)
            // 上午 09:31..11:30；下午 13:01..15:00（label为bar结束时刻）
            val labelMin = if (k <= 120) 9 * 60 + 30 + k else 13 * 60 + (k - 120)
            val startMin = labelMin - 1
            bars.add(
                Kline(
                    time = day.time + startMin * 60_000L,
                    label = String.format("%02d:%02d", labelMin / 60, labelMin % 60),
                    open = o,
                    high = h,
                    low = l,
                    close = c,
                    volume = dayVol * weights[k - 1] / sumW,
                    amount = dayVol * weights[k - 1] / sumW * (o + c) / 2.0
                )
            )
        }
        if (stepMinutes <= 1) return bars

        // 4) 按步长聚合(5/15/30/60均能整除240)
        return bars.chunked(stepMinutes).map { g ->
            Kline(
                time = g.first().time,
                label = g.last().label,
                open = g.first().open,
                high = g.maxOf { it.high },
                low = g.minOf { it.low },
                close = g.last().close,
                volume = g.sumOf { it.volume },
                amount = g.sumOf { it.amount }
            )
        }
    }
}
