package com.mcclabs.gimmer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mcclabs.gimmer.R

@OptIn(ExperimentalTextApi::class)
private fun manropeFont(weight: FontWeight) = Font(
    R.font.manrope, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Manrope = FontFamily(
    manropeFont(FontWeight.Normal),
    manropeFont(FontWeight.Medium),
    manropeFont(FontWeight.SemiBold),
    manropeFont(FontWeight.Bold),
)

private fun TextStyle.manrope(weight: FontWeight = fontWeight ?: FontWeight.Normal) =
    copy(fontFamily = Manrope, fontWeight = weight)

internal val GimmerTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.manrope(FontWeight.SemiBold),
        displayMedium = base.displayMedium.manrope(FontWeight.SemiBold),
        displaySmall = base.displaySmall.manrope(FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.manrope(FontWeight.Bold).copy(letterSpacing = (-0.7).sp),
        headlineMedium = base.headlineMedium.manrope(FontWeight.Bold),
        headlineSmall = base.headlineSmall.manrope(FontWeight.SemiBold),
        titleLarge = base.titleLarge.manrope(FontWeight.SemiBold),
        titleMedium = base.titleMedium.manrope(FontWeight.Bold),
        titleSmall = base.titleSmall.manrope(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.manrope(),
        bodyMedium = base.bodyMedium.manrope(),
        bodySmall = base.bodySmall.manrope(),
        labelLarge = base.labelLarge.manrope(FontWeight.Bold),
        labelMedium = base.labelMedium.manrope(FontWeight.SemiBold),
        labelSmall = base.labelSmall.manrope(FontWeight.SemiBold),
    )
}
