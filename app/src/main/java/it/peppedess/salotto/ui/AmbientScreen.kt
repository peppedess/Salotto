package it.peppedess.salotto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.peppedess.salotto.ui.components.icon
import it.peppedess.salotto.ui.theme.Bg
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class AmbientSlide(val art: Any, val caption: String)

/**
 * Salvaschermo in stile Google TV: immagini dei titoli a tutto schermo con zoom lento,
 * orologio, data e meteo. Si chiude con qualsiasi tocco o tasto.
 */
@Composable
fun AmbientScreen(vm: HomeViewModel) {
    val rows by vm.providerRows.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val media by vm.continueItems.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val source = settings.screensaverSource

    val slides = remember(rows, trending, media, photos, source) {
        val photoSlides = photos.map { p ->
            val date = java.time.Instant.ofEpochMilli(p.takenAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            AmbientSlide(p.uri, date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)))
        }
        if (source == 1 && photoSlides.isNotEmpty()) return@remember photoSlides.shuffled()
        val out = mutableListOf<AmbientSlide>()
        if (source == 2) out += photoSlides.shuffled().take(20)
        media.take(3).forEach { item ->
            item.artModel?.let { out += AmbientSlide(it, "Continua a guardare · ${item.title}") }
        }
        rows.forEach { row ->
            row.titles.filter { it.backdropUrl != null }.take(4).forEach { t ->
                out += AmbientSlide(t.backdropUrl!!, "${t.title} · su ${row.app.label}")
            }
        }
        trending.filter { it.backdropUrl != null }.take(6).forEach { t ->
            out += AmbientSlide(t.backdropUrl!!, "${t.title} · di tendenza")
        }
        out.distinctBy { it.art }.shuffled()
    }

    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(slides.size) {
        while (slides.size > 1) {
            delay(SLIDE_MS)
            index = (index + 1) % slides.size
        }
    }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // Piccolo spostamento periodico dei testi per non segnare lo schermo.
    var shift by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            if (now.second == 0) shift = (shift + 1) % 4
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val dx by animateDpAsState((shift % 2 * 18).dp, tween(2000), label = "dx")
    val dy by animateDpAsState((shift / 2 * 14).dp, tween(2000), label = "dy")

    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val current = slides.getOrNull(index % slides.size.coerceAtLeast(1))
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn(tween(1800)) togetherWith fadeOut(tween(1800)) },
            label = "ambient",
        ) { slide ->
            if (slide != null) {
                KenBurnsImage(slide.art)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(DefaultAmbient, Bg)))
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = dx, y = -dy)
                .padding(start = 48.dp, bottom = 36.dp)
        ) {
            Text(
                text = now.format(timeFormat),
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = now.format(dateFormat).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            weather?.let { w ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(w.icon(), null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${w.temperature}° · ${w.description} · ${w.city}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "↑${w.max}° ↓${w.min}°",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }

        if (current != null) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -dx, y = -dy)
                    .padding(end = 48.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = current.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun KenBurnsImage(art: Any) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(art) {
        progress.animateTo(1f, tween((SLIDE_MS + 2000).toInt(), easing = LinearEasing))
    }
    // Direzione dello spostamento diversa per ogni immagine.
    val seed = remember(art) { (art.hashCode() and 3) }
    AsyncImage(
        model = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                val scale = 1.05f + 0.12f * p
                scaleX = scale
                scaleY = scale
                transformOrigin = when (seed) {
                    0 -> TransformOrigin(0.2f, 0.3f)
                    1 -> TransformOrigin(0.8f, 0.3f)
                    2 -> TransformOrigin(0.2f, 0.7f)
                    else -> TransformOrigin(0.8f, 0.7f)
                }
            },
    )
}

private const val SLIDE_MS = 14_000L
