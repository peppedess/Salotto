package it.peppedess.salotto.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ContinueItem
import it.peppedess.salotto.ui.components.AppCard
import it.peppedess.salotto.ui.components.Clock
import it.peppedess.salotto.ui.components.ContinueCard
import it.peppedess.salotto.ui.components.ShelfRow
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.components.darken
import it.peppedess.salotto.ui.components.rememberFileBitmap
import it.peppedess.salotto.ui.theme.Bg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DefaultAmbient = Color(0xFF243044)

private sealed interface HeroSlide {
    val key: String
    val color: Color

    data class Media(val item: ContinueItem, val app: AppEntry?) : HeroSlide {
        override val key: String get() = "m:" + item.id
        override val color: Color get() = app?.color ?: DefaultAmbient
    }

    data class App(val app: AppEntry) : HeroSlide {
        override val key: String get() = "a:" + app.key
        override val color: Color get() = app.color
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onSearch: (voice: Boolean) -> Unit,
    onSettings: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val media by vm.continueItems.collectAsStateWithLifecycle()
    val access by vm.notificationAccess.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ambientTarget by remember { mutableStateOf(DefaultAmbient) }
    val ambient by animateColorAsState(ambientTarget, tween(700), label = "ambient")
    var menuItem by remember { mutableStateOf<ContinueItem?>(null) }

    LaunchedEffect(homeTick) { tab = 0 }

    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val setAmbient: (Color) -> Unit = { ambientTarget = it }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(ambient.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(size.width * 0.78f, 0f),
                        radius = size.maxDimension * 0.8f,
                    )
                )
            }
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            TopBar(tab = tab, onTab = { tab = it }, onSearch = onSearch, onSettings = onSettings)
            Crossfade(targetState = tab, label = "tab") { current ->
                if (current == 0) {
                    ForYou(
                        apps = apps,
                        favorites = favorites,
                        media = media,
                        byPkg = byPkg,
                        access = access,
                        loading = loading,
                        onAmbient = setAmbient,
                        onLaunch = vm::launch,
                        onResume = vm::resume,
                        onAppMenu = onAppMenu,
                        onMediaMenu = { menuItem = it },
                        onEnableAccess = { vm.openNotificationAccess() },
                    )
                } else {
                    AllApps(apps = apps, onAmbient = setAmbient, onLaunch = vm::launch, onAppMenu = onAppMenu)
                }
            }
        }
    }

    menuItem?.let { item ->
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = { Text(item.title) },
            text = { if (item.subtitle.isNotBlank()) Text(item.subtitle) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resume(item)
                    menuItem = null
                }) { Text("Riprendi") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.removeContinue(item)
                    menuItem = null
                }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rimuovi")
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    tab: Int,
    onTab: (Int) -> Unit,
    onSearch: (Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 32.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Salotto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(end = 10.dp),
        )
        TvPill(text = "Per te", onClick = { onTab(0) }, selected = tab == 0)
        TvPill(text = "App", onClick = { onTab(1) }, selected = tab == 1)
        Spacer(Modifier.weight(1f))
        TvPill(text = "Cerca", icon = Icons.Rounded.Search, onClick = { onSearch(false) })
        TvIconButton(Icons.Rounded.Mic, "Ricerca vocale", onClick = { onSearch(true) })
        Clock(Modifier.padding(horizontal = 8.dp))
        TvIconButton(Icons.Rounded.Settings, "Impostazioni", onClick = onSettings)
    }
}

