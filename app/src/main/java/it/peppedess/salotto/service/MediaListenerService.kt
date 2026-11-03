package it.peppedess.salotto.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import it.peppedess.salotto.data.ContinueStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Ascolta le sessioni multimediali attive (YouTube, Netflix, Prime Video…)
 * e alimenta la riga "Continua a guardare".
 */
class MediaListenerService : NotificationListenerService() {

    private var sessions: MediaSessionManager? = null
    private val registered = mutableListOf<Pair<MediaController, MediaController.Callback>>()

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

    private fun snapshot(controller: MediaController) {
        val md = controller.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return
        val subtitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val state = controller.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        var position = state?.position ?: 0L
        if (playing && state != null && state.lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }
        if (duration > 0) position = position.coerceAtMost(duration)

        ContinueStore.get(this).upsert(
            packageName = controller.packageName,
            title = title,
            subtitle = subtitle,
            art = art,
            positionMs = position,
            durationMs = duration,
            playing = playing,
        )
    }

    companion object {
        /** Controller delle sessioni ancora vive, per riprendere la riproduzione. */
        val active = ConcurrentHashMap<String, MediaController>()
    }
}
