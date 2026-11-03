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
import it.peppedess.salotto.data.Prefs
import it.peppedess.salotto.data.Settings
import it.peppedess.salotto.data.TmdbClient
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

/** Scheda dettagli di un film o serie, con le app installate dove guardarlo. */
data class DetailState(
    val title: TmdbTitle,
    val apps: List<AppEntry>,
    val resolving: Boolean,
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
    val continueItems: StateFlow<List<ContinueItem>> = store.items

    val favorites: StateFlow<List<AppEntry>> =
        combine(_apps, prefs.favorites) { list, keys ->
            val map = list.associateBy { it.key }
            keys.mapNotNull { map[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _notificationAccess = MutableStateFlow(false)
    val notificationAccess: StateFlow<Boolean> = _notificationAccess.asStateFlow()

    private val _deviceOwner = MutableStateFlow(false)
    val deviceOwner: StateFlow<Boolean> = _deviceOwner.asStateFlow()

    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    private val _providerRows = MutableStateFlow<List<ProviderRow>>(emptyList())
    val providerRows: StateFlow<List<ProviderRow>> = _providerRows.asStateFlow()

    private val _trending = MutableStateFlow<List<TmdbTitle>>(emptyList())
    val trending: StateFlow<List<TmdbTitle>> = _trending.asStateFlow()

    private val _catalogStatus = MutableStateFlow("")
    val catalogStatus: StateFlow<String> = _catalogStatus.asStateFlow()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()

    private val catalogTick = MutableStateFlow(0)
    private var lastCatalog = 0L
    private var reloadJob: Job? = null

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
        if (lastCatalog > 0 && SystemClock.elapsedRealtime() - lastCatalog > CATALOG_TTL_MS) {
            catalogTick.value = catalogTick.value + 1
        }
    }

    fun goHome() {
        _homeTick.value = _homeTick.value + 1
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

    fun openDetail(title: TmdbTitle, app: AppEntry?) {
        _detail.value = DetailState(title, listOfNotNull(app), resolving = true)
        val key = prefs.settings.value.tmdbKey.trim()
        viewModelScope.launch {
            val names = if (key.isBlank()) emptyList() else safe { TmdbClient(key).watchProviders(title.type, title.id) }
            val apps = _apps.value
            val found = (listOfNotNull(app) + names.mapNotNull { matchApp(it, apps) }).distinctBy { it.packageName }
            val current = _detail.value
            if (current != null && current.title.key == title.key) {
                _detail.value = current.copy(apps = found, resolving = false)
            }
        }
    }

    fun refreshCatalog() {
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
        if (canSearch && start(search, quiet = true)) return

        val clipboard = ctx.getSystemService(ClipboardManager::class.java)
        runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("Titolo", title.title)) }
        launch(app)
        toast("«${title.title}» copiato: incollalo nella ricerca di ${app.label}")
    }

    fun watchTrailer(title: TmdbTitle) {
        searchYouTube("${title.title} ${title.year} trailer ita".trim())
    }

    // ---- Avvio app e contenuti ----

    fun launch(app: AppEntry) {
        val component = ComponentName.unflattenFromString(app.key)
        val ok = component != null && runCatching {
            launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
        }.isSuccess
        if (!ok) launchPackage(app.packageName)
    }

    fun resume(item: ContinueItem) {
        val controller = MediaListenerService.active[item.packageName]
        val pending = controller?.sessionActivity
        val sent = pending != null && runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pending.send(ctx, 0, null, null, null, null, options.toBundle())
            } else {
                pending.send()
            }
        }.isSuccess
        if (!sent) launchPackage(item.packageName)
        if (controller != null && controller.playbackState?.state != PlaybackState.STATE_PLAYING) {
            runCatching { controller.transportControls.play() }
        }
    }

    private fun launchPackage(pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast("App non disponibile")
            return
        }
        start(intent)
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

    private fun start(intent: Intent, quiet: Boolean = false): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }
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
        const val MAX_PROVIDER_ROWS = 6
        const val CATALOG_TTL_MS = 6L * 60 * 60 * 1000

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
            "com.spotify.music",
            "deezer.android.app",
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
