package it.peppedess.salotto.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.peppedess.salotto.data.AppEntry
import it.peppedess.salotto.data.ChannelGuide
import it.peppedess.salotto.data.EpisodeInfo
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.darken
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME)

/** Canale in diretta: programma in onda, avanzamento e programma successivo. */
@Composable
internal fun LiveCard(
    guide: ChannelGuide,
    now: Long,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onFocus: (Color) -> Unit,
) {
    val tint = Color(guide.channel.color)
    val current = guide.current(now)
    val next = guide.upcoming(now, 1).firstOrNull()
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(240.dp).aspectRatio(16f / 9f),
        onFocus = { onFocus(tint) },
        onMenu = onMenu,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(tint, tint.darken(0.65f))))
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "${guide.channel.number}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    guide.channel.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF4D4D))
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = current?.title ?: "Diretta",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                Spacer(Modifier.height(4.dp))
                val span = (current.stop - current.start).coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { ((now - current.start).toFloat() / span).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
            if (next != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatTime(next.start)} · ${next.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Nuovo episodio di una serie in lista. */
@Composable
internal fun EpisodeCard(
    episode: EpisodeInfo,
    onClick: () -> Unit,
    onFocus: (Color) -> Unit,
) {
    val date = runCatching { LocalDate.parse(episode.airDate) }.getOrNull()
    val dateLabel = when {
        date == null -> ""
        date == LocalDate.now() -> "oggi"
        date == LocalDate.now().minusDays(1) -> "ieri"
        else -> "il " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))
    }
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(220.dp).aspectRatio(16f / 9f),
        onFocus = { onFocus(Color(0xFF2B3445)) },
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1B2433)))
        val art = episode.series.backdropUrl ?: episode.series.posterUrl
        if (art != null) {
            AsyncImage(art, episode.series.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f)))
        )
        Text(
            text = "NUOVO",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                episode.series.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "S${episode.season} E${episode.episode} · uscito $dateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
    }
}

/** Riquadro della riga "Strumenti". */
@Composable
internal fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    onFocus: (Color) -> Unit,
) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.width(170.dp).height(96.dp),
        shape = RoundedCornerShape(20.dp),
        onFocus = { onFocus(color) },
    ) { focused ->
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(color.copy(alpha = if (focused) 0.75f else 0.45f), color.darken(0.6f).copy(alpha = 0.55f))
                    )
                )
                .padding(14.dp)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** App grande per la home dei bambini. */
@Composable
internal fun KidsAppCard(app: AppEntry, onClick: () -> Unit) {
    TvCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.2f),
        shape = RoundedCornerShape(28.dp),
        focusScale = 1.08f,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(app.color, app.color.darken(0.5f)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.icon, null, Modifier.size(72.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}
