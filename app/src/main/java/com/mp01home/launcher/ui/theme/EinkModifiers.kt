package com.mp01home.launcher.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * EINK-3 tappable: at least 56dp × 56dp, content vertically centred, no ripple (the theme's
 * LocalIndication draws nothing). Use this for every tappable element instead of raw
 * `clickable`/`combinedClickable`.
 *
 * Example: `Text("Open", Modifier.fillMaxWidth().einkTappable { open() })`
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.einkTappable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this
    .defaultMinSize(minWidth = EinkMinTouchTarget, minHeight = EinkMinTouchTarget)
    .combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClick = onLongClick,
        onClick = onClick
    )
    .wrapContentHeight(align = Alignment.CenterVertically)
