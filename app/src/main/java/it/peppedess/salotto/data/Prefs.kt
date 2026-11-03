package it.peppedess.salotto.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Settings(
    val keepScreenOn: Boolean = true,
    val allowPortrait: Boolean = false,
    val kiosk: Boolean = false,
    val pin: String = "",
    val tmdbKey: String = "",
    val screensaverMinutes: Int = 5,
    val weatherCity: String = "Milano",
    val haUrl: String = "",
    val haToken: String = "",
    val haEntities: List<String> = emptyList(),
    val entertainmentOnly: Boolean = true,
    val navSounds: Boolean = true,
    val liveTv: Boolean = true,
    val autoUpdate: Boolean = true,
    val episodeAlerts: Boolean = true,
) {
    val haConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()
}

/** Profilo d'uso: ognuno ha le sue preferite e la sua lista; i profili bambini hanno regole. */
data class Profile(
    val id: String,
    val name: String,
    val kids: Boolean = false,
    val allowedApps: List<String> = emptyList(),
    val dailyLimitMin: Int = 0,
) {
    val initial: String get() = name.trim().take(1).uppercase().ifEmpty { "?" }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("kids", kids)
        .put("apps", JSONArray(allowedApps))
        .put("limit", dailyLimitMin)

    companion object {
        const val MAIN = "main"

        fun fromJson(o: JSONObject): Profile {
            val apps = o.optJSONArray("apps")
            return Profile(
                id = o.getString("id"),
                name = o.optString("name"),
                kids = o.optBoolean("kids"),
                allowedApps = if (apps == null) emptyList() else (0 until apps.length()).map { apps.getString(it) },
                dailyLimitMin = o.optInt("limit"),
            )
        }

        fun newId(): String = UUID.randomUUID().toString().take(8)
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("salotto", Context.MODE_PRIVATE)
    private val artCache = context.getSharedPreferences("tmdb_art", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _profiles = MutableStateFlow(readProfiles())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _profile = MutableStateFlow(resolveProfile(sp.getString(KEY_PROFILE, Profile.MAIN).orEmpty()))
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _favorites = MutableStateFlow(readFavorites(_profile.value.id))
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _myList = MutableStateFlow(readTitles(myListKey(_profile.value.id)))
    val myList: StateFlow<List<TmdbTitle>> = _myList.asStateFlow()

    private val _history = MutableStateFlow(readTitles(KEY_HISTORY))
    val history: StateFlow<List<TmdbTitle>> = _history.asStateFlow()

    val favoritesInitialized: Boolean
        get() = sp.getBoolean(favInitKey(_profile.value.id), false)

    // ---- Impostazioni ----

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit {
            putBoolean(KEY_SCREEN_ON, s.keepScreenOn)
            putBoolean(KEY_PORTRAIT, s.allowPortrait)
            putBoolean(KEY_KIOSK, s.kiosk)
            putString(KEY_PIN, s.pin)
            putString(KEY_TMDB, s.tmdbKey)
            putInt(KEY_SCREENSAVER, s.screensaverMinutes)
            putString(KEY_CITY, s.weatherCity)
            putString(KEY_HA_URL, s.haUrl)
            putString(KEY_HA_TOKEN, s.haToken)
            putString(KEY_HA_ENTITIES, s.haEntities.joinToString("\n"))
            putBoolean(KEY_ENTERTAINMENT, s.entertainmentOnly)
            putBoolean(KEY_NAV_SOUNDS, s.navSounds)
            putBoolean(KEY_LIVE_TV, s.liveTv)
            putBoolean(KEY_AUTO_UPDATE, s.autoUpdate)
            putBoolean(KEY_EPISODE_ALERTS, s.episodeAlerts)
        }
        _settings.value = s
    }

    // ---- Profili ----

    fun setProfile(id: String) {
        val p = resolveProfile(id)
        sp.edit { putString(KEY_PROFILE, p.id) }
        _profile.value = p
        _favorites.value = readFavorites(p.id)
        _myList.value = readTitles(myListKey(p.id))
    }

    fun saveProfile(profile: Profile) {
        val list = _profiles.value
        val updated = if (list.any { it.id == profile.id }) {
            list.map { if (it.id == profile.id) profile else it }
        } else {
            list + profile
        }
        writeProfiles(updated)
        if (_profile.value.id == profile.id) _profile.value = profile
    }

    fun deleteProfile(id: String) {
        if (id == Profile.MAIN) return
        writeProfiles(_profiles.value.filter { it.id != id })
        sp.edit {
            remove(favKey(id))
            remove(favInitKey(id))
            remove(myListKey(id))
        }
        if (_profile.value.id == id) setProfile(Profile.MAIN)
    }

    // ---- Preferite ----

    fun setFavorites(keys: List<String>) {
        val id = _profile.value.id
        val clean = keys.distinct()
        sp.edit {
            putString(favKey(id), clean.joinToString("\n"))
            putBoolean(favInitKey(id), true)
        }
        _favorites.value = clean
    }

    // ---- La mia lista e cronologia ----

    fun isInMyList(title: TmdbTitle): Boolean = _myList.value.any { it.key == title.key }

    /** Restituisce true se il titolo è stato aggiunto, false se rimosso. */
    fun toggleMyList(title: TmdbTitle): Boolean {
        val current = _myList.value
        val added = current.none { it.key == title.key }
        val updated = if (added) listOf(title) + current else current.filter { it.key != title.key }
        writeTitles(myListKey(_profile.value.id), updated)
        _myList.value = updated
        return added
    }

    /** Tutte le serie nelle liste di tutti i profili, per gli avvisi dei nuovi episodi. */
    fun allSeriesInLists(): List<TmdbTitle> =
        _profiles.value.flatMap { readTitles(myListKey(it.id)) }.filter { it.type == "tv" }.distinctBy { it.key }

    fun addHistory(title: TmdbTitle) {
        val updated = (listOf(title) + _history.value.filter { it.key != title.key }).take(MAX_HISTORY)
        writeTitles(KEY_HISTORY, updated)
        _history.value = updated
    }

    fun notifiedEpisode(seriesId: Int): String = sp.getString("notified_$seriesId", "").orEmpty()

    fun setNotifiedEpisode(seriesId: Int, code: String) {
        sp.edit { putString("notified_$seriesId", code) }
    }

    // ---- Tempo dei bambini ----

    fun kidsUsedSeconds(profileId: String): Int = sp.getInt(kidsKey(profileId), 0)

    fun addKidsUsage(profileId: String, seconds: Int) {
        val key = kidsKey(profileId)
        sp.edit { putInt(key, sp.getInt(key, 0) + seconds) }
    }

    fun grantKidsExtra(profileId: String, minutes: Int) {
        val key = kidsKey(profileId)
        sp.edit { putInt(key, (sp.getInt(key, 0) - minutes * 60).coerceAtLeast(-minutes * 60)) }
    }

    // ---- Cache locandine ----

    /** null = mai cercato, "" = cercato senza risultati, altrimenti l'URL dell'immagine. */
    fun cachedArt(query: String): String? {
        val k = query.lowercase()
        return if (artCache.contains(k)) artCache.getString(k, "").orEmpty() else null
    }

    fun cacheArt(query: String, url: String) {
        artCache.edit { putString(query.lowercase(), url) }
    }

    fun clearArtCache() {
        artCache.edit { clear() }
    }

    // ---- Lettura e scrittura ----

    private fun readSettings(): Settings = Settings(
        keepScreenOn = sp.getBoolean(KEY_SCREEN_ON, true),
        allowPortrait = sp.getBoolean(KEY_PORTRAIT, false),
        kiosk = sp.getBoolean(KEY_KIOSK, false),
        pin = sp.getString(KEY_PIN, "").orEmpty(),
        tmdbKey = sp.getString(KEY_TMDB, "").orEmpty(),
        screensaverMinutes = sp.getInt(KEY_SCREENSAVER, 5),
        weatherCity = sp.getString(KEY_CITY, "Milano").orEmpty(),
        haUrl = sp.getString(KEY_HA_URL, "").orEmpty(),
        haToken = sp.getString(KEY_HA_TOKEN, "").orEmpty(),
        haEntities = lines(KEY_HA_ENTITIES),
        entertainmentOnly = sp.getBoolean(KEY_ENTERTAINMENT, true),
        navSounds = sp.getBoolean(KEY_NAV_SOUNDS, true),
        liveTv = sp.getBoolean(KEY_LIVE_TV, true),
        autoUpdate = sp.getBoolean(KEY_AUTO_UPDATE, true),
        episodeAlerts = sp.getBoolean(KEY_EPISODE_ALERTS, true),
    )

    private fun readProfiles(): List<Profile> {
        val list = runCatching {
            val arr = JSONArray(sp.getString(KEY_PROFILES, "[]"))
            (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        return if (list.none { it.id == Profile.MAIN }) listOf(Profile(Profile.MAIN, "Principale")) + list else list
    }

    private fun writeProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit { putString(KEY_PROFILES, arr.toString()) }
        _profiles.value = list
    }

    private fun resolveProfile(id: String): Profile =
        _profilesOrRead().firstOrNull { it.id == id } ?: _profilesOrRead().first()

    @Suppress("FunctionName")
    private fun _profilesOrRead(): List<Profile> =
        runCatching { _profiles.value }.getOrNull() ?: readProfiles()

    private fun readFavorites(profileId: String): List<String> = lines(favKey(profileId))

    private fun readTitles(key: String): List<TmdbTitle> = runCatching {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).map { TmdbTitle.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun writeTitles(key: String, titles: List<TmdbTitle>) {
        val arr = JSONArray()
        titles.forEach { arr.put(it.toJson()) }
        sp.edit { putString(key, arr.toString()) }
    }

    private fun lines(key: String): List<String> =
        sp.getString(key, "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun favKey(id: String) = if (id == Profile.MAIN) KEY_FAVORITES else "${KEY_FAVORITES}_$id"
    private fun favInitKey(id: String) = if (id == Profile.MAIN) KEY_FAV_INIT else "${KEY_FAV_INIT}_$id"
    private fun myListKey(id: String) = "mylist_$id"
    private fun kidsKey(id: String) = "kids_${id}_${LocalDate.now()}"

    private companion object {
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_PORTRAIT = "allow_portrait"
        const val KEY_KIOSK = "kiosk"
        const val KEY_PIN = "pin"
        const val KEY_TMDB = "tmdb_key"
        const val KEY_SCREENSAVER = "screensaver_minutes"
        const val KEY_CITY = "weather_city"
        const val KEY_HA_URL = "ha_url"
        const val KEY_HA_TOKEN = "ha_token"
        const val KEY_HA_ENTITIES = "ha_entities"
        const val KEY_ENTERTAINMENT = "entertainment_only"
        const val KEY_NAV_SOUNDS = "nav_sounds"
        const val KEY_LIVE_TV = "live_tv"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_EPISODE_ALERTS = "episode_alerts"
        const val KEY_PROFILES = "profiles"
        const val KEY_PROFILE = "profile_current"
        const val KEY_HISTORY = "history"
        const val KEY_FAVORITES = "favorites"
        const val KEY_FAV_INIT = "favorites_init"
        const val MAX_HISTORY = 8
    }
}
