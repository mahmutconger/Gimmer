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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: WorkoutViewModel, bottomSpace: Dp) {
    val context = LocalContext.current
    val json by viewModel.exportedJson.collectAsStateWithLifecycle()
    val saving by viewModel.savingJson.collectAsStateWithLifecycle()
    var copyStatus by rememberSaveable { mutableStateOf("") }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::saveJsonDocument)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading("Ayarlar", "Verilerini yedekle ve dışa aktar.")
        DriveSettings(viewModel)
        RoutineSettings(viewModel)
        SettingsCard("Veri aktarımı", "Antrenman, beslenme ve ölçüm geçmişini dışa aktar. Gelişim fotoğrafları Drive yedeğine eklenir.") {
            Button(
                onClick = { createDocument.launch("gimmer-yedek-${LocalDate.now()}.json") },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (saving) "Dosya kaydediliyor…" else "JSON dosyasını indir") }
            OutlinedButton(onClick = {
                copyStatus = ""
                viewModel.prepareExport()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("JSON önizlemesini göster")
            }
            if (json.isNotBlank()) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    copyStatus = try {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Gimmer kayıtları", json))
                        "JSON panoya kopyalandı."
                    } catch (_: RuntimeException) {
                        "Panoya kopyalanamadı. JSON dosyasını indirebilirsin."
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Panoya kopyala") }

                if (copyStatus.isNotBlank()) Text(copyStatus, style = MaterialTheme.typography.bodySmall)
                val preview = remember(json) { if (json.length > 4000) json.take(4000) + "\n…" else json }
                Text("JSON önizlemesi", style = MaterialTheme.typography.titleSmall)
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
