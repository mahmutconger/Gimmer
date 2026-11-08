package com.mcclabs.gimmer.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.ui.GimmerField
import com.mcclabs.gimmer.ui.GimmerSheet
import com.mcclabs.gimmer.ui.ScreenHeading
import com.mcclabs.gimmer.ui.SectionTitle
import com.mcclabs.gimmer.ui.Turkish
import com.mcclabs.gimmer.ui.formatMetric
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val NutritionDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Turkish)
private val ProteinTint = Color(0xFFBDAEFF)
private val CarbsTint = Color(0xFF92D8CB)
private val FatTint = Color(0xFFF1C582)

@Composable
internal fun NutritionScreen(
    viewModel: NutritionViewModel,
    bottomSpace: Dp,
    onAddFood: (MealType, LocalDate) -> Unit,
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val mealsByType = remember(day.meals) { day.meals.orEmpty().groupBy { it.log.mealType } }
    var editingTargets by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LoggedMeal?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp + bottomSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeading("Beslenme", "Öğünlerini kaydet, günlük hedeflerini takip et.") }
        item { NutritionDateBar(day, viewModel::selectDate) }
        item { NutritionDashboard(day, targets) { editingTargets = true } }
        item { SectionTitle("Öğünler") }
        items(MealType.entries.size, key = { MealType.entries[it].name }) { index ->
            val mealType = MealType.entries[index]
            MealCard(
                type = mealType,
                meals = mealsByType[mealType].orEmpty(),
                loading = day.meals == null,
                onAdd = { onAddFood(mealType, day.date) },
                onDelete = { pendingDelete = it },
            )
        }
    }
    if (editingTargets) {
        TargetEditor(targets, onDismiss = { editingTargets = false }) {
            viewModel.saveTargets(it)
            editingTargets = false
        }
    }
    pendingDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Besin kaydı silinsin mi?") },
            text = { Text(meal.food.foodName) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeal(meal.log.id)
                    pendingDelete = null
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun NutritionDateBar(day: NutritionDay, onDate: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onDate(day.date.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Önceki gün")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (day.date == day.today) {
                    Text("Bugün", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
                Text(day.date.format(NutritionDateFormat), style = MaterialTheme.typography.titleSmall)
            }
            IconButton(enabled = day.date < day.today, onClick = { onDate(day.date.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Sonraki gün")
            }
        }
    }
}

@Composable
private fun NutritionDashboard(day: NutritionDay, targets: NutritionTargets, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainer)))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Günlük hedefler", style = MaterialTheme.typography.titleLarge)
                    Text("Tüketilen / Hedef", style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant)
                }
                TextButton(onClick = onEdit) { Text("Düzenle") }
            }
            if (day.meals == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val totals = day.totals
                NutritionProgress("Kalori", totals.calories, targets.calories, "kcal", colors.primary)
                HorizontalDivider(color = colors.outlineVariant)
                NutritionProgress("Protein", totals.protein, targets.protein, "g", ProteinTint)
                NutritionProgress("Karbonhidrat", totals.carbs, targets.carbs, "g", CarbsTint)
                NutritionProgress("Yağ", totals.fat, targets.fat, "g", FatTint)
            }
        }
    }
}

@Composable
private fun NutritionProgress(label: String, value: Double, target: Int, unit: String, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("${formatMetric(value)} / ${formatMetric(target.toDouble())} $unit",
                style = MaterialTheme.typography.labelLarge, color = tint)
        }
        LinearProgressIndicator(
            progress = { (value / target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MealCard(
    type: MealType,
    meals: List<LoggedMeal>,
    loading: Boolean,
    onAdd: () -> Unit,
    onDelete: (LoggedMeal) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                    if (!loading) {
                        val detail = if (meals.isEmpty()) "Henüz kayıt yok"
                            else "${formatMetric(meals.totals().calories)} kcal"
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onAdd, enabled = !loading) {
                    Icon(Icons.Rounded.Add, "${type.label} öğününe besin ekle", tint = colors.primary)
                }
            }
            meals.forEach { meal ->
                HorizontalDivider(color = colors.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(meal.food.foodName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${formatNutritionGrams(meal.log.consumedGrams)} g · ${formatMetric(meal.nutrients.calories)} kcal",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(meal) }) {
                        Icon(Icons.Rounded.DeleteOutline, "${meal.food.foodName} kaydını sil",
                            tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetEditor(initial: NutritionTargets, onDismiss: () -> Unit, onSave: (NutritionTargets) -> Unit) {
    var calories by rememberSaveable { mutableStateOf(initial.calories.toString()) }
    var protein by rememberSaveable { mutableStateOf(initial.protein.toString()) }
    var carbs by rememberSaveable { mutableStateOf(initial.carbs.toString()) }
    var fat by rememberSaveable { mutableStateOf(initial.fat.toString()) }
    val targets = NutritionTargets(
        calories.toIntOrNull() ?: 0, protein.toIntOrNull() ?: 0, carbs.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0,
    )
    GimmerSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeading("Günlük hedefler", "Kalori ve makro hedeflerini belirle.")
            TargetField(calories, { calories = it }, "Kalori (kcal)")
            TargetField(protein, { protein = it }, "Protein (g)")
            TargetField(carbs, { carbs = it }, "Karbonhidrat (g)")
            TargetField(fat, { fat = it }, "Yağ (g)")
            Button(
                onClick = { onSave(targets) },
                enabled = targets.isValid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Hedefleri kaydet") }
        }
    }
}

@Composable
private fun TargetField(value: String, onChange: (String) -> Unit, label: String) {
    GimmerField(
        value, { if (it.length <= 5 && it.all { char -> char in '0'..'9' }) onChange(it) },
        label, Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
