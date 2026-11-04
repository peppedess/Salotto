package it.peppedess.salotto.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoItem(val uri: Uri, val takenAt: Long)

/** Foto della galleria per il salvaschermo, come la modalità ambiente con Google Foto. */
object PhotosRepository {

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Le foto più recenti, preferendo quelle orizzontali che riempiono bene lo schermo. */
    suspend fun recent(context: Context, limit: Int = 80): List<PhotoItem> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val landscape = mutableListOf<PhotoItem>()
        val others = mutableListOf<PhotoItem>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (c.moveToNext() && landscape.size < limit && landscape.size + others.size < limit * 4) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(id))
                    val time = c.getLong(taken).takeIf { it > 0 } ?: (c.getLong(added) * 1000)
                    val width = c.getInt(w)
                    val height = c.getInt(h)
                    if (width < 800 && height < 800) continue
                    val item = PhotoItem(uri, time)
                    if (width >= height) landscape += item else others += item
                }
            }
        }
        (landscape + others).take(limit)
    }
}
