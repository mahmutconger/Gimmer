package com.mcclabs.gimmer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.data.ExerciseSummary
import com.mcclabs.gimmer.data.WorkoutLog
import com.mcclabs.gimmer.data.WorkoutCatalog

@Composable
fun ProgressScreen(viewModel: WorkoutViewModel, bottomSpace: Dp, onExercise: (ExerciseSummary) -> Unit) {
    val summaries by viewModel.progressSummaries.collectAsStateWithLifecycle()
    val bodyweights by viewModel.bodyweights.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val names = remember(days) { days.orEmpty().flatMap { it.exercises }.associate { it.id to it.displayName } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeading("Gelişim", "Ölçümlerini ve set geçmişini takip et.") }
        item { ActivityCalendar(viewModel) }
        item { DailyConditionCard(viewModel) }
        item { BodyweightSection(bodyweights, viewModel) }
        item {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Hareket geçmişi")
                HistoryFilters(viewModel)
            }
        }
        val entries = summaries
        when {
            entries == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            entries.isEmpty() -> item {
                EmptyMessage("Kayıt bulunamadı", "Bir set kaydet veya arama ve filtreleri değiştir.")
            }
            else -> items(entries, key = { it.exerciseId?.let { id -> "exercise:$id" } ?: "legacy:${it.exerciseName}" }) { summary ->
                ExerciseSummaryRow(summary, names[summary.exerciseId] ?: WorkoutCatalog.displayName(summary.exerciseName)) {
                    onExercise(summary)
                }
            }
        }
    }
}

@Composable
private fun HistoryFilters(viewModel: WorkoutViewModel) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val dayOptions = remember(days) { listOf("") + (days.orEmpty() + WorkoutCatalog.days).map { it.name }.distinct() }
    val muscleOptions = remember(days) {
        listOf("") + (days.orEmpty() + WorkoutCatalog.days).flatMap { it.exercises }.map { it.muscleGroup }.distinct()
    }
    val appliedFilters = listOf(filter.day.isNotEmpty(), filter.muscle.isNotEmpty(), filter.includeWarmups).count { it }

    GimmerField(filter.query, { query -> viewModel.filter.value = filter.copy(query = query) },
        label = "Hareket ara", modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { showFilters = !showFilters }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val label = if (appliedFilters == 0) "Filtreler" else "Filtreler ($appliedFilters)"
            Text(if (showFilters) "$label · Kapat" else label)
        }
        if (appliedFilters > 0) {
            TextButton(onClick = { viewModel.filter.value = LogFilter(query = filter.query) }) {
                Text("Temizle")
            }
        }
    }
    if (showFilters) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Program günü", style = MaterialTheme.typography.labelLarge)
                FilterRow(dayOptions, filter.day) { viewModel.filter.value = filter.copy(day = it) }
                Text("Kas grubu", style = MaterialTheme.typography.labelLarge)
                FilterRow(muscleOptions, filter.muscle) { viewModel.filter.value = filter.copy(muscle = it) }
                Row(Modifier.fillMaxWidth().toggleable(
                    value = filter.includeWarmups,
                    role = Role.Switch,
                    onValueChange = { viewModel.filter.value = filter.copy(includeWarmups = it) },
                ), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Isınma setlerini göster", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = filter.includeWarmups,
                        onCheckedChange = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { value ->
            PillChip(value.ifBlank { "Tümü" }, value == selected, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun ExerciseSummaryRow(summary: ExerciseSummary, displayName: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Icon(Icons.Rounded.FitnessCenter, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleMedium)
                val description = if (summary.workingSets == 0L) "Isınma kayıtları"
                    else "${summary.workingSets} çalışma seti · ${formatMetric(summary.volume)} kg hacim"
                Text(description, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.primary)
        }
    }
}

@Composable
fun ExerciseHistoryScreen(name: String, exerciseId: String?, viewModel: WorkoutViewModel, bottomSpace: Dp, onBack: () -> Unit) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val displayName = remember(name, exerciseId, days) {
        days.orEmpty().asSequence().flatMap { it.exercises.asSequence() }
            .firstOrNull { it.id == exerciseId }?.displayName ?: WorkoutCatalog.displayName(name)
    }
    val historyFlow = remember(name, exerciseId, viewModel) { viewModel.exerciseHistory(name, exerciseId) }
    val history by historyFlow.collectAsStateWithLifecycle(null)
    var editingLog by remember { mutableStateOf<WorkoutLog?>(null) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            BackHeader("Gelişim", onBack)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomSpace),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeading(displayName) }
            val detail = history
            when {
                detail == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                detail.logs.isEmpty() -> item {
                    EmptyMessage("Kayıt bulunamadı", "Bu hareket için seçili filtrelere uyan set kalmadı.")
                }
                else -> {
                    item { ExerciseMetrics(detail) }
                    item { ExerciseProgressChart(detail.dailyProgress) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionTitle("Set geçmişi")
                            Text("En yeni kayıtlar önce", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(detail.logs, key = { it.id }) { log ->
                        LogRow(log, showContext = true) { editingLog = log }
                    }
                }
            }
        }
    }
    editingLog?.let { EditLogDialog(it, viewModel) { editingLog = null } }
}

@Composable
private fun ExerciseMetrics(detail: ExerciseHistory) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Çalışma setleri")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(detail.workingSets.toString(), "Toplam set", Modifier.weight(1f))
                MetricTile("${formatMetric(detail.volume)} kg", "Toplam hacim", Modifier.weight(1f))
            }
            val first = detail.first
            val last = detail.last
            if (first != null && last != null && first.id != last.id) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SetSnapshot("İlk set", first, Modifier.weight(1f))
                    SetSnapshot("Son set", last, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SetSnapshot(label: String, log: WorkoutLog, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatMetric(log.weight)} kg · ${log.reps} tekrar", style = MaterialTheme.typography.bodyMedium)
        Text("RIR ${log.rir ?: "—"}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
