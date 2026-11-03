package it.peppedess.salotto.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import it.peppedess.salotto.service.InstallReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class UpdateInfo(val build: Int, val apkUrl: String, val notes: String)

/** Aggiornamenti dalle release pubbliche di GitHub (tag build-N). */
object Updater {
    private const val API = "https://api.github.com/repos/peppedess/Salotto/releases/latest"

    fun currentBuild(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .build()
        val json = Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
        val build = json.optString("tag_name").substringAfter("build-").toIntOrNull() ?: return@withContext null
        if (build <= currentBuild(context)) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?: return@withContext null
        UpdateInfo(build, apk.optString("browser_download_url"), json.optString("body"))
    }

    /** Scarica e installa: in silenzio con il root, altrimenti con la conferma di Android. */
    suspend fun install(context: Context, info: UpdateInfo): String = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "salotto-update.apk")
        Net.client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download ${response.code}")
            file.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
        }
        if (installWithRoot(file)) return@withContext "Installazione in corso…"
        installWithSession(context, file)
        "Conferma l'installazione nella finestra di Android"
    }

    private fun installWithRoot(file: File): Boolean = runCatching {
        val tmp = "/data/local/tmp/salotto-update.apk"
        val cmd = "cp '${file.absolutePath}' $tmp && chmod 644 $tmp && pm install -r $tmp; rm -f $tmp"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor() == 0 && out.contains("Success")
    }.getOrDefault(false)

    private fun installWithSession(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("salotto.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
