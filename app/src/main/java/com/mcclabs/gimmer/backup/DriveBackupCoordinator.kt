package com.mcclabs.gimmer.backup

import android.content.Context
import com.mcclabs.gimmer.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

internal const val BACKUPS_PER_DEVICE = 5

internal sealed interface DriveBackupOutcome {
    data object Empty : DriveBackupOutcome
    data class Saved(val file: DriveBackupFile, val uploaded: Boolean) : DriveBackupOutcome
}

internal object DriveBackupCoordinator {
    private val operation = Mutex()

    suspend fun backup(context: Context, token: String, beforeUpload: () -> Unit = {}): DriveBackupOutcome =
        operation.withLock {
            val database = WorkoutRepository(context).exportNonEmptyDatabase() ?: return@withLock DriveBackupOutcome.Empty
            val name = DriveBackupPreferences.get(context).deviceFileName()
            val client = DriveBackupClient(token)
            val backups = client.listBackups(deviceFileName = name)
            val checksum = checksum(database)
            val latest = backups.firstOrNull()
            if (latest?.checksum == checksum) return@withLock DriveBackupOutcome.Saved(latest, uploaded = false)

            beforeUpload()
            val created = client.upload(name, database)
            check(created.name == name && created.checksum == checksum) { "Drive did not verify the uploaded backup." }

            // Only prune this installation's older snapshots, after verifying the replacement.
            for (file in backups.filter { it.name == name && it.checksum != null }
                .drop(BACKUPS_PER_DEVICE - 1)) {
                try {
                    client.delete(file)
                } catch (_: IOException) {
                    // The new snapshot is already complete. Cleanup can wait until the next upload.
                }
            }
            DriveBackupOutcome.Saved(created, uploaded = true)
        }

    suspend fun restore(context: Context, token: String, file: DriveBackupFile): Int = operation.withLock {
        val bytes = DriveBackupClient(token).download(file)
        require(file.checksum == null || checksum(bytes) == file.checksum)
        val repository = WorkoutRepository(context)
        if (file.name.endsWith(".json")) repository.restore(bytes.toString(Charsets.UTF_8))
        else repository.restoreDatabase(bytes)
    }

    private suspend fun checksum(bytes: ByteArray): String = withContext(Dispatchers.Default) {
        // Drive exposes MD5 for blob integrity checks and identical-snapshot detection.
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
