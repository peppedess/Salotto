package it.peppedess.salotto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.peppedess.salotto.data.CastMember
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.ui.components.LocalNarrow
import it.peppedess.salotto.ui.components.PosterCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.components.enterCascade
import it.peppedess.salotto.ui.components.sharedPoster
import it.peppedess.salotto.ui.theme.Bg
import java.util.Locale

/** Scheda di un film o una serie: trama, generi, durata, cast, dove guardarlo e titoli simili. */
@Composable
fun DetailScreen(vm: HomeViewModel, onClose: () -> Unit, onOpenTitle: (TmdbTitle) -> Unit) {
    BackHandler(onBack = onClose)
    val state by vm.detail.collectAsStateWithLifecycle()
    val detail = state
    if (detail == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val title = detail.title
    val info = detail.details
    val tint = detail.apps.firstOrNull()?.color ?: DefaultAmbient
    val context = LocalContext.current
    val myList by vm.myList.collectAsStateWithLifecycle()
    val inList = myList.any { it.key == title.key }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Zoom lento dello sfondo all'apertura.
    var started by remember(title.key) { mutableStateOf(false) }
    LaunchedEffect(title.key) { started = true }
    val zoom by animateFloatAsState(if (started) 1.08f else 1.0f, tween(14_000, easing = LinearEasing), label = "zoom")

    val narrow = LocalNarrow.current
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
                modifier = Modifier
                    .fillMaxHeight(if (narrow) 0.45f else 1f)
                    .fillMaxWidth(if (narrow) 1f else 0.72f)
                    .align(if (narrow) Alignment.TopCenter else Alignment.CenterEnd)
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    },
                contentScale = ContentScale.Crop,
            )
        }
        if (narrow) {
            // In verticale l'immagine sta in alto e il testo scorre sotto.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Bg.copy(alpha = 0.4f), 0.2f to Color.Transparent, 0.45f to Bg))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Bg,
                            0.38f to Bg.copy(alpha = 0.94f),
                            0.7f to tint.copy(alpha = 0.18f),
                            1f to Color.Transparent,
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Bg))
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (narrow) 1f else 0.62f)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .verticalScroll(rememberScrollState())
                .padding(start = 40.dp, end = 16.dp, top = 16.dp, bottom = 28.dp)
        ) {
            TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
            Spacer(Modifier.height(if (narrow) 140.dp else 16.dp))

            Row(verticalAlignment = Alignment.Top) {
                if (title.posterUrl != null) {
                    AsyncImage(
                        model = title.posterUrl,
                        contentDescription = title.title,
                        modifier = Modifier
                            .width(104.dp)
                            .aspectRatio(2f / 3f)
                            .sharedPoster(detail.shareKey)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(18.dp))
                }
                Column(Modifier.enterCascade(0)) {
                    val logos = LocalLogos.current
                    val logo = logos.map[title.key]
                    LaunchedEffect(title.key) { logos.request(title) }
                    if (logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = title.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .height(84.dp)
                                .fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = title.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    MetaChips(
                        listOfNotNull(
                            title.year.takeIf { it.isNotBlank() },
                            title.kindLabel,
                            info?.lengthLabel?.takeIf { it.isNotBlank() },
                            info?.certification?.takeIf { it.isNotBlank() }?.let { if (it.all(Char::isDigit)) "VM$it" else it },
                            info?.status?.takeIf { it.isNotBlank() },
                        ),
                        rating = title.rating,
                    )
                }
            }

            val genres = info?.genres?.takeIf { it.isNotEmpty() } ?: title.genres
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .enterCascade(1),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.forEach { g ->
                        Text(
                            g,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (!info?.tagline.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "«${info?.tagline}»",
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.enterCascade(2),
                )
            }

            if (title.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.enterCascade(3),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
                    .enterCascade(4),
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

            AnimatedVisibility(visible = info != null, enter = fadeIn(tween(400)) + expandVertically()) {
                Column {
                    if (info != null && info.creators.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        InfoLine(
                            if (title.type == "tv") "Ideata da" else "Regia",
                            info.creators.joinToString(", "),
                        )
                    }
                    if (info != null && info.cast.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Cast", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(end = 16.dp),
                        ) {
                            items(info.cast, key = { it.name + it.role }) { member -> CastChip(member) }
                        }
                    }
                }
            }

            if (detail.similar.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Titoli simili", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp, end = 16.dp),
                ) {
                    items(detail.similar, key = { it.key }) { t ->
                        PosterCard(
                            title = t,
                            tint = tint,
                            onClick = { onOpenTitle(t) },
                            onFocus = {},
                        )
                    }
                }
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

@Composable
private fun MetaChips(items: List<String>, rating: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rating >= 0.5) {
            Text(
                "★ " + String.format(Locale.ITALIAN, "%.1f", rating),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFC960))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            items.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 2,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            "$label  ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun CastChip(member: CastMember) {
    Column(
        Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, tint = Color.White.copy(alpha = 0.5f))
            if (member.photoUrl != null) {
                AsyncImage(member.photoUrl, member.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        if (member.role.isNotBlank()) {
            Text(
                member.role,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
