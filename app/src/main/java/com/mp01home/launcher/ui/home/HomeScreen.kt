package com.mp01home.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.settings.Gesture
import com.mp01home.launcher.data.settings.GestureAction
import com.mp01home.launcher.data.settings.HomeSection
import com.mp01home.launcher.data.settings.HorizontalAlignment
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.actionFor
import com.mp01home.launcher.ui.components.RemoveFromDockDialog
import com.mp01home.launcher.ui.components.TimelineRail
import com.mp01home.launcher.ui.home.sections.CalendarSection
import com.mp01home.launcher.ui.home.sections.CaptureSection
import com.mp01home.launcher.ui.home.sections.DigestSection
import com.mp01home.launcher.ui.home.sections.DockSection
import com.mp01home.launcher.ui.home.sections.FocusSection
import com.mp01home.launcher.ui.home.sections.HabitSection
import com.mp01home.launcher.ui.home.sections.HeaderSection
import com.mp01home.launcher.ui.home.sections.HomeSectionHost
import com.mp01home.launcher.ui.home.sections.MailSection
import com.mp01home.launcher.ui.home.sections.ReadingSection
import com.mp01home.launcher.ui.home.sections.ScreenTimeLine
import com.mp01home.launcher.ui.home.sections.TasksSection
import com.mp01home.launcher.ui.home.sections.Top3Section
import com.mp01home.launcher.ui.home.sections.WeekPage
import com.mp01home.launcher.ui.theme.EinkTheme

/** Section slots on the first home page, top to bottom (SPEC §8). Capture and Dock sit below the page body. */
private val PAGE_ONE_ORDER = listOf(
    HomeSection.TOP3,
    HomeSection.CALENDAR,
    HomeSection.TASKS,
    HomeSection.MAIL,
    HomeSection.DIGEST,
    HomeSection.FOCUS,
    HomeSection.HABITS,
    HomeSection.READING,
    HomeSection.SCREEN_TIME
)

private const val WEEK_PAGE_INDEX = 1

/**
 * Home: a vertical, NON-scrolling, paged layout (EINK-1) built from section slots.
 *
 *     [rail] | Header (time, date, battery, weather)
 *            | page body (weight 1, clipped): page 0 = enabled sections, page 1 = WeekPage
 *            | Capture
 *            | Dock
 *            | page indicator (only when the week page is enabled)
 *
 * Gestures are mapped through [LauncherSettings.gestures] (CORE-6). Pages switch instantly,
 * without animation.
 */
@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigate: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    var appPendingRemoval by remember { mutableStateOf<AppInfo?>(null) }

    val pageCount = if (settings.isSectionEnabled(HomeSection.WEEK_PAGE)) 2 else 1
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    // If the week page gets disabled while shown, fall back to page 0 without writing state here.
    val page = selectedPage.coerceAtMost(pageCount - 1)

    val currentOpenDrawer by rememberUpdatedState(onOpenAppDrawer)
    val currentOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentNavigate by rememberUpdatedState(onNavigate)

    val host = remember(viewModel) {
        HomeSectionHost(
            openDrawer = { currentOpenDrawer() },
            openSettings = { currentOpenSettings() },
            navigate = { route -> currentNavigate(route) },
            requestFullRefresh = { viewModel.requestFullRefresh() }
        )
    }

    val onGesture: (Gesture) -> Unit = { gesture ->
        when (settings.gestures.actionFor(gesture)) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_DRAWER -> onOpenAppDrawer()
            GestureAction.QUICK_CAPTURE -> host.requestCapture()
            GestureAction.FULL_REFRESH -> viewModel.requestFullRefresh()
            GestureAction.OPEN_SETTINGS -> onOpenSettings()
            GestureAction.NEXT_PAGE -> if (page < pageCount - 1) selectedPage = page + 1
            GestureAction.PREVIOUS_PAGE -> if (page > 0) selectedPage = page - 1
        }
    }
    val currentOnGesture by rememberUpdatedState(onGesture)

    // Home never navigates "back"; on the week page, Back returns to page 0.
    BackHandler(enabled = true) {
        if (page > 0) selectedPage = 0
    }

    val dimens = EinkTheme.dimens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { currentOnGesture(Gesture.DOUBLE_TAP) },
                    onLongPress = { currentOnGesture(Gesture.LONG_PRESS) }
                )
            }
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        classifySwipe(total.x, total.y, threshold)?.let { currentOnGesture(it) }
                        total = Offset.Zero
                    },
                    onDragCancel = { total = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    }
                )
            }
            .padding(dimens.gutter)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            val railOnLeft = settings.timelineAlignment == HorizontalAlignment.LEFT
            if (settings.timelineEnabled && railOnLeft) {
                TimelineRail(currentTime = uiState.now.toLocalTime(), modifier = Modifier.fillMaxHeight())
                Spacer(modifier = Modifier.width(dimens.gutter))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                HeaderSection(
                    now = uiState.now,
                    settings = settings,
                    batteryPercent = uiState.batteryPercent,
                    weather = uiState.weather
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    if (page == WEEK_PAGE_INDEX) {
                        WeekPage(host = host, modifier = Modifier.fillMaxSize())
                    } else {
                        PageOneSections(settings = settings, host = host)
                    }
                }

                if (settings.isSectionEnabled(HomeSection.CAPTURE)) {
                    CaptureSection(host = host)
                }
                if (settings.isSectionEnabled(HomeSection.DOCK)) {
                    DockSection(
                        apps = uiState.dock,
                        onTap = { app -> viewModel.launchApp(app.packageName) },
                        onLongPress = { app -> appPendingRemoval = app }
                    )
                }
                if (pageCount > 1) {
                    Text(
                        text = (0 until pageCount).joinToString(" ") { if (it == page) "●" else "○" },
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimens.rowPadding)
                    )
                }
            }

            if (settings.timelineEnabled && !railOnLeft) {
                Spacer(modifier = Modifier.width(dimens.gutter))
                TimelineRail(currentTime = uiState.now.toLocalTime(), modifier = Modifier.fillMaxHeight())
            }
        }
    }

    appPendingRemoval?.let { app ->
        RemoveFromDockDialog(
            appLabel = app.label,
            onConfirm = {
                viewModel.removeFromDock(app.packageName)
                appPendingRemoval = null
            },
            onDismiss = { appPendingRemoval = null }
        )
    }
}

@Composable
private fun PageOneSections(settings: LauncherSettings, host: HomeSectionHost) {
    Column(modifier = Modifier.fillMaxSize()) {
        PAGE_ONE_ORDER.forEach { section ->
            if (settings.isSectionEnabled(section)) {
                when (section) {
                    HomeSection.TOP3 -> Top3Section(host = host)
                    HomeSection.CALENDAR -> CalendarSection(host = host)
                    HomeSection.TASKS -> TasksSection(host = host)
                    HomeSection.MAIL -> MailSection(host = host)
                    HomeSection.DIGEST -> DigestSection(host = host)
                    HomeSection.FOCUS -> FocusSection(host = host)
                    HomeSection.HABITS -> HabitSection(host = host)
                    HomeSection.READING -> ReadingSection(host = host)
                    HomeSection.SCREEN_TIME -> ScreenTimeLine(host = host)
                    else -> Unit
                }
            }
        }
    }
}
