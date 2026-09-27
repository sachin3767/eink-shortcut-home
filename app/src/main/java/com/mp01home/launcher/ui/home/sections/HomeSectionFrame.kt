package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp01home.launcher.ui.theme.EinkTheme

/**
 * Standard chrome for a home section: a 1dp black rule, an optional bold title with an
 * optional right-aligned [trailing] note (e.g. "synced 10:42"), then [content].
 * Sections must not scroll (EINK-1): show at most a few rows and link to a full screen.
 */
@Composable
fun HomeSectionFrame(
    title: String?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.Black, thickness = 1.dp)
        if (title != null || trailing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = EinkTheme.dimens.rowPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (trailing != null) {
                    Text(text = trailing, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = EinkTheme.dimens.sectionGap)
        ) {
            content()
        }
    }
}

/** What an unimplemented section renders: its title and "—". */
@Composable
fun SectionPlaceholder(title: String, modifier: Modifier = Modifier) {
    HomeSectionFrame(title = title, modifier = modifier) {
        Text(text = "—", style = MaterialTheme.typography.bodyLarge)
    }
}
