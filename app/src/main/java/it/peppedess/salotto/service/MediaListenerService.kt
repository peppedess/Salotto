package it.peppedess.salotto.service

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import it.peppedess.salotto.data.ContinueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Ascolta le sessioni multimediali attive (YouTube, Netflix, Prime Video…)
 * e alimenta la riga "Continua a guardare", con la copertina più grande disponibile.
 */
class MediaListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessions: MediaSessionManager? = null
    private val registered = mutableListOf<Pair<MediaController, MediaController.Callback>>()
    private val triedUris: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            track(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            track(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        release()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        release()
        scope.cancel()
        super.onDestroy()
    }

    private fun track(controllers: List<MediaController>) {
        release()
        controllers
            .filter { it.packageName != packageName }
            .forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        snapshot(controller)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        snapshot(controller)
                    }

                    override fun onSessionDestroyed() {
                        active.remove(controller.packageName, controller)
                        if (nowPlaying.value?.packageName == controller.packageName) nowPlaying.value = null
                        ContinueStore.get(this@MediaListenerService).markStopped(controller.packageName)
                    }
                }
                runCatching { controller.registerCallback(callback) }
                registered += controller to callback
                active[controller.packageName] = controller
                snapshot(controller)
            }
    }

    private fun release() {
        registered.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        registered.clear()
        active.clear()
    }

    override fun onCreate() {
        super.onCreate()
        nowPlaying.value = null
    }

    private fun snapshot(controller: MediaController) {
        val md = controller.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return
        val subtitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""

        // Tra le immagini incorporate nella sessione prende la più grande.
        val art = listOfNotNull(
            md.getBitmap(MediaMetadata.METADATA_KEY_ART),
            md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        ).maxByOrNull { it.width * it.height }

        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val state = controller.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        var position = state?.position ?: 0L
        if (playing && state != null && state.lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }
        if (duration > 0) position = position.coerceAtMost(duration)

        // Barra "Ora in riproduzione": vince la sessione che sta suonando.
        val current = nowPlaying.value
        if (playing || current == null || current.packageName == controller.packageName) {
            nowPlaying.value = NowPlaying(
                packageName = controller.packageName,
                title = title,
                subtitle = subtitle,
                art = art,
                playing = playing,
                positionMs = position,
                durationMs = duration,
            )
        }

        val store = ContinueStore.get(this)
        store.upsert(
            packageName = controller.packageName,
            title = title,
            subtitle = subtitle,
            art = art,
            positionMs = position,
            durationMs = duration,
            playing = playing,
        )

        // Molte app indicano anche l'indirizzo della copertina in alta risoluzione.
        val uris = listOfNotNull(
            md.getString(MediaMetadata.METADATA_KEY_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
        ).filter { it.isNotBlank() }
        if (uris.isEmpty()) return
        val id = ContinueStore.idFor(controller.packageName, title)
        val fresh = uris.filter { triedUris.add("$id|$it") }
        if (fresh.isEmpty()) return
        scope.launch {
            val best = fresh.mapNotNull { loadUri(it) }.maxByOrNull { it.width * it.height } ?: return@launch
            store.updateArt(id, best)
        }
    }

    private fun loadUri(uri: String): Bitmap? = runCatching {
        val parsed = Uri.parse(uri)
        val stream = when (parsed.scheme) {
            "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
            }.inputStream
            else -> contentResolver.openInputStream(parsed)
        }
        stream?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    companion object {
        /** Controller delle sessioni ancora vive, per riprendere la riproduzione. */
        val active = ConcurrentHashMap<String, MediaController>()

        /** Contenuto in riproduzione (o appena messo in pausa) in qualsiasi app. */
        val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    }
}

data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String,
    val art: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)
