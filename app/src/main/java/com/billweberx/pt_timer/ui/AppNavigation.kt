package com.billweberx.pt_timer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.billweberx.pt_timer.TimerViewModel
import com.billweberx.pt_timer.ui.screens.AboutScreen
import com.billweberx.pt_timer.ui.screens.HelpScreen
import com.billweberx.pt_timer.ui.screens.PTTimerScreen
import com.billweberx.pt_timer.ui.screens.SetupScreen

@Composable
fun AppNavigation(viewModel: TimerViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "timer") {
        composable("timer") {
            // Note: The parameters for PTTimerScreen will change later
            PTTimerScreen(
                viewModel = viewModel,
                onGoToSettings = { navController.navigate("settings") },
                onGoToHelp = { navController.navigate("help") },
                onGoToAbout = { navController.navigate("about") }
            )
        }
        composable("settings") {
            SetupScreen(
                onGoBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable("help") { // New route for the HelpScreen
            HelpScreen(
                onGoBack = { navController.popBackStack() } // Navigate back to the previous screen (PTTimerScreen)
            )
        }
        composable("about") { // New route for the AboutScreen
            AboutScreen(
                onGoBack = { navController.popBackStack() } // Navigate back to the previous screen (PTTimerScreen)
            )
        }
    }
}