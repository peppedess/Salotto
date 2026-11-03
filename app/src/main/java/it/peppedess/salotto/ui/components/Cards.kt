package it.peppedess.salotto.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ContinueItem
import it.peppedess.salotto.data.HaEntity
import androidx.compose.animation.animateColorAsState
import it.peppedess.salotto.data.TmdbTitle
import kotlinx.coroutines.delay

/**
 * Elemento focalizzabile in stile Google TV: funziona col tocco e col D-pad
 * (telecomando, gamepad, tastiera). Tasto Menu o pressione lunga = opzioni.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusScale: Float = 1.07f,
    onFocus: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    onHorizontal: ((Int) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.95f
            focused -> focusScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "scale",
    )
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "border")
    val navSound = LocalNavSound.current

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { state ->
                if (state.isFocused != focused) {
                    focused = state.isFocused
                    if (state.isFocused) {
                        navSound()
                        onFocus()
                    }
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else if (e.key == Key.Menu && onMenu != null) {
                    onMenu()
                    true
                } else if (e.key == Key.DirectionLeft && onHorizontal != null) {
                    onHorizontal(-1)
                } else if (e.key == Key.DirectionRight && onHorizontal != null) {
                    onHorizontal(1)
                } else {
                    false
                }
            }
            .clip(shape)
            .border(3.dp, Color.White.copy(alpha = borderAlpha), shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onMenu,
            ),
    ) {
        content(focused)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfRow(
    title: String,
    order: Int,
    icon: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    // L'ingresso a cascata si vede solo la prima volta: scorrendo le righe restano visibili.
    val firstTime = remember(title) { title !in shownRows }
    val appear = remember(title) { Animatable(if (firstTime) 0f else 1f) }
    LaunchedEffect(title) {
        if (firstTime) {
            shownRows += title
            delay(order.coerceAtMost(5) * 45L)
            appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val density = LocalDensity.current
    val start = rowStart
    val pivot = remember(density, start) { with(density) { pivotSpec(start.toPx()) } }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 40f
            }
    ) {
        Row(
            Modifier.padding(start = start, top = 10.dp, end = start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(icon, null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides pivot) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = start, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}


@Composable
fun AppCard(
    app: AppEntry,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onFocus: (Color) -> Unit,
    modifier: Modifier = Modifier.width(150.dp),
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        onFocus = { onFocus(app.color) },
        onMenu = onMenu,
    ) {
        val banner = app.banner
        if (banner != null) {
            Image(banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(app.color, app.color.darken(0.45f)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(app.icon, null, Modifier.size(40.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueCard(
    item: ContinueItem,
    app: AppEntry?,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onFocus: (Color) -> Unit,
) {
    val tint = app?.color ?: Color(0xFF2B3445)
    val remaining = if (item.durationMs > 0) ((item.durationMs - item.positionMs) / 60_000L).toInt() else -1
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(200.dp).aspectRatio(16f / 9f),
        onFocus = { onFocus(tint) },
        onMenu = onMenu,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint, tint.darken(0.5f)))))
        if (app != null) {
            Image(app.icon, null, Modifier.size(44.dp).align(Alignment.Center))
        }
        val model = item.artModel
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
        )
        if (app != null) {
            Image(
                app.icon,
                app.label,
                Modifier.padding(8.dp).size(22.dp).align(Alignment.TopStart),
            )
        }
        if (item.playing) {
            Text(
                text = "In riproduzione",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val info = listOfNotNull(
                item.subtitle.takeIf { it.isNotBlank() },
                when {
                    remaining > 1 -> "mancano $remaining min"
                    remaining in 0..1 -> "quasi finito"
                    else -> null
                },
            ).joinToString(" · ")
            if (info.isNotBlank()) {
                Text(
                    text = info,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.durationMs > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}

private val shownRows = mutableSetOf<String>()

/** Locandina di un film o una serie: le informazioni compaiono nell'intestazione in alto. */
@Composable
fun PosterCard(
    title: TmdbTitle,
    tint: Color,
    onClick: () -> Unit,
    onFocus: (Color) -> Unit,
    shareKey: String? = null,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.width(POSTER_W).height(POSTER_H),
        shape = RoundedCornerShape(12.dp),
        focusScale = 1.08f,
        onFocus = { onFocus(tint) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(tint, tint.darken(0.6f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
        if (title.posterUrl != null) {
            AsyncImage(
                model = title.posterUrl,
                contentDescription = title.title,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedPoster(shareKey),
                contentScale = ContentScale.Crop,
            )
        }
        if (title.rating >= 0.5) {
            Text(
                text = "★ " + String.format(java.util.Locale.ITALIAN, "%.1f", title.rating),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

val POSTER_W = 104.dp
val POSTER_H = 156.dp

/** Riquadro di un'entità di Home Assistant: luce, interruttore, scena… */
@Composable
fun HaTile(
    entity: HaEntity,
    onClick: () -> Unit,
    onFocus: (Color) -> Unit,
) {
    val active = entity.isOn || entity.isAction
    val accent = if (entity.domain == "light") Color(0xFFFFC960) else MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        when {
            entity.unavailable -> Color.White.copy(alpha = 0.05f)
            entity.isOn -> accent.copy(alpha = 0.32f)
            else -> Color.White.copy(alpha = 0.09f)
        },
        label = "haBg",
    )
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(170.dp).height(88.dp),
        shape = RoundedCornerShape(20.dp),
        onFocus = { onFocus(if (entity.isOn) accent.darken(0.4f) else Color(0xFF2B3445)) },
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color.White.copy(alpha = 0.20f) else bg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (entity.isOn) accent else Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    entity.icon(),
                    null,
                    tint = if (entity.isOn) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entity.stateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = if (active) 0.85f else 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        focusScale = 1.12f,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun TvPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    primary: Boolean = false,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        focusScale = 1.06f,
    ) { focused ->
        val bg = when {
            focused -> Color.White
            primary -> MaterialTheme.colorScheme.primary
            selected -> Color.White.copy(alpha = 0.20f)
            else -> Color.White.copy(alpha = 0.08f)
        }
        val fg = when {
            focused -> Color.Black
            primary -> MaterialTheme.colorScheme.onPrimary
            else -> Color.White
        }
        Row(
            Modifier
                .fillMaxHeight()
                .background(bg)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun MenuEntry(icon: ImageVector, text: String, onClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        focusScale = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (focused) Color.Black else Color.White
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = fg, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
