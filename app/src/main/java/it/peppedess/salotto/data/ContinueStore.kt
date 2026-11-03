package it.peppedess.salotto.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ContinueItem(
    val id: String,
    val packageName: String,
    val title: String,
    val subtitle: String,
    val artPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val playing: Boolean,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Contenuti visti di recente, raccolti dalle sessioni multimediali delle altre app. */
class ContinueStore private constructor(context: Context) {
    private val sp = context.getSharedPreferences("continue", Context.MODE_PRIVATE)
    private val artDir = File(context.filesDir, "art").apply { mkdirs() }

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<ContinueItem>> = _items.asStateFlow()

    @Synchronized
    fun upsert(
        packageName: String,
        title: String,
        subtitle: String,
        art: Bitmap?,
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
    ) {
        if (title.isBlank()) return
        val id = "$packageName|$title"
        val current = _items.value
        val existing = current.firstOrNull { it.id == id }

        var artPath = existing?.artPath?.takeIf { File(it).exists() }
        if (artPath == null && art != null) artPath = saveArt(id, art)

        if (existing != null &&
            existing.playing == playing &&
            existing.subtitle == subtitle &&
            existing.artPath == artPath &&
            kotlin.math.abs(existing.positionMs - positionMs) < 15_000 &&
            current.firstOrNull()?.id == id
        ) return

        val item = ContinueItem(
            id = id,
            packageName = packageName,
            title = title,
            subtitle = subtitle,
            artPath = artPath,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis(),
            playing = playing,
        )
        val others = current
            .filter { it.id != id }
            .map { if (playing && it.packageName == packageName && it.playing) it.copy(playing = false) else it }
        commit((listOf(item) + others).take(MAX_ITEMS))
    }

    @Synchronized
    fun markStopped(packageName: String) {
        val current = _items.value
        if (current.none { it.packageName == packageName && it.playing }) return
        commit(current.map { if (it.packageName == packageName) it.copy(playing = false) else it })
    }

    @Synchronized
    fun remove(id: String) {
        commit(_items.value.filter { it.id != id })
    }

    @Synchronized
    fun clear() {
        commit(emptyList())
    }

    private fun commit(list: List<ContinueItem>) {
        _items.value = list
        write(list)
        val keep = list.mapNotNull { it.artPath }.toSet()
        artDir.listFiles()?.forEach { f -> if (f.absolutePath !in keep) f.delete() }
    }

    private fun saveArt(id: String, bitmap: Bitmap): String? = runCatching {
        val scaled = if (bitmap.width > ART_MAX) {
            val h = (bitmap.height * ART_MAX / bitmap.width.toFloat()).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, ART_MAX, h, true)
        } else bitmap
        val file = File(artDir, Integer.toHexString(id.hashCode()) + ".jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()

    private fun read(): List<ContinueItem> = runCatching {
        val arr = JSONArray(sp.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ContinueItem(
                id = o.getString("id"),
                packageName = o.getString("pkg"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle"),
                artPath = o.optString("art").ifEmpty { null },
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                updatedAt = o.optLong("at"),
                playing = false,
            )
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<ContinueItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("pkg", it.packageName)
                    .put("title", it.title)
                    .put("subtitle", it.subtitle)
                    .put("art", it.artPath ?: "")
                    .put("pos", it.positionMs)
                    .put("dur", it.durationMs)
                    .put("at", it.updatedAt)
            )
        }
        sp.edit { putString(KEY, arr.toString()) }
    }

    companion object {
        private const val KEY = "items"
        private const val MAX_ITEMS = 15
        private const val ART_MAX = 720

        @Volatile
        private var instance: ContinueStore? = null

        fun get(context: Context): ContinueStore =
            instance ?: synchronized(this) {
                instance ?: ContinueStore(context.applicationContext).also { instance = it }
            }
    }
}
