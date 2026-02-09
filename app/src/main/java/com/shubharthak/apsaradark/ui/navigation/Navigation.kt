package com.shubharthak.apsaradark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shubharthak.apsaradark.ui.screens.HomeScreen
import com.shubharthak.apsaradark.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
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
                openDrawerOnReturn = openDrawer,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
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
    }
}
