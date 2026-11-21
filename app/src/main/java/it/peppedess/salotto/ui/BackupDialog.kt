package it.peppedess.salotto.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import it.peppedess.salotto.data.Backup
import it.peppedess.salotto.remote.RemoteServer
import it.peppedess.salotto.ui.components.LocalNarrow
import it.peppedess.salotto.ui.components.MenuEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class BackupStep { Menu, Qr, Confirm }

/**
 * Backup e ripristino delle impostazioni: QR da inquadrare con un altro telefono
 * (lo scanner di Google, senza permesso della fotocamera) oppure un file.
 */
@Composable
fun BackupDialog(vm: HomeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(BackupStep.Menu) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrLists by remember { mutableStateOf(true) }
    var qrTooBig by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun received(raw: String?) {
        val json = raw?.let { Backup.decode(it) }
        if (json == null) {
            message = "Questo non è un backup di Salotto."
        } else {
            pending = json
            step = BackupStep.Confirm
        }
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupJson().toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            message = if (ok) "Backup salvato. Aprilo da Salotto sull'altro telefono con «Apri un file di backup»." else "Salvataggio non riuscito."
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().take(3_000_000).toByteArray().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            received(text)
        }
    }

    LaunchedEffect(step) {
        if (step != BackupStep.Qr) return@LaunchedEffect
        qr = null
        val result = withContext(Dispatchers.Default) {
            vm.backupQr()?.let { b -> b to RemoteServer.qr(b.text, 900).asImageBitmap() }
        }
        if (result == null) {
            qrTooBig = true
        } else {
            qrLists = result.first.withLists
            qr = result.second
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SettingsBackupRestore, null) },
        title = {
            Text(
                when (step) {
                    BackupStep.Menu -> "Backup e ripristino"
                    BackupStep.Qr -> "Inquadra con l'altro telefono"
                    BackupStep.Confirm -> "Ripristinare le impostazioni?"
                }
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (step) {
                    BackupStep.Menu -> {
                        Text(
                            "Porta su un altro telefono profili, preferite, La mia lista, chiave TMDB, Home Assistant ed esperienza TV.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MenuEntry(Icons.Rounded.QrCode2, "Mostra il QR del backup") { step = BackupStep.Qr }
                        MenuEntry(Icons.Rounded.QrCodeScanner, "Inquadra il QR di un altro telefono") {
                            message = null
                            val options = GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            runCatching {
                                GmsBarcodeScanning.getClient(context, options).startScan()
                                    .addOnSuccessListener { code -> received(code.rawValue) }
                                    .addOnFailureListener {
                                        message = "Scanner non disponibile su questo telefono: usa il file di backup."
                                    }
                            }.onFailure {
                                message = "Scanner non disponibile su questo telefono: usa il file di backup."
                            }
                        }
                        MenuEntry(Icons.Rounded.Download, "Salva un file di backup") {
                            saveFile.launch("salotto-backup-${LocalDate.now()}.json")
                        }
                        MenuEntry(Icons.Rounded.FolderOpen, "Apri un file di backup") {
                            openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                        }
                    }
                    BackupStep.Qr -> {
                        val qrBox: @Composable () -> Unit = {
                            Box(
                                Modifier
                                    .size(250.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val image = qr
                                when {
                                    image != null -> Image(image, "QR del backup", Modifier.size(230.dp))
                                    qrTooBig -> Text("Troppe impostazioni per un QR", color = Color.Black)
                                    else -> CircularProgressIndicator()
                                }
                            }
                        }
                        val notes: @Composable () -> Unit = {
                            Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Sull'altro telefono: Salotto → Impostazioni → Backup e ripristino → Inquadra il QR (oppure dalla prima schermata della configurazione).",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (!qrLists || qrTooBig) {
                                    Text(
                                        "La mia lista non entra nel QR: per portarla usa il file di backup.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Text(
                                    "Il QR contiene le tue chiavi (TMDB, Home Assistant) e il PIN: non mostrarlo ad altri.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (LocalNarrow.current) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                qrBox()
                                notes()
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                qrBox()
                                notes()
                            }
                        }
                    }
                    BackupStep.Confirm -> {
                        val count = pending?.let { Backup.count(it) } ?: 0
                        Text(
                            "Il backup contiene $count impostazioni. Quelle di questo telefono con lo stesso nome verranno sostituite e Salotto si riavvierà.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "I permessi (accesso alle notifiche, Servizio TV, Home predefinita) vanno concessi di nuovo su questo telefono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            when (step) {
                BackupStep.Confirm -> TextButton(onClick = { pending?.let { vm.restoreBackup(it) } }) { Text("Ripristina") }
                else -> TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        },
        dismissButton = {
            if (step != BackupStep.Menu) {
                TextButton(onClick = {
                    step = BackupStep.Menu
                    pending = null
                }) { Text("Indietro") }
            }
        },
    )
}
