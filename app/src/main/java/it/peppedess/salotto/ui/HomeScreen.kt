package it.peppedess.salotto.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ChannelGuide
import it.peppedess.salotto.data.ContinueItem
import it.peppedess.salotto.data.EpisodeInfo
import it.peppedess.salotto.data.HaEntity
import it.peppedess.salotto.data.Playlist
import it.peppedess.salotto.data.Profile
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.data.Weather
import it.peppedess.salotto.ui.components.AppCard
import it.peppedess.salotto.ui.components.Clock
import it.peppedess.salotto.ui.components.ContinueCard
import it.peppedess.salotto.ui.components.HaTile
import it.peppedess.salotto.ui.components.PosterCard
import it.peppedess.salotto.ui.components.ShelfRow
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.components.WeatherChip
import it.peppedess.salotto.ui.components.darken
import it.peppedess.salotto.ui.theme.Bg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal val DefaultAmbient = Color(0xFF243044)

private sealed interface HeroSlide {
    val key: String
    val color: Color
    val art: Any?

    data class Media(val item: ContinueItem, val app: AppEntry?) : HeroSlide {
        override val key: String get() = "m:" + item.id
        override val color: Color get() = app?.color ?: DefaultAmbient
        override val art: Any? get() = item.artModel
    }

    data class Featured(val title: TmdbTitle, val app: AppEntry) : HeroSlide {
        override val key: String get() = "t:" + title.key
        override val color: Color get() = app.color
        override val art: Any? get() = title.backdropUrl ?: title.posterUrl
    }

    data class App(val app: AppEntry) : HeroSlide {
        override val key: String get() = "a:" + app.key
        override val color: Color get() = app.color
        override val art: Any? get() = null
    }
}

fun TmdbTitle.metaLine(): String = listOfNotNull(
    year.takeIf { it.isNotBlank() },
    kindLabel,
    if (rating > 0) "★ " + String.format(Locale.ITALIAN, "%.1f", rating) else null,
).joinToString(" · ")

