package it.peppedess.salotto.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.res.ResourcesCompat
import it.peppedess.salotto.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Servizio TV di Salotto (facoltativo, senza root):
 * - i tasti del volume mostrano l'indicatore in stile TV sopra qualsiasi app;
 * - con "Tutte le app in orizzontale" una finestra invisibile tiene lo schermo in orizzontale,
 *   anche per le app che pretendono il verticale.
 */
class TvAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var hud: VolumeBarView? = null
    private var rotationView: View? = null
    private val prefs: SharedPreferences by lazy { getSharedPreferences("salotto", Context.MODE_PRIVATE) }

    private val hideHud = Runnable {
        hud?.animate()?.alpha(0f)?.translationX(dp(24).toFloat())?.setDuration(250)?.start()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        prefs.registerOnSharedPreferenceChangeListener(this)
        instance = WeakReference(this)
        enabled.value = true
        updateRotation()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        enabled.value = false
        instance = null
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(this) }
        handler.removeCallbacks(hideHud)
        hud?.let { runCatching { windowManager?.removeView(it) } }
        hud = null
        rotationView?.let { runCatching { windowManager?.removeView(it) } }
        rotationView = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "landscape_all") updateRotation()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!prefs.getBoolean("tv_volume", true)) return false
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            KeyEvent.KEYCODE_VOLUME_MUTE -> 0
            else -> return false
        }
        val audio = getSystemService(AudioManager::class.java) ?: return false
        // Durante chiamate e suonerie decide il telefono.
        if (audio.mode != AudioManager.MODE_NORMAL) return false
        if (event.action == KeyEvent.ACTION_DOWN) volumeStep(direction)
        return true
    }

    /** Cambia il volume dei contenuti e mostra la barra in stile TV. */
    fun volumeStep(direction: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        val adjust = when {
            direction > 0 -> AudioManager.ADJUST_RAISE
            direction < 0 -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, 0) }
        val level = if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0 else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        showHud(level, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1))
    }

    /**
     * Comandi del telecomando dentro le altre app. Indietro e Home funzionano ovunque;
     * le frecce e OK richiedono Android 13 o successivo.
     */
    fun remoteKey(key: String): Boolean {
        val action = when (key) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "up" -> if (Build.VERSION.SDK_INT >= 33) GLOBAL_ACTION_DPAD_UP else return false
            "down" -> if (Build.VERSION.SDK_INT >= 33) GLOBAL_ACTION_DPAD_DOWN else return false
            "left" -> if (Build.VERSION.SDK_INT >= 33) GLOBAL_ACTION_DPAD_LEFT else return false
            "right" -> if (Build.VERSION.SDK_INT >= 33) GLOBAL_ACTION_DPAD_RIGHT else return false
            "ok" -> if (Build.VERSION.SDK_INT >= 33) GLOBAL_ACTION_DPAD_CENTER else return false
            else -> return false
        }
        return runCatching { performGlobalAction(action) }.getOrDefault(false)
    }

    private fun showHud(level: Int, max: Int) {
        val wm = windowManager ?: return
        val view = hud ?: VolumeBarView(this).also { v ->
            val params = WindowManager.LayoutParams(
                dp(64),
                dp(260),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = dp(28)
            }
            v.alpha = 0f
            v.translationX = dp(24).toFloat()
            if (runCatching { wm.addView(v, params) }.isSuccess) hud = v
        }
        view.setLevel(level, max)
        view.animate().alpha(1f).translationX(0f).setDuration(150).start()
        handler.removeCallbacks(hideHud)
        handler.postDelayed(hideHud, 1800)
    }

    private fun updateRotation() {
        val wm = windowManager ?: return
        val wanted = prefs.getBoolean("landscape_all", false)
        if (wanted && rotationView == null) {
            val v = View(this)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (runCatching { wm.addView(v, params) }.isSuccess) rotationView = v
        } else if (!wanted && rotationView != null) {
            runCatching { wm.removeView(rotationView) }
            rotationView = null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** true mentre il servizio è attivo. */
        val enabled = MutableStateFlow(false)

        @Volatile
        var instance: WeakReference<TvAccessibilityService>? = null
    }
}

/** Barra del volume in stile Google TV, disegnata a mano: niente Compose in una finestra di servizio. */
private class VolumeBarView(context: Context) : View(context) {
    private var level = 0
    private var max = 1
    private val density = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 31, 34, 40) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 15 * density
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_500) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()

    fun setLevel(level: Int, max: Int) {
        this.level = level
        this.max = max.coerceAtLeast(1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, w / 2, w / 2, bg)
        canvas.drawText(level.toString(), w / 2, 30 * density, text)
        val barW = 8 * density
        val top = 46 * density
        val bottom = h - 22 * density
        rect.set(w / 2 - barW / 2, top, w / 2 + barW / 2, bottom)
        canvas.drawRoundRect(rect, barW / 2, barW / 2, track)
        val filled = (bottom - top) * (level.toFloat() / max)
        if (filled > 0f) {
            rect.set(w / 2 - barW / 2, bottom - filled, w / 2 + barW / 2, bottom)
            canvas.drawRoundRect(rect, barW / 2, barW / 2, fill)
        }
    }
}
