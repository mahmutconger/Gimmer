package com.mcclabs.gimmer.backup

import android.content.Context
import com.mcclabs.gimmer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

internal enum class BackupInterval(val hours: Long, val label: Int) {
    HOURLY(1, R.string.backup_hourly),
    SIX_HOURS(6, R.string.backup_six_hours),
    DAILY(24, R.string.backup_daily),
    WEEKLY(168, R.string.backup_weekly),
}

internal enum class BackupStatus {
    WAITING, SAVED, EMPTY, AUTH_REQUIRED, RETRYING, FAILED,
}

internal data class AutomaticBackupState(
    val account: String?,
    val interval: BackupInterval,
    val status: BackupStatus,
    val lastBackupAt: Long?,
)

internal class DriveBackupPreferences private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("drive_backup", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        AutomaticBackupState(
            account = preferences.getString("account", null),
            interval = BackupInterval.entries.firstOrNull { it.hours == preferences.getLong("hours", 24) }
                ?: BackupInterval.DAILY,
            status = BackupStatus.entries.firstOrNull { it.name == preferences.getString("status", null) }
                ?: BackupStatus.WAITING,
            lastBackupAt = preferences.getLong("lastBackupAt", 0).takeIf { it > 0 },
        ),
    )
    val automaticBackup = state.asStateFlow()

    suspend fun configure(account: String?, interval: BackupInterval, resetStatus: Boolean = false) =
        update { previous ->
            previous.copy(
                account = account,
                interval = interval,
                status = if (resetStatus || account != previous.account) BackupStatus.WAITING else previous.status,
                lastBackupAt = previous.lastBackupAt.takeIf { account == previous.account },
            )
        }

    suspend fun report(account: String, status: BackupStatus, backupAt: Long? = null) =
        update { previous ->
            if (previous.account == account) {
                previous.copy(status = status, lastBackupAt = backupAt ?: previous.lastBackupAt)
            } else previous
        }

    private suspend fun update(transform: (AutomaticBackupState) -> AutomaticBackupState) = withContext(Dispatchers.IO) {
        synchronized(this@DriveBackupPreferences) {
            val previous = state.value
            val updated = transform(previous)
            if (previous != updated) {
                check(preferences.edit()
                    .putString("account", updated.account)
                    .putLong("hours", updated.interval.hours)
                    .putString("status", updated.status.name)
                    .putLong("lastBackupAt", updated.lastBackupAt ?: 0)
                    .commit())
                state.value = updated
            }
        }
    }

    suspend fun deviceFileName(): String = withContext(Dispatchers.IO) {
        synchronized(this@DriveBackupPreferences) {
            val device = preferences.getString("device", null) ?: UUID.randomUUID().toString().also {
                check(preferences.edit().putString("device", it).commit())
            }
            "gimmer-v3-$device.db"
        }
    }

    companion object {
        @Volatile
        private var instance: DriveBackupPreferences? = null

        fun get(context: Context): DriveBackupPreferences = instance ?: synchronized(this) {
            instance ?: DriveBackupPreferences(context.applicationContext).also { instance = it }
        }
    }
}
