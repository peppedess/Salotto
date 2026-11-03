package it.peppedess.salotto.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import it.peppedess.salotto.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Kiosk {

    fun admin(context: Context) = ComponentName(context, KioskAdmin::class.java)

    fun isOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            ?.isDeviceOwnerApp(context.packageName) == true

    /**
     * Con Salotto proprietario del dispositivo: blocco completo (lock task).
     * Senza: solo schermo acceso, barre nascoste e impostazioni protette da PIN.
     */
    fun apply(activity: Activity, enabled: Boolean, packages: List<String>) {
        val dpm = activity.getSystemService(DevicePolicyManager::class.java) ?: return
        val am = activity.getSystemService(ActivityManager::class.java) ?: return
        val owner = dpm.isDeviceOwnerApp(activity.packageName)
        val admin = admin(activity)
        val locked = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

        if (enabled && owner) {
            runCatching {
                dpm.setLockTaskPackages(admin, (packages + activity.packageName).distinct().toTypedArray())
                if (Build.VERSION.SDK_INT >= 28) {
                    dpm.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                    )
                }
                val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                dpm.addPersistentPreferredActivity(admin, filter, ComponentName(activity, MainActivity::class.java))
            }
            if (!locked) runCatching { activity.startLockTask() }
        } else {
            if (owner) runCatching { dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName) }
            if (locked) runCatching { activity.stopLockTask() }
        }
    }

    @Suppress("DEPRECATION")
    fun releaseOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin(context), context.packageName)
            dpm.clearDeviceOwnerApp(context.packageName)
        }.isSuccess
    }

    /** Usa il root per rendere Salotto proprietario del dispositivo. */
    suspend fun becomeOwnerWithRoot(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val component = "${context.packageName}/${KioskAdmin::class.java.name}"
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dpm set-device-owner $component"))
            val out = process.inputStream.bufferedReader().readText() +
                process.errorStream.bufferedReader().readText()
            val code = process.waitFor()
            (code == 0) to out.trim()
        }.getOrElse { false to (it.message ?: "Root non disponibile") }
    }
}
