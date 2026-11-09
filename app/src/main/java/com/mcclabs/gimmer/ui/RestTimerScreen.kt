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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.R

@Composable
fun RestTimerScreen(viewModel: WorkoutViewModel, bottomSpace: Dp) {
    val timer = viewModel.restTimer
    val days by viewModel.days.collectAsStateWithLifecycle()
    val exerciseId by timer.exerciseId.collectAsStateWithLifecycle()
    val language = LocalExerciseLanguage.current
    val remaining by timer.remaining.collectAsStateWithLifecycle()
    val preset by timer.preset.collectAsStateWithLifecycle()
    val duration by timer.duration.collectAsStateWithLifecycle()
    val exerciseName by timer.exerciseName.collectAsStateWithLifecycle()
    val finished by timer.finished.collectAsStateWithLifecycle()
    val active = remaining > 0
    val seconds = if (active || finished) remaining else preset
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeading(stringResource(R.string.timer), stringResource(R.string.timer_description))
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
                        active -> stringResource(R.string.timer_resting)
                        finished -> stringResource(R.string.timer_done)
                        else -> stringResource(R.string.timer_ready)
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
                if (active || finished) exerciseName?.let { name ->
                    val label = loggedExerciseName(name, exerciseId, days.orEmpty().flatMap { it.exercises }, language)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val ringSize = minOf(216.dp * LocalDensity.current.fontScale, maxWidth)
                    CircularProgressIndicator(
                        progress = { if (finished) 0f else seconds.toFloat() / (if (active) duration else preset) },
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
                    onClick = { if (active || finished) timer.restart() else timer.start() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(if (active || finished) Icons.Rounded.RestartAlt else Icons.Rounded.PlayArrow,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (active || finished) stringResource(R.string.timer_restart) else stringResource(R.string.timer_start))
                }
                if (active || finished) {
                    OutlinedButton(onClick = timer::cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (finished) stringResource(R.string.timer_reset) else stringResource(R.string.timer_end))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(stringResource(R.string.timer_manual))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestTimer.presets.forEach { option ->
                    PillChip(formatTime(option), selected = (if (active) duration else preset) == option,
                        onClick = { timer.selectPreset(option) })
                }
            }
            Text(
                if (active) stringResource(R.string.timer_change)
                else stringResource(R.string.timer_auto),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
