package com.klinetrain.app

import com.klinetrain.app.data.KlineUtils
import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.TimeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class KlineUtilsTest {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }

    private fun t(date: String): Long = fmt.parse(date)!!.time

    private fun k(date: String, o: Double, h: Double, l: Double, c: Double, v: Double): Kline =
        Kline(time = t(date), label = date, open = o, high = h, low = l, close = c, volume = v)

    // ---------- aggregate ----------

    @Test
    fun `周K按自然周聚合`() {
        // 2024-01-04(周四) 2024-01-05(周五) | 2024-01-08(周一) 2024-01-09(周二)
        val daily = listOf(
            k("2024-01-04", 10.0, 12.0, 9.0, 11.0, 100.0),
            k("2024-01-05", 11.0, 13.0, 10.0, 12.0, 200.0),
            k("2024-01-08", 12.0, 14.0, 11.0, 13.0, 300.0),
            k("2024-01-09", 13.0, 15.0, 12.0, 12.5, 400.0)
        )
        val weekly = KlineUtils.aggregate(daily, TimeFrame.WEEK)
        assertEquals(2, weekly.size)

        val w1 = weekly[0]
        assertEquals(10.0, w1.open, 1e-9)
        assertEquals(12.0, w1.close, 1e-9)
        assertEquals(13.0, w1.high, 1e-9)
        assertEquals(9.0, w1.low, 1e-9)
        assertEquals(300.0, w1.volume, 1e-9)
        assertEquals("2024-01-05", w1.label)
        assertEquals(t("2024-01-04"), w1.time)

        val w2 = weekly[1]
        assertEquals(12.0, w2.open, 1e-9)
        assertEquals(12.5, w2.close, 1e-9)
        assertEquals(15.0, w2.high, 1e-9)
        assertEquals(11.0, w2.low, 1e-9)
        assertEquals(700.0, w2.volume, 1e-9)
        assertEquals("2024-01-09", w2.label)
    }

    @Test
    fun `月K按自然月聚合`() {
        val daily = listOf(
            k("2024-01-30", 20.0, 22.0, 19.0, 21.0, 100.0),
            k("2024-01-31", 21.0, 23.0, 20.0, 22.0, 150.0),
            k("2024-02-01", 22.0, 25.0, 21.5, 24.0, 250.0)
        )
        val monthly = KlineUtils.aggregate(daily, TimeFrame.MONTH)
        assertEquals(2, monthly.size)

        val m1 = monthly[0]
        assertEquals(20.0, m1.open, 1e-9)
        assertEquals(22.0, m1.close, 1e-9)
        assertEquals(23.0, m1.high, 1e-9)
        assertEquals(19.0, m1.low, 1e-9)
        assertEquals(250.0, m1.volume, 1e-9)
        assertEquals("2024-01-31", m1.label)

        val m2 = monthly[1]
        assertEquals(22.0, m2.open, 1e-9)
        assertEquals(24.0, m2.close, 1e-9)
        assertEquals("2024-02-01", m2.label)
    }

    @Test
    fun `日K原样返回且分钟周期返回daily`() {
        val daily = listOf(
            k("2024-01-04", 10.0, 12.0, 9.0, 11.0, 100.0),
            k("2024-01-05", 11.0, 13.0, 10.0, 12.0, 200.0)
        )
        assertEquals(daily, KlineUtils.aggregate(daily, TimeFrame.DAY))
        assertEquals(daily, KlineUtils.aggregate(daily, TimeFrame.MIN5))
        assertTrue(KlineUtils.aggregate(emptyList(), TimeFrame.WEEK).isEmpty())
    }

    // ---------- synthesizeMinuteBars ----------

    private val day = k("2024-03-15", 10.0, 11.0, 9.5, 10.5, 1_000_000.0)

    @Test
    fun `1分钟合成240根且端点与极值精确匹配`() {
        val bars = KlineUtils.synthesizeMinuteBars(day, 9.8, 1)
        assertEquals(240, bars.size)
        assertEquals(day.open, bars.first().open, 1e-6)
        assertEquals(day.close, bars.last().close, 1e-6)
        assertEquals(day.high, bars.maxOf { it.high }, 1e-6)
        assertEquals(day.low, bars.minOf { it.low }, 1e-6)
    }

    @Test
    fun `合成bar的OHLC自洽且价格在日内高低之间`() {
        val bars = KlineUtils.synthesizeMinuteBars(day, 9.8, 1)
        for (b in bars) {
            assertTrue("high应不小于open/close", b.high >= max(b.open, b.close) - 1e-9)
            assertTrue("low应不大于open/close", b.low <= min(b.open, b.close) + 1e-9)
            assertTrue("high不越过日高", b.high <= day.high + 1e-9)
            assertTrue("low不越过日低", b.low >= day.low - 1e-9)
        }
    }

    @Test
    fun `分时label按A股交易时段生成`() {
        val bars = KlineUtils.synthesizeMinuteBars(day, 9.8, 1)
        assertEquals("09:31", bars[0].label)
        assertEquals("11:30", bars[119].label)
        assertEquals("13:01", bars[120].label)
        assertEquals("15:00", bars[239].label)
    }

    @Test
    fun `成交量按变动加权分配且总和守恒`() {
        val bars = KlineUtils.synthesizeMinuteBars(day, 9.8, 1)
        val sum = bars.sumOf { it.volume }
        assertTrue(abs(sum - day.volume) < day.volume * 1e-6 + 1.0)
        assertTrue(bars.all { it.volume >= 0.0 })
    }

    @Test
    fun `同种子确定性一致`() {
        val a = KlineUtils.synthesizeMinuteBars(day, 9.8, 1)
        val b = KlineUtils.synthesizeMinuteBars(day, 9.8, 1)
        assertEquals(a, b)
    }

    @Test
    fun `5分钟聚合为48根且保持端点与极值`() {
        val bars5 = KlineUtils.synthesizeMinuteBars(day, 9.8, 5)
        assertEquals(48, bars5.size)
        assertEquals(day.open, bars5.first().open, 1e-6)
        assertEquals(day.close, bars5.last().close, 1e-6)
        assertEquals(day.high, bars5.maxOf { it.high }, 1e-6)
        assertEquals(day.low, bars5.minOf { it.low }, 1e-6)
        assertEquals("09:35", bars5[0].label)
        assertEquals("15:00", bars5[47].label)
        val sum = bars5.sumOf { it.volume }
        assertTrue(abs(sum - day.volume) < day.volume * 1e-6 + 1.0)
    }

    @Test
    fun `60分钟聚合为4根`() {
        val bars60 = KlineUtils.synthesizeMinuteBars(day, 9.8, 60)
        assertEquals(4, bars60.size)
        assertEquals(day.open, bars60.first().open, 1e-6)
        assertEquals(day.close, bars60.last().close, 1e-6)
    }

    @Test
    fun `平盘日不崩溃`() {
        val flat = k("2024-03-18", 8.0, 8.0, 8.0, 8.0, 500_000.0)
        val bars = KlineUtils.synthesizeMinuteBars(flat, 8.0, 1)
        assertEquals(240, bars.size)
        assertTrue(bars.all { abs(it.open - 8.0) < 1e-9 && abs(it.close - 8.0) < 1e-9 })
        assertTrue(abs(bars.sumOf { it.volume } - 500_000.0) < 1.0)
    }
}
