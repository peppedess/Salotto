package it.peppedess.salotto.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    val haConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("salotto", Context.MODE_PRIVATE)
    private val artCache = context.getSharedPreferences("tmdb_art", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    val favoritesInitialized: Boolean
        get() = sp.getBoolean(KEY_FAV_INIT, false)

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
        }
        _settings.value = s
    }

    fun setFavorites(keys: List<String>) {
        val clean = keys.distinct()
        sp.edit {
            putString(KEY_FAVORITES, clean.joinToString("\n"))
            putBoolean(KEY_FAV_INIT, true)
        }
        _favorites.value = clean
    }

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
    )

    private fun readFavorites(): List<String> = lines(KEY_FAVORITES)

    private fun lines(key: String): List<String> =
        sp.getString(key, "").orEmpty().split("\n").filter { it.isNotBlank() }

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
        const val KEY_FAVORITES = "favorites"
        const val KEY_FAV_INIT = "favorites_init"
    }
}
