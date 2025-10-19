// In file: app/src/main/java/com/billweberx/pt_timer/MainActivity.kt

package com.billweberx.pt_timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.billweberx.pt_timer.ui.theme.PT_TimerTheme

// --- Data classes remain here as they define the data structure ---
data class SoundOption(val displayName: String, val resourceId: Int)
data class TimerSetup(val name: String, val config: SetupConfig)data class SetupConfig(val exerciseTime: String, val restTime: String, val sets: String, val totalTime: String, val delayTime: String)


class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()

    // Keys for SharedPreferences are still needed here for type safety
    companion object {
        const val KEY_EXERCISE_TIME = "exercise_time"
        const val KEY_REST_TIME = "rest_time"
        const val KEY_SETS = "sets"
        const val KEY_TOTAL_TIME = "total_time"
        const val KEY_DELAY_TIME = "delay_time"
        const val KEY_ACTIVE_SETUP_NAME = "active_setup_name"
        const val KEY_START_SOUND_ID = "start_sound_id"
        const val KEY_REST_SOUND_ID = "rest_sound_id"
        const val KEY_COMPLETE_SOUND_ID = "complete_sound_id"
    }

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
        // Save the current state whenever the app is paused
        viewModel.saveStateToPrefs()
    }
}


@Composable
fun AppNavigation(viewModel: TimerViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "timer") {
        composable("timer") {
            PTTimerScreen(
                viewModel = viewModel,
                onGoToSettings = { navController.navigate("settings") },
                onSaveSetup = { setup -> viewModel.addOrUpdateSetup(setup) },
                onDeleteSetup = { setupName -> viewModel.deleteSetup(setupName) }
            )
        }
        composable("settings") {
            SetupScreen(
                navController = navController,
                viewModel = viewModel,
                onImport = { json -> viewModel.importSetupsFromJson(json) },
                onExport = { viewModel.exportSetupsToJson() }
            )
        }
    }
}
