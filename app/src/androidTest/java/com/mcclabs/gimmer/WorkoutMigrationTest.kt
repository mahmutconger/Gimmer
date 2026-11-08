package com.mcclabs.gimmer

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mcclabs.gimmer.data.WorkoutDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutMigrationTest {
    @Test fun versionOneHistorySurvivesMigrationAndSupportsEditing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("""CREATE TABLE workout_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                exerciseName TEXT NOT NULL, weight REAL NOT NULL, sets INTEGER NOT NULL,
                reps INTEGER NOT NULL, dateTimestamp INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX index_workout_logs_exerciseName ON workout_logs(exerciseName)")
            db.execSQL("CREATE INDEX index_workout_logs_dateTimestamp ON workout_logs(dateTimestamp)")
            db.execSQL("INSERT INTO workout_logs VALUES (7, 'Machine Chest Press', 50, 3, 8, 1794000000000)")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, WorkoutDatabase::class.java, name)
            .addMigrations(WorkoutDatabase.MIGRATION_1_2, WorkoutDatabase.MIGRATION_2_3,
                WorkoutDatabase.MIGRATION_3_4).build()
        try {
            val dao = database.workoutLogDao()
            val original = dao.getAll().single()
            assertEquals(7L, original.id)
            assertEquals("Göğüs", original.muscleGroup)
            assertEquals("Pazartesi", original.dayName)
            assertNull(original.note)
            assertNull(original.rir)
            assertFalse(original.isWarmup)
            assertTrue(database.bodyweightDao().getAll().isEmpty())
            assertTrue(database.nutritionDao().allMeals().isEmpty())
            dao.update(original.copy(weight = 55.0, note = "Kontrollü"))
            assertEquals(55.0, dao.search("Chest", "Pazartesi", "Göğüs").first().single().weight, 0.0)
            assertTrue(dao.search("Chest", "Salı", "").first().isEmpty())
            dao.delete(original)
            assertTrue(dao.getAll().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
