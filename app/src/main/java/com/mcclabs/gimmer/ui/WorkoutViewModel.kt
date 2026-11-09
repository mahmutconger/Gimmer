package com.mcclabs.gimmer.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mcclabs.gimmer.data.*
import com.mcclabs.gimmer.backup.*
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogFilter(val query: String = "", val day: String = "", val muscle: String = "",
    val includeWarmups: Boolean = false)

internal fun workingSetVolume(logs: List<WorkoutLog>): Double =
    logs.sumOf { if (it.isWarmup) 0.0 else it.weight * it.reps * it.sets }

internal data class ExerciseHistory(
    val logs: List<WorkoutLog>,
    val workingSets: Long,
    val volume: Double,
    val first: WorkoutLog?,
    val last: WorkoutLog?,
    val dailyProgress: List<DailyExerciseProgress>,
)

internal data class DailyExerciseProgress(val date: LocalDate, val weight: Double, val reps: Int)

internal data class DriveRestoreOptions(val files: List<DriveBackupFile>, val deviceFileName: String)

internal fun dailyExerciseProgress(logs: List<WorkoutLog>, zone: ZoneId): List<DailyExerciseProgress> {
    val bestByDay = sortedMapOf<LocalDate, WorkoutLog>()
    for (log in logs) {
        if (log.isWarmup) continue
        val date = Instant.ofEpochMilli(log.dateTimestamp).atZone(zone).toLocalDate()
        val previous = bestByDay[date]
        if (previous == null || log.weight > previous.weight ||
            (log.weight == previous.weight && log.reps > previous.reps)) {
            bestByDay[date] = log
        }
    }
    return bestByDay.map { (date, log) -> DailyExerciseProgress(date, log.weight, log.reps) }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class WorkoutViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = WorkoutRepository(application)
    private val drivePreferences = DriveBackupPreferences.get(application)
    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val messages = eventChannel.receiveAsFlow()
    val restTimer = RestTimer(savedState, viewModelScope) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31)
                application.getSystemService(VibratorManager::class.java).defaultVibrator
            else application.getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    val days = repository.routine.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bodyweights = repository.bodyweights.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filter = MutableStateFlow(LogFilter())
    private val searchQuery = filter.map { it.query.trim() }.distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else 180L }
    private val selectedFilters = filter.map { it.copy(query = "") }.distinctUntilChanged()
    val progressSummaries = combine(searchQuery, selectedFilters, days) { query, options, routine ->
            options.copy(query = query) to routine.orEmpty()
        }.flatMapLatest { (options, routine) ->
            val query = options.query.lowercase(Turkish)
            val matches = (WorkoutCatalog.exercisesById.values + routine.flatMap { it.exercises })
                .filter { exercise -> query.isNotEmpty() && exercise.displayName.lowercase(Turkish).contains(query) }
            repository.summaries(options.query, options.day, options.muscle, options.includeWarmups, matches)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val exportedJson = MutableStateFlow("")
    val cloudBusy = MutableStateFlow(false)
    val savingJson = MutableStateFlow(false)
    internal val automaticBackup = drivePreferences.automaticBackup
    internal val restoreOptions = MutableStateFlow<DriveRestoreOptions?>(null)
    private var restoreToken: String? = null

    init {
        val settings = automaticBackup.value
        val account = settings.account
        if (account != null) viewModelScope.launch {
            try {
                scheduleDriveBackup(application, settings.interval)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                drivePreferences.report(account, BackupStatus.FAILED)
            }
        }
    }

    fun workoutLogs(exercise: Exercise, day: String, start: Long, end: Long) =
        repository.workoutLogs(exercise, day, start, end)

    suspend fun latestSet(exercise: Exercise) = repository.latestSet(exercise)

    internal fun monthlyActivity(month: YearMonth, zone: ZoneId): Flow<Map<LocalDate, Long>> {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return repository.activity(start, end).map { rows ->
            val dailySets = mutableMapOf<LocalDate, Long>()
            for (row in rows) {
                val date = Instant.ofEpochMilli(row.dateTimestamp).atZone(zone).toLocalDate()
                dailySets[date] = (dailySets[date] ?: 0L) + row.sets
            }
            dailySets
        }.flowOn(Dispatchers.Default)
    }

    internal fun exerciseHistory(name: String, exerciseId: String?) = selectedFilters.flatMapLatest {
        repository.exerciseLogs(name, it.day, it.muscle, it.includeWarmups, exerciseId)
    }.map { logs ->
        ExerciseHistory(
            logs = logs.asReversed(),
            workingSets = logs.sumOf { if (it.isWarmup) 0L else it.sets.toLong() },
            volume = workingSetVolume(logs),
            first = logs.firstOrNull { !it.isWarmup },
            last = logs.lastOrNull { !it.isWarmup },
            dailyProgress = dailyExerciseProgress(logs, ZoneId.systemDefault()),
        )
    }.flowOn(Dispatchers.Default)

    private fun perform(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); eventChannel.send(success) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { eventChannel.send("İşlem tamamlanamadı. Lütfen yeniden deneyin.") }
    }

    fun saveWorkout(exercise: Exercise, dayName: String, weight: Double, reps: Int, rir: Int,
        isWarmup: Boolean, note: String?, partner: Exercise? = null, firstInPair: Boolean = false,
        onSaved: (Exercise?) -> Unit) = perform("Set kaydedildi.") {
        require(rir in 0..4)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val next = partner?.takeUnless { isWarmup }?.takeIf {
            repository.countWorkingSets(it, dayName, today.atStartOfDay(zone).toInstant().toEpochMilli(),
                today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()) < it.targetSets
        }
        repository.save(WorkoutLog(exerciseName = exercise.name, exerciseId = exercise.id,
            isCompound = exercise.isCompound, muscleGroup = exercise.muscleGroup,
            dayName = dayName, weight = weight, sets = 1, reps = reps, rir = rir, isWarmup = isWarmup,
            note = note?.trim()?.ifBlank { null }, dateTimestamp = now.toEpochMilli()))
        if (!firstInPair || next == null) {
            restTimer.startFor(if (!isWarmup && !firstInPair && partner?.isCompound == true) partner else exercise)
        } else {
            restTimer.cancel()
        }
        onSaved(next)
    }
    fun saveBodyweight(weight: Double, onSaved: () -> Unit) = perform("Vücut ağırlığı kaydedildi.") {
        repository.saveBodyweight(BodyweightLog(LocalDate.now().toString(), weight))
        onSaved()
    }
    fun dailyCondition(date: LocalDate) = repository.dailyCondition(date.toString())

    fun saveSleep(date: LocalDate, minutes: Int) = perform("Gece uykusu kaydedildi.") {
        repository.saveDailyCondition(DailyCondition(date.toString(), minutes))
    }

    fun replaceRoutine(days: List<WorkoutDay>, onSaved: () -> Unit) = perform("Antrenman programı güncellendi.") {
        repository.replaceRoutine(days)
        onSaved()
    }
    fun updateLog(log: WorkoutLog, onSaved: () -> Unit) = perform("Kayıt güncellendi.") {
        repository.update(log); onSaved()
    }
    fun deleteLog(log: WorkoutLog, onDeleted: () -> Unit) = perform("Kayıt silindi.") {
        repository.delete(log); onDeleted()
    }
    fun prepareExport() = perform("Veriler dışa aktarıldı.") { exportedJson.value = repository.exportAsJson() }

    fun saveJsonDocument(uri: Uri) {
        if (savingJson.value) return
        savingJson.value = true
        perform("JSON dosyası kaydedildi.") {
            val json = repository.exportAsJson()
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use {
                    it.write(json)
                }
            }
        }.invokeOnCompletion { savingJson.value = false }
    }

    internal val progressPhotos = repository.progressPhotos
    internal val savingPhoto = MutableStateFlow(false)
    internal suspend fun progressPhotoImage(id: String) = repository.photoImage(id)

    internal fun addProgressPhoto(uri: Uri, date: LocalDate) {
        if (!savingPhoto.compareAndSet(expect = false, update = true)) return
        perform("Fotoğraf kaydedildi.") { repository.addPhoto(uri, date) }
            .invokeOnCompletion { savingPhoto.value = false }
    }

    internal fun changePhotoDate(id: String, date: LocalDate) =
        perform("Fotoğraf tarihi güncellendi.") { repository.updatePhotoDate(id, date) }

    internal fun removeProgressPhoto(id: String) =
        perform("Fotoğraf silindi.") { repository.deletePhoto(id) }

    internal fun enableAutomaticBackup(account: String) = driveAction {
        val interval = automaticBackup.value.interval
        drivePreferences.configure(account, interval, resetStatus = true)
        try {
            scheduleDriveBackup(getApplication(), interval, runSoon = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            drivePreferences.report(account, BackupStatus.FAILED)
            throw error
        }
        "Otomatik Drive yedeklemesi açıldı."
    }

    internal fun disableAutomaticBackup() = driveAction {
        drivePreferences.configure(null, automaticBackup.value.interval)
        scheduleDriveBackup(getApplication(), null)
        "Otomatik Drive yedeklemesi kapatıldı."
    }

    internal fun setBackupInterval(interval: BackupInterval) = driveAction {
        val previous = automaticBackup.value
        if (previous.account != null) scheduleDriveBackup(getApplication(), interval)
        drivePreferences.configure(previous.account, interval)
        null
    }

    internal fun backupToDrive(token: String, account: String?) = driveAction {
        val result = DriveBackupCoordinator.backup(getApplication(), token)
        if (account != null) drivePreferences.report(account, result.status, result.backupTimestamp)
        when (result) {
            DriveBackupOutcome.Empty -> "Yedeklenecek kayıt bulunamadı."
            is DriveBackupOutcome.Saved -> if (result.uploaded) "Google Drive yedeği oluşturuldu."
                else "Drive yedeğin zaten güncel."
        }
    }

    internal fun listDriveBackups(token: String) = driveAction {
        val files = DriveBackupClient(token).listBackups()
        if (files.isEmpty()) {
            "Bu Google hesabında Gimmer yedeği bulunamadı."
        } else {
            val deviceFileName = drivePreferences.deviceFileName()
            restoreToken = token
            restoreOptions.value = DriveRestoreOptions(files, deviceFileName)
            null
        }
    }

    internal fun dismissRestoreOptions() {
        restoreToken = null
        restoreOptions.value = null
    }

    internal fun restoreDriveBackup(fileId: String) {
        val token = restoreToken ?: return
        val file = restoreOptions.value?.files?.firstOrNull { it.id == fileId } ?: return
        driveAction {
            dismissRestoreOptions()
            val count = DriveBackupCoordinator.restore(getApplication(), token, file)
            "$count yeni kayıt geri yüklendi."
        }
    }

    private fun driveAction(block: suspend () -> String?) {
        if (cloudBusy.value) return
        cloudBusy.value = true
        viewModelScope.launch {
            try {
                block()?.let { eventChannel.send(it) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                eventChannel.send("Drive işlemi tamamlanamadı. Bağlantınızı ve hesap izinlerini kontrol edin.")
            } finally { cloudBusy.value = false }
        }
    }
}
