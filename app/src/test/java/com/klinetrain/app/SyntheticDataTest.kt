package com.klinetrain.app

import com.klinetrain.app.data.SyntheticData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * SyntheticData 兜底数据质量测试:
 * 1. 不得产生未来K线(裁剪到当前日期);
 * 2. 加密合成按UTC对齐(与 CryptoApi 真实行情一致);
 * 3. 同种子确定性。
 */
class SyntheticDataTest {

    private val dayMs = 86_400_000L

    @Test
    fun `A股合成数据不含未来K线`() {
        val bars = SyntheticData.generate("sh600519", 1600)
        assertTrue(bars.isNotEmpty())
        val now = System.currentTimeMillis()
        assertTrue("合成K线不应包含未来日期", bars.all { it.time <= now })
        // 升序且无重复日期
        val times = bars.map { it.time }
        assertEquals(times.sorted(), times)
        assertEquals(times.size, times.distinct().size)
        // 最后一根应是最近一个不晚于今天的交易日
        assertTrue(bars.last().time >= now - 10L * dayMs)
    }

    @Test
    fun `加密合成数据按UTC零点对齐`() {
        val bars = SyntheticData.generateCrypto("BTCUSDT", 1600)
        assertTrue(bars.isNotEmpty())
        val utc = TimeZone.getTimeZone("UTC")
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
        for (b in bars) {
            assertTrue("time应为UTC零点", b.time % dayMs == 0L)
            assertEquals("label应为UTC日期", fmt.format(b.time), b.label)
        }
        // 7天连续(不跳周末)
        for (i in 1 until bars.size) {
            assertEquals(dayMs, bars[i].time - bars[i - 1].time)
        }
    }

    @Test
    fun `同种子确定性一致`() {
        val a = SyntheticData.generate("sz000001", 300)
        val b = SyntheticData.generate("sz000001", 300)
        assertEquals(a, b)
        val c = SyntheticData.generateCrypto("ETHUSDT", 300)
        val d = SyntheticData.generateCrypto("ETHUSDT", 300)
        assertEquals(c, d)
    }
}
