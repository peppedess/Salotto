package it.peppedess.salotto.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import coil3.compose.AsyncImage
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ContinueItem
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
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "scale",
    )
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "border")

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
                    if (state.isFocused) onFocus()
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

@Composable
fun ShelfRow(title: String, order: Int, icon: ImageBitmap? = null, content: LazyListScope.() -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order.coerceAtMost(8) * 70L)
        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 80f
            }
    ) {
        Row(
            Modifier.padding(start = 40.dp, top = 14.dp, end = 40.dp),
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
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
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
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(220.dp).aspectRatio(16f / 9f),
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
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
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

/** Locandina verticale di un film o una serie. */
@Composable
fun PosterCard(
    title: TmdbTitle,
    tint: Color,
    onClick: () -> Unit,
    onFocus: (Color) -> Unit,
) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(118.dp).aspectRatio(2f / 3f),
        shape = RoundedCornerShape(14.dp),
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
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
