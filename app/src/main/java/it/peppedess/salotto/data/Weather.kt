package it.peppedess.salotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

data class Weather(
    val city: String,
    val temperature: Int,
    val code: Int,
    val max: Int,
    val min: Int,
    val isDay: Boolean,
) {
    val description: String
        get() = when (code) {
            0 -> if (isDay) "Sereno" else "Cielo limpido"
            1 -> "Poco nuvoloso"
            2 -> "Parzialmente nuvoloso"
            3 -> "Nuvoloso"
            45, 48 -> "Nebbia"
            51, 53, 55, 56, 57 -> "Pioviggine"
            61, 63, 65, 66, 67 -> "Pioggia"
            71, 73, 75, 77 -> "Neve"
            80, 81, 82 -> "Rovesci"
            85, 86 -> "Rovesci di neve"
            95, 96, 99 -> "Temporale"
            else -> "—"
        }

    val kind: Kind
        get() = when (code) {
            0 -> if (isDay) Kind.Clear else Kind.Night
            1, 2 -> Kind.PartlyCloudy
            3, 45, 48 -> Kind.Cloudy
            71, 73, 75, 77, 85, 86 -> Kind.Snow
            95, 96, 99 -> Kind.Storm
            else -> Kind.Rain
        }

    enum class Kind { Clear, Night, PartlyCloudy, Cloudy, Rain, Snow, Storm }
}

/** Meteo da Open-Meteo: gratuito e senza chiave. */
object WeatherClient {

    private var cachedCity: String? = null
    private var cachedCoords: Pair<Double, Double>? = null

    suspend fun load(city: String): Weather = withContext(Dispatchers.IO) {
        val (lat, lon) = coordinates(city)
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min")
            .addQueryParameter("forecast_days", "1")
            .addQueryParameter("timezone", "auto")
            .build()
        val json = get(url.toString())
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        Weather(
            city = city.trim(),
            temperature = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            isDay = current.optInt("is_day", 1) == 1,
        )
    }

    private fun coordinates(city: String): Pair<Double, Double> {
        val key = city.trim().lowercase()
        if (key == cachedCity) cachedCoords?.let { return it }
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city.trim())
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "it")
            .build()
        val result = get(url.toString()).optJSONArray("results")?.optJSONObject(0)
            ?: throw IOException("Città non trovata")
        val coords = result.getDouble("latitude") to result.getDouble("longitude")
        cachedCity = key
        cachedCoords = coords
        return coords
    }

    private fun get(url: String): JSONObject =
        Net.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Meteo ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
}
