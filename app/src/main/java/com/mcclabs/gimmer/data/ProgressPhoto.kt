package com.mcclabs.gimmer.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

@Entity(tableName = "progress_photos", indices = [Index("date")])
internal data class ProgressPhoto(
    @PrimaryKey val id: String,
    val date: String,
    val jpeg: ByteArray,
)

internal data class ProgressPhotoEntry(val id: String, val date: String)

@Dao
internal interface ProgressPhotoDao {
    @Query("SELECT id, date FROM progress_photos WHERE length(jpeg) > 0 ORDER BY date DESC, id")
    fun observe(): Flow<List<ProgressPhotoEntry>>

    @Query("SELECT id FROM progress_photos ORDER BY id")
    suspend fun ids(): List<String>

    @Query("SELECT * FROM progress_photos WHERE id = :id")
    suspend fun get(id: String): ProgressPhoto?

    @Query("SELECT jpeg FROM progress_photos WHERE id = :id AND length(jpeg) > 0")
    suspend fun image(id: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(photo: ProgressPhoto): Long

    @Query("UPDATE progress_photos SET date = :date WHERE id = :id AND length(jpeg) > 0")
    suspend fun updateDate(id: String, date: String)

    // Keep the ID so older backups cannot reintroduce a photo deleted on this installation.
    @Query("UPDATE progress_photos SET jpeg = X'' WHERE id = :id")
    suspend fun delete(id: String)
}

internal object ProgressPhotoImage {
    private const val MAX_EDGE = 2400
    private const val MAX_IMAGE_BYTES = 640 * 1024

    suspend fun import(resolver: ContentResolver, uri: Uri, date: LocalDate): ProgressPhoto =
        withContext(Dispatchers.IO) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (width, height) = info.size.let { it.width to it.height }
                val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
                decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1))
            }
            try {
                val output = ByteArrayOutputStream()
                for (quality in listOf(90, 82, 74, 65, 50, 35, 20, 10)) {
                    output.reset()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                    if (output.size() <= MAX_IMAGE_BYTES) {
                        return@withContext ProgressPhoto(UUID.randomUUID().toString(), date.toString(), output.toByteArray())
                    }
                }
                error("Image exceeds the encoded size limit")
            } finally {
                bitmap.recycle()
            }
        }

    fun validate(photo: ProgressPhoto) {
        require(UUID.fromString(photo.id).toString() == photo.id)
        require(LocalDate.parse(photo.date).toString() == photo.date)
        if (photo.jpeg.isEmpty()) return
        require(photo.jpeg.size <= MAX_IMAGE_BYTES)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size, options)
        require(options.outMimeType == "image/jpeg" && options.outWidth in 1..MAX_EDGE &&
            options.outHeight in 1..MAX_EDGE)
    }

    suspend fun decode(bytes: ByteArray, fullSize: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        if (!fullSize) {
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inJustDecodeBounds = false
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 640) {
                options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            }
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
