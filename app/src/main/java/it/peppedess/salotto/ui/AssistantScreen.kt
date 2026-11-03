package it.peppedess.salotto.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.ChatMessage
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.ui.components.MorphMic
import it.peppedess.salotto.ui.components.PosterCard
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.theme.Bg

private val SUGGESTIONS = listOf(
    "Cosa guardo stasera?",
    "Un film breve che faccia ridere",
    "Una serie come quelle della mia lista",
    "Spegni le luci",
)

/** Assistente con l'AI: consigli su cosa guardare e controllo della casa, anche a voce. */
@Composable
fun AssistantScreen(
    vm: HomeViewModel,
    startWithVoice: Boolean,
    onClose: () -> Unit,
    onOpenTitle: (TmdbTitle) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.assistantBusy.collectAsStateWithLifecycle()
    val titles by vm.assistantTitles.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }

    val voice = remember {
        VoiceInput(
            context = context.applicationContext,
            onListening = { on ->
                listening = on
                if (on) partial = ""
            },
            onLevel = { v -> level = v },
            onPartial = { text -> partial = text },
            onResult = { text -> vm.ask(text) },
            onFailure = { message -> input = ""; partial = message },
        )
    }
    DisposableEffect(Unit) {
        onDispose { voice.stop() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start()
    }
    val startVoice: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            voice.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(Unit) {
        if (startWithVoice) startVoice()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(chat.size, titles.size) {
        val last = chat.size + (if (titles.isNotEmpty()) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    val send: () -> Unit = {
        val text = input.trim()
        if (text.isNotEmpty()) {
            vm.ask(text)
            input = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.ime))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
            Spacer(Modifier.size(14.dp))
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFB39DFF))
            Spacer(Modifier.size(8.dp))
            Text(
                "Assistente",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (chat.isNotEmpty()) {
                TvIconButton(Icons.Rounded.DeleteSweep, "Nuova conversazione", onClick = vm::clearChat)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (chat.isEmpty()) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Chiedimi cosa guardare, cerca un film per descrizione o fammi accendere le luci.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SUGGESTIONS.forEach { s -> TvPill(text = s, onClick = { vm.ask(s) }) }
                        }
                    }
                }
            }
            items(chat.size) { i -> Bubble(chat[i]) }
            if (titles.isNotEmpty()) {
                item(key = "titles") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(titles, key = { it.key }) { t ->
                            PosterCard(t, DefaultAmbient, onClick = { onOpenTitle(t) }, onFocus = {})
                        }
                    }
                }
            }
            if (busy) {
                item(key = "busy") {
                    LinearProgressIndicator(Modifier.widthIn(max = 220.dp).fillMaxWidth())
                }
            }
        }

        if (listening) {
            Text(
                partial.ifBlank { "Ti ascolto…" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Scrivi una domanda…") },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Spacer(Modifier.size(10.dp))
            TvCard(
                onClick = {
                    if (listening) {
                        voice.stop()
                        listening = false
                    } else {
                        startVoice()
                    }
                },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                focusScale = 1.12f,
            ) {
                MorphMic(
                    listening = listening,
                    level = level,
                    color = if (listening) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                    iconTint = if (listening) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.size(8.dp))
            TvIconButton(Icons.AutoMirrored.Rounded.Send, "Invia", onClick = send)
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (message.fromUser) Color.Black else Color.White,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (message.fromUser) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.09f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
