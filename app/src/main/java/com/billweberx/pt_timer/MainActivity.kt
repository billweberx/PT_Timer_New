// C:/Users/billw/AndroidStudioProjects/PT_Timer_New/app/src/main/java/com/billweberx/pt_timer/MainActivity.kt

package com.billweberx.pt_timer

import android.app.Application
import android.content.Context // <-- IMPORT THIS
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.billweberx.pt_timer.ui.theme.PT_TimerTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Field
import androidx.core.content.edit


// ... (Data classes SoundOption, TimerSetup, SetupConfig remain the same) ...
data class SoundOption(val displayName: String, val resourceId: Int)
data class TimerSetup(val name: String, val config: SetupConfig, val startSoundId: Int, val restSoundId: Int, val completeSoundId: Int)
data class SetupConfig(val exerciseTime: String, val restTime: String, val sets: String, val totalTime: String, val delayTime: String)


class TimerViewModel(application: Application) : AndroidViewModel(application) {
    // --- ADD TIMER STATE VARIABLES HERE ---
    var exerciseTime by mutableStateOf("10")
    var restTime by mutableStateOf("5")
    var sets by mutableStateOf("3")
    var totalTime by mutableStateOf("0")
    var delayTime by mutableStateOf("0")

    var loadedSetups by mutableStateOf<List<TimerSetup>>(emptyList())
        private set


    var startSoundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    var restSoundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    var completeSoundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set

    internal val defaultSound = SoundOption("None", -1)
    var selectedStartSound by mutableStateOf(defaultSound)
    var selectedRestSound by mutableStateOf(defaultSound)
    var selectedCompleteSound by mutableStateOf(defaultSound)

    fun saveState() {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("PTTimerState", Context.MODE_PRIVATE)

        prefs.edit { // Using the clean KTX version
            putString(MainActivity.KEY_EXERCISE_TIME, exerciseTime)
            putString(MainActivity.KEY_REST_TIME, restTime)
            putString(MainActivity.KEY_SETS, sets)
            putString(MainActivity.KEY_TOTAL_TIME, totalTime)
            putString(MainActivity.KEY_DELAY_TIME, delayTime)
            putInt(MainActivity.KEY_START_SOUND_ID, selectedStartSound.resourceId)
            putInt(MainActivity.KEY_REST_SOUND_ID, selectedRestSound.resourceId)
            putInt(MainActivity.KEY_COMPLETE_SOUND_ID, selectedCompleteSound.resourceId)
        }
    }

    fun updateSetups(newSetups: List<TimerSetup>) { loadedSetups = newSetups }
    fun saveSetup(setup: TimerSetup) {
        val existingIndex = loadedSetups.indexOfFirst { it.name.equals(setup.name, ignoreCase = true) }
        val newList = loadedSetups.toMutableList()
        if (existingIndex != -1) { newList[existingIndex] = setup }
        else { newList.add(setup) }
        loadedSetups = newList
    }
    fun deleteSetup(setupName: String) {
        loadedSetups = loadedSetups.filter { !it.name.equals(setupName, ignoreCase = true) }
    }
    fun initializeSounds(startSounds: List<SoundOption>, restSounds: List<SoundOption>, completeSounds: List<SoundOption>) {
        startSoundOptions = startSounds
        restSoundOptions = restSounds
        completeSoundOptions = completeSounds
        // Don't set defaults here anymore, loadState will handle it
    }
}


