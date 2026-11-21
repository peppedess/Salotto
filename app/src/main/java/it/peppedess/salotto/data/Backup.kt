package it.peppedess.salotto.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Backup e ripristino delle impostazioni di Salotto: profili, preferite, liste, chiavi,
 * Home Assistant ed esperienza TV. Restano fuori i dati del giorno (tempo dei bambini),
 * lo stato della configurazione iniziale e il telecomando acceso.
 */
object Backup {
    private const val PREFS = "salotto"
    private const val QR_PREFIX = "SALOTTO1:"
    /** Oltre questa lunghezza il QR diventa troppo fitto per essere letto bene. */
    private const val QR_MAX = 2_300

    private val SKIP_KEYS = setOf("setup_done", "remote_enabled")
    private val SKIP_PREFIXES = listOf("kids_", "notified_")

    data class QrBackup(val text: String, val withLists: Boolean)

    /** Tutte le impostazioni come JSON. Con [compact] le liste perdono trame e sfondi, per stare in un QR. */
    fun export(context: Context, compact: Boolean = false, lists: Boolean = true): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefs = JSONObject()
        sp.all.toSortedMap().forEach { (key, value) ->
            if (key in SKIP_KEYS || SKIP_PREFIXES.any { key.startsWith(it) } || value == null) return@forEach
            val isList = key.startsWith("mylist_") || key == "history"
            if (isList && (!lists || (compact && key == "history"))) return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", if (isList && compact) compactTitles(value) else value)
                is Set<*> -> entry.put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> return@forEach
            }
            prefs.put(key, entry)
        }
        return JSONObject()
            .put("app", "salotto")
            .put("v", 1)
            .put("at", System.currentTimeMillis())
            .put("prefs", prefs)
            .toString()
    }

    /** Testo per il QR: compresso; se le liste non ci stanno, si lasciano fuori. */
    fun qr(context: Context): QrBackup? {
        val full = QR_PREFIX + pack(export(context, compact = true, lists = true))
        if (full.length <= QR_MAX) return QrBackup(full, withLists = true)
        val small = QR_PREFIX + pack(export(context, compact = true, lists = false))
        return if (small.length <= QR_MAX) QrBackup(small, withLists = false) else null
    }

    /** Riconosce sia il testo del QR sia il file JSON. Restituisce il JSON o null se non è un backup. */
    fun decode(raw: String): String? {
        val text = raw.trim()
        val json = when {
            text.startsWith(QR_PREFIX) -> runCatching { unpack(text.removePrefix(QR_PREFIX)) }.getOrNull()
            text.startsWith("{") -> text
            else -> null
        } ?: return null
        val ok = runCatching { JSONObject(json).optString("app") == "salotto" }.getOrDefault(false)
        return if (ok) json else null
    }

    /** Quante impostazioni contiene il backup. */
    fun count(json: String): Int = runCatching { JSONObject(json).getJSONObject("prefs").length() }.getOrDefault(0)

    /** Scrive le impostazioni del backup. Salotto va riavviato subito dopo. */
    fun restore(context: Context, json: String): Boolean = runCatching {
        val prefs = JSONObject(json).getJSONObject("prefs")
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val keys = prefs.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in SKIP_KEYS || SKIP_PREFIXES.any { key.startsWith(it) }) continue
            val e = prefs.getJSONObject(key)
            when (e.optString("t")) {
                "b" -> editor.putBoolean(key, e.getBoolean("v"))
                "i" -> editor.putInt(key, e.getInt("v"))
                "l" -> editor.putLong(key, e.getLong("v"))
                "f" -> editor.putFloat(key, e.getDouble("v").toFloat())
                "s" -> editor.putString(key, e.getString("v"))
                "set" -> {
                    val arr = e.getJSONArray("v")
                    editor.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }
        }
        editor.commit()
    }.getOrDefault(false)

    private fun compactTitles(raw: String): String = runCatching {
        val arr = JSONArray(raw)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.put(
                JSONObject()
                    .put("id", o.optInt("id"))
                    .put("type", o.optString("type"))
                    .put("title", o.optString("title"))
                    .put("poster", o.optString("poster"))
                    .put("year", o.optString("year"))
                    .put("rating", o.optDouble("rating", 0.0))
            )
        }
        out.toString()
    }.getOrDefault(raw)

    private fun pack(text: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(text.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4_096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun unpack(text: String): String {
        val data = Base64.decode(text, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4_096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 2_000_000) break
        }
        inflater.end()
        return out.toString("UTF-8")
    }
}
