package com.klinetrain.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingRecordDao {
    @Insert
    suspend fun insert(record: TrainingRecordEntity): Long

    @Update
    suspend fun update(record: TrainingRecordEntity)

    @Delete
    suspend fun delete(record: TrainingRecordEntity)

    @Query("SELECT * FROM training_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrainingRecordEntity>>

    @Query("SELECT * FROM training_records ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TrainingRecordEntity>>

    @Query("SELECT * FROM training_records WHERE id = :id")
    suspend fun getById(id: Long): TrainingRecordEntity?

    @Query("SELECT * FROM training_records WHERE strategyId = :strategyId ORDER BY createdAt DESC")
    fun observeByStrategy(strategyId: Long): Flow<List<TrainingRecordEntity>>

    @Query("SELECT * FROM training_records WHERE seasonIndex = :season ORDER BY createdAt ASC")
    fun observeBySeason(season: Int): Flow<List<TrainingRecordEntity>>

    @Query("SELECT DISTINCT seasonIndex FROM training_records ORDER BY seasonIndex ASC")
    fun observeSeasons(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM training_records")
    suspend fun count(): Int
}

@Dao
interface TradeDao {
    @Insert
    suspend fun insertAll(trades: List<TradeEntity>)

    @Query("SELECT * FROM trades WHERE recordId = :recordId ORDER BY id ASC")
    suspend fun getByRecord(recordId: Long): List<TradeEntity>
}

@Dao
interface StrategyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(strategy: StrategyEntity): Long

    @Update
    suspend fun update(strategy: StrategyEntity)

    @Delete
    suspend fun delete(strategy: StrategyEntity)

    @Query("SELECT * FROM strategies ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategies WHERE id = :id")
    suspend fun getById(id: Long): StrategyEntity?
}

@Dao
interface KlineCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(cache: KlineCacheEntity)

    @Query("SELECT * FROM kline_cache WHERE symbol = :symbol")
    suspend fun get(symbol: String): KlineCacheEntity?
}

@Dao
interface FormulaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(formula: FormulaEntity): Long

    @Update
    suspend fun update(formula: FormulaEntity)

    @Delete
    suspend fun delete(formula: FormulaEntity)

    @Query("SELECT * FROM formulas ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FormulaEntity>>

    @Query("SELECT * FROM formulas")
    suspend fun getAll(): List<FormulaEntity>
}
