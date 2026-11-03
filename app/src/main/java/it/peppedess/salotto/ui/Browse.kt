package it.peppedess.salotto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ChannelGuide
import it.peppedess.salotto.data.ContinueItem
import it.peppedess.salotto.data.EpisodeInfo
import it.peppedess.salotto.data.HaEntity
import it.peppedess.salotto.data.Programme
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.ui.components.AppCard
import it.peppedess.salotto.ui.components.ContinueCard
import it.peppedess.salotto.ui.components.HaTile
import it.peppedess.salotto.ui.components.PosterCard
import it.peppedess.salotto.ui.components.LocalNarrow
import it.peppedess.salotto.ui.components.rowStart
import it.peppedess.salotto.ui.components.ShelfRow
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.components.darken
import it.peppedess.salotto.ui.components.pivotSpec
import it.peppedess.salotto.ui.theme.Bg
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Modello delle righe
// ---------------------------------------------------------------------------

/** Un elemento di una riga: locandina, app, canale, luce di casa… */
@Immutable
sealed interface ShelfItem {
    val key: String

    data class Poster(val title: TmdbTitle, val tint: Color, val app: AppEntry?, val shareKey: String) : ShelfItem {
        override val key: String get() = "p:" + title.key
    }

    data class Continue(val item: ContinueItem, val app: AppEntry?) : ShelfItem {
        override val key: String get() = "c:" + item.id
    }

    data class App(val app: AppEntry) : ShelfItem {
        override val key: String get() = "a:" + app.key
    }

    data class Live(val guide: ChannelGuide) : ShelfItem {
        override val key: String get() = "l:" + guide.channel.number
    }

    data class Show(val guide: ChannelGuide, val programme: Programme) : ShelfItem {
        override val key: String get() = "s:" + guide.channel.number + ":" + programme.start
    }

    data class Episode(val episode: EpisodeInfo) : ShelfItem {
        override val key: String get() = "e:" + episode.series.key + episode.code
    }

    data class Home(val entity: HaEntity) : ShelfItem {
        override val key: String get() = "h:" + entity.id
    }

    data class Tool(
        val id: String,
        val icon: ImageVector,
        val title: String,
        val subtitle: String,
        val color: Color,
    ) : ShelfItem {
        override val key: String get() = "t:$id"
    }
}

/** Voce della lista principale: una riga oppure un avviso. */
@Immutable
sealed interface BrowseEntry {
    val key: String
}

@Immutable
data class Shelf(
    override val key: String,
    val title: String,
    val items: List<ShelfItem>,
    val icon: ImageBitmap? = null,
) : BrowseEntry

@Immutable
data class Hint(
    override val key: String,
    val icon: ImageVector,
    val title: String,
    val text: String,
    val onClick: () -> Unit,
) : BrowseEntry

// ---------------------------------------------------------------------------
// Intestazione: le informazioni dell'elemento selezionato
// ---------------------------------------------------------------------------

@Immutable
data class Spotlight(
    val key: String,
    val art: Any?,
    val tint: Color,
    val eyebrow: String,
    val title: String,
    val meta: String,
    val overview: String,
    val progress: Float?,
    val action: String,
    val actionIcon: ImageVector,
)

private fun remainingLabel(item: ContinueItem): String? {
    if (item.durationMs <= 0) return null
    val min = ((item.durationMs - item.positionMs) / 60_000L).toInt()
    return if (min > 1) "mancano $min min" else "quasi finito"
}

