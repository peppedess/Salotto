package it.peppedess.salotto.ui

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.ui.components.LocalNarrow
import it.peppedess.salotto.ui.components.TextInputDialog
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.theme.Bg

private const val PAGES = 6

/** Configurazione iniziale a schermo intero, come quella di una TV Google appena accesa. */
@Composable
fun SetupScreen(vm: HomeViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val narrow = LocalNarrow.current

    val go: (Int) -> Unit = { target ->
        forward = target > page
        page = target.coerceIn(0, PAGES - 1)
    }
    BackHandler(enabled = page > 0) { go(page - 1) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1B2433), Bg, Bg)))
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (fadeIn(tween(300)) + slideInHorizontally(tween(350)) { dir * it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -dir * it / 6 })
            },
            label = "setup",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (narrow) 24.dp else 64.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (current) {
                    0 -> WelcomePage()
                    1 -> HomePage(vm)
                    2 -> NotificationsPage(vm)
                    3 -> MoviesPage(vm)
                    4 -> TvPage(vm)
                    else -> DonePage()
                }
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (current > 0) {
                        TvPill(text = "Indietro", icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = { go(current - 1) })
                    }
                    if (current < PAGES - 1) {
                        TvPill(
                            text = if (current == 0) "Iniziamo" else "Avanti",
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            primary = true,
                            onClick = { go(current + 1) },
                        )
                        if (current > 0) {
                            TvPill(text = "Salta tutto", onClick = { vm.completeSetup() })
                        }
                    } else {
                        TvPill(text = "Inizia a guardare", icon = Icons.Rounded.Tv, primary = true, onClick = { vm.completeSetup() })
                    }
                }
                Spacer(Modifier.height(20.dp))
                Dots(current)
            }
        }
    }
}

@Composable
private fun Dots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGES) { i ->
            val w by animateDpAsState(if (i == current) 22.dp else 6.dp, label = "dot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.3f))
            )
        }
    }
}

@Composable
private fun PageTitle(icon: ImageVector, title: String, text: String) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.height(18.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.75f))
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun WelcomePage() {
    Text(
        "Salotto",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Trasformiamo questo telefono in una TV: la tua home con film, serie, dirette e app, da usare col dito o con un telecomando.",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.8f),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Ci vogliono un paio di minuti. Ogni passaggio si può saltare e rifare dalle impostazioni.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f),
    )
}

@Composable
private fun HomePage(vm: HomeViewModel) {
    val context = LocalContext.current
    val isHome by vm.isDefaultHome.collectAsStateWithLifecycle()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshStatus()
    }
    PageTitle(
        Icons.Rounded.Home,
        "Salotto come schermata Home",
        "Così il tasto Home del telefono (o del telecomando) porta sempre qui, come su una TV.",
    )
    Choice(
        icon = Icons.Rounded.Home,
        title = if (isHome) "Salotto è la tua schermata Home" else "Imposta come schermata Home",
        done = isHome,
        onClick = {
            val rm = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                    .onFailure { vm.openHomeSettings() }
            } else {
                vm.openHomeSettings()
            }
        },
    )
}

@Composable
private fun NotificationsPage(vm: HomeViewModel) {
    val access by vm.notificationAccess.collectAsStateWithLifecycle()
    PageTitle(
        Icons.Rounded.Notifications,
        "Continua a guardare",
        "Con l'accesso alle notifiche Salotto vede cosa stai guardando nelle altre app e te lo ripropone in cima. Non legge i tuoi messaggi.",
    )
    Choice(
        icon = Icons.Rounded.Notifications,
        title = if (access) "Accesso concesso" else "Concedi l'accesso alle notifiche",
        done = access,
        onClick = { vm.openNotificationAccess() },
    )
    if (!access) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Se Android lo blocca: Info app di Salotto → ⋮ → Consenti impostazioni con restrizioni, poi riprova.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun MoviesPage(vm: HomeViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.catalogStatus.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(false) }
    val hasKey = settings.tmdbKey.isNotBlank()
    PageTitle(
        Icons.Rounded.Movie,
        "Film e serie",
        "Locandine, trame e le righe dei tuoi servizi di streaming arrivano da TMDB. Serve una chiave gratuita: la crei in un minuto su themoviedb.org.",
    )
    Choice(
        icon = Icons.Rounded.OpenInBrowser,
        title = "Ottieni una chiave gratuita",
        done = false,
        onClick = { vm.openUrl("https://www.themoviedb.org/settings/api") },
    )
    Spacer(Modifier.height(10.dp))
    Choice(
        icon = Icons.Rounded.Key,
        title = when {
            !hasKey -> "Inserisci la chiave"
            status.isNotBlank() -> "Chiave inserita · $status"
            else -> "Chiave inserita"
        },
        done = hasKey && !status.startsWith("TMDB non") && status != "Chiave non valida",
        onClick = { dialog = true },
    )
    if (dialog) {
        TextInputDialog(
            title = "Chiave TMDB",
            label = "Chiave API o token di lettura",
            initial = settings.tmdbKey,
            help = "Incollala da themoviedb.org → Impostazioni → API.",
            onConfirm = { key ->
                vm.updateSettings { s -> s.copy(tmdbKey = key) }
                dialog = false
            },
            onDismiss = { dialog = false },
        )
    }
}

@Composable
private fun TvPage(vm: HomeViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val service by vm.tvServiceEnabled.collectAsStateWithLifecycle()
    val canWrite by vm.canWriteSettings.collectAsStateWithLifecycle()
    PageTitle(
        Icons.Rounded.Tv,
        "Esperienza TV",
        "Facoltativo: fai sparire le parti del telefono che ricordano di non essere su una TV.",
    )
    Choice(
        icon = Icons.Rounded.SettingsAccessibility,
        title = if (service) "Servizio TV attivo" else "Attiva il Servizio TV di Salotto",
        subtitle = "Volume in stile TV in tutte le app e orizzontale anche per le app bloccate in verticale",
        done = service,
        onClick = { vm.openAccessibilitySettings() },
    )
    Spacer(Modifier.height(10.dp))
    Choice(
        icon = Icons.Rounded.ScreenLockLandscape,
        title = "Tutte le app in orizzontale",
        subtitle = when {
            !settings.landscapeAll -> "Tocca per attivarlo"
            service -> "Attivo tramite il Servizio TV"
            canWrite -> "Attivo"
            else -> "Serve il permesso \"Modifica impostazioni di sistema\""
        },
        done = settings.landscapeAll && (service || canWrite),
        onClick = { vm.setLandscapeAll(!settings.landscapeAll || !(service || canWrite)) },
    )
    Spacer(Modifier.height(10.dp))
    Choice(
        icon = Icons.Rounded.DoNotDisturbOn,
        title = "Niente notifiche mentre guardi",
        subtitle = "Attiva Non disturbare (solo priorità) quando usi Salotto",
        done = settings.tvQuiet,
        onClick = { vm.setTvQuiet(!settings.tvQuiet) },
    )
}

@Composable
private fun DonePage() {
    PageTitle(
        Icons.Rounded.CheckCircle,
        "Tutto pronto",
        "Usa le schede in alto per passare tra Per te, Live, Film, Serie TV, App e Libreria. L'ingranaggio apre le impostazioni rapide. Buona visione!",
    )
}

@Composable
private fun Choice(
    icon: ImageVector,
    title: String,
    done: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    TvCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        focusScale = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(
                if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (done) Color(0xFF81C995) else Color.White.copy(alpha = 0.35f),
            )
        }
    }
}
