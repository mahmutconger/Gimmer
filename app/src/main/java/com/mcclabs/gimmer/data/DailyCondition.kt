package com.mcclabs.gimmer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

internal const val MIN_SLEEP_MINUTES = 240
internal const val MAX_SLEEP_MINUTES = 600

@Entity(tableName = "daily_conditions")
data class DailyCondition(@PrimaryKey val date: String, val sleepMinutes: Int)

internal fun DailyCondition.requireValid() {
    require(LocalDate.parse(date).toString() == date)
    require(sleepMinutes in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES)
}

@Dao
interface DailyConditionDao {
    @Query("SELECT * FROM daily_conditions WHERE date = :date")
    fun observe(date: String): Flow<DailyCondition?>

    @Query("SELECT * FROM daily_conditions ORDER BY date")
    suspend fun getAll(): List<DailyCondition>

    @Upsert
    suspend fun upsert(condition: DailyCondition)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(conditions: List<DailyCondition>): List<Long>
}
