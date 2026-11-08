package com.mcclabs.gimmer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CalendarMonth = DateTimeFormatter.ofPattern("LLLL yyyy", Turkish)
private val CalendarDay = DateTimeFormatter.ofPattern("d MMMM yyyy", Turkish)
private val Weekdays = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pa")

@Composable
internal fun ActivityCalendar(viewModel: WorkoutViewModel) {
    val zone = ZoneId.systemDefault()
    val currentMonth = YearMonth.now(zone)
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val month = selectedMonth?.let(YearMonth::parse) ?: currentMonth
    var selectedDay by rememberSaveable(month) { mutableIntStateOf(0) }
    val activity = key(month, zone) {
        val updates = remember(viewModel) { viewModel.monthlyActivity(month, zone) }
        updates.collectAsStateWithLifecycle(initialValue = null).value
    }
    val cells = remember(month) {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
        List(cellCount) { index ->
            val day = index - leading + 1
            if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }.chunked(7)
    }
    val colors = MaterialTheme.colorScheme
    val cellShape = RoundedCornerShape(8.dp)
    val intensity = listOf(colors.surfaceContainerHigh, colors.primaryContainer, Color(0xFF325F9C), colors.primary)

    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Antrenman takvimi")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedMonth = month.minusMonths(1).toString() }) {
                    Icon(Icons.Rounded.ChevronLeft, "Önceki ay", tint = colors.primary)
                }
                Text(
                    month.format(CalendarMonth).replaceFirstChar { it.titlecase(Turkish) },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    selectedMonth = month.plusMonths(1).takeUnless { it == currentMonth }?.toString()
                }, enabled = month < currentMonth) {
                    Icon(Icons.Rounded.ChevronRight, "Sonraki ay")
                }
            }

            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Weekdays.forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            val cell = Modifier.weight(1f).heightIn(min = 36.dp)
                            if (date == null) {
                                Spacer(cell)
                            } else {
                                val count = activity?.get(date) ?: 0L
                                val level = when {
                                    count == 0L -> 0
                                    count <= 3L -> 1
                                    count <= 8L -> 2
                                    else -> 3
                                }
                                val selected = selectedDay == date.dayOfMonth
                                Surface(
                                    modifier = cell.clip(cellShape).selectable(selected, enabled = activity != null,
                                        role = Role.Button, onClick = { selectedDay = date.dayOfMonth })
                                        .semantics {
                                            contentDescription = if (activity == null) date.format(CalendarDay)
                                            else "${date.format(CalendarDay)}, $count set"
                                        },
                                    shape = cellShape,
                                    color = intensity[level],
                                    contentColor = if (level == 3) colors.onPrimary else colors.onSurface,
                                    border = if (selected) BorderStroke(2.dp, colors.onSurface) else null,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(date.dayOfMonth.toString(), modifier = Modifier.clearAndSetSemantics {},
                                            style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val monthActivity = activity
            if (monthActivity == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val selected = selectedDay.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
                Text(
                    if (selected == null) "${monthActivity.size} antrenman günü"
                    else "${selected.format(CalendarDay)} · ${monthActivity[selected] ?: 0L} set",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
            }
            Row(
                Modifier.align(Alignment.End).clearAndSetSemantics { contentDescription = "Mavi tonlar set sayısını gösterir" },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Az", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                intensity.forEach { color ->
                    Spacer(Modifier.size(12.dp).background(color, MaterialTheme.shapes.extraSmall))
                }
                Text("Çok", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
