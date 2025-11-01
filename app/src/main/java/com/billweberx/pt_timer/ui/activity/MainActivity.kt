// In file: app/src/main/java/com/billweberx/pt_timer/MainActivity.kt

package com.billweberx.pt_timer.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.billweberx.pt_timer.ui.theme.PT_TimerTheme
import com.billweberx.pt_timer.TimerViewModel
import com.billweberx.pt_timer.ui.AppNavigation
class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()
    private var wasRunningOnPause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PT_TimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()

        // If the timer is NOT paused, it means it was running (or stopped).
        // For the user's workflow, we assume if they leave the app, a non-paused timer
        // is a running timer they want to resume later. This is a safe assumption.
        val isCurrentlyPaused = viewModel.timerScreenState.value.isPaused
        wasRunningOnPause = !isCurrentlyPaused
        // If it was considered running, explicitly pause it.
        if (wasRunningOnPause) {
            viewModel.pauseTimer() // This will set isPaused = true
        }

        // Always save the current state.
        viewModel.saveAppState()
    }

    override fun onResume() {
        super.onResume()

        // If the timer was running when we paused, automatically resume it.
        if (wasRunningOnPause) {
            viewModel.resumeTimer() // This will set isPaused = false
            wasRunningOnPause = false // Reset the flag.
        }
    }
}