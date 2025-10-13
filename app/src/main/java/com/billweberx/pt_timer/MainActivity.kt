package com.billweberx.pt_timer

import android.content.Context
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.billweberx.pt_timer.ui.theme.PT_TimerTheme
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// Define AppSoundIds
object AppSoundIds {
    var EXERCISE_START_SOUND_ID: Int = 0
    var EXERCISE_REST_SOUND_ID: Int = 0
    var EXERCISE_COMPLETE_SOUND_ID: Int = 0
}

// Define SetupConfig data class
data class SetupConfig(
    val exerciseTime: Double,
    val restTime: Double,
    val sets: Double,
    val totalTime: Double,
    val delayTime: Double
)

// Sealed class for Navigation
sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Setup : Screen("setup")
}

// Top-level function, accessible everywhere
fun formatTime(timeInMillis: Double): String {
    val totalSeconds = (timeInMillis / 1000).roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class) // Added for Scaffold
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PT_TimerTheme {
                // Use Scaffold to provide proper layout structure
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // The content of your app, with padding applied by the Scaffold
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

fun playSound(
    soundPool: SoundPool,
    activeStreamIds: SnapshotStateList<Int>,
    soundId: Int
) {
    activeStreamIds.forEach { streamId ->
        soundPool.stop(streamId)
    }
    activeStreamIds.clear()

    if (soundId != 0) {
        val streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        if (streamId != 0) {
            activeStreamIds.add(streamId)
        }
    }
}

suspend fun timerCoroutine(
    isSetsMode: Boolean,
    exerciseDurationMs: Double,
    restDurationMs: Double,
    targetTotalWorkoutTimeMs: Double,    workoutCompleted: MutableState<Boolean>,
    isResuming: Boolean,
    initialTimeLeftInPhaseMs: Double,
    initialIsExercisePhase: Boolean,
    initialSetsRemaining: Int,
    playSoundAction: (soundId: Int) -> Unit,
    // The onUpdate callback now receives all the data needed
    onUpdate: (
        timeLeftInPhase: Double,
        statusText: String,
        secondaryValue: Double
    ) -> Unit,
    onComplete: (finalStatus: String) -> Unit
) {
    val tickIntervalMs = 100L

    if (!isResuming) {
        playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
    }

    if (isSetsMode) {
        var setsLeft = initialSetsRemaining
        var currentPhaseIsExercise = initialIsExercisePhase
        var currentTimeLeftInPhaseMs = initialTimeLeftInPhaseMs

        while (setsLeft > 0) {
            while (currentTimeLeftInPhaseMs > 0) {
                delay(tickIntervalMs)
                currentTimeLeftInPhaseMs -= tickIntervalMs
                // In sets mode, secondary value is the number of sets left
                onUpdate(
                    currentTimeLeftInPhaseMs,
                    if (currentPhaseIsExercise) "Exercise" else "Rest",
                    setsLeft.toDouble()
                )
            }

            if (currentPhaseIsExercise) {
                setsLeft--
                if (setsLeft <= 0) break
                currentPhaseIsExercise = false
                currentTimeLeftInPhaseMs = restDurationMs
                playSoundAction(AppSoundIds.EXERCISE_REST_SOUND_ID)
            } else {
                currentPhaseIsExercise = true
                currentTimeLeftInPhaseMs = exerciseDurationMs
                playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
            }
        }
    } else { // Total Time Mode
        var totalTimeLeftMs = if (isResuming) initialTimeLeftInPhaseMs else targetTotalWorkoutTimeMs
        var currentPhaseIsExercise = initialIsExercisePhase
        var phaseTimeLeftMs = if (isResuming) initialTimeLeftInPhaseMs else exerciseDurationMs

        // This is a special case for resuming in total time mode
        if(isResuming) {
            phaseTimeLeftMs = initialTimeLeftInPhaseMs
        }


        while (totalTimeLeftMs > 0) {
            while (phaseTimeLeftMs > 0 && totalTimeLeftMs > 0) {
                delay(tickIntervalMs)
                phaseTimeLeftMs -= tickIntervalMs
                totalTimeLeftMs -= tickIntervalMs
                // In total time mode, secondary value is the total time left
                onUpdate(
                    phaseTimeLeftMs,
                    if (currentPhaseIsExercise) "Exercise" else "Rest",
                    totalTimeLeftMs
                )
            }

            if (totalTimeLeftMs <= 0) break

            if (currentPhaseIsExercise) {
                currentPhaseIsExercise = false
                phaseTimeLeftMs = restDurationMs
                if (phaseTimeLeftMs > 0) playSoundAction(AppSoundIds.EXERCISE_REST_SOUND_ID)
            } else {
                currentPhaseIsExercise = true
                phaseTimeLeftMs = exerciseDurationMs
                playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
            }
        }
    }

    onComplete("Workout Complete")
    workoutCompleted.value = true
    playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            PTTimerScreen(_onNavigateToSetup = { navController.navigate(Screen.Setup.route) })
        }
        composable(Screen.Setup.route) {
            SetupScreen(onNavigateToMain = { navController.popBackStack() })
        }
    }
}