/** Dati di tutte le righe della home, raccolti in un solo oggetto. */
private data class HomeData(
    val apps: List<AppEntry>,
    val favorites: List<AppEntry>,
    val media: List<ContinueItem>,
    val rows: List<ProviderRow>,
    val trending: List<TmdbTitle>,
    val haTiles: List<HaEntity>,
    val myList: List<TmdbTitle>,
    val episodes: List<EpisodeInfo>,
    val recommendations: List<Pair<TmdbTitle, List<TmdbTitle>>>,
    val live: List<ChannelGuide>,
    val playlists: List<Playlist>,
    val speakers: List<HaEntity>,
    val byPkg: Map<String, AppEntry>,
    val access: Boolean,
    val loading: Boolean,
    val showTmdbHint: Boolean,
    val updateBuild: Int?,
    val sleepEndsAt: Long,
)

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onSearch: (voice: Boolean) -> Unit,
    onSettings: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onOpenTitle: (TmdbTitle, AppEntry?) -> Unit,
    onAssistant: () -> Unit,
    onStats: () -> Unit,
    onProfiles: () -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val media by vm.continueItems.collectAsStateWithLifecycle()
    val access by vm.notificationAccess.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val rows by vm.providerRows.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haTiles by vm.haTiles.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val myList by vm.myList.collectAsStateWithLifecycle()
    val episodes by vm.newEpisodes.collectAsStateWithLifecycle()
    val recommendations by vm.recommendations.collectAsStateWithLifecycle()
    val live by vm.liveGuide.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val speakers by vm.speakers.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val adminActive by vm.adminActive.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val kidsRemaining by vm.kidsRemaining.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ambientTarget by remember { mutableStateOf(DefaultAmbient) }
    val ambient by animateColorAsState(ambientTarget, tween(700), label = "ambient")
    var ambientArt by remember { mutableStateOf<Any?>(null) }
    var menuItem by remember { mutableStateOf<ContinueItem?>(null) }
    var channelMenu by remember { mutableStateOf<ChannelGuide?>(null) }
    var playlistMenu by remember { mutableStateOf<Playlist?>(null) }
    var showAudio by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showCastHelp by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(homeTick) {
        tab = 0
        runCatching { listState.scrollToItem(0) }
    }

    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val setAmbient: (Color) -> Unit = { ambientTarget = it }
    val setArt: (Any?) -> Unit = { if (it != null) ambientArt = it }

    val data = HomeData(
        apps = apps,
        favorites = favorites,
        media = media,
        rows = rows,
        trending = trending,
        haTiles = haTiles,
        myList = myList,
        episodes = episodes,
        recommendations = recommendations,
        live = if (settings.liveTv) live else emptyList(),
        playlists = playlists,
        speakers = speakers,
        byPkg = byPkg,
        access = access,
        loading = loading,
        showTmdbHint = settings.tmdbKey.isBlank(),
        updateBuild = update?.build,
        sleepEndsAt = sleepEndsAt,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Sfondo: immagine sfocata dell'elemento selezionato, più un alone di colore.
        Crossfade(targetState = ambientArt, animationSpec = tween(900), label = "ambientArt") { art ->
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                        .graphicsLayer { alpha = if (Build.VERSION.SDK_INT >= 31) 0.38f else 0.14f },
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(ambient.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.78f, 0f),
                            radius = size.maxDimension * 0.8f,
                        )
                    )
                    drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Bg.copy(alpha = 0.9f)))
                }
        )

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            AnimatedVisibility(
                visible = !scrolled || tab != 0 || profile.kids,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                TopBar(
                    tab = tab,
                    profile = profile,
                    weather = weather,
                    sleepEndsAt = sleepEndsAt,
                    kidsRemaining = kidsRemaining,
                    onTab = { tab = it },
                    onSearch = onSearch,
                    onSettings = onSettings,
                    onAssistant = onAssistant,
                    onProfiles = onProfiles,
                    onSleep = { showSleep = true },
                )
            }
            if (profile.kids) {
                KidsHome(profile = profile, apps = apps, onLaunch = vm::launch)
            } else {
                Crossfade(targetState = tab, label = "tab") { current ->
                    if (current == 0) {
                        ForYou(
                            listState = listState,
                            data = data,
                            onAmbient = setAmbient,
                            onArt = setArt,
                            onLaunch = vm::launch,
                            onResume = vm::resume,
                            onAppMenu = onAppMenu,
                            onMediaMenu = { menuItem = it },
                            onOpenTitle = onOpenTitle,
                            onHaPress = vm::haPress,
                            onEnableAccess = { vm.openNotificationAccess() },
                            onTmdbHint = onSettings,
                            onOpenLive = { vm.openLive(it.channel) },
                            onChannelMenu = { channelMenu = it },
                            onPlaylist = { playlistMenu = it },
                            onInstallUpdate = { vm.installUpdate() },
                            onAssistant = onAssistant,
                            onSleep = { showSleep = true },
                            onAudio = { showAudio = true },
                            onStats = onStats,
                            onCast = {
                                if (!vm.openCastReceiver()) {
                                    showCastHelp = true
                                }
                            },
                        )
                    } else {
                        AllApps(apps = apps, onAmbient = setAmbient, onLaunch = vm::launch, onAppMenu = onAppMenu)
                    }
                }
            }
        }

        val np = nowPlaying
        AnimatedVisibility(
            visible = np != null && !profile.kids,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        ) {
            if (np != null) {
                NowPlayingBar(
                    nowPlaying = np,
                    app = byPkg[np.packageName],
                    onCommand = vm::mediaCommand,
                    onOpen = vm::openNowPlayingApp,
                    onAudio = { showAudio = true },
                )
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
    channelMenu?.let { guide ->
        ChannelDialog(
            guide = guide,
            onWatch = {
                vm.openLive(guide.channel)
                channelMenu = null
            },
            onDismiss = { channelMenu = null },
        )
    }
    playlistMenu?.let { playlist ->
        PlaylistDialog(
            playlist = playlist,
            speakers = speakers,
            onPick = { speaker ->
                vm.playPlaylist(playlist, speaker)
                playlistMenu = null
            },
            onDismiss = { playlistMenu = null },
        )
    }
    if (showAudio) {
        AudioDialog(vm = vm, speakers = speakers, onDismiss = { showAudio = false })
    }
    if (showSleep) {
        SleepDialog(
            endsAt = sleepEndsAt,
            adminActive = adminActive,
            onStart = vm::startSleepTimer,
            onCancel = vm::cancelSleepTimer,
            onEnableAdmin = vm::requestDeviceAdmin,
            onDismiss = { showSleep = false },
        )
    }
    if (showCastHelp) {
        AlertDialog(
            onDismissRequest = { showCastHelp = false },
            icon = { Icon(Icons.Rounded.Cast, null) },
            title = { Text("Proietta su Salotto") },
            text = {
                Text(
                    "Non ho trovato un ricevitore installato. Ti consiglio AirScreen: riceve Google Cast, AirPlay e Miracast. " +
                        "Dopo averlo installato, questo pulsante lo apre direttamente."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.searchPlayStore("AirScreen")
                    showCastHelp = false
                }) { Text("Apri il Play Store") }
            },
            dismissButton = { TextButton(onClick = { showCastHelp = false }) { Text("Chiudi") } },
        )
    }
}

