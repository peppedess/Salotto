package it.peppedess.salotto.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/** Canale del digitale terrestre con il link alla diretta nella sua app. */
data class TvChannel(
    val number: Int,
    val name: String,
    val aliases: List<String>,
    val liveUrl: String,
    val color: Long,
)

data class Programme(val start: Long, val stop: Long, val title: String, val description: String)

data class ChannelGuide(val channel: TvChannel, val programmes: List<Programme>) {
    fun current(now: Long = System.currentTimeMillis()): Programme? =
        programmes.firstOrNull { now in it.start until it.stop }

    fun upcoming(now: Long = System.currentTimeMillis(), count: Int = 4): List<Programme> =
        programmes.filter { it.start > now }.take(count)
}

object TvChannels {
    private const val RAI = 0xFF0B4FA8
    private const val MEDIASET = 0xFF1F2A44
    private const val OTHER = 0xFF3A2D4A

    val all = listOf(
        TvChannel(1, "Rai 1", listOf("rai1"), "https://www.raiplay.it/dirette/rai1", RAI),
        TvChannel(2, "Rai 2", listOf("rai2"), "https://www.raiplay.it/dirette/rai2", RAI),
        TvChannel(3, "Rai 3", listOf("rai3"), "https://www.raiplay.it/dirette/rai3", RAI),
        TvChannel(4, "Rete 4", listOf("rete4"), "https://mediasetinfinity.mediaset.it/diretta/rete4_r4", MEDIASET),
        TvChannel(5, "Canale 5", listOf("canale5"), "https://mediasetinfinity.mediaset.it/diretta/canale5_c5", MEDIASET),
        TvChannel(6, "Italia 1", listOf("italia1"), "https://mediasetinfinity.mediaset.it/diretta/italia1_i1", MEDIASET),
        TvChannel(7, "La7", listOf("la7"), "https://www.la7.it/dirette-tv", OTHER),
        TvChannel(8, "TV8", listOf("tv8"), "https://www.tv8.it/streaming", OTHER),
        TvChannel(9, "Nove", listOf("nove", "canalenove"), "https://www.nove.tv/live", OTHER),
        TvChannel(20, "20 Mediaset", listOf("20mediaset", "20"), "https://mediasetinfinity.mediaset.it/diretta/20_lb", MEDIASET),
        TvChannel(21, "Rai 4", listOf("rai4"), "https://www.raiplay.it/dirette/rai4", RAI),
        TvChannel(22, "Iris", listOf("iris"), "https://mediasetinfinity.mediaset.it/diretta/iris_ki", MEDIASET),
        TvChannel(23, "Rai 5", listOf("rai5"), "https://www.raiplay.it/dirette/rai5", RAI),
        TvChannel(24, "Rai Movie", listOf("raimovie"), "https://www.raiplay.it/dirette/raimovie", RAI),
        TvChannel(25, "Rai Premium", listOf("raipremium"), "https://www.raiplay.it/dirette/raipremium", RAI),
        TvChannel(27, "Twentyseven", listOf("twentyseven", "27twentyseven"), "https://mediasetinfinity.mediaset.it/diretta/twentyseven_ts", MEDIASET),
        TvChannel(34, "Cine34", listOf("cine34"), "https://mediasetinfinity.mediaset.it/diretta/cine34_b6", MEDIASET),
        TvChannel(35, "Focus", listOf("focus"), "https://mediasetinfinity.mediaset.it/diretta/focus_fu", MEDIASET),
        TvChannel(39, "Top Crime", listOf("topcrime"), "https://mediasetinfinity.mediaset.it/diretta/topcrime_lt", MEDIASET),
        TvChannel(48, "Rai News 24", listOf("rainews24", "rainews"), "https://www.raiplay.it/dirette/rainews24", RAI),
        TvChannel(57, "Rai Sport", listOf("raisport", "raisporthd"), "https://www.raiplay.it/dirette/raisport", RAI),
    )
}

/**
 * Guida TV dai dati XMLTV gratuiti di epgshare01 (canali italiani, aggiornati ogni giorno).
 * Si scarica in streaming e si tengono solo i canali sopra, dalle 3 ore passate alle 30 future.
 */
class EpgRepository(context: Context) {
    private val cache = File(context.filesDir, "epg.json")

