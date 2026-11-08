package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mcclabs.gimmer.data.BodyweightLog
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ChartDate = DateTimeFormatter.ofPattern("dd MMM", Turkish)

@Composable
fun BodyweightSection(logs: List<BodyweightLog>, viewModel: WorkoutViewModel) {
    var showEntry by rememberSaveable { mutableStateOf(false) }
    var showRecent by rememberSaveable { mutableStateOf(false) }
    val current = logs.lastOrNull()
    val colors = MaterialTheme.colorScheme

    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Vücut ağırlığı")
            if (current != null) {
                Text("${formatMetric(current.weight)} kg", color = colors.primary,
                    style = MaterialTheme.typography.headlineMedium)
                Text("Son ölçüm · ${LocalDate.parse(current.date).format(ChartDate)}",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                Text("Henüz ölçüm yok.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (logs.size >= 2) BodyweightChart(logs)
            Button(onClick = { showEntry = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ölçüm kaydet")
            }
            if (logs.isNotEmpty()) {
                TextButton(onClick = { showRecent = !showRecent }) {
                    Text(if (showRecent) "Son ölçümleri gizle" else "Son ölçümler")
                }
                if (showRecent) {
                    logs.takeLast(7).asReversed().forEach { log ->
                        Text("${log.date} · ${formatMetric(log.weight)} kg", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (showEntry) {
        val today = LocalDate.now().toString()
        val todaysWeight = if (current?.date == today) current.weight else null
        BodyweightEntry(todaysWeight, viewModel, onDismiss = { showEntry = false })
    }
}

@Composable
private fun BodyweightEntry(todaysWeight: Double?, viewModel: WorkoutViewModel, onDismiss: () -> Unit) {
    var weight by rememberSaveable { mutableStateOf(todaysWeight?.toString().orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val number = weight.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (todaysWeight == null) "Ölçüm kaydet" else "Bugünkü ölçümü güncelle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GimmerField(weight, { if (it.length <= 8) weight = it }, label = "Ağırlık (kg)",
                    placeholder = "Örn. 65", modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Her gün için bir ölçüm kaydedilir.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    viewModel.saveBodyweight(requireNotNull(number), onDismiss).invokeOnCompletion { saving = false }
                },
                enabled = !saving && number != null && number in 1.0..500.0,
            ) { Text(if (saving) "Kaydediliyor…" else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Vazgeç") } },
    )
}

@Composable
private fun BodyweightChart(logs: List<BodyweightLog>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(logs) {
        val (dates, weights) = withContext(Dispatchers.Default) {
            logs.map { LocalDate.parse(it.date).toEpochDay() } to logs.map { it.weight }
        }
        producer.runTransaction { lineSeries { series(x = dates, y = weights) } }
    }
    val formatter = remember {
        CartesianValueFormatter { _, value, _ -> LocalDate.ofEpochDay(value.toLong()).format(ChartDate) }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = formatter)),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(150.dp).semantics {
                contentDescription = "Vücut ağırlığı grafiği. ${logs.first().date}: ${logs.first().weight} kilogram. " +
                    "${logs.last().date}: ${logs.last().weight} kilogram. Dikey eksen kilogram, yatay eksen tarih."
            },
        )
    }
}
