package com.mp01home.launcher.ui.home.sections

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * What the home screen offers to its section slots. Every feature section receives one.
 *
 * - [navigate]: open a full screen, e.g. `host.navigate(Routes.FOCUS)`.
 * - [captureRequests]: incremented by the swipe-down gesture (CORE-6). CaptureSection should
 *   focus its input whenever it changes (ignore the initial 0):
 *   `LaunchedEffect(host.captureRequests) { if (host.captureRequests > 0) focusRequester.requestFocus() }`
 */
@Stable
class HomeSectionHost(
    val openDrawer: () -> Unit,
    val openSettings: () -> Unit,
    val navigate: (route: String) -> Unit,
    val requestFullRefresh: () -> Unit
) {
    var captureRequests: Int by mutableIntStateOf(0)
        private set

    fun requestCapture() {
        captureRequests++
    }
}
