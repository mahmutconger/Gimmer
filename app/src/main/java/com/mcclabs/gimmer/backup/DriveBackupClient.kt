package com.mcclabs.gimmer.backup

import com.mcclabs.gimmer.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

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
    private suspend fun request(
        url: String,
        method: String = "GET",
        body: ByteArray? = null,
        contentType: String = "application/octet-stream",
    ): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw DriveHttpException(status)
            if (status == 204) return@withContext ByteArray(0)
            connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= WorkoutRepository.MAX_BACKUP_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
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

    suspend fun upload(name: String, database: ByteArray): DriveBackupFile {
        val bodyAndBoundary = withContext(Dispatchers.IO) {
            require(database.size <= WorkoutRepository.MAX_BACKUP_BYTES)
            val boundary = UUID.randomUUID().toString()
            val metadata = JSONObject().put("name", name)
                .put("parents", JSONArray().put("appDataFolder"))
            val body = ByteArrayOutputStream().apply {
                write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                    metadata + "\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
                write(database)
                write("\r\n--$boundary--\r\n".toByteArray())
            }.toByteArray()
            body to boundary
        }
        val (body, boundary) = bodyAndBoundary
        val response = request("$UPLOAD?uploadType=multipart&fields=${encode(FILE_FIELDS)}",
            "POST", body, "multipart/related; boundary=$boundary")
        return JSONObject(response.toString(Charsets.UTF_8)).backupFile()
    }

    suspend fun download(file: DriveBackupFile): ByteArray =
        request("$FILES/${encode(file.id)}?alt=media")

    suspend fun delete(file: DriveBackupFile) {
        request("$FILES/${encode(file.id)}", method = "DELETE")
    }

    private fun JSONObject.backupFile() = DriveBackupFile(
        getString("id"), getString("name"), Instant.parse(getString("modifiedTime")),
        optString("md5Checksum").ifBlank { null },
    )

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
