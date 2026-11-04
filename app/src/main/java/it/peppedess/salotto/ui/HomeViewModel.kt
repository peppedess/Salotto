package it.peppedess.salotto.ui

import android.app.ActivityOptions
import android.app.Application
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.AppRepository
import it.peppedess.salotto.data.ContinueItem
import it.peppedess.salotto.data.ContinueStore
import android.app.admin.DevicePolicyManager
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.PowerManager
import android.view.KeyEvent
import it.peppedess.salotto.MainActivity
import it.peppedess.salotto.data.ChannelGuide
import it.peppedess.salotto.data.EpgRepository
import it.peppedess.salotto.data.EpisodeInfo
import it.peppedess.salotto.data.Profile
import it.peppedess.salotto.data.StatsRepository
import it.peppedess.salotto.data.TvChannel
import it.peppedess.salotto.data.TvChannels
import it.peppedess.salotto.data.UpdateInfo
import it.peppedess.salotto.data.Updater
import it.peppedess.salotto.data.WeekStats
import it.peppedess.salotto.service.DailyWorker
import it.peppedess.salotto.service.SleepTimer
import it.peppedess.salotto.service.TvAccessibilityService
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import it.peppedess.salotto.data.HaClient
import it.peppedess.salotto.data.HaEntity
import it.peppedess.salotto.data.HaException
import it.peppedess.salotto.data.Weather
import it.peppedess.salotto.data.WeatherClient
import it.peppedess.salotto.data.Prefs
import it.peppedess.salotto.data.Settings
import it.peppedess.salotto.data.TmdbClient
import it.peppedess.salotto.data.SystemTv
import it.peppedess.salotto.ui.components.LaunchOrigin
import it.peppedess.salotto.data.TmdbDetails
import it.peppedess.salotto.data.TmdbException
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.kiosk.Kiosk
import it.peppedess.salotto.service.MediaListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Riga "Popolari su …" per un servizio di streaming installato. */
data class ProviderRow(
    val app: AppEntry,
    val providerName: String,
    val titles: List<TmdbTitle>,
)

/** Riga delle schede Film e Serie TV. */
data class CatalogRow(
    val key: String,
    val title: String,
    val titles: List<TmdbTitle>,
    val app: AppEntry? = null,
)

