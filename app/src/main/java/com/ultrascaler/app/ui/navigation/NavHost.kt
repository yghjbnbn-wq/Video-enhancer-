package com.ultrascaler.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ultrascaler.app.ui.screens.home.HomeScreen
import com.ultrascaler.app.ui.screens.processing.ProcessingScreen
import com.ultrascaler.app.ui.screens.result.ResultScreen
import com.ultrascaler.app.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Processing : Screen("processing/{videoUri}") {
        fun createRoute(videoUri: String) = "processing/${java.net.URLEncoder.encode(videoUri, "UTF-8")}"
    }
    data object Result : Screen("result/{outputPath}") {
        fun createRoute(outputPath: String) = "result/${java.net.URLEncoder.encode(outputPath, "UTF-8")}"
    }
    data object Settings : Screen("settings")
}

@Composable
fun UltraScalerNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onVideoSelected = { uri ->
                    navController.navigate(Screen.Processing.createRoute(uri))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            ProcessingScreen(
                videoUri = java.net.URLDecoder.decode(videoUri, "UTF-8"),
                onComplete = { outputPath ->
                    navController.navigate(Screen.Result.createRoute(outputPath)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument("outputPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val outputPath = backStackEntry.arguments?.getString("outputPath") ?: ""
            ResultScreen(
                outputPath = java.net.URLDecoder.decode(outputPath, "UTF-8"),
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