@Composable
private fun TopBar(
    tab: Int,
    profile: Profile,
    weather: Weather?,
    sleepEndsAt: Long,
    kidsRemaining: Int?,
    onTab: (Int) -> Unit,
    onSearch: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onAssistant: () -> Unit,
    onProfiles: () -> Unit,
    onSleep: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        while (sleepEndsAt > 0) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 32.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvCard(onClick = onProfiles, modifier = Modifier.size(44.dp), shape = CircleShape, focusScale = 1.12f) {
            ProfileAvatar(profile, 44)
        }
        Text(
            text = if (profile.kids) "Ciao ${profile.name}!" else "Salotto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(end = 10.dp),
        )
        if (!profile.kids) {
            TvPill(text = "Per te", onClick = { onTab(0) }, selected = tab == 0)
            TvPill(text = "App", onClick = { onTab(1) }, selected = tab == 1)
        }
        Spacer(Modifier.weight(1f))
        if (kidsRemaining != null) {
            Text(
                text = "Tempo rimasto: ${kidsRemaining / 60} min",
                style = MaterialTheme.typography.titleMedium,
                color = if (kidsRemaining < 600) Color(0xFFFFB74D) else Color.White,
            )
        }
        if (!profile.kids) {
            if (sleepEndsAt > now) {
                TvPill(
                    text = "${((sleepEndsAt - now) / 60_000L) + 1} min",
                    icon = Icons.Rounded.Bedtime,
                    onClick = onSleep,
                )
            }
            TvPill(text = "Cerca", icon = Icons.Rounded.Search, onClick = { onSearch(false) })
            TvIconButton(Icons.Rounded.Mic, "Ricerca vocale", onClick = { onSearch(true) })
            TvIconButton(Icons.Rounded.AutoAwesome, "Assistente", onClick = onAssistant)
        }
        WeatherChip(weather, Modifier.padding(start = 8.dp))
        Clock(Modifier.padding(horizontal = 8.dp))
        if (!profile.kids) {
            TvIconButton(Icons.Rounded.Settings, "Impostazioni", onClick = onSettings)
        }
    }
}

@Composable
private fun KidsHome(profile: Profile, apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    val allowed = remember(profile, apps) {
        val byPkg = apps.associateBy { it.packageName }
        profile.allowedApps.mapNotNull { byPkg[it] }
    }
    if (allowed.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nessuna app scelta per questo profilo. Chiedi a un adulto di sceglierle in Impostazioni → Profili.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 20.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(allowed, key = { it.key }) { app ->
            KidsAppCard(app) { onLaunch(app) }
        }
    }
}

