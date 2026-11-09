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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.R
import com.mcclabs.gimmer.ui.BackHeader
import com.mcclabs.gimmer.ui.EmptyMessage
import com.mcclabs.gimmer.ui.GimmerField
import com.mcclabs.gimmer.ui.GimmerSheet
import com.mcclabs.gimmer.ui.ScreenHeading
import com.mcclabs.gimmer.ui.SectionTitle
import com.mcclabs.gimmer.ui.appLocale
import com.mcclabs.gimmer.ui.formatMetric
import java.text.NumberFormat
import java.time.LocalDate

private val DecimalInput = Regex("[0-9]{0,5}([.,][0-9]{0,4})?")

@Composable
internal fun formatNutritionGrams(grams: Double): String = NumberFormat.getNumberInstance(appLocale()).apply {
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
        item { BackHeader(stringResource(R.string.nutrition), onBack) }
        item { ScreenHeading(stringResource(R.string.add_food), stringResource(mealType.label)) }
        item {
            GimmerField(
                state.query, viewModel::changeQuery, stringResource(R.string.food_name_or_barcode),
                Modifier.fillMaxWidth(), placeholder = stringResource(R.string.food_search_example),
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
                ) { Text(stringResource(R.string.search)) }
                OutlinedButton(
                    onClick = {
                        focus.clearFocus()
                        viewModel.openEditor()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.add_manually)) }
            }
        }
        item {
            Text(
                stringResource(R.string.food_catalog),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.notice?.let { notice ->
            item { EmptyMessage(stringResource(R.string.catalog_result), stringResource(notice)) }
        }
        if (state.results.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.search_results)) }
            items(state.results, key = { "result-${it.barcode}" }) { result ->
                FoodListRow(result.name, listOfNotNull(result.brands, result.barcode).joinToString(" · "),
                    enabled = !state.loading, onClick = { viewModel.loadProduct(result.barcode) })
            }
        }
        item { SectionTitle(stringResource(R.string.recent_foods)) }
        if (recentFoods.isEmpty()) {
            item { EmptyMessage(stringResource(R.string.no_foods), stringResource(R.string.reuse_foods)) }
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
            ScreenHeading(editor.food?.foodName ?: stringResource(R.string.add_food_manually),
                listOfNotNull(stringResource(mealType.label), editor.brands).joinToString(" · "))
            if (editor.food == null) {
                GimmerField(name, { name = it.take(200) }, stringResource(R.string.food_name), Modifier.fillMaxWidth())
                SectionTitle(stringResource(R.string.nutrients_per_100g))
                Text(
                    stringResource(R.string.weighed_food_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(calories, { calories = it }, stringResource(R.string.calories_kcal), Modifier.weight(1f))
                    DecimalField(protein, { protein = it }, stringResource(R.string.protein_g), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(carbs, { carbs = it }, stringResource(R.string.carbs_g), Modifier.weight(1f))
                    DecimalField(fat, { fat = it }, stringResource(R.string.fat_g), Modifier.weight(1f))
                }
                if (food == null && listOf(name, calories, protein, carbs, fat).all { it.isNotBlank() }) {
                    Text(stringResource(R.string.check_nutrients), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            } else {
                NutrientPreview(stringResource(R.string.per_100g), editor.food.nutrientsFor(100.0))
            }
            DecimalField(gramsText, { gramsText = it }, stringResource(R.string.consumed_grams), Modifier.fillMaxWidth())
            if (gramsText.isNotBlank() && grams == null) {
                Text(stringResource(R.string.invalid_grams), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (food != null && grams != null) {
                NutrientPreview(stringResource(R.string.grams_portion, formatNutritionGrams(grams)), food.nutrientsFor(grams))
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
            ) { Text(if (saving) stringResource(R.string.saving) else stringResource(R.string.save_to_meal)) }
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
                stringResource(R.string.macro_summary, formatMetric(nutrients.protein),
                    formatMetric(nutrients.carbs), formatMetric(nutrients.fat)),
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
