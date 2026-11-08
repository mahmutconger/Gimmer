package com.mcclabs.gimmer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mcclabs.gimmer.data.WorkoutLog
import com.mcclabs.gimmer.data.WorkoutCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PerformanceFields(
    weight: String, reps: String, rir: Int?, isWarmup: Boolean, note: String,
    onWeight: (String) -> Unit, onReps: (String) -> Unit, onRir: (Int) -> Unit,
    onWarmup: (Boolean) -> Unit, onNote: (String) -> Unit,
    onPlateCalculator: (() -> Unit)? = null,
) {
    val numericWeight = parseWeight(weight) ?: 0.0
    val numericReps = reps.toIntOrNull()?.coerceIn(0, 10_000) ?: 0
    StepperInput("Ağırlık (kg)", weight, onWeight,
        onDecrease = { onWeight(stepWeight(weight, -2.5)) },
        onIncrease = { onWeight(stepWeight(weight, 2.5)) },
        decreaseEnabled = numericWeight > 0, increaseEnabled = numericWeight < 10_000,
        keyboardType = KeyboardType.Decimal,
        decreaseLabel = "Ağırlığı 2,5 kg azalt", increaseLabel = "Ağırlığı 2,5 kg artır",
        trailingIcon = onPlateCalculator?.let { onClick ->
            { IconButton(onClick = onClick) { Icon(Icons.Rounded.FitnessCenter, "Plaka hesaplayıcı") } }
        })
    StepperInput("Tekrar", reps, onReps,
        onDecrease = { onReps((numericReps - 1).toString()) },
        onIncrease = { onReps((numericReps + 1).toString()) },
        decreaseEnabled = numericReps > 1, increaseEnabled = numericReps < 10_000,
        keyboardType = KeyboardType.Number, decreaseLabel = "Tekrarı azalt", increaseLabel = "Tekrarı artır")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillChip("Çalışma", !isWarmup, onClick = { onWarmup(false) })
        PillChip("Isınma", isWarmup, onClick = { onWarmup(true) })
    }
    Text("RIR · Yedekte kalan tekrar", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..4).forEach { value ->
            PillChip(value.toString(), rir == value, onClick = { onRir(value) })
        }
    }
    if (rir == null) {
        Text("Bu eski kayıtta RIR girilmemiş.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    GimmerField(note, { if (it.length <= 2000) onNote(it) }, label = "Not (isteğe bağlı)",
        modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 4)
}

internal fun validPerformance(weight: String, reps: String): Boolean {
    return parseWeight(weight) != null && (reps.toIntOrNull() ?: 0) in 1..10000
}

@Composable
fun LogRow(log: WorkoutLog, showContext: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val date = remember(log.dateTimestamp) { formatSessionDate(log.dateTimestamp) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${if (log.isWarmup) "Isınma" else "Çalışma"} · $date",
                    color = if (log.isWarmup) colors.onSurfaceVariant else colors.primary,
                    style = MaterialTheme.typography.labelMedium)
                val sets = if (log.sets > 1) " · ${log.sets} set" else ""
                Text("${formatMetric(log.weight)} kg · ${log.reps} tekrar$sets",
                    style = MaterialTheme.typography.titleMedium)
                val context = if (showContext) listOf(log.dayName, log.muscleGroup).filter { it.isNotBlank() }
                    else emptyList()
                Text((context + "RIR ${log.rir ?: "—"}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                log.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Icon(Icons.Rounded.Edit, contentDescription = "Kaydı düzenle", tint = colors.primary,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun EditLogDialog(log: WorkoutLog, viewModel: WorkoutViewModel, onDismiss: () -> Unit) {
    var weight by rememberSaveable(log.id) { mutableStateOf(log.weight.toString()) }
    var reps by rememberSaveable(log.id) { mutableStateOf(log.reps.toString()) }
    var rir by rememberSaveable(log.id) { mutableStateOf(log.rir) }
    var isWarmup by rememberSaveable(log.id) { mutableStateOf(log.isWarmup) }
    var note by rememberSaveable(log.id) { mutableStateOf(log.note.orEmpty()) }
    var deleting by rememberSaveable(log.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { if (!saving) deleting = false },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("${WorkoutCatalog.displayName(log.exerciseName)} için bu set kaydı kalıcı olarak silinecek.") },
            confirmButton = {
                Button(
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    onClick = {
                        saving = true
                        viewModel.deleteLog(log, onDismiss).invokeOnCompletion { saving = false }
                    },
                ) { Text(if (saving) "Siliniyor…" else "Kaydı sil") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }, enabled = !saving) { Text("Vazgeç") }
            },
        )
    } else {
        GimmerSheet(onDismiss, dismissEnabled = !saving) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Kaydı düzenle", style = MaterialTheme.typography.headlineSmall)
                Text(WorkoutCatalog.displayName(log.exerciseName), style = MaterialTheme.typography.titleMedium)
                Text(formatSessionDate(log.dateTimestamp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (log.sets > 1) {
                    Text("Bu kayıt ${log.sets} set içeriyor.", style = MaterialTheme.typography.bodySmall)
                }
                PerformanceFields(weight, reps, rir, isWarmup, note, { weight = it }, { reps = it },
                    { rir = it }, { isWarmup = it }, { note = it })
                Button(
                    enabled = !saving && validPerformance(weight, reps),
                    onClick = {
                        saving = true
                        val updated = log.copy(weight = weight.replace(',', '.').toDouble(), reps = reps.toInt(),
                            rir = rir, isWarmup = isWarmup, note = note.trim().ifBlank { null })
                        viewModel.updateLog(updated, onDismiss).invokeOnCompletion { saving = false }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (saving) "Kaydediliyor…" else "Değişiklikleri kaydet") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { deleting = true }, enabled = !saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Kaydı sil")
                    }
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Vazgeç") }
                }
            }
        }
    }
}

private val SessionDate = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Turkish)

internal fun formatSessionDate(timestamp: Long): String =
    SessionDate.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
