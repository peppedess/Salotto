package it.peppedess.salotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class TmdbTitle(
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String,
    val rating: Double,
    val genres: List<String> = emptyList(),
) {
    val key: String get() = type + id
    val kindLabel: String get() = if (type == "tv") "Serie TV" else "Film"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type)
        .put("title", title)
        .put("overview", overview)
        .put("poster", posterUrl ?: "")
        .put("backdrop", backdropUrl ?: "")
        .put("year", year)
        .put("rating", rating)
        .put("genres", JSONArray(genres))

    companion object {
        fun fromJson(o: JSONObject) = TmdbTitle(
            id = o.optInt("id"),
            type = o.optString("type", "movie"),
            title = o.optString("title"),
            overview = o.optString("overview"),
            posterUrl = o.optString("poster").ifEmpty { null },
            backdropUrl = o.optString("backdrop").ifEmpty { null },
            year = o.optString("year"),
            rating = o.optDouble("rating", 0.0),
            genres = o.optJSONArray("genres")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty(),
        )
    }
}

data class CastMember(val name: String, val role: String, val photoUrl: String?)

/** Dettagli completi di un titolo per la scheda. */
data class TmdbDetails(
    val tagline: String,
    val genres: List<String>,
    val runtimeMin: Int,
    val seasons: Int,
    val episodes: Int,
    val certification: String,
    val creators: List<String>,
    val cast: List<CastMember>,
    val status: String,
) {
    val lengthLabel: String
        get() = when {
            seasons > 0 -> buildString {
                append(if (seasons == 1) "1 stagione" else "$seasons stagioni")
                if (episodes > 0) append(" · $episodes episodi")
            }
            runtimeMin > 0 -> if (runtimeMin >= 60) "${runtimeMin / 60} h ${runtimeMin % 60} min" else "$runtimeMin min"
            else -> ""
        }
}

