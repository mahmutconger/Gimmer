package com.mcclabs.gimmer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "bodyweight_logs")
data class BodyweightLog(@PrimaryKey val date: String, val weight: Double)

fun requireValidBodyweight(log: BodyweightLog) {
    require(LocalDate.parse(log.date).toString() == log.date)
    require(log.weight.isFinite() && log.weight in 1.0..500.0)
}

@Dao
interface BodyweightDao {
    @Upsert
    suspend fun upsert(log: BodyweightLog)

    @Query("SELECT * FROM bodyweight_logs ORDER BY date ASC")
    fun observeAll(): Flow<List<BodyweightLog>>

    @Query("SELECT * FROM bodyweight_logs ORDER BY date ASC")
    suspend fun getAll(): List<BodyweightLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(logs: List<BodyweightLog>)
}
