package com.mcclabs.gimmer.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mcclabs.gimmer.nutrition.NutritionRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkoutRepository(private val context: Context,
    private val database: WorkoutDatabase = WorkoutDatabase.getInstance(context)) {
    private val dao = database.workoutLogDao()
    private val weightDao = database.bodyweightDao()
    private val nutrition = NutritionRepository(database)
    private val conditionDao = database.dailyConditionDao()
    val bodyweights = weightDao.observeAll()
    fun workoutLogs(exercise: Exercise, day: String, start: Long, end: Long) =
        dao.observeWorkoutLogs(exercise.name, day, start, end, exercise.id)
    suspend fun latestSet(exercise: Exercise) = dao.latestSet(exercise.id, exercise.name)
    fun activity(start: Long, end: Long) = dao.observeActivity(start, end)
    fun summaries(name: String, day: String, muscle: String, includeWarmups: Boolean, localizedMatches: List<String>) =
        dao.observeSummaries(name, day, muscle, includeWarmups, localizedMatches)
    fun exerciseLogs(name: String, day: String, muscle: String, includeWarmups: Boolean) =
        dao.observeExercise(name, day, muscle, includeWarmups)
    suspend fun save(log: WorkoutLog) { requireValidLog(log); dao.insert(log) }
    suspend fun update(log: WorkoutLog) { requireValidLog(log); dao.update(log) }
    suspend fun delete(log: WorkoutLog) = dao.delete(log)
    suspend fun saveBodyweight(log: BodyweightLog) { requireValidBodyweight(log); weightDao.upsert(log) }
    fun dailyCondition(date: String) = conditionDao.observe(date)
    suspend fun saveDailyCondition(condition: DailyCondition) {
        condition.requireValid()
        conditionDao.upsert(condition)
    }

    private suspend fun snapshot(): BackupSnapshot = database.withTransaction {
        database.readSnapshot()
    }
    suspend fun exportAsJson(): String = withContext(Dispatchers.IO) {
        val data = snapshot()
        BackupCodec.encode(data.workouts, data.bodyweights, data.meals, dailyConditions = data.dailyConditions)
    }

    private suspend fun merge(data: BackupSnapshot): Int {
        data.workouts.forEach(::requireValidLog)
        data.bodyweights.forEach(::requireValidBodyweight)
        data.dailyConditions.forEach { it.requireValid() }
        return database.withTransaction {
            val existing = dao.getAll().map { it.copy(id = 0) }.toMutableSet()
            val newLogs = data.workouts.map { it.copy(id = 0) }.filter { existing.add(it) }
            val dates = weightDao.getAll().map { it.date }.toSet()
            val newWeights = data.bodyweights.filter { it.date !in dates }.distinctBy { it.date }
            dao.insertAll(newLogs)
            weightDao.insertMissing(newWeights)
            val newMeals = nutrition.merge(data.meals)
            val newConditions = conditionDao.insertMissing(data.dailyConditions).count { it != -1L }
            newLogs.size + newWeights.size + newMeals + newConditions
        }
    }
    suspend fun restore(json: String): Int = withContext(Dispatchers.IO) {
        merge(BackupCodec.decodeSnapshot(json))
    }

    suspend fun exportDatabase(): ByteArray = checkNotNull(exportDatabase(skipEmpty = false))

    internal suspend fun exportNonEmptyDatabase(): ByteArray? = exportDatabase(skipEmpty = true)

    // Build a closed, standalone SQLite snapshot; never copy a live WAL database file.
    private suspend fun exportDatabase(skipEmpty: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        val data = snapshot()
        if (skipEmpty && data.workouts.isEmpty() && data.bodyweights.isEmpty() &&
            data.meals.isEmpty() && data.dailyConditions.isEmpty()) {
            return@withContext null
        }
        withTemporaryDatabase { backup, file ->
            backup.withTransaction {
                backup.workoutLogDao().insertAll(data.workouts)
                data.bodyweights.forEach { backup.bodyweightDao().upsert(it) }
                NutritionRepository(backup).merge(data.meals)
                backup.dailyConditionDao().insertMissing(data.dailyConditions)
            }
            backup.close()
            require(file.length() <= MAX_BACKUP_BYTES)
            file.readBytes()
        }
    }

    suspend fun restoreDatabase(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        require(bytes.size in 100..MAX_BACKUP_BYTES)
        require(bytes.take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
        val data = withTemporaryDatabase(bytes) { backup, _ ->
            backup.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok")
            }
            backup.withTransaction { backup.readSnapshot() }
        }
        merge(data)
    }

    private suspend fun WorkoutDatabase.readSnapshot() = BackupSnapshot(
        workoutLogDao().getAll(), bodyweightDao().getAll(), nutritionDao().allMeals(), dailyConditionDao().getAll(),
    )

    private suspend fun <T> withTemporaryDatabase(bytes: ByteArray? = null,
        block: suspend (WorkoutDatabase, File) -> T): T {
        val file = File.createTempFile("gimmer-snapshot-", ".db", context.cacheDir)
        var backup: WorkoutDatabase? = null
        try {
            if (bytes != null) file.writeBytes(bytes)
            val opened = Room.databaseBuilder(context, WorkoutDatabase::class.java, file.absolutePath)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(*WorkoutDatabase.MIGRATIONS).build()
            backup = opened
            return block(opened, file)
        } finally {
            backup?.close()
            context.deleteDatabase(file.absolutePath)
        }
    }

    companion object { const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 }
}
