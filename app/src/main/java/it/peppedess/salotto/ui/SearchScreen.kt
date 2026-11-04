package it.peppedess.salotto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.ui.components.AppCard
import it.peppedess.salotto.ui.components.PosterCard
import kotlinx.coroutines.delay
import it.peppedess.salotto.ui.components.ContinueCard
import it.peppedess.salotto.ui.components.MorphMic
import it.peppedess.salotto.ui.components.ShelfRow
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.darken
import it.peppedess.salotto.ui.theme.Bg

private data class SearchAction(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val run: (String) -> Unit,
)

@Composable
fun SearchScreen(
    vm: HomeViewModel,
    startWithVoice: Boolean,
    onClose: () -> Unit,
    onOpenTitle: (TmdbTitle) -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    val media by vm.continueItems.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf(vm.consumePendingSearch() ?: "") }
    // Testo inviato dal telecomando mentre la ricerca è già aperta.
    val remoteQuery by vm.pendingSearch.collectAsStateWithLifecycle()
    LaunchedEffect(remoteQuery) {
        if (remoteQuery != null) vm.consumePendingSearch()?.let { query = it }
    }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    val voice = remember {
        VoiceInput(
            context = context.applicationContext,
            onListening = { on ->
                listening = on
                if (on) {
                    partial = ""
                    error = null
                }
            },
            onLevel = { v -> level = v },
            onPartial = { text -> partial = text },
            onResult = { text -> query = text },
            onFailure = { message -> error = message },
        )
    }
    DisposableEffect(Unit) {
        onDispose { voice.stop() }
    }

    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { query = it }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voice.start()
        } else {
            error = "Serve il permesso del microfono"
        }
    }
    val startVoice: () -> Unit = {
        if (!voice.isAvailable()) {
            val ok = runCatching { fallback.launch(VoiceInput.recognizerIntent()) }.isSuccess
            if (!ok) {
                error = "Riconoscimento vocale non disponibile"
            }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
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

    val q = (if (listening) partial else query).trim()
    val matchedApps = remember(q, apps) {
        if (q.isBlank()) emptyList() else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val matchedMedia = remember(q, media) {
        if (q.isBlank()) {
            emptyList()
        } else {
            media.filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
        }
    }
    var titles by remember { mutableStateOf<List<TmdbTitle>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.length < 2) {
            titles = emptyList()
        } else {
            delay(450)
            titles = vm.searchTitles(q)
        }
    }
    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val actions = remember {
        listOf(
            SearchAction("YouTube", Icons.Rounded.SmartDisplay, Color(0xFFCC0000)) { vm.searchYouTube(it) },
            SearchAction("Google", Icons.Rounded.TravelExplore, Color(0xFF1A73E8)) { vm.searchWeb(it) },
            SearchAction("Play Store", Icons.Rounded.Shop, Color(0xFF01875F)) { vm.searchPlayStore(it) },
        )
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
            OutlinedTextField(
                value = if (listening) partial else query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Cerca app, contenuti, YouTube o il web") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (matchedApps.size == 1) {
                        vm.launch(matchedApps.first())
                    } else if (q.isNotBlank()) {
                        vm.searchYouTube(q)
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                ),
            )
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
        }

        if (listening) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphMic(
                        listening = true,
                        level = level,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(140.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = partial.ifBlank { "Ti ascolto…" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                error?.let { msg ->
                    item(key = "error") {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
                        )
                    }
                }
                if (q.isBlank()) {
                    item(key = "hint") {
                        Text(
                            text = "Scrivi o tocca il microfono. Puoi cercare tra le tue app, in \"Continua a guardare\", su YouTube, Google e Play Store.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    // Come Google TV: risultati divisi per tipo, poi dove cercare ancora.
                    val movies = titles.filter { it.type == "movie" }
                    val shows = titles.filter { it.type == "tv" }
                    if (movies.isNotEmpty()) {
                        item(key = "movies") {
                            ShelfRow("Film", 0) {
                                items(movies, key = { it.key }) { t ->
                                    PosterCard(title = t, tint = DefaultAmbient, onClick = { onOpenTitle(t) }, onFocus = {})
                                }
                            }
                        }
                    }
                    if (shows.isNotEmpty()) {
                        item(key = "shows") {
                            ShelfRow("Serie TV", 1) {
                                items(shows, key = { it.key }) { t ->
                                    PosterCard(title = t, tint = DefaultAmbient, onClick = { onOpenTitle(t) }, onFocus = {})
                                }
                            }
                        }
                    }
                    if (matchedApps.isNotEmpty()) {
                        item(key = "apps") {
                            ShelfRow("App", 2) {
                                items(matchedApps, key = { it.key }) { app ->
                                    AppCard(
                                        app = app,
                                        onClick = { vm.launch(app) },
                                        onMenu = { vm.launch(app) },
                                        onFocus = {},
                                    )
                                }
                            }
                        }
                    }
                    if (matchedMedia.isNotEmpty()) {
                        item(key = "media") {
                            ShelfRow("Continua a guardare", 3) {
                                items(matchedMedia, key = { it.id }) { entry ->
                                    ContinueCard(
                                        item = entry,
                                        app = byPkg[entry.packageName],
                                        onClick = { vm.resume(entry) },
                                        onMenu = { vm.resume(entry) },
                                        onFocus = {},
                                    )
                                }
                            }
                        }
                    }
                    item(key = "actions") {
                        ShelfRow("Cerca \"$q\" anche su", 4) {
                            items(actions, key = { it.label }) { action ->
                                ActionCard(action) { action.run(q) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(action: SearchAction, onClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(170.dp).aspectRatio(16f / 9f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(action.color, action.color.darken(0.5f)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(action.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(6.dp))
                Text(action.label, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
