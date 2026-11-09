package com.mcclabs.gimmer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mcclabs.gimmer.R
import java.math.BigDecimal

internal fun parseWeight(value: String): Double? =
    value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10_000.0 }

internal fun inputWeight(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')

internal fun stepWeight(value: String, amount: Double): String {
    val current = BigDecimal.valueOf(parseWeight(value) ?: 0.0)
    return inputWeight((current + BigDecimal.valueOf(amount)).toDouble().coerceIn(0.0, 10_000.0))
}

@Composable
internal fun StepperInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    keyboardType: KeyboardType,
    decreaseLabel: String,
    increaseLabel: String,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onDecrease, enabled = enabled && decreaseEnabled, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.Remove, decreaseLabel)
        }
        GimmerField(value, { if (it.length <= 10) onChange(it) }, label = label,
            modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon, enabled = enabled)
        FilledTonalIconButton(onClick = onIncrease, enabled = enabled && increaseEnabled, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.Add, increaseLabel)
        }
    }
}

internal data class PlateCount(val kilograms: Double, val countPerSide: Int)
internal data class PlateLoad(val totalWeight: Double, val plates: List<PlateCount>)

internal fun calculatePlateLoad(target: Double): PlateLoad? {
    if (!target.isFinite() || target !in 20.0..10_000.0) return null
    // One minimum plate on both sides adds 2.5 kg to the total.
    val totalUnits = (BigDecimal.valueOf(target) - BigDecimal("20"))
        .divideToIntegralValue(BigDecimal("2.5")).toInt()
    var unitsPerSide = totalUnits
    val plates = buildList {
        for (size in listOf(16, 8, 4, 2, 1)) {
            val count = unitsPerSide / size
            if (count > 0) add(PlateCount(size * 1.25, count))
            unitsPerSide %= size
        }
    }
    return PlateLoad(20.0 + totalUnits * 2.5, plates)
}

@Composable
internal fun PlateCalculatorSheet(target: Double?, onDismiss: () -> Unit) {
    val load = remember(target) { target?.let(::calculatePlateLoad) }
    val colors = MaterialTheme.colorScheme
    GimmerSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.plate_calculator))
            Text(stringResource(R.string.bar_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            when {
                target == null -> Text(stringResource(R.string.enter_target_weight))
                load == null -> Text(stringResource(R.string.minimum_bar_weight))
                else -> {
                    Text(stringResource(R.string.total_weight, formatMetric(load.totalWeight)),
                        style = MaterialTheme.typography.headlineMedium, color = colors.primary)
                    if (load.totalWeight != target) {
                        Text(stringResource(R.string.target_weight_unavailable, formatMetric(target)), style = MaterialTheme.typography.bodySmall)
                    }
                    if (load.plates.isEmpty()) Text(stringResource(R.string.bar_only))
                    else {
                        Text(stringResource(R.string.each_side), style = MaterialTheme.typography.titleSmall)
                        load.plates.forEach { plate ->
                            val tint = lerp(colors.primaryContainer, colors.primary, (plate.kilograms / 20).toFloat() * .35f)
                            Surface(shape = MaterialTheme.shapes.medium, color = tint) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${formatMetric(plate.kilograms)} kg", style = MaterialTheme.typography.titleMedium)
                                    Text(pluralStringResource(R.plurals.plate_count, plate.countPerSide, plate.countPerSide), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
        }
    }
}
