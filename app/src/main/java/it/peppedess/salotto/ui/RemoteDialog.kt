package it.peppedess.salotto.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsRemote
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.remote.RemoteServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** QR code del telecomando: lo inquadri con un altro telefono e si apre la pagina con i comandi. */
@Composable
internal fun RemoteDialog(vm: HomeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val running by RemoteServer.running.collectAsStateWithLifecycle()
    val tvService by vm.tvServiceEnabled.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        vm.setRemoteEnabled(true)
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        delay(150)
        val address = RemoteServer.address(context)
        url = address
        qr = address?.let { a -> withContext(Dispatchers.Default) { RemoteServer.qr(a).asImageBitmap() } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SettingsRemote, null) },
        title = { Text("Telecomando dal telefono") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = qr
                    if (image != null) {
                        Image(image, "QR code del telecomando", Modifier.size(164.dp))
                    } else {
                        Text(if (running) "…" else "Avvio…", color = Color.Black)
                    }
                }
                Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Inquadra il codice con la fotocamera di un altro telefono collegato alla stessa rete Wi‑Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    url?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                    if (url == null && running) {
                        Text(
                            "Collega questo telefono al Wi‑Fi: il telecomando funziona solo sulla rete di casa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (tvService) {
                            "Con il Servizio TV attivo il telecomando funziona anche dentro le altre app."
                        } else {
                            "Il telecomando comanda Salotto. Per usarlo anche dentro le altre app attiva il Servizio TV."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        dismissButton = {
            TextButton(onClick = {
                vm.setRemoteEnabled(false)
                onDismiss()
            }) { Text("Disattiva") }
        },
    )
}
