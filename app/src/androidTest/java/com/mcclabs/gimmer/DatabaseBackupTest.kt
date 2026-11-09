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
    @Test fun photoOnlySnapshotRestoresTheDateAndImageWithoutResurrectingLocalDeletions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val from = WorkoutRepository(context, source)
            val into = WorkoutRepository(context, target)
            val output = java.io.ByteArrayOutputStream()
            val bitmap = android.graphics.Bitmap.createBitmap(48, 64, android.graphics.Bitmap.Config.ARGB_8888)
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output))
            bitmap.recycle()
            val photo = ProgressPhoto(UUID.randomUUID().toString(), "2026-11-07", output.toByteArray())
            source.progressPhotoDao().insert(photo)
            val editedDate = LocalDate.parse("2026-11-08")
            from.updatePhotoDate(photo.id, editedDate)

            val restored = from.withDatabaseSnapshot { file ->
                val bytes = file.readBytes()
                assertEquals(1, into.restoreDatabase(file))
                assertEquals(listOf(ProgressPhotoEntry(photo.id, editedDate.toString())), into.progressPhotos.first())
                assertArrayEquals(photo.jpeg, into.photoImage(photo.id))
                assertEquals(0, into.restoreDatabase(bytes))
                into.deletePhoto(photo.id)
                assertEquals(0, into.restoreDatabase(bytes))
                into.progressPhotos.first()
            }
            assertNotNull(restored)
            assertTrue(restored!!.isEmpty())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun routineReplacementAndRestoreLeaveTheWorkoutHistoryIntact() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val from = WorkoutRepository(context, source)
            val into = WorkoutRepository(context, target)
            val day = WorkoutCatalog.days.first()
            val updated = listOf(day.copy(exercises = day.exercises.take(1), pairs = emptyList(), programName = "Yeni program"))
            val exercise = day.exercises.first()
            from.save(WorkoutLog(exerciseName = exercise.name, exerciseId = exercise.id,
                weight = 55.0, sets = 1, reps = 8, dateTimestamp = 1794000000000))
            from.replaceRoutine(updated)
            assertTrue(runCatching { from.replaceRoutine(emptyList()) }.isFailure)
            assertEquals(updated, from.routine.first())
            assertEquals(55.0, from.latestSet(exercise)!!.weight, 0.0)
            assertEquals(updated, BackupCodec.decodeSnapshot(from.exportAsJson()).routine)
            val bytes = requireNotNull(from.exportNonEmptyDatabase())
            assertEquals(2, into.restoreDatabase(bytes))
            assertEquals(updated, into.routine.first())
            into.replaceRoutine(listOf(day))
            assertEquals(0, into.restoreDatabase(bytes))
            assertEquals(listOf(day), into.routine.first())
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun sleepOnlyBackupRestoresAndPreservesTheLocalValueForTheSameDay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val from = WorkoutRepository(context, source)
            val into = WorkoutRepository(context, target)
            val condition = DailyCondition("2026-11-08", 450)
            from.saveDailyCondition(condition)
            assertEquals(listOf(condition), BackupCodec.decodeSnapshot(from.exportAsJson()).dailyConditions)
            val bytes = requireNotNull(from.exportNonEmptyDatabase())
            assertEquals(1, into.restoreDatabase(bytes))
            assertEquals(condition, target.dailyConditionDao().getAll().single())
            into.saveDailyCondition(condition.copy(sleepMinutes = 420))
            assertEquals(0, into.restoreDatabase(bytes))
            assertEquals(420, target.dailyConditionDao().getAll().single().sleepMinutes)
        } finally {
            source.close()
            target.close()
        }
    }

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
