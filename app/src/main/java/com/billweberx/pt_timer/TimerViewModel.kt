// In TimerViewModel.kt
// DELETE EVERYTHING in your current file and REPLACE it with this complete version.

package com.billweberx.pt_timer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billweberx.pt_timer.data.SetupConfig
import com.billweberx.pt_timer.data.TimerScreenState
import com.billweberx.pt_timer.data.TimerSetup
import com.billweberx.pt_timer.ui.activity.MainActivity
import com.billweberx.pt_timer.util.AppSoundPlayer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("PTTimerState", Application.MODE_PRIVATE)
    private val setupsFilename = "setups.json"
    private val gson = Gson()
    private var timerJob: Job? = null

    // --- State Management ---
    private val _setups = MutableStateFlow<List<TimerSetup>>(emptyList())
    val loadedSetups = _setups.asStateFlow()
    private var setMasterClock = 0L
    private val _timerScreenState = MutableStateFlow(TimerScreenState())
    val timerScreenState = _timerScreenState.asStateFlow()

    // --- UI Properties ---
    var moveToTime by mutableStateOf("5")
    var exerciseTime by mutableStateOf("30")
    var moveFromTime by mutableStateOf("0")
    var restTime by mutableStateOf("10")
    var reps by mutableStateOf("1")
    var sets by mutableStateOf("1")
    var setRestTime by mutableStateOf("60")
    var totalTime by mutableStateOf("0")
    var soundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    val defaultSound: SoundOption get() = soundOptions.firstOrNull { it.resourceId == -1 } ?: SoundOption("None", -1)
    var selectedStartRepSound by mutableStateOf(defaultSound)
    var selectedStartRestSound by mutableStateOf(defaultSound)
    var selectedStartSetRestSound by mutableStateOf(defaultSound)
    var selectedCompleteSound by mutableStateOf(defaultSound)
    var activeSetupName by mutableStateOf<String?>(null)
    var activeSetup by mutableStateOf<TimerSetup?>(null)
    var isPaused by mutableStateOf(false)
        private set

    // The current state of our machine. Starts in 'Ready'.
    private var currentState: TimerState = TimerState.Ready// Holds the state right before we paused, so we can resume correctly.
    private var stateBeforePause: TimerState? = null

    // These will track our progress through the workout
    private var currentRepNumber = 1
    private var currentSetNumber = 1

    init {
        initializeSounds()
        loadSetupsFromFile()
        val lastActiveSetupName = prefs.getString(MainActivity.KEY_ACTIVE_SETUP_NAME, null)
        if (lastActiveSetupName != null) {
            _setups.value.find { it.name.equals(lastActiveSetupName, ignoreCase = true) }?.let { setup ->
                applySetup(setup)
            } ?: loadStateFromPrefs()
        } else {
            loadStateFromPrefs()
        }
    }

    fun addOrUpdateSetup(
        name: String,
        moveToTime: String,
        exerciseTime: String,
        moveFromTime: String,
        restTime: String,
        sets: String,
        setRestTime: String,
        reps: String
    ) {
        if (name.isBlank()) return // <-- FIX: Use 'name' parameter

        val config = SetupConfig(
            moveToTime = moveToTime,
            exerciseTime = exerciseTime,
            moveFromTime = moveFromTime,
            restTime = restTime,
            reps = reps,
            sets = sets,
            setRestTime = setRestTime,
            totalTime = this.totalTime // Assuming totalTime is calculated elsewhere or not passed in
        )

        val newOrUpdatedSetup = TimerSetup(
            name = name, // <-- FIX: Use 'name' parameter
            config = config,
            startRepSoundId = selectedStartRepSound.resourceId,
            startRestSoundId = selectedStartRestSound.resourceId,
            startSetRestSoundId = selectedStartSetRestSound.resourceId,
            completeSoundId = selectedCompleteSound.resourceId
        )

        val currentList = _setups.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.name.equals(
                name,
                ignoreCase = true
            )
        } // <-- FIX: Use 'name' parameter

        if (existingIndex != -1) {
            currentList[existingIndex] = newOrUpdatedSetup
        } else {
            currentList.add(newOrUpdatedSetup)
        }
        _setups.value = currentList
        saveSetupsToFile()
    }


    fun moveSetupUp(setupToMove: TimerSetup) {
        val currentList = _setups.value.toMutableList()
        val currentIndex = currentList.indexOf(setupToMove)

        // If the item is found and is not already at the top, move it up.
        if (currentIndex > 0) {
            val item = currentList.removeAt(currentIndex)
            currentList.add(currentIndex - 1, item)
            _setups.value = currentList
            saveSetupsToFile() // Save the new order
        }
    }

    fun moveSetupDown(setupToMove: TimerSetup) {
        val currentList = _setups.value.toMutableList()
        val currentIndex = currentList.indexOf(setupToMove)

        // If the item is found and is not already at the bottom, move it down.
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            val item = currentList.removeAt(currentIndex)
            currentList.add(currentIndex + 1, item)
            _setups.value = currentList
            saveSetupsToFile() // Save the new order
        }
    }
    fun deleteSetup(setupName: String) {
        _setups.value = _setups.value.filter { !it.name.equals(setupName, ignoreCase = true) }
        if (activeSetupName.equals(setupName, ignoreCase = true)) {
            activeSetupName = null
            activeSetup = null
            loadStateFromPrefs()
        }
        saveSetupsToFile()
    }

    fun clearAllSetups() {
        _setups.value = emptyList()
        activeSetupName = null
        activeSetup = null
        saveSetupsToFile()
        loadStateFromPrefs()
    }

    // --- File I/O (Using FLAT TimerSetup) ---
    fun importSetupsFromJson(json: String) {
        try {
            val type = object : TypeToken<List<TimerSetup>>() {}.type
            val importedSetups: List<TimerSetup> = gson.fromJson(json, type) ?: emptyList()
            if (importedSetups.isNotEmpty()) {
                _setups.value = importedSetups
                saveSetupsToFile()
                applySetup(importedSetups.first())
            }
        } catch (e: JsonSyntaxException) {
            // Catch ANY exception, not just JsonSyntaxException
            // CRITICAL: Log the exception message AND its root cause.
            // The 'cause' is often where the real, specific error is hidden.
            Log.e("GSON_FAILURE", "----------------- GSON PARSING FAILED -----------------")
            Log.e("GSON_FAILURE", "Exception Type: ${e.javaClass.simpleName}")
            Log.e("GSON_FAILURE", "Message: ${e.message}")
            Log.e("GSON_FAILURE", "Cause: ${e.cause?.message ?: "No specific cause"}")
            Log.e("GSON_FAILURE", "Full Stack Trace:", e)
            Log.e("GSON_FAILURE", "-------------------------------------------------------")
        }
    }

    private fun loadSetupsFromFile() {
        try {
            File(getApplication<Application>().filesDir, setupsFilename).takeIf { it.exists() }?.let {
                val json = it.readText()
                val type = object : TypeToken<List<TimerSetup>>() {}.type
                _setups.value = gson.fromJson(json, type) ?: emptyList()
            }
        } catch (_: Exception) {
            _setups.value = emptyList()
        }
    }

    private fun saveSetupsToFile() {
        try {
            val jsonSetups = GsonBuilder().setPrettyPrinting().create().toJson(_setups.value)
            File(getApplication<Application>().filesDir, setupsFilename).writeText(jsonSetups)
        } catch (e: Exception) {
            Log.e("SaveToFile", "Error writing setups to file", e)
        }
    }

    fun saveSetupsToUri(context: Context, uri: Uri) {
        try {
            val jsonSetups = GsonBuilder().setPrettyPrinting().create().toJson(_setups.value)
            context.contentResolver.openOutputStream(uri)?.use { it.write(jsonSetups.toByteArray()) }
        } catch (e: Exception) {
            Log.e("SaveToUri", "Failed to write setups to URI: $uri", e)
        }
    }

    fun applySetup(setup: TimerSetup) {
        // ADD ".config" back to every line that needs it.
        moveToTime = setup.config.moveToTime
        exerciseTime = setup.config.exerciseTime
        moveFromTime = setup.config.moveFromTime
        restTime = setup.config.restTime
        reps = setup.config.reps
        sets = setup.config.sets
        setRestTime = setup.config.setRestTime
        totalTime = setup.config.totalTime

        // These lines were already correct and do not need .config
        selectedStartRepSound = soundOptions.find { it.resourceId == setup.startRepSoundId } ?: defaultSound
        selectedStartRestSound = soundOptions.find { it.resourceId == setup.startRestSoundId } ?: defaultSound
        selectedStartSetRestSound = soundOptions.find { it.resourceId == setup.startSetRestSoundId } ?: defaultSound
        selectedCompleteSound = soundOptions.find { it.resourceId == setup.completeSoundId } ?: defaultSound
        activeSetupName = setup.name
        activeSetup = setup
    }

    // --- Timer Functions ---
    fun startTimer() {
        if (timerJob?.isActive == true) return
        isPaused = false
        // Reset progress counters for a fresh start
        currentRepNumber = 1
        currentSetNumber = 1

        // Initialize the master clock if in Total Time mode
        val reps = this.reps.toIntOrNull() ?: 1
        val totalTime = this.totalTime.toLongOrNull() ?: 0L
        setMasterClock = if (reps <= 0 && totalTime > 0) totalTime else 0

        // Determine the very first state based on mode
        currentState = determineInitialState()

        // Launch the state machine
        timerJob = viewModelScope.launch {
            runStateMachine()
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        isPaused = false
        currentState = TimerState.Ready
        // Reset the UI to its default state
        _timerScreenState.update { TimerScreenState() }
    }

    fun pauseTimer() {
        if (timerJob?.isActive != true || isPaused) return

        isPaused = true
        // Save the current state *with* its remaining time from the UI
        stateBeforePause = when (val state = currentState) {
            is TimerState.Exercising -> state.copy(remainingDuration = _timerScreenState.value.remainingTime)
            is TimerState.Resting -> state.copy(remainingDuration = _timerScreenState.value.remainingTime)
            is TimerState.SetResting -> state.copy(remainingDuration = _timerScreenState.value.remainingTime)
            else -> state // For non-timed states like Ready, Finished
        }
        currentState = TimerState.Paused

        timerJob?.cancel() // Cancel the job AFTER saving the state
        timerJob = null

        // Update the UI last
        _timerScreenState.update { it.copy(status = "Paused") }
    }

    fun resumeTimer() {
        if (!isPaused || timerJob?.isActive == true) return

        isPaused = false
        // Restore the state from before we paused. If null, something went wrong, so start fresh.
        currentState = stateBeforePause ?: determineInitialState()
        stateBeforePause = null

        // Launch a new job that will pick up from the restored state
        timerJob = viewModelScope.launch {
            runStateMachine(isResuming = true)
        }
    }

    // This is the state machine runner
    private suspend fun runStateMachine(isResuming: Boolean = false) {
        var resuming = isResuming
        // The loop continues as long as we're in an active state
        while (coroutineContext.isActive && currentState !is TimerState.Finished && currentState !is TimerState.Ready && currentState !is TimerState.Paused) {

            // The 'when' block is the heart of the state machine.
            when (val state = currentState) {
                is TimerState.Exercising -> {
                    _timerScreenState.update {
                        it.copy(
                            status = "Exercise!",
                            currentRep = currentRepNumber,
                            currentSet = currentSetNumber
                        )
                    }
                    // ONLY play sound if we are starting from the beginning of this state
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(getApplication(), selectedStartRepSound.resourceId)                        }
                    countdown(state.remainingDuration)
                    currentState = determineNextStateAfterExercise()
                }

                is TimerState.Resting -> {
                    _timerScreenState.update { it.copy(status = "Rest") }
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(getApplication(), selectedStartRestSound.resourceId)
                    }
                    countdown(state.remainingDuration)
                    currentState = determineNextStateAfterRest()
                }

                is TimerState.SetResting -> {
                    _timerScreenState.update { it.copy(status = "Set Rest") }
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(getApplication(), selectedStartSetRestSound.resourceId)
                    }
                    countdown(state.remainingDuration)
                    currentState = determineNextStateAfterSetRest()
                }
                // Paused, Ready, and Finished states will cause the while loop to terminate.
                else -> break
            }
            resuming = false
        }

    // If the loop finishes because the state became Finished, handle completion.
    if (currentState is TimerState.Finished) {
        _timerScreenState.update { it.copy(status = "Finished!", remainingTime = 0, progressDisplay = "") }
        AppSoundPlayer.playSound(getApplication(), selectedCompleteSound.resourceId)
        delay(100)
        stopTimer()
    }
}
    // A simple, reusable, cancellable countdown function
    private suspend fun countdown(seconds: Int) {
        val isInTotalTimeMode = setMasterClock > 0

        for (t in seconds downTo 1) {
            if (!coroutineContext.isActive) return // Exit immediately if cancelled

            // Decrement the master clock if we are in total time mode
            if (isInTotalTimeMode) {
                setMasterClock--
                _timerScreenState.update {
                    it.copy(
                        remainingTime = t,
                        progressDisplay = "Time: $setMasterClock sec"
                    )
                }
            } else {
                _timerScreenState.update { it.copy(remainingTime = t, progressDisplay = "") }
            }
            delay(1000)

            // If the master clock hits zero, break the current phase early
            if (isInTotalTimeMode && setMasterClock <= 0) {
                break
            }
        }
    }
        // --- State Transition Logic ---
        private fun determineInitialState(): TimerState {
            // In both modes, the first state is always Exercising
            val exerciseSec = (this.exerciseTime.toLongOrNull() ?: 0L).toInt()
            val moveToSec = (this.moveToTime.toLongOrNull() ?: 0L).toInt()
            val fullExerciseDuration = exerciseSec + moveToSec
            return TimerState.Exercising(fullExerciseDuration, fullExerciseDuration)
        }

    private fun determineNextStateAfterExercise(): TimerState {
        val totalReps = this.reps.toIntOrNull() ?: 1
        val hasReps = totalReps > 0
        val totalSets = this.sets.toIntOrNull() ?: 1
        val setRestSec = (this.setRestTime.toLongOrNull() ?: 0L).toInt()

        // --- Reps Mode Logic ---
        // This logic now runs for BOTH modes, but the `currentRepNumber` only matters in Reps mode.
        if (hasReps && currentRepNumber < totalReps) {
            // If we have reps and haven't finished them all, go to the next rep's rest.
            currentRepNumber++
            val restSec = (this.restTime.toLongOrNull() ?: 0L).toInt()
            val moveFromSec = (this.moveFromTime.toLongOrNull() ?: 0L).toInt()
            val fullRestDuration = restSec + moveFromSec
            return TimerState.Resting(fullRestDuration, fullRestDuration)
        }

        // --- Total Time Mode Logic ---
        // If we're in total time mode and time still remains, just go to rest. The rep counter doesn't matter.
        if (!hasReps && setMasterClock > 0) {
            val restSec = (this.restTime.toLongOrNull() ?: 0L).toInt()
            val moveFromSec = (this.moveFromTime.toLongOrNull() ?: 0L).toInt()
            val fullRestDuration = restSec + moveFromSec
            return TimerState.Resting(fullRestDuration, fullRestDuration)
        }

        // --- End of Reps/Time or End of Set Logic ---
        // If we reach here, it means the reps are done OR total time has expired. Time to check for the next set.
        if (currentSetNumber < totalSets) {
            // Go to the next set's rest
            currentRepNumber = 1 // Reset reps for the new set
            currentSetNumber++
            return TimerState.SetResting(setRestSec, setRestSec)
        } else {
            // Workout is complete
            return TimerState.Finished
        }
    }
    private fun determineNextStateAfterRest(): TimerState {
        val totalReps = this.reps.toIntOrNull() ?: 1
        val isInTotalTimeMode = totalReps <= 0

        // In TOTAL TIME mode, we check if the master clock has run out.
        if (isInTotalTimeMode && setMasterClock <= 0) {
            // Time is up, so skip the next exercise and go straight to the end-of-set logic.
            return determineNextStateAfterExercise()
        }

        // In REPS mode, OR if time remains in Total Time mode, just start the next exercise phase.
        val exerciseSec = (this.exerciseTime.toLongOrNull() ?: 0L).toInt()
        val moveToSec = (this.moveToTime.toLongOrNull() ?: 0L).toInt()
        val fullExerciseDuration = exerciseSec + moveToSec
        return TimerState.Exercising(fullExerciseDuration, fullExerciseDuration)
    }
        private fun determineNextStateAfterSetRest(): TimerState {
            // After a set rest, we start a new set.
            val reps = this.reps.toIntOrNull() ?: 1
            val totalTime = this.totalTime.toLongOrNull() ?: 0L

            // Re-initialize the master clock for the new set
            setMasterClock = if (reps <= 0 && totalTime > 0) totalTime else 0

            // Always go back to exercising for the new set
            val exerciseSec = (this.exerciseTime.toLongOrNull() ?: 0L).toInt()
            val moveToSec = (this.moveToTime.toLongOrNull() ?: 0L).toInt()
            val fullExerciseDuration = exerciseSec + moveToSec
            return TimerState.Exercising(fullExerciseDuration, fullExerciseDuration)
        }

    // --- Preference and Sound Initialization ---
    fun saveStateToPrefs() {
        prefs.edit {
            putString(MainActivity.KEY_MOVE_TO_TIME, moveToTime)
            putString(MainActivity.KEY_EXERCISE_TIME, exerciseTime)
            putString(MainActivity.KEY_MOVE_FROM_TIME, moveFromTime)
            putString(MainActivity.KEY_REST_TIME, restTime)
            putString(MainActivity.KEY_REPS, reps)
            putString(MainActivity.KEY_SETS, sets)
            putString(MainActivity.KEY_SET_REST_TIME, setRestTime)
            putString(MainActivity.KEY_TOTAL_TIME, totalTime)
            putString(MainActivity.KEY_ACTIVE_SETUP_NAME, activeSetupName)
            putInt(MainActivity.KEY_START_REP_SOUND_ID, selectedStartRepSound.resourceId)
            putInt(MainActivity.KEY_START_REST_SOUND_ID, selectedStartRestSound.resourceId)
            putInt(MainActivity.KEY_START_SET_REST_SOUND_ID, selectedStartSetRestSound.resourceId)
            putInt(MainActivity.KEY_COMPLETE_SOUND_ID, selectedCompleteSound.resourceId)
        }
    }

    private fun initializeSounds() {
        val allSounds = mutableListOf<SoundOption>()
        allSounds.add(SoundOption("None", -1))
        R.raw::class.java.fields.forEach { field ->
            try {
                if (field.name.startsWith("$")) return@forEach
                val resourceId = field.getInt(null)
                val displayName = field.name.replace('_', ' ').replaceFirstChar { it.titlecase() }
                allSounds.add(SoundOption(displayName, resourceId))
            } catch (_: Exception) {}
        }
        soundOptions = allSounds.sortedBy { it.displayName }
    }

    private fun loadStateFromPrefs() {
        moveToTime = prefs.getString(MainActivity.KEY_MOVE_TO_TIME, "5") ?: "5"
        exerciseTime = prefs.getString(MainActivity.KEY_EXERCISE_TIME, "30") ?: "30"
        moveFromTime = prefs.getString(MainActivity.KEY_MOVE_FROM_TIME, "0") ?: "0"
        restTime = prefs.getString(MainActivity.KEY_REST_TIME, "10") ?: "10"
        reps = prefs.getString(MainActivity.KEY_REPS, "1") ?: "1"
        sets = prefs.getString(MainActivity.KEY_SETS, "1") ?: "1"
        setRestTime = prefs.getString(MainActivity.KEY_SET_REST_TIME, "60") ?: "60"
        totalTime = prefs.getString(MainActivity.KEY_TOTAL_TIME, "0") ?: "0"
        selectedStartRepSound = soundOptions.find { it.resourceId == prefs.getInt(MainActivity.KEY_START_REP_SOUND_ID, defaultSound.resourceId) } ?: defaultSound
        selectedStartRestSound = soundOptions.find { it.resourceId == prefs.getInt(MainActivity.KEY_START_REST_SOUND_ID, defaultSound.resourceId) } ?: defaultSound
        selectedStartSetRestSound = soundOptions.find { it.resourceId == prefs.getInt(MainActivity.KEY_START_SET_REST_SOUND_ID, defaultSound.resourceId) } ?: defaultSound
        selectedCompleteSound = soundOptions.find { it.resourceId == prefs.getInt(MainActivity.KEY_COMPLETE_SOUND_ID, defaultSound.resourceId) } ?: defaultSound
    }
}

sealed class TimerState {
    // States that have a duration and a concept of progress
    data class Exercising(val totalDuration: Int, val remainingDuration: Int) : TimerState()
    data class Resting(val totalDuration: Int, val remainingDuration: Int) : TimerState()
    data class SetResting(val totalDuration: Int, val remainingDuration: Int) : TimerState()

    // States that are points in time or represent a status
    data object Paused : TimerState()
    data object Finished : TimerState()
    data object Ready : TimerState() // The initial state before starting
}


