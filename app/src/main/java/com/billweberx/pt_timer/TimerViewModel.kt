package com.billweberx.pt_timer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billweberx.pt_timer.data.AppState
import com.billweberx.pt_timer.data.SetupConfig
import com.billweberx.pt_timer.data.TimerScreenState
import com.billweberx.pt_timer.data.TimerSetup
import com.billweberx.pt_timer.util.AppSoundPlayer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val appStateFilename = "app_state.json"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var timerJob: Job? = null
    private var countdownJob: Job? = null

    // --- State Management ---
    private val _setups = MutableStateFlow<List<TimerSetup>>(emptyList())
    val loadedSetups = _setups.asStateFlow()
    private var setMasterClock = 0L
    private val _timerScreenState = MutableStateFlow(TimerScreenState())
    val timerScreenState = _timerScreenState.asStateFlow()

    // --- UI Properties ---
    var configState by mutableStateOf(SetupConfig())
    var soundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    val defaultSound: SoundOption
        get() = soundOptions.firstOrNull { it.resourceId == -1 } ?: SoundOption("None", -1)
    var selectedStartRepSound by mutableStateOf(defaultSound)
    var selectedStartRestSound by mutableStateOf(defaultSound)
    var selectedStartSetRestSound by mutableStateOf(defaultSound)
    var selectedCompleteSound by mutableStateOf(defaultSound)
    var activeSetupName by mutableStateOf<String?>("")
    var activeSetup by mutableStateOf<TimerSetup?>(null)

    // Timer State Machine properties
    private var currentState: TimerState = TimerState.Ready
    private var stateBeforePause: TimerState? = null
    private var currentRepNumber = 1
    private var currentSetNumber = 1

    init {
        initializeSounds()
        loadAppState() // Simplified initialization
    }

    private fun loadAppState() {
        val appStateFile = File(getApplication<Application>().filesDir, appStateFilename)
        if (appStateFile.exists() && appStateFile.length() > 0) {
            try {
                val json = appStateFile.readText()
                val appState = gson.fromJson(json, AppState::class.java)

                if (appState != null && appState.allSetups.isNotEmpty()) {
                    _setups.value = appState.allSetups
                    val setupToApply =
                        appState.allSetups.find { it.name == appState.activeSetupName }
                            ?: appState.allSetups.first() // Default to first if name not found
                    applySetup(setupToApply, isInitialLoad = true)
                } else {
                    handleFirstLaunchOrEmptyState(shouldSave = false) // Don't save if file was just empty
                }
            } catch (e: Exception) {
                Log.e("LoadAppState", "Failed to parse app_state.json", e)
                handleFirstLaunchOrEmptyState()
            }
        } else {
            handleFirstLaunchOrEmptyState()
        }
    }

    fun saveAppState() {
        try {
            val currentState = AppState(
                allSetups = _setups.value,
                activeSetupName = this.activeSetup?.name
            )
            val json = gson.toJson(currentState)
            File(getApplication<Application>().filesDir, appStateFilename).writeText(json)
        } catch (e: Exception) {
            Log.e("SaveAppState", "Error writing app state to file", e)
        }
    }

    private fun handleFirstLaunchOrEmptyState(shouldSave: Boolean = true) {
        val initialSetup = TimerSetup(
            name = "Default Workout",
            config = SetupConfig(), // Default values
            startRepSoundId = defaultSound.resourceId,
            startRestSoundId = defaultSound.resourceId,
            startSetRestSoundId = defaultSound.resourceId,
            completeSoundId = defaultSound.resourceId
        )
        _setups.value = listOf(initialSetup)
        applySetup(initialSetup, isInitialLoad = true) // Apply but don't re-save yet
        if (shouldSave) {
            saveAppState() // Save the initial state only if needed
        }
    }

    // --- Setup Management Functions (now use saveAppState) ---

    fun addOrUpdateSetup(name: String) {
        if (name.isBlank()) return

        val newOrUpdatedSetup = TimerSetup(
            name = name,
            config = configState,
            startRepSoundId = selectedStartRepSound.resourceId,
            startRestSoundId = selectedStartRestSound.resourceId,
            startSetRestSoundId = selectedStartSetRestSound.resourceId,
            completeSoundId = selectedCompleteSound.resourceId
        )

        val currentList = _setups.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.equals(name, ignoreCase = true) }

        if (existingIndex != -1) {
            currentList[existingIndex] = newOrUpdatedSetup
        } else {
            currentList.add(newOrUpdatedSetup)
        }
        _setups.value = currentList
        applySetup(newOrUpdatedSetup) // Apply the new/updated setup as the active one
    }

    fun deleteSetup(setupName: String) {
        val currentList = _setups.value.toMutableList()
        currentList.removeAll { it.name.equals(setupName, ignoreCase = true) }

        if (currentList.isEmpty()) {
            _setups.value = emptyList()
            clearAllSetups() // Resets to a default "Unsaved" state and saves
        } else {
            _setups.value = currentList
            if (activeSetupName.equals(setupName, ignoreCase = true)) {
                applySetup(currentList.first()) // Make the first one active and save
            } else {
                saveAppState() // Just save the smaller list
            }
        }
    }

    fun clearAllSetups() {
        _setups.value = emptyList()
        val unsavedDefault = TimerSetup(
            name = "Unsaved Workout",
            config = SetupConfig(),
            startRepSoundId = defaultSound.resourceId,
            startRestSoundId = defaultSound.resourceId,
            startSetRestSoundId = defaultSound.resourceId,
            completeSoundId = defaultSound.resourceId
        )
        applySetup(unsavedDefault, isUnsaved = true) // Apply temp state to UI
        saveAppState() // Persist the now-empty list of setups
    }

    fun applySetup(setup: TimerSetup, isInitialLoad: Boolean = false, isUnsaved: Boolean = false) {
        configState = setup.config
        selectedStartRepSound =
            soundOptions.find { it.resourceId == setup.startRepSoundId } ?: defaultSound
        selectedStartRestSound =
            soundOptions.find { it.resourceId == setup.startRestSoundId } ?: defaultSound
        selectedStartSetRestSound =
            soundOptions.find { it.resourceId == setup.startSetRestSoundId } ?: defaultSound
        selectedCompleteSound =
            soundOptions.find { it.resourceId == setup.completeSoundId } ?: defaultSound
        activeSetupName = setup.name
        activeSetup = setup

        if (!isInitialLoad && !isUnsaved) {
            saveAppState()
        }
    }

    fun moveSetupUp(setupToMove: TimerSetup) {
        val currentList = _setups.value.toMutableList()
        val currentIndex = currentList.indexOf(setupToMove)

        if (currentIndex > 0) {
            currentList.removeAt(currentIndex)
            currentList.add(currentIndex - 1, setupToMove)
            _setups.value = currentList
            saveAppState()
        }
    }

    fun moveSetupDown(setupToMove: TimerSetup) {
        val currentList = _setups.value.toMutableList()
        val currentIndex = currentList.indexOf(setupToMove)

        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            currentList.removeAt(currentIndex)
            currentList.add(currentIndex + 1, setupToMove)
            _setups.value = currentList
            saveAppState()
        }
    }

    // --- Import/Export (now uses the full AppState) ---