@Composable
private fun ForYou(
    listState: LazyListState,
    data: HomeData,
    onAmbient: (Color) -> Unit,
    onArt: (Any?) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onResume: (ContinueItem) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onMediaMenu: (ContinueItem) -> Unit,
    onOpenTitle: (TmdbTitle, AppEntry?) -> Unit,
    onHaPress: (HaEntity) -> Unit,
    onEnableAccess: () -> Unit,
    onTmdbHint: () -> Unit,
    onOpenLive: (ChannelGuide) -> Unit,
    onChannelMenu: (ChannelGuide) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onInstallUpdate: () -> Unit,
    onAssistant: () -> Unit,
    onSleep: () -> Unit,
    onAudio: () -> Unit,
    onStats: () -> Unit,
    onCast: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val titleFocus: (TmdbTitle) -> (Color) -> Unit = { t ->
        { c ->
            onAmbient(c)
            onArt(t.backdropUrl ?: t.posterUrl)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.64f).coerceIn(180.dp, 460.dp)
        val slides = remember(data.media, data.rows, data.favorites, data.apps, data.byPkg) {
            buildSlides(data.media, data.rows, data.favorites, data.apps, data.byPkg)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            var order = 1
            item(key = "hero") {
                Hero(
                    slides = slides,
                    height = heroHeight,
                    loading = data.loading,
                    onAmbient = { slide ->
                        onAmbient(slide.color)
                        onArt(slide.art)
                    },
                    onAction = { slide ->
                        when (slide) {
                            is HeroSlide.Media -> onResume(slide.item)
                            is HeroSlide.Featured -> onOpenTitle(slide.title, slide.app)
                            is HeroSlide.App -> onLaunch(slide.app)
                        }
                    },
                )
            }
            data.updateBuild?.let { build ->
                item(key = "update") {
                    HintCard(
                        icon = Icons.Rounded.SystemUpdate,
                        title = "Aggiornamento disponibile: build $build",
                        text = "Tocca per scaricarlo e installarlo.",
                        onClick = onInstallUpdate,
                    )
                }
            }
            if (data.media.isNotEmpty()) {
                val o = order++
                item(key = "continue") {
                    ShelfRow("Continua a guardare", o) {
                        items(data.media, key = { it.id }) { entry ->
                            ContinueCard(
                                item = entry,
                                app = data.byPkg[entry.packageName],
                                onClick = { onResume(entry) },
                                onMenu = { onMediaMenu(entry) },
                                onFocus = { c ->
                                    onAmbient(c)
                                    onArt(entry.artModel)
                                },
                            )
                        }
                    }
                }
            } else if (!data.access) {
                item(key = "access") {
                    HintCard(
                        icon = Icons.Rounded.Notifications,
                        title = "Attiva \"Continua a guardare\"",
                        text = "Concedi l'accesso alle notifiche per vedere qui cosa stavi guardando.",
                        onClick = onEnableAccess,
                    )
                }
            }
            if (data.episodes.isNotEmpty()) {
                val o = order++
                item(key = "episodes") {
                    ShelfRow("Nuovi episodi", o) {
                        items(data.episodes, key = { it.series.key + it.code }) { ep ->
                            EpisodeCard(
                                episode = ep,
                                onClick = { onOpenTitle(ep.series, null) },
                                onFocus = titleFocus(ep.series),
                            )
                        }
                    }
                }
            }
            if (data.favorites.isNotEmpty()) {
                val o = order++
                item(key = "favorites") {
                    ShelfRow("Preferite", o) {
                        items(data.favorites, key = { it.key }) { app ->
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
            if (data.myList.isNotEmpty()) {
                val o = order++
                item(key = "mylist") {
                    ShelfRow("La mia lista", o) {
                        items(data.myList, key = { it.key }) { t ->
                            PosterCard(t, DefaultAmbient, onClick = { onOpenTitle(t, null) }, onFocus = titleFocus(t))
                        }
                    }
                }
            }
            if (data.live.isNotEmpty()) {
                val o = order++
                item(key = "live") {
                    ShelfRow("In onda ora", o) {
                        items(data.live, key = { it.channel.number }) { guide ->
                            LiveCard(
                                guide = guide,
                                now = now,
                                onClick = { onOpenLive(guide) },
                                onMenu = { onChannelMenu(guide) },
                                onFocus = onAmbient,
                            )
                        }
                    }
                }
            }
            if (data.haTiles.isNotEmpty()) {
                val o = order++
                item(key = "home") {
                    ShelfRow("Casa", o) {
                        items(data.haTiles, key = { it.id }) { entity ->
                            HaTile(entity = entity, onClick = { onHaPress(entity) }, onFocus = onAmbient)
                        }
                    }
                }
            }
            data.rows.forEach { row ->
                val o = order++
                item(key = "row:" + row.app.packageName) {
                    ShelfRow("Popolari su ${row.app.label}", o, icon = row.app.icon) {
                        items(row.titles, key = { it.key }) { t ->
                            PosterCard(t, row.app.color, onClick = { onOpenTitle(t, row.app) }, onFocus = titleFocus(t))
                        }
                    }
                }
            }
            data.recommendations.forEach { (seed, titles) ->
                val o = order++
                item(key = "rec:" + seed.key) {
                    ShelfRow("Perché ti interessa «${seed.title}»", o) {
                        items(titles, key = { it.key }) { t ->
                            PosterCard(t, DefaultAmbient, onClick = { onOpenTitle(t, null) }, onFocus = titleFocus(t))
                        }
                    }
                }
            }
            if (data.speakers.isNotEmpty() || data.playlists.isNotEmpty()) {
                val o = order++
                item(key = "music") {
                    ShelfRow("Musica", o) {
                        items(data.speakers, key = { "spk:" + it.id }) { speaker ->
                            HaTile(entity = speaker, onClick = { onHaPress(speaker) }, onFocus = onAmbient)
                        }
                        items(data.playlists, key = { "pl:" + it.id }) { playlist ->
                            PlaylistCard(playlist = playlist, onClick = { onPlaylist(playlist) }, onFocus = onAmbient)
                        }
                    }
                }
            }
            if (data.trending.isNotEmpty()) {
                val o = order++
                item(key = "trending") {
                    ShelfRow("Di tendenza questa settimana", o) {
                        items(data.trending, key = { it.key }) { t ->
                            PosterCard(t, DefaultAmbient, onClick = { onOpenTitle(t, null) }, onFocus = titleFocus(t))
                        }
                    }
                }
            }
            if (data.showTmdbHint) {
                item(key = "tmdb") {
                    HintCard(
                        icon = Icons.Rounded.Movie,
                        title = "Aggiungi film e serie",
                        text = "Inserisci una chiave TMDB gratuita nelle impostazioni per locandine e righe dei tuoi servizi di streaming.",
                        onClick = onTmdbHint,
                    )
                }
            }
            run {
                val o = order++
                item(key = "tools") {
                    ShelfRow("Strumenti", o) {
                        item(key = "t-ai") {
                            ToolCard(Icons.Rounded.AutoAwesome, "Assistente", "Chiedi cosa guardare", Color(0xFF7C4DFF), onAssistant, onAmbient)
                        }
                        item(key = "t-sleep") {
                            ToolCard(
                                Icons.Rounded.Bedtime,
                                "Timer",
                                if (data.sleepEndsAt > now) "Attivo" else "Spegnimento",
                                Color(0xFF3949AB),
                                onSleep,
                                onAmbient,
                            )
                        }
                        item(key = "t-audio") {
                            ToolCard(Icons.Rounded.Tune, "Audio", "Volume e uscita", Color(0xFF00897B), onAudio, onAmbient)
                        }
                        item(key = "t-cast") {
                            ToolCard(Icons.Rounded.Cast, "Proietta", "Ricevitore Cast", Color(0xFFEF6C00), onCast, onAmbient)
                        }
                        item(key = "t-stats") {
                            ToolCard(Icons.Rounded.BarChart, "Statistiche", "Ore di visione", Color(0xFF546E7A), onStats, onAmbient)
                        }
                    }
                }
            }
            if (data.apps.isNotEmpty()) {
                val o = order++
                item(key = "apps") {
                    ShelfRow("Le tue app", o) {
                        items(data.apps, key = { it.key }) { app ->
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
    rows: List<ProviderRow>,
    favorites: List<AppEntry>,
    apps: List<AppEntry>,
    byPkg: Map<String, AppEntry>,
): List<HeroSlide> {
    val out = mutableListOf<HeroSlide>()
    media.take(2).forEach { out += HeroSlide.Media(it, byPkg[it.packageName]) }

    // In evidenza: i titoli più popolari dei tuoi servizi, alternati.
    val withBackdrop = rows.take(3).map { row -> row.titles.filter { it.backdropUrl != null } to row.app }
    for (i in 0 until 2) {
        for ((titles, app) in withBackdrop) {
            if (out.size >= 6) break
            titles.getOrNull(i)?.let { out += HeroSlide.Featured(it, app) }
        }
    }

    if (out.size < 3) {
        val pool = favorites.ifEmpty { apps.filter { it.banner != null }.take(5) }
        pool
            .filter { app -> media.take(2).none { it.packageName == app.packageName } }
            .take((5 - out.size).coerceAtLeast(0))
            .forEach { out += HeroSlide.App(it) }
    }
    return out
}

@Composable
private fun Hero(
    slides: List<HeroSlide>,
    height: Dp,
    loading: Boolean,
    onAmbient: (HeroSlide) -> Unit,
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
            delay(9000)
            val n = current.value.size
            if (!focusedInside && n > 1 && !pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.currentPage + 1) % n)
            }
        }
    }
    LaunchedEffect(pager.currentPage, slides) {
        slides.getOrNull(pager.currentPage)?.let { onAmbient(it) }
    }

    TvCard(
        onClick = { current.value.getOrNull(pager.currentPage)?.let(onAction) },
        modifier = outer.onFocusChanged { focusedInside = it.hasFocus },
        shape = RoundedCornerShape(28.dp),
        focusScale = 1.015f,
        onFocus = { current.value.getOrNull(pager.currentPage)?.let(onAmbient) },
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
            if (slide != null) HeroPage(slide, focused, isCurrent = page == pager.currentPage)
        }
        Dots(
            count = slides.size,
            current = pager.currentPage,
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
        )
    }
}

@Composable
private fun HeroPage(slide: HeroSlide, focused: Boolean, isCurrent: Boolean) {
    val color = slide.color
    val model: Any? = slide.art
    val banner: ImageBitmap?
    val icon: ImageBitmap?
    val label: String
    val title: String
    val subtitle: String
    val action: String
    var progress = -1f

    when (slide) {
        is HeroSlide.Media -> {
            banner = if (model == null) slide.app?.banner else null
            icon = slide.app?.icon
            label = if (slide.item.playing) "In riproduzione" else "Continua a guardare"
            title = slide.item.title
            subtitle = listOfNotNull(slide.app?.label, slide.item.subtitle.takeIf { it.isNotBlank() }).joinToString(" · ")
            action = "Riprendi"
            if (slide.item.durationMs > 0) progress = slide.item.progress
        }
        is HeroSlide.Featured -> {
            banner = null
            icon = slide.app.icon
            label = "In evidenza su ${slide.app.label}"
            title = slide.title.title
            subtitle = slide.title.metaLine()
            action = "Dettagli"
        }
        is HeroSlide.App -> {
            banner = null
            icon = slide.app.icon
            label = "In evidenza"
            title = slide.app.label
            subtitle = "Apri l'app"
            action = "Apri"
        }
    }

    // Zoom lento sull'immagine della pagina visibile (effetto Ken Burns).
    val zoom by animateFloatAsState(
        targetValue = if (isCurrent) 1.1f else 1f,
        animationSpec = if (isCurrent) tween(12_000, easing = LinearEasing) else tween(400),
        label = "zoom",
    )
    val dark = color.darken(0.7f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(color.darken(0.2f), Bg)))
    ) {
        val artModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.7f, 0.4f)
            }
        when {
            model != null -> AsyncImage(
                model = model,
                contentDescription = null,
                modifier = artModifier,
                contentScale = ContentScale.Crop,
            )
            banner != null -> Image(banner, null, artModifier, contentScale = ContentScale.Crop)
            icon != null -> Image(
                icon,
                null,
                Modifier.align(Alignment.CenterEnd).padding(end = 80.dp).size(130.dp),
            )
        }
        // Sfumatura continua da sinistra (testo) verso l'immagine, senza bordi netti.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to dark.copy(alpha = 0.97f),
                        0.3f to dark.copy(alpha = 0.85f),
                        0.55f to dark.copy(alpha = 0.35f),
                        0.8f to Color.Transparent,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))
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
                maxLines = 1,
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
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress >= 0f) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
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
                val actionIcon = when (slide) {
                    is HeroSlide.Media -> Icons.Rounded.PlayArrow
                    is HeroSlide.Featured -> Icons.Rounded.Info
                    is HeroSlide.App -> Icons.Rounded.Apps
                }
                Icon(actionIcon, null, tint = fg, modifier = Modifier.size(20.dp))
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
private fun HintCard(
    icon: ImageVector,
    title: String,
    text: String,
    onClick: () -> Unit,
) {
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
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    text,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
