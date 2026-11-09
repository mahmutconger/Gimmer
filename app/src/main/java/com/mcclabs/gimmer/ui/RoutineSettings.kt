package com.mcclabs.gimmer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.R
import com.mcclabs.gimmer.data.RoutineCodec
import com.mcclabs.gimmer.data.WorkoutDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RoutineSettings(viewModel: WorkoutViewModel) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<Int?>(null) }

    SettingsCard(stringResource(R.string.routine), stringResource(R.string.routine_description)) {
        Button(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_routine))
        }
        OutlinedButton(
            onClick = {
                val routine = days ?: return@OutlinedButton
                scope.launch {
                    val json = withContext(Dispatchers.Default) { RoutineCodec.encode(routine) }
                    status = try {
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.routine), json))
                        R.string.routine_copied
                    } catch (_: RuntimeException) {
                        R.string.routine_copy_failed
                    }
                }
            },
            enabled = days != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.copy_routine)) }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
    }
    if (showImport) RoutineImportSheet(viewModel, clipboard) { showImport = false }
}

@Composable
private fun RoutineImportSheet(
    viewModel: WorkoutViewModel,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<WorkoutDay>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun changeDraft(value: String) {
        if (busy) return
        preview = null
        if (value.length <= RoutineCodec.MAX_JSON_LENGTH) {
            draft = value
            error = null
        } else {
            error = R.string.routine_too_large
        }
    }

    GimmerSheet(onDismiss, dismissEnabled = !busy) {
        val focus = LocalFocusManager.current
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.import_routine))
            val candidate = preview
            if (candidate == null) {
                Text(stringResource(R.string.paste_routine))
                OutlinedButton(
                    onClick = {
                        val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (pasted.isNullOrBlank()) error = R.string.clipboard_empty
                        else changeDraft(pasted)
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.paste_clipboard)) }
                GimmerField(draft, ::changeDraft, stringResource(R.string.routine_json), modifier = Modifier.fillMaxWidth(),
                    placeholder = "{ \"routineVersion\": 1, \"days\": […] }",
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), singleLine = false, maxLines = 8)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        focus.clearFocus()
                        busy = true
                        val text = draft
                        scope.launch {
                            try {
                                preview = withContext(Dispatchers.Default) { RoutineCodec.decode(text) }
                                error = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = R.string.routine_invalid
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = draft.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (busy) stringResource(R.string.validating) else stringResource(R.string.review_routine)) }
            } else {
                val dayCount = pluralStringResource(R.plurals.day_count, candidate.size, candidate.size)
                val totalExercises = candidate.sumOf { it.exercises.size }
                val exerciseCount = pluralStringResource(R.plurals.exercise_count, totalExercises, totalExercises)
                Text("$dayCount · $exerciseCount",
                    style = MaterialTheme.typography.titleMedium)
                candidate.forEach { day ->
                    val count = pluralStringResource(R.plurals.exercise_count, day.exercises.size, day.exercises.size)
                    Text("${workoutDayName(day.name)} · ${day.localizedProgramName()} · $count",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.replace_routine_description))
                Button(
                    onClick = {
                        busy = true
                        viewModel.replaceRoutine(candidate, onDismiss).invokeOnCompletion { busy = false }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (busy) stringResource(R.string.saving) else stringResource(R.string.replace_routine)) }
                TextButton(onClick = { preview = null }, enabled = !busy) { Text(stringResource(R.string.edit_json)) }
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    }
}
