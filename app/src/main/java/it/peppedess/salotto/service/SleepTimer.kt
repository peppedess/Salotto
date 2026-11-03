package it.peppedess.salotto.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.edit
import it.peppedess.salotto.kiosk.KioskAdmin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Timer di spegnimento: allo scadere mette in pausa e spegne lo schermo. */
object SleepTimer {
    private const val PREFS = "sleep"
    private const val KEY_AT = "at"

    private val _endsAt = MutableStateFlow(0L)
    /** Istante di fine in millisecondi, 0 se il timer non è attivo. */
    val endsAt: StateFlow<Long> = _endsAt.asStateFlow()

    fun load(context: Context) {
        val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0L)
        _endsAt.value = if (at > System.currentTimeMillis()) at else 0L
    }

    fun start(context: Context, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        save(context, at)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        save(context, 0L)
    }

    internal fun fire(context: Context) {
        save(context, 0L)
        pauseEverything(context)
        lockScreen(context)
    }

    private fun save(context: Context, at: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_AT, at) }
        _endsAt.value = at
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SleepReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun pauseEverything(context: Context) {
        MediaListenerService.active.values.forEach { runCatching { it.transportControls.pause() } }
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    /** Spegne lo schermo: amministratore del dispositivo se attivo, altrimenti root. */
    fun lockScreen(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, KioskAdmin::class.java)
        if (dpm != null && dpm.isAdminActive(admin)) {
            if (runCatching { dpm.lockNow() }.isSuccess) return true
        }
        return runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 223")).waitFor() == 0
        }.getOrDefault(false)
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                SleepTimer.fire(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
