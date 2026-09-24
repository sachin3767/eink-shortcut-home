package com.mp01home.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mp01home.launcher.ui.drawer.AppDrawerScreen
import com.mp01home.launcher.ui.home.HomeScreen
import com.mp01home.launcher.ui.settings.SettingsScreen
import com.mp01home.launcher.ui.theme.MP01HomeTheme

private object Routes {
    const val HOME = "home"
    const val APP_DRAWER = "app_drawer"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MP01HomeTheme {
                LauncherNavHost()
            }
        }
    }
}

@Composable
private fun LauncherNavHost(navController: NavHostController = rememberNavController()) {
    // Instant screen swaps (no crossfade/slide) to avoid eInk ghosting on navigation.
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenAppDrawer = { navController.navigate(Routes.APP_DRAWER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.APP_DRAWER) {
            AppDrawerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
