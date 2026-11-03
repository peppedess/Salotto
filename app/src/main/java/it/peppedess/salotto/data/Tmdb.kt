package it.peppedess.salotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TmdbTitle(
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String,
    val rating: Double,
) {
    val key: String get() = type + id
    val kindLabel: String get() = if (type == "tv") "Serie TV" else "Film"
}

data class TmdbProvider(val id: Int, val name: String, val priority: Int)

class TmdbException(val code: Int) : IOException("TMDB $code")

/**
 * Client minimo per The Movie Database. Accetta sia la "chiave API" (v3)
 * sia il "token di accesso in lettura" (v4, inizia con eyJ).
 */
class TmdbClient(credential: String) {
    private val credential = credential.trim()
    private val bearer = this.credential.startsWith("eyJ")

    suspend fun search(query: String): List<TmdbTitle> =
        parseList(get("/search/multi", mapOf("query" to query, "include_adult" to "false")), null)

    suspend fun trending(): List<TmdbTitle> = parseList(get("/trending/all/week"), null)

    suspend fun discover(type: String, providerId: Int): List<TmdbTitle> = parseList(
        get(
            "/discover/$type",
            mapOf(
                "watch_region" to REGION,
                "with_watch_providers" to providerId.toString(),
                "with_watch_monetization_types" to "flatrate|free|ads",
                "sort_by" to "popularity.desc",
            ),
        ),
        type,
    )

    suspend fun providers(): List<TmdbProvider> {
        val out = mutableListOf<TmdbProvider>()
        for (type in listOf("movie", "tv")) {
            val arr = get("/watch/providers/$type", mapOf("watch_region" to REGION)).optJSONArray("results") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val priorities = o.optJSONObject("display_priorities")
                val priority = priorities?.optInt(REGION, o.optInt("display_priority", 999))
                    ?: o.optInt("display_priority", 999)
                out += TmdbProvider(o.optInt("provider_id"), str(o, "provider_name"), priority)
            }
        }
        return out.distinctBy { it.id }.sortedBy { it.priority }
    }

    /** Nomi dei servizi su cui il titolo è disponibile in Italia. */
    suspend fun watchProviders(type: String, id: Int): List<String> {
        val region = get("/$type/$id/watch/providers").optJSONObject("results")?.optJSONObject(REGION)
            ?: return emptyList()
        val out = mutableListOf<Pair<String, Int>>()
        for (kind in listOf("flatrate", "free", "ads")) {
            val arr = region.optJSONArray(kind) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += str(o, "provider_name") to o.optInt("display_priority", 999)
            }
        }
        return out.sortedBy { it.second }.map { it.first }.distinct()
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("language=it-IT")
                params.forEach { (k, v) ->
                    append('&').append(k).append('=').append(URLEncoder.encode(v, "UTF-8"))
                }
                if (!bearer) append("&api_key=").append(URLEncoder.encode(credential, "UTF-8"))
            }
            val conn = (URL("$BASE$path?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (bearer) setRequestProperty("Authorization", "Bearer $credential")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw TmdbException(code)
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }

    private fun parseList(json: JSONObject, defaultType: String?): List<TmdbTitle> {
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> parseTitle(arr.getJSONObject(i), defaultType) }
    }

    private fun parseTitle(o: JSONObject, defaultType: String?): TmdbTitle? {
        val type = str(o, "media_type").ifEmpty { defaultType ?: "movie" }
        if (type != "movie" && type != "tv") return null
        val title = str(o, if (type == "movie") "title" else "name")
            .ifEmpty { str(o, if (type == "movie") "original_title" else "original_name") }
        if (title.isBlank()) return null
        val date = str(o, if (type == "movie") "release_date" else "first_air_date")
        return TmdbTitle(
            id = o.optInt("id"),
            type = type,
            title = title,
            overview = str(o, "overview"),
            posterUrl = image(o, "poster_path", "w342"),
            backdropUrl = image(o, "backdrop_path", "w1280"),
            year = date.take(4),
            rating = o.optDouble("vote_average", 0.0),
        )
    }

    private fun image(o: JSONObject, key: String, size: String): String? {
        val path = str(o, key)
        return if (path.isBlank()) null else "https://image.tmdb.org/t/p/$size$path"
    }

    private fun str(o: JSONObject, key: String): String =
        if (!o.has(key) || o.isNull(key)) "" else o.optString(key)

    private companion object {
        const val BASE = "https://api.themoviedb.org/3"
        const val REGION = "IT"
    }
}
