package it.peppedess.salotto.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ChannelGuide
import it.peppedess.salotto.data.ContinueItem
import it.peppedess.salotto.data.EpisodeInfo
import it.peppedess.salotto.data.HaEntity
import it.peppedess.salotto.data.Profile
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.data.Weather
import it.peppedess.salotto.ui.components.AppCard
import it.peppedess.salotto.ui.components.Clock
import it.peppedess.salotto.ui.components.ROW_START
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.components.WeatherChip
import it.peppedess.salotto.ui.theme.Bg
import kotlinx.coroutines.delay
import java.util.Locale

internal val DefaultAmbient = Color(0xFF243044)
private val TOP_BAR_HEIGHT = 64.dp

private val TABS = listOf("Per te", "Film", "Serie TV", "Live", "App")
private const val TAB_APPS = 4

fun TmdbTitle.metaLine(): String = listOfNotNull(
    year.takeIf { it.isNotBlank() },
    kindLabel,
    genres.take(2).joinToString(", ").takeIf { it.isNotBlank() },
    if (rating > 0) "★ " + String.format(Locale.ITALIAN, "%.1f", rating) else null,
).joinToString(" · ")

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onSearch: (voice: Boolean) -> Unit,
    onSettings: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onOpenTitle: (TmdbTitle, AppEntry?, String?) -> Unit,
    onStats: () -> Unit,
    onProfiles: () -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val listed by vm.listedApps.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val media by vm.continueItems.collectAsStateWithLifecycle()
    val access by vm.notificationAccess.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val navKey by vm.navKey.collectAsStateWithLifecycle()
    val providerRows by vm.providerRows.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haTiles by vm.haTiles.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val myList by vm.myList.collectAsStateWithLifecycle()
    val episodes by vm.newEpisodes.collectAsStateWithLifecycle()
    val recommendations by vm.recommendations.collectAsStateWithLifecycle()
    val live by vm.liveGuide.collectAsStateWithLifecycle()
    val movieRows by vm.movieRows.collectAsStateWithLifecycle()
    val tvRows by vm.tvRows.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val adminActive by vm.adminActive.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val kidsRemaining by vm.kidsRemaining.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collapsed by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var resetTick by remember { mutableIntStateOf(0) }
    var anyFocus by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<ContinueItem?>(null) }
    var channelMenu by remember { mutableStateOf<ChannelGuide?>(null) }
    var showAudio by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showCastHelp by remember { mutableStateOf(false) }

    val listStates = remember { List(4) { LazyListState() } }
    val firstFocus = remember { FocusRequester() }
    val topFocus = remember { FocusRequester() }

    // Il tasto Home riporta su "Per te", in cima.
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            tab = 0
            showPanel = false
            resetTick++
        }
    }
    LaunchedEffect(tab) {
        collapsed = false
        when (tab) {
            1 -> vm.loadBrowse("movie")
            2 -> vm.loadBrowse("tv")
        }
    }
    // Primo tasto del telecomando senza nulla selezionato: seleziona il primo elemento.
    LaunchedEffect(navKey) {
        if (navKey > 0 && !anyFocus && !showPanel) {
            val ok = runCatching { firstFocus.requestFocus() }.isSuccess
            if (!ok) runCatching { topFocus.requestFocus() }
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val liveGuide = if (settings.liveTv) live else emptyList()
    val tmdbMissing = settings.tmdbKey.isBlank()

    val forYou = remember(
        listed, favorites, media, providerRows, trending, haTiles, myList, episodes,
        recommendations, liveGuide, byPkg, access, tmdbMissing, update, sleepEndsAt, now,
    ) {
        buildForYou(
            apps = listed,
            favorites = favorites,
            media = media,
            providerRows = providerRows,
            trending = trending,
            haTiles = haTiles,
            myList = myList,
            episodes = episodes,
            recommendations = recommendations,
            live = liveGuide,
            byPkg = byPkg,
            access = access,
            tmdbMissing = tmdbMissing,
            updateBuild = update?.build,
            sleepActive = sleepEndsAt > now,
            onUpdate = vm::installUpdate,
            onAccess = vm::openNotificationAccess,
            onTmdb = onSettings,
        )
    }
    val moviesTab = remember(movieRows, tmdbMissing) { buildCatalog("movie", movieRows, emptyList(), tmdbMissing, onSettings) }
    val seriesTab = remember(tvRows, episodes, tmdbMissing) { buildCatalog("tv", tvRows, episodes, tmdbMissing, onSettings) }
    val liveTab = remember(liveGuide, now, settings.liveTv) { buildLive(liveGuide, now, settings.liveTv, onSettings) }

    val onItem: (ShelfItem) -> Unit = { item ->
        when (item) {
            is ShelfItem.Poster -> {
                onOpenTitle(item.title, item.app, item.shareKey)
            }
            is ShelfItem.Continue -> {
                vm.resume(item.item)
            }
            is ShelfItem.App -> {
                vm.launch(item.app)
            }
            is ShelfItem.Live -> {
                vm.openLive(item.guide.channel)
            }
            is ShelfItem.Show -> {
                vm.openLive(item.guide.channel)
            }
            is ShelfItem.Episode -> {
                onOpenTitle(item.episode.series, null, null)
            }
            is ShelfItem.Home -> {
                vm.haPress(item.entity)
            }
            is ShelfItem.Tool -> {
                when (item.id) {
                    "sleep" -> {
                        showSleep = true
                    }
                    "audio" -> {
                        showAudio = true
                    }
                    "stats" -> {
                        onStats()
                    }
                    else -> {
                        if (!vm.openCastReceiver()) {
                            showCastHelp = true
                        }
                    }
                }
            }
        }
        Unit
    }
    val onItemMenu: (ShelfItem) -> Unit = { item ->
        when (item) {
            is ShelfItem.Continue -> {
                menuItem = item.item
            }
            is ShelfItem.App -> {
                onAppMenu(item.app)
            }
            is ShelfItem.Live -> {
                channelMenu = item.guide
            }
            is ShelfItem.Show -> {
                channelMenu = item.guide
            }
            else -> {
                onItem(item)
            }
        }
        Unit
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .onFocusChanged { anyFocus = it.hasFocus }
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            if (profile.kids) {
                KidsHome(profile = profile, apps = apps, firstFocus = firstFocus, onLaunch = vm::launch)
            } else {
                Crossfade(targetState = tab, animationSpec = tween(260), label = "tab") { current ->
                    val content = when (current) {
                        0 -> forYou
                        1 -> moviesTab
                        2 -> seriesTab
                        3 -> liveTab
                        else -> null
                    }
                    if (content == null) {
                        AllApps(apps = listed, firstFocus = firstFocus, onLaunch = vm::launch, onAppMenu = onAppMenu)
                    } else {
                        BrowseScreen(
                            entries = content.first,
                            featured = content.second,
                            listState = listStates[current],
                            topBarHeight = TOP_BAR_HEIGHT,
                            firstItemFocus = firstFocus,
                            onItem = onItem,
                            onItemMenu = onItemMenu,
                            onCollapsedChange = { collapsed = it },
                            resetTick = resetTick,
                        )
                    }
                }
            }

            // Barra in alto, sovrapposta: quando si scorre le righe scivola via.
            val hidden = collapsed && tab != TAB_APPS && !profile.kids
            val shift by animateFloatAsState(if (hidden) 1f else 0f, tween(240), label = "bar")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT)
                    .graphicsLayer {
                        translationY = -size.height * shift
                        alpha = 1f - shift
                    }
                    .background(Brush.verticalGradient(0f to Bg.copy(alpha = 0.8f), 1f to Color.Transparent)),
                contentAlignment = Alignment.CenterStart,
            ) {
                TopBar(
                    tab = tab,
                    profile = profile,
                    weather = weather,
                    sleepEndsAt = sleepEndsAt,
                    now = now,
                    kidsRemaining = kidsRemaining,
                    firstTabFocus = topFocus,
                    onTab = { tab = it },
                    onSearch = onSearch,
                    onPanel = { showPanel = true },
                    onProfiles = onProfiles,
                    onSleep = { showSleep = true },
                )
            }
        }

        QuickPanel(
            visible = showPanel,
            vm = vm,
            weather = weather,
            profile = profile,
            sleepEndsAt = sleepEndsAt,
            kiosk = settings.kiosk,
            onDismiss = { showPanel = false },
            onSettings = {
                showPanel = false
                onSettings()
            },
            onProfiles = {
                showPanel = false
                onProfiles()
            },
            onSleep = {
                showPanel = false
                showSleep = true
            },
        )
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
    if (showAudio) {
        AudioDialog(vm = vm, onDismiss = { showAudio = false })
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

// ---------------------------------------------------------------------------
// Contenuto delle schede
// ---------------------------------------------------------------------------

private fun buildForYou(
    apps: List<AppEntry>,
    favorites: List<AppEntry>,
    media: List<ContinueItem>,
    providerRows: List<ProviderRow>,
    trending: List<TmdbTitle>,
    haTiles: List<HaEntity>,
    myList: List<TmdbTitle>,
    episodes: List<EpisodeInfo>,
    recommendations: List<Pair<TmdbTitle, List<TmdbTitle>>>,
    live: List<ChannelGuide>,
    byPkg: Map<String, AppEntry>,
    access: Boolean,
    tmdbMissing: Boolean,
    updateBuild: Int?,
    sleepActive: Boolean,
    onUpdate: () -> Unit,
    onAccess: () -> Unit,
    onTmdb: () -> Unit,
): Pair<List<BrowseEntry>, List<ShelfItem>> {
    val entries = mutableListOf<BrowseEntry>()
    if (updateBuild != null) {
        entries += Hint("update", Icons.Rounded.SystemUpdate, "Aggiornamento disponibile: build $updateBuild", "Tocca per scaricarlo e installarlo.", onUpdate)
    }
    val continueItems = media.map { ShelfItem.Continue(it, byPkg[it.packageName]) }
    if (continueItems.isNotEmpty()) {
        entries += Shelf("continue", "Continua a guardare", continueItems)
    } else if (!access) {
        entries += Hint("access", Icons.Rounded.Notifications, "Attiva \"Continua a guardare\"", "Concedi l'accesso alle notifiche per vedere qui cosa stavi guardando.", onAccess)
    }
    if (favorites.isNotEmpty()) entries += Shelf("favorites", "Le tue app preferite", favorites.map { ShelfItem.App(it) })
    if (episodes.isNotEmpty()) entries += Shelf("episodes", "Nuovi episodi", episodes.map { ShelfItem.Episode(it) })
    if (myList.isNotEmpty()) {
        entries += Shelf("mylist", "La mia lista", myList.map { ShelfItem.Poster(it, DefaultAmbient, null, "mylist/${it.key}") })
    }
    if (live.isNotEmpty()) entries += Shelf("live", "In onda ora", live.map { ShelfItem.Live(it) })
    if (haTiles.isNotEmpty()) entries += Shelf("home", "Casa", haTiles.map { ShelfItem.Home(it) })
    providerRows.forEach { row ->
        entries += Shelf(
            key = "row:" + row.app.packageName,
            title = "Popolari su ${row.app.label}",
            items = row.titles.map { ShelfItem.Poster(it, row.app.color, row.app, "row:${row.app.packageName}/${it.key}") },
            icon = row.app.icon,
        )
    }
    recommendations.forEach { (seed, titles) ->
        entries += Shelf(
            key = "rec:" + seed.key,
            title = "Perché ti interessa «${seed.title}»",
            items = titles.map { ShelfItem.Poster(it, DefaultAmbient, null, "rec:${seed.key}/${it.key}") },
        )
    }
    if (trending.isNotEmpty()) {
        entries += Shelf("trending", "Di tendenza questa settimana", trending.map { ShelfItem.Poster(it, DefaultAmbient, null, "trend/${it.key}") })
    }
    if (tmdbMissing) {
        entries += Hint("tmdb", Icons.Rounded.Movie, "Aggiungi film e serie", "Inserisci una chiave TMDB gratuita nelle impostazioni per locandine e righe dei tuoi servizi di streaming.", onTmdb)
    }
    entries += Shelf(
        "tools",
        "Strumenti",
        listOf(
            ShelfItem.Tool("sleep", Icons.Rounded.Bedtime, "Timer", if (sleepActive) "Attivo" else "Spegnimento", Color(0xFF3949AB)),
            ShelfItem.Tool("audio", Icons.Rounded.Tune, "Audio", "Volume e uscita", Color(0xFF00897B)),
            ShelfItem.Tool("cast", Icons.Rounded.Cast, "Proietta", "Ricevitore Cast", Color(0xFFEF6C00)),
            ShelfItem.Tool("stats", Icons.Rounded.BarChart, "Statistiche", "Ore di visione", Color(0xFF546E7A)),
        ),
    )
    if (apps.isNotEmpty()) entries += Shelf("apps", "Le tue app", apps.map { ShelfItem.App(it) })

    // In evidenza quando non è selezionato nulla: cosa stavi guardando e i titoli dei tuoi servizi.
    val featured = mutableListOf<ShelfItem>()
    featured += continueItems.take(2)
    val withBackdrop = providerRows.take(3).map { row -> row.titles.filter { it.backdropUrl != null } to row.app }
    for (i in 0 until 2) {
        withBackdrop.forEach { (titles, app) ->
            if (featured.size < 6) {
                titles.getOrNull(i)?.let { featured += ShelfItem.Poster(it, app.color, app, "feat/${it.key}") }
            }
        }
    }
    if (featured.size < 3) {
        featured += trending.filter { it.backdropUrl != null }.take(4 - featured.size).map { ShelfItem.Poster(it, DefaultAmbient, null, "feat/${it.key}") }
    }
    if (featured.isEmpty()) featured += favorites.take(4).map { ShelfItem.App(it) }
    return entries to featured
}

private fun buildCatalog(
    type: String,
    rows: List<CatalogRow>,
    episodes: List<EpisodeInfo>,
    tmdbMissing: Boolean,
    onTmdb: () -> Unit,
): Pair<List<BrowseEntry>, List<ShelfItem>> {
    val entries = mutableListOf<BrowseEntry>()
    if (tmdbMissing) {
        entries += Hint("tmdb", Icons.Rounded.Movie, "Serve la chiave TMDB", "Inseriscila nelle impostazioni per vedere film e serie dei tuoi servizi.", onTmdb)
        return entries to emptyList()
    }
    if (type == "tv" && episodes.isNotEmpty()) {
        entries += Shelf("episodes", "Nuovi episodi", episodes.map { ShelfItem.Episode(it) })
    }
    rows.forEach { row ->
        entries += Shelf(
            key = "$type:${row.key}",
            title = row.title,
            items = row.titles.map { ShelfItem.Poster(it, row.app?.color ?: DefaultAmbient, row.app, "$type:${row.key}/${it.key}") },
            icon = row.app?.icon,
        )
    }
    if (rows.isEmpty()) {
        entries += Hint("loading", Icons.Rounded.HourglassTop, if (type == "tv") "Carico le serie…" else "Carico i film…", "Un attimo: cerco i titoli disponibili sui tuoi servizi.", {})
    }
    val featured = rows.firstOrNull()?.titles.orEmpty()
        .filter { it.backdropUrl != null }
        .take(6)
        .map { ShelfItem.Poster(it, DefaultAmbient, null, "feat/${it.key}") }
    return entries to featured
}

private fun buildLive(
    guides: List<ChannelGuide>,
    now: Long,
    enabled: Boolean,
    onSettings: () -> Unit,
): Pair<List<BrowseEntry>, List<ShelfItem>> {
    if (!enabled) {
        return listOf<BrowseEntry>(
            Hint("live-off", Icons.Rounded.LiveTv, "Dirette TV disattivate", "Attivale in Impostazioni → TV e film.", onSettings)
        ) to emptyList()
    }
    val entries = guides.map { guide ->
        val shows = guide.programmes.filter { it.stop > now }.take(10)
        Shelf(
            key = "ch:" + guide.channel.number,
            title = "${guide.channel.number} · ${guide.channel.name}",
            items = if (shows.isEmpty()) listOf(ShelfItem.Live(guide)) else shows.map { ShelfItem.Show(guide, it) },
        )
    }
    val featured = guides.take(6).map { ShelfItem.Live(it) }
    return entries to featured
}

// ---------------------------------------------------------------------------
// Barra in alto
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    tab: Int,
    profile: Profile,
    weather: Weather?,
    sleepEndsAt: Long,
    now: Long,
    kidsRemaining: Int?,
    firstTabFocus: FocusRequester,
    onTab: (Int) -> Unit,
    onSearch: (Boolean) -> Unit,
    onPanel: () -> Unit,
    onProfiles: () -> Unit,
    onSleep: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ROW_START - 4.dp, end = 28.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvCard(onClick = onProfiles, modifier = Modifier.size(40.dp), shape = CircleShape, focusScale = 1.12f) {
            ProfileAvatar(profile, 40)
        }
        Spacer(Modifier.width(6.dp))
        if (profile.kids) {
            Text(
                text = "Ciao ${profile.name}!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        } else {
            TABS.forEachIndexed { index, label ->
                TabLabel(
                    label = label,
                    selected = tab == index,
                    onClick = { onTab(index) },
                    modifier = if (index == 0) Modifier.focusRequester(firstTabFocus) else Modifier,
                )
            }
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
                TvPill(text = "${((sleepEndsAt - now) / 60_000L) + 1} min", icon = Icons.Rounded.Bedtime, onClick = onSleep)
            }
            TvIconButton(Icons.Rounded.Search, "Cerca", onClick = { onSearch(false) })
            TvIconButton(Icons.Rounded.Mic, "Ricerca vocale", onClick = { onSearch(true) })
        }
        WeatherChip(weather, Modifier.padding(start = 6.dp))
        Clock(Modifier.padding(horizontal = 6.dp))
        if (!profile.kids) {
            TvIconButton(Icons.Rounded.Settings, "Pannello rapido", onClick = onPanel)
        }
    }
}

