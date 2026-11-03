package it.peppedess.salotto.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.ui.components.PinDialog
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.theme.Bg

@Composable
fun SettingsScreen(vm: HomeViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val access by vm.notificationAccess.collectAsStateWithLifecycle()
    val owner by vm.deviceOwner.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var pinDialog by remember { mutableStateOf(false) }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
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
            TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
            Spacer(Modifier.width(16.dp))
            Text(
                "Impostazioni",
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
            message?.let { msg ->
                item(key = "message") {
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            item { SectionTitle("Schermo") }
            item {
                SettingRow(
                    icon = Icons.Rounded.LightMode,
                    title = "Schermo sempre acceso",
                    subtitle = "Utile quando è collegato alla TV o sul supporto",
                    checked = settings.keepScreenOn,
                    onClick = { vm.updateSettings { s -> s.copy(keepScreenOn = !s.keepScreenOn) } },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Rounded.ScreenRotation,
                    title = "Consenti verticale",
                    subtitle = "Di base Salotto resta in orizzontale, come una TV",
                    checked = settings.allowPortrait,
                    onClick = { vm.updateSettings { s -> s.copy(allowPortrait = !s.allowPortrait) } },
                )
            }

            item { SectionTitle("Continua a guardare") }
            item {
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Accesso alle notifiche",
                    subtitle = if (access) {
                        "Attivo: Salotto vede cosa stai riproducendo"
                    } else {
                        "Non attivo: tocca per attivarlo. Se è bloccato, in Info app → ⋮ → Consenti impostazioni con restrizioni"
                    },
                    onClick = { vm.openNotificationAccess() },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Svuota \"Continua a guardare\"",
                    subtitle = "Rimuove tutti i contenuti recenti",
                    onClick = {
                        vm.clearContinue()
                        message = "Elenco svuotato."
                    },
                )
            }

            item { SectionTitle("Kiosk") }
            item {
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = "Modalità Kiosk",
                    subtitle = if (owner) {
                        "Blocca barre, recenti e notifiche: si usano solo Salotto e le sue app"
                    } else {
                        "Senza proprietario del dispositivo: schermo acceso e impostazioni protette dal PIN"
                    },
                    checked = settings.kiosk,
                    onClick = {
                        val enable = !settings.kiosk
                        vm.updateSettings { s -> s.copy(kiosk = enable, keepScreenOn = if (enable) true else s.keepScreenOn) }
                        if (enable && settings.pin.isEmpty()) {
                            message = "Kiosk attivo. Imposta un PIN per proteggere le impostazioni."
                        }
                    },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Rounded.Password,
                    title = "PIN delle impostazioni",
                    subtitle = if (settings.pin.isEmpty()) "Nessun PIN" else "PIN impostato: tocca per cambiarlo",
                    onClick = { pinDialog = true },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = "Proprietario del dispositivo",
                    subtitle = if (owner) {
                        "Attivo: tocca per rimuoverlo"
                    } else {
                        "Non attivo: tocca per attivarlo con il root (serve un telefono senza account)"
                    },
                    onClick = {
                        if (owner) {
                            vm.removeOwner { message = it }
                        } else {
                            message = "Richiesta root in corso…"
                            vm.becomeOwner { message = it }
                        }
                    },
                )
            }

            item { SectionTitle("Sistema") }
            item {
                SettingRow(
                    icon = Icons.Rounded.Home,
                    title = "Launcher predefinito",
                    subtitle = "Scegli Salotto come schermata Home",
                    onClick = { vm.openHomeSettings() },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Rounded.Settings,
                    title = "Impostazioni di Android",
                    subtitle = if (settings.kiosk && owner) "Disattiva il Kiosk per aprirle" else "Apri le impostazioni di sistema",
                    onClick = { vm.openSystemSettings() },
                )
            }
            item {
                SettingRow(
                    icon = Icons.Rounded.Info,
                    title = "Salotto",
                    subtitle = "Versione $version",
                    onClick = {},
                )
            }
        }
    }

    if (pinDialog) {
        PinDialog(
            title = "Nuovo PIN (vuoto per toglierlo)",
            confirmLabel = "Salva",
            error = false,
            onConfirm = { newPin ->
                vm.updateSettings { s -> s.copy(pin = newPin) }
                message = if (newPin.isEmpty()) "PIN rimosso." else "PIN salvato."
                pinDialog = false
            },
            onDismiss = { pinDialog = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    checked: Boolean? = null,
) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        shape = RoundedCornerShape(20.dp),
        focusScale = 1.015f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
            }
            if (checked != null) {
                Spacer(Modifier.width(12.dp))
                Switch(checked = checked, onCheckedChange = null)
            }
        }
    }
}
