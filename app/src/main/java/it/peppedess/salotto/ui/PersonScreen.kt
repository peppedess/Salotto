package it.peppedess.salotto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.peppedess.salotto.data.TmdbTitle
import it.peppedess.salotto.ui.components.LocalNarrow
import it.peppedess.salotto.ui.components.POSTER_W
import it.peppedess.salotto.ui.components.PosterCard
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.enterCascade
import it.peppedess.salotto.ui.components.rowStart
import it.peppedess.salotto.ui.theme.Bg

/** Pagina di un attore o regista, come su Google TV: foto, biografia e i suoi film e serie. */
@Composable
fun PersonScreen(vm: HomeViewModel, onClose: () -> Unit, onOpenTitle: (TmdbTitle) -> Unit) {
    BackHandler(onBack = onClose)
    val state by vm.person.collectAsStateWithLifecycle()
    val person = state
    if (person == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val details = person.details
    val narrow = LocalNarrow.current
    val photo = details?.photoUrl ?: person.member.photoUrl
    var bioOpen by remember(person.member.id) { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Sfondo: la foto sfocata, appena accennata.
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp),
                alpha = 0.28f,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Bg.copy(alpha = 0.3f), 0.6f to Bg.copy(alpha = 0.9f), 1f to Bg))
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout),
            contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "back") {
                Box(Modifier.padding(start = rowStart)) {
                    TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
                }
            }

            item(key = "head") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rowStart),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .width(if (narrow) 108.dp else 150.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                        if (photo != null) {
                            AsyncImage(photo, person.member.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f).enterCascade(0)) {
                        val label = details?.departmentLabel?.takeIf { it.isNotBlank() }
                        if (label != null) {
                            Text(
                                label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            details?.name?.ifBlank { null } ?: person.member.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (person.member.role.isNotBlank()) {
                            Text(
                                "Nel ruolo di ${person.member.role}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val life = details?.lifeLine.orEmpty()
                        if (life.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(life, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                        Spacer(Modifier.height(12.dp))
                        when {
                            person.loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            person.error != null -> Text(
                                person.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                            !details?.biography.isNullOrBlank() -> {
                                // Biografia: tre righe, si apre tutta con un tocco o con OK.
                                TvCard(
                                    onClick = { bioOpen = !bioOpen },
                                    shape = RoundedCornerShape(14.dp),
                                    focusScale = 1.01f,
                                ) { focused ->
                                    Column(
                                        Modifier
                                            .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                            .padding(8.dp)
                                            .animateContentSize(),
                                    ) {
                                        Text(
                                            details?.biography.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = if (bioOpen) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (bioOpen) "Mostra meno" else "Leggi tutto",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (details != null) {
                val directingFirst = details.department == "Directing"
                val rows = listOfNotNull(
                    details.acting.takeIf { it.isNotEmpty() }?.let { "Film e serie" to it },
                    details.directing.takeIf { it.isNotEmpty() }?.let { "Dietro la macchina da presa" to it },
                ).let { if (directingFirst) it.reversed() else it }
                rows.forEach { (label, titles) ->
                    item(key = "row:$label") {
                        Column {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(start = rowStart),
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(start = rowStart, end = rowStart, top = 10.dp, bottom = 10.dp),
                            ) {
                                items(titles, key = { it.key }) { t ->
                                    Column(Modifier.width(POSTER_W)) {
                                        PosterCard(
                                            title = t,
                                            tint = DefaultAmbient,
                                            onClick = { onOpenTitle(t) },
                                            onFocus = {},
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            listOf(t.year, t.kindLabel).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nessun titolo trovato.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = rowStart),
                        )
                    }
                }
            }

            item(key = "credits") {
                Text(
                    "Dati e immagini: TMDB",
                    color = Color.White.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = rowStart),
                )
            }
        }
    }
}
