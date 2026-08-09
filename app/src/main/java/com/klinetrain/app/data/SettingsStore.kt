package com.klinetrain.app.data

import android.content.Context
import android.content.SharedPreferences

/** 全局设置(SharedPreferences) */
class SettingsStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("klinetrain_settings", Context.MODE_PRIVATE)

    var leverage: Double
        get() = sp.getFloat("leverage", 1f).toDouble()
        set(v) = sp.edit().putFloat("leverage", v.toFloat()).apply()

    var sessionBars: Int
        get() = sp.getInt("session_bars", 120)
        set(v) = sp.edit().putInt("session_bars", v).apply()

    var feeRate: Double
        get() = sp.getFloat("fee_rate", 0.00075f).toDouble()
        set(v) = sp.edit().putFloat("fee_rate", v.toFloat()).apply()

    var allowShort: Boolean
        get() = sp.getBoolean("allow_short", true)
        set(v) = sp.edit().putBoolean("allow_short", v).apply()

    var nickname: String
        get() = sp.getString("nickname", "训练者8888") ?: "训练者8888"
        set(v) = sp.edit().putString("nickname", v).apply()

    /** 累计爆竹数(跨局货币,展示用) */
    var firecrackers: Double
        get() = sp.getFloat("firecrackers", 10000f).toDouble()
        set(v) = sp.edit().putFloat("firecrackers", v.toFloat()).apply()

    /** 示例公式是否已预置(只做一次，删除后不复活) */
    var formulasSeeded: Boolean
        get() = sp.getBoolean("formulas_seeded", false)
        set(v) = sp.edit().putBoolean("formulas_seeded", v).apply()
}
