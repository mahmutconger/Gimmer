package com.mcclabs.gimmer

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mcclabs.gimmer.data.ExerciseSummary
import com.mcclabs.gimmer.data.SetValues
import com.mcclabs.gimmer.data.WorkoutDatabase
import com.mcclabs.gimmer.data.WorkoutLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHistoryTest {
    @Test
    fun summariesAndDetailAgreeWhileWorkoutOnlyReadsTheOpenExerciseForTheDay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val dao = database.workoutLogDao()
            val set = WorkoutLog(exerciseName = "Lat Pulldown", muscleGroup = "Sırt",
                dayName = "Pazartesi", weight = 50.0, sets = 3, reps = 10, dateTimestamp = 100)
            val warmup = set.copy(isWarmup = true, weight = 20.0, dateTimestamp = 180)
            val last = set.copy(weight = 55.0, sets = 1, dateTimestamp = 200)
            dao.insertAll(listOf(
                last, warmup, set,
                set.copy(dayName = "Salı", dateTimestamp = 150),
                set.copy(exerciseName = "Seated Cable Row", dateTimestamp = 120),
            ))

            val summaries = dao.observeSummaries("pulldown", "Pazartesi", "Sırt", true).first()
            assertEquals(listOf(ExerciseSummary("Lat Pulldown", 4, 2050.0)), summaries)

            val history = dao.observeExercise("Lat Pulldown", "Pazartesi", "Sırt", false).first()
            assertEquals(listOf(100L, 200L), history.map { it.dateTimestamp })
            assertEquals(listOf(50.0, 55.0), history.map { it.weight })

            val today = dao.observeWorkoutLogs("Lat Pulldown", "Pazartesi", 100, 200).first()
            assertEquals(listOf(180L, 100L), today.map { it.dateTimestamp })
            assertEquals(listOf(true, false), today.map { it.isWarmup })

            val activity = dao.observeActivity(100, 200).first()
            assertEquals(listOf(100L, 120L, 150L, 180L), activity.map { it.dateTimestamp }.sorted())
            assertEquals(12, activity.sumOf { it.sets })

            assertEquals(SetValues(55.0, 10), dao.latestSet("lat_pulldown", "Lat Pulldown"))
            dao.insert(set.copy(exerciseId = "lat_pulldown", exerciseName = "Yeni ad",
                weight = 60.0, dateTimestamp = 300))
            dao.insert(set.copy(exerciseId = "other_pulldown", weight = 70.0, dateTimestamp = 400))
            assertEquals(SetValues(60.0, 10), dao.latestSet("lat_pulldown", "Lat Pulldown"))
            val tracked = dao.observeSummaries("görünen ad", "", "", true,
                localizedIds = listOf("lat_pulldown")).first().single()
            assertEquals("lat_pulldown", tracked.exerciseId)
            assertEquals(60.0, dao.observeExercise(tracked.exerciseName, "", "", true,
                tracked.exerciseId).first().single().weight, 0.0)
        } finally {
            database.close()
        }
    }
}
