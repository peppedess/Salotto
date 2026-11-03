package it.peppedess.salotto.data

import android.content.Context
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
        return AppEntry(
            key = info.componentName.flattenToString(),
            packageName = info.applicationInfo.packageName,
            label = info.label.toString(),
            icon = icon.asImageBitmap(),
            banner = banner?.asImageBitmap(),
            color = Color(tone),
        )
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
        const val ICON_PX = 192
        const val BANNER_PX = 480f
    }
}
