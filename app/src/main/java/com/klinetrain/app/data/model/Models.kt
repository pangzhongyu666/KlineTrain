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
    DAY5("五日", 1),
    MIN1("1分", 1),
    MIN5("5分", 5),
    MIN15("15分", 15),
    MIN30("30分", 30),
    MIN60("60分", 60),
    DAY("日K", 240),
    WEEK("周K", 1200),
    MONTH("月K", 5200);

    val isIntraday: Boolean get() = this.ordinal <= MIN60.ordinal

    /** 逐根推进的分钟周期(1/5/15/30/60分)；分时与五日整天展示不参与逐根推进 */
    val isStepped: Boolean get() = isIntraday && this != MIN_RT && this != DAY5

    /** 每个交易日的bar数(仅分钟周期有意义, 240可被各步长整除) */
    val barsPerDay: Int get() = 240 / minutes

    companion object {
        /** "更多"下拉里的分钟周期 */
        val minuteSteps = listOf(MIN1, MIN5, MIN15, MIN30, MIN60)
        /** 周期栏一级选项: 分时/日K/周K/月K/五日 (+更多) */
        val primaryTabs = listOf(MIN_RT, DAY, WEEK, MONTH, DAY5)
    }
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

/**
 * A股交易规则: 涨跌停幅度与T+1适用性。
 * 创业板(30)/科创板(688) 20%；ST 5%；其余股票与ETF 10%；
 * 科创/创业板类ETF 20%；黄金/跨境ETF T+0；指数/加密不适用。
 */
object AShareRules {
    /**
     * 涨跌停判定阈值(比率)。返回 null 表示该品类无涨跌停(指数/加密)。
     * 阈值 = 名义幅度 - 0.5% 容差, 兼容前复权价格无法精确对齐涨停价的情况
     * (与既有 0.095 判定口径一致)。
     */
    fun limitThreshold(code: String, name: String, type: MarketType): Double? = when (type) {
        MarketType.CRYPTO, MarketType.INDEX -> null
        MarketType.STOCK -> when {
            code.startsWith("30") || code.startsWith("688") -> 0.195
            name.uppercase().contains("ST") -> 0.045
            else -> 0.095
        }
        MarketType.ETF -> when {
            // 科创板ETF(588xxx)与双创指数类ETF按20%
            code.startsWith("588") ||
                name.contains("科创") || name.contains("创业板") || name.contains("双创") -> 0.195
            else -> 0.095
        }
    }

    /** T+1(当日买入次日才可卖): 股票与多数ETF适用; 黄金/跨境ETF为T+0; 指数/加密不适用 */
    fun t1Applies(code: String, name: String, type: MarketType): Boolean = when (type) {
        MarketType.STOCK -> true
        MarketType.ETF -> !(
            code.startsWith("518") || code.startsWith("513") ||   // 黄金ETF / 沪市跨境ETF
                name.contains("黄金") || name.contains("中概") || name.contains("恒生") ||
                name.contains("纳指") || name.contains("标普") || name.contains("日经")
            )
        else -> false
    }
}

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

/**
 * 王者荣耀式段位系统。
 * 每局收益≥+5%加一颗星，≤-5%减一颗星，其间不变。
 * 满星后再加星升段(新段位1星)；0星再掉星降段(上一段位满星-1)；青铜0星保底；王者星数无上限。
 */
object RankSystem {
    val tiers = listOf("青铜", "白银", "黄金", "铂金", "钻石", "大师", "王者")

    /** 该段位升段所需星数；王者返回 Int.MAX_VALUE(无上限) */
    fun starsNeeded(tier: Int): Int = when (tier.coerceIn(0, tiers.lastIndex)) {
        0, 1 -> 3
        2, 3 -> 4
        4, 5 -> 5
        else -> Int.MAX_VALUE
    }

    /** 根据一局收益结算段位变化。@return (新tier, 新stars) */
    fun apply(tier: Int, stars: Int, returnPct: Double): Pair<Int, Int> {
        var t = tier.coerceIn(0, tiers.lastIndex)
        var s = stars.coerceAtLeast(0)
        when {
            returnPct >= 5.0 -> {
                s += 1
                if (t < tiers.lastIndex && s > starsNeeded(t)) {
                    t += 1
                    s = 1
                }
            }
            returnPct <= -5.0 -> {
                s -= 1
                if (s < 0) {
                    if (t == 0) {
                        s = 0
                    } else {
                        t -= 1
                        s = (starsNeeded(t) - 1).coerceAtLeast(0)
                    }
                }
            }
        }
        return t to s
    }

    /** 段位显示名，如 "黄金" / "王者" */
    fun tierName(tier: Int): String = tiers[tier.coerceIn(0, tiers.lastIndex)]

    /** 完整显示，如 "黄金 ★2/4"、"王者·12星" */
    fun label(tier: Int, stars: Int): String {
        val t = tier.coerceIn(0, tiers.lastIndex)
        return if (t == tiers.lastIndex) "王者·${stars.coerceAtLeast(0)}星"
        else "${tiers[t]} ${stars.coerceAtLeast(0)}/${starsNeeded(t)}星"
    }
}
