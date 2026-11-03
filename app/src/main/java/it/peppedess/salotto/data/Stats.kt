package it.peppedess.salotto.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AppUsage(val packageName: String, val minutes: Int)

data class WeekStats(
    val days: List<Pair<LocalDate, Int>>,
    val apps: List<AppUsage>,
) {
    val totalMinutes: Int get() = days.sumOf { it.second }
}

/** Tempo passato nelle app di intrattenimento negli ultimi 7 giorni (permesso "Accesso utilizzo"). */
object StatsRepository {

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun week(context: Context, extraPackages: Set<String>): WeekStats = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val pm = context.packageManager
        val perApp = HashMap<String, Long>()
        val perDay = mutableListOf<Pair<LocalDate, Int>>()

        for (offset in 6 downTo 0) {
            val day = today.minusDays(offset.toLong())
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val stats = usm?.queryAndAggregateUsageStats(start, end).orEmpty()
            var dayTotal = 0L
            stats.forEach { (pkg, s) ->
                if (pkg == context.packageName) return@forEach
                if (!isEntertainment(pm, pkg, extraPackages)) return@forEach
                val ms = s.totalTimeInForeground
                if (ms <= 0) return@forEach
                dayTotal += ms
                perApp[pkg] = (perApp[pkg] ?: 0L) + ms
            }
            perDay += day to (dayTotal / 60_000L).toInt()
        }

        WeekStats(
            days = perDay,
            apps = perApp.entries
                .map { AppUsage(it.key, (it.value / 60_000L).toInt()) }
                .filter { it.minutes > 0 }
                .sortedByDescending { it.minutes },
        )
    }

    private fun isEntertainment(pm: android.content.pm.PackageManager, pkg: String, extra: Set<String>): Boolean {
        if (pkg in extra) return true
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        return info.category == ApplicationInfo.CATEGORY_VIDEO ||
            info.category == ApplicationInfo.CATEGORY_AUDIO ||
            info.category == ApplicationInfo.CATEGORY_GAME
    }
}
