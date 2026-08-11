package com.klinetrain.app.data

import android.content.Context
import android.content.SharedPreferences
import com.klinetrain.app.data.model.AxisType
import com.klinetrain.app.data.model.CandleStyle
import com.klinetrain.app.data.model.ChartStyle

/** 全局设置(SharedPreferences) */
class SettingsStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("klinetrain_settings", Context.MODE_PRIVATE)

    init {
        // 训练时间段选项v2迁移: 旧值 1=最近5年 2=最近10年 → 新值 2=最近5年 / 3=自定义10年
        if (!sp.getBoolean("time_range_v2", false)) {
            val e = sp.edit()
            when (sp.getInt("time_range_filter", 0)) {
                1 -> e.putInt("time_range_filter", 2)
                2 -> e.putInt("time_range_filter", 3).putInt("custom_years", 10)
            }
            e.putBoolean("time_range_v2", true).apply()
        }
    }

    // ---------------- 训练参数 ----------------

    /** 基准初始金额(可设置)。暴富=100倍，破产=5% */
    var initialCash: Double
        get() = sp.getFloat("initial_cash", 10000f).toDouble()
        set(v) = sp.edit().putFloat("initial_cash", v.toFloat()).apply()

    /** 使用杠杆开关 */
    var useLeverage: Boolean
        get() = sp.getBoolean("use_leverage", false)
        set(v) = sp.edit().putBoolean("use_leverage", v).apply()

    var leverage: Double
        get() = sp.getFloat("leverage", 1f).toDouble()
        set(v) = sp.edit().putFloat("leverage", v.toFloat()).apply()

    var sessionBars: Int
        get() = sp.getInt("session_bars", 120)
        set(v) = sp.edit().putInt("session_bars", v).apply()

    /** 单边费率(0.0003 = 0.03%) */
    var feeRate: Double
        get() = sp.getFloat("fee_rate", 0.0003f).toDouble()
        set(v) = sp.edit().putFloat("fee_rate", v.toFloat()).apply()

    var allowShort: Boolean
        get() = sp.getBoolean("allow_short", true)
        set(v) = sp.edit().putBoolean("allow_short", v).apply()

    /** 分仓模式(false=每次全仓进出) */
    var splitMode: Boolean
        get() = sp.getBoolean("split_mode", true)
        set(v) = sp.edit().putBoolean("split_mode", v).apply()

    /** 总仓数(2/3/5/10) */
    var totalSlots: Int
        get() = sp.getInt("total_slots", 5)
        set(v) = sp.edit().putInt("total_slots", v).apply()

    /** 每次买卖操作的仓数 */
    var slotsPerTrade: Int
        get() = sp.getInt("slots_per_trade", 1)
        set(v) = sp.edit().putInt("slots_per_trade", v).apply()

    /** 震动反馈 */
    var vibrateMode: Boolean
        get() = sp.getBoolean("vibrate_mode", true)
        set(v) = sp.edit().putBoolean("vibrate_mode", v).apply()

    /** 横屏训练 */
    var landscapeMode: Boolean
        get() = sp.getBoolean("landscape_mode", false)
        set(v) = sp.edit().putBoolean("landscape_mode", v).apply()

    /** 市场选择: 0不限 1主板 2创业板 3科创板 */
    var marketFilter: Int
        get() = sp.getInt("market_filter", 0)
        set(v) = sp.edit().putInt("market_filter", v).apply()

    /** 训练时间段: 0不限 1最近1年 2最近5年 3自定义(customYears) */
    var timeRangeFilter: Int
        get() = sp.getInt("time_range_filter", 0)
        set(v) = sp.edit().putInt("time_range_filter", v).apply()

    /** 自定义时间段(最近N年), timeRangeFilter=3 时生效 */
    var customYears: Int
        get() = sp.getInt("custom_years", 3)
        set(v) = sp.edit().putInt("custom_years", v).apply()

    /** 去除ST */
    var excludeSt: Boolean
        get() = sp.getBoolean("exclude_st", true)
        set(v) = sp.edit().putBoolean("exclude_st", v).apply()

    /** 止盈止损开关 */
    var stopEnabled: Boolean
        get() = sp.getBoolean("stop_enabled", false)
        set(v) = sp.edit().putBoolean("stop_enabled", v).apply()

    /** 止盈%(按价格涨跌幅) */
    var stopProfitPct: Double
        get() = sp.getFloat("stop_profit_pct", 20f).toDouble()
        set(v) = sp.edit().putFloat("stop_profit_pct", v.toFloat()).apply()

    /** 止损% */
    var stopLossPct: Double
        get() = sp.getFloat("stop_loss_pct", 10f).toDouble()
        set(v) = sp.edit().putFloat("stop_loss_pct", v.toFloat()).apply()

    /** 自定义币种(逗号分隔的基础币符号，如 SOL,DOGE) */
    var customCoins: List<String>
        get() = (sp.getString("custom_coins", "") ?: "")
            .split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("custom_coins", v.joinToString(",")).apply()

    /** 币圈训练当前选中的币(基础符号，如 BTC) */
    var selectedCoin: String
        get() = sp.getString("selected_coin", "BTC") ?: "BTC"
        set(v) = sp.edit().putString("selected_coin", v).apply()

    // ---------------- 赛季与金钱 ----------------

    /** 累计金钱数(跨局货币，每局以此入场)。持久化key沿用firecrackers */
    var firecrackers: Double
        get() = sp.getFloat("firecrackers", initialCash.toFloat()).toDouble()
        set(v) = sp.edit().putFloat("firecrackers", v.toFloat()).apply()

    /** 当前赛季序号(破产重置后+1) */
    var seasonIndex: Int
        get() = sp.getInt("season_index", 1)
        set(v) = sp.edit().putInt("season_index", v).apply()

    // ---------------- 段位(王者荣耀式) ----------------

    /** 段位下标(RankSystem.tiers) */
    var rankTier: Int
        get() = sp.getInt("rank_tier", 0)
        set(v) = sp.edit().putInt("rank_tier", v).apply()

    /** 当前段位星数 */
    var rankStars: Int
        get() = sp.getInt("rank_stars", 0)
        set(v) = sp.edit().putInt("rank_stars", v).apply()

    // ---------------- K线设置 ----------------

    var chartStyle: ChartStyle
        get() = ChartStyle(
            candleStyle = CandleStyle.entries.getOrElse(sp.getInt("cs_candle", CandleStyle.SOLID.ordinal)) { CandleStyle.SOLID },
            axisType = AxisType.entries.getOrElse(sp.getInt("cs_axis", AxisType.NORMAL.ordinal)) { AxisType.NORMAL },
            greenUp = sp.getBoolean("cs_green_up", false),
            limitColors = sp.getBoolean("cs_limit_colors", true),
            lastPriceLine = sp.getBoolean("cs_last_price", true),
            costLineEnabled = sp.getBoolean("cs_cost_line", true),
            watermark = sp.getBoolean("cs_watermark", true),
            rightPadding = sp.getBoolean("cs_right_padding", false),
            initialBars = sp.getInt("cs_initial_bars", 100)
        )
        set(v) = sp.edit()
            .putInt("cs_candle", v.candleStyle.ordinal)
            .putInt("cs_axis", v.axisType.ordinal)
            .putBoolean("cs_green_up", v.greenUp)
            .putBoolean("cs_limit_colors", v.limitColors)
            .putBoolean("cs_last_price", v.lastPriceLine)
            .putBoolean("cs_cost_line", v.costLineEnabled)
            .putBoolean("cs_watermark", v.watermark)
            .putBoolean("cs_right_padding", v.rightPadding)
            .putInt("cs_initial_bars", v.initialBars)
            .apply()

    // ---------------- 其他 ----------------

    var nickname: String
        get() = sp.getString("nickname", "训练者8888") ?: "训练者8888"
        set(v) = sp.edit().putString("nickname", v).apply()

    /** 示例公式是否已预置(只做一次，删除后不复活) */
    var formulasSeeded: Boolean
        get() = sp.getBoolean("formulas_seeded", false)
        set(v) = sp.edit().putBoolean("formulas_seeded", v).apply()
}
