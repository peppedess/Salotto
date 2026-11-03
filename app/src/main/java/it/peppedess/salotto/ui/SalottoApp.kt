package it.peppedess.salotto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.ui.components.MenuEntry
import it.peppedess.salotto.ui.components.PinDialog

enum class Screen { Home, Search, Settings }

@Composable
fun SalottoApp(vm: HomeViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var searchWithVoice by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()

    LaunchedEffect(homeTick) {
        if (homeTick > 0) screen = Screen.Home
    }

    val openSettings: () -> Unit = {
        if (settings.kiosk && settings.pin.isNotEmpty()) {
            pinError = false
            pinPrompt = true
        } else {
            screen = Screen.Settings
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
        },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onSearch = { voice ->
                    searchWithVoice = voice
                    screen = Screen.Search
                },
                onSettings = openSettings,
                onAppMenu = { menuApp = it },
            )

            Screen.Search -> SearchScreen(
                vm = vm,
                startWithVoice = searchWithVoice,
                onClose = { screen = Screen.Home },
            )

            Screen.Settings -> SettingsScreen(
                vm = vm,
                onClose = { screen = Screen.Home },
            )
        }
    }

    if (pinPrompt) {
        PinDialog(
            title = "Inserisci il PIN",
            confirmLabel = "Sblocca",
            error = pinError,
            onConfirm = { pin ->
                if (pin == settings.pin) {
                    pinPrompt = false
                    screen = Screen.Settings
                } else {
                    pinError = true
                }
            },
            onDismiss = { pinPrompt = false },
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
