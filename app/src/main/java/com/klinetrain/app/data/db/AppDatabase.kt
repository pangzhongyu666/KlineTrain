package com.klinetrain.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrainingRecordEntity::class,
        TradeEntity::class,
        StrategyEntity::class,
        KlineCacheEntity::class,
        FormulaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingRecordDao(): TrainingRecordDao
    abstract fun tradeDao(): TradeDao
    abstract fun strategyDao(): StrategyDao
    abstract fun klineCacheDao(): KlineCacheDao
    abstract fun formulaDao(): FormulaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "klinetrain.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
