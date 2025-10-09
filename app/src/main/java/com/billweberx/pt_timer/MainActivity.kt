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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

// Required import for MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.ui.focus.FocusManager

import java.text.DecimalFormatSymbols
import java.util.Locale

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
    val totalTime: Double
)

// Format function at top level
fun formatForTextField(value: Double, isInteger: Boolean = false): String {
    return if (isInteger) value.toInt().toString()
    else if (value == 0.0) "0"
    else value.toString()
}

class MainActivity : ComponentActivity() {
    private var audioFocusRequest: AudioFocusRequest? = null
    override fun onCreate(savedInstanceState: Bundle?) {
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
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        setContent {
            PT_TimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PTTimerScreen()
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
            PTTimerScreenContent(sharedPreferences = null,
                // 2. Add a fake FocusManager for the preview:
                focusManager = object : FocusManager {
                    override fun clearFocus(force: Boolean) {}
                    override fun moveFocus(focusDirection: FocusDirection): Boolean = false
                },
                // 3. Add a fake sound player that does nothing for the preview:
                onPlaySound = { /* In preview, this does nothing */ }
                )
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
        (exerciseDurationMs * totalWorkoutSets + restDurationMs * (totalWorkoutSets - 1).coerceAtLeast(0))
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
            onUpdate(currentTimeLeftInPhaseMs, currentOverallElapsedTimeMs, currentPhaseIsExercise, setsLeft)

            if (currentPhaseIsExercise) {
                setsLeft--
                if (setsLeft <= 0 && totalWorkoutSets > 0 && effectiveTotalWorkoutTimeMs <= 0) {
                    onComplete("Workout Complete")
                    workoutCompleted.value = true
                    playSoundAction(AppSoundIds.EXERCISE_COMPLETE_SOUND_ID)
                    break@mainLoop
                }
                if (setsLeft > 0 || (effectiveTotalWorkoutTimeMs > 0 && currentOverallElapsedTimeMs < effectiveTotalWorkoutTimeMs)) {
                    currentPhaseIsExercise = false
                    if (restDurationMs > 0) {
                        currentTimeLeftInPhaseMs = restDurationMs
                        onPhaseChange("Rest Phase")
                        if ((effectiveTotalWorkoutTimeMs - currentOverallElapsedTimeMs) > 0.1) {
                            if (!workoutCompleted.value) {
                                playSoundAction(AppSoundIds.EXERCISE_REST_SOUND_ID)
                            }
                        }
                    } else {
                        println("Skipping rest, starting next exercise: setsLeft=$setsLeft")
                        currentPhaseIsExercise = true
                        currentTimeLeftInPhaseMs = exerciseDurationMs
                        onPhaseChange("Exercise Phase")
                        if (!workoutCompleted.value) {
                            playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
                        }
                    }
                } else {
                    break@mainLoop
                }
            } else {
                currentPhaseIsExercise = true
                currentTimeLeftInPhaseMs = exerciseDurationMs
                onPhaseChange("Exercise Phase")
                if (!workoutCompleted.value) {
                    playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)
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
        onUpdate(currentTimeLeftInPhaseMs, currentOverallElapsedTimeMs, currentPhaseIsExercise, setsLeft)
    }
}

@Composable
fun PTTimerScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("PT_Timer_Prefs", Context.MODE_PRIVATE)
    val focusManager = LocalFocusManager.current

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
    LaunchedEffect(soundPool, context) {
        AppSoundIds.EXERCISE_START_SOUND_ID = soundPool.load(context, R.raw.exercise_start, 1)
        AppSoundIds.EXERCISE_REST_SOUND_ID = soundPool.load(context, R.raw.exercise_rest, 1)
        AppSoundIds.EXERCISE_COMPLETE_SOUND_ID = soundPool.load(context, R.raw.exercise_complete, 1)
    }

    // Now, call PTTimerScreenContent with all the required parameters
    PTTimerScreenContent(
        sharedPreferences = sharedPreferences,
        focusManager = focusManager, // <-- Provide the focus manager
        onPlaySound = { soundId -> // <-- Provide the REAL sound-playing function
            playSound(soundPool, activeStreamIds, soundId)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PTTimerScreenContent(
    sharedPreferences: SharedPreferences?,
    focusManager: FocusManager,
    onPlaySound: (soundId: Int) -> Unit
) {
    val editor = sharedPreferences?.edit()

    // Numeric states
    var exerciseTime by remember {
        mutableDoubleStateOf(sharedPreferences?.getString("exercise_time", "30.0")?.toDoubleOrNull() ?: 30.0)
    }
    var restTime by remember {
        mutableDoubleStateOf(sharedPreferences?.getString("rest_time", "0.0")?.toDoubleOrNull() ?: 0.0)
    }
    var sets by remember {
        mutableDoubleStateOf(sharedPreferences?.getString("sets", "3")?.toDoubleOrNull() ?: 3.0)
    }
    var totalTime by remember {
        mutableDoubleStateOf(sharedPreferences?.getString("total_time", "0.0")?.toDoubleOrNull() ?: 0.0)
    }

    // TextFieldValue states
    var exerciseTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = formatForTextField(exerciseTime)))
    }
    var restTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = formatForTextField(restTime)))
    }
    var setsTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = formatForTextField(sets, isInteger = true)))
    }
    var totalTimeTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = formatForTextField(totalTime)))
    }

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
    var newSetupName by remember { mutableStateOf("") }

    // Load setup names
    LaunchedEffect(Unit) {
        if (sharedPreferences?.getString("total_time", "0.0") != "0") {
            editor?.putString("total_time", "0")?.apply()
        }
        if (sharedPreferences?.getString("rest_time", "0.0") != "0") {
            editor?.putString("rest_time", "0")?.apply()
        }
        val savedNames = sharedPreferences?.getStringSet("setup_names", emptySet())?.toList() ?: emptyList()
        setupNames.addAll(savedNames.sorted())
        println("Setup names loaded: $setupNames")
    }

    // Gson for JSON serialization
    val gson = remember { Gson() }

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
        val config = SetupConfig(exerciseTime, restTime, sets, totalTime)
        val json = gson.toJson(config)
        editor?.putString("setup_$name", json)?.apply()
        if (!setupNames.contains(name)) {
            val updatedNames = sharedPreferences?.getStringSet("setup_names", emptySet())?.toMutableSet() ?: mutableSetOf()
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
        val updatedNames = sharedPreferences?.getStringSet("setup_names", emptySet())?.toMutableSet() ?: mutableSetOf()
        updatedNames.remove(name)
        editor?.putStringSet("setup_names", updatedNames)?.apply()
        setupNames.remove(name)
        selectedSetup = null
        println("Deleted setup: $name")
    }

    // Sync text fields
    LaunchedEffect(exerciseTime) {
        val newText = formatForTextField(exerciseTime)
        if (newText != exerciseTextFieldValue.text) {
            exerciseTextFieldValue = TextFieldValue(text = newText)
        }
    }
    LaunchedEffect(restTime) {
        val newText = formatForTextField(restTime)
        if (newText != restTextFieldValue.text) {
            restTextFieldValue = TextFieldValue(text = newText)
        }
    }
    LaunchedEffect(sets) {
        val newText = formatForTextField(sets, isInteger = true)
        if (newText != setsTextFieldValue.text) {
            setsTextFieldValue = TextFieldValue(text = newText)
        }
    }
    LaunchedEffect(totalTime) {
        val newText = formatForTextField(totalTime)
        if (newText != totalTimeTextFieldValue.text) {
            totalTimeTextFieldValue = TextFieldValue(text = newText)
        }
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
    var pausedCurrentSetsRemaining by remember { mutableDoubleStateOf(0.0) }
    var pausedStatus by remember { mutableStateOf("") }
    var pausedCalculatedInitialTotalWorkoutTimeMs by remember { mutableDoubleStateOf(0.0) }
    val decimalFormat = remember {
        DecimalFormat("0.0", DecimalFormatSymbols(Locale.US))
    }

    val formattedTimeLeft = remember(timeLeftInCurrentPhaseMs) {
        val totalSeconds = timeLeftInCurrentPhaseMs / 1000.0
        val minutes = (totalSeconds / 60).toInt()
        val seconds = totalSeconds % 60
        String.format(Locale.US, "%02d:%s", minutes, decimalFormat.format(seconds))
    }

    val remainingDisplay = remember(
        isRunning,
        isPaused,
        status,
        currentSetsRemaining,
        calculatedInitialTotalWorkoutTimeMs,
        overallElapsedTimeMs,
        totalTime
    ) {
        when {
            !isRunning && !isPaused && status == "Ready" -> "Configure and start"
            status.startsWith("Workout Complete") || status.startsWith("Error:") -> ""
            isPaused -> {
                "Paused. " + if (totalTime > 0 && calculatedInitialTotalWorkoutTimeMs > 0) {
                    val remainingOverallSeconds = (calculatedInitialTotalWorkoutTimeMs - overallElapsedTimeMs).coerceAtLeast(0.0) / 1000.0
                    "Total time left: ${decimalFormat.format(remainingOverallSeconds)}s"
                } else if (sets > 0) {
                    "Sets left: ${currentSetsRemaining.toInt()}"
                } else {
                    ""
                }
            }
            isRunning -> {
                if (totalTime > 0 && calculatedInitialTotalWorkoutTimeMs > 0) {
                    val remainingOverallSeconds = (calculatedInitialTotalWorkoutTimeMs - overallElapsedTimeMs).coerceAtLeast(0.0) / 1000.0
                    "Total time left: ${decimalFormat.format(remainingOverallSeconds)}s"
                } else if (sets > 0) {
                    "Sets left: ${currentSetsRemaining.toInt()}"
                } else {
                    "Running..."
                }
            }
            else -> "Awaiting action"
        }
    }

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
        val parsedValue = if (isInteger) textValue.toIntOrNull()?.toDouble() else textValue.toDoubleOrNull()
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

    fun startTimer()
    {
        var hasError = false
        if (exerciseTime <= 0) { exerciseError = "Must be > 0"; hasError = true } else { exerciseError = null }
        if (sets <= 0 && totalTime <= 0) { setsError = "Sets or total time must be > 0"; hasError = true } else { setsError = null }

        validateAndSave(
            editor = editor,
            restTextFieldValue.text,
            false,
            0.0,
            true,
            { restError = it },
            { restTime = it },
            "rest_time"
        )
        if (restError != null) {
            hasError = true
        }

        validateAndSave(
            editor = editor,
            totalTimeTextFieldValue.text,
            false,
            0.0,
            true,
            { totalTimeError = it },
            { totalTime = it },
            "total_time"
        )
        if (totalTimeError != null) {
            hasError = true
        }

        if (hasError) {
            status = "Fix errors before starting"
            return
        }
        exerciseError = null; restError = null; setsError = null; totalTimeError = null
        workoutCompleted.value = false

        editor?.putString("rest_time", if (restTime == 0.0) "0" else restTime.toString())?.apply()
        editor?.putString("total_time", if (totalTime == 0.0) "0" else totalTime.toString())?.apply()

        if (isPaused) {
            timeLeftInCurrentPhaseMs = pausedTimeLeftInCurrentPhaseMs
            overallElapsedTimeMs = pausedOverallElapsedTimeMs
            isExercisePhase = pausedIsExercisePhase
            currentSetsRemaining = pausedCurrentSetsRemaining
            calculatedInitialTotalWorkoutTimeMs = pausedCalculatedInitialTotalWorkoutTimeMs
        } else {
            isExercisePhase = true
            currentSetsRemaining = sets
            timeLeftInCurrentPhaseMs = exerciseTime * 1000
            overallElapsedTimeMs = 0.0
            calculatedInitialTotalWorkoutTimeMs = if (totalTime > 0) {
                totalTime * 1000
            } else {
                (exerciseTime * sets + restTime * (sets - 1).coerceAtLeast(0.0)) * 1000
            }
        }
        isPaused = false
        isRunning = true

        println("startTimer: restTime=$restTime, totalTime=$totalTime, sets=$sets, calculatedInitialTotalWorkoutTimeMs=$calculatedInitialTotalWorkoutTimeMs")
    }

    fun pauseTimer() {
        if (isRunning) {
            pausedTimeLeftInCurrentPhaseMs = timeLeftInCurrentPhaseMs
            pausedOverallElapsedTimeMs = overallElapsedTimeMs
            pausedIsExercisePhase = isExercisePhase
            pausedCurrentSetsRemaining = currentSetsRemaining
            pausedStatus = status
            pausedCalculatedInitialTotalWorkoutTimeMs = calculatedInitialTotalWorkoutTimeMs

            isRunning = false
            isPaused = true
            status = "Paused. $pausedStatus"
        }
    }

    fun stopTimer() {
        isRunning = false
        isPaused = false
        status = "Ready"
        timeLeftInCurrentPhaseMs = exerciseTime * 1000
        overallElapsedTimeMs = 0.0
        currentSetsRemaining = sets
        isExercisePhase = true
        workoutCompleted.value = false
        pausedTimeLeftInCurrentPhaseMs = 0.0
        pausedOverallElapsedTimeMs = 0.0
        pausedIsExercisePhase = true
        pausedCurrentSetsRemaining = 0.0
        pausedStatus = ""
        pausedCalculatedInitialTotalWorkoutTimeMs = 0.0
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            timerJob = coroutineScope.launch {
                timerCoroutine(
                    playSoundAction = onPlaySound,
                    workoutCompleted = workoutCompleted,
                    initialTimeLeftInPhaseMs = timeLeftInCurrentPhaseMs,
                    initialOverallElapsedTimeMs = overallElapsedTimeMs,
                    initialIsExercisePhase = isExercisePhase,
                    initialSetsRemaining = currentSetsRemaining.toInt(),
                    exerciseDurationMs = exerciseTime * 1000,
                    restDurationMs = restTime * 1000,
                    totalWorkoutSets = sets.toInt(),
                    targetTotalWorkoutTimeMs = if (totalTime > 0) totalTime * 1000 else 0.0,
                    onUpdate = { newTimeLeft, newOverallElapsed, newIsExercise, newSetsLeft ->
                        timeLeftInCurrentPhaseMs = newTimeLeft
                        overallElapsedTimeMs = newOverallElapsed
                        isExercisePhase = newIsExercise
                        currentSetsRemaining = newSetsLeft.toDouble()
                    },
                    onPhaseChange = { newStatus ->
                        status = newStatus
                    },
                    onComplete = { finalStatus ->
                        status = finalStatus
                        isRunning = false
                        isPaused = false
                    }
                )
            }
        } else {
            timerJob?.cancel()
        }
    }

    @Composable
    fun AutoSelectTextField(
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        label: String,
        isError: Boolean,
        onValidate: () -> Unit,
        modifier: Modifier = Modifier,
        readOnly: Boolean = false
    ) {
        var wasFocused by remember { mutableStateOf(false) }
        val isFocused = remember { mutableStateOf(false) }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                if (isError) onValidate()
            },
            label = { Text(label) },
            isError = isError,
            modifier = modifier
                .onFocusChanged { focusState ->
                    isFocused.value = focusState.isFocused
                    if (focusState.isFocused && !wasFocused) {
                        wasFocused = true
                    } else if (!focusState.isFocused && wasFocused) {
                        wasFocused = false
                        onValidate()
                    }
                },
            readOnly = readOnly,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (label.contains("sets", ignoreCase = true)) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = if (label.contains("total", ignoreCase = true)) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onValidate()
                    focusManager.clearFocus()
                },
                onNext = {
                    onValidate()
                    focusManager.moveFocus(FocusDirection.Down)
                }
            )
        )

        LaunchedEffect(wasFocused) {
            if (wasFocused) {
                onValueChange(value.copy(selection = TextRange(0, value.text.length)))
            }
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("PT Interval Timer") }) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    formattedTimeLeft,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExercisePhase) Color(0xFF4CAF50) else Color(0xFF2196F3)
                )
                Text(status, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(remainingDisplay, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))

                // Dropdown for setups
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedSetup ?: "Select Setup",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Saved Setups") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        setupNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedSetup = name
                                    loadSetup(name)
                                    expanded = false
                                    println("Selected setup: $name")
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                AutoSelectTextField(
                    value = exerciseTextFieldValue,
                    onValueChange = { exerciseTextFieldValue = it },
                    label = "Exercise time (seconds)",
                    isError = exerciseError != null,
                    onValidate = {
                        validateAndSave(
                            editor = editor,
                            exerciseTextFieldValue.text,
                            false,
                            0.01,
                            false,
                            { exerciseError = it },
                            { exerciseTime = it },
                            "exercise_time"
                        )
                    },
                    readOnly = isRunning || isPaused,
                    modifier = Modifier.fillMaxWidth()
                )

                AutoSelectTextField(
                    value = restTextFieldValue,
                    onValueChange = { restTextFieldValue = it },
                    label = "Rest time (seconds)",
                    isError = restError != null,
                    onValidate = {
                        validateAndSave(
                            editor = editor,
                            restTextFieldValue.text,
                            false,
                            0.0,
                            true,
                            { restError = it },
                            { restTime = it },
                            "rest_time"
                        )
                    },
                    readOnly = isRunning || isPaused,
                    modifier = Modifier.fillMaxWidth()
                )

                AutoSelectTextField(
                    value = setsTextFieldValue,
                    onValueChange = { setsTextFieldValue = it },
                    label = "Number of sets",
                    isError = setsError != null,
                    onValidate = {
                        validateAndSave(
                            editor = editor,
                            setsTextFieldValue.text,
                            true,
                            0.0,
                            true,
                            { setsError = it },
                            { sets = it },
                            "sets"
                        )
                    },
                    readOnly = isRunning || isPaused,
                    modifier = Modifier.fillMaxWidth()
                )

                AutoSelectTextField(
                    value = totalTimeTextFieldValue,
                    onValueChange = { totalTimeTextFieldValue = it },
                    label = "Total workout time (sec, optional)",
                    isError = totalTimeError != null,
                    onValidate = {
                        validateAndSave(
                            editor = editor,
                            totalTimeTextFieldValue.text,
                            false,
                            0.0,
                            true,
                            { totalTimeError = it },
                            { totalTime = it },
                            "total_time"
                        )
                    },
                    readOnly = isRunning || isPaused,
                    modifier = Modifier.fillMaxWidth()
                )

                // Single Column for all buttons with consistent 8.dp spacing
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus() // Force validation before saving
                            showSaveDialog = true
                            newSetupName = selectedSetup ?: "" // Prefill with selected setup name
                        },
                        enabled = !isRunning && !isPaused,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 4.dp)
                    ) {
                        Text("Save Setup")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { selectedSetup?.let { deleteSetup(it) } },
                        enabled = !isRunning && !isPaused && selectedSetup != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 4.dp)
                    ) {
                        Text("Delete Selected Setup")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (isRunning || isPaused) {
                                stopTimer()
                            } else {
                                startTimer()
                            }
                        },
                        enabled = if (!isRunning && !isPaused) {
                            exerciseTime > 0 && (sets > 0 || totalTime > 0)
                        } else {
                            true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning || isPaused) Color(0xFFFF9999) else Color(0xFF90EE90),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 4.dp)
                    ) {
                        Text(if (isRunning || isPaused) "Stop Timer" else "Start Timer")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (isRunning && !isPaused) {
                                pauseTimer()
                            } else if (isPaused) {
                                workoutCompleted.value = false
                                startTimer()
                            }
                        },
                        enabled = isRunning || isPaused,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 4.dp)
                    ) {
                        Text(
                            when {
                                isRunning && !isPaused -> "Pause"
                                isPaused -> "Resume"
                                else -> "Pause"
                            }
                        )
                    }
                }
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Setup") },
                    text = {
                        OutlinedTextField(
                            value = newSetupName,
                            onValueChange = { newSetupName = it },
                            label = { Text("Enter setup name") },
                            singleLine = true,
                            isError = newSetupName.isNotBlank() && setupNames.contains(newSetupName) && newSetupName != selectedSetup,
                            supportingText = {
                                if (newSetupName.isNotBlank() && setupNames.contains(newSetupName) && newSetupName != selectedSetup) {
                                    Text("Name already exists")
                                }
                            }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                saveSetup(newSetupName)
                                newSetupName = ""
                                showSaveDialog = false
                                selectedSetup = null // Reset selection after saving
                            },
                            enabled = newSetupName.isNotBlank() && (!setupNames.contains(newSetupName) || newSetupName == selectedSetup)
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showSaveDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}