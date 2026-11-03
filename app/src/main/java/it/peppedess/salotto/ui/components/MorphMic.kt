package it.peppedess.salotto.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * Microfono Material 3 Expressive: un cerchio che si trasforma in un "biscotto"
 * pulsante mentre ascolta, seguendo il volume della voce.
 */
@Composable
fun MorphMic(
    listening: Boolean,
    level: Float,
    color: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val morph = remember {
        Morph(
            RoundedPolygon(numVertices = 12, rounding = CornerRounding(0.6f)),
            RoundedPolygon.star(
                numVerticesPerRadius = 9,
                innerRadius = 0.78f,
                rounding = CornerRounding(0.28f),
                innerRounding = CornerRounding(0.28f),
            ),
        )
    }
    val transition = rememberInfiniteTransition(label = "mic")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "spin",
    )
    val active by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "active",
    )
    val smoothLevel by animateFloatAsState(level, label = "level")

    val path = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val progress = (active * (0.3f + 0.7f * maxOf(wave * 0.6f, smoothLevel))).coerceIn(0f, 1f)
            path.rewind()
            morph.toPath(progress, path)
            val radius = size.minDimension / 2f * (0.92f + 0.08f * active * smoothLevel)
            matrix.reset()
            matrix.setScale(radius, radius)
            matrix.postRotate(spin * active)
            matrix.postTranslate(center.x, center.y)
            path.transform(matrix)
            drawPath(path.asComposePath(), color)
        }
        Icon(Icons.Rounded.Mic, null, tint = iconTint, modifier = Modifier.fillMaxSize(0.42f))
    }
}