/** Scheda della barra: testo semplice, con una pillola chiara quando è selezionata. */
@Composable
private fun TabLabel(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvCard(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = CircleShape,
        focusScale = 1.06f,
    ) { focused ->
        Box(
            Modifier
                .background(
                    when {
                        focused -> Color.White
                        selected -> Color.White.copy(alpha = 0.18f)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    focused -> Color.Black
                    selected -> Color.White
                    else -> Color.White.copy(alpha = 0.7f)
                },
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bambini e griglia delle app
// ---------------------------------------------------------------------------

@Composable
private fun KidsHome(profile: Profile, apps: List<AppEntry>, firstFocus: FocusRequester, onLaunch: (AppEntry) -> Unit) {
    val allowed = remember(profile, apps) {
        val byPkg = apps.associateBy { it.packageName }
        profile.allowedApps.mapNotNull { byPkg[it] }
    }
    if (allowed.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = TOP_BAR_HEIGHT + 40.dp, bottom = 40.dp), contentAlignment = Alignment.Center) {
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
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = TOP_BAR_HEIGHT + 20.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(allowed, key = { _, it -> it.key }) { i, app ->
            Box(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) {
                KidsAppCard(app) { onLaunch(app) }
            }
        }
    }
}

@Composable
private fun AllApps(
    apps: List<AppEntry>,
    firstFocus: FocusRequester,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = TOP_BAR_HEIGHT + 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(apps, key = { _, it -> it.key }) { i, app ->
                Box(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) {
                    AppCard(
                        app = app,
                        onClick = { onLaunch(app) },
                        onMenu = { onAppMenu(app) },
                        onFocus = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

