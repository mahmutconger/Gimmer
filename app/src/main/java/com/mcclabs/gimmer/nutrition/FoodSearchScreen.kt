package com.mcclabs.gimmer.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.ui.BackHeader
import com.mcclabs.gimmer.ui.EmptyMessage
import com.mcclabs.gimmer.ui.GimmerField
import com.mcclabs.gimmer.ui.GimmerSheet
import com.mcclabs.gimmer.ui.ScreenHeading
import com.mcclabs.gimmer.ui.SectionTitle
import com.mcclabs.gimmer.ui.Turkish
import com.mcclabs.gimmer.ui.formatMetric
import java.text.NumberFormat
import java.time.LocalDate

private val DecimalInput = Regex("[0-9]{0,5}([.,][0-9]{0,4})?")

internal fun formatNutritionGrams(grams: Double): String = NumberFormat.getNumberInstance(Turkish).apply {
    maximumFractionDigits = 4
}.format(grams)

@Composable
internal fun FoodSearchScreen(
    viewModel: NutritionViewModel,
    mealType: MealType,
    date: LocalDate,
    bottomSpace: Dp,
    onBack: () -> Unit,
) {
    val state by viewModel.search.collectAsStateWithLifecycle()
    val recentFoods by viewModel.recentFoods.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        viewModel.submitSearch()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp + bottomSpace),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackHeader("Beslenme", onBack) }
        item { ScreenHeading("Besin ekle", mealType.label) }
        item {
            GimmerField(
                state.query, viewModel::changeQuery, "Besin adı veya barkod",
                Modifier.fillMaxWidth(), placeholder = "Örn. yoğurt veya 869…",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = submit,
                    enabled = state.query.trim().length >= 2 && !state.loading,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Ara") }
                OutlinedButton(
                    onClick = {
                        focus.clearFocus()
                        viewModel.openEditor()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Elle ekle") }
            }
        }
        item {
            Text(
                "Besin kataloğu · Open Food Facts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.notice?.let { notice ->
            item { EmptyMessage("Katalog sonucu", notice) }
        }
        if (state.results.isNotEmpty()) {
            item { SectionTitle("Arama sonuçları") }
            items(state.results, key = { "result-${it.barcode}" }) { result ->
                FoodListRow(result.name, listOfNotNull(result.brands, result.barcode).joinToString(" · "),
                    enabled = !state.loading, onClick = { viewModel.loadProduct(result.barcode) })
            }
        }
        item { SectionTitle("Son kullanılanlar") }
        if (recentFoods.isEmpty()) {
            item { EmptyMessage("Henüz besin yok", "Kaydettiğin besinleri burada tekrar kullanabilirsin.") }
        }
        items(recentFoods, key = { "recent-${it.id}" }) { food ->
            FoodListRow(food.foodName, "${formatMetric(food.caloriesPer100g)} kcal / 100 g",
                onClick = { viewModel.openEditor(food) })
        }
    }
    state.editor?.let { editor ->
        FoodPortionSheet(editor, mealType, state.saving, viewModel::closeEditor) { food, grams ->
            viewModel.saveFood(food, mealType, date, grams, onBack)
        }
    }
}

@Composable
private fun FoodListRow(name: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.primary)
        }
    }
}

@Composable
private fun FoodPortionSheet(
    editor: FoodEditor,
    mealType: MealType,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (FoodEntry, Double) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var gramsText by rememberSaveable { mutableStateOf("") }
    val grams = gramsText.decimalValue()?.takeIf(::validConsumedGrams)
    val food = (editor.food ?: FoodEntry(
        foodName = name.trim(),
        caloriesPer100g = calories.decimalValue() ?: -1.0,
        proteinPer100g = protein.decimalValue() ?: -1.0,
        carbsPer100g = carbs.decimalValue() ?: -1.0,
        fatPer100g = fat.decimalValue() ?: -1.0,
    )).takeIf { it.isValid }
    GimmerSheet(onDismiss, dismissEnabled = !saving) {
        val focus = LocalFocusManager.current
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeading(editor.food?.foodName ?: "Elle besin ekle",
                listOfNotNull(mealType.label, editor.brands).joinToString(" · "))
            if (editor.food == null) {
                GimmerField(name, { name = it.take(200) }, "Besin adı", Modifier.fillMaxWidth())
                SectionTitle("100 g için besin değerleri")
                Text(
                    "Tarttığın hâle ait 100 g değerlerini gir. Örneğin pişmiş tavuk için pişmiş ürün değerlerini kullan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(calories, { calories = it }, "Kalori (kcal)", Modifier.weight(1f))
                    DecimalField(protein, { protein = it }, "Protein (g)", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(carbs, { carbs = it }, "Karbonhidrat (g)", Modifier.weight(1f))
                    DecimalField(fat, { fat = it }, "Yağ (g)", Modifier.weight(1f))
                }
                if (food == null && listOf(name, calories, protein, carbs, fat).all { it.isNotBlank() }) {
                    Text("100 g değerlerini kontrol et.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            } else {
                NutrientPreview("100 g için", editor.food.nutrientsFor(100.0))
            }
            DecimalField(gramsText, { gramsText = it }, "Tüketilen miktar (gram)", Modifier.fillMaxWidth())
            if (gramsText.isNotBlank() && grams == null) {
                Text("0'dan büyük ve en fazla 10.000 gram gir.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (food != null && grams != null) {
                NutrientPreview("${formatNutritionGrams(grams)} g için", food.nutrientsFor(grams))
            }
            Button(
                onClick = {
                    if (food != null && grams != null) {
                        focus.clearFocus()
                        onSave(food, grams)
                    }
                },
                enabled = !saving && food != null && grams != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (saving) "Kaydediliyor…" else "Öğüne kaydet") }
        }
    }
}

@Composable
private fun NutrientPreview(title: String, nutrients: Nutrients) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text("${formatMetric(nutrients.calories)} kcal", color = colors.primary,
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Protein ${formatMetric(nutrients.protein)} g · Karbonhidrat ${formatMetric(nutrients.carbs)} g · Yağ ${formatMetric(nutrients.fat)} g",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecimalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    GimmerField(value, { if (it.matches(DecimalInput)) onChange(it) }, label, modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

private fun String.decimalValue() = replace(',', '.').toDoubleOrNull()
