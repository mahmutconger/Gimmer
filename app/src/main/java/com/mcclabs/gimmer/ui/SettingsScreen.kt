package com.mcclabs.gimmer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.R
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: WorkoutViewModel, bottomSpace: Dp) {
    val context = LocalContext.current
    val json by viewModel.exportedJson.collectAsStateWithLifecycle()
    val saving by viewModel.savingJson.collectAsStateWithLifecycle()
    var copyStatus by rememberSaveable { mutableStateOf<Int?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::saveJsonDocument)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading(stringResource(R.string.settings), stringResource(R.string.settings_description))
        LanguageSettings(viewModel)
        DriveSettings(viewModel)
        RoutineSettings(viewModel)
        SettingsCard(stringResource(R.string.data_export), stringResource(R.string.data_export_description)) {
            Button(
                onClick = { createDocument.launch(context.getString(R.string.backup_filename, LocalDate.now().toString())) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (saving) stringResource(R.string.saving_file) else stringResource(R.string.download_json)) }
            OutlinedButton(onClick = {
                copyStatus = null
                viewModel.prepareExport()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.show_json_preview))
            }
            if (json.isNotBlank()) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    copyStatus = try {
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clipboard_logs), json))
                        R.string.json_copied
                    } catch (_: RuntimeException) {
                        R.string.json_copy_failed
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_clipboard)) }

                copyStatus?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                val preview = remember(json) { if (json.length > 4000) json.take(4000) + "\n…" else json }
                Text(stringResource(R.string.json_preview), style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    SelectionContainer {
                        Text(preview, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()).padding(14.dp),
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsCard(title: String, description: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            content()
        }
    }
}
