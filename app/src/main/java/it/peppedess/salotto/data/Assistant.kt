package it.peppedess.salotto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ChatMessage(val fromUser: Boolean, val text: String)

data class TitleHint(val title: String, val year: String, val type: String?)

data class HaAction(val entityId: String, val service: String)

/** Risposta dell'assistente, già separata dalle parti che Salotto esegue. */
data class AssistantReply(
    val text: String,
    val titles: List<TitleHint>,
    val actions: List<HaAction>,
    val openPackage: String?,
)

class AssistantException(val code: Int, message: String) : IOException(message)

/** Assistente che usa l'API di Claude con la chiave dell'utente. */
class AssistantClient(private val apiKey: String) {

    suspend fun ask(context: String, history: List<ChatMessage>): AssistantReply = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        history.takeLast(12).forEach { m ->
            messages.put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 900)
            .put("system", SYSTEM + "\n\n" + context)
            .put("messages", messages)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        val json = Net.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw AssistantException(response.code, msg ?: "Errore ${response.code}")
            }
            JSONObject(text)
        }
        val content = json.optJSONArray("content") ?: JSONArray()
        val raw = buildString {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
        parse(raw)
    }

    private fun parse(raw: String): AssistantReply {
        val titles = mutableListOf<TitleHint>()
        val actions = mutableListOf<HaAction>()
        var open: String? = null
        val text = StringBuilder()
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("TITOLI:") -> runCatching {
                    val arr = JSONArray(trimmed.removePrefix("TITOLI:").trim())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        titles += TitleHint(o.optString("title"), o.optString("year"), o.optString("type").ifBlank { null })
                    }
                }
                trimmed.startsWith("AZIONI:") -> runCatching {
                    val arr = JSONArray(trimmed.removePrefix("AZIONI:").trim())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        actions += HaAction(o.optString("entity_id"), o.optString("service", "toggle"))
                    }
                }
                trimmed.startsWith("APRI:") -> open = trimmed.removePrefix("APRI:").trim().ifBlank { null }
                else -> text.appendLine(line)
            }
        }
        return AssistantReply(text.toString().trim(), titles.filter { it.title.isNotBlank() }, actions, open)
    }

    companion object {
        const val MODEL = "claude-haiku-5-5"

        private val SYSTEM = """
            Sei l'assistente di Salotto, un launcher in stile Google TV su un telefono usato come TV.
            Rispondi sempre in italiano, in modo breve e cordiale (massimo 4 frasi): verrà letto su uno schermo.
            Puoi consigliare film e serie, rispondere su cosa guardare e controllare la casa.
            Regole per le parti che Salotto esegue, ognuna su una riga a sé, alla fine della risposta:
            - Se consigli titoli, aggiungi: TITOLI: [{"title":"Titolo","year":"2019","type":"movie"}] (type: movie o tv, al massimo 8).
            - Se l'utente chiede di accendere, spegnere o avviare qualcosa di casa, usa solo le entità elencate nel contesto:
              AZIONI: [{"entity_id":"light.salotto","service":"turn_off"}] (service: turn_on, turn_off, toggle).
            - Se l'utente chiede di aprire un'app tra quelle installate: APRI: nome.del.pacchetto
            Non inventare entità né pacchetti. Preferisci titoli disponibili sui servizi installati.
        """.trimIndent()
    }
}
