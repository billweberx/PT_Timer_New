package com.billweberx.pt_timer

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billweberx.pt_timer.ui.theme.PT_TimerTheme
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import android.util.Log

// Required import for MenuAnchorType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.navigation.compose.composable

import java.text.DecimalFormatSymbols
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalFocusManager

//// Define AppTones
//object AppTones {
//    const val COUNTDOWN_BEEP = ToneGenerator.TONE_PROP_BEEP
//    const val PHASE_END_BEEP = ToneGenerator.TONE_CDMA_HIGH_L
//    const val WORKOUT_COMPLETE_BEEP = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
//}

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


sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Setup : Screen("setup")
}

// Format function at top level
fun formatForTextField(value: Double, isInteger: Boolean = false): String {
    return if (isInteger) value.toInt().toString()
    else if (value == 0.0) "0"
    else value.toString()
}

class MainActivity : ComponentActivity() {
    private var audioFocusRequest: AudioFocusRequest? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate HAS BEEN CALLED!")
        super.onCreate(savedInstanceState)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        setContent {
            PT_TimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    @Preview(
        showBackground = true,
        showSystemUi = true,
        uiMode = Configuration.UI_MODE_NIGHT_YES // <-- Make sure it uses the right Configuration
    )

    @Composable
    fun DefaultPreview() {
        PT_TimerTheme {
//            PTTimerScreenContent(
//                // 2. Add a fake FocusManager for the preview:
////                focusManager = object : FocusManager {
////                    override fun clearFocus(force: Boolean) {}
////                    override fun moveFocus(focusDirection: FocusDirection): Boolean = false
////                },
//                formattedTimeLeft = "10:00.0",
//                status = "Ready",
//                remainingDisplay = "Configure and start",
//                exerciseTextFieldValue = TextFieldValue("30"),
//                restTextFieldValue = TextFieldValue("0"),
//                setsTextFieldValue = TextFieldValue("3"),
//                totalTimeTextFieldValue = TextFieldValue("0"),
//                exerciseError = null,
//                restError = null,
//                setsError = null,
//                totalTimeError = null,
//                isRunning = false,
//                expanded = false,
//                selectedSetup = null,
//                savedSetupNames = listOf("Setup 1", "Setup 2"),
//                showSaveDialog = false,
//                newSetupName = "",
//                onExerciseTimeChange = {},
//                onRestTimeChange = {},
//                onSetsChange = {},
//                onTotalTimeChange = {},
//                onExpandedChange = {},
//                onDropdownMenuItemClick = {},
//                onSaveSetupClick = {},
//                onSaveDialogConfirm = {},
//                onSaveDialogDismiss = {},
//                onNewSetupNameChange = {},
//                onDeleteAction = {},
//                onPlayPauseClick = {},
//                onStopClick = {},
//                onNavigateToSetup = {}
//                // 3. Add a fake sound player that does nothing for the preview:
//            )
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
        Handler(Looper.getMainLooper()).postDelayed({
            val streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            if (streamId != 0) {
                activeStreamIds.add(streamId)
            }
        }, 150)
    }
}

suspend fun timerCoroutine(
    playSoundAction: (soundId: Int) -> Unit,
    workoutCompleted: MutableState<Boolean>,
    initialTimeLeftInPhaseMs: Double,
    initialOverallElapsedTimeMs: Double,
    initialIsExercisePhase: Boolean,
    initialSetsRemaining: Int,
    exerciseDurationMs: Double,
    restDurationMs: Double,
    totalWorkoutSets: Int,
    targetTotalWorkoutTimeMs: Double,
    onUpdate: (timeLeftInPhaseMs: Double, overallElapsedTimeMs: Double, isExercisePhase: Boolean, setsRemaining: Int) -> Unit,
    onPhaseChange: (newStatus: String) -> Unit,
    onComplete: (finalStatus: String) -> Unit
) {
    var currentTimeLeftInPhaseMs = initialTimeLeftInPhaseMs
    var currentOverallElapsedTimeMs = initialOverallElapsedTimeMs
    var currentPhaseIsExercise = initialIsExercisePhase
    var setsLeft = initialSetsRemaining
    val tickIntervalMs = 100L

    val effectiveTotalWorkoutTimeMs = if (targetTotalWorkoutTimeMs > 0) {
        targetTotalWorkoutTimeMs
    } else {
        (exerciseDurationMs * totalWorkoutSets + restDurationMs * (totalWorkoutSets - 1).coerceAtLeast(
            0
        ))
    }

    if (currentPhaseIsExercise) {
        onPhaseChange("Exercise Phase")
        if (!workoutCompleted.value) {
            playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
        }
    } else {
        onPhaseChange("Rest Phase")
        if (!workoutCompleted.value) {
            playSoundAction(AppSoundIds.EXERCISE_REST_SOUND_ID)
        }
    }
    if (setsLeft <= 0 && effectiveTotalWorkoutTimeMs <= 0) {
        onComplete("Error: No sets or time specified")
        return
    }
    mainLoop@ while (true) {
        if (effectiveTotalWorkoutTimeMs > 0 && currentOverallElapsedTimeMs >= effectiveTotalWorkoutTimeMs) {
            onComplete("Workout Complete")
            workoutCompleted.value = true
            playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
            break@mainLoop
        }
        if (setsLeft <= 0 && totalWorkoutSets > 0 && effectiveTotalWorkoutTimeMs <= 0) {
            onComplete("Workout Complete")
            workoutCompleted.value = true
            playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
            break@mainLoop
        }
        delay(tickIntervalMs)
        currentTimeLeftInPhaseMs -= tickIntervalMs
        currentOverallElapsedTimeMs += tickIntervalMs

        if (effectiveTotalWorkoutTimeMs > 0 && currentOverallElapsedTimeMs >= effectiveTotalWorkoutTimeMs) {
            onComplete("Workout Complete")
            workoutCompleted.value = true
            playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
            break@mainLoop
        }

        if (currentTimeLeftInPhaseMs <= 0) {
            currentTimeLeftInPhaseMs = 0.0
            onUpdate(
                currentTimeLeftInPhaseMs,
                currentOverallElapsedTimeMs,
                currentPhaseIsExercise,
                setsLeft
            )

            if (currentPhaseIsExercise) {
                setsLeft-- // Decrement sets after an exercise phase

                // Check for workout completion
                if (setsLeft <= 0) {
                    onComplete("Workout Complete")
                    workoutCompleted.value = true
                    playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
                    break@mainLoop // Exit the timer loop
                }

                // If not complete, transition to rest (or next set)
                currentPhaseIsExercise = false
                if (restDurationMs > 0) {
                    currentTimeLeftInPhaseMs = restDurationMs
                    onPhaseChange("Rest Phase")
                    if (!workoutCompleted.value) {
                        playSoundAction(AppSoundIds.EXERCISE_REST_SOUND_ID)
                    }
                } else {
                    // No rest, go straight to the next exercise phase
                    currentPhaseIsExercise = true
                    currentTimeLeftInPhaseMs = exerciseDurationMs
                    onPhaseChange("Exercise Phase")
                    if (!workoutCompleted.value) {
                        playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
                    }
                }
            }
            if (effectiveTotalWorkoutTimeMs > 0 && currentOverallElapsedTimeMs >= effectiveTotalWorkoutTimeMs) {
                onComplete("Workout Complete")
                workoutCompleted.value = true
                playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
                break@mainLoop
            }
            if (setsLeft <= 0 && totalWorkoutSets > 0 && effectiveTotalWorkoutTimeMs <= 0) {
                onComplete("Workout Complete")
                workoutCompleted.value = true
                playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
                break@mainLoop
            }

        }
        onUpdate(
            currentTimeLeftInPhaseMs,
            currentOverallElapsedTimeMs,
            currentPhaseIsExercise,
            setsLeft
        )
    }
}

@Composable
fun AppNavigation() {
    // 1. Create the NavController
    val navController = rememberNavController()

    // 2. Create the NavHost container
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {

        // 3. Define the composable for the "main" screen
        composable(Screen.Main.route) {
            PTTimerScreen(
                onNavigateToSetup = {
                    navController.navigate(Screen.Setup.route)
                }
            )
        }

        // 4. Define the composable for the "setup" screen
        composable(Screen.Setup.route) {
            SetupScreen(
                onNavigateToMain = {
                    navController.popBackStack() // This goes back to the previous screen
                }
            )
        }
    }
}


@Composable
fun PTTimerScreen(onNavigateToSetup: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sharedPreferences = context.getSharedPreferences("PT_Timer_Prefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    // val focusManager = LocalFocusManager.current
    fun loadDouble(key: String, default: Double): Double {
        val stringValue = sharedPreferences.getString(key, default.toString())
        return stringValue?.toDoubleOrNull() ?: default
    }
    fun formatForTextField(value: Double, isInteger: Boolean = false): String {
        return if (isInteger) {
            value.toInt().toString()
        } else if (value == 0.0) {
            "0"
        } else {
            // This removes trailing ".0" from whole numbers
            if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                value.toString()
            }
        }
    }

    // Numeric states
// Numeric states - Initialize with simple defaults ONLY.
    var exerciseTime by remember { mutableDoubleStateOf(30.0) }
    var restTime by remember { mutableDoubleStateOf(0.0) }
    var sets by remember { mutableDoubleStateOf(3.0) }
    var totalTime by remember { mutableDoubleStateOf(0.0) }

// TextFieldValue states
// TextFieldValue states
    var exerciseTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var restTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var setsTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var totalTimeTextFieldValue by remember { mutableStateOf(TextFieldValue()) }


// Error states
    var exerciseError by remember { mutableStateOf<String?>(null) }
    var restError by remember { mutableStateOf<String?>(null) }
    var setsError by remember { mutableStateOf<String?>(null) }
    var totalTimeError by remember { mutableStateOf<String?>(null) }
    val workoutCompleted = remember { mutableStateOf(false) }

// Setup states
    val setupNames = remember { mutableStateListOf<String>() }
    var selectedSetup by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newSetupName by remember { mutableStateOf(TextFieldValue("")) }
    var expanded by remember { mutableStateOf(false) }

// This single LaunchedEffect runs once on startup to initialize everything safely.
    LaunchedEffect(Unit) {
        // 1. Load initial values from SharedPreferences SAFELY.
        exerciseTime = loadDouble("exercise_time", 30.0)
        restTime = loadDouble("rest_time", 0.0)
        sets = loadDouble("sets", 3.0)
        totalTime = loadDouble("total_time", 0.0)

        // 2. Load setup names
        val savedNames =
            sharedPreferences.getStringSet("setup_names", emptySet())?.toList() ?: emptyList()
        setupNames.clear() // Clear any old names before adding new ones
        setupNames.addAll(savedNames.sorted())
        println("Setup names loaded: $setupNames")

        // 3. Set initial text field values from the now-loaded state.
        exerciseTextFieldValue = TextFieldValue(text = formatForTextField(exerciseTime))
        restTextFieldValue = TextFieldValue(text = formatForTextField(restTime))
        setsTextFieldValue = TextFieldValue(text = formatForTextField(sets, isInteger = true))
        totalTimeTextFieldValue = TextFieldValue(text = formatForTextField(totalTime))
    }


    // Gson for JSON serialization
    val gson = remember { Gson() }

    fun validateAndSave(
        editor: SharedPreferences.Editor?,
        textValue: String,
        isInteger: Boolean,
        minValue: Double = 0.0,
        allowZero: Boolean = true,
        onError: (String?) -> Unit,
        onSuccess: (Double) -> Unit,
        prefKey: String
    ) {
        if (textValue.isBlank() || textValue == "0") {
            if (!allowZero && minValue > 0) {
                onError("Cannot be empty or zero")
                return
            } else {
                onError(null)
                onSuccess(0.0)
                editor?.putString(prefKey, "0")?.apply()
                return
            }
        }
        val parsedValue =
            if (isInteger) textValue.toIntOrNull()?.toDouble() else textValue.toDoubleOrNull()
        if (parsedValue == null) {
            onError("Invalid number")
        } else if (parsedValue < minValue) {
            onError("Must be >= ${if (isInteger) minValue.toInt() else minValue}")
        } else {
            onError(null)
            onSuccess(parsedValue)
            editor?.putString(prefKey, textValue)?.apply()
        }
    }

    // Load setup
    fun loadSetup(name: String) {
        val json = sharedPreferences?.getString("setup_$name", null)
        json?.let {
            val config = gson.fromJson(it, SetupConfig::class.java)
            exerciseTime = config.exerciseTime
            restTime = config.restTime
            sets = config.sets
            totalTime = config.totalTime
            exerciseTextFieldValue = TextFieldValue(text = formatForTextField(exerciseTime))
            restTextFieldValue = TextFieldValue(text = formatForTextField(restTime))
            setsTextFieldValue = TextFieldValue(text = formatForTextField(sets, isInteger = true))
            totalTimeTextFieldValue = TextFieldValue(text = formatForTextField(totalTime))
            println("Loaded setup: $name, config=$config")
        }
    }

    // Save setup
    fun saveSetup(name: String) {
        if (name.isBlank()) return
        val config = SetupConfig(exerciseTime, restTime, sets, totalTime, delayTime = 0.0)
        val json = gson.toJson(config)
        editor?.putString("setup_$name", json)?.apply()
        if (!setupNames.contains(name)) {
            val updatedNames =
                sharedPreferences?.getStringSet("setup_names", emptySet())?.toMutableSet()
                    ?: mutableSetOf()
            updatedNames.add(name)
            editor?.putStringSet("setup_names", updatedNames)?.apply()
            setupNames.add(name)
            setupNames.sort()
        }
        println("Saved setup: $name, config=$config")
    }

    // Delete setup
    fun deleteSetup(name: String) {
        editor?.remove("setup_$name")?.apply()
        val updatedNames =
            sharedPreferences?.getStringSet("setup_names", emptySet())?.toMutableSet()
                ?: mutableSetOf()
        updatedNames.remove(name)
        editor?.putStringSet("setup_names", updatedNames)?.apply()
        setupNames.remove(name)
        selectedSetup = null
        println("Deleted setup: $name")
    }

    // Timer states
    val coroutineScope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var timeLeftInCurrentPhaseMs by remember { mutableDoubleStateOf(0.0) }
    var overallElapsedTimeMs by remember { mutableDoubleStateOf(0.0) }
    var currentSetsRemaining by remember { mutableDoubleStateOf(0.0) }
    var isExercisePhase by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Ready") }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var calculatedInitialTotalWorkoutTimeMs by remember { mutableDoubleStateOf(0.0) }
    var pausedTimeLeftInCurrentPhaseMs by remember { mutableDoubleStateOf(0.0) }
    var pausedOverallElapsedTimeMs by remember { mutableDoubleStateOf(0.0) }
    var pausedIsExercisePhase by remember { mutableStateOf(true) }

    // Set up the REAL SoundPool and its state here
    val soundPool = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        SoundPool.Builder().setMaxStreams(3).setAudioAttributes(audioAttributes).build()
    }
    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
        }
    }

    val activeStreamIds = remember { mutableStateListOf<Int>() }

    // Load sounds into the SoundPool
    // to change to a different sound file, just change the name after R.raw. to the new filename
    LaunchedEffect(soundPool, context) {
        AppSoundIds.EXERCISE_START_SOUND_ID = soundPool.load(context, R.raw.exercise_start, 1)
        AppSoundIds.EXERCISE_REST_SOUND_ID = soundPool.load(context, R.raw.exercise_rest, 1)
        AppSoundIds.EXERCISE_COMPLETE_SOUND_ID = soundPool.load(context, R.raw.exercise_complete, 1)
    }


    val decimalFormat = remember {
        DecimalFormat("0.0", DecimalFormatSymbols(Locale.US))
    }


    val formattedTimeLeft = remember(timeLeftInCurrentPhaseMs) {
        // Use Long for milliseconds to avoid floating point errors
        val totalMillis = timeLeftInCurrentPhaseMs.toLong()
        val totalSeconds = totalMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (totalMillis % 1000) / 100 // Get the first decimal place

        // Format using reliable integer values
        String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }


    val remainingDisplay = remember(
        isRunning,
        isPaused,
        status,
        totalTime,
        exerciseTime,
        sets,
        restTime,
        overallElapsedTimeMs
    ) {
        // Calculate the total time consistently here
        val totalWorkoutTimeMs = if (totalTime > 0) {
            totalTime * 1000
        } else {
            (exerciseTime * sets + restTime * (sets - 1).coerceAtLeast(0.0)) * 1000
        }

        val remainingSeconds =
            (totalWorkoutTimeMs - overallElapsedTimeMs).coerceAtLeast(0.0) / 1000.0

        when {
            // If the workout is complete or in a ready/error state, show nothing.
            status.startsWith("Workout Complete") || status == "Ready" || status.startsWith("Error:") -> ""

            // If the timer is active (running or paused) and we have a valid calculated time...
            (isRunning || isPaused) && totalWorkoutTimeMs > 0 -> { // <--- CORRECTED
                // Format the remaining time into M:SS
                val minutes = (remainingSeconds / 60).toInt()
                val seconds = (remainingSeconds % 60).toInt()
                val prefix = if (isPaused) "Paused | " else ""
                String.format(Locale.US, "%sTotal Left: %d:%02d", prefix, minutes, seconds)
            }

            // Fallback for any other state
            else -> ""
        }
    }



    PTTimerScreenContent(
        // State to Display
        formattedTimeLeft = formattedTimeLeft,
        status = status,
        remainingDisplay = remainingDisplay,
        exerciseTextFieldValue = exerciseTextFieldValue,
        restTextFieldValue = restTextFieldValue,
        setsTextFieldValue = setsTextFieldValue,
        totalTimeTextFieldValue = totalTimeTextFieldValue,
        exerciseError = exerciseError,
        restError = restError,
        setsError = setsError,
        totalTimeError = totalTimeError,
        isRunning = isRunning,
        expanded = expanded, // This seems to be a bug in original code, should be a separate 'expanded' state
        selectedSetup = selectedSetup,
        savedSetupNames = setupNames,
        newSetupName = newSetupName,

        // Event Callbacks
        onExerciseTimeChange = { textFieldValue ->
            exerciseTextFieldValue = textFieldValue
            validateAndSave(
                editor,
                textFieldValue.text,
                isInteger = false,
                allowZero = false,
                prefKey = "exercise_time",
                onError = { error -> exerciseError = error },
                onSuccess = { value -> exerciseTime = value })
        },
        onRestTimeChange = { textFieldValue ->
            restTextFieldValue = textFieldValue
            validateAndSave(
                editor,
                textFieldValue.text,
                isInteger = false,
                prefKey = "rest_time",
                onError = { error -> restError = error },
                onSuccess = { value -> restTime = value })
        },
        onSetsChange = { textFieldValue ->
            setsTextFieldValue = textFieldValue
            validateAndSave(
                editor,
                textFieldValue.text,
                isInteger = true,
                allowZero = false,
                prefKey = "sets",
                onError = { error -> setsError = error },
                onSuccess = { value -> sets = value })
        },
        onTotalTimeChange = { textFieldValue ->
            totalTimeTextFieldValue = textFieldValue
            validateAndSave(
                editor,
                textFieldValue.text,
                isInteger = false,
                prefKey = "total_time",
                onError = { error -> totalTimeError = error },
                onSuccess = { value -> totalTime = value })
        },
        onExpandedChange = { isExpanded ->
            expanded = isExpanded
        },
        focusManager = focusManager,
        onDropdownMenuItemClick = { name ->
            loadSetup(name)
            selectedSetup = name
            newSetupName = TextFieldValue(name)
            expanded = false
        },
        onSaveSetupClick = {
            if (newSetupName.text.isNotBlank()) {
                saveSetup(newSetupName.text)
                selectedSetup = newSetupName.text
            }
        },
        // This now accepts a TextFieldValue
        onNewSetupNameChange = { newTextFieldValue ->
            newSetupName = newTextFieldValue
        },

        onDeleteAction = {
            selectedSetup?.let {
                deleteSetup(it)
            }
        },
        onPlayPauseClick = {
            if (isRunning) { // Pause logic
                timerJob?.cancel()
                isPaused = true
                isRunning = false
                pausedTimeLeftInCurrentPhaseMs = timeLeftInCurrentPhaseMs
                pausedOverallElapsedTimeMs = overallElapsedTimeMs
                pausedIsExercisePhase = isExercisePhase
            } else { // Play/Resume logic
                timerJob = coroutineScope.launch {
                    isRunning = true // Set running state
                    timerCoroutine(

                        // Parameters to start or resume the timer
                        initialTimeLeftInPhaseMs = if (isPaused) pausedTimeLeftInCurrentPhaseMs else exerciseTime * 1000,
                        initialOverallElapsedTimeMs = if (isPaused) pausedOverallElapsedTimeMs else 0.0,
                        initialIsExercisePhase = if (isPaused) pausedIsExercisePhase else true, // <-- Corrected Name
                        initialSetsRemaining = if (isPaused) currentSetsRemaining.toInt() else sets.toInt(), // <-- ADDED THIS

                        // Static workout parameters
                        exerciseDurationMs = exerciseTime * 1000,
                        restDurationMs = restTime * 1000,
                        totalWorkoutSets = sets.toInt(),
                        targetTotalWorkoutTimeMs = totalTime * 1000,
                        workoutCompleted = workoutCompleted,

                        // Callback Actions
                        playSoundAction = { soundId ->
                            playSound(
                                soundPool,
                                activeStreamIds,
                                soundId
                            )
                        },
                        onUpdate = { timeLeft, overallTime, isExercise, setsRemaining ->
                            timeLeftInCurrentPhaseMs = timeLeft
                            overallElapsedTimeMs = overallTime
                            isExercisePhase = isExercise
                            currentSetsRemaining = setsRemaining.toDouble()
                        },
                        onPhaseChange = { newStatus ->
                            status = newStatus
                        },
                        onComplete = { finalStatus ->
                            status = finalStatus
                            isRunning = false
                        }
                    )
                }
                isPaused = false
            }

        },


        onStopClick = {
            timerJob?.cancel()
            timerJob = null
            status = "Ready"
            isRunning = false
            isPaused = false
            timeLeftInCurrentPhaseMs = exerciseTime * 1000
            workoutCompleted.value = false
        },
        onNavigateToSetup = onNavigateToSetup,

        // Utilities

    )

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PTTimerScreenContent(
    focusManager: FocusManager,
    formattedTimeLeft: String,
    status: String,
    remainingDisplay: String,
    exerciseTextFieldValue: TextFieldValue,
    restTextFieldValue: TextFieldValue,
    setsTextFieldValue: TextFieldValue,
    totalTimeTextFieldValue: TextFieldValue,
    exerciseError: String?,
    restError: String?,
    setsError: String?,
    totalTimeError: String?,
    isRunning: Boolean,
    expanded: Boolean,
    selectedSetup: String?,
    savedSetupNames: List<String>,
    newSetupName: TextFieldValue,

    // Event Callbacks
    onExerciseTimeChange: (TextFieldValue) -> Unit,
    onRestTimeChange: (TextFieldValue) -> Unit,
    onSetsChange: (TextFieldValue) -> Unit,
    onTotalTimeChange: (TextFieldValue) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onDropdownMenuItemClick: (String) -> Unit,
    onSaveSetupClick: () -> Unit,
    onNewSetupNameChange: (TextFieldValue) -> Unit,
    onDeleteAction: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onNavigateToSetup: () -> Unit


) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formattedTimeLeft,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 100.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = status, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = remainingDisplay, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown for setups
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { onExpandedChange(!expanded) },
            ) {
                TextField(
                    value = newSetupName,
                    onValueChange = onNewSetupNameChange,
                    modifier = Modifier
                        .menuAnchor() // This is from the DropdownBox
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                // This check is important. If the menu is open, we don't
                                // want to immediately close it by changing the expanded state.
                                // But if it loses focus for any other reason (button click, etc),
                                // we can ensure the dropdown menu is closed.
                                onExpandedChange(false)
                            }
                        },
                    readOnly = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    label = { Text("Workout Setup") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        onExpandedChange(false)
                        focusManager.clearFocus()
                    }
                ) {
                    savedSetupNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onDropdownMenuItemClick(name)
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onNavigateToSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Setup Workouts")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input fields
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = exerciseTextFieldValue,
                    onValueChange = onExerciseTimeChange,
                    label = { Text("Exercise") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    isError = exerciseError != null,
                    supportingText = { if (exerciseError != null) Text(exerciseError) })
                TextField(
                    value = restTextFieldValue,
                    onValueChange = onRestTimeChange,
                    label = { Text("Rest") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    isError = restError != null,
                    supportingText = { if (restError != null) Text(restError) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = setsTextFieldValue,
                    onValueChange = onSetsChange,
                    label = { Text("Sets") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    isError = setsError != null,
                    supportingText = { if (setsError != null) Text(setsError) })
                TextField(
                    value = totalTimeTextFieldValue,
                    onValueChange = onTotalTimeChange,
                    label = { Text("Total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    isError = totalTimeError != null,
                    supportingText = { if (totalTimeError != null) Text(totalTimeError) })
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes controls to the bottom

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveSetupClick) { Text("Save") }
                Button(onClick = onDeleteAction, enabled = selectedSetup != null) { Text("Delete") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timer controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FloatingActionButton(
                    onClick = onPlayPauseClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Play"
                    )
                }
                FloatingActionButton(
                    onClick = onStopClick,
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                }
            }


        }
    }
}







