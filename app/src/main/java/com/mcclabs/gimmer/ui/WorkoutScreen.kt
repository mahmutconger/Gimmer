package com.mcclabs.gimmer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.data.Exercise
import com.mcclabs.gimmer.data.WorkoutDay
import com.mcclabs.gimmer.data.WorkoutLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel, bottomSpace: Dp) {
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(0) }
    var expandedExercise by rememberSaveable { mutableStateOf<String?>(null) }
    val day = viewModel.days[selectedDayIndex]
    val listState = rememberLazyListState()
    var infoExercise by remember { mutableStateOf<Exercise?>(null) }
    var editingLog by remember { mutableStateOf<WorkoutLog?>(null) }
    val zone = ZoneId.systemDefault()
    val today by produceState(LocalDate.now(zone), zone) {
        while (true) {
            value = LocalDate.now(zone)
            delay(30_000)
        }
    }
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val expandedName = day.exercises.firstOrNull { it.id == expandedExercise }?.name
    val logs = if (expandedName == null) emptyList() else key(expandedName, day.id, start, end) {
        val workoutLogs = remember(viewModel) { viewModel.workoutLogs(expandedName, day.name, start, end) }
        workoutLogs.collectAsStateWithLifecycle(emptyList()).value
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)) {
            ScreenHeading("Antrenman", "Programını seç ve setlerini kaydet.")
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            viewModel.days.forEachIndexed { index, item ->
                PillChip(item.name, selectedDayIndex == index, onClick = {
                    if (selectedDayIndex != index) {
                        selectedDayIndex = index
                        expandedExercise = null
                        listState.requestScrollToItem(0)
                    }
                })
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp + bottomSpace),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ProgramCard(day) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Hareketler")
                    Text("${day.exercises.size} hareket", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            day.exercises.forEachIndexed { index, exercise ->
                val expanded = expandedExercise == exercise.id
                item(key = exercise.id) {
                    WorkoutExerciseCard(
                        exercise = exercise,
                        index = index,
                        dayName = day.name,
                        viewModel = viewModel,
                        expanded = expanded,
                        bottomSpace = bottomSpace,
                        onToggle = {
                            expandedExercise = if (expanded) null else exercise.id
                        },
                        onInfo = { infoExercise = exercise },
                    )
                }
                if (expanded && logs.isNotEmpty()) {
                    item(key = "sets:${exercise.id}") { SectionTitle("Bugünkü setler") }
                    items(logs, key = { it.id }) { log ->
                        LogRow(log) { editingLog = log }
                    }
                }
            }
        }
    }
    infoExercise?.let { ExerciseInfoSheet(it) { infoExercise = null } }
    editingLog?.let { EditLogDialog(it, viewModel) { editingLog = null } }
}

@Composable
private fun ProgramCard(day: WorkoutDay) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.horizontalGradient(listOf(colors.primaryContainer, colors.surfaceContainer)))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(day.name, color = colors.primary, style = MaterialTheme.typography.labelLarge)
            Text(day.programName, color = colors.onSurface, style = MaterialTheme.typography.headlineMedium)
        }
        Surface(shape = CircleShape, color = colors.primary.copy(alpha = 0.14f), contentColor = colors.primary) {
            Icon(Icons.Rounded.FitnessCenter, contentDescription = null,
                modifier = Modifier.padding(14.dp).size(24.dp))
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    exercise: Exercise,
    index: Int,
    dayName: String,
    viewModel: WorkoutViewModel,
    expanded: Boolean,
    bottomSpace: Dp,
    onToggle: () -> Unit,
    onInfo: () -> Unit,
) {
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var rir by rememberSaveable { mutableIntStateOf(2) }
    var isWarmup by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val visibility = remember { MutableTransitionState(expanded) }.apply { targetState = expanded }
    val formRequester = remember { BringIntoViewRequester() }
    var formSize by remember { mutableStateOf(IntSize.Zero) }
    val clearance = with(LocalDensity.current) { (bottomSpace + 8.dp).toPx() }
    LaunchedEffect(expanded) {
        if (expanded) {
            val size = snapshotFlow {
                formSize.takeIf { visibility.isIdle && visibility.currentState && it != IntSize.Zero }
            }.filterNotNull().first()
            formRequester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + clearance))
        }
    }
    val colors = MaterialTheme.colorScheme
    val target = "${exercise.targetSets} set × ${exercise.targetReps}" +
        if (exercise.id == "side_plank") "" else " tekrar"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Açık" else "Kapalı" },
            color = if (expanded) colors.surfaceContainerHigh else colors.surfaceContainer,
            contentColor = colors.onSurface,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (expanded) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (expanded) colors.onPrimary else colors.primary,
                ) {
                    Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text("${exercise.muscleGroup} · $target", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null, tint = colors.primary)
            }
        }
        AnimatedVisibility(
            visibleState = visibility,
            modifier = Modifier.bringIntoViewRequester(formRequester).onSizeChanged { formSize = it },
            enter = expandVertically(animationSpec = tween(260), expandFrom = Alignment.Top) +
                fadeIn(animationSpec = tween(200, delayMillis = 50)),
            exit = shrinkVertically(animationSpec = tween(220), shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = tween(130)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Set girişi") }
                    IconButton(onClick = onInfo) {
                        Icon(Icons.Rounded.Info, contentDescription = "Hareket bilgisi", tint = colors.primary)
                    }
                }
                PerformanceFields(weight, reps, rir, isWarmup, note, { weight = it }, { reps = it },
                    { rir = it }, { isWarmup = it }, { note = it })
                Button(
                    enabled = !saving && validPerformance(weight, reps),
                    onClick = {
                        saving = true
                        val job = viewModel.saveWorkout(exercise, dayName, weight.replace(',', '.').toDouble(),
                            reps.toInt(), rir, isWarmup, note) {
                            reps = ""
                            note = ""
                        }
                        job.invokeOnCompletion { saving = false }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(if (saving) "Kaydediliyor…" else "Seti kaydet")
                }
            }
        }
    }
}
