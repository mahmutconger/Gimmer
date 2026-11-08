package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RestTimerScreen(timer: RestTimer, bottomSpace: Dp) {
    val remaining by timer.remaining.collectAsStateWithLifecycle()
    val preset by timer.preset.collectAsStateWithLifecycle()
    val finished by timer.finished.collectAsStateWithLifecycle()
    val active = remaining > 0
    val seconds = if (active || finished) remaining else preset
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeading("Zamanlayıcı", "Set aralarında dinlenme süreni takip et.")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainer,
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    when {
                        active -> "Dinleniyorsun"
                        finished -> "Dinlenme tamamlandı"
                        else -> "Başlamaya hazır"
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val ringSize = minOf(216.dp * LocalDensity.current.fontScale, maxWidth)
                    CircularProgressIndicator(
                        progress = { if (finished) 0f else seconds.toFloat() / preset },
                        modifier = Modifier.size(ringSize),
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        formatTime(seconds),
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Button(
                    onClick = timer::start,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(if (active || finished) Icons.Rounded.RestartAlt else Icons.Rounded.PlayArrow,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (active || finished) "Yeniden başlat" else "Başlat")
                }
                if (active || finished) {
                    OutlinedButton(onClick = timer::cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (finished) "Sıfırla" else "Dinlenmeyi bitir")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Dinlenme süresi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestTimer.presets.forEach { duration ->
                    PillChip(formatTime(duration), selected = preset == duration,
                        onClick = { timer.selectPreset(duration) })
                }
            }
            Text(
                if (active) "Süreyi değiştirirsen sayaç yeniden başlar."
                else "Yeni bir set kaydettiğinde sayaç otomatik başlar.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
