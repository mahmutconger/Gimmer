package com.mcclabs.gimmer

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mcclabs.gimmer.data.*
import com.mcclabs.gimmer.nutrition.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DatabaseBackupTest {
    @Test fun closedSqliteBackupRestoresBothTablesAndMergesWithoutDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val from = WorkoutRepository(context, source)
            val into = WorkoutRepository(context, target)
            assertNull(from.exportNonEmptyDatabase())
            val set = WorkoutLog(exerciseName = "Machine Chest Press", muscleGroup = "Göğüs",
                dayName = "Pazartesi", weight = 50.0, sets = 1, reps = 10, rir = 2,
                dateTimestamp = 1794000000000)
            from.save(set)
            from.save(set.copy(isWarmup = true, weight = 20.0, rir = 4))
            from.saveBodyweight(BodyweightLog("2026-11-07", 65.0))
            val bytes = from.exportDatabase()
            assertArrayEquals(bytes, from.exportNonEmptyDatabase())
            assertEquals("SQLite format 3", bytes.take(15).toByteArray().toString(Charsets.UTF_8))
            assertEquals(3, into.restoreDatabase(bytes))
            assertEquals(0, into.restoreDatabase(bytes))
            assertEquals(2, target.workoutLogDao().getAll().size)
            assertEquals(1, target.workoutLogDao().search("", "", "").first().size)
            assertEquals(2, target.workoutLogDao().search("", "", "", true).first().size)
            assertEquals(65.0, target.bodyweightDao().getAll().single().weight, 0.0)
            into.saveBodyweight(BodyweightLog("2026-11-07", 66.0))
            into.restoreDatabase(bytes)
            assertEquals(66.0, target.bodyweightDao().getAll().single().weight, 0.0)
            from.saveBodyweight(BodyweightLog("2026-11-07", 67.0))
            assertFalse(bytes.contentEquals(checkNotNull(from.exportNonEmptyDatabase())))
        } finally { source.close(); target.close() }
    }

    @Test fun nutritionOnlyBackupRetainsMealsAndHistoricalFoodValues() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val from = NutritionRepository(source)
            val into = WorkoutRepository(context, target)
            val day = LocalDate.parse("2026-11-08")
            val timestamp = Instant.parse("2026-11-08T10:00:00Z").toEpochMilli()
            val food = FoodEntry(foodName = "Yoğurt", caloriesPer100g = 75.0, proteinPer100g = 4.1,
                carbsPer100g = 5.7, fatPer100g = 4.0, barcode = "8690000000000")
            val log = MealLog(UUID.randomUUID().toString(), 0, MealType.BREAKFAST, 120.5, timestamp)
            from.save(LoggedMeal(log, food))
            from.save(LoggedMeal(log.copy(id = UUID.randomUUID().toString(), dateTimestamp = timestamp + 1),
                food.copy(caloriesPer100g = 80.0)))
            val daily = from.observeDay(day, ZoneId.of("Europe/Istanbul")).first()
            assertEquals(186.775, daily.totals().calories, 0.000001)
            assertEquals(80.0, from.recentFoods.first().first().caloriesPer100g, 0.0)

            val bytes = checkNotNull(WorkoutRepository(context, source).exportNonEmptyDatabase())
            assertEquals(2, into.restoreDatabase(bytes))
            assertEquals(0, into.restoreDatabase(bytes))
            assertEquals(0, into.restore(WorkoutRepository(context, source).exportAsJson()))
            val restored = target.nutritionDao().allMeals()
            assertEquals(daily.map { it.log.id }, restored.map { it.log.id })
            assertEquals(daily.map { it.food.caloriesPer100g }, restored.map { it.food.caloriesPer100g })
        } finally { source.close(); target.close() }
    }
}
