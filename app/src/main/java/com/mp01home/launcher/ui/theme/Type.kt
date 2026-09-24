package com.mp01home.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monospace reads cleanly on eInk at low refresh rates and keeps list alignment tidy.
private val EinkFontFamily = FontFamily.Monospace

val EinkTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = EinkFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = EinkFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    labelSmall = TextStyle(
        fontFamily = EinkFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
)
