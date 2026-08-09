package com.klinetrain.app

import android.app.Application
import com.klinetrain.app.data.SettingsStore
import com.klinetrain.app.data.StockRepository
import com.klinetrain.app.data.StockRepositoryImpl
import com.klinetrain.app.data.db.AppDatabase
import com.klinetrain.app.data.db.FormulaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KlineTrainApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: StockRepository by lazy {
        StockRepositoryImpl(this, database.klineCacheDao())
    }

    companion object {
        lateinit var instance: KlineTrainApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        seedFormulasOnce()
    }

    /** 首次启动预置示例公式(应用级只做一次，用户删除后不复活) */
    private fun seedFormulasOnce() {
        if (settings.formulasSeeded) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val dao = database.formulaDao()
            if (dao.getAll().isEmpty()) {
                dao.insert(FormulaEntity(name = "双均线", source = "MA5: MA(C,5);\nMA10: MA(C,10);", onMainChart = true))
                dao.insert(FormulaEntity(name = "乖离率", source = "BIAS: (C-MA(C,12))/MA(C,12)*100;", onMainChart = false))
            }
            settings.formulasSeeded = true
        }
    }
}
