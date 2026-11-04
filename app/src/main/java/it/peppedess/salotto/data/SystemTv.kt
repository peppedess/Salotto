package it.peppedess.salotto.data

import android.content.Context
import android.provider.Settings
import android.view.Surface
import androidx.core.content.edit

/**
 * Impostazioni di sistema che fanno sembrare il telefono una TV, senza root:
 * rotazione bloccata in orizzontale per tutte le app (permesso "Modifica impostazioni di sistema").
 */
object SystemTv {
    private const val PREFS = "system_tv"
    private const val KEY_SAVED = "saved"
    private const val KEY_ACCEL = "accel"
    private const val KEY_USER_ROT = "user_rotation"

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    /** Blocca tutte le app in orizzontale, salvando le impostazioni precedenti per ripristinarle. */
    fun lockLandscape(context: Context): Boolean {
        if (!canWriteSettings(context)) return false
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cr = context.contentResolver
        return runCatching {
            if (!sp.getBoolean(KEY_SAVED, false)) {
                sp.edit {
                    putInt(KEY_ACCEL, Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1))
                    putInt(KEY_USER_ROT, Settings.System.getInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_0))
                    putBoolean(KEY_SAVED, true)
                }
            }
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_90)
        }.getOrDefault(false)
    }

    /** Rimette la rotazione com'era prima di Salotto. */
    fun restoreRotation(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_SAVED, false) || !canWriteSettings(context)) return
        val cr = context.contentResolver
        runCatching {
            Settings.System.putInt(cr, Settings.System.USER_ROTATION, sp.getInt(KEY_USER_ROT, Surface.ROTATION_0))
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, sp.getInt(KEY_ACCEL, 1))
        }
        sp.edit { putBoolean(KEY_SAVED, false) }
    }

    /** Pacchetti di SmartTube, il client YouTube con l'interfaccia della TV che funziona anche sui telefoni. */
    val SMARTTUBE = listOf(
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv.beta",
        "com.liskovsoft.smarttubetv",
    )
}
