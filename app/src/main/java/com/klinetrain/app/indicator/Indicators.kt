package com.klinetrain.app.indicator

import com.klinetrain.app.data.model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 技术指标纯函数计算。所有返回序列与输入等长，暖机期(数据不足)为 null。
 */
object Indicators {

    fun ma(values: List<Double>, n: Int): List<Double?> {
        if (n <= 0) return values.map { null }
        val out = arrayOfNulls<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= n) sum -= values[i - n]
            if (i >= n - 1) out[i] = sum / n
        }
        return out.toList()
    }

    fun ema(values: List<Double>, n: Int): List<Double?> {
        if (values.isEmpty() || n <= 0) return values.map { null }
        val out = arrayOfNulls<Double>(values.size)
        val k = 2.0 / (n + 1)
        var prev = values[0]
        out[0] = prev
        for (i in 1 until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out.toList()
    }

    /** 威廉SMA: y = (x*m + y'*(n-m))/n */
    fun smaTdx(values: List<Double?>, n: Int, m: Int): List<Double?> {
        val out = arrayOfNulls<Double>(values.size)
        var prev: Double? = null
        for (i in values.indices) {
            val x = values[i] ?: continue
            prev = if (prev == null) x else (x * m + prev * (n - m)) / n
            out[i] = prev
        }
        return out.toList()
    }

    data class MacdResult(val dif: List<Double?>, val dea: List<Double?>, val hist: List<Double?>)

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): MacdResult {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val dif = closes.indices.map { i ->
            val f = emaFast[i]; val s = emaSlow[i]
            if (f != null && s != null) f - s else null
        }
        val difNonNull = dif.map { it ?: 0.0 }
        val dea = ema(difNonNull, signal)
        val hist = closes.indices.map { i ->
            val d = dif[i]; val e = dea[i]
            if (d != null && e != null) (d - e) * 2 else null
        }
        return MacdResult(dif, dea, hist)
    }

    data class BollResult(val mid: List<Double?>, val upper: List<Double?>, val lower: List<Double?>)

    fun boll(closes: List<Double>, n: Int = 20, p: Double = 2.0): BollResult {
        val mid = ma(closes, n)
        val upper = arrayOfNulls<Double>(closes.size)
        val lower = arrayOfNulls<Double>(closes.size)
        for (i in closes.indices) {
            val m = mid[i] ?: continue
            var sum = 0.0
            for (j in i - n + 1..i) {
                val d = closes[j] - m
                sum += d * d
            }
            val std = sqrt(sum / n)
            upper[i] = m + p * std
            lower[i] = m - p * std
        }
        return BollResult(mid, upper.toList(), lower.toList())
    }

    data class KdjResult(val k: List<Double?>, val d: List<Double?>, val j: List<Double?>)

    fun kdj(klines: List<Kline>, n: Int = 9, m1: Int = 3, m2: Int = 3): KdjResult {
        val size = klines.size
        val rsv = arrayOfNulls<Double>(size)
        for (i in 0 until size) {
            val from = max(0, i - n + 1)
            var hh = Double.MIN_VALUE
            var ll = Double.MAX_VALUE
            for (j in from..i) {
                hh = max(hh, klines[j].high)
                ll = min(ll, klines[j].low)
            }
            rsv[i] = if (hh > ll) (klines[i].close - ll) / (hh - ll) * 100 else 50.0
        }
        val k = smaTdx(rsv.toList(), m1, 1)
        val d = smaTdx(k, m2, 1)
        val j = (0 until size).map { i ->
            val kv = k[i]; val dv = d[i]
            if (kv != null && dv != null) 3 * kv - 2 * dv else null
        }
        return KdjResult(k, d, j)
    }

    fun rsi(closes: List<Double>, n: Int = 14): List<Double?> {
        if (closes.size < 2) return closes.map { null }
        val up = arrayOfNulls<Double>(closes.size)
        val dn = arrayOfNulls<Double>(closes.size)
        for (i in 1 until closes.size) {
            val chg = closes[i] - closes[i - 1]
            up[i] = max(chg, 0.0)
            dn[i] = abs(min(chg, 0.0))
        }
        val avgUp = smaTdx(up.toList(), n, 1)
        val avgDn = smaTdx(dn.toList(), n, 1)
        return closes.indices.map { i ->
            val u = avgUp[i]; val d = avgDn[i]
            if (u != null && d != null && u + d > 0) u / (u + d) * 100 else null
        }
    }

    fun atr(klines: List<Kline>, n: Int = 14): List<Double?> {
        if (klines.isEmpty()) return emptyList()
        val tr = klines.indices.map { i ->
            if (i == 0) klines[0].high - klines[0].low
            else {
                val prev = klines[i - 1].close
                max(
                    klines[i].high - klines[i].low,
                    max(abs(klines[i].high - prev), abs(klines[i].low - prev))
                )
            }
        }
        return smaTdx(tr.map { it as Double? }, n, 1)
    }

    fun cci(klines: List<Kline>, n: Int = 14): List<Double?> {
        val tp = klines.map { (it.high + it.low + it.close) / 3 }
        val maTp = ma(tp, n)
        return klines.indices.map { i ->
            val m = maTp[i] ?: return@map null
            var dev = 0.0
            for (j in i - n + 1..i) dev += abs(tp[j] - m)
            dev /= n
            if (dev == 0.0) 0.0 else (tp[i] - m) / (0.015 * dev)
        }
    }

    // ---------------- 筹码分布 ----------------

    data class ChipDistribution(
        val prices: List<Double>,   // 每个价格档
        val weights: List<Double>,  // 对应筹码量(归一化)
        val profitRatio: Double,    // 获利盘比例 0..1
        val avgCost: Double         // 平均成本
    )

    /**
     * 基于历史成交量的三角形分布筹码算法。
     * @param uptoIndex 计算截止的bar下标(含)
     */
    fun chipDistribution(klines: List<Kline>, uptoIndex: Int, bins: Int = 240, decay: Double = 1.0): ChipDistribution? {
        if (klines.isEmpty() || uptoIndex < 0) return null
        val end = min(uptoIndex, klines.size - 1)
        val from = max(0, end - 250)
        var lo = Double.MAX_VALUE
        var hi = Double.MIN_VALUE
        for (i in from..end) {
            lo = min(lo, klines[i].low)
            hi = max(hi, klines[i].high)
        }
        if (hi <= lo) return null
        val step = (hi - lo) / bins
        val weights = DoubleArray(bins + 1)
        // bins随屏幕像素自适应(可达上千), 形状缓冲复用一份, 不每根K线重新分配
        val shapes = DoubleArray(bins + 1)
        for (i in from..end) {
            val k = klines[i]
            val age = end - i
            val w = k.volume * Math.pow(decay, age.toDouble())
            // 在 low..high 间三角形分布，峰值在 avg=(h+l+2c)/4
            val peak = (k.high + k.low + 2 * k.close) / 4
            val lowBin = ((k.low - lo) / step).toInt().coerceIn(0, bins)
            val highBin = ((k.high - lo) / step).toInt().coerceIn(0, bins)
            if (highBin == lowBin) {
                weights[lowBin] += w
            } else {
                var totalShape = 0.0
                for (b in lowBin..highBin) {
                    val price = lo + b * step
                    val h = k.high; val l = k.low
                    val shape = if (price <= peak) {
                        if (peak - l <= 0.0) 1.0 else (price - l) / (peak - l)
                    } else {
                        if (h - peak <= 0.0) 1.0 else (h - price) / (h - peak)
                    }.coerceAtLeast(0.0)
                    shapes[b - lowBin] = shape
                    totalShape += shape
                }
                if (totalShape > 0) {
                    for (b in lowBin..highBin) weights[b] += w * shapes[b - lowBin] / totalShape
                }
            }
        }
        val total = weights.sum()
        if (total <= 0) return null
        val cur = klines[end].close
        var profit = 0.0
        var costSum = 0.0
        val prices = ArrayList<Double>(bins + 1)
        for (b in 0..bins) {
            val price = lo + b * step
            prices.add(price)
            if (price <= cur) profit += weights[b]
            costSum += price * weights[b]
        }
        return ChipDistribution(
            prices = prices,
            weights = weights.map { it / total },
            profitRatio = profit / total,
            avgCost = costSum / total
        )
    }
}
