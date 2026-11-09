package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.R
import com.mcclabs.gimmer.data.DailyCondition
import com.mcclabs.gimmer.data.MAX_SLEEP_MINUTES
import com.mcclabs.gimmer.data.MIN_SLEEP_MINUTES
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToInt

private const val SLEEP_STEP_MINUTES = 30
private data class SleepState(val loaded: Boolean = false, val condition: DailyCondition? = null)

@Composable
internal fun DailyConditionCard(viewModel: WorkoutViewModel) {
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    key(today) { SleepEntry(today, viewModel) }
}

@Composable
private fun SleepEntry(date: LocalDate, viewModel: WorkoutViewModel) {
    val conditionFlow = remember(viewModel, date) {
        viewModel.dailyCondition(date).map { SleepState(loaded = true, condition = it) }
    }
    val state by conditionFlow.collectAsStateWithLifecycle(SleepState())
    val condition = state.condition
    var draft by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val minutes = draft ?: condition?.sleepMinutes ?: 420
    val duration = sleepDuration(minutes)
    val sleepDescription = stringResource(R.string.sleep_duration)
    val colors = MaterialTheme.colorScheme

    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = colors.primary)
                SectionTitle(stringResource(R.string.daily_condition))
            }
            Text(stringResource(R.string.night_sleep), style = MaterialTheme.typography.titleSmall)
            Text(duration, style = MaterialTheme.typography.headlineSmall, color = colors.primary)
            Slider(
                value = minutes.toFloat(),
                onValueChange = { draft = (it / SLEEP_STEP_MINUTES).roundToInt() * SLEEP_STEP_MINUTES },
                valueRange = MIN_SLEEP_MINUTES.toFloat()..MAX_SLEEP_MINUTES.toFloat(),
                steps = (MAX_SLEEP_MINUTES - MIN_SLEEP_MINUTES) / SLEEP_STEP_MINUTES - 1,
                enabled = !saving && state.loaded,
                modifier = Modifier.semantics {
                    contentDescription = sleepDescription
                    stateDescription = duration
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hours, 4), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(stringResource(R.string.hours, 10), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Button(
                onClick = {
                    saving = true
                    draft = minutes
                    viewModel.saveSleep(date, minutes).invokeOnCompletion { saving = false }
                },
                enabled = !saving && state.loaded && condition?.sleepMinutes != minutes,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(when {
                    saving -> stringResource(R.string.saving)
                    condition?.sleepMinutes == minutes -> stringResource(R.string.sleep_saved)
                    else -> stringResource(R.string.save_sleep)
                })
            }
        }
    }
}

@Composable
private fun sleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) stringResource(R.string.hours, hours)
    else stringResource(R.string.hours_minutes, hours, remainder)
}
