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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var status by rememberSaveable { mutableStateOf("") }

    SettingsCard("Antrenman programı", "Programını JSON olarak kopyala veya düzenlediğin programı içe aktar.") {
        Button(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Program JSON’u içe aktar")
        }
        OutlinedButton(
            onClick = {
                val routine = days ?: return@OutlinedButton
                scope.launch {
                    val json = withContext(Dispatchers.Default) { RoutineCodec.encode(routine) }
                    status = try {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Antrenman programı", json))
                        "Program JSON’u panoya kopyalandı."
                    } catch (_: RuntimeException) {
                        "Program panoya kopyalanamadı."
                    }
                }
            },
            enabled = days != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Program JSON’unu kopyala") }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
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
    var error by remember { mutableStateOf("") }

    fun changeDraft(value: String) {
        if (busy) return
        preview = null
        if (value.length <= RoutineCodec.MAX_JSON_LENGTH) {
            draft = value
            error = ""
        } else {
            error = "Program JSON’u çok büyük."
        }
    }

    GimmerSheet(onDismiss, dismissEnabled = !busy) {
        val focus = LocalFocusManager.current
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Program JSON’u içe aktar")
            val candidate = preview
            if (candidate == null) {
                Text("Gimmer biçimindeki program JSON’unu yapıştır.")
                OutlinedButton(
                    onClick = {
                        val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (pasted.isNullOrBlank()) error = "Panoda program metni bulunamadı."
                        else changeDraft(pasted)
                    },
                    enabled = !busy,
                ) { Text("Panodan yapıştır") }
                GimmerField(draft, ::changeDraft, "Program JSON’u", modifier = Modifier.fillMaxWidth(),
                    placeholder = "{ \"routineVersion\": 1, \"days\": […] }",
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), singleLine = false, maxLines = 8)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        focus.clearFocus()
                        busy = true
                        val text = draft
                        scope.launch {
                            try {
                                preview = withContext(Dispatchers.Default) { RoutineCodec.decode(text) }
                                error = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "Program doğrulanamadı. Günleri, hareket kimliklerini, hedefleri ve tür bilgilerini kontrol et."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = draft.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (busy) "Doğrulanıyor…" else "Programı incele") }
            } else {
                Text("${candidate.size} gün · ${candidate.sumOf { it.exercises.size }} hareket",
                    style = MaterialTheme.typography.titleMedium)
                candidate.forEach { day ->
                    Text("${day.name} · ${day.programName} · ${day.exercises.size} hareket",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Text("Onaylarsan mevcut programın bu programla değişir. Set ve ölçüm geçmişin korunur.")
                Button(
                    onClick = {
                        busy = true
                        viewModel.replaceRoutine(candidate, onDismiss).invokeOnCompletion { busy = false }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (busy) "Kaydediliyor…" else "Programı değiştir") }
                TextButton(onClick = { preview = null }, enabled = !busy) { Text("JSON’u düzenle") }
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") }
        }
    }
}
