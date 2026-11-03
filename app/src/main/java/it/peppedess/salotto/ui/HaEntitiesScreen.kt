package it.peppedess.salotto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.peppedess.salotto.ui.components.TvCard
import it.peppedess.salotto.ui.components.TvIconButton
import it.peppedess.salotto.ui.components.TvPill
import it.peppedess.salotto.ui.components.icon
import it.peppedess.salotto.ui.theme.Bg

private val DOMAIN_LABELS = linkedMapOf(
    "light" to "Luci",
    "switch" to "Interruttori",
    "fan" to "Ventilatori",
    "cover" to "Tapparelle",
    "scene" to "Scene",
    "script" to "Script",
    "button" to "Pulsanti",
    "input_boolean" to "Aiutanti",
    "media_player" to "Lettori multimediali",
)

/** Scelta delle entità di Home Assistant da mostrare nella riga "Casa". */
@Composable
fun HaEntitiesScreen(vm: HomeViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val all by vm.haAll.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.haStatus.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshHomeAssistant() }

    val selected = settings.haEntities
    val visible = all.filter { filter == null || it.domain == filter }
    val domains = DOMAIN_LABELS.keys.filter { d -> all.any { it.domain == d } }

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
            Column(Modifier.weight(1f)) {
                Text(
                    "Riga \"Casa\"",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "${selected.size} selezionate · l'ordine è quello in cui le scegli",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (all.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = status.ifBlank { "Caricamento delle entità…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                item(key = "filters") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        TvPill(text = "Tutte", onClick = { filter = null }, selected = filter == null)
                        domains.forEach { d ->
                            TvPill(text = DOMAIN_LABELS[d] ?: d, onClick = { filter = d }, selected = filter == d)
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { entity ->
                val checked = entity.id in selected
                TvCard(
                    onClick = { vm.toggleHaEntity(entity.id) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    focusScale = 1.01f,
                ) { focused ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .background(
                                when {
                                    focused -> Color.White.copy(alpha = 0.16f)
                                    checked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else -> Color.White.copy(alpha = 0.05f)
                                }
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(entity.icon(), null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entity.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                "${entity.id} · ${entity.stateLabel}",
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint = if (checked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}