fun ShelfItem.spotlight(now: Long = System.currentTimeMillis()): Spotlight = when (this) {
    is ShelfItem.Poster -> Spotlight(
        key = key,
        art = title.backdropUrl ?: title.posterUrl,
        tint = tint,
        eyebrow = app?.let { "Su ${it.label}" } ?: title.kindLabel,
        title = title.title,
        meta = title.metaLine(),
        overview = title.overview,
        progress = null,
        action = "Dettagli",
        actionIcon = Icons.Rounded.Info,
    )
    is ShelfItem.Continue -> Spotlight(
        key = key,
        art = item.artModel ?: app?.banner,
        tint = app?.color ?: DefaultAmbient,
        eyebrow = if (item.playing) "In riproduzione" else "Continua a guardare",
        title = item.title,
        meta = listOfNotNull(app?.label, item.subtitle.takeIf { it.isNotBlank() }, remainingLabel(item)).joinToString(" · "),
        overview = "",
        progress = if (item.durationMs > 0) item.progress else null,
        action = "Riprendi",
        actionIcon = Icons.Rounded.PlayArrow,
    )
    is ShelfItem.App -> Spotlight(
        key = key,
        art = app.banner,
        tint = app.color,
        eyebrow = "App",
        title = app.label,
        meta = "",
        overview = "",
        progress = null,
        action = "Apri",
        actionIcon = Icons.Rounded.Apps,
    )
    is ShelfItem.Live -> {
        val p = guide.current(now)
        val next = guide.upcoming(now, 1).firstOrNull()
        Spotlight(
            key = key,
            art = null,
            tint = Color(guide.channel.color),
            eyebrow = "In onda ora su ${guide.channel.name}",
            title = p?.title ?: guide.channel.name,
            meta = listOfNotNull(
                p?.let { "${formatTime(it.start)}–${formatTime(it.stop)}" },
                next?.let { "poi ${formatTime(it.start)} ${it.title}" },
            ).joinToString(" · "),
            overview = p?.description.orEmpty(),
            progress = p?.let { ((now - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
            action = "Guarda in diretta",
            actionIcon = Icons.Rounded.LiveTv,
        )
    }
    is ShelfItem.Show -> {
        val onAir = now in programme.start until programme.stop
        Spotlight(
            key = key,
            art = null,
            tint = Color(guide.channel.color),
            eyebrow = if (onAir) "In onda ora su ${guide.channel.name}" else "Alle ${formatTime(programme.start)} su ${guide.channel.name}",
            title = programme.title,
            meta = "${formatTime(programme.start)}–${formatTime(programme.stop)} · ${((programme.stop - programme.start) / 60_000L)} min",
            overview = programme.description,
            progress = if (onAir) ((now - programme.start).toFloat() / (programme.stop - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f) else null,
            action = "Guarda in diretta",
            actionIcon = Icons.Rounded.LiveTv,
        )
    }
    is ShelfItem.Episode -> Spotlight(
        key = key,
        art = episode.series.backdropUrl ?: episode.series.posterUrl,
        tint = DefaultAmbient,
        eyebrow = "Nuovo episodio",
        title = episode.series.title,
        meta = "Stagione ${episode.season} · Episodio ${episode.episode}" + if (episode.name.isNotBlank()) " · ${episode.name}" else "",
        overview = episode.series.overview,
        progress = null,
        action = "Dettagli",
        actionIcon = Icons.Rounded.Info,
    )
    is ShelfItem.Home -> Spotlight(
        key = key,
        art = null,
        tint = if (entity.isOn) Color(0xFF8A6A1E) else DefaultAmbient,
        eyebrow = "Casa",
        title = entity.name,
        meta = entity.stateLabel,
        overview = "",
        progress = null,
        action = if (entity.isAction) "Avvia" else if (entity.isOn) "Spegni" else "Accendi",
        actionIcon = Icons.Rounded.PowerSettingsNew,
    )
    is ShelfItem.Tool -> Spotlight(
        key = key,
        art = null,
        tint = color,
        eyebrow = "Strumenti",
        title = title,
        meta = subtitle,
        overview = "",
        progress = null,
        action = "Apri",
        actionIcon = icon,
    )
}

// ---------------------------------------------------------------------------
// Schermata di navigazione
// ---------------------------------------------------------------------------

/**
 * Schermata in stile Google TV: in alto lo sfondo e le informazioni dell'elemento selezionato,
 * sotto le righe. Con il telecomando la selezione resta ferma a sinistra; scorrendo col dito
 * l'intestazione si compatta per lasciare spazio alle righe.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    entries: List<BrowseEntry>,
    featured: List<ShelfItem>,
    listState: LazyListState,
    topBarHeight: Dp,
    firstItemFocus: FocusRequester,
    onItem: (ShelfItem) -> Unit,
    onItemMenu: (ShelfItem) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    resetTick: Int,
) {
    var pinned by remember { mutableStateOf<ShelfItem?>(null) }
    var focusedRow by remember { mutableIntStateOf(0) }
    var rowsFocused by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }
    var touchCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(rowsFocused, headerFocused) {
        if (!rowsFocused && !headerFocused) pinned = null
    }
    LaunchedEffect(resetTick) {
        touchCollapsed = false
        pinned = null
        runCatching { listState.scrollToItem(0) }
    }

    // Idle: i titoli in evidenza ruotano da soli.
    var featuredIndex by remember { mutableIntStateOf(0) }
    val featuredState = rememberUpdatedState(featured)
    LaunchedEffect(Unit) {
        while (true) {
            delay(9000)
            if (pinned == null && featuredState.value.size > 1) {
                featuredIndex = (featuredIndex + 1) % featuredState.value.size
            }
        }
    }

    // L'intestazione segue la selezione dopo un istante: niente scatti scorrendo veloce.
    val target = pinned ?: featured.getOrNull(featuredIndex % featured.size.coerceAtLeast(1))
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target?.key) {
        if (shown != null && target != null) delay(160)
        shown = target
    }
    val spotlight = remember(shown) { shown?.spotlight() }

    val collapsed = if (pinned != null) focusedRow >= 1 else touchCollapsed
    LaunchedEffect(collapsed) { onCollapsedChange(collapsed) }

    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pinned == null && available.y < -2f) touchCollapsed = true
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 2f) touchCollapsed = false
                return Offset.Zero
            }
        }
    }

    val narrow = LocalNarrow.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = if (narrow) (maxHeight * 0.48f).coerceIn(260.dp, 520.dp) else (maxHeight * 0.56f).coerceIn(200.dp, 470.dp)
        val compact = if (narrow) (maxHeight * 0.24f).coerceIn(130.dp, 260.dp) else (maxHeight * 0.30f).coerceIn(110.dp, 280.dp)
        // Col dito non c'è un elemento selezionato: scorrendo, l'intestazione sparisce del tutto
        // e lo sfondo si attenua, così non restano informazioni che non c'entrano con le righe.
        val hideHeader = collapsed && pinned == null
        val headerHeight by animateDpAsState(
            targetValue = when {
                hideHeader -> 0.dp
                collapsed -> compact
                else -> full
            },
            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
            label = "header",
        )
        val backdropAlpha by animateFloatAsState(if (hideHeader) 0.22f else 1f, tween(350), label = "backdropAlpha")

        Backdrop(spotlight, narrow, backdropAlpha)

        Column(Modifier.fillMaxSize()) {
            if (headerHeight > 2.dp) {
                Header(
                    spotlight = spotlight,
                    height = headerHeight,
                    topInset = if (collapsed) 12.dp else topBarHeight,
                    compact = collapsed,
                    dots = if (pinned == null && featured.size > 1) featured.size else 0,
                    dotIndex = featuredIndex % featured.size.coerceAtLeast(1),
                    onAction = { shown?.let(onItem) },
                    onFocusChange = { headerFocused = it },
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            val density = LocalDensity.current
            val verticalPivot = remember(density) { with(density) { pivotSpec(44.dp.toPx()) } }
            CompositionLocalProvider(LocalBringIntoViewSpec provides verticalPivot) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(scrollConnection)
                        .onFocusChanged { rowsFocused = it.hasFocus },
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    var shelfIndex = 0
                    entries.forEach { entry ->
                        when (entry) {
                            is Hint -> item(key = entry.key, contentType = "hint") { HintCard(entry) }
                            is Shelf -> {
                                val rowIndex = shelfIndex++
                                item(key = entry.key, contentType = "shelf") {
                                    ShelfRow(entry.title, rowIndex, icon = entry.icon) {
                                        itemsIndexed(entry.items, key = { _, it -> it.key }) { i, item ->
                                            val mod = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                                            ShelfCard(
                                                item = item,
                                                modifier = mod,
                                                onClick = { onItem(item) },
                                                onMenu = { onItemMenu(item) },
                                                onFocus = {
                                                    pinned = item
                                                    focusedRow = rowIndex
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Backdrop(spotlight: Spotlight?, narrow: Boolean, alpha: Float) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Crossfade(
            targetState = spotlight?.key to spotlight?.art,
            animationSpec = tween(450),
            label = "backdrop",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
        ) { (_, art) ->
            val tint = spotlight?.tint ?: DefaultAmbient
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(tint.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(size.width * 0.8f, 0f),
                                radius = size.maxDimension * 0.9f,
                            )
                        )
                    }
            ) {
                val align = if (narrow) Alignment.TopCenter else Alignment.TopEnd
                when (art) {
                    null -> Unit
                    is ImageBitmap -> Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = align)
                    else -> AsyncImage(
                        model = ImageRequest.Builder(context).data(art).size(1280, 720).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = align,
                    )
                }
            }
        }
        // Sfumature su tutto lo schermo, indipendenti dall'immagine: nessun bordo visibile.
        if (narrow) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Bg.copy(alpha = 0.5f),
                            0.22f to Color.Transparent,
                            0.45f to Bg.copy(alpha = 0.75f),
                            0.7f to Bg,
                        )
                    )
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Bg.copy(alpha = 0.97f),
                            0.3f to Bg.copy(alpha = 0.82f),
                            0.6f to Bg.copy(alpha = 0.2f),
                            0.85f to Color.Transparent,
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Bg.copy(alpha = 0.35f),
                            0.15f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.75f to Bg.copy(alpha = 0.88f),
                            1f to Bg,
                        )
                    )
            )
        }
    }
}

@Composable
private fun Header(
    spotlight: Spotlight?,
    height: Dp,
    topInset: Dp,
    compact: Boolean,
    dots: Int,
    dotIndex: Int,
    onAction: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val narrow = LocalNarrow.current
    val start = rowStart
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .padding(start = start, end = start, top = topInset, bottom = 6.dp)
            .onFocusChanged { onFocusChange(it.hasFocus) }
    ) {
        AnimatedContent(
            targetState = spotlight,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            contentKey = { it?.key },
            label = "spotlight",
            modifier = Modifier
                .fillMaxWidth(if (narrow) 1f else 0.56f)
                .fillMaxHeight(),
        ) { s ->
            if (s == null) return@AnimatedContent
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Text(
                    s.eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    s.title,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (s.meta.isNotBlank()) {
                    Text(
                        s.meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (s.overview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val progress = s.progress
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.width(200.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
                if (!compact) {
                    Spacer(Modifier.height(10.dp))
                    TvPill(text = s.action, icon = s.actionIcon, primary = true, onClick = onAction)
                }
            }
        }
        if (dots > 1 && !compact) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(dots) { i ->
                    val w by animateDpAsState(if (i == dotIndex) 20.dp else 6.dp, label = "dot")
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == dotIndex) 0.95f else 0.4f))
                    )
                }
            }
        }
    }
}

/** Disegna la scheda giusta per ogni tipo di elemento. */
@Composable
fun ShelfCard(
    item: ShelfItem,
    modifier: Modifier,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onFocus: () -> Unit,
) {
    val focus: (Color) -> Unit = { onFocus() }
    Box(modifier) {
        when (item) {
            is ShelfItem.Poster -> PosterCard(item.title, item.tint, onClick = onClick, onFocus = focus, shareKey = item.shareKey)
            is ShelfItem.Continue -> ContinueCard(item.item, item.app, onClick = onClick, onMenu = onMenu, onFocus = focus)
            is ShelfItem.App -> AppCard(item.app, onClick = onClick, onMenu = onMenu, onFocus = focus, modifier = Modifier.width(150.dp))
            is ShelfItem.Live -> LiveCard(item.guide, System.currentTimeMillis(), onClick = onClick, onMenu = onMenu, onFocus = focus)
            is ShelfItem.Show -> ShowCard(item, onClick = onClick, onFocus = focus)
            is ShelfItem.Episode -> EpisodeCard(item.episode, onClick = onClick, onFocus = focus)
            is ShelfItem.Home -> HaTile(item.entity, onClick = onClick, onFocus = focus)
            is ShelfItem.Tool -> ToolCard(item.icon, item.title, item.subtitle, item.color, onClick = onClick, onFocus = focus)
        }
    }
}

/** Programma nella scheda Live. */
@Composable
private fun ShowCard(item: ShelfItem.Show, onClick: () -> Unit, onFocus: (Color) -> Unit) {
    val now = System.currentTimeMillis()
    val p = item.programme
    val onAir = now in p.start until p.stop
    val tint = Color(item.guide.channel.color)
    TvCard(
        onClick = onClick,
        modifier = Modifier
            .width(if (onAir) 230.dp else 180.dp)
            .aspectRatio(if (onAir) 230f / 112f else 180f / 112f),
        onFocus = { onFocus(tint) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (onAir) {
                        Brush.linearGradient(listOf(tint, tint.darken(0.6f)))
                    } else {
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f)))
                    }
                )
        )
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                if (onAir) "ORA · ${formatTime(p.start)}–${formatTime(p.stop)}" else formatTime(p.start),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (onAir) 0.95f else 0.7f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                p.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onAir) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun HintCard(hint: Hint) {
    TvCard(
        onClick = hint.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rowStart, vertical = 10.dp)
            .height(68.dp),
        shape = RoundedCornerShape(20.dp),
        focusScale = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(hint.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(hint.title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    hint.text,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