/** Scheda dettagli di un film o serie, con le app installate dove guardarlo. */
data class DetailState(
    val title: TmdbTitle,
    val apps: List<AppEntry>,
    val resolving: Boolean,
    val details: TmdbDetails? = null,
    val similar: List<TmdbTitle> = emptyList(),
    /** Chiave della locandina da cui si è aperta la scheda, per l'animazione condivisa. */
    val shareKey: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val repo = AppRepository(app)
    private val prefs = Prefs(app)
    private val store = ContinueStore.get(app)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val settings: StateFlow<Settings> = prefs.settings

    private val _providerPackages = MutableStateFlow<Set<String>>(emptySet())

    /** App per la riga "Le tue app" e la scheda App: solo intrattenimento, se richiesto. */
    val listedApps: StateFlow<List<AppEntry>> =
        combine(_apps, prefs.settings.map { it.entertainmentOnly }.distinctUntilChanged(), _providerPackages) { list, only, providers ->
            if (!only) list else list.filter { it.entertainment || it.packageName in providers }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Solo video: la musica (Deezer, Spotify…) resta fuori da "Continua a guardare". */
    val continueItems: StateFlow<List<ContinueItem>> =
        store.items.map { list -> list.filterNot { isMusicApp(it.packageName) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<AppEntry>> =
        combine(_apps, prefs.favorites) { list, keys ->
            val map = list.associateBy { it.key }
            keys.mapNotNull { map[it] }.filterNot { isMusicApp(it.packageName) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _notificationAccess = MutableStateFlow(false)
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    private val _deviceOwner = MutableStateFlow(false)
    val deviceOwner: StateFlow<Boolean> = _deviceOwner.asStateFlow()

    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    private val _navKey = MutableStateFlow(0)
    /** Cresce a ogni tasto direzionale: serve a selezionare il primo elemento se non c'è selezione. */
    val navKey: StateFlow<Int> = _navKey.asStateFlow()

    fun navKeyPressed() {
        _navKey.value = _navKey.value + 1
    }

    private val _providerRows = MutableStateFlow<List<ProviderRow>>(emptyList())
    val providerRows: StateFlow<List<ProviderRow>> = _providerRows.asStateFlow()

    private val _trending = MutableStateFlow<List<TmdbTitle>>(emptyList())
    val trending: StateFlow<List<TmdbTitle>> = _trending.asStateFlow()

    private val _catalogStatus = MutableStateFlow("")
    val catalogStatus: StateFlow<String> = _catalogStatus.asStateFlow()

    private val _movieRows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val movieRows: StateFlow<List<CatalogRow>> = _movieRows.asStateFlow()
    private val _tvRows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val tvRows: StateFlow<List<CatalogRow>> = _tvRows.asStateFlow()
    private var providerMatches: List<Pair<it.peppedess.salotto.data.TmdbProvider, AppEntry>> = emptyList()
    private val browseRequested = mutableSetOf<String>()
    private val browseLoaded = mutableSetOf<String>()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()

    private val catalogTick = MutableStateFlow(0)
    private var lastCatalog = 0L
    private var reloadJob: Job? = null

    // ---- Home Assistant ----
    private val _haAll = MutableStateFlow<List<HaEntity>>(emptyList())
    /** Tutte le entità controllabili, per la scelta nelle impostazioni. */
    val haAll: StateFlow<List<HaEntity>> = _haAll.asStateFlow()

    /** Le entità scelte, nell'ordine scelto, per la riga "Casa". */
    val haTiles: StateFlow<List<HaEntity>> =
        combine(_haAll, prefs.settings.map { it.haEntities }.distinctUntilChanged()) { all, ids ->
            val map = all.associateBy { it.id }
            ids.mapNotNull { map[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _haStatus = MutableStateFlow("")
    val haStatus: StateFlow<String> = _haStatus.asStateFlow()
    private val haTick = MutableStateFlow(0)

    // ---- Meteo ----
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    // ---- Salvaschermo ----
    private val foreground = MutableStateFlow(false)
    private val onHome = MutableStateFlow(true)
    private val lastActivity = MutableStateFlow(SystemClock.elapsedRealtime())
    private val _screensaver = MutableStateFlow(false)
    val screensaver: StateFlow<Boolean> = _screensaver.asStateFlow()

    // ---- Profili e bambini ----
    val profiles: StateFlow<List<Profile>> = prefs.profiles
    val profile: StateFlow<Profile> = prefs.profile
    private val _kidsRemaining = MutableStateFlow<Int?>(null)
    /** Secondi rimasti oggi al profilo bambini, null se non c'è limite. */
    val kidsRemaining: StateFlow<Int?> = _kidsRemaining.asStateFlow()
    private val _kidsLocked = MutableStateFlow(false)
    val kidsLocked: StateFlow<Boolean> = _kidsLocked.asStateFlow()

    // ---- La mia lista, nuovi episodi, consigli ----
    val myList: StateFlow<List<TmdbTitle>> = prefs.myList
    val history: StateFlow<List<TmdbTitle>> = prefs.history
    private val _newEpisodes = MutableStateFlow<List<EpisodeInfo>>(emptyList())
    val newEpisodes: StateFlow<List<EpisodeInfo>> = _newEpisodes.asStateFlow()
    private val _recommendations = MutableStateFlow<List<Pair<TmdbTitle, List<TmdbTitle>>>>(emptyList())
    val recommendations: StateFlow<List<Pair<TmdbTitle, List<TmdbTitle>>>> = _recommendations.asStateFlow()

    // ---- Dirette TV ----
    private val epg = EpgRepository(app)
    private val _liveGuide = MutableStateFlow<List<ChannelGuide>>(emptyList())
    val liveGuide: StateFlow<List<ChannelGuide>> = _liveGuide.asStateFlow()
    private val _epgStatus = MutableStateFlow("")
    val epgStatus: StateFlow<String> = _epgStatus.asStateFlow()
    private val epgTick = MutableStateFlow(0)

    // ---- Timer ----
    val sleepEndsAt: StateFlow<Long> = SleepTimer.endsAt
    private val _adminActive = MutableStateFlow(false)
    val adminActive: StateFlow<Boolean> = _adminActive.asStateFlow()

    // ---- Aggiornamenti ----
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()
    private val _updateStatus = MutableStateFlow("")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()
    private var lastUpdateCheck = 0L

    // ---- Statistiche ----
    private val _stats = MutableStateFlow<WeekStats?>(null)
    val stats: StateFlow<WeekStats?> = _stats.asStateFlow()
    private val _statsPermission = MutableStateFlow(false)
    val statsPermission: StateFlow<Boolean> = _statsPermission.asStateFlow()

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = scheduleReload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleReload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleReload()
    }

    init {
        runCatching { launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper())) }
        viewModelScope.launch { loadApps() }
        refreshStatus()

        // Catalogo TMDB: si ricarica quando cambia la chiave o l'elenco delle app.
        viewModelScope.launch {
            combine(
                prefs.settings.map { it.tmdbKey.trim() }.distinctUntilChanged(),
                _apps.map { list -> list.map { it.packageName }.toSet() }.distinctUntilChanged(),
                catalogTick,
            ) { key, _, _ -> key }
                .collectLatest { key -> loadCatalog(key) }
        }

        // Home Assistant: aggiorna gli stati ogni 15 secondi mentre Salotto è in primo piano.
        viewModelScope.launch {
            combine(
                prefs.settings.map { it.haUrl.trim() to it.haToken.trim() }.distinctUntilChanged(),
                foreground,
                haTick,
            ) { config, fg, _ -> config to fg }
                .collectLatest { (config, fg) ->
                    val (url, token) = config
                    if (url.isBlank() || token.isBlank()) {
                        _haAll.value = emptyList()
                        _haStatus.value = ""
                        return@collectLatest
                    }
                    if (!fg) return@collectLatest
                    val client = HaClient(url, token)
                    while (true) {
                        refreshHa(client)
                        delay(HA_POLL_MS)
                    }
                }
        }

        // Meteo: all'avvio, quando cambia la città e ogni 30 minuti.
        viewModelScope.launch {
            prefs.settings.map { it.weatherCity.trim() }.distinctUntilChanged().collectLatest { city ->
                if (city.isBlank()) {
                    _weather.value = null
                    return@collectLatest
                }
                while (true) {
                    val result = try {
                        WeatherClient.load(city)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (result != null) _weather.value = result
                    delay(if (result != null) WEATHER_TTL_MS else 120_000L)
                }
            }
        }

        SleepTimer.load(app)
        DailyWorker.schedule(app)

        // Righe personali: nuovi episodi e consigli, da La mia lista e dai titoli aperti.
        viewModelScope.launch {
            combine(
                prefs.settings.map { it.tmdbKey.trim() }.distinctUntilChanged(),
                prefs.myList,
                prefs.history,
            ) { key, list, history -> Triple(key, list, history) }
                .collectLatest { (key, list, history) -> loadPersonal(key, list, history) }
        }

        // Profilo bambini: conta il tempo a schermo acceso e blocca al limite.
        viewModelScope.launch {
            prefs.profile.collectLatest { p ->
                _kidsLocked.value = false
                if (!p.kids || p.dailyLimitMin <= 0) {
                    _kidsRemaining.value = null
                    return@collectLatest
                }
                while (true) {
                    val remaining = p.dailyLimitMin * 60 - prefs.kidsUsedSeconds(p.id)
                    _kidsRemaining.value = remaining.coerceAtLeast(0)
                    if (remaining <= 0 && !_kidsLocked.value) {
                        _kidsLocked.value = true
                        enforceKidsLock()
                    }
                    delay(KIDS_TICK_S * 1000L)
                    if (isInteractive() && !_kidsLocked.value) prefs.addKidsUsage(p.id, KIDS_TICK_S)
                }
            }
        }

        // Dirette TV: guida scaricata al massimo ogni 12 ore.
        viewModelScope.launch {
            combine(prefs.settings.map { it.liveTv }.distinctUntilChanged(), epgTick) { on, _ -> on }
                .collectLatest { on ->
                    if (!on) {
                        _liveGuide.value = emptyList()
                        return@collectLatest
                    }
                    while (true) {
                        try {
                            _liveGuide.value = epg.load()
                            _epgStatus.value = "Guida aggiornata"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (_liveGuide.value.isEmpty()) {
                                _liveGuide.value = TvChannels.all.map { ChannelGuide(it, emptyList()) }
                            }
                            _epgStatus.value = "Guida non disponibile — " + describe(e)
                        }
                        delay(EPG_REFRESH_MS)
                    }
                }
        }

        if (prefs.settings.value.autoUpdate) checkUpdate(manual = false)

        // Salvaschermo: parte dopo N minuti senza tocchi né tasti, solo sulla Home.
        viewModelScope.launch {
            combine(
                lastActivity,
                foreground,
                onHome,
                prefs.settings.map { it.screensaverMinutes }.distinctUntilChanged(),
            ) { last, fg, home, minutes -> Triple(last, fg && home, minutes) }
                .collectLatest { (last, eligible, minutes) ->
                    if (!eligible || minutes <= 0) return@collectLatest
                    val wait = minutes * 60_000L - (SystemClock.elapsedRealtime() - last)
                    if (wait > 0) delay(wait)
                    _screensaver.value = true
                }
        }

        // Locandine TMDB per "Continua a guardare".
        viewModelScope.launch {
            combine(store.items, prefs.settings.map { it.tmdbKey.trim() }.distinctUntilChanged()) { items, key ->
                items to key
            }.collectLatest { (items, key) ->
                if (key.isNotBlank()) enrich(items, key)
            }
        }
    }

    private suspend fun loadApps() {
        val list = repo.load()
        _apps.value = list
        _loading.value = false
        if (!prefs.favoritesInitialized) {
            val byPkg = list.associateBy { it.packageName }
            prefs.setFavorites(DEFAULT_FAVORITES.mapNotNull { byPkg[it]?.key })
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(400)
            loadApps()
        }
    }

    fun refreshStatus() {
        _notificationAccess.value =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        _deviceOwner.value = Kiosk.isOwner(ctx)
        _adminActive.value = ctx.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(Kiosk.admin(ctx)) == true
        _statsPermission.value = StatsRepository.hasPermission(ctx)
        _isDefaultHome.value = checkDefaultHome()
        applySystemTv()
        if (lastCatalog > 0 && SystemClock.elapsedRealtime() - lastCatalog > CATALOG_TTL_MS) {
            catalogTick.value = catalogTick.value + 1
        }
        if (prefs.settings.value.autoUpdate && lastUpdateCheck > 0 &&
            SystemClock.elapsedRealtime() - lastUpdateCheck > CATALOG_TTL_MS
        ) {
            checkUpdate(manual = false)
        }
    }

    fun goHome() {
        _homeTick.value = _homeTick.value + 1
    }

    // ---- Salvaschermo ----

    fun setForeground(value: Boolean) {
        foreground.value = value
        if (!value) _screensaver.value = false
        userActivity()
    }

    fun setOnHome(value: Boolean) {
        onHome.value = value
    }

    /** Chiamato a ogni tocco o tasto. Restituisce true se ha chiuso il salvaschermo. */
    fun userActivity(): Boolean {
        lastActivity.value = SystemClock.elapsedRealtime()
        if (_screensaver.value) {
            _screensaver.value = false
            return true
        }
        return false
    }

    fun startScreensaver() {
        _screensaver.value = true
    }

    // ---- Home Assistant ----

    private suspend fun refreshHa(client: HaClient) {
        try {
            _haAll.value = client.states()
            _haStatus.value = "Connesso"
        } catch (e: CancellationException) {
            throw e
        } catch (e: HaException) {
            _haStatus.value = if (e.code == 401) "Token non valido" else "Errore Home Assistant (${e.code})"
        } catch (e: Exception) {
            _haStatus.value = "Home Assistant non raggiungibile — " + describe(e)
        }
    }

    fun refreshHomeAssistant() {
        haTick.value = haTick.value + 1
    }

    /** Tocco su un riquadro: aggiorna subito l'aspetto, poi chiede lo stato reale. */
    fun haPress(entity: HaEntity) {
        val s = prefs.settings.value
        if (!s.haConfigured) return
        if (!entity.isAction && entity.domain != "media_player") {
            val flipped = if (entity.isOn) {
                if (entity.domain == "cover") "closing" else "off"
            } else {
                if (entity.domain == "cover") "opening" else "on"
            }
            _haAll.value = _haAll.value.map { if (it.id == entity.id) it.copy(state = flipped) else it }
        }
        viewModelScope.launch {
            val client = HaClient(s.haUrl, s.haToken)
            try {
                client.call(entity)
                delay(700)
                refreshHa(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Home Assistant: comando non riuscito")
                refreshHa(client)
            }
        }
    }

    fun toggleHaEntity(id: String) {
        prefs.updateSettings { s ->
            s.copy(haEntities = if (id in s.haEntities) s.haEntities - id else s.haEntities + id)
        }
    }

    // ---- Catalogo TMDB ----

    private suspend fun loadCatalog(key: String) {
        if (key.isBlank()) {
            _providerRows.value = emptyList()
            _trending.value = emptyList()
            _catalogStatus.value = ""
            return
        }
        val apps = _apps.value
        if (apps.isEmpty()) return
        val client = TmdbClient(key)
        _catalogStatus.value = "Caricamento…"

        val providers = try {
            client.providers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TmdbException) {
            _catalogStatus.value = if (e.code == 401) "Chiave non valida" else "Errore TMDB (${e.code})"
            return
        } catch (e: Exception) {
            _catalogStatus.value = "TMDB non raggiungibile — " + describe(e)
            return
        }

        val matched = providers
            .mapNotNull { p -> matchApp(p.name, apps)?.let { p to it } }
            .distinctBy { it.second.packageName }
            .take(MAX_PROVIDER_ROWS)

        val rows = coroutineScope {
            matched.map { (provider, app) ->
                async {
                    val shows = safe { client.discover("tv", provider.id) }
                    val movies = safe { client.discover("movie", provider.id) }
                    ProviderRow(app, provider.name, interleave(shows, movies).take(20))
                }
            }.awaitAll()
        }.filter { it.titles.isNotEmpty() }

        _providerRows.value = rows
        _providerPackages.value = matched.map { it.second.packageName }.toSet()
        providerMatches = matched
        browseLoaded.clear()
        browseRequested.toList().forEach { loadBrowse(it) }
        _trending.value = safe { client.trending() }.filter { it.posterUrl != null }
        _catalogStatus.value = if (rows.isEmpty()) {
            "Nessuna app di streaming riconosciuta"
        } else {
            "Attiva: " + rows.joinToString { it.app.label }
        }
        lastCatalog = SystemClock.elapsedRealtime()
    }

    private suspend fun enrich(items: List<ContinueItem>, key: String) {
        val client = TmdbClient(key)
        for (item in items) {
            if (item.remoteArt != null || item.packageName in NO_TMDB_PACKAGES) continue
            val url = findArt(client, item) ?: continue
            store.setRemoteArt(item.id, url)
        }
    }

    private suspend fun findArt(client: TmdbClient, item: ContinueItem): String? {
        val candidates = listOf(
            item.title,
            item.title.substringBefore(":"),
            item.title.substringBefore(" - "),
            item.subtitle,
        ).map { cleanTitle(it) }.filter { it.length >= 2 }.distinct()

        for (query in candidates) {
            val cached = prefs.cachedArt(query)
            if (cached != null) {
                if (cached.isNotEmpty()) return cached
                continue
            }
            val results = try {
                client.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
            val best = results.firstOrNull { it.backdropUrl != null || it.posterUrl != null }
            val url = best?.backdropUrl ?: best?.posterUrl ?: ""
            prefs.cacheArt(query, url)
            if (url.isNotEmpty()) return url
        }
        return null
    }

    suspend fun searchTitles(query: String): List<TmdbTitle> {
        val key = prefs.settings.value.tmdbKey.trim()
        if (key.isBlank() || query.isBlank()) return emptyList()
        return safe { TmdbClient(key).search(query) }.filter { it.posterUrl != null }.take(20)
    }

    fun openDetail(title: TmdbTitle, app: AppEntry?, shareKey: String? = null) {
        _detail.value = DetailState(title, listOfNotNull(app), resolving = true, shareKey = shareKey)
        prefs.addHistory(title)
        val key = prefs.settings.value.tmdbKey.trim()
        if (key.isBlank()) {
            _detail.value = _detail.value?.copy(resolving = false)
            return
        }
        val client = TmdbClient(key)
        viewModelScope.launch {
            val names = safe { client.watchProviders(title.type, title.id) }
            val apps = _apps.value
            val found = (listOfNotNull(app) + names.mapNotNull { matchApp(it, apps) }).distinctBy { it.packageName }
            updateDetail(title) { it.copy(apps = found, resolving = false) }
        }
        viewModelScope.launch {
            val details = try {
                client.details(title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            updateDetail(title) { it.copy(details = details) }
        }
        viewModelScope.launch {
            val similar = safe { client.recommendations(title) }.filter { it.posterUrl != null }.take(15)
            updateDetail(title) { it.copy(similar = similar) }
        }
    }

    private fun updateDetail(title: TmdbTitle, change: (DetailState) -> DetailState) {
        val current = _detail.value
        if (current != null && current.title.key == title.key) _detail.value = change(current)
    }

    /** Carica le righe della scheda Film ("movie") o Serie TV ("tv") la prima volta che si apre. */
    fun loadBrowse(type: String) {
        browseRequested += type
        val key = prefs.settings.value.tmdbKey.trim()
        if (key.isBlank() || !browseLoaded.add(type)) return
        val client = TmdbClient(key)
        val matches = providerMatches
        val providerIds = matches.map { it.first.id }
        val genres = if (type == "tv") TV_GENRES else MOVIE_GENRES
        val kind = if (type == "tv") "Serie" else "Film"
        viewModelScope.launch {
            val rows = coroutineScope {
                val trending = async {
                    CatalogRow("trend", "$kind di tendenza", safe { client.trending(type) })
                }
                val recent = async {
                    CatalogRow("recent", if (type == "tv") "Nuove serie sui tuoi servizi" else "Arrivati da poco sui tuoi servizi", safe { client.discoverRecent(type, providerIds) })
                }
                val perProvider = matches.map { (provider, app) ->
                    async {
                        CatalogRow("prov:${app.packageName}", "$kind su ${app.label}", safe { client.discover(type, provider.id) }, app)
                    }
                }
                val perGenre = genres.map { (id, name) ->
                    async { CatalogRow("genre:$id", name, safe { client.discoverGenre(type, id, providerIds) }) }
                }
                listOf(trending, recent).awaitAll() + perProvider.awaitAll() + perGenre.awaitAll()
            }.map { row -> row.copy(titles = row.titles.filter { it.posterUrl != null }.distinctBy { it.key }.take(20)) }
                .filter { it.titles.size >= 3 }
            if (type == "tv") _tvRows.value = rows else _movieRows.value = rows
            if (rows.isEmpty()) browseLoaded.remove(type)
        }
    }

    fun refreshCatalog() {
        browseLoaded.clear()
        browseRequested.toList().forEach { loadBrowse(it) }
        prefs.clearArtCache()
        store.clearRemoteArt()
        catalogTick.value = catalogTick.value + 1
    }

    /** Apre il titolo nell'app: ricerca interna se supportata, altrimenti app + titolo negli appunti. */
    fun watchIn(title: TmdbTitle, app: AppEntry) {
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(app.packageName)
            .putExtra(SearchManager.QUERY, title.title)
        val canSearch = ctx.packageManager.queryIntentActivities(search, 0).isNotEmpty()
        if (canSearch && start(search, quiet = true, animate = true)) return

        val clipboard = ctx.getSystemService(ClipboardManager::class.java)
        runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("Titolo", title.title)) }
        launch(app)
        toast("«${title.title}» copiato: incollalo nella ricerca di ${app.label}")
    }

    fun watchTrailer(title: TmdbTitle) {
        searchYouTube("${title.title} ${title.year} trailer ita".trim())
    }

    // ---- Profili e bambini ----

    fun switchProfile(id: String) {
        prefs.setProfile(id)
        if (!prefs.favoritesInitialized) {
            val byPkg = _apps.value.associateBy { it.packageName }
            prefs.setFavorites(DEFAULT_FAVORITES.mapNotNull { byPkg[it]?.key })
        }
        _screensaver.value = false
        goHome()
    }

    fun saveProfile(profile: Profile) = prefs.saveProfile(profile)

    fun deleteProfile(id: String) = prefs.deleteProfile(id)

    fun grantKidsExtra(minutes: Int) {
        val p = prefs.profile.value
        prefs.grantKidsExtra(p.id, minutes)
        _kidsLocked.value = false
        _kidsRemaining.value = (p.dailyLimitMin * 60 - prefs.kidsUsedSeconds(p.id)).coerceAtLeast(0)
    }

    private fun isInteractive(): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** Tempo finito: pausa, poi Salotto torna davanti (proprietario del dispositivo o root). */
    private fun enforceKidsLock() {
        SleepTimer.pauseEverything(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { ctx.startActivity(intent) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!foreground.value) {
                runCatching {
                    Runtime.getRuntime()
                        .exec(arrayOf("su", "-c", "am start -n ${ctx.packageName}/${MainActivity::class.java.name}"))
                        .waitFor()
                }
            }
        }
    }

    // ---- La mia lista, nuovi episodi, consigli ----

    fun isInMyList(title: TmdbTitle): Boolean = prefs.isInMyList(title)

    /** Restituisce true se il titolo è stato aggiunto. */
    fun toggleMyList(title: TmdbTitle): Boolean {
        val added = prefs.toggleMyList(title)
        toast(if (added) "Aggiunto a La mia lista" else "Rimosso da La mia lista")
        return added
    }

    private suspend fun loadPersonal(key: String, list: List<TmdbTitle>, history: List<TmdbTitle>) {
        if (key.isBlank()) {
            _newEpisodes.value = emptyList()
            _recommendations.value = emptyList()
            return
        }
        val client = TmdbClient(key)
        val today = LocalDate.now()

        val episodes = coroutineScope {
            list.filter { it.type == "tv" }.take(15).map { series ->
                async {
                    try {
                        client.lastEpisode(series)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull().filter { ep ->
            val date = runCatching { LocalDate.parse(ep.airDate) }.getOrNull()
            date != null && !date.isAfter(today) && !date.isBefore(today.minusDays(14))
        }.sortedByDescending { it.airDate }
        _newEpisodes.value = episodes

        val inList = list.map { it.key }.toSet()
        val seeds = (list.take(2) + history.take(3)).distinctBy { it.key }.take(3)
        _recommendations.value = coroutineScope {
            seeds.map { seed ->
                async {
                    val recs = safe { client.recommendations(seed) }
                        .filter { it.posterUrl != null && it.key !in inList }
                        .take(15)
                    seed to recs
                }
            }.awaitAll()
        }.filter { it.second.isNotEmpty() }
    }

    // ---- Dirette TV ----

    fun openLive(channel: TvChannel) {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(channel.liveUrl)), animate = true)
    }

    fun reloadEpg() {
        viewModelScope.launch {
            try {
                _liveGuide.value = epg.load(force = true)
                _epgStatus.value = "Guida aggiornata"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _epgStatus.value = "Guida non disponibile — " + describe(e)
            }
        }
    }

    // ---- Audio ----

    private fun audio(): AudioManager? = ctx.getSystemService(AudioManager::class.java)

    fun volume(): Pair<Int, Int> {
        val a = audio() ?: return 0 to 1
        return a.getStreamVolume(AudioManager.STREAM_MUSIC) to a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(level: Int) {
        runCatching { audio()?.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
    }

    fun toggleMute() {
        runCatching { audio()?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
    }

    fun isMuted(): Boolean = audio()?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false

    /** Selettore dell'uscita audio di sistema (altoparlante, Bluetooth, cast). */
    fun openOutputSwitcher() {
        if (Build.VERSION.SDK_INT >= 34) {
            val shown = runCatching { MediaRouter2.getInstance(ctx).showSystemOutputSwitcher() }.getOrDefault(false)
            if (shown) return
        }
        val pkg = MediaListenerService.nowPlaying.value?.packageName ?: ctx.packageName
        val sent = runCatching {
            ctx.sendBroadcast(
                Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                    .setPackage("com.android.systemui")
                    .putExtra("package_name", pkg)
            )
        }.isSuccess
        if (!sent) start(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openWifi() {
        val panel = if (Build.VERSION.SDK_INT >= 29) AndroidSettings.Panel.ACTION_INTERNET_CONNECTIVITY else AndroidSettings.ACTION_WIFI_SETTINGS
        if (!start(Intent(panel), quiet = true)) start(Intent(AndroidSettings.ACTION_WIFI_SETTINGS))
    }

    fun openBluetoothSettings() = start(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))

    // ---- Timer di spegnimento ----

    fun startSleepTimer(minutes: Int) {
        SleepTimer.start(ctx, minutes)
        toast("Spegnimento tra $minutes minuti")
    }

    fun cancelSleepTimer() {
        SleepTimer.cancel(ctx)
        toast("Timer annullato")
    }

    fun requestDeviceAdmin() {
        start(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Kiosk.admin(ctx))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Serve a Salotto per spegnere lo schermo allo scadere del timer.",
                )
        )
    }

    // ---- Ricevitore Cast ----

    fun openCastReceiver(): Boolean {
        val apps = _apps.value
        val match = apps.firstOrNull { it.packageName in CAST_PACKAGES }
            ?: apps.firstOrNull { app ->
                val label = app.label.lowercase()
                CAST_KEYWORDS.any { it in label }
            }
        if (match != null) {
            launch(match)
            return true
        }
        return false
    }

    // ---- Aggiornamenti ----

    fun checkUpdate(manual: Boolean) {
        lastUpdateCheck = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            if (manual) _updateStatus.value = "Controllo in corso…"
            try {
                val info = Updater.check(ctx)
                _update.value = info
                _updateStatus.value = if (info == null) {
                    "Salotto è aggiornato (build ${Updater.currentBuild(ctx)})"
                } else {
                    "Disponibile la build ${info.build}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) _updateStatus.value = "Controllo non riuscito — " + describe(e)
            }
        }
    }

    fun installUpdate() {
        val info = _update.value ?: return
        viewModelScope.launch {
            _updateStatus.value = "Scarico la build ${info.build}…"
            try {
                _updateStatus.value = Updater.install(ctx, info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _updateStatus.value = "Aggiornamento non riuscito — " + describe(e)
            }
        }
    }

    // ---- Statistiche ----

    fun loadStats() {
        val allowed = StatsRepository.hasPermission(ctx)
        _statsPermission.value = allowed
        if (!allowed) return
        val extra = (
            _providerRows.value.map { it.app.packageName } +
                favorites.value.map { it.packageName } +
                store.items.value.map { it.packageName } +
                DEFAULT_FAVORITES
            ).toSet()
        viewModelScope.launch {
            _stats.value = runCatching { StatsRepository.week(ctx, extra) }.getOrNull()
        }
    }

    fun openUsageAccess() = start(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))

    /** App di musica e audio: pacchetti noti o categoria "Audio" di Android. */
    fun isMusicApp(pkg: String): Boolean = musicCache.getOrPut(pkg) {
        if (MUSIC_PACKAGES.any { pkg.startsWith(it) }) return@getOrPut true
        val info = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
        info?.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO
    }

    fun appByPackage(pkg: String): AppEntry? = _apps.value.firstOrNull { it.packageName == pkg }

    // ---- Avvio app e contenuti ----

    fun launch(app: AppEntry) {
        tvIntentFor(app.packageName)?.let { tv ->
            if (start(tv, quiet = true, animate = true)) return
        }
        val component = ComponentName.unflattenFromString(app.key)
        val ok = component != null && runCatching {
            launcherApps.startMainActivity(
                component,
                Process.myUserHandle(),
                LaunchOrigin.sourceBounds(),
                LaunchOrigin.options()?.toBundle(),
            )
        }.isSuccess
        if (!ok) launchPackage(app.packageName)
    }

    /**
     * Versione per TV di un'app, se c'è: l'attività "leanback" dell'app stessa,
     * oppure SmartTube al posto di YouTube.
     */
    private fun tvIntentFor(pkg: String): Intent? {
        if (!prefs.settings.value.preferTvApps) return null
        val pm = ctx.packageManager
        if (pkg == YOUTUBE) {
            SystemTv.SMARTTUBE.firstNotNullOfOrNull { tube ->
                pm.getLeanbackLaunchIntentForPackage(tube) ?: pm.getLaunchIntentForPackage(tube)
            }?.let { return it }
        }
        return pm.getLeanbackLaunchIntentForPackage(pkg)
    }

    fun smartTubeInstalled(): Boolean =
        SystemTv.SMARTTUBE.any { runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess }

    // ---- Esperienza TV di sistema ----

    private val _canWriteSettings = MutableStateFlow(false)
    val canWriteSettings: StateFlow<Boolean> = _canWriteSettings.asStateFlow()

    private val _volume = MutableStateFlow<Pair<Int, Int>?>(null)
    /** Livello e massimo del volume da mostrare nell'indicatore in stile TV, null se nascosto. */
    val volumeHud: StateFlow<Pair<Int, Int>?> = _volume.asStateFlow()
    private var volumeJob: Job? = null

    private val _returnTick = MutableStateFlow(0)
    val returnTick: StateFlow<Int> = _returnTick.asStateFlow()

    fun returnedHome() {
        _returnTick.value = _returnTick.value + 1
    }

    /** Volume gestito da Salotto: niente pannello di MIUI, solo l'indicatore in stile TV. */
    fun changeVolume(direction: Int) {
        val a = ctx.getSystemService(AudioManager::class.java) ?: return
        val adjust = when {
            direction > 0 -> AudioManager.ADJUST_RAISE
            direction < 0 -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        runCatching { a.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, 0) }
        val level = if (a.isStreamMute(AudioManager.STREAM_MUSIC)) 0 else a.getStreamVolume(AudioManager.STREAM_MUSIC)
        _volume.value = level to a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            delay(1800)
            _volume.value = null
        }
    }

    fun setLandscapeAll(enabled: Boolean) {
        prefs.updateSettings { it.copy(landscapeAll = enabled) }
        if (enabled) {
            if (SystemTv.canWriteSettings(ctx)) {
                SystemTv.lockLandscape(ctx)
            } else if (!TvAccessibilityService.enabled.value) {
                // Senza il servizio TV serve il permesso di modificare la rotazione di sistema.
                start(Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }
        } else {
            SystemTv.restoreRotation(ctx)
        }
    }

    // ---- Servizio TV e configurazione iniziale ----

    val tvServiceEnabled: StateFlow<Boolean> = TvAccessibilityService.enabled.asStateFlow()

    private val _isDefaultHome = MutableStateFlow(false)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    private fun checkDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = ctx.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == ctx.packageName
    }

    fun openAccessibilitySettings() = start(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openWriteSettings() = start(
        Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    )

    fun completeSetup() {
        prefs.updateSettings { it.copy(setupDone = true) }
    }

    fun restartSetup() {
        prefs.updateSettings { it.copy(setupDone = false) }
    }

    fun setTvQuiet(enabled: Boolean) {
        prefs.updateSettings { it.copy(tvQuiet = enabled) }
        MediaListenerService.setQuiet(ctx, enabled)
    }

    private fun applySystemTv() {
        _canWriteSettings.value = SystemTv.canWriteSettings(ctx)
        if (prefs.settings.value.landscapeAll) SystemTv.lockLandscape(ctx)
        if (prefs.settings.value.tvQuiet) MediaListenerService.setQuiet(ctx, true)
    }

    fun resume(item: ContinueItem) {
        val controller = MediaListenerService.active[item.packageName]
        val pending = controller?.sessionActivity
        val sent = pending != null && runCatching {
            val options = LaunchOrigin.options() ?: ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            pending.send(ctx, 0, null, null, null, null, options.toBundle())
        }.isSuccess
        if (!sent) launchPackage(item.packageName)
        if (controller != null && controller.playbackState?.state != PlaybackState.STATE_PLAYING) {
            runCatching { controller.transportControls.play() }
        }
    }

    private fun launchPackage(pkg: String) {
        val intent = tvIntentFor(pkg)
            ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast("App non disponibile")
            return
        }
        start(intent, animate = true)
    }

    fun removeContinue(item: ContinueItem) = store.remove(item.id)

    fun clearContinue() = store.clear()

    // ---- Preferite ----

    fun toggleFavorite(app: AppEntry) {
        val list = prefs.favorites.value
        prefs.setFavorites(if (app.key in list) list - app.key else list + app.key)
    }

    fun moveFavorite(app: AppEntry, delta: Int) {
        val list = prefs.favorites.value.toMutableList()
        val from = list.indexOf(app.key)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.removeAt(from)
        list.add(to, app.key)
        prefs.setFavorites(list)
    }

    // ---- Impostazioni ----

    fun updateSettings(transform: (Settings) -> Settings) = prefs.updateSettings(transform)

    fun becomeOwner(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val (ok, out) = Kiosk.becomeOwnerWithRoot(ctx)
            refreshStatus()
            onResult(
                if (ok) "Salotto ora è proprietario del dispositivo: il Kiosk blocca tutto il sistema."
                else "Non riuscito: ${out.ifBlank { "nessuna risposta da su" }}. Se ci sono account sul telefono, Android non lo consente."
            )
        }
    }

    fun removeOwner(onResult: (String) -> Unit) {
        prefs.updateSettings { it.copy(kiosk = false) }
        val ok = Kiosk.releaseOwner(ctx)
        refreshStatus()
        onResult(if (ok) "Proprietario del dispositivo rimosso." else "Impossibile rimuovere il proprietario.")
    }

    fun openNotificationAccess() = start(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openHomeSettings() = start(Intent(AndroidSettings.ACTION_HOME_SETTINGS))

    fun openSystemSettings() = start(Intent(AndroidSettings.ACTION_SETTINGS))

    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun openAppInfo(app: AppEntry) = start(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
    )

    fun uninstall(app: AppEntry) = start(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
    )

    // ---- Ricerca web ----

    fun searchYouTube(query: String) {
        val intent = Intent(Intent.ACTION_SEARCH)
            .setPackage("com.google.android.youtube")
            .putExtra(SearchManager.QUERY, query)
        if (!start(intent, quiet = true)) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
        }
    }

    fun searchWeb(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!start(intent, quiet = true)) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
        }
    }

    fun searchPlayStore(query: String) {
        if (!start(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query))), quiet = true)) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query))))
        }
    }

    private fun start(intent: Intent, quiet: Boolean = false, animate: Boolean = false): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = if (animate) LaunchOrigin.options()?.toBundle() else null
        if (animate) LaunchOrigin.sourceBounds()?.let { intent.sourceBounds = it }
        return runCatching { ctx.startActivity(intent, options) }
            .onFailure { if (!quiet) toast("Impossibile aprire") }
            .isSuccess
    }

    private fun toast(text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
    }

    override fun onCleared() {
        runCatching { launcherApps.unregisterCallback(packageCallback) }
        super.onCleared()
    }

    private companion object {
        val MOVIE_GENRES = listOf(
            28 to "Azione", 35 to "Commedia", 18 to "Dramma", 53 to "Thriller", 878 to "Fantascienza",
            16 to "Animazione", 27 to "Horror", 10749 to "Romantici", 10751 to "Per tutta la famiglia", 99 to "Documentari",
        )
        val TV_GENRES = listOf(
            80 to "Crime", 18 to "Drammi", 35 to "Commedie", 10765 to "Fantascienza e fantasy", 9648 to "Mistero",
            10759 to "Azione e avventura", 16 to "Animazione", 99 to "Documentari", 10764 to "Reality",
        )
        const val YOUTUBE = "com.google.android.youtube"
        const val MAX_PROVIDER_ROWS = 6
        const val CATALOG_TTL_MS = 6L * 60 * 60 * 1000
        const val HA_POLL_MS = 15_000L
        const val WEATHER_TTL_MS = 30L * 60 * 1000
        const val KIDS_TICK_S = 30
        const val EPG_REFRESH_MS = 60L * 60 * 1000

        /** Ricevitori Cast/AirPlay/Miracast più diffusi. */
        val CAST_PACKAGES = setOf(
            "com.ionitech.airscreen",
            "com.waxrain.airplaydmr",
            "com.waxrain.airplaydmr_free",
            "com.hpplay.happyplay.aw",
            "com.xindawn",
            "com.toxic.apps.chrome",
        )
        val CAST_KEYWORDS = listOf("airscreen", "miracast", "cast receiver", "ricevitore", "wi-fi display", "airplay", "airpin")

        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
            "it.rainet.raiplay",
            "it.mediaset.infinity",
            "com.dazn",
            "tv.twitch.android.app",
            "com.plexapp.android",
        )

        /** Statico: viene usato anche durante la costruzione del ViewModel. */
        private val musicCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

        val MUSIC_PACKAGES = listOf(
            "deezer.", "com.spotify", "com.google.android.apps.youtube.music", "com.amazon.mp3",
            "com.apple.android.music", "com.soundcloud", "com.aspiro.tidal", "com.qobuz",
            "com.audible", "com.google.android.apps.podcasts", "au.com.shiftyjelly.pocketcasts",
            "fm.castbox", "com.shazam", "com.sonos", "tunein.player", "com.maxmpz.audioplayer",
        )

        /** App di musica o video brevi: qui la copertina originale è già quella giusta. */
        val NO_TMDB_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            "deezer.android.app",
            "com.amazon.mp3",
            "com.apple.android.music",
            "com.soundcloud.android",
            "tv.twitch.android.app",
            "com.audible.application",
            "com.google.android.apps.podcasts",
            "au.com.shiftyjelly.pocketcasts",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.facebook.katana",
        )

        /** Nomi TMDB dei servizi → pacchetti noti (gli altri si abbinano per nome). */
        val PROVIDER_HINTS = mapOf(
            "netflix" to "com.netflix.mediaclient",
            "amazonprimevideo" to "com.amazon.avod.thirdpartyclient",
            "disneyplus" to "com.disney.disneyplus",
        )

        private val EPISODE = Regex("""\b(S\d+\s*[:.]?\s*E\d+|Stagione\s+\d+|Episodio\s+\d+|Ep\.?\s*\d+)\b""", RegexOption.IGNORE_CASE)

        fun cleanTitle(raw: String): String =
            raw.replace(EPISODE, " ")
                .replace(Regex("""[«»“”"]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', ':', '-', '·', ',')

        fun describe(e: Throwable): String {
            val root = generateSequence(e) { it.cause }.last()
            val kind = when (root) {
                is java.net.UnknownHostException -> "dominio non risolto (DNS o blocco annunci)"
                is java.net.ConnectException -> "connessione rifiutata"
                is java.net.SocketTimeoutException -> "tempo scaduto"
                is javax.net.ssl.SSLException -> "errore SSL (connessione intercettata?)"
                is SecurityException -> "permesso di rete mancante"
                else -> root.javaClass.simpleName
            }
            val detail = root.message?.take(80)?.let { ": $it" }.orEmpty()
            return kind + detail
        }

        fun normalize(s: String): String =
            s.lowercase().replace("+", "plus").filter { it.isLetterOrDigit() }

        fun matchApp(providerName: String, apps: List<AppEntry>): AppEntry? {
            val n = normalize(providerName)
            if (n.isEmpty()) return null
            PROVIDER_HINTS[n]?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
            apps.firstOrNull { normalize(it.label) == n }?.let { return it }
            return apps.firstOrNull {
                val l = normalize(it.label)
                l.length >= 5 && n.length >= 5 && (n.contains(l) || l.contains(n))
            }
        }

        fun <T> interleave(a: List<T>, b: List<T>): List<T> {
            val out = ArrayList<T>(a.size + b.size)
            for (i in 0 until maxOf(a.size, b.size)) {
                a.getOrNull(i)?.let { out += it }
                b.getOrNull(i)?.let { out += it }
            }
            return out
        }

        suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}