@Composable
fun SetupScreen(onNavigateToMain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("This is the Setup Screen")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToMain) {
            Text("Main")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTimerScreen(_onNavigateToSetup: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sharedPreferences = remember { context.getSharedPreferences("PT_Timer_Prefs", Context.MODE_PRIVATE) }
    val editor = remember { sharedPreferences.edit() }

    // --- State Variables ---
    var exerciseTime by remember { mutableDoubleStateOf(0.0) }
    var restTime by remember { mutableDoubleStateOf(0.0) }
    var sets by remember { mutableDoubleStateOf(0.0) }
    var totalTime by remember { mutableDoubleStateOf(0.0) }

    var exerciseTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var restTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var setsTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var totalTimeTextFieldValue by remember { mutableStateOf(TextFieldValue()) }

    var exerciseError by remember { mutableStateOf<String?>(null) }
    var restError by remember { mutableStateOf<String?>(null) }
    var setsError by remember { mutableStateOf<String?>(null) }
    var totalTimeError by remember { mutableStateOf<String?>(null) }

    val setupNames = remember { mutableStateListOf<String>() }
    var selectedSetup by remember { mutableStateOf<String?>(null) }
    var newSetupName by remember { mutableStateOf(TextFieldValue("")) }
    var expanded by remember { mutableStateOf(false) }

    // --- Timer State ---
    var timeLeftInCurrentPhaseMs by remember { mutableDoubleStateOf(0.0) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready") }
    // NEW STATE: For the secondary display (sets or total time)
    var secondaryDisplayValue by remember { mutableDoubleStateOf(0.0) }

    var pausedTimeLeftInCurrentPhaseMs by remember { mutableDoubleStateOf(0.0) }
    var pausedCurrentSetsRemaining by remember { mutableDoubleStateOf(0.0) }
    val workoutCompleted = remember { mutableStateOf(false) }

    // --- Sound Pool ---
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(2).build().also {
            AppSoundIds.EXERCISE_START_SOUND_ID = it.load(context, R.raw.exercise_start, 1)
            AppSoundIds.EXERCISE_REST_SOUND_ID = it.load(context, R.raw.exercise_rest, 1)
            AppSoundIds.EXERCISE_COMPLETE_SOUND_ID = it.load(context, R.raw.exercise_complete, 1)
        }
    }
    val activeStreamIds = remember { mutableStateListOf<Int>() }

    // --- Data Handling ---
    fun formatForTextField(value: Double, isInteger: Boolean = false): String {
        return if (isInteger) value.toInt().toString()
        else if (value == 0.0) "0"
        else if (value % 1.0 == 0.0) value.toInt().toString()
        else value.toString()
    }

    val gson = remember { Gson() }
    val fileName = "pt_timer_setups.json"

    fun saveSetupsToFile(setups: Map<String, SetupConfig>) {
        val jsonString = gson.toJson(setups)
        try {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace() // Log any errors
        }
    }

    fun loadSetupsFromFile(): MutableMap<String, SetupConfig> {
        return try {
            val jsonString = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, SetupConfig>>() {}.type
            gson.fromJson(jsonString, type) ?: mutableMapOf()
        } catch (_: java.io.FileNotFoundException) {
            mutableMapOf() // File doesn't exist yet, return an empty map
        } catch (e: Exception) {
            e.printStackTrace()
            mutableMapOf() // Return empty map on any other error
        }
    }


    LaunchedEffect(Unit) {
        // Load the last-used timer values (still ok to keep in SharedPreferences)
        fun loadDouble(key: String, default: Double): Double {
            val stringValue = sharedPreferences.getString(key, default.toString())
            return stringValue?.toDoubleOrNull() ?: default
        }
        exerciseTime = loadDouble("exercise_time", 30.0)
        restTime = loadDouble("rest_time", 0.0)
        sets = loadDouble("sets", 3.0)
        totalTime = loadDouble("total_time", 0.0)

        // *** NEW: Load setup names from the file ***
        val setupsFromFile = loadSetupsFromFile()
        setupNames.clear()
        setupNames.addAll(setupsFromFile.keys.sorted())

        // Update TextFields with last-used values
        exerciseTextFieldValue = TextFieldValue(text = formatForTextField(exerciseTime))
        restTextFieldValue = TextFieldValue(text = formatForTextField(restTime))
        setsTextFieldValue = TextFieldValue(text = formatForTextField(sets, isInteger = true))
        totalTimeTextFieldValue = TextFieldValue(text = formatForTextField(totalTime))
    }


    // *** THIS IS THE CORRECTED validateAndSave FUNCTION ***
    fun validateAndSave(
        textValue: String,
        allowZero: Boolean,
        onError: (String?) -> Unit,
        onSuccess: (Double) -> Unit
    ) {
        if (textValue.isBlank()) {
            if (!allowZero) onError("Cannot be empty") else {
                onError(null); onSuccess(0.0)
            }
            return
        }
        val parsedValue = textValue.toDoubleOrNull()
        if (parsedValue == null) onError("Invalid")
        else if (!allowZero && parsedValue == 0.0) onError("Not 0")
        else {
            onError(null); onSuccess(parsedValue)
        }
    }

    fun loadSetup(name: String) {
        val allSetups = loadSetupsFromFile()
        val config = allSetups[name]
        config?.let {
            // This is the corrected section
            exerciseTime = it.exerciseTime
            restTime = it.restTime

            sets = it.sets
            totalTime = it.totalTime

            exerciseTextFieldValue = TextFieldValue(formatForTextField(exerciseTime))
            restTextFieldValue = TextFieldValue(formatForTextField(restTime))
            setsTextFieldValue = TextFieldValue(formatForTextField(sets, isInteger = true))
            totalTimeTextFieldValue = TextFieldValue(formatForTextField(totalTime))
            selectedSetup = name
        }
    }


    // --- NEW, CORRECTED saveSetup ---
    fun saveSetup(name: String) {if (name.isBlank()) return

        val currentSetups = loadSetupsFromFile()
        val newConfig = SetupConfig(exerciseTime, restTime, sets, totalTime, delayTime = 0.0)
        currentSetups[name] = newConfig
        saveSetupsToFile(currentSetups)

        // Update the UI state
        if (!setupNames.contains(name)) {
            setupNames.add(name)
            setupNames.sort()
        }

        newSetupName = TextFieldValue("")
        selectedSetup = name
        expanded = false
        focusManager.clearFocus()
    }

    fun deleteSetup(name: String) {
        if (name.isBlank()) return
        val currentSetups = loadSetupsFromFile()
        if (currentSetups.containsKey(name)) {
            currentSetups.remove(name)
            saveSetupsToFile(currentSetups)

            // Update the UI state after deleting
            setupNames.remove(name)
            selectedSetup = null // Clear the selection from the dropdown display
        }
    }

        val onPlayPauseClick = {
        if (isRunning) {
            timerJob?.cancel()
            isPaused = true
            isRunning = false
            pausedTimeLeftInCurrentPhaseMs = timeLeftInCurrentPhaseMs
            // This is incorrect for total time mode, but we will leave it for now
            // as resuming total time mode is complex.
            pausedCurrentSetsRemaining = secondaryDisplayValue
        } else {
            val useSetsMode = sets > 0
            val isResumingFromPause = isPaused

            timerJob = coroutineScope.launch {
                isRunning = true

                // Correctly set initial state before starting coroutine
                if (!isResumingFromPause) {
                    timeLeftInCurrentPhaseMs = exerciseTime * 1000
                    secondaryDisplayValue = if (useSetsMode) sets else totalTime * 1000
                }

                val initialTime = if (isResumingFromPause) pausedTimeLeftInCurrentPhaseMs else exerciseTime * 1000
                val initialIsExercise = if (isResumingFromPause) true else true
                val initialSets = if (isResumingFromPause) pausedCurrentSetsRemaining.toInt() else sets.toInt()

                timerCoroutine(
                    isSetsMode = useSetsMode,
                    exerciseDurationMs = exerciseTime * 1000,
                    restDurationMs = restTime * 1000,
                    targetTotalWorkoutTimeMs = totalTime * 1000,
                    workoutCompleted = workoutCompleted,
                    isResuming = isResumingFromPause,
                    initialTimeLeftInPhaseMs = initialTime,
                    initialIsExercisePhase = initialIsExercise,
                    initialSetsRemaining = initialSets,
                    playSoundAction = { soundId -> playSound(soundPool, activeStreamIds, soundId) },
                    onUpdate = { timeLeftInPhase, statusText, secondaryVal ->
                        timeLeftInCurrentPhaseMs = timeLeftInPhase
                        status = statusText
                        secondaryDisplayValue = secondaryVal
                    },
                    onComplete = { finalStatus ->
                        status = finalStatus
                        timeLeftInCurrentPhaseMs = 0.0
                        isRunning = false
                        isPaused = false
                    }
                )
            }
            isPaused = false
        }
    }

    // *** THIS IS THE CORRECTED onStopClick FUNCTION ***
    val onStopClick = {
        timerJob?.cancel()
        isRunning = false
        isPaused = false
        timeLeftInCurrentPhaseMs = exerciseTime * 1000
        secondaryDisplayValue = 0.0
        status = "Ready"
        workoutCompleted.value = false
        focusManager.clearFocus()

        // Manually save the current timer values as the "last used" state
        editor.putString("exercise_time", exerciseTime.toString())
        editor.putString("rest_time", restTime.toString())
        editor.putString("sets", sets.toString())
        editor.putString("total_time", totalTime.toString())
        editor.apply()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            // Main status: "Exercise", "Rest", "Ready"
            Text(text = status, fontSize = 24.sp, style = MaterialTheme.typography.headlineMedium)

            // Secondary Status Display: Shows Sets or Total Time
            if (isRunning || isPaused) {
                val secondaryText = if (sets > 0) {
                    "Set: ${secondaryDisplayValue.toInt()}"
                } else {
                    "Total Time: ${formatTime(secondaryDisplayValue)}"
                }
                Text(
                    text = secondaryText,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Main Timer: Always shows time left in current phase
            Text(
                text = formatTime(timeLeftInCurrentPhaseMs),
                fontSize = 72.sp,
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- All other items remain the same ---
        item {
            // Input Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimerInputColumn(
                    label = "Exercise Time",
                    value = exerciseTextFieldValue,
                    onValueChange = {
                        exerciseTextFieldValue = it
                        // Note the corrected call without the "prefKey"
                        validateAndSave(
                            it.text,
                            false,
                            { e -> exerciseError = e }) { v -> exerciseTime = v }
                    },
                    isError = exerciseError != null,
                    errorMessage = exerciseError,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TimerInputColumn(
                    label = "Rest Time",
                    value = restTextFieldValue,
                    onValueChange = {
                        restTextFieldValue = it
                        validateAndSave(it.text, true, { e -> restError = e }) { v -> restTime = v }
                    },
                    isError = restError != null,
                    errorMessage = restError,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimerInputColumn(
                    label = "Sets",
                    value = setsTextFieldValue,
                    onValueChange = {
                        setsTextFieldValue = it
                        validateAndSave(it.text, true, { e -> setsError = e }) { v -> sets = v }
                    },
                    isError = setsError != null,
                    errorMessage = setsError,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TimerInputColumn(
                    label = "Total Time",
                    value = totalTimeTextFieldValue,
                    onValueChange = {
                        totalTimeTextFieldValue = it
                        validateAndSave(
                            it.text,
                            true,
                            { e -> totalTimeError = e }) { v -> totalTime = v }
                    },
                    isError = totalTimeError != null,
                    errorMessage = totalTimeError,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // Control Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isRunning && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning && !isPaused) "Pause" else "Play"
                    )
                }
                Button(onClick = onStopClick, enabled = isRunning || isPaused) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // --- Dropdown for Selecting a Setup ---
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded } // Toggle on click
            ) {
                OutlinedTextField(
                    value = selectedSetup ?: "Select a Setup",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Setups") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    setupNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                loadSetup(name)
                                expanded = false // Close menu on selection
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- NEW, REORGANIZED SAVE AND NAVIGATION AREA ---
        item {
            // Text field now takes the full width on its own line
            OutlinedTextField(
                value = newSetupName,
                onValueChange = { newSetupName = it },
                label = { Text("New Setup Name") },
                modifier = Modifier.fillMaxWidth(), // This makes it stretch
                singleLine = true
            )
        }

        item {
            // Buttons are now on the same row with shorter names
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly, // Spaces them out evenly
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Save" button
                Button(
                    onClick = { saveSetup(newSetupName.text) },
                    enabled = newSetupName.text.isNotBlank()
                ) {
                    Text("Save")
                }

                // "Delete" button
                Button(
                    onClick = { selectedSetup?.let { deleteSetup(it) } },
                    enabled = selectedSetup != null // Only enabled when a setup is selected
                ) {
                    Text("Delete")
                }

                // "Setup" button
                Button(onClick = _onNavigateToSetup) {
                    Text("Setup")
                }
            }
        }
    }
}

@Composable
fun TimerInputColumn(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PT_TimerTheme {
        AppNavigation()
    }
}
