package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcclabs.gimmer.R
import com.mcclabs.gimmer.data.Exercise

@Composable
fun ExerciseInfoSheet(exercise: Exercise, onDismiss: () -> Unit) {
    GimmerSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(exerciseGroup(exercise.muscleGroup), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(exercise.localizedName(LocalExerciseLanguage.current), style = MaterialTheme.typography.headlineSmall)
            val context = exerciseContext()
            val instructions = when (exercise.id) {
                "machine_chest_press" -> R.array.machine_chest_press_steps
                "lat_pulldown" -> R.array.lat_pulldown_steps
                "hack_squat" -> R.array.hack_squat_steps
                else -> null
            }
            val steps = instructions?.let { context.resources.getStringArray(it) }.orEmpty()
            if (steps.isEmpty()) Text(context.getString(R.string.no_exercise_steps))
            else {
                SectionTitle(context.getString(R.string.exercise_steps))
                steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
        }
    }
}
