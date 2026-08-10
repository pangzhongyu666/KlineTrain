package com.klinetrain.app.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.klinetrain.app.data.db.KlineCacheDao
import com.klinetrain.app.data.db.KlineCacheEntity
import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.MarketType
import com.klinetrain.app.data.model.Stock
import com.klinetrain.app.data.net.CryptoApi
import com.klinetrain.app.data.net.TencentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 行情仓库实现：assets标的池(+自定义币种) + 腾讯行情/加密行情 + Room缓存(24h) + 离线合成兜底。
 */
class StockRepositoryImpl(
    context: android.content.Context,
    private val cacheDao: KlineCacheDao
) : StockRepository {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val settings by lazy { SettingsStore(appContext) }

    @Volatile
    private var cachedStocks: List<Stock>? = null
    private val listMutex = Mutex()

    override suspend fun getStockList(type: MarketType?): List<Stock> =
        withContext(Dispatchers.IO) {
            val all = cachedStocks ?: listMutex.withLock {
                cachedStocks ?: loadStocksFromAssets().also { cachedStocks = it }
            }
            // 自定义币种随设置实时变化，故每次读取、不并入 cachedStocks；与json内置(BTC/ETH)按code去重
            val merged = if (type == null || type == MarketType.CRYPTO) {
                val existing = all.mapTo(HashSet()) { it.code }
                val custom = settings.customCoins
                    .map { coin ->
                        Stock(code = coin + "USDT", name = coin, market = "crypto", type = MarketType.CRYPTO)
                    }
                    .distinctBy { it.code }
                    .filter { it.code !in existing }
                all + custom
            } else all
            if (type == null) merged else merged.filter { it.type == type }
        }

    override suspend fun getDailyKlines(stock: Stock, count: Int): List<Kline> =
        withContext(Dispatchers.IO) {
            val symbol = stock.symbol
            val now = System.currentTimeMillis()
            val cached = runCatching { cacheDao.get(symbol) }.getOrNull()

            // 1) 24h内的新鲜缓存
            if (cached != null && now - cached.updatedAt < CACHE_TTL_MS) {
                val list = parseKlineJson(cached.json)
                if (!list.isNullOrEmpty()) return@withContext list.takeLast(count)
            }

            // 2) 网络拉取，成功则写缓存(加密走 Binance/OKX，其余走腾讯)
            val net = if (stock.type == MarketType.CRYPTO) {
                CryptoApi.fetchDailyKlines(stock.code.removeSuffix("USDT"), count)
            } else {
                TencentApi.fetchDailyKlines(symbol, count)
            }
            if (!net.isNullOrEmpty()) {
                runCatching {
                    cacheDao.put(KlineCacheEntity(symbol = symbol, json = gson.toJson(net), updatedAt = now))
                }
                return@withContext net.takeLast(count)
            }

            // 3) 网络失败：过期缓存也比合成数据好
            if (cached != null) {
                val list = parseKlineJson(cached.json)
                if (!list.isNullOrEmpty()) return@withContext list.takeLast(count)
            }

            // 4) 确定性合成兜底
            if (stock.type == MarketType.CRYPTO) SyntheticData.generateCrypto(stock.code, count)
            else SyntheticData.generate(symbol, count)
        }

    private fun parseKlineJson(json: String): List<Kline>? = runCatching {
        val type = object : TypeToken<List<Kline>>() {}.type
        gson.fromJson<List<Kline>>(json, type)
    }.getOrNull()

    private fun loadStocksFromAssets(): List<Stock> = try {
        appContext.assets.open("stocks.json").bufferedReader(Charsets.UTF_8).use { reader ->
            val arr = JsonParser.parseReader(reader).asJsonArray
            arr.mapNotNull { el ->
                runCatching {
                    val o = el.asJsonObject
                    Stock(
                        code = o.get("code").asString,
                        name = o.get("name").asString,
                        market = o.get("market").asString,
                        type = MarketType.valueOf(o.get("type").asString)
                    )
                }.getOrNull()
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