// In TimerViewModel.kt

    fun importSetupsFromJson(json: String) {
        try {
            // First, try to parse it as the NEW AppState format
            val appState = gson.fromJson(json, AppState::class.java)
            if (appState != null && appState.allSetups.isNotEmpty()) {
                _setups.value = appState.allSetups
                val setupToApply = appState.allSetups.find { it.name == appState.activeSetupName }
                    ?: appState.allSetups.first()
                applySetup(setupToApply) // Applies and saves
                return // Success, we are done
            }
        } catch (_: Exception) {
            // It failed, so it might be the OLD format. We'll log it and try the old way.
            Log.d("ImportSetups", "Could not parse as AppState, trying legacy format.")
        }

        try {
            // Second, try to parse it as the OLD List<TimerSetup> format
            val setupListType = object : com.google.gson.reflect.TypeToken<List<TimerSetup>>() {}.type
            val legacySetups: List<TimerSetup>? = gson.fromJson(json, setupListType)

            if (!legacySetups.isNullOrEmpty()) {
                _setups.value = legacySetups
                applySetup(legacySetups.first()) // Apply the first one and save the new AppState
            }
        } catch (e: Exception) {
            // If both attempts fail, then the file is truly invalid.
            Log.e("ImportFailure", "Failed to import from JSON as AppState or legacy List", e)
        }
    }


    fun saveSetupsToUri(context: Context, uri: Uri) {
        try {
            val currentState = AppState(
                allSetups = _setups.value,
                activeSetupName = this.activeSetup?.name
            )
            val json = gson.toJson(currentState)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.e("SaveToUri", "Failed to write setups to URI: $uri", e)
        }
    }

    // --- Timer Functions and State Machine ---

    fun startTimer() {
        if (timerJob?.isActive == true) return
        _timerScreenState.update { it.copy(isPaused = false) }
        currentRepNumber = 1
        currentSetNumber = 1

        val reps = this.configState.reps.toIntOrNull() ?: 1
        val totalTime = this.configState.totalTime.toLongOrNull() ?: 0L
        setMasterClock = if (reps <= 0 && totalTime > 0) totalTime else 0

        currentState = determineInitialState()

        timerJob = viewModelScope.launch {
            runStateMachine()
        }
    }

    fun stopTimer() {
        countdownJob?.cancel() // <-- ADD THIS to instantly stop the countdown
        timerJob?.cancel()
        countdownJob = null    // <-- ADD THIS for cleanup
        timerJob = null
        currentState = TimerState.Ready
        _timerScreenState.update { TimerScreenState(isPaused = false) }
    }


    fun pauseTimer() {
        val state = currentState
        // Only pause if we are in a cancellable, running state
        if (countdownJob?.isActive == true && (state is TimerState.ExercisingInProgress || state is TimerState.RestingInProgress || state is TimerState.SetRestingInProgress)) {
            stateBeforePause = state // Save our current "in progress" state
            currentState = TimerState.Paused
            _timerScreenState.update { it.copy(status = "Paused", isPaused = true) }
        }
    }

    fun resumeTimer() {
        if (timerScreenState.value.isPaused) { // Check the state from the flow
            // If we're resuming, we're definitely not paused anymore. Update UI immediately.
            _timerScreenState.update { it.copy(isPaused = false) }

            // This is the state we are trying to restore.
            val stateToRestore = stateBeforePause
            stateBeforePause = null // Clear it immediately

            if (countdownJob?.isActive != true && stateToRestore != null) {
                currentState = when (stateToRestore) {
                    is TimerState.ExercisingInProgress -> determineNextStateAfterExercise()
                    is TimerState.RestingInProgress -> determineNextStateAfterRest()
                    is TimerState.SetRestingInProgress -> determineNextStateAfterSetRest()
                    else -> stateToRestore // Fallback for other states
                }
            } else {
                // The countdown job is still active or we have nothing to restore, so normal resume is fine.
                currentState = stateToRestore ?: return
            }
        }
    }
    private suspend fun runStateMachine(isResuming: Boolean = false) {
        var resuming = isResuming
        while (coroutineContext.isActive) {
            when (val state = currentState) {
                is TimerState.Exercising -> {
                    _timerScreenState.update {
                        it.copy(
                            status = "Exercise!",
                            currentRep = currentRepNumber,
                            currentSet = currentSetNumber,
                            isPaused = false
                        )
                    }
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(getApplication(), selectedStartRepSound.resourceId)
                    }
                    // Launch the countdown as a supervised worker job
                    countdownJob = viewModelScope.launch { countdown(state.remainingDuration) }
                    currentState = TimerState.ExercisingInProgress
                }
                is TimerState.Resting -> {
                    _timerScreenState.update {
                        it.copy(
                            status = "Rest",
                            isPaused = false
                        )
                    }
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(getApplication(), selectedStartRestSound.resourceId)
                    }
                    countdownJob = viewModelScope.launch { countdown(state.remainingDuration) }
                    currentState = TimerState.RestingInProgress
                }
                is TimerState.SetResting -> {
                    _timerScreenState.update {
                        it.copy(
                            status = "Set Rest",
                            isPaused = false
                        )
                    }
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(
                            getApplication(),
                            selectedStartSetRestSound.resourceId
                        )
                    }
                    countdownJob = viewModelScope.launch { countdown(state.remainingDuration) }
                    currentState = TimerState.SetRestingInProgress
                }

                // --- WAITING / IDLE STATES ---
                // These states do nothing but wait for an external event (from the countdown worker or UI)
                // to change the `currentState`.

                is TimerState.ExercisingInProgress,
                is TimerState.RestingInProgress,
                is TimerState.SetRestingInProgress,
                is TimerState.Paused -> {
                    // DO NOTHING. The state machine is supervising.
                    // It's waiting for the countdown worker or a UI event to change `currentState`.
                }
                // --- TERMINATION STATES ---
                is TimerState.Finished -> {
                    _timerScreenState.update { it.copy(status = "Finished!", remainingTime = 0, progressDisplay = "") }
                    AppSoundPlayer.playSound(getApplication(), selectedCompleteSound.resourceId)
                    stopTimer() // This will cancel the parent timerJob and exit the loop
                }
                is TimerState.Ready -> {
                    // The timer has been stopped or reset. Exit the loop.
                    timerJob?.cancel()
                }
            }
            resuming = false
            // This delay is CRITICAL. It prevents the while loop from running at 100% CPU
            // while in an "InProgress" or "Paused" state. It yields the thread.
            delay(50)
        }
    }

    private suspend fun countdown(seconds: Int) {
        var remaining = seconds
        try {
            while (remaining > 0) {
                // This is the cooperative pause loop. While the main state is Paused,
                // this coroutine will wait here without consuming CPU.
                while (currentState is TimerState.Paused) {
                    coroutineContext.ensureActive() // Allow cancellation by stopTimer()
                    delay(100) // Wait patiently
                }

                // If not paused, ensure we can still be cancelled before doing work.
                coroutineContext.ensureActive()

                // Update the UI with the current time remaining.
                val isInTotalTimeMode = setMasterClock > 0
                if (isInTotalTimeMode) {
                    setMasterClock--
                    _timerScreenState.update {
                        it.copy(
                            remainingTime = remaining,
                            progressDisplay = "Time: $setMasterClock sec"
                        )
                    }
                } else {
                    _timerScreenState.update { it.copy(remainingTime = remaining, progressDisplay = "") }
                }

                // Wait for one second.
                delay(1000)
                remaining--

                // If in total time mode, check if the master clock has run out.
                if (isInTotalTimeMode && setMasterClock <= 0) {
                    break // Exit the loop early if the total time is up.
                }
            }

            // SUCCESS: The countdown finished (either by reaching 0 or by the master clock running out).
            // We must check if the coroutine is still active before changing the state.
            if (coroutineContext.isActive) {
                // This check prevents a state change if the user paused at the exact last second.
                if (currentState !is TimerState.Paused) {
                    currentState = when (currentState) {
                        is TimerState.ExercisingInProgress -> determineNextStateAfterExercise()
                        is TimerState.RestingInProgress -> determineNextStateAfterRest()
                        is TimerState.SetRestingInProgress -> determineNextStateAfterSetRest()
                        else -> currentState // Should not happen, but a safe fallback.
                    }
                }
            }
        } catch (_: CancellationException) {
            // This block is entered when countdownJob is cancelled by stopTimer().
            // We do nothing, as this is expected behavior for stopping.
        }
    }



    private fun determineInitialState(): TimerState {
        val exerciseSec = (this.configState.exerciseTime.toLongOrNull() ?: 0L).toInt()
        val moveToSec = (this.configState.moveToTime.toLongOrNull() ?: 0L).toInt()
        val fullExerciseDuration = exerciseSec + moveToSec
        return TimerState.Exercising(fullExerciseDuration, fullExerciseDuration)
    }

    private fun determineNextStateAfterExercise(): TimerState {
        val totalReps = this.configState.reps.toIntOrNull() ?: 1
        val hasReps = totalReps > 0
        val totalSets = this.configState.sets.toIntOrNull() ?: 1
        val setRestSec = (this.configState.setRestTime.toLongOrNull() ?: 0L).toInt()

        if (hasReps && currentRepNumber < totalReps) {
            currentRepNumber++
            val restSec = (this.configState.restTime.toLongOrNull() ?: 0L).toInt()
            val moveFromSec = (this.configState.moveFromTime.toLongOrNull() ?: 0L).toInt()
            val fullRestDuration = restSec + moveFromSec
            return TimerState.Resting(fullRestDuration, fullRestDuration)
        }

        if (!hasReps && setMasterClock > 0) {
            val restSec = (this.configState.restTime.toLongOrNull() ?: 0L).toInt()
            val moveFromSec = (this.configState.moveFromTime.toLongOrNull() ?: 0L).toInt()
            val fullRestDuration = restSec + moveFromSec
            return TimerState.Resting(fullRestDuration, fullRestDuration)
        }

        if (currentSetNumber < totalSets) {
            currentRepNumber = 1
            currentSetNumber++
            return TimerState.SetResting(setRestSec, setRestSec)
        } else {
            return TimerState.Finished
        }
    }

    private fun determineNextStateAfterRest(): TimerState {
        val totalReps = this.configState.reps.toIntOrNull() ?: 1
        val isInTotalTimeMode = totalReps <= 0

        if (isInTotalTimeMode && setMasterClock <= 0) {
            return determineNextStateAfterExercise()
        }

        val exerciseSec = (this.configState.exerciseTime.toLongOrNull() ?: 0L).toInt()
        val moveToSec = (this.configState.moveToTime.toLongOrNull() ?: 0L).toInt()
        val fullExerciseDuration = exerciseSec + moveToSec
        return TimerState.Exercising(fullExerciseDuration, fullExerciseDuration)
    }

    private fun determineNextStateAfterSetRest(): TimerState {
        val reps = this.configState.reps.toIntOrNull() ?: 1
        val totalTime = this.configState.totalTime.toLongOrNull() ?: 0L
        setMasterClock = if (reps <= 0 && totalTime > 0) totalTime else 0

        val exerciseSec = (this.configState.exerciseTime.toLongOrNull() ?: 0L).toInt()
        val moveToSec = (this.configState.moveToTime.toLongOrNull() ?: 0L).toInt()
        val fullExerciseDuration = exerciseSec + moveToSec
        return TimerState.Exercising(fullExerciseDuration, fullExerciseDuration)
    }

    // --- Sound Initialization ---

    private fun initializeSounds() {
        val allSounds = mutableListOf<SoundOption>()
        allSounds.add(SoundOption("None", -1))
        R.raw::class.java.fields.forEach { field ->
            try {
                if (field.name.startsWith("$")) return@forEach
                val resourceId = field.getInt(null)
                val displayName = field.name.replace('_', ' ').replaceFirstChar { it.titlecase() }
                allSounds.add(SoundOption(displayName, resourceId))
            } catch (_: Exception) {
            }
        }
        soundOptions = allSounds.sortedBy { it.displayName }
    }


    sealed class TimerState {
        // States that INITIATE a countdown
        data class Exercising(val totalDuration: Int, val remainingDuration: Int) : TimerState()
        data class Resting(val totalDuration: Int, val remainingDuration: Int) : TimerState()
        data class SetResting(val totalDuration: Int, val remainingDuration: Int) : TimerState()

        // States that REPRESENT an ONGOING countdown
        data object ExercisingInProgress : TimerState()
        data object RestingInProgress : TimerState()
        data object SetRestingInProgress : TimerState()

        // Control states
        data object Paused : TimerState()
        data object Finished : TimerState()
        data object Ready : TimerState()
    }

}
