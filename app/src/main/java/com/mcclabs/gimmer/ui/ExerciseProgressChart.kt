package com.mcclabs.gimmer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.dashed
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val ChartDay = DateTimeFormatter.ofPattern("d MMM", Turkish)
private val ChartFullDate = DateTimeFormatter.ofPattern("d MMM yyyy", Turkish)
private val RepsColor = Color(0xFF69DECC)

@Composable
internal fun ExerciseProgressChart(days: List<DailyExerciseProgress>) {
    val colors = MaterialTheme.colorScheme
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("Hareket gelişimi")
            if (days.isEmpty()) {
                Text("Grafik için bu harekete bir çalışma seti kaydet.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                Text("Her nokta o günün en ağır çalışma setini gösterir.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChartKey("Ağırlık (kg)", colors.primary)
                    ChartKey("Tekrar", RepsColor)
                }
                ProvideVicoTheme(rememberM3VicoTheme()) {
                    ProgressPlot(days) { selectedDate = it.date.toEpochDay() }
                }
                val selected = remember(days, selectedDate) {
                    days.firstOrNull { it.date.toEpochDay() == selectedDate }
                }
                val point = selected ?: days.last()
                val label = if (selected != null) "Seçili gün" else "Son gün"
                Text(
                    "$label · ${point.date.format(ChartFullDate)} · ${formatMetric(point.weight)} kg · ${point.reps} tekrar",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartKey(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Spacer(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ProgressPlot(days: List<DailyExerciseProgress>, onDaySelected: (DailyExerciseProgress) -> Unit) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(days) {
        producer.runTransaction {
            lineSeries { series(days.map { it.weight }) }
            lineSeries { series(days.map { it.reps }) }
        }
    }

    val colors = MaterialTheme.colorScheme
    val weight = colors.primary
    val repLimit = remember(days) { ceil(days.maxOf { it.reps } / 4.0) * 4.0 }
    val repRange = remember(repLimit) { CartesianLayerRangeProvider.fixed(maxY = repLimit) }
    val labelCount = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) }
    val yFormatter = remember { CartesianValueFormatter { _, value, _ -> formatMetric(value) } }
    val dayFormatter = remember(days) {
        CartesianValueFormatter { _, value, _ -> days.getOrNull(value.toInt())?.date?.format(ChartDay).orEmpty() }
    }
    val markerFormatter = remember(days) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val day = targets.firstOrNull()?.x?.toInt()?.let(days::getOrNull)
            day?.let { "${it.date.format(ChartFullDate)} · ${formatMetric(it.weight)} kg · ${it.reps} tekrar" }.orEmpty()
        }
    }
    val selectDay by rememberUpdatedState(onDaySelected)
    val markerListener = remember(days) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.x?.toInt()?.let(days::getOrNull)?.let(selectDay)
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onShown(marker, targets)
            }
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            color = colors.onSurface,
            textSize = 11.sp,
            padding = Insets(horizontalDp = 7f, verticalDp = 4f),
            background = rememberShapeComponent(fill(colors.surfaceContainerHighest), CorneredShape.rounded(8f)),
        ),
        valueFormatter = markerFormatter,
        guideline = rememberLineComponent(fill(colors.outline), thickness = 1.dp),
    )

    val weights = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(weight)),
            stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.5.dp, cap = StrokeCap.Round),
            pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.point(
                rememberShapeComponent(fill(weight), CorneredShape.Pill), size = 9.dp,
            )),
        )),
        verticalAxisPosition = Axis.Position.Vertical.Start,
        pointSpacing = 48.dp,
    )
    val reps = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(RepsColor)),
            stroke = LineCartesianLayer.LineStroke.dashed(thickness = 2.dp, dashLength = 5.dp, gapLength = 4.dp),
            pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.point(
                rememberShapeComponent(fill(RepsColor), CorneredShape.Pill), size = 5.dp,
            )),
        )),
        verticalAxisPosition = Axis.Position.Vertical.End,
        pointSpacing = 48.dp,
        rangeProvider = repRange,
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            weights, reps,
            startAxis = VerticalAxis.rememberStart(
                label = rememberAxisLabelComponent(color = weight),
                line = null, tick = null, itemPlacer = labelCount, valueFormatter = yFormatter,
            ),
            endAxis = VerticalAxis.rememberEnd(
                label = rememberAxisLabelComponent(color = RepsColor),
                line = null, tick = null, guideline = null, itemPlacer = labelCount, valueFormatter = yFormatter,
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = rememberAxisLabelComponent(color = colors.onSurfaceVariant),
                line = null, tick = null, guideline = null, valueFormatter = dayFormatter,
            ),
            marker = marker,
            markerVisibilityListener = markerListener,
            layerPadding = { cartesianLayerPadding(scalableStart = 24.dp, scalableEnd = 24.dp) },
            getXStep = { 1.0 },
        ),
        modelProducer = producer,
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
        modifier = Modifier.fillMaxWidth().height(195.dp).semantics {
            contentDescription = "Ağırlık ve tekrar grafiği. ${days.size} antrenman günü, " +
                "${days.first().date.format(ChartFullDate)} ile ${days.last().date.format(ChartFullDate)} arası."
        },
        animateIn = false,
    )
}
