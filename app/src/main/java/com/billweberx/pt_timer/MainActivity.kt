package com.billweberx.pt_timer

import android.content.Context
import android.content.Intent
import android.media.SoundPool
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
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
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import java.io.File
import androidx.core.net.toUri

// Define AppSoundIds
object AppSoundIds {
    var EXERCISE_START_SOUND_ID: Int = 0
    var EXERCISE_REST_SOUND_ID: Int = 0
    var EXERCISE_COMPLETE_SOUND_ID: Int = 0
}

// Define SetupConfig data class for storing timer values
data class SetupConfig(
    val exerciseTime: Double,
    val restTime: Double,
    val sets: Double,
    val totalTime: Double,
    val delayTime: Double
)

// Data class for storing a named setup in a file
data class TimerSetup(val name: String, val config: SetupConfig)

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

    // --- MODERN FILE HANDLING ---
    private lateinit var saveFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var openFileLauncher: ActivityResultLauncher<Intent>

    // We will use a shared ViewModel to communicate results back to the UI
    private val viewModel = TimerViewModel()

    private var setupsForSaving: List<TimerSetup> = emptyList()

    // 1. Silent saving to private app storage
    fun saveToPrivateFile(setups: List<TimerSetup>) {
        try {
            val file = File(filesDir, "pt_timer_setups.json")
            val json = GsonBuilder().setPrettyPrinting().create().toJson(setups)
            file.writeText(json)
            // No Toast needed for silent save
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error during silent save.", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Silent loading from private app storage
    fun loadFromPrivateFile(): List<TimerSetup> {
        try {
            val file = File(filesDir, "pt_timer_setups.json")
            if (!file.exists()) return emptyList()

            val json = file.readText()
            return Gson().fromJson(json, object : TypeToken<List<TimerSetup>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading setups.", Toast.LENGTH_SHORT).show()
        }
        return emptyList()
    }

    // EXPORT: Use the file picker to save a copy of the private file
    fun exportSetups() {
        setupsForSaving = viewModel.loadedSetups
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "pt_timer_backup.json")

            // Only set the initial URI on Android 8.0 (API 26) and higher
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/document/primary:Documents".toUri())
            }

        }
        if (::saveFileLauncher.isInitialized) {
            saveFileLauncher.launch(intent)
        }
    }

    // IMPORT: Use the file picker to load a file and overwrite private storage
    fun importSetups() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"

            // Only set the initial URI on Android 8.0 (API26) and higher
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/document/primary:Documents".toUri())
            }

        }
        if (::openFileLauncher.isInitialized) {
            openFileLauncher.launch(intent)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the launchers here, in onCreate, where it's safe.
        saveFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val json = GsonBuilder().setPrettyPrinting().create().toJson(setupsForSaving)
                        contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
                        viewModel.updateSetups(setupsForSaving) // Update the ViewModel
                    } catch (_: Exception) {
                        Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        openFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val json = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                        val setups = Gson().fromJson<MutableList<TimerSetup>>(json, object : TypeToken<MutableList<TimerSetup>>() {}.type) ?: mutableListOf()
                        Toast.makeText(this, "${setups.size} setups loaded", Toast.LENGTH_SHORT).show()
                        saveToPrivateFile(setups)
                        viewModel.updateSetups(setups) // Update the ViewModel
                        Toast.makeText(this, "${setups.size} setups imported successfully.", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        setContent {
            PT_TimerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }
}

// A simple data holder class to bridge MainActivity and the UI
class TimerViewModel {
    var loadedSetups by mutableStateOf<List<TimerSetup>>(emptyList())
        private set

    fun updateSetups(newSetups: List<TimerSetup>) {
        loadedSetups = newSetups
    }
}


// --- NAVIGATION COMPOSABLE ---
@Composable
fun AppNavigation(viewModel: TimerViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            PTTimerScreen(
                viewModel = viewModel,
                _onNavigateToSetup = { navController.navigate(Screen.Setup.route) }
            )
        }
        composable(Screen.Setup.route) { SetupScreen(onNavigateToMain = { navController.popBackStack() }) }
    }
}

