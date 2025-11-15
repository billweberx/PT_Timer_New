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
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.lang.reflect.Field
import com.billweberx.pt_timer.data.ImageOption
import com.billweberx.pt_timer.data.SpinnerOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext




class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    private val appStateFilename = "app_state.json"
  //  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var timerJob: Job? = null
    private var countdownJob: Job? = null

    // --- State Management ---
    private val _setups = MutableStateFlow<List<TimerSetup>>(emptyList())
    val loadedSetups = _setups.asStateFlow()
    private var setMasterClock = 0L
    private val _timerScreenState = MutableStateFlow(TimerScreenState())
    val timerScreenState = _timerScreenState.asStateFlow()
    var imageOptions by mutableStateOf<List<ImageOption>>(emptyList())
        private set
    var selectedImage by mutableStateOf<ImageOption?>(null)
    val defaultImage: ImageOption
        get() = imageOptions.firstOrNull { it.resourceId == 0 } ?: ImageOption("None", 0)

    // --- UI Properties ---
    var configState by mutableStateOf(SetupConfig())
    var soundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    val defaultSound: SoundOption
        get() = soundOptions.find { it.displayName.equals("None", ignoreCase = true) } ?: SoundOption("None", -1, "none")

    var selectedStartRepSound by mutableStateOf(defaultSound)
    var selectedStartRestSound by mutableStateOf(defaultSound)
    var selectedStartSetRestSound by mutableStateOf(defaultSound)
    var selectedCompleteSound by mutableStateOf(defaultSound)
    var selectedGetReadySound by mutableStateOf(defaultSound)
    var activeSetupName by mutableStateOf<String?>("")
    var activeSetup by mutableStateOf<TimerSetup?>(null)

    // Timer State Machine properties
    private var currentState: TimerState = TimerState.Ready
    private var stateBeforePause: TimerState? = null
    private var currentRepNumber = 1
    private var currentSetNumber = 1

    // band color and weights properties
    var bandColorOptions by mutableStateOf<List<SpinnerOption>>(emptyList())
    var selectedBandColor by mutableStateOf(SpinnerOption("N/A"))
    var weightOptions by mutableStateOf<List<SpinnerOption>>(emptyList())

    var selectedWeight by mutableStateOf(SpinnerOption("N/A"))
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val defaultOption = SpinnerOption("N/A")
    init {
        initializeSounds()
        initializeImages()
        loadAppState() // Simplified initialization
    }
    fun onToastShown() {
        _toastMessage.value = null
    }
    fun onConfigChange(newConfig: SetupConfig) {    // --- Validation Logic ---
        val exerciseTime = newConfig.exerciseTime.toDoubleOrNull() // Check as a Double
        if (exerciseTime != null && exerciseTime <= 0.0) { // Compare to 0.0
            // Invalid value. Post a message to the UI and reject the change.
            _toastMessage.value = "Exercise time must be greater than 0."
            return // Exit the function, preventing the invalid state from being set.
        }

        // --- If validation passes, update the state ---
        configState = newConfig
    }
    fun validateExerciseTime(): Boolean {
        val exerciseTimeValue = configState.exerciseTime.toDoubleOrNull()

        // If the final value is null (e.g., blank, ".") or not positive...
        if (exerciseTimeValue == null || exerciseTimeValue <= 0.0) {
            _toastMessage.value = "Exercise time must be a positive number."
            return false // Validation FAILED
        }
        return true // Validation PASSED
    }
    private fun loadAppState() {
        viewModelScope.launch(Dispatchers.IO) { // Continue to do file I/O on a background thread
            val stateFile = File(getApplication<Application>().filesDir, appStateFilename)
            if (!stateFile.exists()) {            // If file doesn't exist, switch to main thread to set up initial UI state
                withContext(Dispatchers.Main) {
                    handleFirstLaunchOrEmptyState()
                }
                return@launch
            }

            try {
                val json = stateFile.readText()
                val appState = gson.fromJson(json, AppState::class.java)

                // Switch to the Main thread before updating the UI state
                withContext(Dispatchers.Main) {
                    // 1. Load the option lists FIRST.
                    // Convert loaded List<String> back to List<SpinnerOption>
                    bandColorOptions = appState.bandColorOptions.map { SpinnerOption(it) }.ifEmpty { listOf(defaultOption) }
                    weightOptions = appState.weightOptions.map { SpinnerOption(it) }.ifEmpty { listOf(defaultOption) }

                    // 2. Load all the setups.
                    _setups.value = appState.allSetups

                    // 3. NOW, find and apply the active setup. Because we are on the Main thread,
                    //    the UI will recompose reliably.
                    if (appState.allSetups.isEmpty()) {
                        handleFirstLaunchOrEmptyState()
                    } else {
                        val activeSetup = appState.allSetups.find { it.name == appState.activeSetupName }
                            ?: appState.allSetups.first()
                        // The 'isInitialLoad' flag prevents a re-save, which is correct.
                        applySetup(activeSetup, isInitialLoad = true)
                    }
                }
                // --- END of the FIX ---

            } catch (e: Exception) {
                Log.e("LoadAppState", "Exception while reading/parsing app_state.json.", e)
                withContext(Dispatchers.Main) {
                    handleFirstLaunchOrEmptyState(shouldSave = false)
                }
            }
        }
    }

    fun saveAppState() {
        try {
            val activeIndex = _setups.value.indexOfFirst { it.name == activeSetupName }

            // 2. If the active setup is found in the list...
            if (activeIndex != -1) {
                // 3. ...create an updated version of it using the LATEST configState.
                val updatedActiveSetup = _setups.value[activeIndex].copy(config = configState)
                // 4. Create a new list with the updated setup replaced at the correct index.
                _setups.value = _setups.value.toMutableList().apply {
                    set(activeIndex, updatedActiveSetup)
                }.toList()
            }
            val currentState = AppState(
                allSetups = _setups.value,
                activeSetupName = this.activeSetupName,
                // Convert List<SpinnerOption> to List<String> for saving
                bandColorOptions = bandColorOptions.map { it.value },
                weightOptions = weightOptions.map { it.value }
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
            config = SetupConfig(
                instructions = "Welcome! This is a default workout to get you started.",
                imageResId = R.drawable.dowel_assisted_overhead_reach,
                timesPerDay = "1",
                timesPerWeek = "7"
            ), // Default values
            getReadySound = defaultSound.displayName,
            startRepSound = defaultSound.displayName,
            startRestSound = defaultSound.displayName,
            startSetRestSound = defaultSound.displayName,
            completeSound = defaultSound.displayName
        )
        bandColorOptions = listOf(defaultOption)
        weightOptions = listOf(defaultOption)
        _setups.value = listOf(initialSetup)
        applySetup(initialSetup, isInitialLoad = true) // Apply but don't re-save yet
        if (shouldSave) {
            saveAppState() // Save the initial state only if needed
        }
    }

    // --- Setup Management Functions (now use saveAppState) ---

    fun addOrUpdateSetup(name: String) {
        if (name.isBlank()) return
        val newConfig = configState.copy(
            imageResId = selectedImage?.resourceId ?: 0,
            bandColor = selectedBandColor.value,
            weightLbs = selectedWeight.value,
        )
        val newOrUpdatedSetup = TimerSetup(
            name = name,
            config = newConfig,
            getReadySound = selectedGetReadySound.resourceName,
            startRepSound = selectedStartRepSound.resourceName,
            startRestSound = selectedStartRestSound.resourceName,
            startSetRestSound = selectedStartSetRestSound.resourceName,
            completeSound = selectedCompleteSound.resourceName
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
            getReadySound = defaultSound.displayName,
            startRepSound = defaultSound.displayName,
            startRestSound = defaultSound.displayName,
            startSetRestSound = defaultSound.displayName,
            completeSound = defaultSound.displayName,
            config = SetupConfig(
            )
        )
        applySetup(unsavedDefault, isUnsaved = true) // Apply temp state to UI
        selectedImage = defaultImage //  to reset the dropdown UI
        saveAppState() // Persist the now-empty list of setups
    }

    fun applySetup(setup: TimerSetup, isInitialLoad: Boolean = false, isUnsaved: Boolean = false) {
        configState = setup.config
        selectedStartRepSound = soundOptions.find { it.resourceName == setup.startRepSound } ?: defaultSound
        selectedStartRestSound = soundOptions.find { it.resourceName == setup.startRestSound } ?: defaultSound
        selectedStartSetRestSound = soundOptions.find { it.resourceName == setup.startSetRestSound } ?: defaultSound
        selectedCompleteSound = soundOptions.find { it.resourceName == setup.completeSound } ?: defaultSound
        selectedGetReadySound = soundOptions.find { it.resourceName == setup.getReadySound } ?: defaultSound
        selectedImage = imageOptions.find { it.resourceId == setup.config.imageResId } ?: defaultImage
        selectedBandColor = bandColorOptions.find { it.value == setup.config.bandColor } ?: defaultOption
        selectedWeight = weightOptions.find { it.value == setup.config.weightLbs } ?: defaultOption
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

    fun addBandColorOption(color: String) {
        if (color.isNotBlank() && color != "N/A" && bandColorOptions.none { it.value.equals(color, ignoreCase = true) }) {
            val newOption = SpinnerOption(color)        // 1. Add the new option to the master list
            bandColorOptions = (bandColorOptions + newOption).sortedBy { it.value }

            // 2. Set the new option as the currently selected one for the UI
            selectedBandColor = newOption
            // 3. Update the central configState with the new value
            configState = configState.copy(bandColor = newOption.value)

            // 4. Save the complete, updated state
            saveAppState()
        }
    }


    fun addWeightOption(weight: String) {
        val weightNum = weight.toDoubleOrNull()
        if (weightNum != null && weightOptions.none { it.value.equals(weight, ignoreCase = true) }) {
            val newOption = SpinnerOption(weight)
            // 1. Add the new option to the master list
            weightOptions = (weightOptions + newOption).sortedBy { it.value.toDoubleOrNull() ?: Double.MAX_VALUE }

            // 2. Set the new option as the currently selected one for the UI
            selectedWeight = newOption
            // 3. Update the central configState with the new value
            configState = configState.copy(weightLbs = newOption.value)

            // 4. Save the complete, updated state
            saveAppState()
        }
    }


    fun deleteBandColorOption(option: SpinnerOption) {
        if (option.value == "N/A" || bandColorOptions.none { it.value == option.value }) return
        bandColorOptions = bandColorOptions.filter { it.value != option.value }

        // Update any setups that were using the deleted color
        _setups.value = _setups.value.map { setup ->
            var config = setup.config
            if (config.bandColor == option.value) config = config.copy(bandColor = "N/A")
            setup.copy(config = config)
        }
        if (selectedBandColor.value == option.value) selectedBandColor = defaultOption
        saveAppState() // Save after deleting
    }

    fun deleteWeightOption(option: SpinnerOption) {
        if (option.value == "N/A" || weightOptions.none { it.value == option.value }) return
        weightOptions = weightOptions.filter { it.value != option.value }

        // Update any setups that were using the deleted weight
        _setups.value = _setups.value.map { setup ->
            if (setup.config.weightLbs == option.value) {
                setup.copy(config = setup.config.copy(weightLbs = "N/A"))
            } else {
                setup
            }
        }
        if (selectedWeight.value == option.value) selectedWeight = defaultOption
        saveAppState() // Save after deleting
    }

    fun importSetupsFromJson(json: String) {
        try {
            // First, try to parse it as the NEW AppState format
            val appState = gson.fromJson(json, AppState::class.java)
            if (appState != null && appState.allSetups.isNotEmpty()) {
                // 1. Load the spinner options from the imported file into the ViewModel's lists.
                // 1. Load the spinner options by converting the imported List<String> back to List<SpinnerOption>.
                bandColorOptions = appState.bandColorOptions.map { SpinnerOption(it) }.ifEmpty { listOf(defaultOption) }
                weightOptions = appState.weightOptions.map { SpinnerOption(it) }.ifEmpty { listOf(defaultOption) }


                // 2. Now that the lists are populated, load the setups.
                _setups.value = appState.allSetups

                // 3. Find the active setup from the imported file.
                val setupToApply = appState.allSetups.find { it.name == appState.activeSetupName }
                    ?: appState.allSetups.first()

                // 4. Apply the setup. This will now work because the spinner lists have data.
                //    We pass 'isInitialLoad = true' to prevent an immediate re-save.
                applySetup(setupToApply, isInitialLoad = true)

                return // Success, we are done
            }

        } catch (_: Exception) {
            // It failed, so it might be the OLD format. We'll log it and try the old way.
            Log.d("ImportSetups", "Could not parse as AppState, trying legacy format.")
        }

        try {
            // Second, try to parse it as the OLD List<TimerSetup> format
            val setupListType = object : TypeToken<List<TimerSetup>>() {}.type
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
                activeSetupName = this.activeSetup?.name,
                bandColorOptions = bandColorOptions.map { it.value },
                weightOptions = weightOptions.map { it.value }
            )
            val json = gson.toJson(currentState)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.e("SaveToUri", "Failed to write setups to URI: $uri", e)
        }
    }

    // --- Timer Functions and State Machine ---

    fun startTimer() {
        if (timerJob?.isActive == true) return   // prevent accidental entry by clicking start again
        countdownJob?.cancel()
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
        countdownJob?.cancel() // <-- instantly stop the countdown
        timerJob?.cancel()
        countdownJob = null    // <-- cleanup
        timerJob = null
        currentState = TimerState.Ready
        _timerScreenState.update { TimerScreenState(isPaused = false) }
    }


    fun pauseTimer() {
        val state = currentState
        // Only pause if we are in a cancellable, running state
        if (countdownJob?.isActive == true && (state is TimerState.ExercisingInProgress || state is TimerState.RestingInProgress || state is TimerState.SetRestingInProgress || state is TimerState.GettingReadyInProgress)) {
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

            // Use the 'if' as an expression to determine the new state in one place.
            currentState = if (countdownJob?.isActive != true && stateToRestore != null) {
                // The countdown job is dead, so calculate the *next* state.
                when (stateToRestore) {
                    is TimerState.ExercisingInProgress -> determineNextStateAfterExercise()
                    is TimerState.RestingInProgress -> determineNextStateAfterRest()
                    is TimerState.SetRestingInProgress -> determineNextStateAfterSetRest()
                    is TimerState.GettingReadyInProgress -> determineNextStateAfterGetReady()
                    else -> stateToRestore // Fallback for other states
                }
            } else {
                // check() will throw an IllegalStateException if its condition is false.
                check(stateToRestore != null) { "resumeTimer was called but there was no state to restore." }
                // Because the check() passed, the compiler is now smart enough to know
                // that stateToRestore is guaranteed not to be null from this point forward.
                stateToRestore
            }
        }
    }

    private suspend fun runStateMachine(isResuming: Boolean = false) {
        var resuming = isResuming
        while (viewModelScope.isActive) {
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
                is TimerState.GettingReady -> {
                    _timerScreenState.update {
                        it.copy(
                            status = "Get Ready!",
                            isPaused = false
                        )
                    }
                    if (state.remainingDuration == state.totalDuration && !resuming) {
                        AppSoundPlayer.playSound(
                            getApplication(),
                            selectedGetReadySound.resourceId
                        )
                    }
                    countdownJob = viewModelScope.launch { countdown(state.remainingDuration) }
                    currentState = TimerState.GettingReadyInProgress
                }
                // --- WAITING / IDLE STATES ---
                // These states do nothing but wait for an external event (from the countdown worker or UI)
                // to change the `currentState`.

                is TimerState.ExercisingInProgress,
                is TimerState.RestingInProgress,
                is TimerState.SetRestingInProgress,
                is TimerState.GettingReadyInProgress,
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

    private suspend fun countdown(durationMillis: Long) {
        var remainingMillis = durationMillis
        try {
            while (remainingMillis > 0) {
                // This is the cooperative pause loop.
                while (currentState is TimerState.Paused) {
                    viewModelScope.ensureActive() // Allow cancellation
                    delay(50) // Wait patiently
                }
                viewModelScope.ensureActive()
                _timerScreenState.update {
                    it.copy(
                        // remainingTime is now in Millis, formatTime will handle display
                        remainingTime = remainingMillis,
                        progressDisplay = "" // We can simplify or adjust this as needed
                    )
                }

                // Wait for a short interval. 100ms is a good balance for UI updates.
                val delayTime = minOf(100L, remainingMillis)
                delay(delayTime)
                remainingMillis -= delayTime
            }

            // SUCCESS: The countdown finished.
            if (viewModelScope.isActive && currentState !is TimerState.Paused) {
                currentState = when (currentState) {
                    is TimerState.ExercisingInProgress -> determineNextStateAfterExercise()
                    is TimerState.RestingInProgress -> determineNextStateAfterRest()
                    is TimerState.SetRestingInProgress -> determineNextStateAfterSetRest()
                    is TimerState.GettingReadyInProgress -> determineNextStateAfterGetReady()
                    else -> currentState
                }
            }
        } catch (_: CancellationException) {
            // Countdown was cancelled, which is expected on stop.
        }
    }

    private fun determineInitialState(): TimerState {
        return determineNextStateAfterSetRest()  // starts at the Get Ready phase.
    }

    private fun determineNextStateAfterExercise(): TimerState {
        val totalReps = this.configState.reps.toIntOrNull() ?: 1
        val totalSets = this.configState.sets.toIntOrNull() ?: 1
        val setRestSec = this.configState.setRestTime.toDoubleOrNull() ?: 0.0
        val setRestMillis = (setRestSec * 1000).toLong()

        if (currentRepNumber < totalReps) {
            val restSec = this.configState.restTime.toDoubleOrNull() ?: 0.0
            val moveFromSec = this.configState.moveFromTime.toDoubleOrNull() ?: 0.0
            val fullRestDurationMillis = ((restSec + moveFromSec) * 1000).toLong()

            return if (fullRestDurationMillis > 0) {
                currentRepNumber++
                TimerState.Resting(fullRestDurationMillis, fullRestDurationMillis)
            } else {
                currentRepNumber++
                determineNextStateAfterRest()
            }
        }

        if (currentSetNumber < totalSets) {
            currentRepNumber = 1
            currentSetNumber++
            return TimerState.SetResting(setRestMillis, setRestMillis)
        } else {
            return TimerState.Finished
        }
    }

    private fun determineNextStateAfterRest(): TimerState {
        val exerciseSec = this.configState.exerciseTime.toDoubleOrNull() ?: 0.0
        val moveToSec = this.configState.moveToTime.toDoubleOrNull() ?: 0.0
        val fullExerciseDurationMillis = ((exerciseSec + moveToSec) * 1000).toLong()
        return TimerState.Exercising(fullExerciseDurationMillis, fullExerciseDurationMillis)
    }

    private fun determineNextStateAfterSetRest(): TimerState {
        val getReadySec = this.configState.getReadyTime.toDoubleOrNull() ?: 0.0
        val fullGetReadyDurationMillis = (getReadySec * 1000).toLong()
        return TimerState.GettingReady(fullGetReadyDurationMillis, fullGetReadyDurationMillis)
    }

    private fun determineNextStateAfterGetReady(): TimerState {
        val exerciseSec = this.configState.exerciseTime.toDoubleOrNull() ?: 0.0
        val moveToSec = this.configState.moveToTime.toDoubleOrNull() ?: 0.0
        val fullExerciseDurationMillis = ((exerciseSec + moveToSec) * 1000).toLong()
        return TimerState.Exercising(fullExerciseDurationMillis, fullExerciseDurationMillis)
    }
    //
    // --- Sound Initialization ---
    //
    private fun initializeSounds() {
        val allSounds = mutableListOf<SoundOption>()
        // Add the resource name "none" to the default option
        allSounds.add(SoundOption("None", -1, "none"))
        R.raw::class.java.fields.forEach { field ->
            try {
                if (field.name.startsWith("$")) return@forEach
                val resourceId = field.getInt(null)
                // The display name is the user-friendly, formatted name
                val displayName = field.name.replace('_', ' ').replaceFirstChar { it.titlecase() }
                // The resource name is the raw field name, which matches your JSON
                val resourceName = field.name
                // Create the SoundOption with all three properties
                allSounds.add(SoundOption(displayName, resourceId, resourceName))
            } catch (_: Exception) {
            }
        }
        soundOptions = allSounds.sortedBy { it.displayName }
    }

    //
    // --- Image Initialization ---
    //
    private fun initializeImages() {
        val imageList = mutableListOf(ImageOption("None", 0)) // Start with a "None" option
        val fields: Array<Field> = R.drawable::class.java.fields

        try {
            for (field in fields) {
                // Filter to include only your actual image files, not system XML drawables
                if (field.name.startsWith("dowel_") || field.name.startsWith("another_prefix_")) { // Adjust prefixes as needed
                    val name = field.name.replace("_", " ").replaceFirstChar { it.uppercase() }
                    val resourceId = field.getInt(null)
                    imageList.add(ImageOption(name, resourceId))
                }
            }
        } catch (e: Exception) {
            Log.e("InitializeImages", "Error loading drawable resources", e)
        }
        imageOptions = imageList
        selectedImage = defaultImage // Set the initial selection to "None"
    }

    sealed class TimerState {
        // States that INITIATE a countdown
        data class Exercising(val totalDuration: Long, val remainingDuration: Long) : TimerState()
        data class Resting(val totalDuration: Long, val remainingDuration: Long) : TimerState()
        data class SetResting(val totalDuration: Long, val remainingDuration: Long) : TimerState()
        data class GettingReady(val totalDuration: Long, val remainingDuration: Long) : TimerState()

        // States that REPRESENT an ONGOING countdown
        data object ExercisingInProgress : TimerState()
        data object RestingInProgress : TimerState()
        data object SetRestingInProgress : TimerState()
        data object GettingReadyInProgress : TimerState()

        // Control states
        data object Paused : TimerState()
        data object Finished : TimerState()
        data object Ready : TimerState()
    }

}
