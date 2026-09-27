package com.mp01home.launcher.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mp01home.launcher.review.ReviewAutoLauncher
import com.mp01home.launcher.review.ReviewKind
import com.mp01home.launcher.ui.navigation.Routes
import kotlinx.coroutines.launch

/**
 * PLAN-1/2 auto-open. Place once inside the NavHost owner (MainActivity). On every resume of the
 * activity — which is what the first unlock / screen-on with the launcher in front produces — it
 * asks [ReviewAutoLauncher] whether a review is due and, if the user is on the home screen,
 * navigates to it. Each review opens at most once per day (see ReviewTrigger).
 */
@Composable
fun ReviewAutoTrigger(isOnHome: () -> Boolean, onNavigate: (route: String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentIsOnHome by rememberUpdatedState(isOnHome)
    val currentNavigate by rememberUpdatedState(onNavigate)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Only interrupt the home screen, never settings / drawer / another review.
        if (!currentIsOnHome()) return@LifecycleEventEffect
        scope.launch {
            val due = ReviewAutoLauncher.claimDueReview(context) ?: return@launch
            currentNavigate(
                when (due) {
                    ReviewKind.MORNING -> Routes.MORNING_REVIEW
                    ReviewKind.EVENING -> Routes.EVENING_REVIEW
                }
            )
        }
    }
}
