package com.klinetrain.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrainingRecordEntity::class,
        TradeEntity::class,
        StrategyEntity::class,
        KlineCacheEntity::class,
        FormulaEntity::class
    ],
    version = 2,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE training_records ADD COLUMN seasonIndex INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE training_records ADD COLUMN endFirecrackers REAL NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "klinetrain.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
