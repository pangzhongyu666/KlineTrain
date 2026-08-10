package com.klinetrain.app.data.model

/** 市场品类 */
enum class MarketType(val label: String) {
    STOCK("股票"), INDEX("指数"), ETF("ETF"), CRYPTO("加密货币")
}

/** 一只可训练的标的 */
data class Stock(
    val code: String,      // 如 600519 / 000001 / 510300
    val name: String,      // 如 贵州茅台
    val market: String,    // sh / sz
    val type: MarketType
) {
    /** 腾讯行情接口用的符号，如 sh600519 */
    val symbol: String get() = market + code
}

/** 单根K线 */
data class Kline(
    val time: Long,        // bar 起始时间, epoch millis
    val label: String,     // 显示标签，如 2023-01-05 或 10:35
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,    // 成交量(股)
    val amount: Double = 0.0
) {
    val isUp: Boolean get() = close >= open
}

/** 图表周期 */
enum class TimeFrame(val label: String, val minutes: Int) {
    MIN_RT("分时", 1),
    MIN5("5分", 5),
    MIN15("15分", 15),
    MIN30("30分", 30),
    MIN60("60分", 60),
    DAY("日K", 240),
    WEEK("周K", 1200),
    MONTH("月K", 5200);

    val isIntraday: Boolean get() = this.ordinal <= MIN60.ordinal
}

/** 训练模式 */
enum class TrainingMode(val label: String, val desc: String) {
    BLIND("双盲训练", "未知时间未知股票"),
    LIMIT_UP("涨停训练", "涨停后50个交易日"),
    INDEX("指数训练", "上证/深证/创业板等"),
    ETF("ETF训练", "宽基与行业ETF"),
    CRYPTO("币圈训练", "BTC/ETH与自定义币种")
}

/** 主图叠加指标 */
enum class MainOverlay(val label: String) {
    MA("MA"), EMA("EMA"), BOLL("BOLL"), NONE("无")
}

/** 副图指标 */
enum class SubIndicator(val label: String) {
    VOL("成交量"), MACD("MACD"), KDJ("KDJ"), RSI("RSI"),
    ATR("ATR"), CCI("CCI"), FORMULA("自定义")
}

/** 交易方向 */
enum class Direction(val label: String) { LONG("做多"), SHORT("做空") }

/** 图表上的买卖标记 */
data class TradeMarker(
    val barIndex: Int,        // 在传入图表的 klines 中的下标
    val direction: Direction,
    val isOpen: Boolean       // true=开仓(B), false=平仓(S)
)

/** 一次训练的配置 */
data class TrainingConfig(
    val mode: TrainingMode,
    val sessionBars: Int = 120,     // 本局需要走完的日K数量
    val warmupBars: Int = 90,       // 开局时已显示的历史K线数量
    val leverage: Double = 1.0,     // 杠杆
    val feeRate: Double = 0.00075,  // 单边费率
    val allowShort: Boolean = true,
    val initialCash: Double = 10000.0,   // 本局入场资金(=当前爆竹数)
    val baseInitial: Double = 10000.0,   // 基准初始金额(暴富/破产阈值基准)
    val totalSlots: Int = 5,
    val slotsPerTrade: Int = 1,          // 每次买卖操作的仓数
    val splitMode: Boolean = true,       // 分仓模式(false=每次全仓进出)
    val stopProfitPct: Double? = null,   // 止盈%(按价格涨跌幅), null=不启用
    val stopLossPct: Double? = null      // 止损%
) {
    /** 暴富线: 爆竹达到基准初始的100倍 */
    val richLine: Double get() = baseInitial * 100
    /** 破产线: 爆竹跌到基准初始的5% */
    val bankruptLine: Double get() = baseInitial * 0.05
}

/** 训练结束后的统计结果 */
data class SessionResult(
    val returnPct: Double,          // 本局收益(%)
    val intervalChangePct: Double,  // 区间涨跌幅(%)
    val outperformPct: Double,      // 跑赢区间(%)
    val openCount: Int,             // 开仓次数
    val openWinRate: Double,        // 开仓胜率(%)
    val profitLossRatio: Double,    // 盈亏比 (avg win / avg loss, 无亏损时为正无穷用 999 表示)
    val maxDrawdownPct: Double,     // 最大回撤(%)
    val holdRatio: Double,          // 持仓率(%): 有持仓的bar数 / 总bar数
    val heavyRatio: Double,         // 重仓率(%): 持仓≥3/5仓的bar数 / 总bar数
    val holdBars: Int,              // 持仓bar数(交易日)
    val bankrupt: Boolean,          // 是否破产(爆竹≤基准初始5%)
    val richOnce: Boolean           // 是否暴富(爆竹曾达基准初始100倍)
)

/** K线样式 */
enum class CandleStyle(val label: String) {
    HOLLOW("空心阳"), SOLID("实心阳"), LINE("折线图"), BAR("竹节图")
}

/** 坐标类型 */
enum class AxisType(val label: String) {
    NORMAL("普通"), LOG("对数"), PERCENT("百分比")
}

/** K线图表外观设置(K线设置页) */
data class ChartStyle(
    val candleStyle: CandleStyle = CandleStyle.SOLID,
    val axisType: AxisType = AxisType.NORMAL,
    val greenUp: Boolean = false,        // 绿涨红跌(反色)
    val limitColors: Boolean = true,     // 涨停黄色跌停蓝色
    val lastPriceLine: Boolean = true,   // 最新价水平虚线
    val costLineEnabled: Boolean = true, // 持仓成本线
    val watermark: Boolean = true,       // 品牌水印
    val rightPadding: Boolean = false,   // K线右边距(右侧留白)
    val initialBars: Int = 100           // K线初始可见数量
)

/** 标的静态元数据(确定性合成，用于换手率等展示) */
object MarketMeta {
    /** 流通股本(股)。指数与加密货币无意义返回 null */
    fun floatShares(stock: Stock): Double? {
        if (stock.type == MarketType.INDEX || stock.type == MarketType.CRYPTO) return null
        val rnd = java.util.Random(stock.code.hashCode().toLong())
        // 对数均匀分布: 4亿 ~ 800亿股
        val logMin = Math.log(4e8)
        val logMax = Math.log(8e10)
        return Math.exp(logMin + (logMax - logMin) * rnd.nextDouble())
    }

    /** 换手率% = 当日成交量/流通股本*100，不可用时返回 null */
    fun turnoverPct(stock: Stock, volume: Double): Double? {
        val shares = floatShares(stock) ?: return null
        if (shares <= 0) return null
        return volume / shares * 100
    }
}
