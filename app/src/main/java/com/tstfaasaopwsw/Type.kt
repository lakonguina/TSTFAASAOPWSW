package com.tstfaasaopwsw

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Inter — a near-SF grotesque — as the app's one type family, loaded from the single bundled
 * variable font via weight axes. The scale leans Apple: big, tight, confident titles and
 * calm body text. Everything reads through [AppTypography] in [ForcelistTheme].
 */
private fun inter(weight: Int) = Font(
    R.font.inter_var,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

internal val AppTypography = Typography().run {
    val f = Inter
    copy(
        displayLarge = displayLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = displayMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displaySmall = displaySmall.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        // Apple "large title": 34sp, heavy, tight.
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
        titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f),
        bodySmall = bodySmall.copy(fontFamily = f, color = bodySmall.color),
        labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        labelSmall = labelSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    )
}
