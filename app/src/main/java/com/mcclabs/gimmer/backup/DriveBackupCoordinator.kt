package com.mcclabs.gimmer.backup

import android.content.Context
import com.mcclabs.gimmer.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import kotlinx.coroutines.ensureActive
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
            WorkoutRepository(context).withDatabaseSnapshot { database ->
                val name = DriveBackupPreferences.get(context).deviceFileName()
                val client = DriveBackupClient(token)
                val backups = client.listBackups(deviceFileName = name)
                val checksum = checksum(database)
                val latest = backups.firstOrNull()
                if (latest?.checksum == checksum) {
                    return@withDatabaseSnapshot DriveBackupOutcome.Saved(latest, uploaded = false)
                }

                beforeUpload()
                val created = client.upload(name, database)
                check(created.name == name && created.checksum == checksum) { "Drive did not verify the uploaded backup." }

                // Only prune this installation's older snapshots, after verifying the replacement.
                for (file in backups.filter { it.name == name && it.checksum != null }.drop(BACKUPS_PER_DEVICE - 1)) {
                    try {
                        client.delete(file)
                    } catch (_: IOException) {
                        // The new snapshot is already complete. Cleanup can wait until the next upload.
                    }
                }
                DriveBackupOutcome.Saved(created, uploaded = true)
            } ?: DriveBackupOutcome.Empty
        }

    suspend fun restore(context: Context, token: String, file: DriveBackupFile): Int = operation.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = File.createTempFile("gimmer-restore-", ".db", context.cacheDir)
            try {
                DriveBackupClient(token).download(file, snapshot)
                require(file.checksum == null || checksum(snapshot) == file.checksum)
                val repository = WorkoutRepository(context)
                if (file.name.endsWith(".json")) repository.restore(snapshot.readText())
                else repository.restoreDatabase(snapshot)
            } finally {
                context.deleteDatabase(snapshot.absolutePath)
            }
        }
    }

    private suspend fun checksum(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
