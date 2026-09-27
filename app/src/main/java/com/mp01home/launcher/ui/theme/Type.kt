package com.mp01home.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Monospace reads cleanly on eInk at low refresh rates and keeps list alignment tidy.
private val EinkFontFamily = FontFamily.Monospace

/** EINK-3: body text never renders below this, whatever the font-scale setting. */
const val MIN_BODY_SP = 18f

private fun scaled(baseSp: Float, scale: Float, minSp: Float = 0f): TextUnit =
    maxOf(baseSp * scale, minSp).sp

/**
 * Typography scaled by the UX-7 font-scale setting. Body/title/label-large styles are clamped to
 * ≥ 18sp (EINK-3); only labelSmall/labelMedium (metadata such as "synced 10:42") may go smaller.
 */
fun einkTypography(fontScale: Float = 1f): Typography {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = EinkFontFamily, fontWeight = weight, fontSize = size)

    return Typography(
        displayLarge = style(scaled(48f, fontScale, MIN_BODY_SP), FontWeight.Bold),
        headlineLarge = style(scaled(34f, fontScale, MIN_BODY_SP), FontWeight.Bold),
        titleLarge = style(scaled(28f, fontScale, MIN_BODY_SP), FontWeight.Bold),
        titleMedium = style(scaled(22f, fontScale, MIN_BODY_SP), FontWeight.Bold),
        titleSmall = style(scaled(20f, fontScale, MIN_BODY_SP), FontWeight.Bold),
        bodyLarge = style(scaled(22f, fontScale, MIN_BODY_SP)),
        bodyMedium = style(scaled(20f, fontScale, MIN_BODY_SP)),
        bodySmall = style(scaled(18f, fontScale, MIN_BODY_SP)),
        labelLarge = style(scaled(20f, fontScale, MIN_BODY_SP), FontWeight.Bold),
        labelMedium = style(scaled(16f, fontScale)),
        labelSmall = style(scaled(14f, fontScale))
    )
}

/** Default (scale 1.0) typography, kept for previews and legacy callers. */
val EinkTypography: Typography = einkTypography(1f)
