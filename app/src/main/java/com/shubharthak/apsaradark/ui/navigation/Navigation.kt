package com.shubharthak.apsaradark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.screens.CanvasScreen
import com.shubharthak.apsaradark.ui.screens.HomeScreen
import com.shubharthak.apsaradark.ui.screens.PluginsScreen
import com.shubharthak.apsaradark.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PLUGINS = "plugins"
    const val CANVAS = "canvas"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val liveSettings = LocalLiveSettings.current

    // ── Hoist LiveSessionViewModel to Activity scope ──
    // This ensures the live session survives navigation between screens.
    // Previously scoped to HomeScreen — destroyed when navigating to Settings/Plugins.
    val liveViewModel: LiveSessionViewModel = viewModel(
        factory = LiveSessionViewModel.Factory(context, liveSettings)
    )

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) { backStackEntry ->
            // Check if we're returning from Settings
            val openDrawer = backStackEntry.savedStateHandle.get<Boolean>("openDrawer") == true
            // Consume the flag so it doesn't re-trigger
            if (openDrawer) {
                backStackEntry.savedStateHandle.remove<Boolean>("openDrawer")
            }

            HomeScreen(
                liveViewModel = liveViewModel,
                openDrawerOnReturn = openDrawer,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToPlugins = {
                    navController.navigate(Routes.PLUGINS)
                },
                onNavigateToCanvas = {
                    navController.navigate(Routes.CANVAS)
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    // Set flag on Home's savedStateHandle before popping
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("openDrawer", true)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.PLUGINS) {
            PluginsScreen(
                onBack = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("openDrawer", true)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.CANVAS) {
            CanvasScreen(
                onBack = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("openDrawer", true)
                    navController.popBackStack()
                }
            )
        }
    }
}
