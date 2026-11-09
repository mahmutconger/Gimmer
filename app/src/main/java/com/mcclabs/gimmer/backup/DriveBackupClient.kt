package com.mcclabs.gimmer.backup

import com.mcclabs.gimmer.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

private const val FILES = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
private const val FILE_FIELDS = "id,name,modifiedTime,md5Checksum"
private val DeviceBackupName = Regex("gimmer-v3-[0-9a-f-]{36}\\.db")

internal data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedAt: Instant,
    val checksum: String?,
) {
    val legacy: Boolean get() = name == "gimmer-v3.db" || name == "gimmer-backup.json"
}

internal class DriveHttpException(val status: Int) : IOException("Drive HTTP $status")

/** All requests are limited to the authorized account's appDataFolder. */
internal class DriveBackupClient(private val accessToken: String) {
    private suspend fun <T> exchange(
        url: String,
        method: String,
        write: ((HttpURLConnection) -> Unit)? = null,
        read: (HttpURLConnection) -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            write?.invoke(connection)
            val status = connection.responseCode
            if (status !in 200..299) throw DriveHttpException(status)
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun copyLimited(source: InputStream, target: OutputStream, limit: Long, checkActive: () -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            checkActive()
            val count = source.read(buffer)
            if (count < 0) return
            size += count
            require(size <= limit)
            target.write(buffer, 0, count)
        }
    }

    private suspend fun request(url: String, method: String = "GET"): ByteArray {
        val context = currentCoroutineContext()
        return exchange(url, method) { connection ->
            val output = ByteArrayOutputStream()
            connection.inputStream.use {
                copyLimited(it, output, WorkoutRepository.MAX_BACKUP_BYTES.toLong()) { context.ensureActive() }
            }
            output.toByteArray()
        }
    }

    suspend fun listBackups(deviceFileName: String? = null): List<DriveBackupFile> {
        val names = if (deviceFileName != null) "name = '$deviceFileName'"
            else "(name = 'gimmer-v3.db' or name = 'gimmer-backup.json' or name contains 'gimmer-v3-')"
        val query = encode("trashed = false and $names")
        val fields = encode("nextPageToken,files($FILE_FIELDS)")
        val backups = mutableListOf<DriveBackupFile>()
        var nextPage: String? = null
        do {
            val url = "$FILES?spaces=appDataFolder&q=$query&fields=$fields&orderBy=modifiedTime%20desc&pageSize=100"
            val pageUrl = nextPage?.let { "$url&pageToken=${encode(it)}" } ?: url
            val page = JSONObject(request(pageUrl).toString(Charsets.UTF_8))
            val files = page.getJSONArray("files")
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i).backupFile()
                if (file.legacy || DeviceBackupName.matches(file.name)) backups.add(file)
            }
            nextPage = page.optString("nextPageToken").ifBlank { null }
        } while (nextPage != null)
        return backups.sortedByDescending { it.modifiedAt }
    }

    suspend fun upload(name: String, database: File): DriveBackupFile {
        val length = database.length()
        require(length <= WorkoutRepository.MAX_DRIVE_BACKUP_BYTES)
        val context = currentCoroutineContext()
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put("appDataFolder"))
            .toString().toByteArray()
        val session = exchange("$UPLOAD?uploadType=resumable&fields=${encode(FILE_FIELDS)}", "POST", write = { connection ->
            connection.doOutput = true
            connection.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-Upload-Content-Length", length.toString())
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setFixedLengthStreamingMode(metadata.size)
            connection.outputStream.use { it.write(metadata) }
        }) { connection -> URL(checkNotNull(connection.getHeaderField("Location"))) }
        check(session.protocol == "https" && session.host == "www.googleapis.com" &&
            session.path == "/upload/drive/v3/files" && session.port in setOf(-1, 443))

        return exchange(session.toString(), "PUT", write = { connection ->
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(length)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { output ->
                database.inputStream().use { copyLimited(it, output, length) { context.ensureActive() } }
            }
        }) { connection ->
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).backupFile()
        }
    }

    suspend fun download(file: DriveBackupFile, destination: File) {
        val context = currentCoroutineContext()
        val limit = if (file.name.endsWith(".json")) WorkoutRepository.MAX_BACKUP_BYTES.toLong()
            else WorkoutRepository.MAX_DRIVE_BACKUP_BYTES
        exchange("$FILES/${encode(file.id)}?alt=media", "GET") { connection ->
            connection.inputStream.use { source ->
                destination.outputStream().use { copyLimited(source, it, limit) { context.ensureActive() } }
            }
        }
    }

    suspend fun delete(file: DriveBackupFile) {
        exchange("$FILES/${encode(file.id)}", "DELETE") {}
    }

    private fun JSONObject.backupFile() = DriveBackupFile(
        getString("id"), getString("name"), Instant.parse(getString("modifiedTime")),
        optString("md5Checksum").ifBlank { null },
    )

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