    suspend fun load(force: Boolean = false): List<ChannelGuide> = withContext(Dispatchers.IO) {
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < MAX_AGE_MS
        if (!force && fresh) {
            readCache()?.let { return@withContext it }
        }
        try {
            val guides = download()
            writeCache(guides)
            guides
        } catch (e: IOException) {
            readCache() ?: throw e
        }
    }

    private fun download(): List<ChannelGuide> {
        val request = Request.Builder().url(EPG_URL).build()
        return Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Guida TV ${response.code}")
            GZIPInputStream(response.body!!.byteStream()).use { stream -> parse(stream) }
        }
    }

    private fun parse(stream: java.io.InputStream): List<ChannelGuide> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        val now = System.currentTimeMillis()
        val from = now - 3 * HOUR
        val to = now + 30 * HOUR
        val idToChannel = HashMap<String, TvChannel>()
        val programmes = HashMap<Int, MutableList<Programme>>()

        var channelId: String? = null
        var progChannel: TvChannel? = null
        var progStart = 0L
        var progStop = 0L
        var progTitle = ""
        var progDesc = ""
        var tag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (parser.name) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id")
                            channelId?.let { id -> match(id)?.let { idToChannel[id] = it } }
                        }
                        "programme" -> {
                            val ch = idToChannel[parser.getAttributeValue(null, "channel").orEmpty()]
                            progChannel = ch
                            progTitle = ""
                            progDesc = ""
                            if (ch != null) {
                                progStart = parseTime(parser.getAttributeValue(null, "start"))
                                progStop = parseTime(parser.getAttributeValue(null, "stop"))
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (tag) {
                            "display-name" -> {
                                val id = channelId
                                if (id != null && id !in idToChannel) match(text)?.let { idToChannel[id] = it }
                            }
                            "title" -> if (progChannel != null && progTitle.isEmpty()) progTitle = text
                            "desc" -> if (progChannel != null && progDesc.isEmpty()) progDesc = text.take(280)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> channelId = null
                        "programme" -> {
                            val ch = progChannel
                            if (ch != null && progStop > from && progStart < to && progTitle.isNotEmpty()) {
                                programmes.getOrPut(ch.number) { mutableListOf() } +=
                                    Programme(progStart, progStop, progTitle, progDesc)
                            }
                            progChannel = null
                        }
                    }
                    tag = null
                }
            }
            event = parser.next()
        }

        return TvChannels.all.map { ch ->
            ChannelGuide(ch, programmes[ch.number].orEmpty().sortedBy { it.start }.distinctBy { it.start })
        }
    }

    private fun match(raw: String): TvChannel? {
        val base = raw.lowercase().filter { it.isLetterOrDigit() }
        val candidates = listOf(
            base,
            base.removeSuffix("it"),
            base.removeSuffix("hd"),
            base.removeSuffix("hd").removeSuffix("it"),
            base.removeSuffix("it").removeSuffix("hd"),
        )
        return TvChannels.all.firstOrNull { ch -> ch.aliases.any { it in candidates } }
    }

    private fun parseTime(value: String?): Long =
        runCatching { OffsetDateTime.parse(value?.trim(), TIME_FORMAT).toInstant().toEpochMilli() }.getOrDefault(0L)

    private fun readCache(): List<ChannelGuide>? = runCatching {
        val root = JSONObject(cache.readText())
        val arr = root.getJSONArray("channels")
        val byNumber = TvChannels.all.associateBy { it.number }
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val ch = byNumber[o.getInt("n")] ?: return@mapNotNull null
            val progs = o.getJSONArray("p")
            ChannelGuide(ch, (0 until progs.length()).map { j ->
                val p = progs.getJSONObject(j)
                Programme(p.getLong("s"), p.getLong("e"), p.getString("t"), p.optString("d"))
            })
        }
    }.getOrNull()

    private fun writeCache(guides: List<ChannelGuide>) {
        val arr = JSONArray()
        guides.forEach { g ->
            val progs = JSONArray()
            g.programmes.forEach { p ->
                progs.put(JSONObject().put("s", p.start).put("e", p.stop).put("t", p.title).put("d", p.description))
            }
            arr.put(JSONObject().put("n", g.channel.number).put("p", progs))
        }
        runCatching { cache.writeText(JSONObject().put("channels", arr).toString()) }
    }

    companion object {
        private const val EPG_URL = "https://epgshare01.online/epgshare01/epg_ripper_IT1.xml.gz"
        private const val HOUR = 60L * 60 * 1000
        private const val MAX_AGE_MS = 12 * HOUR
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    }
}
