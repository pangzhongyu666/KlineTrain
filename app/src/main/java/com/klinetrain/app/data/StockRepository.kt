package com.klinetrain.app.data

import com.klinetrain.app.data.model.Kline
import com.klinetrain.app.data.model.MarketType
import com.klinetrain.app.data.model.Stock

/**
 * 行情数据仓库。实现在 StockRepositoryImpl：
 * 网络(腾讯行情) + Room缓存 + 离线合成数据兜底。
 */
interface StockRepository {
    /** 全部可训练标的(来自 assets/stocks.json) */
    suspend fun getStockList(type: MarketType? = null): List<Stock>

    /**
     * 拉取日K(前复权)。优先缓存(24h内)，其次网络，失败时返回确定性合成数据。
     * @param count 希望拿到的K线数量上限
     */
    suspend fun getDailyKlines(stock: Stock, count: Int = 640): List<Kline>
}
