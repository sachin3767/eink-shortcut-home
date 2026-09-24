package com.mp01home.launcher.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

private val EinkColorScheme = lightColorScheme(
    primary = EinkBlack,
    onPrimary = EinkWhite,
    background = EinkWhite,
    onBackground = EinkBlack,
    surface = EinkWhite,
    onSurface = EinkBlack
)

/**
 * Pure black-on-white, no ripple/hover indication: eInk panels can't show a fading
 * ripple animation cleanly, and repeated partial refreshes cause visible ghosting.
 */
@Composable
fun MP01HomeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides NoIndication) {
        MaterialTheme(
            colorScheme = EinkColorScheme,
            typography = EinkTypography,
            content = content
        )
    }
}

private object NoIndication : Indication {
    private object NoIndicationInstance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() {
            drawContent()
        }
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance =
        NoIndicationInstance
}
