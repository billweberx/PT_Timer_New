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
import androidx.annotation.Keep


@Keep
data class TimerSetup(
    val name: String,
    val config: SetupConfig, // This correctly matches the "config": {} object in your JSON
    val startRepSoundId: Int,
    val startRestSoundId: Int,
    val startSetRestSoundId: Int,
    val completeSoundId: Int
)

@Keep
data class SetupConfig(
    val moveToTime: String,
    val exerciseTime: String,
    val moveFromTime: String,
    val restTime: String,
    val reps: String,
    val sets: String,
    val setRestTime: String,
    val totalTime: String
)
// Data class for the main timer screen's reactive state, moved here to be globally accessible
data class TimerScreenState(
    val status: String = "Ready",
    val remainingTime: Int = 0,
    val progressDisplay: String = "",
    val currentSet: Int = 0,
    val currentRep: Int = 0,
    val activePhase: String = "Exercise"
)

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()

    // --- Keys for SharedPreferences. Updated for new fields. ---
    companion object {
        const val KEY_MOVE_TO_TIME = "move_to_time"
        const val KEY_EXERCISE_TIME = "exercise_time"
        const val KEY_MOVE_FROM_TIME = "move_from_time"
        const val KEY_REST_TIME = "rest_time"
        const val KEY_REPS = "reps"
        const val KEY_SETS = "sets"
        const val KEY_SET_REST_TIME = "set_rest_time"
        const val KEY_TOTAL_TIME = "total_time"

        const val KEY_ACTIVE_SETUP_NAME = "active_setup_name"

        // New keys for all the sound options
        const val KEY_START_REP_SOUND_ID = "start_rep_sound_id"
        const val KEY_START_REST_SOUND_ID = "start_rest_sound_id"
        const val KEY_START_SET_REST_SOUND_ID = "start_set_rest_sound_id"
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
