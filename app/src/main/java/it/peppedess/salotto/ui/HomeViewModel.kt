package it.peppedess.salotto.ui

import android.app.ActivityOptions
import android.app.Application
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
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
import it.peppedess.salotto.kiosk.Kiosk
import it.peppedess.salotto.service.MediaListenerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    }

    fun goHome() {
        _homeTick.value = _homeTick.value + 1
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

    fun isFavorite(app: AppEntry): Boolean = prefs.favorites.value.contains(app.key)

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

    fun openAppInfo(app: AppEntry) = start(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
    )

    fun uninstall(app: AppEntry) = start(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
    )

    // ---- Ricerca ----

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
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        runCatching { launcherApps.unregisterCallback(packageCallback) }
        super.onCleared()
    }

    private companion object {
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
    }
}
