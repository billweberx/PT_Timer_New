package com.billweberx.pt_timer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.billweberx.pt_timer.TimerViewModel
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
                onGoToSettings = { navController.navigate("settings") }
                // Setup management parameters are removed as they move to the settings screen
            )
        }
        composable("settings") {
            SetupScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}