package com.billweberx.pt_timer

import android.app.Application
import android.media.SoundPool
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

@Composable
fun PTTimerScreen(
    viewModel: TimerViewModel, onGoToSettings: () -> Unit,
    onSaveSetup: (TimerSetup) -> Unit,
    onDeleteSetup: (String) -> Unit,
) {
    // --- State for UI ---
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Ready") }
    var setsRemaining by rememberSaveable { mutableIntStateOf(0) }
    var secondaryDisplay by rememberSaveable { mutableDoubleStateOf(0.0) }
    val workoutCompleted = remember { mutableStateOf(false) }



    // --- Coroutine and Sound state ---
    val coroutineScope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var soundPool by remember { mutableStateOf<SoundPool?>(null) }
    val activeStreamIds = remember { mutableStateListOf<Int>() }
    val soundIdMap = remember { mutableStateMapOf<Int, Int>() }

    // --- Error State ---
    var exerciseTimeError by remember { mutableStateOf<String?>(null) }
    var restTimeError by remember { mutableStateOf<String?>(null) }
    var setsError by remember { mutableStateOf<String?>(null) }
    var totalTimeError by remember { mutableStateOf<String?>(null) }
    var delayTimeError by remember { mutableStateOf<String?>(null) }

    // --- SETUP SAVING/LOADING STATE ---
    var newSetupName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue()
        )
    }
    var selectedSetupName by rememberSaveable { mutableStateOf("None") }

    // --- LOADED SETUPS AND SOUNDS (from ViewModel) ---
    val loadedSetups = viewModel.loadedSetups
    val startSoundOptions = viewModel.startSoundOptions
    val restSoundOptions = viewModel.restSoundOptions
    val completeSoundOptions = viewModel.completeSoundOptions
    var selectedStartSound by viewModel::selectedStartSound
    var selectedRestSound by viewModel::selectedRestSound
    var selectedCompleteSound by viewModel::selectedCompleteSound
    // Load sounds into SoundPool and map their IDs
    LaunchedEffect(startSoundOptions, restSoundOptions, completeSoundOptions) {
        Log.d("SoundDebug", "LaunchedEffect triggered. Reloading sounds.")

        // 1. Release the OLD sound pool if it exists
        soundPool?.release()
        soundIdMap.clear()

        // 2. Create a NEW SoundPool instance
        val newSoundPool = SoundPool.Builder().setMaxStreams(3).build()
        soundPool = newSoundPool // Assign the new instance to our state variable

        val context = viewModel.getApplication<Application>().applicationContext
        val allSounds =
            (startSoundOptions + restSoundOptions + completeSoundOptions)
                .distinctBy { it.resourceId }
                .filter { it.resourceId != -1 }
        // 3. Set the load listener on the NEW instance
        newSoundPool.setOnLoadCompleteListener { _, sampleId, status ->
            Log.d("SoundDebug", "Sound loaded. sampleId: $sampleId, status: $status")
        }

        // 4. Load sounds into the NEW instance
        allSounds.forEach { soundInfo ->
            val loadedId = newSoundPool.load(context, soundInfo.resourceId, 1)
            soundIdMap[soundInfo.resourceId] = loadedId
            Log.d("SoundDebug", "Mapping resourceId ${soundInfo.resourceId} to loadedId $loadedId")
        }
    }


    // --- LOGIC ---
    fun onPlayPauseClick() {
        if (isRunning) {
            // --- ACTION: PAUSE ---
            timerJob?.cancel() // Stop the running coroutine
            isPaused = true
            isRunning = false
            status = "Paused"
        } else {
            // --- ACTION: PLAY OR RESUME ---
            val isResuming = isPaused
            // 1. Get current input values, defaulting to 0 if text is invalid
            val exerciseValue = viewModel.exerciseTime.trim().toDoubleOrNull() ?: 0.0
            val totalTimeValue = viewModel.totalTime.trim().toDoubleOrNull() ?: 0.0
            val setsValue = viewModel.sets.trim().toDoubleOrNull()?.toInt() ?: 0

            // 2. Determine the current mode (Sets or Total Time)
            val isSetsMode = if (isResuming) {
                // When resuming, trust the status text
                status.startsWith("Set") || status.startsWith("Rest")
            } else {
                // For a new start, if user entered sets, use sets mode
                setsValue > 0
            }

            // 3. CRITICAL: Check if the timer has valid parameters to start
            val canStart = if (isSetsMode) {
                // Sets mode requires exercise time and a number of sets
                exerciseValue > 0 && (!isResuming || setsRemaining > 0)
            } else {
                // Total Time mode requires exercise time and a total duration
                exerciseValue > 0 && totalTimeValue > 0
            }

            // 4. If parameters are not valid, do nothing and exit
            if (!canStart) {
                return // <--- THIS IS THE FIX. Just a simple return.
            }

            // 5. If validation passes, update state and launch the timer
            isRunning = true
            isPaused = false

            timerJob = coroutineScope.launch {
                // This is the correct place to launch the coroutine for a click event

                // Get sound IDs safely
                val finalStartSoundId = soundIdMap[selectedStartSound.resourceId] ?: -1
                val finalRestSoundId = soundIdMap[selectedRestSound.resourceId] ?: -1
                val finalCompleteSoundId = soundIdMap[selectedCompleteSound.resourceId] ?: -1

                // Determine the starting duration (resume from where we left off, or start fresh)
                val exerciseDuration = if (isResuming && secondaryDisplay > 0) {
                    secondaryDisplay
                } else {
                    (viewModel.delayTime.trim().toDoubleOrNull() ?: 0.0) * 1000 + (exerciseValue * 1000)
                }


                timerCoroutine(
                    isSetsMode = isSetsMode,
                    exerciseDurationMs = exerciseDuration,
                    restDurationMs = (viewModel.restTime.trim().toDoubleOrNull() ?: 0.0) * 1000,
                    targetTotalWorkoutTimeMs = totalTimeValue * 60 * 1000,
                    workoutCompleted = workoutCompleted,
                    isResuming = isResuming,
                    initialSetsRemaining = if (isResuming) setsRemaining else setsValue,
                    playSoundAction = { soundId ->
                        soundPool?.let { sp ->
                            playSound(sp, activeStreamIds, soundId)
                        }
                    },
                    startSoundId = finalStartSoundId,
                    restSoundId = finalRestSoundId,
                    completeSoundId = finalCompleteSoundId,
                    onUpdate = { isTimerInSetsMode, timeLeftInPhase, statusText, secondaryVal ->
                        status = statusText
                        // The main display should ALWAYS show the time left in the current phase.
                        secondaryDisplay = timeLeftInPhase

                        // The secondary value is ONLY used to update the remaining sets count in sets mode.
                        if (isTimerInSetsMode) {
                            setsRemaining = secondaryVal.toInt()
                        }
                    },

                    onComplete = { finalStatus ->
                        status = finalStatus
                        isRunning = false
                        isPaused = false
                    }
                )
            }
        }
    }



    val onStopClick = {
        timerJob?.cancel()
        isRunning = false
        isPaused = false
        status = "Ready"
        secondaryDisplay = 0.0
        setsRemaining = 0
        workoutCompleted.value = false
    }

    val onSaveClick: () -> Unit = {
        val setupName = newSetupName.text
        if (setupName.isNotBlank()) {
            val currentConfig = SetupConfig(
                viewModel.exerciseTime,
                viewModel.restTime,
                viewModel.sets,
                viewModel.totalTime,
                viewModel.delayTime
            )
            // *** FIX: Include the currently selected sound IDs when saving ***
            val newTimerSetup = TimerSetup(
                name = setupName,
                config = currentConfig,
                startSoundId = selectedStartSound.resourceId,
                restSoundId = selectedRestSound.resourceId,
                completeSoundId = selectedCompleteSound.resourceId
            )
            onSaveSetup(newTimerSetup)
            newSetupName = TextFieldValue() // Clear input box
        }
    }

    val onDeleteClick: () -> Unit = {
        if (selectedSetupName != "None") {
            onDeleteSetup(selectedSetupName)
            selectedSetupName = "None" // Reset dropdown
            onStopClick() // Reset timers
        }
    }

    // --- MAIN UI LAYOUT ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp), // Main padding for left/right
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: App Title and Settings Gear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "PT Timer",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onGoToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Row 2: Phase Status
        Text(status, style = MaterialTheme.typography.headlineSmall)

        // Row 3: Time Remaining
        Text(
            String.format(java.util.Locale.US, "%.1f", secondaryDisplay / 1000),
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Row 4: Delay, Exercise, Rest
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimerInputColumn(
                header = "Delay time",
                value = viewModel.delayTime,
                onValueChange = {
                    viewModel.delayTime = it
                    viewModel.saveState()
                },
                error = delayTimeError,
                modifier = Modifier.weight(1f)
            )
            TimerInputColumn(
                header = "Exercise time",
                value = viewModel.exerciseTime,
                onValueChange = {
                    viewModel.exerciseTime = it
                    viewModel.saveState()
                },
                error = exerciseTimeError,
                modifier = Modifier.weight(1f)
            )
            TimerInputColumn(
                header = "Rest time",
                value = viewModel.restTime,
                onValueChange = {
                    viewModel.restTime = it
                    viewModel.saveState()
                },
                error = restTimeError,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 5: Sets, Total Time
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimerInputColumn(
                header = "Sets",
                value = viewModel.sets,
                onValueChange = {
                    viewModel.sets = it
                    viewModel.saveState()
                },
                error = setsError,
                modifier = Modifier.weight(1f)
            )
            TimerInputColumn(
                header = "Total Time",
                value = viewModel.totalTime,
                onValueChange = {
                    viewModel.totalTime = it
                    viewModel.saveState()
                },
                error = totalTimeError,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Row 6: Start/Stop Controls
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { onPlayPauseClick() },
                colors = if (isRunning) ButtonDefaults.buttonColors(
                    containerColor = Color(
                        0xFFFFF9C4
                    )
                ) // Light Yellow
                else ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9)) // Light Green
            ) {
                if (isRunning) Icon(Icons.Default.Pause, contentDescription = "Pause")
                else Icon(Icons.Default.PlayArrow, contentDescription = "Start")
            }
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)) // Light Red
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }
        }

        Spacer(modifier = Modifier.weight(1f)) // Pushes content below to the bottom

        // Row 7: Setups Spinner
        SetupLoadDropdown(
            label = "Setups",
            loadedSetups = loadedSetups,
            selectedSetupName = selectedSetupName,
            onSetupSelected = { timerSetup ->
                val config = timerSetup.config
                // --- FIX: Update the ViewModel directly ---
                viewModel.exerciseTime = config.exerciseTime
                viewModel.restTime = config.restTime
                viewModel.sets = config.sets
                viewModel.totalTime = config.totalTime
                viewModel.delayTime = config.delayTime

                // When a setup is loaded, update the selected sounds in the ViewModel
                selectedStartSound =
                    startSoundOptions.find { it.resourceId == timerSetup.startSoundId }
                        ?: viewModel.defaultSound
                selectedRestSound =
                    restSoundOptions.find { it.resourceId == timerSetup.restSoundId }
                        ?: viewModel.defaultSound
                selectedCompleteSound =
                    completeSoundOptions.find { it.resourceId == timerSetup.completeSoundId }
                        ?: viewModel.defaultSound
                selectedSetupName = timerSetup.name

                // Manually trigger a recomposition to ensure UI reflects loaded state
                viewModel.saveState()
            },
            modifier = Modifier.fillMaxWidth()
        )


        Spacer(modifier = Modifier.height(16.dp))

        // Row 8: New Setup Name
        OutlinedTextField(
            value = newSetupName,
            onValueChange = { newSetupName = it },
            label = { Text("New Setup Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Row 9: Save/Delete Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onSaveClick, modifier = Modifier.weight(1f)) { Text("Save") }
            Button(
                onClick = onDeleteClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)), // Light Red
                modifier = Modifier.weight(1f)
            ) { Text("Delete") }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupLoadDropdown(
    label: String,
    loadedSetups: List<TimerSetup>,
    selectedSetupName: String,
    onSetupSelected: (TimerSetup) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSetupName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor() // This is important
                .fillMaxWidth() // *** FIX: Make the text field fill the width ***
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSetupSelected(
                        TimerSetup(
                            "None",
                            SetupConfig("0", "0", "0", "0", "0"),
                            -1,
                            -1,
                            -1
                        )
                    )
                    expanded = false
                }
            )
            loadedSetups.forEach { setup ->
                DropdownMenuItem(
                    text = { Text(setup.name) },
                    onClick = {
                        onSetupSelected(setup)
                        expanded = false
                    }
                )
            }
        }
    }
}


