package com.mcclabs.gimmer.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mcclabs.gimmer.nutrition.NutritionRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WorkoutRepository(private val context: Context,
    private val database: WorkoutDatabase = WorkoutDatabase.getInstance(context)) {
    private val dao = database.workoutLogDao()
    private val weightDao = database.bodyweightDao()
    private val nutrition = NutritionRepository(database)
    private val conditionDao = database.dailyConditionDao()
    private val routineDao = database.workoutRoutineDao()
    val bodyweights = weightDao.observeAll()
    private val photoDao = database.progressPhotoDao()
    internal val progressPhotos = photoDao.observe()
    internal suspend fun photoImage(id: String) = photoDao.image(id)
    internal suspend fun addPhoto(uri: android.net.Uri, date: java.time.LocalDate) {
        val photo = ProgressPhotoImage.import(context.contentResolver, uri, date)
        check(photoDao.insert(photo) != -1L)
    }
    internal suspend fun updatePhotoDate(id: String, date: java.time.LocalDate) = photoDao.updateDate(id, date.toString())
    internal suspend fun deletePhoto(id: String) = photoDao.delete(id)
    val routine = routineDao.observe()
        .map { it?.let { record -> RoutineCodec.decode(record.json) } ?: WorkoutCatalog.days }
        .flowOn(Dispatchers.Default)
    fun workoutLogs(exercise: Exercise, day: String, start: Long, end: Long) =
        dao.observeWorkoutLogs(exercise.name, day, start, end, exercise.id)
    suspend fun latestSet(exercise: Exercise) = dao.latestSet(exercise.id, exercise.name)
    suspend fun countWorkingSets(exercise: Exercise, day: String, start: Long, end: Long) =
        dao.countWorkingSets(exercise.id, exercise.name, day, start, end)
    fun activity(start: Long, end: Long) = dao.observeActivity(start, end)
    fun summaries(name: String, day: String, muscle: String, includeWarmups: Boolean, localizedMatches: List<Exercise>) =
        dao.observeSummaries(name, day, muscle, includeWarmups,
            localizedMatches.map { it.name }.distinct(), localizedMatches.map { it.id }.distinct())
    fun exerciseLogs(name: String, day: String, muscle: String, includeWarmups: Boolean, exerciseId: String?) =
        dao.observeExercise(name, day, muscle, includeWarmups, exerciseId)
    suspend fun save(log: WorkoutLog) { requireValidLog(log); dao.insert(log) }
    suspend fun update(log: WorkoutLog) { requireValidLog(log); dao.update(log) }
    suspend fun delete(log: WorkoutLog) = dao.delete(log)
    suspend fun saveBodyweight(log: BodyweightLog) { requireValidBodyweight(log); weightDao.upsert(log) }
    fun dailyCondition(date: String) = conditionDao.observe(date)
    suspend fun saveDailyCondition(condition: DailyCondition) {
        condition.requireValid()
        conditionDao.upsert(condition)
    }
    suspend fun replaceRoutine(days: List<WorkoutDay>) {
        val document = withContext(Dispatchers.Default) { RoutineCodec.toJson(days).also { RoutineCodec.decode(it) } }
        routineDao.replace(WorkoutRoutine(json = document.toString()))
    }

    private suspend fun snapshot(): BackupSnapshot = database.withTransaction {
        database.readSnapshot()
    }
    suspend fun exportAsJson(): String = withContext(Dispatchers.IO) {
        val data = snapshot()
        BackupCodec.encode(data.workouts, data.bodyweights, data.meals,
            dailyConditions = data.dailyConditions, routine = data.routine)
    }

    private suspend fun merge(data: BackupSnapshot, photos: ProgressPhotoDao? = null): Int {
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
            val newRoutine = if (data.routine != null && routineDao.get() == null) {
                routineDao.replace(WorkoutRoutine(json = RoutineCodec.toJson(data.routine).toString()))
                1
            } else 0
            var newPhotos = 0
            if (photos != null) {
                for (id in photos.ids()) {
                    val photo = checkNotNull(photos.get(id))
                    ProgressPhotoImage.validate(photo)
                    if (photoDao.insert(photo) != -1L && photo.jpeg.isNotEmpty()) newPhotos++
                }
            }
            newLogs.size + newWeights.size + newMeals + newConditions + newRoutine + newPhotos
        }
    }
    suspend fun restore(json: String): Int = withContext(Dispatchers.IO) {
        merge(BackupCodec.decodeSnapshot(json))
    }

    suspend fun exportDatabase(): ByteArray = checkNotNull(exportDatabase(skipEmpty = false))

    internal suspend fun exportNonEmptyDatabase(): ByteArray? = exportDatabase(skipEmpty = true)

    private suspend fun exportDatabase(skipEmpty: Boolean): ByteArray? =
        withDatabaseSnapshot(skipEmpty) { file ->
            require(file.length() <= MAX_BACKUP_BYTES)
            file.readBytes()
        }

    // Copy individual rows in a consistent read, then stream the closed SQLite file to Drive.
    internal suspend fun <T> withDatabaseSnapshot(skipEmpty: Boolean = true, consume: suspend (File) -> T): T? =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("gimmer-snapshot-", ".db", context.cacheDir)
            try {
                val saved = openTemporaryDatabase(file) { backup ->
                    database.withTransaction {
                        val data = database.readSnapshot()
                        val photoIds = photoDao.ids()
                        if (skipEmpty && data.workouts.isEmpty() && data.bodyweights.isEmpty() &&
                            data.meals.isEmpty() && data.dailyConditions.isEmpty() && data.routine == null &&
                            photoIds.isEmpty()) {
                            return@withTransaction false
                        }
                        backup.withTransaction {
                            backup.workoutLogDao().insertAll(data.workouts)
                            data.bodyweights.forEach { backup.bodyweightDao().upsert(it) }
                            NutritionRepository(backup).merge(data.meals)
                            backup.dailyConditionDao().insertMissing(data.dailyConditions)
                            data.routine?.let {
                                backup.workoutRoutineDao().replace(WorkoutRoutine(json = RoutineCodec.toJson(it).toString()))
                            }
                            for (id in photoIds) {
                                backup.progressPhotoDao().insert(checkNotNull(photoDao.get(id)))
                            }
                        }
                        true
                    }
                }
                if (saved) {
                    require(file.length() <= MAX_DRIVE_BACKUP_BYTES)
                    consume(file)
                } else null
            } finally {
                context.deleteDatabase(file.absolutePath)
            }
        }

    suspend fun restoreDatabase(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        require(bytes.size in 100..MAX_BACKUP_BYTES)
        val file = File.createTempFile("gimmer-restore-", ".db", context.cacheDir)
        try {
            file.writeBytes(bytes)
            restoreDatabase(file)
        } finally {
            context.deleteDatabase(file.absolutePath)
        }
    }

    internal suspend fun restoreDatabase(file: File): Int = withContext(Dispatchers.IO) {
        require(file.length() in 100..MAX_DRIVE_BACKUP_BYTES)
        val header = "SQLite format 3\u0000".toByteArray()
        val actualHeader = ByteArray(header.size)
        java.io.DataInputStream(file.inputStream()).use { it.readFully(actualHeader) }
        require(actualHeader.contentEquals(header))
        openTemporaryDatabase(file) { backup ->
            backup.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok")
            }
            merge(backup.readSnapshot(), backup.progressPhotoDao())
        }
    }

    private suspend fun WorkoutDatabase.readSnapshot() = BackupSnapshot(
        workoutLogDao().getAll(), bodyweightDao().getAll(), nutritionDao().allMeals(), dailyConditionDao().getAll(),
        workoutRoutineDao().get()?.json?.let(RoutineCodec::decode),
    )

    private suspend fun <T> openTemporaryDatabase(file: File, block: suspend (WorkoutDatabase) -> T): T {
        val backup = Room.databaseBuilder(context, WorkoutDatabase::class.java, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(*WorkoutDatabase.MIGRATIONS).build()
        try {
            return block(backup)
        } finally {
            backup.close()
        }
    }

    companion object {
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        const val MAX_DRIVE_BACKUP_BYTES = 256L * 1024 * 1024
    }
}
