package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.data.MAX_SLEEP_MINUTES
import com.mcclabs.gimmer.data.MIN_SLEEP_MINUTES
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

private const val SLEEP_STEP_MINUTES = 30

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
    val conditionFlow = remember(viewModel, date) { viewModel.dailyCondition(date) }
    val condition by conditionFlow.collectAsStateWithLifecycle(initialValue = null)
    var draft by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val minutes = draft ?: condition?.sleepMinutes ?: 420
    val duration = sleepDuration(minutes)
    val colors = MaterialTheme.colorScheme

    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = colors.primary)
                SectionTitle("Günlük Durum")
            }
            Text("Gece Uykusu", style = MaterialTheme.typography.titleSmall)
            Text(duration, style = MaterialTheme.typography.headlineSmall, color = colors.primary)
            Slider(
                value = minutes.toFloat(),
                onValueChange = { draft = (it / SLEEP_STEP_MINUTES).roundToInt() * SLEEP_STEP_MINUTES },
                valueRange = MIN_SLEEP_MINUTES.toFloat()..MAX_SLEEP_MINUTES.toFloat(),
                steps = (MAX_SLEEP_MINUTES - MIN_SLEEP_MINUTES) / SLEEP_STEP_MINUTES - 1,
                enabled = !saving,
                modifier = Modifier.semantics {
                    contentDescription = "Gece uykusu süresi"
                    stateDescription = duration
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("4 saat", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text("10 saat", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Button(
                onClick = {
                    saving = true
                    draft = minutes
                    viewModel.saveSleep(date, minutes).invokeOnCompletion { saving = false }
                },
                enabled = !saving && condition?.sleepMinutes != minutes,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(when {
                    saving -> "Kaydediliyor…"
                    condition?.sleepMinutes == minutes -> "Uyku kaydedildi"
                    else -> "Uykuyu kaydet"
                })
            }
        }
    }
}

private fun sleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "$hours saat" else "$hours saat $remainder dk"
}
