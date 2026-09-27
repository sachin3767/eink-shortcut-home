package com.mp01home.launcher.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mp01home.launcher.data.settings.LayoutDensity

private val EinkColorScheme = lightColorScheme(
    primary = EinkBlack,
    onPrimary = EinkWhite,
    secondary = EinkBlack,
    onSecondary = EinkWhite,
    background = EinkWhite,
    onBackground = EinkBlack,
    surface = EinkWhite,
    onSurface = EinkBlack,
    surfaceVariant = EinkWhite,
    onSurfaceVariant = EinkBlack,
    outline = EinkBlack
)

/** EINK-3: minimum size of anything tappable. */
val EinkMinTouchTarget: Dp = 56.dp

/** UX-7: spacing derived from the density setting. Read via [EinkTheme.dimens]. */
@Immutable
data class EinkDimens(
    /** Outer screen padding. */
    val gutter: Dp,
    /** Vertical gap between home sections. */
    val sectionGap: Dp,
    /** Vertical padding inside list rows. */
    val rowPadding: Dp,
    val minTouchTarget: Dp = EinkMinTouchTarget
) {
    companion object {
        fun forDensity(density: LayoutDensity): EinkDimens = EinkDimens(
            gutter = (16f * density.spacingScale).dp,
            sectionGap = (12f * density.spacingScale).dp,
            rowPadding = (6f * density.spacingScale).dp
        )
    }
}

val LocalEinkDimens = staticCompositionLocalOf { EinkDimens.forDensity(LayoutDensity.NORMAL) }

object EinkTheme {
    val dimens: EinkDimens
        @Composable @ReadOnlyComposable get() = LocalEinkDimens.current
}

/**
 * Pure black-on-white, no ripple/hover indication: eInk panels can't show a fading ripple
 * animation cleanly, and repeated partial refreshes cause visible ghosting (EINK-1/2).
 *
 * The locals are provided *inside* MaterialTheme because MaterialTheme itself provides a
 * ripple as LocalIndication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MP01HomeTheme(
    fontScale: Float = 1f,
    density: LayoutDensity = LayoutDensity.NORMAL,
    content: @Composable () -> Unit
) {
    val typography = remember(fontScale) { einkTypography(fontScale) }
    val dimens = remember(density) { EinkDimens.forDensity(density) }
    MaterialTheme(colorScheme = EinkColorScheme, typography = typography) {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalRippleConfiguration provides null,
            LocalEinkDimens provides dimens,
            content = content
        )
    }
}

/** An Indication that draws nothing. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}
