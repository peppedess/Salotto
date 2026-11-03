package it.peppedess.salotto.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Scope della transizione condivisa tra le schermate (locandina → scheda). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Scope dell'animazione della schermata corrente. */
val LocalScreenScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Suono di navigazione da riprodurre quando la selezione si sposta con il D-pad. */
val LocalNavSound = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Scorrimento "a perno" come su Google TV: l'elemento selezionato resta fermo
 * a [startPx] dal bordo e sono le altre righe o locandine a scorrere.
 */
@OptIn(ExperimentalFoundationApi::class)
fun pivotSpec(startPx: Float): BringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = if (size + startPx > containerSize) 0f else startPx
        return offset - target
    }
}

/**
 * Collega l'elemento a quello con la stessa chiave nell'altra schermata,
 * così la locandina "vola" nella scheda dettagli e torna indietro.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val screen = LocalScreenScope.current
    if (key == null || shared == null || screen == null) return this
    return with(shared) {
        this@sharedPoster.sharedElement(rememberSharedContentState("poster:$key"), screen)
    }
}

/** Ingresso a cascata: dissolvenza e leggero scorrimento verso l'alto, con ritardo per posizione. */
@Composable
fun Modifier.enterCascade(index: Int, stepMs: Long = 60L): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMs)
        progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 48f
    }
}
