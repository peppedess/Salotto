package it.peppedess.salotto.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction)

@Composable
fun rememberFileBitmap(path: String?, version: Long): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path, version) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return state.value
}

@Composable
fun Clock(modifier: Modifier = Modifier) {
    val format = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    Text(
        text = now.format(format),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = modifier,
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    help: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> value = v.trim() },
                singleLine = true,
                label = { Text(label) },
                supportingText = { Text(help) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun PinDialog(
    title: String,
    confirmLabel: String,
    error: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { c -> c.isDigit() }.take(8) },
                singleLine = true,
                label = { Text("PIN") },
                isError = error,
                supportingText = { if (error) Text("PIN errato") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
