package com.klinetrain.app.data.net

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.klinetrain.app.data.model.Kline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * 加密货币日K行情(USDT计价现货)。
 * 依次尝试：Binance 三个镜像 host → OKX，每个源超时5秒，失败换下一个，全部失败返回 null。
 *
 * Binance: GET https://{host}/api/v3/klines?symbol=BTCUSDT&interval=1d&limit=N
 *   返回数组的数组: [openTime(ms), "open","high","low","close","volume", closeTime, "quoteVol", ...]
 * OKX:     GET https://www.okx.com/api/v5/market/candles?instId=BTC-USDT&bar=1D&limit=300
 *   返回 {"code":"0","data":[[ts毫秒字符串,o,h,l,c,vol,...],...]}，data 最新在前。
 */
object CryptoApi {

    private val BINANCE_HOSTS = listOf(
        "data-api.binance.vision",
        "api.binance.com",
        "api1.binance.com"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /** 加密货币日K按 UTC 对齐(币安日K开盘=00:00 UTC) */
    private fun utcFmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * 拉取日K。任何异常/超时/解析失败自动降级到下一个数据源，全失败返回 null。
     * @param baseSymbol 基础币符号，如 "BTC"（不带USDT后缀）
     * @param count      希望获取的K线数量（Binance上限1000，OKX固定300）
     */
    suspend fun fetchDailyKlines(baseSymbol: String, count: Int): List<Kline>? =
        withContext(Dispatchers.IO) {
            val base = baseSymbol.trim().uppercase(Locale.US).removeSuffix("USDT")
            if (base.isEmpty()) return@withContext null
            for (host in BINANCE_HOSTS) {
                val list = tryBinance(host, base, count)
                if (!list.isNullOrEmpty()) return@withContext list
            }
            tryOkx(base)
        }

    /**
     * 拉取 [endTimeMs](epoch毫秒, 不含)之前的更早历史日K，用于图表左滑加载。
     * Binance 加 &endTime={endTimeMs-1}；OKX 用 history-candles 的 after 分页(单页最多100条)。
     * 全部失败返回 null。结果按时间升序、全部 time < endTimeMs。
     */
    suspend fun fetchDailyKlinesBefore(baseSymbol: String, endTimeMs: Long, count: Int): List<Kline>? =
        withContext(Dispatchers.IO) {
            val base = baseSymbol.trim().uppercase(Locale.US).removeSuffix("USDT")
            if (base.isEmpty() || endTimeMs <= 0 || count <= 0) return@withContext null
            for (host in BINANCE_HOSTS) {
                val list = tryBinance(host, base, count, endTimeMs)
                    ?.filter { it.time < endTimeMs }
                if (!list.isNullOrEmpty()) return@withContext list
            }
            tryOkxBefore(base, endTimeMs)?.filter { it.time < endTimeMs }?.ifEmpty { null }
        }

    private fun httpGet(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (e: Exception) {
        null
    }

    /** @param endTimeMs 非null时只取该时刻(不含)之前的bar(Binance endTime 参数为闭区间，故减1) */
    private fun tryBinance(host: String, base: String, count: Int, endTimeMs: Long? = null): List<Kline>? {
        return try {
            val limit = min(if (count > 0) count else 300, 1000)
            val endParam = if (endTimeMs != null) "&endTime=${endTimeMs - 1}" else ""
            val url = "https://$host/api/v3/klines?symbol=${base}USDT&interval=1d&limit=$limit$endParam"
            val body = httpGet(url) ?: return null
            val arr = JsonParser.parseString(body).asJsonArray
            parseRows(arr, newestFirst = false)
        } catch (e: Exception) {
            null
        }
    }

    private fun tryOkx(base: String): List<Kline>? {
        return try {
            val url = "https://www.okx.com/api/v5/market/candles?instId=$base-USDT&bar=1D&limit=300"
            val body = httpGet(url) ?: return null
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("code")?.asString != "0") return null
            val data = root.get("data")
            if (data == null || !data.isJsonArray) return null
            parseRows(data.asJsonArray, newestFirst = true)
        } catch (e: Exception) {
            null
        }
    }

    /** OKX history-candles: 返回比 after 时间戳更早的K线，最新在前(parseRows会reverse)，单页最多100条 */
    private fun tryOkxBefore(base: String, endTimeMs: Long): List<Kline>? {
        return try {
            val url = "https://www.okx.com/api/v5/market/history-candles?instId=$base-USDT&bar=1D&after=$endTimeMs&limit=100"
            val body = httpGet(url) ?: return null
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("code")?.asString != "0") return null
            val data = root.get("data")
            if (data == null || !data.isJsonArray) return null
            parseRows(data.asJsonArray, newestFirst = true)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 两家的行格式前6列一致: [ts, open, high, low, close, volume, ...]，
     * 字符串/数字混合，Gson 的 asLong/asDouble 均可解析。逐行容错，坏行跳过。
     */
    private fun parseRows(arr: JsonArray, newestFirst: Boolean): List<Kline>? {
        val fmt = utcFmt()
        val out = ArrayList<Kline>(arr.size())
        for (el in arr) {
            try {
                if (!el.isJsonArray) continue
                val item = el.asJsonArray
                if (item.size() < 6) continue
                val time = item[0].asLong
                val open = item[1].asDouble
                val high = item[2].asDouble
                val low = item[3].asDouble
                val close = item[4].asDouble
                val volume = item[5].asDouble
                if (time <= 0 || open <= 0 || high <= 0 || low <= 0 || close <= 0) continue
                out.add(
                    Kline(
                        time = time,
                        label = fmt.format(Date(time)),
                        open = open,
                        high = maxOf(high, open, close),
                        low = minOf(low, open, close),
                        close = close,
                        volume = if (volume >= 0) volume else 0.0,
                        amount = (if (volume >= 0) volume else 0.0) * (open + close) / 2.0
                    )
                )
            } catch (e: Exception) {
                // 跳过坏行
            }
        }
        if (newestFirst) out.reverse() // OKX 最新在前 → 转为时间升序
        out.sortBy { it.time }         // 双保险：确保严格升序
        return out.ifEmpty { null }
    }
}
