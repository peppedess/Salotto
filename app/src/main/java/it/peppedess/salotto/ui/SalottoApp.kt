package it.peppedess.salotto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import it.peppedess.salotto.ui.components.LocalScreenScope
import it.peppedess.salotto.ui.components.LocalSharedScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.ui.components.MenuEntry
import it.peppedess.salotto.ui.components.PinDialog
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.theme.Bg

enum class Screen { Home, Search, Settings, Detail, HaEntities, Stats, Profiles }

/** Azione che richiede il PIN prima di essere eseguita. */
private sealed interface PinAction {
    data object Settings : PinAction
    data class SwitchProfile(val id: String) : PinAction
    data object KidsExtra : PinAction
    data object KidsSwitch : PinAction
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SalottoApp(vm: HomeViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var searchWithVoice by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf<PinAction?>(null) }
    var pinError by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var detailReturn by rememberSaveable { mutableStateOf(Screen.Home) }
    var showProfiles by remember { mutableStateOf(false) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val screensaver by vm.screensaver.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val kidsLocked by vm.kidsLocked.collectAsStateWithLifecycle()

    LaunchedEffect(homeTick) {
        if (homeTick > 0) screen = Screen.Home
    }
    LaunchedEffect(screen) {
        vm.setOnHome(screen == Screen.Home)
    }

    /** Esegue subito se non serve il PIN, altrimenti lo chiede. */
    fun guarded(action: PinAction, needsPin: Boolean, run: () -> Unit) {
        if (needsPin && settings.pin.isNotEmpty()) {
            pinError = false
            pinAction = action
        } else {
            run()
        }
    }

    fun perform(action: PinAction) {
        when (action) {
            PinAction.Settings -> screen = Screen.Settings
            is PinAction.SwitchProfile -> {
                vm.switchProfile(action.id)
                showProfiles = false
            }
            PinAction.KidsExtra -> vm.grantKidsExtra(30)
            PinAction.KidsSwitch -> showProfiles = true
        }
    }

    val openSettings: () -> Unit = {
        guarded(PinAction.Settings, needsPin = settings.kiosk || profile.kids) { perform(PinAction.Settings) }
    }

    if (!kidsLocked) SharedTransitionLayout {
      CompositionLocalProvider(LocalSharedScope provides this) {
        AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val toDetail = targetState == Screen.Detail
            val fromDetail = initialState == Screen.Detail
            when {
                toDetail -> (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { it / 8 }) togetherWith fadeOut(tween(220))
                fromDetail -> fadeIn(tween(280)) togetherWith (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { it / 8 })
                else -> (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
            }
        },
        label = "screen",
    ) { target ->
      CompositionLocalProvider(LocalScreenScope provides this) {
        when (target) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onSearch = { voice ->
                    searchWithVoice = voice
                    screen = Screen.Search
                },
                onSettings = openSettings,
                onAppMenu = { menuApp = it },
                onOpenTitle = { title, app, shareKey ->
                    vm.openDetail(title, app, shareKey)
                    detailReturn = Screen.Home
                    screen = Screen.Detail
                },
                onStats = { screen = Screen.Stats },
                onProfiles = { showProfiles = true },
            )

            Screen.Search -> SearchScreen(
                vm = vm,
                startWithVoice = searchWithVoice,
                onClose = { screen = Screen.Home },
                onOpenTitle = { title ->
                    vm.openDetail(title, null)
                    searchWithVoice = false
                    detailReturn = Screen.Search
                    screen = Screen.Detail
                },
            )

            Screen.Settings -> SettingsScreen(
                vm = vm,
                onClose = { screen = Screen.Home },
                onHaEntities = { screen = Screen.HaEntities },
                onProfiles = { screen = Screen.Profiles },
                onStats = { screen = Screen.Stats },
            )

            Screen.Detail -> DetailScreen(
                vm = vm,
                onClose = { screen = detailReturn },
                onOpenTitle = { title -> vm.openDetail(title, null) },
            )

            Screen.HaEntities -> HaEntitiesScreen(
                vm = vm,
                onClose = { screen = Screen.Settings },
            )

            Screen.Stats -> StatsScreen(
                vm = vm,
                onClose = { screen = Screen.Home },
            )

            Screen.Profiles -> ProfilesScreen(
                vm = vm,
                onClose = { screen = Screen.Settings },
            )
        }
      }
    }
      }
    }

    // Tempo finito per il profilo bambini.
    AnimatedVisibility(visible = kidsLocked, enter = fadeIn(tween(500)), exit = fadeOut(tween(300))) {
        KidsLockScreen(
            name = profile.name,
            onExtra = { guarded(PinAction.KidsExtra, needsPin = true) { perform(PinAction.KidsExtra) } },
            onSwitch = { guarded(PinAction.KidsSwitch, needsPin = true) { perform(PinAction.KidsSwitch) } },
        )
    }

    // Salvaschermo sopra a tutto.
    AnimatedVisibility(
        visible = screensaver,
        enter = fadeIn(tween(1200)),
        exit = fadeOut(tween(400)),
    ) {
        AmbientScreen(vm)
    }

    if (showProfiles) {
        ProfilePickerDialog(
            profiles = profiles,
            current = profile,
            onPick = { p ->
                if (p.id == profile.id) {
                    showProfiles = false
                } else {
                    // Uscire da un profilo bambini richiede il PIN.
                    guarded(PinAction.SwitchProfile(p.id), needsPin = profile.kids) {
                        perform(PinAction.SwitchProfile(p.id))
                    }
                }
            },
            onManage = if (profile.kids) {
                null
            } else {
                {
                    showProfiles = false
                    guarded(PinAction.Settings, needsPin = settings.kiosk) { screen = Screen.Profiles }
                }
            },
            onDismiss = { showProfiles = false },
        )
    }

    pinAction?.let { action ->
        PinDialog(
            title = "Inserisci il PIN",
            confirmLabel = "Sblocca",
            error = pinError,
            onConfirm = { pin ->
                if (pin == settings.pin) {
                    pinAction = null
                    perform(action)
                } else {
                    pinError = true
                }
            },
            onDismiss = { pinAction = null },
        )
    }

    menuApp?.let { app ->
        val isFavorite = favorites.any { it.key == app.key }
        AlertDialog(
            onDismissRequest = { menuApp = null },
            icon = { Image(app.icon, null, Modifier.size(48.dp)) },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuEntry(Icons.Rounded.PlayArrow, "Apri") {
                        vm.launch(app)
                        menuApp = null
                    }
                    MenuEntry(
                        if (isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
                        if (isFavorite) "Rimuovi dalle preferite" else "Aggiungi alle preferite",
                    ) {
                        vm.toggleFavorite(app)
                        menuApp = null
                    }
                    if (isFavorite) {
                        MenuEntry(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Sposta a sinistra") {
                            vm.moveFavorite(app, -1)
                        }
                        MenuEntry(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Sposta a destra") {
                            vm.moveFavorite(app, 1)
                        }
                    }
                    if (!settings.kiosk) {
                        MenuEntry(Icons.Rounded.Info, "Info app") {
                            vm.openAppInfo(app)
                            menuApp = null
                        }
                        MenuEntry(Icons.Rounded.Delete, "Disinstalla") {
                            vm.uninstall(app)
                            menuApp = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuApp = null }) { Text("Chiudi") }
            },
        )
    }
}

@Composable
private fun KidsLockScreen(name: String, onExtra: () -> Unit, onSwitch: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.HourglassEmpty, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                "Per oggi il tempo è finito, $name!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ci vediamo domani. Un adulto può aggiungere altro tempo.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPill(text = "+30 minuti", icon = Icons.Rounded.Add, primary = true, onClick = onExtra)
                TvPill(text = "Cambia profilo", icon = Icons.Rounded.Group, onClick = onSwitch)
            }
        }
    }
}
