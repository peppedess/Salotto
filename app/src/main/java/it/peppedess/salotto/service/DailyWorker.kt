package it.peppedess.salotto.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.peppedess.salotto.MainActivity
import it.peppedess.salotto.R
import it.peppedess.salotto.data.Prefs
import it.peppedess.salotto.data.TmdbClient
import it.peppedess.salotto.data.Updater
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Controllo periodico: nuovi episodi delle serie in lista e nuove versioni di Salotto. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val settings = prefs.settings.value
        if (settings.episodeAlerts && settings.tmdbKey.isNotBlank()) {
            runCatching { checkEpisodes(prefs, settings.tmdbKey) }
        }
        if (settings.autoUpdate) {
            runCatching {
                Updater.check(applicationContext)?.let { info ->
                    notify(
                        id = 9_000,
                        channel = CHANNEL_UPDATES,
                        title = "Aggiornamento di Salotto disponibile",
                        text = "Build ${info.build}: apri Salotto per installarla.",
                    )
                }
            }
        }
        return Result.success()
    }

    private suspend fun checkEpisodes(prefs: Prefs, key: String) {
        val client = TmdbClient(key)
        val today = LocalDate.now()
        prefs.allSeriesInLists().forEach { series ->
            val ep = runCatching { client.lastEpisode(series) }.getOrNull() ?: return@forEach
            val aired = runCatching { LocalDate.parse(ep.airDate) }.getOrNull() ?: return@forEach
            if (aired.isAfter(today) || aired.isBefore(today.minusDays(3))) return@forEach
            if (prefs.notifiedEpisode(series.id) == ep.code) return@forEach
            prefs.setNotifiedEpisode(series.id, ep.code)
            notify(
                id = series.id,
                channel = CHANNEL_EPISODES,
                title = "Nuovo episodio di ${series.title}",
                text = "Stagione ${ep.season}, episodio ${ep.episode}" + if (ep.name.isNotBlank()) ": ${ep.name}" else "",
            )
        }
    }

    private fun notify(id: Int, channel: String, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, notification) }
    }

    companion object {
        const val CHANNEL_EPISODES = "episodi"
        const val CHANNEL_UPDATES = "aggiornamenti"
        private const val WORK = "salotto-daily"

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_EPISODES, "Nuovi episodi", NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_UPDATES, "Aggiornamenti di Salotto", NotificationManager.IMPORTANCE_LOW)
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
