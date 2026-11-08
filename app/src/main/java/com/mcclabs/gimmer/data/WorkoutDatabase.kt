package com.mcclabs.gimmer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mcclabs.gimmer.nutrition.FoodEntry
import com.mcclabs.gimmer.nutrition.MealLog
import com.mcclabs.gimmer.nutrition.NutritionDao

@Database(
    entities = [WorkoutLog::class, BodyweightLog::class, FoodEntry::class, MealLog::class, DailyCondition::class],
    version = 6,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun bodyweightDao(): BodyweightDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun dailyConditionDao(): DailyConditionDao

    companion object {
        @Volatile
        private var instance: WorkoutDatabase? = null

        fun getInstance(context: Context): WorkoutDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WorkoutDatabase::class.java,
                "gimmer_workout_database",
            ).addMigrations(*MIGRATIONS).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN muscleGroup TEXT NOT NULL DEFAULT 'Diğer'")
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN dayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN note TEXT")
                WorkoutCatalog.days.forEach { day ->
                    day.exercises.forEach { exercise ->
                        db.execSQL(
                            "UPDATE workout_logs SET muscleGroup = ?, dayName = ? WHERE exerciseName = ?",
                            arrayOf(exercise.muscleGroup, day.name, exercise.name),
                        )
                    }
                }
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN isWarmup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN rir INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS bodyweight_logs (date TEXT NOT NULL PRIMARY KEY, weight REAL NOT NULL)")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE food_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, foodName TEXT NOT NULL,
                    caloriesPer100g REAL NOT NULL, proteinPer100g REAL NOT NULL,
                    carbsPer100g REAL NOT NULL, fatPer100g REAL NOT NULL, barcode TEXT)""")
                db.execSQL("CREATE INDEX index_food_entries_foodName ON food_entries(foodName)")
                db.execSQL("""CREATE TABLE meal_logs (
                    id TEXT NOT NULL PRIMARY KEY, foodEntryId INTEGER NOT NULL, mealType TEXT NOT NULL,
                    consumedGrams REAL NOT NULL, dateTimestamp INTEGER NOT NULL,
                    FOREIGN KEY(foodEntryId) REFERENCES food_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("CREATE INDEX index_meal_logs_foodEntryId ON meal_logs(foodEntryId)")
                db.execSQL("CREATE INDEX index_meal_logs_dateTimestamp ON meal_logs(dateTimestamp)")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN exerciseId TEXT")
                db.execSQL("ALTER TABLE workout_logs ADD COLUMN isCompound INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX index_workout_logs_exerciseId_dateTimestamp ON workout_logs(exerciseId, dateTimestamp)")
                WorkoutCatalog.exercisesById.values.forEach { exercise ->
                    db.execSQL(
                        "UPDATE workout_logs SET exerciseId = ?, isCompound = ? WHERE exerciseName = ?",
                        arrayOf<Any>(exercise.id, if (exercise.isCompound) 1 else 0, exercise.name),
                    )
                }
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE daily_conditions (date TEXT NOT NULL PRIMARY KEY, sleepMinutes INTEGER NOT NULL)")
            }
        }

        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
