package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mcclabs.gimmer.data.Exercise
import com.mcclabs.gimmer.data.ExerciseMetadata

@Composable
fun ExerciseInfoSheet(exercise: Exercise, onDismiss: () -> Unit) {
    GimmerSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(exercise.muscleGroup, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(exercise.displayName, style = MaterialTheme.typography.headlineSmall)
            val steps = ExerciseMetadata.instructions(exercise.id)
            if (steps.isEmpty()) Text("Bu hareket için uygulama adımları henüz eklenmedi.")
            else {
                SectionTitle("Uygulama adımları")
                steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Kapat") }
        }
    }
}