class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()
    private val setupsFilename = "pt_timer_setups.json"

    // --- ADD SharedPreferences and Keys ---
    private val prefs by lazy { getSharedPreferences("PTTimerState", MODE_PRIVATE) }

    companion object {
        const val KEY_EXERCISE_TIME = "exercise_time"
        const val KEY_REST_TIME = "rest_time"
        const val KEY_SETS = "sets"
        const val KEY_TOTAL_TIME = "total_time"
        const val KEY_DELAY_TIME = "delay_time"
        const val KEY_START_SOUND_ID = "start_sound_id"
        const val KEY_REST_SOUND_ID = "rest_sound_id"
        const val KEY_COMPLETE_SOUND_ID = "complete_sound_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val (startSounds, restSounds, completeSounds) = loadSoundOptions()
        viewModel.initializeSounds(startSounds, restSounds, completeSounds)

        // --- CALL loadState() HERE ---
        loadState()
        loadSetupsFromFile()

        setContent {
            PT_TimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        viewModel = viewModel,
                        saveToFile = { saveSetupsToFile() },
                        // Pass saveState to AppNavigation
                        importSetups = { content ->
                            val type = object : TypeToken<List<TimerSetup>>() {}.type
                            val setups: List<TimerSetup> = Gson().fromJson(content, type)
                            viewModel.updateSetups(setups)
                            saveSetupsToFile()
                        },
                        exportSetups = { Gson().toJson(viewModel.loadedSetups) }
                    )
                }
            }
        }
    }

    // --- ADD loadState() function ---
    private fun loadState() {
        viewModel.exerciseTime = prefs.getString(KEY_EXERCISE_TIME, "10") ?: "10"
        viewModel.restTime = prefs.getString(KEY_REST_TIME, "5") ?: "5"
        viewModel.sets = prefs.getString(KEY_SETS, "3") ?: "3"
        viewModel.totalTime = prefs.getString(KEY_TOTAL_TIME, "0") ?: "0"
        viewModel.delayTime = prefs.getString(KEY_DELAY_TIME, "0") ?: "0"

        // Get the saved ID, or -1 if nothing is saved
        val startSoundId = prefs.getInt(KEY_START_SOUND_ID, -1)
        val restSoundId = prefs.getInt(KEY_REST_SOUND_ID, -1)
        val completeSoundId = prefs.getInt(KEY_COMPLETE_SOUND_ID, -1)


        // Find the sound by ID, or fall back to a safe default if not found
        viewModel.selectedStartSound = viewModel.startSoundOptions.find { it.resourceId == startSoundId }
            ?: viewModel.defaultSound
        viewModel.selectedRestSound = viewModel.restSoundOptions.find { it.resourceId == restSoundId }
            ?: viewModel.defaultSound
        viewModel.selectedCompleteSound = viewModel.completeSoundOptions.find { it.resourceId == completeSoundId }
            ?: viewModel.defaultSound

    }


    private fun saveSetupsToFile() {
        try {
            val json = Gson().toJson(viewModel.loadedSetups)
            val file = File(filesDir, setupsFilename)
            file.writeText(json)
        } catch (e: Exception) { Log.e("MainActivity", "Error saving setups to file", e) }
    }

    private fun loadSetupsFromFile() {
        try {
            val file = File(filesDir, setupsFilename)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<TimerSetup>>() {}.type
                val setups: List<TimerSetup> = Gson().fromJson(json, type)
                viewModel.updateSetups(setups)
            }
        } catch (e: Exception) { Log.e("MainActivity", "Error loading setups from file", e) }
    }
}


@Composable
fun AppNavigation(
    viewModel: TimerViewModel,
    saveToFile: () -> Unit,
    importSetups: (String) -> Unit,
    exportSetups: () -> String
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "timer") {
        composable("timer") {
            PTTimerScreen(
                viewModel = viewModel,
                onGoToSettings = { navController.navigate("settings") },
                onSaveSetup = { setup ->
                    viewModel.saveSetup(setup)
                    saveToFile()
                },
                onDeleteSetup = { setupName ->
                    viewModel.deleteSetup(setupName)
                    saveToFile()
                }
            )
        }
        composable("settings") {
            SetupScreen(
                navController = navController,
                viewModel = viewModel,
                onImport = importSetups,
                onExport = exportSetups
            )
        }

    }
}

// ... (loadSoundOptions remains the same) ...
fun loadSoundOptions(): Triple<List<SoundOption>, List<SoundOption>, List<SoundOption>> {
    val startSounds = mutableListOf<SoundOption>()
    val restSounds = mutableListOf<SoundOption>()
    val completeSounds = mutableListOf<SoundOption>()
    // Add a "None" option manually
    startSounds.add(SoundOption("None", -1))
    restSounds.add(SoundOption("None", -1))
    completeSounds.add(SoundOption("None", -1))

    val rawFields: Array<Field> = R.raw::class.java.fields
    for (field in rawFields) {
        try {
            val resourceName = field.name
            val resourceId = field.getInt(null)
            val displayName = resourceName.substringAfter('_').replace('_', ' ').replaceFirstChar { it.titlecase() }
            when {
                resourceName.startsWith("start_") -> startSounds.add(SoundOption(displayName, resourceId))
                resourceName.startsWith("rest_") -> restSounds.add(SoundOption(displayName, resourceId))
                resourceName.startsWith("complete_") -> completeSounds.add(SoundOption(displayName, resourceId))
            }
        } catch (e: Exception) { Log.e("loadSoundOptions", "Error reading raw resource fields", e) }
    }
    return Triple(startSounds, restSounds, completeSounds)
}