/** Ultimo (o prossimo) episodio di una serie. */
data class EpisodeInfo(
    val series: TmdbTitle,
    val season: Int,
    val episode: Int,
    val name: String,
    val airDate: String,
) {
    val code: String get() = "S${season}E$episode"
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

    suspend fun details(title: TmdbTitle): TmdbDetails {
        val tv = title.type == "tv"
        val extra = if (tv) "aggregate_credits,content_ratings" else "credits,release_dates"
        val o = get("/${title.type}/${title.id}", mapOf("append_to_response" to extra))

        val genres = o.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).map { str(arr.getJSONObject(it), "name") }.filter { it.isNotBlank() }
        }.orEmpty()

        val certification = if (tv) {
            o.optJSONObject("content_ratings")?.optJSONArray("results")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { str(it, "iso_3166_1") == REGION }?.let { str(it, "rating") }
            }.orEmpty()
        } else {
            o.optJSONObject("release_dates")?.optJSONArray("results")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { str(it, "iso_3166_1") == REGION }
                    ?.optJSONArray("release_dates")?.let { dates ->
                        (0 until dates.length()).map { str(dates.getJSONObject(it), "certification") }.firstOrNull { it.isNotBlank() }
                    }
            }.orEmpty()
        }

        val credits = o.optJSONObject(if (tv) "aggregate_credits" else "credits")
        val cast = credits?.optJSONArray("cast")?.let { arr ->
            (0 until minOf(arr.length(), 15)).map { i ->
                val c = arr.getJSONObject(i)
                val role = if (tv) {
                    c.optJSONArray("roles")?.optJSONObject(0)?.let { str(it, "character") }.orEmpty()
                } else {
                    str(c, "character")
                }
                CastMember(str(c, "name"), role, image(c, "profile_path", "w185"))
            }
        }.orEmpty()

        val creators = if (tv) {
            o.optJSONArray("created_by")?.let { arr -> (0 until arr.length()).map { str(arr.getJSONObject(it), "name") } }.orEmpty()
        } else {
            credits?.optJSONArray("crew")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { str(it, "job") == "Director" }
                    .map { str(it, "name") }
            }.orEmpty()
        }

        return TmdbDetails(
            tagline = str(o, "tagline"),
            genres = genres,
            runtimeMin = if (tv) {
                o.optJSONArray("episode_run_time")?.optInt(0) ?: 0
            } else {
                o.optInt("runtime")
            },
            seasons = if (tv) o.optInt("number_of_seasons") else 0,
            episodes = if (tv) o.optInt("number_of_episodes") else 0,
            certification = certification,
            creators = creators.filter { it.isNotBlank() }.distinct().take(3),
            cast = cast.filter { it.name.isNotBlank() },
            status = when (str(o, "status")) {
                "Returning Series" -> "In corso"
                "Ended" -> "Conclusa"
                "Canceled" -> "Cancellata"
                "In Production" -> "In produzione"
                else -> ""
            },
        )
    }

    suspend fun trending(type: String): List<TmdbTitle> = parseList(get("/trending/$type/week"), type)

    /** Titoli di un genere, se possibile solo tra quelli disponibili sui servizi indicati. */
    suspend fun discoverGenre(type: String, genreId: Int, providerIds: List<Int>): List<TmdbTitle> =
        parseList(get("/discover/$type", discoverParams(providerIds) + ("with_genres" to genreId.toString())), type)

    /** Uscite degli ultimi due mesi sui servizi indicati. */
    suspend fun discoverRecent(type: String, providerIds: List<Int>): List<TmdbTitle> {
        val from = java.time.LocalDate.now().minusDays(60).toString()
        val dateKey = if (type == "tv") "first_air_date.gte" else "primary_release_date.gte"
        return parseList(get("/discover/$type", discoverParams(providerIds) + (dateKey to from)), type)
    }

    private fun discoverParams(providerIds: List<Int>): Map<String, String> {
        val params = mutableMapOf("sort_by" to "popularity.desc", "vote_count.gte" to "20")
        if (providerIds.isNotEmpty()) {
            params["watch_region"] = REGION
            params["with_watch_providers"] = providerIds.joinToString("|")
            params["with_watch_monetization_types"] = "flatrate|free|ads"
        }
        return params
    }

    suspend fun recommendations(title: TmdbTitle): List<TmdbTitle> =
        parseList(get("/${title.type}/${title.id}/recommendations"), title.type)

    /** Ultimo episodio uscito di una serie, se c'è. */
    suspend fun lastEpisode(series: TmdbTitle): EpisodeInfo? {
        val o = get("/tv/${series.id}").optJSONObject("last_episode_to_air") ?: return null
        val date = str(o, "air_date")
        if (date.isBlank()) return null
        return EpisodeInfo(
            series = series,
            season = o.optInt("season_number"),
            episode = o.optInt("episode_number"),
            name = str(o, "name"),
            airDate = date,
        )
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

    /** Prova l'host principale e poi quello alternativo ufficiale (api.tmdb.org). */
    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            var lastError: IOException? = null
            for (host in HOSTS) {
                val url = "https://$host/3$path".toHttpUrl().newBuilder().apply {
                    addQueryParameter("language", "it-IT")
                    params.forEach { (k, v) -> addQueryParameter(k, v) }
                    if (!bearer) addQueryParameter("api_key", credential)
                }.build()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .apply { if (bearer) header("Authorization", "Bearer $credential") }
                    .build()
                try {
                    Net.client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw TmdbException(response.code)
                        return@withContext JSONObject(response.body?.string().orEmpty())
                    }
                } catch (e: TmdbException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("Nessun host TMDB raggiungibile")
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
            genres = o.optJSONArray("genre_ids")?.let { arr ->
                (0 until arr.length()).mapNotNull { GENRES[arr.optInt(it)] }
            }.orEmpty(),
        )
    }

    private fun image(o: JSONObject, key: String, size: String): String? {
        val path = str(o, key)
        return if (path.isBlank()) null else "https://image.tmdb.org/t/p/$size$path"
    }

    private fun str(o: JSONObject, key: String): String =
        if (!o.has(key) || o.isNull(key)) "" else o.optString(key)

    private companion object {
        val HOSTS = listOf("api.themoviedb.org", "api.tmdb.org")

        /** Generi TMDB (film e serie) in italiano. */
        val GENRES = mapOf(
            28 to "Azione", 12 to "Avventura", 16 to "Animazione", 35 to "Commedia", 80 to "Crime",
            99 to "Documentario", 18 to "Dramma", 10751 to "Famiglia", 14 to "Fantasy", 36 to "Storia",
            27 to "Horror", 10402 to "Musica", 9648 to "Mistero", 10749 to "Romance", 878 to "Fantascienza",
            10770 to "Film TV", 53 to "Thriller", 10752 to "Guerra", 37 to "Western",
            10759 to "Azione e avventura", 10762 to "Bambini", 10763 to "Notizie", 10764 to "Reality",
            10765 to "Sci-fi e fantasy", 10766 to "Soap", 10767 to "Talk show", 10768 to "Guerra e politica",
        )
        const val REGION = "IT"
    }
}
