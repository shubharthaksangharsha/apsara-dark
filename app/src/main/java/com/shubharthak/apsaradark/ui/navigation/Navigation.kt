package com.shubharthak.apsaradark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shubharthak.apsaradark.data.LocalLiveSettings
import com.shubharthak.apsaradark.live.LiveSessionViewModel
import com.shubharthak.apsaradark.ui.screens.CanvasScreen
import com.shubharthak.apsaradark.ui.screens.HomeScreen
import com.shubharthak.apsaradark.ui.screens.InterpreterScreen
import com.shubharthak.apsaradark.ui.screens.PluginsScreen
import com.shubharthak.apsaradark.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PLUGINS = "plugins"
    const val CANVAS = "canvas"
    const val CANVAS_APP = "canvas/{canvasId}"
    const val INTERPRETER = "interpreter"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startInLiveMode: Boolean = false
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
                startInLiveMode = startInLiveMode,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToPlugins = {
                    navController.navigate(Routes.PLUGINS)
                },
                onNavigateToCanvas = {
                    navController.navigate(Routes.CANVAS)
                },
                onNavigateToCanvasApp = { canvasId ->
                    navController.navigate("canvas/$canvasId")
                },
                onNavigateToInterpreter = {
                    navController.navigate(Routes.INTERPRETER)
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
                    // Don't open the drawer when returning from Canvas —
                    // Canvas can be opened via Snackbar during a live session,
                    // so we should go straight back to the live view.
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Routes.CANVAS_APP,
            arguments = listOf(navArgument("canvasId") { type = NavType.StringType })
        ) { backStackEntry ->
            val canvasId = backStackEntry.arguments?.getString("canvasId")
            CanvasScreen(
                canvasId = canvasId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.INTERPRETER) {
            InterpreterScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
