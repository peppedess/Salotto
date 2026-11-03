package it.peppedess.salotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class Playlist(
    val id: Long,
    val title: String,
    val tracks: Int,
    val coverUrl: String?,
) {
    val webUrl: String get() = "https://www.deezer.com/playlist/$id"
    /** URI per Music Assistant: provider://tipo/id */
    val massUri: String get() = "deezer://playlist/$id"
}

/** Playlist pubbliche di un profilo Deezer (API pubblica, senza login). */
object DeezerClient {

    suspend fun playlists(userId: String): List<Playlist> = withContext(Dispatchers.IO) {
        val id = userId.trim().filter { it.isDigit() }
        if (id.isEmpty()) throw IOException("ID Deezer non valido")
        val request = Request.Builder().url("https://api.deezer.com/user/$id/playlists?limit=50").build()
        val json = Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Deezer ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
        json.optJSONObject("error")?.let { throw IOException(it.optString("message", "Errore Deezer")) }
        val arr = json.optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(
                id = o.optLong("id"),
                title = o.optString("title"),
                tracks = o.optInt("nb_tracks"),
                coverUrl = o.optString("picture_xl").ifBlank { o.optString("picture_big") }.ifBlank { null },
            )
        }
    }
}
