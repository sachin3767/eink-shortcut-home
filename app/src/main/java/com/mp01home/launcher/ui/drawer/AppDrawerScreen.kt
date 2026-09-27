package com.mp01home.launcher.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.ui.components.AddToDockDialog
import com.mp01home.launcher.ui.theme.EinkMinTouchTarget
import com.mp01home.launcher.ui.theme.EinkTheme
import com.mp01home.launcher.ui.theme.einkTappable
import kotlin.math.ceil
import kotlin.math.max

/**
 * CORE-4 app drawer: text-only A–Z list, PAGED to exactly fit the screen (no scrolling, EINK-1),
 * with a search box, "‹ Prev / Next ›" buttons (a vertical swipe also turns the page) and an A–Z
 * index that jumps to a letter's page. Hidden apps (CORE-5) are filtered by the ViewModel.
 * Long-press an app for the dock menu (CORE-3).
 */
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    viewModel: AppDrawerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var requestedPage by rememberSaveable { mutableIntStateOf(0) }
    var showIndex by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        when {
            showIndex -> showIndex = false
            query.isNotEmpty() -> {
                query = ""
                requestedPage = 0
            }
            else -> onBack()
        }
    }

    val results = remember(uiState.apps, query) { DrawerPaging.search(uiState.apps, query) { it.label } }
    val dimens = EinkTheme.dimens
    val rowStyle = MaterialTheme.typography.bodyLarge
    val rowHeight: Dp = with(LocalDensity.current) {
        max(EinkMinTouchTarget.value, rowStyle.fontSize.toDp().value * LINE_HEIGHT_FACTOR + 2 * dimens.rowPadding.value).dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(dimens.gutter)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "← Home",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.einkTappable(onClick = onBack)
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = if (showIndex) "List" else "A–Z",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.einkTappable(onClickLabel = "Jump to letter") { showIndex = !showIndex }
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                requestedPage = 0
                showIndex = false
            },
            placeholder = { Text("Search apps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                results.firstOrNull()?.let { viewModel.launchApp(it.packageName) }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.rowPadding)
        )
        HorizontalDivider(color = Color.Black)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            val density = LocalDensity.current
            val rowHeightPx = with(density) { ceil(rowHeight.toPx()).toInt() }
            // The pager bar (fixed height) and its divider sit below the rows.
            val chromePx = with(density) { ceil((PAGER_HEIGHT + 1.dp).toPx()).toInt() }
            val pageSize = DrawerPaging.pageSize(constraints.maxHeight - chromePx, rowHeightPx)
            val pageCount = DrawerPaging.pageCount(results.size, pageSize)
            val page = DrawerPaging.clampPage(requestedPage, results.size, pageSize)
            val currentPage by rememberUpdatedState(page)
            val currentPageCount by rememberUpdatedState(pageCount)

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            val threshold = 48.dp.toPx()
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total < -threshold && currentPage < currentPageCount - 1) {
                                        requestedPage = currentPage + 1
                                    } else if (total > threshold && currentPage > 0) {
                                        requestedPage = currentPage - 1
                                    }
                                    total = 0f
                                },
                                onDragCancel = { total = 0f },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    total += amount
                                }
                            )
                        }
                ) {
                    when {
                        showIndex -> LetterIndex(
                            letters = DrawerPaging.letters(results.map { it.label }),
                            onLetter = { key ->
                                requestedPage = DrawerPaging.pageForKey(results.map { it.label }, key, pageSize)
                                showIndex = false
                            }
                        )
                        uiState.isLoading -> Text("Loading…", style = MaterialTheme.typography.bodyLarge)
                        results.isEmpty() -> Text(
                            text = if (query.isBlank()) "No apps" else "No apps match “$query”",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        else -> Column(modifier = Modifier.fillMaxSize()) {
                            DrawerPaging.page(results, page, pageSize).forEach { app ->
                                Text(
                                    text = app.label,
                                    style = rowStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight)
                                        .einkTappable(
                                            onClickLabel = "Open ${app.label}",
                                            onLongClick = { selectedApp = app },
                                            onClick = { viewModel.launchApp(app.packageName) }
                                        )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.Black)
                PagerBar(
                    page = page,
                    pageCount = pageCount,
                    rangeLabel = pageRangeLabel(DrawerPaging.page(results, page, pageSize)),
                    onPrevious = { requestedPage = page - 1 },
                    onNext = { requestedPage = page + 1 }
                )
            }
        }
    }

    selectedApp?.let { app ->
        AddToDockDialog(
            appLabel = app.label,
            dockIsFull = uiState.isDockFull,
            alreadyInDock = app.packageName in uiState.dockApps,
            onConfirmAdd = {
                viewModel.addToDock(app.packageName)
                selectedApp = null
            },
            onConfirmRemove = {
                viewModel.removeFromDock(app.packageName)
                selectedApp = null
            },
            onDismiss = { selectedApp = null }
        )
    }
}

/** Monospace line box ≈ 1.2× the font size; 1.4 leaves room for descenders at any font scale. */
private const val LINE_HEIGHT_FACTOR = 1.4f

@Composable
private fun PagerBar(
    page: Int,
    pageCount: Int,
    rangeLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val hasPrevious = page > 0
    val hasNext = page < pageCount - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAGER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (hasPrevious) "‹ Prev" else "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .width(PAGER_BUTTON_WIDTH)
                .einkTappable(enabled = hasPrevious, onClickLabel = "Previous page", onClick = onPrevious)
        )
        Text(
            text = if (rangeLabel.isEmpty()) "${page + 1}/$pageCount" else "$rangeLabel  ${page + 1}/$pageCount",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (hasNext) "Next ›" else "",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(PAGER_BUTTON_WIDTH)
                .einkTappable(enabled = hasNext, onClickLabel = "Next page", onClick = onNext)
        )
    }
}

private val PAGER_BUTTON_WIDTH = 96.dp
private val PAGER_HEIGHT = EinkMinTouchTarget

private fun pageRangeLabel(apps: List<AppInfo>): String {
    if (apps.isEmpty()) return ""
    val first = DrawerPaging.indexKey(apps.first().label)
    val last = DrawerPaging.indexKey(apps.last().label)
    return if (first == last) "$first" else "$first–$last"
}

/** Grid of index letters, each a ≥ 56dp target. Only letters that have apps are shown. */
@Composable
private fun LetterIndex(letters: List<Char>, onLetter: (Char) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cell = EinkMinTouchTarget + 8.dp
        val columns = (maxWidth / cell).toInt().coerceAtLeast(1)
        Column(modifier = Modifier.fillMaxWidth()) {
            letters.chunked(columns).forEach { rowLetters ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowLetters.forEach { key ->
                        Text(
                            text = key.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(cell)
                                .height(cell)
                                .einkTappable(onClickLabel = "Jump to $key") { onLetter(key) }
                        )
                    }
                }
            }
        }
    }
}
