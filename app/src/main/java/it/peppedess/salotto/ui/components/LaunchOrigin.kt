package it.peppedess.salotto.ui.components

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference

/**
 * Ricorda dove si trova la scheda appena toccata, così l'app si apre "crescendo"
 * da quel punto, come su Google TV. Senza una scheda recente, cresce dal centro.
 */
object LaunchOrigin {
    private var viewRef: WeakReference<View>? = null
    private var bounds: Rect? = null
    private var at = 0L

    fun attach(view: View) {
        viewRef = WeakReference(view)
    }

    fun set(rect: Rect) {
        bounds = rect
        at = SystemClock.uptimeMillis()
    }

    private fun recent(): Rect? = bounds?.takeIf { SystemClock.uptimeMillis() - at < 2_000L }

    fun sourceBounds(): Rect? = recent()?.let { Rect(it) }

    fun options(): ActivityOptions? {
        val view = viewRef?.get() ?: return null
        if (!view.isAttachedToWindow) return null
        val r = recent()
        return runCatching {
            if (r != null && r.width() > 0 && r.height() > 0) {
                ActivityOptions.makeScaleUpAnimation(view, r.left, r.top, r.width(), r.height())
            } else {
                ActivityOptions.makeScaleUpAnimation(view, view.width / 2, view.height / 2, 0, 0)
            }
        }.getOrNull()
    }
}
