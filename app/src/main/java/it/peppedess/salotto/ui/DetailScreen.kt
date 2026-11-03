package it.peppedess.salotto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.theme.Bg

/** Scheda di un film o una serie, con le app installate dove guardarlo. */
@Composable
fun DetailScreen(vm: HomeViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val state by vm.detail.collectAsStateWithLifecycle()
    val detail = state
    if (detail == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val title = detail.title
    val tint = detail.apps.firstOrNull()?.color ?: DefaultAmbient
    val context = LocalContext.current
    val myList by vm.myList.collectAsStateWithLifecycle()
    val inList = myList.any { it.key == title.key }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        val backdrop = title.backdropUrl ?: title.posterUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.72f).align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Bg,
                        0.38f to Bg.copy(alpha = 0.92f),
                        0.7f to tint.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Bg))
        )

        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.6f)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
        ) {
            TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                if (title.posterUrl != null) {
                    AsyncImage(
                        model = title.posterUrl,
                        contentDescription = title.title,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(18.dp))
                }
                Column {
                    Text(
                        text = title.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title.metaLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            if (title.overview.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                detail.apps.forEachIndexed { index, app ->
                    TvPill(
                        text = "Guarda su ${app.label}",
                        icon = Icons.Rounded.PlayArrow,
                        primary = index == 0,
                        onClick = { vm.watchIn(title, app) },
                    )
                }
                TvPill(
                    text = if (inList) "Nella mia lista" else "La mia lista",
                    icon = if (inList) Icons.Rounded.Check else Icons.Rounded.Add,
                    selected = inList,
                    onClick = {
                        val added = vm.toggleMyList(title)
                        if (added && title.type == "tv" && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                TvPill(
                    text = "Trailer",
                    icon = Icons.Rounded.SmartDisplay,
                    onClick = { vm.watchTrailer(title) },
                )
            }
            if (detail.resolving) {
                Spacer(Modifier.height(8.dp))
                Text("Cerco dove guardarlo…", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            } else if (detail.apps.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Non risulta disponibile nelle app di streaming installate.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Dati e immagini: TMDB",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