// In your SetupScreen.kt or where SetupScreen is defined
@Composable
fun SetupScreen(onNavigateToMain: () -> Unit) {
    val context = LocalContext.current
    val mainActivity = context as MainActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { mainActivity.importSetups() }) {
            Text("Import Setups from Backup")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { mainActivity.exportSetups() }) {
            Text("Export Setups to Backup")
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNavigateToMain) { Text("Back to Main") }
    }
}


// --- SOUND AND TIMER LOGIC (Not part of any class) ---

fun playSound(soundPool: SoundPool, activeStreamIds: SnapshotStateList<Int>, soundId: Int) {
    activeStreamIds.forEach { streamId -> soundPool.stop(streamId) }
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
    targetTotalWorkoutTimeMs: Double,
    workoutCompleted: MutableState<Boolean>,
    isResuming: Boolean,
    initialTimeLeftInPhaseMs: Double,
    initialIsExercisePhase: Boolean,
    initialSetsRemaining: Int,
    playSoundAction: (soundId: Int) -> Unit,
    onUpdate: (timeLeftInPhase: Double, statusText: String, secondaryValue: Double) -> Unit,
    onComplete: (finalStatus: String) -> Unit
) {
    val tickIntervalMs = 100L
    if (!isResuming) playSoundAction(AppSoundIds.EXERCISE_START_SOUND_ID)

    if (isSetsMode) {
        var setsLeft = initialSetsRemaining
        var currentPhaseIsExercise = initialIsExercisePhase
        var currentTimeLeftInPhaseMs = initialTimeLeftInPhaseMs

        while (setsLeft > 0) {
            while (currentTimeLeftInPhaseMs > 0) {
                delay(tickIntervalMs)
                currentTimeLeftInPhaseMs -= tickIntervalMs
                onUpdate(currentTimeLeftInPhaseMs, if (currentPhaseIsExercise) "Exercise" else "Rest", setsLeft.toDouble())
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
        if(isResuming) phaseTimeLeftMs = initialTimeLeftInPhaseMs

        while (totalTimeLeftMs > 0) {
            while (phaseTimeLeftMs > 0 && totalTimeLeftMs > 0) {
                delay(tickIntervalMs)
                phaseTimeLeftMs -= tickIntervalMs
                totalTimeLeftMs -= tickIntervalMs
                onUpdate(phaseTimeLeftMs, if (currentPhaseIsExercise) "Exercise" else "Rest", totalTimeLeftMs)
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

// --- MAIN UI COMPOSABLE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LocalVariableName")
fun PTTimerScreen(viewModel: TimerViewModel, _onNavigateToSetup: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mainActivity = context as MainActivity
    val sharedPreferences = remember { context.getSharedPreferences("PT_Timer_Prefs", Context.MODE_PRIVATE) }
    val editor = remember { sharedPreferences.edit() }
    // --- Data Handling ---
    fun formatForTextField(value: Double, isInteger: Boolean = false): String {
        return if (isInteger) value.toInt().toString()
        else if (value == 0.0) "0"
        else if (value % 1.0 == 0.0) value.toInt().toString()
        else value.toString()
    }
    // --- State Variables ---
    val loadedSetups = viewModel.loadedSetups
    val setupNames = remember(loadedSetups) {
        mutableStateListOf<String>().apply { addAll(loadedSetups.map { it.name }.sorted()) }
    }

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
    var secondaryDisplayValue by remember { mutableDoubleStateOf(0.0) }
    var pausedTimeLeftInCurrentPhaseMs by remember { mutableDoubleStateOf(0.0) }
    var pausedCurrentSetsRemaining by remember { mutableDoubleStateOf(0.0) }
    val workoutCompleted = remember { mutableStateOf(false) }

    // Automatically load setups from the private file when the app starts
    LaunchedEffect(Unit) {
        val setups = mainActivity.loadFromPrivateFile()
        viewModel.updateSetups(setups)

        // --- ADD THIS LOGIC ---
        // After loading, also populate the UI with the first setup if it exists.
        setups.firstOrNull()?.let { setup ->
            val config = setup.config
            exerciseTime = config.exerciseTime
            restTime = config.restTime
            sets = config.sets
            totalTime = config.totalTime
            exerciseTextFieldValue = TextFieldValue(formatForTextField(exerciseTime))
            restTextFieldValue = TextFieldValue(formatForTextField(restTime))
            setsTextFieldValue = TextFieldValue(formatForTextField(sets, isInteger = true))
            totalTimeTextFieldValue = TextFieldValue(formatForTextField(totalTime))
            selectedSetup = setup.name
        }
    }

    // --- Sound Pool ---
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(2).build().also {
            AppSoundIds.EXERCISE_START_SOUND_ID = it.load(context, R.raw.exercise_start, 1)
            AppSoundIds.EXERCISE_REST_SOUND_ID = it.load(context, R.raw.exercise_rest, 1)
            AppSoundIds.EXERCISE_COMPLETE_SOUND_ID = it.load(context, R.raw.exercise_complete, 1)
        }
    }
    val activeStreamIds = remember { mutableStateListOf<Int>() }

      // --- Input Validation ---
    fun validateAndSave(textValue: String, allowZero: Boolean, onError: (String?) -> Unit, onSuccess: (Double) -> Unit) {
        if (textValue.isBlank()) {
            if (!allowZero) onError("Cannot be empty") else { onError(null); onSuccess(0.0) }
            return
        }
        val parsedValue = textValue.toDoubleOrNull()
        if (parsedValue == null) onError("Invalid")
        else if (!allowZero && parsedValue == 0.0) onError("Not 0")
        else { onError(null); onSuccess(parsedValue) }
    }

    // --- Timer Controls ---
    val onPlayPauseClick = {
        if (isRunning) {
            timerJob?.cancel()
            isPaused = true
            isRunning = false
            pausedTimeLeftInCurrentPhaseMs = timeLeftInCurrentPhaseMs
            pausedCurrentSetsRemaining = secondaryDisplayValue
        } else {
            val useSetsMode = sets > 0
            val isResumingFromPause = isPaused
            timerJob = coroutineScope.launch {
                isRunning = true
                if (!isResumingFromPause) {
                    timeLeftInCurrentPhaseMs = exerciseTime * 1000
                    secondaryDisplayValue = if (useSetsMode) sets else totalTime * 1000
                }
                val initialTime = if (isResumingFromPause) pausedTimeLeftInCurrentPhaseMs else exerciseTime * 1000
                val initialIsExercise = true
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

    val onStopClick = {
        timerJob?.cancel()
        isRunning = false
        isPaused = false
        timeLeftInCurrentPhaseMs = 0.0
        secondaryDisplayValue = 0.0
        status = "Ready"
        workoutCompleted.value = false
        focusManager.clearFocus()
        editor.putString("exercise_time", exerciseTime.toString())
        editor.putString("rest_time", restTime.toString())
        editor.putString("sets", sets.toString())
        editor.putString("total_time", totalTime.toString())
        editor.apply()
    }

    // --- UI Layout ---
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Text(text = status, fontSize = 24.sp, style = MaterialTheme.typography.headlineMedium)
            if (isRunning || isPaused) {
                val secondaryText = if (sets > 0) "Set: ${secondaryDisplayValue.toInt()}" else "Total Time: ${formatTime(secondaryDisplayValue)}"
                Text(text = secondaryText, fontSize = 20.sp, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = formatTime(timeLeftInCurrentPhaseMs), fontSize = 72.sp, style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TimerInputColumn(label = "Exercise Time", value = exerciseTextFieldValue, onValueChange = {
                    exerciseTextFieldValue = it
                    validateAndSave(it.text, false, { e -> exerciseError = e }) { v -> exerciseTime = v }
                }, isError = exerciseError != null, errorMessage = exerciseError, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TimerInputColumn(label = "Rest Time", value = restTextFieldValue, onValueChange = {
                    restTextFieldValue = it
                    validateAndSave(it.text, true, { e -> restError = e }) { v -> restTime = v }
                }, isError = restError != null, errorMessage = restError, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TimerInputColumn(label = "Sets", value = setsTextFieldValue, onValueChange = {
                    setsTextFieldValue = it
                    validateAndSave(it.text, true, { e -> setsError = e }) { v -> sets = v }
                }, isError = setsError != null, errorMessage = setsError, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TimerInputColumn(label = "Total Time", value = totalTimeTextFieldValue, onValueChange = {
                    totalTimeTextFieldValue = it
                    validateAndSave(it.text, true, { e -> totalTimeError = e }) { v -> totalTime = v }
                }, isError = totalTimeError != null, errorMessage = totalTimeError, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onPlayPauseClick, colors = if (isRunning && !isPaused) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9))) {
                    Icon(imageVector = if (isRunning && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isRunning && !isPaused) "Pause" else "Play")
                }
                Button(onClick = onStopClick, enabled = isRunning || isPaused, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF9A9A))) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
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
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    setupNames.forEach { name ->
                        DropdownMenuItem(text = { Text(name) },
                            onClick = {
                                val timerSetup = loadedSetups.firstOrNull { it.name == name }
                                timerSetup?.let {
                                    val config = it.config
                                    exerciseTime = config.exerciseTime
                                    restTime = config.restTime
                                    sets = config.sets
                                    totalTime = config.totalTime
                                    exerciseTextFieldValue = TextFieldValue(formatForTextField(exerciseTime))
                                    restTextFieldValue = TextFieldValue(formatForTextField(restTime))
                                    setsTextFieldValue = TextFieldValue(formatForTextField(sets, isInteger = true))
                                    totalTimeTextFieldValue = TextFieldValue(formatForTextField(totalTime))
                                    exerciseError = null; restError = null; setsError = null; totalTimeError = null
                                    selectedSetup = name
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = newSetupName,
                onValueChange = { newSetupName = it },
                label = { Text("New Setup Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- SAVE BUTTON ---
                Button(
                    // Inside the "Save" button's onClick
                    onClick = {
                        if (newSetupName.text.isNotBlank()) {
                            val currentConfig = SetupConfig(exerciseTime, restTime, sets, totalTime, 0.0)
                            val newTimerSetup = TimerSetup(newSetupName.text, currentConfig)

                            val updatedSetups = loadedSetups.toMutableList().apply {
                                removeAll { it.name == newTimerSetup.name }
                                add(newTimerSetup)
                            }
                            // Silently save to private storage
                            mainActivity.saveToPrivateFile(updatedSetups)
                            // Update the UI
                            viewModel.updateSetups(updatedSetups)

                            newSetupName = TextFieldValue("")
                            focusManager.clearFocus()
                        }
                    },

                    enabled = newSetupName.text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9))
                ) {
                    Text("Save")
                }

                // --- DELETE BUTTON ---
                Button(
                    // Inside the "Delete" button's onClick
                    onClick = {
                        selectedSetup?.let { nameToDelete ->
                            val updatedSetups = loadedSetups.filter { it.name != nameToDelete }
                            mainActivity.saveToPrivateFile(updatedSetups)
                            viewModel.updateSetups(updatedSetups)
                        }
                    },

                    enabled = selectedSetup != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF9A9A))
                ) {
                    Text("Delete")
                }

                // --- SETUP BUTTON ---
                Button(onClick = _onNavigateToSetup) {
                    Text("Setup")
                }
            }
        }
    }
}


// --- HELPER COMPOSABLE FOR INPUTS ---
@Composable
fun TimerInputColumn(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
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
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

// --- PREVIEW ---
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    // We can't easily preview the full app with the viewModel, so we provide a dummy one
    AppNavigation(viewModel = TimerViewModel())
}
