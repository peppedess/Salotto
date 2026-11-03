package it.peppedess.salotto.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

data class AppEntry(
    val key: String,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val color: Color,
    /** Video, streaming, giochi o app per TV. */
    val entertainment: Boolean = false,
)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.Default) {
        launcherApps.getActivityList(null, Process.myUserHandle())
            .asSequence()
            .filter { it.applicationInfo.packageName != context.packageName }
            .distinctBy { it.componentName.flattenToString() }
            .mapNotNull { info -> runCatching { toEntry(info) }.getOrNull() }
            .sortedBy { it.label.lowercase(Locale.ITALIAN) }
            .toList()
    }

    private fun toEntry(info: LauncherActivityInfo): AppEntry {
        val icon = info.getIcon(density).toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val banner = loadBanner(info)
        val palette = Palette.from(banner ?: icon).maximumColorCount(12).generate()
        val raw = palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: 0xFF33415C.toInt()
        val tone = ColorUtils.blendARGB(raw, android.graphics.Color.BLACK, 0.35f)
        val label = info.label.toString()
        return AppEntry(
            key = info.componentName.flattenToString(),
            packageName = info.applicationInfo.packageName,
            label = label,
            icon = icon.asImageBitmap(),
            banner = banner?.asImageBitmap(),
            color = Color(tone),
            entertainment = isEntertainment(info.applicationInfo, label),
        )
    }

    private fun isEntertainment(ai: ApplicationInfo, label: String): Boolean {
        val pkg = ai.packageName
        if (EXCLUDED.any { pkg.startsWith(it) }) return false
        if (ai.category == ApplicationInfo.CATEGORY_VIDEO || ai.category == ApplicationInfo.CATEGORY_GAME) return true
        @Suppress("DEPRECATION")
        if (ai.flags and ApplicationInfo.FLAG_IS_GAME != 0) return true
        if (KNOWN.any { pkg.startsWith(it) }) return true
        val words = label.lowercase(Locale.ITALIAN).split(' ', '-', '+', '.', ':')
        return words.any { it in KEYWORDS }
    }

    private fun loadBanner(info: LauncherActivityInfo): Bitmap? {
        val pkg = info.applicationInfo.packageName
        val drawable: Drawable = runCatching {
            pm.getLeanbackLaunchIntentForPackage(pkg)?.component?.let { pm.getActivityBanner(it) }
        }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(info.applicationInfo) }.getOrNull()
            ?: return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 320
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 180
        val scale = BANNER_PX / w.toFloat()
        return drawable.toBitmap(
            (w * scale).roundToInt().coerceAtLeast(1),
            (h * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
    }

    private companion object {
        /** Servizi di streaming, TV e lettori video più diffusi in Italia. */
        val KNOWN = listOf(
            "com.netflix", "com.amazon.avod", "com.disney", "it.rainet", "it.rai.", "it.mediaset",
            "com.dazn", "it.sky", "com.nowtv", "com.cbs.", "com.paramount", "com.apple.atve",
            "com.crunchyroll", "tv.twitch", "com.google.android.youtube", "com.google.android.apps.youtube.kids",
            "com.google.android.videos", "com.plexapp", "org.xbmc.kodi", "org.videolan.vlc", "com.mxtech",
            "com.stremio", "it.telecomitalia", "it.timvision", "com.discovery", "it.dplay", "tv.pluto",
            "tv.rakuten", "it.chili", "com.mubi", "it.la7", "com.wbd", "com.hbo", "com.zattoo",
            "com.samsung.android.tvplus", "com.infuse", "com.jellyfin", "org.jellyfin", "com.emby",
            "com.ionitech.airscreen", "it.tv8", "com.sky.", "com.peacocktv",
        )
        /** App che non sono intrattenimento video anche se ne hanno l'aspetto. */
        val EXCLUDED = listOf(
            "com.google.android.apps.youtube.music", "com.google.android.apps.youtube.creator",
            "com.spotify", "deezer.", "com.amazon.mp3", "com.android.vending",
            "com.futuremark", "com.finalwire", "com.apkmirror", "org.fdroid", "com.primatelabs",
            "com.antutu", "com.google.android.apps.photos", "com.android.camera", "com.miui.",
            "com.xiaomi.", "com.android.", "com.google.android.apps.youtube.creator",
        )
        val KEYWORDS = setOf("tv", "film", "cinema", "movie", "movies", "streaming", "anime", "serie")
        const val ICON_PX = 192
        const val BANNER_PX = 480f
    }
}
