package it.peppedess.salotto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.Profile
import it.peppedess.salotto.ui.components.TextInputDialog
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.theme.Bg

/** Gestione dei profili: nomi, profili bambini, limite di tempo e app consentite. */
@Composable
fun ProfilesScreen(vm: HomeViewModel, onClose: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Profile?>(null) }

    val editing = profiles.firstOrNull { it.id == editingId }
    BackHandler {
        if (editing != null) {
            editingId = null
        } else {
            onClose()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                "Indietro",
                onClick = {
                    if (editing != null) {
                        editingId = null
                    } else {
                        onClose()
                    }
                },
            )
            Spacer(Modifier.width(16.dp))
            Text(
                editing?.name ?: "Profili",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (editing == null) {
                items(profiles, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ProfileRow(
                            leading = { ProfileAvatar(p, 40) },
                            title = p.name,
                            subtitle = when {
                                p.kids && p.dailyLimitMin > 0 -> "Bambini · ${p.allowedApps.size} app · ${formatMinutes(p.dailyLimitMin)} al giorno"
                                p.kids -> "Bambini · ${p.allowedApps.size} app · senza limite"
                                p.id == Profile.MAIN -> "Profilo principale"
                                else -> "Adulto"
                            },
                            onClick = { editingId = p.id },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "add") {
                    ProfileRow(
                        leading = { Icon(Icons.Rounded.Add, null, tint = Color.White) },
                        title = "Aggiungi un profilo",
                        subtitle = "Con preferite e lista separate",
                        onClick = {
                            val p = Profile(Profile.newId(), "Ospite")
                            vm.saveProfile(p)
                            editingId = p.id
                        },
                    )
                }
                item(key = "add-kids") {
                    ProfileRow(
                        leading = { Icon(Icons.Rounded.ChildCare, null, tint = Color.White) },
                        title = "Aggiungi un profilo bambini",
                        subtitle = "Solo le app che scegli, con un limite di tempo al giorno",
                        onClick = {
                            val p = Profile(Profile.newId(), "Bambini", kids = true, dailyLimitMin = 60)
                            vm.saveProfile(p)
                            editingId = p.id
                        },
                    )
                }
                item(key = "note") {
                    Text(
                        "Per uscire da un profilo bambini serve il PIN delle impostazioni, se l'hai impostato.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            } else {
                item(key = "name") {
                    ProfileRow(
                        leading = { Icon(Icons.Rounded.Edit, null, tint = Color.White) },
                        title = "Nome",
                        subtitle = editing.name,
                        onClick = { renaming = editing },
                    )
                }
                if (editing.id != Profile.MAIN) {
                    item(key = "kids") {
                        ProfileRow(
                            leading = { Icon(Icons.Rounded.ChildCare, null, tint = Color.White) },
                            title = "Profilo bambini",
                            subtitle = "Solo le app consentite, niente impostazioni",
                            trailing = { Switch(checked = editing.kids, onCheckedChange = null) },
                            onClick = { vm.saveProfile(editing.copy(kids = !editing.kids)) },
                        )
                    }
                }
                if (editing.kids) {
                    item(key = "limit") {
                        ProfileRow(
                            leading = { Icon(Icons.Rounded.Timer, null, tint = Color.White) },
                            title = "Tempo al giorno",
                            subtitle = if (editing.dailyLimitMin == 0) "Senza limite (tocca per cambiare)" else "${formatMinutes(editing.dailyLimitMin)} (tocca per cambiare)",
                            onClick = {
                                val steps = listOf(0, 30, 60, 90, 120, 180)
                                val next = steps[(steps.indexOf(editing.dailyLimitMin) + 1).mod(steps.size)]
                                vm.saveProfile(editing.copy(dailyLimitMin = next))
                            },
                        )
                    }
                    item(key = "apps-title") {
                        Text(
                            "App consentite",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    items(apps, key = { "app:" + it.key }) { app ->
                        val checked = app.packageName in editing.allowedApps
                        ProfileRow(
                            leading = { Image(app.icon, null, Modifier.size(32.dp)) },
                            title = app.label,
                            subtitle = if (checked) "Consentita" else "",
                            trailing = {
                                Icon(
                                    if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    null,
                                    tint = if (checked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                )
                            },
                            onClick = {
                                val list = if (checked) editing.allowedApps - app.packageName else editing.allowedApps + app.packageName
                                vm.saveProfile(editing.copy(allowedApps = list))
                            },
                        )
                    }
                }
                if (editing.id != Profile.MAIN) {
                    item(key = "delete") {
                        ProfileRow(
                            leading = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF8A80)) },
                            title = "Elimina il profilo",
                            subtitle = "Preferite e lista del profilo vengono cancellate",
                            onClick = {
                                vm.deleteProfile(editing.id)
                                editingId = null
                            },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { p ->
        TextInputDialog(
            title = "Nome del profilo",
            label = "Nome",
            initial = p.name,
            help = "Ad esempio Giuseppe, Ospiti o il nome di un bambino.",
            onConfirm = { name ->
                if (name.isNotBlank()) vm.saveProfile(p.copy(name = name))
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun ProfileRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    trailing: (@Composable () -> Unit)? = null,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        focusScale = 1.01f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
            }
            trailing?.invoke()
        }
    }
}