@Composable
private fun ForYou(
    apps: List<AppEntry>,
    favorites: List<AppEntry>,
    media: List<ContinueItem>,
    byPkg: Map<String, AppEntry>,
    access: Boolean,
    loading: Boolean,
    onAmbient: (Color) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onResume: (ContinueItem) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onMediaMenu: (ContinueItem) -> Unit,
    onEnableAccess: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.62f).coerceIn(170.dp, 440.dp)
        val slides = remember(media, favorites, apps, byPkg) { buildSlides(media, favorites, apps, byPkg) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "hero") {
                Hero(
                    slides = slides,
                    height = heroHeight,
                    loading = loading,
                    onAmbient = onAmbient,
                    onAction = { slide ->
                        when (slide) {
                            is HeroSlide.Media -> onResume(slide.item)
                            is HeroSlide.App -> onLaunch(slide.app)
                        }
                    },
                )
            }
            if (media.isNotEmpty()) {
                item(key = "continue") {
                    ShelfRow("Continua a guardare", 1) {
                        items(media, key = { it.id }) { entry ->
                            ContinueCard(
                                item = entry,
                                app = byPkg[entry.packageName],
                                onClick = { onResume(entry) },
                                onMenu = { onMediaMenu(entry) },
                                onFocus = onAmbient,
                            )
                        }
                    }
                }
            } else if (!access) {
                item(key = "access") { AccessHint(onEnableAccess) }
            }
            if (favorites.isNotEmpty()) {
                item(key = "favorites") {
                    ShelfRow("Preferite", 2) {
                        items(favorites, key = { it.key }) { app ->
                            AppCard(
                                app = app,
                                onClick = { onLaunch(app) },
                                onMenu = { onAppMenu(app) },
                                onFocus = onAmbient,
                                modifier = Modifier.width(170.dp),
                            )
                        }
                    }
                }
            }
            if (apps.isNotEmpty()) {
                item(key = "apps") {
                    ShelfRow("Le tue app", 3) {
                        items(apps, key = { it.key }) { app ->
                            AppCard(
                                app = app,
                                onClick = { onLaunch(app) },
                                onMenu = { onAppMenu(app) },
                                onFocus = onAmbient,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildSlides(
    media: List<ContinueItem>,
    favorites: List<AppEntry>,
    apps: List<AppEntry>,
    byPkg: Map<String, AppEntry>,
): List<HeroSlide> {
    val out = mutableListOf<HeroSlide>()
    media.take(3).forEach { out += HeroSlide.Media(it, byPkg[it.packageName]) }
    val pool = favorites.ifEmpty { apps.filter { it.banner != null }.take(5) }
    pool
        .filter { app -> media.take(3).none { it.packageName == app.packageName } }
        .take((5 - out.size).coerceAtLeast(0))
        .forEach { out += HeroSlide.App(it) }
    return out
}

@Composable
private fun Hero(
    slides: List<HeroSlide>,
    height: Dp,
    loading: Boolean,
    onAmbient: (Color) -> Unit,
    onAction: (HeroSlide) -> Unit,
) {
    val outer = Modifier
        .fillMaxWidth()
        .height(height)
        .padding(horizontal = 40.dp, vertical = 8.dp)

    if (slides.isEmpty()) {
        Box(
            outer
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(DefaultAmbient, Bg))),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Benvenuto in Salotto", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (loading) "Sto caricando le tue app…" else "Aggiungi app alle preferite con una pressione lunga.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
        return
    }

    val current = rememberUpdatedState(slides)
    val pager = rememberPagerState { current.value.size }
    val scope = rememberCoroutineScope()
    var focusedInside by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        while (true) {
            delay(8000)
            val n = current.value.size
            if (!focusedInside && n > 1 && !pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.currentPage + 1) % n)
            }
        }
    }
    LaunchedEffect(pager.currentPage, slides) {
        slides.getOrNull(pager.currentPage)?.let { onAmbient(it.color) }
    }

    TvCard(
        onClick = { current.value.getOrNull(pager.currentPage)?.let(onAction) },
        modifier = outer.onFocusChanged { focusedInside = it.hasFocus },
        shape = RoundedCornerShape(28.dp),
        focusScale = 1.015f,
        onFocus = { current.value.getOrNull(pager.currentPage)?.let { onAmbient(it.color) } },
        onHorizontal = { dir ->
            val target = pager.currentPage + dir
            if (target in 0 until current.value.size) {
                scope.launch { pager.animateScrollToPage(target) }
                true
            } else {
                false
            }
        },
    ) { focused ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { index -> current.value.getOrNull(index)?.key ?: index },
        ) { page ->
            val slide = current.value.getOrNull(page)
            if (slide != null) HeroPage(slide, focused)
        }
        Dots(
            count = slides.size,
            current = pager.currentPage,
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
        )
    }
}

@Composable
private fun HeroPage(slide: HeroSlide, focused: Boolean) {
    val media = slide as? HeroSlide.Media
    val appSlide = slide as? HeroSlide.App
    val fileArt = rememberFileBitmap(media?.item?.artPath, media?.item?.updatedAt ?: 0L)
    val art: ImageBitmap? = fileArt ?: media?.app?.banner ?: appSlide?.app?.banner
    val icon: ImageBitmap? = media?.app?.icon ?: appSlide?.app?.icon
    val color = slide.color

    val label = when {
        media != null && media.item.playing -> "In riproduzione"
        media != null -> "Continua a guardare"
        else -> "In evidenza"
    }
    val title = media?.item?.title ?: appSlide?.app?.label.orEmpty()
    val subtitle = if (media != null) {
        listOfNotNull(media.app?.label, media.item.subtitle.takeIf { it.isNotBlank() }).joinToString(" · ")
    } else {
        "Apri l'app"
    }
    val action = if (media != null) "Riprendi" else "Apri"

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(color, Bg)))
    ) {
        if (art != null) {
            Image(
                art,
                null,
                Modifier.fillMaxHeight().fillMaxWidth(0.7f).align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
            )
        } else if (icon != null) {
            Image(icon, null, Modifier.align(Alignment.CenterEnd).padding(end = 72.dp).size(120.dp))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to color.darken(0.55f),
                        0.42f to color.darken(0.55f).copy(alpha = 0.85f),
                        0.75f to Color.Transparent,
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.55f)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (media != null && media.item.durationMs > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { media.item.progress },
                    modifier = Modifier.width(180.dp).height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (focused) Color.White else MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (focused) Color.Black else MaterialTheme.colorScheme.onPrimary
                Icon(
                    if (media != null) Icons.Rounded.PlayArrow else Icons.Rounded.Apps,
                    null,
                    tint = fg,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(action, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 20.dp else 6.dp, label = "dot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.4f))
            )
        }
    }
}

@Composable
private fun AccessHint(onClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 12.dp)
            .height(72.dp),
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
            Icon(Icons.Rounded.Notifications, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Attiva \"Continua a guardare\"", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Concedi l'accesso alle notifiche per vedere qui cosa stavi guardando.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AllApps(
    apps: List<AppEntry>,
    onAmbient: (Color) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(apps, key = { it.key }) { app ->
            AppCard(
                app = app,
                onClick = { onLaunch(app) },
                onMenu = { onAppMenu(app) },
                onFocus = onAmbient,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
