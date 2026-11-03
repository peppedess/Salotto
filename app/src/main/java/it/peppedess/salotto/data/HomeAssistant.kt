package it.peppedess.salotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class HaEntity(
    val id: String,
    val name: String,
    val state: String,
) {
    val domain: String get() = id.substringBefore('.')

    val isOn: Boolean get() = state in ON_STATES

    /** Scene, script e pulsanti si avviano soltanto: non hanno uno stato acceso/spento. */
    val isAction: Boolean get() = domain in ACTION_DOMAINS

    val unavailable: Boolean get() = state == "unavailable" || state == "unknown"

    val stateLabel: String
        get() = when {
            unavailable -> "Non disponibile"
            isAction -> when (domain) {
                "scene" -> "Scena"
                "script" -> "Script"
                else -> "Pulsante"
            }
            domain == "cover" -> when (state) {
                "open" -> "Aperta"
                "closed" -> "Chiusa"
                "opening" -> "In apertura"
                "closing" -> "In chiusura"
                else -> state
            }
            domain == "media_player" -> when (state) {
                "playing" -> "In riproduzione"
                "paused" -> "In pausa"
                "off" -> "Spento"
                "idle", "standby" -> "In attesa"
                else -> state
            }
            isOn -> "Acceso"
            state == "off" -> "Spento"
            else -> state
        }

    /** Servizio da chiamare quando si tocca il riquadro. */
    val service: Pair<String, String>
        get() = when (domain) {
            "scene", "script" -> domain to "turn_on"
            "button" -> domain to "press"
            "media_player" -> domain to "media_play_pause"
            else -> domain to "toggle"
        }

    companion object {
        val SUPPORTED_DOMAINS = setOf(
            "light", "switch", "fan", "input_boolean", "cover",
            "scene", "script", "button", "media_player",
        )
        private val ACTION_DOMAINS = setOf("scene", "script", "button")
        private val ON_STATES = setOf("on", "open", "opening", "playing")
    }
}

class HaException(val code: Int) : IOException("Home Assistant $code")

/** Client REST minimo per Home Assistant (token di accesso a lunga durata). */
class HaClient(baseUrl: String, private val token: String) {

    private val base: HttpUrl = normalize(baseUrl)

    suspend fun states(): List<HaEntity> = withContext(Dispatchers.IO) {
        val arr = JSONArray(execute(request("api/states").get().build()))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("entity_id")
            if (id.substringBefore('.') !in HaEntity.SUPPORTED_DOMAINS) return@mapNotNull null
            val attrs = o.optJSONObject("attributes")
            val name = attrs?.optString("friendly_name").orEmpty().ifBlank { id.substringAfter('.') }
            HaEntity(id = id, name = name, state = o.optString("state"))
        }.sortedWith(compareBy({ it.domain }, { it.name.lowercase() }))
    }

    suspend fun call(entity: HaEntity) = withContext(Dispatchers.IO) {
        val (domain, service) = entity.service
        val body = JSONObject().put("entity_id", entity.id).toString()
            .toRequestBody("application/json".toMediaType())
        execute(request("api/services/$domain/$service").post(body).build())
        Unit
    }

    private fun request(path: String): Request.Builder =
        Request.Builder()
            .url(base.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Content-Type", "application/json")

    private fun execute(request: Request): String =
        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HaException(response.code)
            response.body?.string().orEmpty()
        }

    private companion object {
        fun normalize(raw: String): HttpUrl {
            var url = raw.trim().trimEnd('/')
            if (!url.contains("://")) url = "http://$url"
            return "$url/".toHttpUrl()
        }
    }
}