// ... (The rest of the file: SoundDropdown, TimerInputColumn, SetupLoadDropdown, playSound, timerCoroutine remains exactly the same) ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDropdown(
    label: String,
    soundOptions: List<SoundOption>,
    selectedSound: SoundOption,
    onSoundSelected: (SoundOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSound.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            soundOptions.forEach { soundOption ->
                DropdownMenuItem(
                    text = { Text(soundOption.displayName) },
                    onClick = {
                        onSoundSelected(soundOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TimerInputColumn(
    header: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(header) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}


// This is the only playSound function that should be in the file.
private fun playSound(soundPool: SoundPool, activeStreamIds: SnapshotStateList<Int>, soundId: Int) {
    if (soundId > 0) { // SoundPool IDs are typically > 0. -1 is our "not found" value.
        val streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        Log.d(
            "SoundDebug",
            "soundPool.play called for soundId $soundId. Resulting streamId: $streamId"
        ) // <-- LOG 6
        if (streamId != 0) {
            activeStreamIds.add(streamId)
        }
    } else {
        Log.d("SoundDebug", "Skipping playSound because soundId is invalid: $soundId") // <-- LOG 7
    }
}

suspend fun timerCoroutine(
    isSetsMode: Boolean,
    exerciseDurationMs: Double,
    restDurationMs: Double, targetTotalWorkoutTimeMs: Double,
    workoutCompleted: MutableState<Boolean>,
    isResuming: Boolean,
    initialSetsRemaining: Int,
    playSoundAction: (Int) -> Unit,
    startSoundId: Int,
    restSoundId: Int,
    completeSoundId: Int,
    onUpdate: (isTimerInSetsMode: Boolean, timeLeftInPhase: Double, statusText: String, secondaryVal: Double) -> Unit,
    onComplete: (String) -> Unit
) {
    var setsCounter = initialSetsRemaining
    var totalTimeCounter = 0.0

    // --- Start of Main Workout ---
    onUpdate(isSetsMode, exerciseDurationMs, "Exercise", setsCounter.toDouble())
    if (!isResuming) {
        playSoundAction(startSoundId)
    }

    if (isSetsMode) {
        // --- SETS MODE ---
        while (setsCounter > 0) {
            // --- EXERCISE PHASE ---
            var timeLeft = exerciseDurationMs
            while (timeLeft > 0) {
                onUpdate(true, timeLeft, "Exercise", setsCounter.toDouble())
                delay(100)
                timeLeft -= 100
            }

            // --- Check if we are done after the final exercise phase ---
            setsCounter-- // Decrement the set after exercise is complete
            if (setsCounter <= 0) {
                break // Exit loop, do not run final rest phase
            }

            // --- REST PHASE ---
            playSoundAction(restSoundId)
            timeLeft = restDurationMs
            while (timeLeft > 0) {
                onUpdate(true, timeLeft, "Rest", setsCounter.toDouble())
                delay(100)
                timeLeft -= 100
            }

            // --- PREPARE FOR NEXT SET ---
            playSoundAction(startSoundId)
        }
    } else {
        // --- TOTAL TIME MODE ---
        // (This logic is simpler as it has no rest)
        var timeLeftInCycle = exerciseDurationMs
        while (totalTimeCounter < targetTotalWorkoutTimeMs) {
            onUpdate(false, timeLeftInCycle, "Exercise", totalTimeCounter)
            delay(100)
            timeLeftInCycle -= 100
            totalTimeCounter += 100

            if (timeLeftInCycle <= 0 && totalTimeCounter < targetTotalWorkoutTimeMs) {
                // Start next cycle
                timeLeftInCycle = exerciseDurationMs
                playSoundAction(startSoundId)
            }
        }
    }

    // --- COMPLETION ---
    workoutCompleted.value = true
    playSoundAction(completeSoundId)
    val finalMessage = if (isSetsMode) "Finished $initialSetsRemaining sets" else "Finished"
    onComplete(finalMessage)
}


