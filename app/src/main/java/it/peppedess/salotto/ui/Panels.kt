package it.peppedess.salotto.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ChannelGuide
import it.peppedess.salotto.data.HaEntity
import it.peppedess.salotto.data.Playlist
import it.peppedess.salotto.data.Profile
import it.peppedess.salotto.ui.components.MenuEntry
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import kotlinx.coroutines.delay

/** Volume, uscita audio e casse di casa. */
@Composable
internal fun AudioDialog(vm: HomeViewModel, speakers: List<HaEntity>, onDismiss: () -> Unit) {
    val initial = remember { vm.volume() }
    var level by remember { mutableFloatStateOf(initial.first.toFloat()) }
    var muted by remember { mutableStateOf(vm.isMuted()) }
    val max = initial.second.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
        title = { Text("Audio") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Volume di questo dispositivo", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIconButton(
                        if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        "Muto",
                        onClick = {
                            vm.toggleMute()
                            muted = !muted
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = level,
                        onValueChange = {
                            level = it
                            vm.setVolume(it.toInt())
                        },
                        valueRange = 0f..max.toFloat(),
                        steps = (max - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f),
                    )
                }
                MenuEntry(Icons.Rounded.SpeakerGroup, "Scegli l'uscita audio") { vm.openOutputSwitcher() }
                MenuEntry(Icons.Rounded.Bluetooth, "Impostazioni Bluetooth") { vm.openBluetoothSettings() }
                if (speakers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Casse di casa", style = MaterialTheme.typography.labelLarge)
                    speakers.forEach { speaker ->
                        SpeakerVolume(speaker) { v -> vm.setSpeakerVolume(speaker, v) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
private fun SpeakerVolume(speaker: HaEntity, onChange: (Float) -> Unit) {
    var value by remember(speaker.id) { mutableFloatStateOf(speaker.volume ?: 0f) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speaker, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(speaker.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(speaker.stateLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value) },
            valueRange = 0f..1f,
            enabled = speaker.volume != null,
        )
    }
}

/** Timer di spegnimento. */
@Composable
internal fun SleepDialog(
    endsAt: Long,
    adminActive: Boolean,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onEnableAdmin: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = { Text("Timer di spegnimento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (endsAt > now) {
                    val left = (endsAt - now) / 1000
                    Text(
                        "Si spegne tra ${left / 60}:${(left % 60).toString().padStart(2, '0')}: metto in pausa e spengo lo schermo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MenuEntry(Icons.Rounded.Close, "Annulla il timer") {
                        onCancel()
                        onDismiss()
                    }
                } else {
                    Text("Allo scadere metto in pausa e spengo lo schermo.", style = MaterialTheme.typography.bodyMedium)
                }
                listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                    MenuEntry(Icons.Rounded.Bedtime, if (minutes < 60) "$minutes minuti" else "${minutes / 60} or${if (minutes == 60) "a" else "e"}" + if (minutes % 60 != 0) " e ${minutes % 60} minuti" else "") {
                        onStart(minutes)
                        onDismiss()
                    }
                }
                if (!adminActive) {
                    Text(
                        "Per spegnere lo schermo senza root, attiva Salotto come amministratore del dispositivo.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    MenuEntry(Icons.Rounded.PhoneAndroid, "Attiva lo spegnimento dello schermo") { onEnableAdmin() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

/** Dove riprodurre una playlist: su questo dispositivo o su una cassa di casa. */
@Composable
internal fun PlaylistDialog(
    playlist: Playlist,
    speakers: List<HaEntity>,
    onPick: (HaEntity?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuEntry(Icons.Rounded.PhoneAndroid, "Su questo dispositivo (Deezer)") { onPick(null) }
                speakers.forEach { speaker ->
                    MenuEntry(Icons.Rounded.Speaker, "Su ${speaker.name}") { onPick(speaker) }
                }
                if (speakers.isEmpty()) {
                    Text(
                        "Per suonarla sulle casse di casa scegli le casse nelle impostazioni (serve Music Assistant in Home Assistant).",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

/** Palinsesto di un canale. */
@Composable
internal fun ChannelDialog(guide: ChannelGuide, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val now = System.currentTimeMillis()
    val current = guide.current(now)
    val upcoming = guide.upcoming(now, 5)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Tv, null) },
        title = { Text("${guide.channel.number} · ${guide.channel.name}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (current != null) {
                    Text("In onda · ${formatTime(current.start)}–${formatTime(current.stop)}", style = MaterialTheme.typography.labelMedium)
                    Text(current.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (current.description.isNotBlank()) {
                        Text(current.description, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (upcoming.isNotEmpty()) {
                    Text("Più tardi", style = MaterialTheme.typography.labelMedium)
                    upcoming.forEach { p ->
                        Row {
                            Text(formatTime(p.start), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (current == null && upcoming.isEmpty()) {
                    Text("Guida non disponibile per questo canale.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onWatch) { Text("Guarda in diretta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

/** Scelta del profilo. */
@Composable
internal fun ProfilePickerDialog(
    profiles: List<Profile>,
    current: Profile,
    onPick: (Profile) -> Unit,
    onManage: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Group, null) },
        title = { Text("Chi sta guardando?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { p ->
                    TvCard(
                        onClick = { onPick(p) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        focusScale = 1.02f,
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    when {
                                        focused -> Color.White.copy(alpha = 0.2f)
                                        p.id == current.id -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else -> Color.White.copy(alpha = 0.06f)
                                    }
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(p, 36)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                if (p.kids) Text("Bambini", style = MaterialTheme.typography.labelSmall)
                            }
                            if (p.id == current.id) Text("In uso", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (onManage != null) {
                    MenuEntry(Icons.Rounded.Group, "Gestisci i profili") { onManage() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
internal fun ProfileAvatar(profile: Profile, sizeDp: Int) {
    val color = if (profile.kids) Color(0xFFFFB74D) else profileColor(profile.id)
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.initial,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            style = if (sizeDp >= 40) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
        )
    }
}

private fun profileColor(id: String): Color {
    val palette = listOf(0xFF8AB4F8, 0xFF81C995, 0xFFF28B82, 0xFFC58AF9, 0xFF78D9EC, 0xFFFDD663)
    return Color(palette[(id.hashCode() and 0x7fffffff) % palette.size])
}
