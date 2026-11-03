package it.peppedess.salotto.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** true sugli schermi stretti: telefono o tablet in verticale, finestra a metà schermo. */
val LocalNarrow = staticCompositionLocalOf { false }

/** Margine sinistro di righe e titoli, come il bordo sicuro di una TV. */
val rowStart: Dp
    @Composable
    @ReadOnlyComposable
    get() = if (LocalNarrow.current) 20.dp else 40.dp

/**
 * Adatta l'interfaccia allo schermo, come fanno le TV con il loro "spazio di progetto".
 *
 * Su tablet e TV tutto viene ingrandito in proporzione, così locandine, testi e margini
 * hanno sempre la stessa presenza; sugli schermi stretti si usa l'impaginazione verticale.
 * La dimensione del testo di sistema viene rispettata fino a un limite che non rompe le schede.
 */
@Composable
fun AdaptiveScale(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    val w = config.screenWidthDp.toFloat().coerceAtLeast(1f)
    val h = config.screenHeightDp.toFloat().coerceAtLeast(1f)
    val landscape = w >= h
    val scale = if (landscape) (h / 500f).coerceIn(1f, 1.5f) else (w / 560f).coerceIn(1f, 1.45f)
    val density = remember(base.density, base.fontScale, scale) {
        Density(base.density * scale, base.fontScale.coerceAtMost(1.15f))
    }
    val narrow = w / scale < 600f
    CompositionLocalProvider(LocalDensity provides density, LocalNarrow provides narrow) {
        content()
    }
}
