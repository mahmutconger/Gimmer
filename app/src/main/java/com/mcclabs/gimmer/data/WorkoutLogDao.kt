package com.mcclabs.gimmer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val HISTORY_FILTER = """
    (:day = '' OR dayName = :day)
    AND (:muscle = '' OR muscleGroup = :muscle)
    AND (:includeWarmups OR isWarmup = 0)
"""

data class ExerciseSummary(val exerciseName: String, val workingSets: Long, val volume: Double)

data class WorkoutActivity(val dateTimestamp: Long, val sets: Int)

data class SetValues(val weight: Double, val reps: Int)

@Dao
interface WorkoutLogDao {
    @Insert
    suspend fun insert(log: WorkoutLog): Long

    @Query("""
        SELECT * FROM workout_logs
        WHERE (exerciseId = :exerciseId OR ((exerciseId IS NULL OR :exerciseId IS NULL) AND exerciseName = :name))
        AND dayName = :day
        AND dateTimestamp >= :start AND dateTimestamp < :end
        ORDER BY dateTimestamp DESC, id DESC
    """)
    fun observeWorkoutLogs(name: String, day: String, start: Long, end: Long, exerciseId: String? = null): Flow<List<WorkoutLog>>

    @Query("""
        SELECT weight, reps FROM workout_logs
        WHERE exerciseId = :exerciseId OR (exerciseId IS NULL AND exerciseName = :name)
        ORDER BY dateTimestamp DESC, id DESC LIMIT 1
    """)
    suspend fun latestSet(exerciseId: String, name: String): SetValues?

    @Query("""
        SELECT dateTimestamp, sets FROM workout_logs
        WHERE dateTimestamp >= :start AND dateTimestamp < :end
    """)
    fun observeActivity(start: Long, end: Long): Flow<List<WorkoutActivity>>

    @Query("SELECT * FROM workout_logs ORDER BY dateTimestamp ASC, id ASC")
    suspend fun getAll(): List<WorkoutLog>

    @androidx.room.Update
    suspend fun update(log: WorkoutLog)

    @androidx.room.Delete
    suspend fun delete(log: WorkoutLog)

    @Insert
    suspend fun insertAll(logs: List<WorkoutLog>)

    @Query("""SELECT * FROM workout_logs
        WHERE (:name = '' OR instr(lower(exerciseName), lower(:name)) > 0)
        AND """ + HISTORY_FILTER + " ORDER BY dateTimestamp ASC, id ASC")
    fun search(name: String, day: String, muscle: String, includeWarmups: Boolean = false): Flow<List<WorkoutLog>>

    @Query("""
        SELECT exerciseName,
            SUM(CASE WHEN isWarmup = 0 THEN sets ELSE 0 END) AS workingSets,
            SUM(CASE WHEN isWarmup = 0 THEN weight * reps * sets ELSE 0.0 END) AS volume
        FROM workout_logs
        WHERE (:name = '' OR instr(lower(exerciseName), lower(:name)) > 0 OR exerciseName IN (:localizedMatches))
        AND """ + HISTORY_FILTER + """
        GROUP BY exerciseName
        ORDER BY exerciseName COLLATE NOCASE, exerciseName
    """)
    fun observeSummaries(name: String, day: String, muscle: String, includeWarmups: Boolean,
        localizedMatches: List<String> = emptyList()):
        Flow<List<ExerciseSummary>>

    @Query("""
        SELECT * FROM workout_logs WHERE exerciseName = :name AND
    """ + HISTORY_FILTER + " ORDER BY dateTimestamp ASC, id ASC")
    fun observeExercise(name: String, day: String, muscle: String, includeWarmups: Boolean):
        Flow<List<WorkoutLog>>
}
