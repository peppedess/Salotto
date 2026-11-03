package it.peppedess.salotto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.peppedess.salotto.data.Profile
import it.peppedess.salotto.data.Weather
import it.peppedess.salotto.ui.components.MenuEntry
import it.peppedess.salotto.ui.components.icon
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pannello rapido che scorre da destra, come quello di Google TV. */
@Composable
internal fun QuickPanel(
    visible: Boolean,
    vm: HomeViewModel,
    weather: Weather?,
    profile: Profile,
    sleepEndsAt: Long,
    kiosk: Boolean,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
    onSleep: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180)),
        ) {
            PanelContent(
                vm = vm,
                weather = weather,
                profile = profile,
                sleepEndsAt = sleepEndsAt,
                kiosk = kiosk,
                onSettings = onSettings,
                onProfiles = onProfiles,
                onSleep = onSleep,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun PanelContent(
    vm: HomeViewModel,
    weather: Weather?,
    profile: Profile,
    sleepEndsAt: Long,
    kiosk: Boolean,
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
    onSleep: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { first.requestFocus() }
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val volume = remember { vm.volume() }
    var level by remember { mutableFloatStateOf(volume.first.toFloat()) }
    val max = volume.second.coerceAtLeast(1)

    Column(
        Modifier
            .width(330.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
            .background(Color(0xFF1A1E26))
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        weather?.let { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(w.icon(), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${w.temperature}° · ${w.description} · ${w.city}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Slider(
                value = level,
                onValueChange = {
                    level = it
                    vm.setVolume(it.toInt())
                },
                valueRange = 0f..max.toFloat(),
                modifier = Modifier.weight(1f),
            )
        }

        Box(Modifier.focusRequester(first)) {
            MenuEntry(Icons.Rounded.Settings, "Impostazioni di Salotto", onClick = onSettings)
        }
        MenuEntry(Icons.Rounded.Wifi, "Wi‑Fi") { vm.openWifi() }
        MenuEntry(Icons.Rounded.Bluetooth, "Bluetooth") { vm.openBluetoothSettings() }
        MenuEntry(Icons.Rounded.SpeakerGroup, "Uscita audio") { vm.openOutputSwitcher() }
        MenuEntry(
            Icons.Rounded.Bedtime,
            if (sleepEndsAt > System.currentTimeMillis()) {
                "Timer: ${((sleepEndsAt - System.currentTimeMillis()) / 60_000L) + 1} min"
            } else {
                "Timer di spegnimento"
            },
            onClick = onSleep,
        )
        MenuEntry(Icons.Rounded.AccountCircle, "Profilo: ${profile.name}", onClick = onProfiles)
        MenuEntry(Icons.Rounded.Wallpaper, "Salvaschermo") {
            onDismiss()
            vm.startScreensaver()
        }
        if (!kiosk) {
            MenuEntry(Icons.Rounded.PhoneAndroid, "Impostazioni di Android") { vm.openSystemSettings() }
        }
    }
}
