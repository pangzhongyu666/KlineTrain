package com.klinetrain.app.data.net

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.klinetrain.app.data.model.Kline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 腾讯免费行情接口。
 * GET https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,{start},{end},{count},qfq
 * (start/end 可留空; 指数无复权时返回 day 数组，解析时两者都兼容)
 * 返回 data.{symbol}.qfqday(前复权) 或 data.{symbol}.day(指数无复权) 数组，
 * 每项形如 ["2023-01-05","1700.00","1712.00","1720.00","1690.00","23456", ...]
 * 顺序为 日期, open, close, high, low, volume(手)。
 */
object TencentApi {

    private const val BASE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 拉取日K(前复权)。任何异常/超时/解析失败均返回 null。
     * @param symbol 如 sh600519 / sz000001 / sh000001(指数)
     * @param count  希望获取的K线数量
     */
    suspend fun fetchDailyKlines(symbol: String, count: Int): List<Kline>? =
        withContext(Dispatchers.IO) {
            fetchAndParse("$BASE_URL?param=$symbol,day,,,$count,qfq", symbol)
        }

    /**
     * 拉取 [endLabel](格式 yyyy-MM-dd)之前(不含当天)的更早历史日K，用于图表左滑加载。
     * 接口返回可能包含 endLabel 当天，解析后过滤掉 label >= endLabel 的bar。
     * 失败或没有更早数据返回 null。结果按时间升序。
     */
    suspend fun fetchDailyKlinesBefore(symbol: String, endLabel: String, count: Int): List<Kline>? =
        withContext(Dispatchers.IO) {
            if (endLabel.isBlank() || count <= 0) return@withContext null
            val list = fetchAndParse("$BASE_URL?param=$symbol,day,,$endLabel,$count,qfq", symbol)
                ?.filter { it.label < endLabel }
            if (list.isNullOrEmpty()) null else list
        }

    /** 发起GET并解析响应。任何异常/超时/解析失败均返回 null。 */
    private fun fetchAndParse(url: String, symbol: String): List<Kline>? = try {
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://gu.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null
            else resp.body?.string()?.let { parse(it, symbol) }
        }
    } catch (e: Exception) {
        null
    }

    /** 手工解析JSON，容忍字符串/数字混合。失败返回 null。 */
    private fun parse(body: String, symbol: String): List<Kline>? {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val data = root.getAsJsonObject("data") ?: return null
            val stockObj = data.getAsJsonObject(symbol) ?: return null
            val arr: JsonArray = when {
                stockObj.has("qfqday") && stockObj.get("qfqday").isJsonArray ->
                    stockObj.getAsJsonArray("qfqday")
                stockObj.has("day") && stockObj.get("day").isJsonArray ->
                    stockObj.getAsJsonArray("day")
                else -> return null
            }
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }
            val out = ArrayList<Kline>(arr.size())
            for (el in arr) {
                try {
                    if (!el.isJsonArray) continue
                    val item = el.asJsonArray
                    if (item.size() < 6) continue
                    val dateStr = item[0].asString
                    val time = fmt.parse(dateStr)?.time ?: continue
                    // 注意腾讯的顺序是 open, close, high, low ！
                    val open = item[1].asDouble
                    val close = item[2].asDouble
                    val high = item[3].asDouble
                    val low = item[4].asDouble
                    val volume = item[5].asDouble * 100.0 // 手 -> 股
                    if (open <= 0 || close <= 0 || high <= 0 || low <= 0) continue
                    out.add(
                        Kline(
                            time = time,
                            label = dateStr,
                            open = open,
                            high = maxOf(high, open, close),
                            low = minOf(low, open, close),
                            close = close,
                            volume = volume
                        )
                    )
                } catch (e: Exception) {
                    // 跳过坏行
                }
            }
            out.sortBy { it.time }
            if (out.isEmpty()) null else out
        } catch (e: Exception) {
            null
        }
    }
}
