package com.mp01home.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.ui.drawer.AppDrawerScreen
import com.mp01home.launcher.ui.focus.FocusScreen
import com.mp01home.launcher.ui.home.HomeScreen
import com.mp01home.launcher.ui.navigation.Routes
import com.mp01home.launcher.ui.review.EveningReviewScreen
import com.mp01home.launcher.ui.review.MorningReviewScreen
import com.mp01home.launcher.ui.review.ReviewAutoTrigger
import com.mp01home.launcher.ui.settings.SettingsScreen
import com.mp01home.launcher.ui.theme.MP01HomeTheme
import com.mp01home.launcher.util.EinkFlashOverlay

class MainActivity : ComponentActivity() {

    /** Bumped when the Home button is pressed while we're already in front: pop to home. */
    private var homeRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = LauncherSettings())
            MP01HomeTheme(fontScale = settings.bodyFontScale, density = settings.density) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LauncherNavHost(homeRequests = homeRequests)
                    // Last child: covers the window during a full-refresh flash (EINK-5).
                    EinkFlashOverlay(container.einkRefresh)
                }
            }
        }
        container.einkRefresh.attach(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequests++
    }

    override fun onDestroy() {
        appContainer.einkRefresh.detach()
        super.onDestroy()
    }
}

@Composable
private fun LauncherNavHost(
    homeRequests: Int,
    navController: NavHostController = rememberNavController()
) {
    LaunchedEffect(homeRequests) {
        if (homeRequests > 0) navController.popBackStack(Routes.HOME, inclusive = false)
    }

    val navigate: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }
    val back: () -> Unit = { navController.popBackStack() }

    // PLAN-1/2: open the morning/evening review once a day when resuming on home.
    ReviewAutoTrigger(
        isOnHome = { navController.currentDestination?.route.let { it == null || it == Routes.HOME } },
        onNavigate = navigate
    )

    // Instant screen swaps (no crossfade/slide): animations ghost badly on eInk (EINK-1).
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenAppDrawer = { navigate(Routes.APP_DRAWER) },
                onOpenSettings = { navigate(Routes.SETTINGS) },
                onNavigate = navigate
            )
        }
        composable(Routes.APP_DRAWER) { AppDrawerScreen(onBack = back) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
        composable(Routes.MORNING_REVIEW) { MorningReviewScreen(onBack = back) }
        composable(Routes.EVENING_REVIEW) { EveningReviewScreen(onBack = back) }
        composable(Routes.FOCUS) { FocusScreen(onBack = back) }
    }
}
