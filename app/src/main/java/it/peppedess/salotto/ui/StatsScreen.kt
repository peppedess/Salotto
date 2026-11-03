package it.peppedess.salotto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.theme.Bg
import java.time.format.TextStyle
import java.util.Locale

internal fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Statistiche di visione degli ultimi 7 giorni. */
@Composable
fun StatsScreen(vm: HomeViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val stats by vm.stats.collectAsStateWithLifecycle()
    val allowed by vm.statsPermission.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()

    LaunchedEffect(allowed) { vm.loadStats() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", onClick = onClose)
            Spacer(Modifier.width(16.dp))
            Text(
                "Statistiche di visione",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        if (!allowed) {
            Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.BarChart, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text(
                    "Per contare il tempo passato nelle app serve il permesso \"Accesso ai dati di utilizzo\".",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                TvPill(text = "Concedi il permesso", primary = true, onClick = { vm.openUsageAccess() })
            }
            return@Column
        }

        val week = stats
        if (week == null) {
            Text(
                "Calcolo in corso…",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(40.dp),
            )
            return@Column
        }

        val byPkg = apps.associateBy { it.packageName }
        val maxDay = week.days.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        val maxApp = week.apps.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
        val primary = MaterialTheme.colorScheme.primary

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "total") {
                Column {
                    Text(
                        formatMinutes(week.totalMinutes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "negli ultimi 7 giorni · media ${formatMinutes(week.totalMinutes / 7)} al giorno",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            item(key = "chart") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    week.days.forEach { (day, minutes) ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (minutes > 0) formatMinutes(minutes) else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                                val h = size.height * minutes / maxDay
                                drawRoundRect(
                                    color = primary,
                                    topLeft = Offset(size.width * 0.2f, size.height - h),
                                    size = Size(size.width * 0.6f, h),
                                    cornerRadius = CornerRadius(8f, 8f),
                                )
                            }
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            items(week.apps, key = { it.packageName }) { usage ->
                val app = byPkg[usage.packageName]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app != null) {
                        Image(app.icon, null, Modifier.size(32.dp))
                    } else {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.DarkGray))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(
                                app?.label ?: usage.packageName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(formatMinutes(usage.minutes), style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(usage.minutes.toFloat() / maxApp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(app?.color ?: primary)
                            )
                        }
                    }
                }
            }
            if (week.apps.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nessuna app di intrattenimento usata questa settimana.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
