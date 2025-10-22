package com.billweberx.pt_timer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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

// In file: app/src/main/java/com/billweberx/pt_timer/TimerViewModel.kt
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("PTTimerState", Application.MODE_PRIVATE)
    private val setupsFilename = "pt_timer_setups.json"
    private val gson = Gson()
    private var timerJob: Job? = null

    // --- UI State Properties (Editable on Settings Screen) ---
    var moveToTime by mutableStateOf("5")
    var exerciseTime by mutableStateOf("30")
    var moveFromTime by mutableStateOf("0")
    var restTime by mutableStateOf("10")
    var reps by mutableStateOf("1") // Formerly 'sets'
    var sets by mutableStateOf("1") // New top-level sets
    var setRestTime by mutableStateOf("60")
    var totalTime by mutableStateOf("0")

    // --- Reactive State for the Timer Screen ---
    private val _timerScreenState = MutableStateFlow<TimerScreenState>(TimerScreenState())
    val timerScreenState = _timerScreenState.asStateFlow()

    // --- Sound Management ---
    var soundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    val defaultSound: SoundOption
        get() = soundOptions.firstOrNull { it.resourceId == -1 } ?: SoundOption("None", -1)

    // Updated sound selections for new phases
    var selectedStartRepSound by mutableStateOf(defaultSound)
    var selectedStartRestSound by mutableStateOf(defaultSound)
    var selectedStartSetRestSound by mutableStateOf(defaultSound)
    var selectedCompleteSound by mutableStateOf(defaultSound)


    // --- Setup Management ---
    var loadedSetups by mutableStateOf<List<TimerSetup>>(emptyList())
        private set
    var activeSetupName by mutableStateOf<String?>(null)
    var activeSetup by mutableStateOf<TimerSetup?>(null)

    // --- Pause/Resume State ---
    var isPaused by mutableStateOf(false)
        private set

    init {
        initializeSounds()
        loadStateFromPrefs()
        loadSetupsFromFile()
        val lastActiveSetupName = prefs.getString(MainActivity.KEY_ACTIVE_SETUP_NAME, null)
        if (lastActiveSetupName != null) {
            val lastActiveSetup =
                loadedSetups.find { it.name.equals(lastActiveSetupName, ignoreCase = true) }
            if (lastActiveSetup != null) {
                applySetup(lastActiveSetup)
            }
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
            } catch (_: Exception) {
                // Log error if needed
            }
        }
        soundOptions = allSounds.sortedBy { it.displayName }
    }

    private fun loadStateFromPrefs() {
        // Load all new values, providing defaults
        moveToTime = prefs.getString(MainActivity.KEY_MOVE_TO_TIME, "5") ?: "5"
        exerciseTime = prefs.getString(MainActivity.KEY_EXERCISE_TIME, "30") ?: "30"
        moveFromTime = prefs.getString(MainActivity.KEY_MOVE_FROM_TIME, "0") ?: "0"
        restTime = prefs.getString(MainActivity.KEY_REST_TIME, "10") ?: "10"
        reps = prefs.getString(MainActivity.KEY_REPS, "1") ?: "1"
        sets = prefs.getString(MainActivity.KEY_SETS, "1") ?: "1"
        setRestTime = prefs.getString(MainActivity.KEY_SET_REST_TIME, "60") ?: "60"
        totalTime = prefs.getString(MainActivity.KEY_TOTAL_TIME, "0") ?: "0"

        // Load all sound selections

        selectedStartRepSound = soundOptions.find {
            it.resourceId == prefs.getInt(
                MainActivity.KEY_START_REP_SOUND_ID,
                defaultSound.resourceId
            )
        } ?: defaultSound
        selectedStartRestSound = soundOptions.find {
            it.resourceId == prefs.getInt(
                MainActivity.KEY_START_REST_SOUND_ID,
                defaultSound.resourceId
            )
        } ?: defaultSound
        selectedStartSetRestSound = soundOptions.find {
            it.resourceId == prefs.getInt(
                MainActivity.KEY_START_SET_REST_SOUND_ID,
                defaultSound.resourceId
            )
        } ?: defaultSound
        selectedCompleteSound = soundOptions.find {
            it.resourceId == prefs.getInt(
                MainActivity.KEY_COMPLETE_SOUND_ID,
                defaultSound.resourceId
            )
        } ?: defaultSound
    }
    fun startTimer(onPlaySound: (Int) -> Unit) {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {            try {
                runTimer(onPlaySound)
            } finally {
                // This block runs when the timer coroutine completes or is cancelled.
                // We only reset the state if it wasn't a manual pause action.
                if (!isPaused) {
                    // Use the existing stopTimer logic to perform a full reset.
                    stopTimer()
                }
            }
        }
    }
    fun saveStateToPrefs() {
        prefs.edit {
            // Save all new values
            putString(MainActivity.KEY_MOVE_TO_TIME, moveToTime)
            putString(MainActivity.KEY_EXERCISE_TIME, exerciseTime)
            putString(MainActivity.KEY_MOVE_FROM_TIME, moveFromTime)
            putString(MainActivity.KEY_REST_TIME, restTime)
            putString(MainActivity.KEY_REPS, reps)
            putString(MainActivity.KEY_SETS, sets)
            putString(MainActivity.KEY_SET_REST_TIME, setRestTime)
            putString(MainActivity.KEY_TOTAL_TIME, totalTime)
            putString(MainActivity.KEY_ACTIVE_SETUP_NAME, activeSetupName)

            // Save all sound selections
            putInt(MainActivity.KEY_START_REP_SOUND_ID, selectedStartRepSound.resourceId)
            putInt(MainActivity.KEY_START_REST_SOUND_ID, selectedStartRestSound.resourceId)
            putInt(MainActivity.KEY_START_SET_REST_SOUND_ID, selectedStartSetRestSound.resourceId)
            putInt(MainActivity.KEY_COMPLETE_SOUND_ID, selectedCompleteSound.resourceId)
        }
    }

    private fun loadSetupsFromFile() {
        try {
            val file = File(getApplication<Application>().filesDir, setupsFilename)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<TimerSetup>>() {}.type
                loadedSetups = gson.fromJson(json, type) ?: emptyList()
            }
        } catch (_: Exception) { /* Log error */
        }
    }

    private fun saveSetupsToFile() {
        try {
            val json = gson.toJson(loadedSetups)
            File(getApplication<Application>().filesDir, setupsFilename).writeText(json)
        } catch (_: Exception) { /* Log error */
        }
    }

    fun addOrUpdateSetup(setup: TimerSetup) {
        val existingIndex =
            loadedSetups.indexOfFirst { it.name.equals(setup.name, ignoreCase = true) }
        val updatedList = loadedSetups.toMutableList()
        if (existingIndex != -1) {
            updatedList[existingIndex] = setup
        } else {
            updatedList.add(setup)
        }
        loadedSetups = updatedList
        saveSetupsToFile()
    }

    fun clearAllSetups() {
        loadedSetups = emptyList()
        activeSetup = null
        activeSetupName = null
        saveSetupsToFile()
    }

    fun deleteSetup(setupName: String) {
        loadedSetups = loadedSetups.filter { !it.name.equals(setupName, ignoreCase = true) }
        saveSetupsToFile()
        if (activeSetupName.equals(setupName, ignoreCase = true)) {
            activeSetupName = null
            activeSetup = null
        }
    }

    fun applySetup(setup: TimerSetup) {
        // Apply all config values from the setup
        moveToTime = setup.config.moveToTime
        exerciseTime = setup.config.exerciseTime
        moveFromTime = setup.config.moveFromTime
        restTime = setup.config.restTime
        reps = setup.config.reps
        sets = setup.config.sets
        setRestTime = setup.config.setRestTime
        totalTime = setup.config.totalTime

        activeSetupName = setup.name
        activeSetup = setup
        saveStateToPrefs() // Save the newly applied setup as the last active state
    }

    fun pauseTimer() {
        isPaused = true
        timerJob?.cancel() // Cancel the running coroutine job
        // The UI state is automatically preserved because we don't reset it here.
    }

    fun stopTimer() {
        // Only cancel the job if it's currently active.
        if (timerJob?.isActive == true) {
            timerJob?.cancel()
        }
        isPaused = false
        timerJob = null    // Clear the reference regardless.
        // Reset the screen state back to the beginning.
        _timerScreenState.update {
            it.copy(
                status = "Ready", // This is the crucial reset for the UI
                remainingTime = 0,
                currentSet = 0,
                currentRep = 0,
                progressDisplay = "",
                totalTimeRemaining = 0
            )
        }
    }
    // Completely rewritten timer logic to support Sets, Reps, and Set Rest
    suspend fun runTimer(onPlaySound: (Int) -> Unit) {
        val startFromPaused = isPaused

        // --- Step 1: Get all values from the UI state ---
// --- Step 1: Get all values from the UI state ---
// These are the full durations for each phase
        val moveToSec =
            (if (startFromPaused) 0.0 else this.moveToTime.toDoubleOrNull())?.toInt() ?: 0
        val exerciseSec = this.exerciseTime.toDoubleOrNull()?.toInt() ?: 0
        val moveFromSec =
            (if (startFromPaused) 0.0 else this.moveFromTime.toDoubleOrNull())?.toInt() ?: 0
        val restSec = this.restTime.toDoubleOrNull()?.toInt() ?: 0
        val setRestSec = this.setRestTime.toDoubleOrNull()?.toInt() ?: 0
        val totalReps = this.reps.toDoubleOrNull()?.toInt() ?: 0
        val totalSets =
            (this.sets.toDoubleOrNull()?.toInt() ?: 1).coerceAtLeast(1) // Treat 0 or less as 1
        val totalTimeSec = this.totalTime.toDoubleOrNull()?.toInt() ?: 0

        // --- Step 2: Determine starting point (fresh or paused) ---
        val startSet: Int
        val startRep: Int
        val initialPhaseTime: Int

        if (startFromPaused) {
            startSet = timerScreenState.value.currentSet
            startRep = timerScreenState.value.currentRep
            initialPhaseTime = timerScreenState.value.remainingTime
            isPaused = false
        } else {
            startSet = 1
            startRep = 1
            initialPhaseTime = 0 // Not used when starting fresh
        }

        // --- Step 3: The Main Timer Logic ---
        if (totalReps > 0) {
            // --- REPS MODE ---
            // Outer loop for SETS - ALWAYS starts from 1
            for (currentSet in 1..totalSets) {
                if (!coroutineContext.isActive) return

                // If resuming, skip past any sets that are already completed.
                if (startFromPaused && currentSet < startSet) {
                    continue
                }

                // Determine if we are resuming within the current set
                val isResumingThisSet = startFromPaused && currentSet == startSet

                // Inner loop for REPS
                // If resuming, start from startRep, otherwise start from 1.
                if (!(isResumingThisSet && timerScreenState.value.status == "Set Rest")) {
                    for (currentRep in (if (isResumingThisSet) startRep else 1)..totalReps) {
                        if (!coroutineContext.isActive) return

                        // --- A. EXERCISE PHASE ---

                        if (!(isResumingThisSet && currentRep == startRep && timerScreenState.value.status == "Rest")) {
                            val isResumingExercise =
                                isResumingThisSet && currentRep == startRep && timerScreenState.value.status == "Exercise!"
                            val exerciseDuration =
                                if (isResumingExercise) initialPhaseTime else moveToSec + exerciseSec

                            if (exerciseDuration > 0) {
                                _timerScreenState.update {
                                    it.copy(
                                        status = "Exercise!",
                                        currentSet = currentSet,
                                        currentRep = currentRep,
                                        progressDisplay = "Reps remaining: ${(totalReps - currentRep) + 1}"
                                    )
                                }
                                if (!isResumingExercise) {
                                    onPlaySound(selectedStartRepSound.resourceId)
                                }
                                for (t in exerciseDuration downTo 1) {
                                    if (!coroutineContext.isActive) return
                                    _timerScreenState.update { it.copy(remainingTime = t) }
                                    delay(1000)
                                }
                            }
                        }
                        // --- B. REP REST PHASE (if not the last rep of the set) ---
                        if (currentRep < totalReps) {
                            val isResumingRest =
                                isResumingThisSet && currentRep == startRep && timerScreenState.value.status == "Rest"
                            val restDuration =
                                if (isResumingRest) initialPhaseTime else moveFromSec + restSec
                            if (restDuration > 0) {
                                _timerScreenState.update {
                                    it.copy(
                                        status = "Rest",
                                        currentSet = currentSet,
                                        currentRep = currentRep
                                    )
                                }
                                if (!isResumingRest) {
                                    onPlaySound(selectedStartRestSound.resourceId)
                                }
                                for (t in restDuration downTo 1) {
                                    if (!coroutineContext.isActive) return
                                    _timerScreenState.update { it.copy(remainingTime = t) }
                                    delay(1000)
                                }
                            }
                        }
                    }
                }

                // --- C. SET REST PHASE (if not the last set) ---
                if (currentSet < totalSets) {
                    val isResumingSetRest =
                        isResumingThisSet && timerScreenState.value.status == "Set Rest"
                    val setRestDuration = if (isResumingSetRest) initialPhaseTime else setRestSec
                    if (setRestDuration > 0) {
                        _timerScreenState.update {
                            it.copy(
                                status = "Set Rest",
                                currentSet = currentSet,
                                currentRep = totalReps
                            )
                        }
                        if (!isResumingSetRest) {
                            onPlaySound(selectedStartSetRestSound.resourceId)
                        }
                        for (t in setRestDuration downTo 1) {
                            if (!coroutineContext.isActive) return
                            _timerScreenState.update { it.copy(remainingTime = t) }
                            delay(1000)
                        }
                    }
                }
            }
        } else if (totalTimeSec > 0) {
            // --- TOTAL TIME MODE (Work-for-Time model) ---
            // Loop through each set.
            for (currentSet in startSet..totalSets) {
                if (!coroutineContext.isActive) return

                // Total time for this set, resets every set.
                val isResumingThisSet = startFromPaused && currentSet == startSet

                var remainingTimeInSet =
                    if (isResumingThisSet) timerScreenState.value.totalTimeRemaining else totalTimeSec

                // Use 'startRep' from the paused state to track which cycle we are on.
                var repCounter = if (isResumingThisSet) startRep else 1
                if (!(isResumingThisSet && timerScreenState.value.status == "Set Rest")) {
                    while (remainingTimeInSet > 0) { // Loop until time for the set runs out
                        if (!coroutineContext.isActive) return

                        // --- A. EXERCISE PHASE ---
                        // Skip this phase if we are resuming from this set's "Rest" phase
                        if (!(isResumingThisSet && repCounter == startRep && timerScreenState.value.status == "Rest")) {
                            val isResumingExercise =
                                isResumingThisSet && repCounter == startRep && timerScreenState.value.status == "Exercise!"
                            val exerciseDuration =
                                if (isResumingExercise) initialPhaseTime else moveToSec + exerciseSec

                            if (exerciseDuration > 0) {
                                _timerScreenState.update {
                                    it.copy(
                                        status = "Exercise!",
                                        currentSet = currentSet,
                                        currentRep = repCounter,
                                        totalTimeRemaining = remainingTimeInSet,
                                        progressDisplay = "Total Time: ${remainingTimeInSet}s"
                                    )
                                }
                                if (!isResumingExercise) {
                                    onPlaySound(selectedStartRepSound.resourceId)
                                }
                                val countdownDuration = minOf(exerciseDuration, remainingTimeInSet)
                                for (t in countdownDuration downTo 1) {
                                    if (!coroutineContext.isActive) return
                                    remainingTimeInSet-- // Decrement first
                                    _timerScreenState.update {
                                        it.copy(
                                            remainingTime = t,
                                            progressDisplay = "Total Time: ${remainingTimeInSet}s" // <-- ADDED
                                        )
                                    }
                                    delay(1000)
                                }
                            }
                        }

                        // --- B. REST PHASE ---
                        if (restSec > 0 && remainingTimeInSet > 0) {
                            val isResumingRest =
                                isResumingThisSet && repCounter == startRep && timerScreenState.value.status == "Rest"
                            val restDuration =
                                if (isResumingRest) initialPhaseTime else moveFromSec + restSec

                            if (restDuration > 0) {
                                _timerScreenState.update {
                                    it.copy(
                                        status = "Rest",
                                        currentSet = currentSet,
                                        currentRep = repCounter,
                                        totalTimeRemaining = remainingTimeInSet,
                                        progressDisplay = "Total Time: ${remainingTimeInSet}s"
                                    )
                                }
                                if (!isResumingRest) {
                                    onPlaySound(selectedStartRestSound.resourceId)
                                }
                                val countdownDuration = minOf(restDuration, remainingTimeInSet)
                                for (t in countdownDuration downTo 1) {
                                    if (!coroutineContext.isActive) return
                                    remainingTimeInSet-- // Decrement first
                                    _timerScreenState.update {
                                        it.copy(
                                            remainingTime = t,
                                            progressDisplay = "Total Time: ${remainingTimeInSet}s" // <-- ADDED
                                        )
                                    }
                                    delay(1000)
                                }
                            }
                        }
                        repCounter++ // Move to the next cycle
                    }
                }
                // --- C. SET REST PHASE (if not the last set) ---
                if (currentSet < totalSets) {
                    val isResumingSetRest =
                        isResumingThisSet && timerScreenState.value.status == "Set Rest"
                    val setRestDuration = if (isResumingSetRest) initialPhaseTime else setRestSec
                    if (setRestDuration > 0) {
                        _timerScreenState.update {
                            it.copy(
                                status = "Set Rest",
                                currentSet = currentSet,
                                currentRep = repCounter // Store the last cycle count
                            )
                        }
                        if (!isResumingSetRest) {
                            onPlaySound(selectedStartSetRestSound.resourceId)
                        }
                        for (t in setRestDuration downTo 1) {
                            if (!coroutineContext.isActive) return
                            _timerScreenState.update { it.copy(remainingTime = t) }
                            delay(1000)
                        }
                    }
                }
            }
        }
        // --- Step 4: Timer Finished ---
        if (coroutineContext.isActive) {
            onPlaySound(selectedCompleteSound.resourceId)
            _timerScreenState.update { it.copy(status = "Finished", progressDisplay = "") }
        }
    }
    fun exportSetupsToJson(): String = gson.toJson(loadedSetups)

    fun importSetupsFromJson(json: String) {
        val type = object : TypeToken<List<TimerSetup>>() {}.type
        val imported: List<TimerSetup> = gson.fromJson(json, type) ?: emptyList()
        val updatedSetups = loadedSetups.toMutableList()
        imported.forEach { newSetup ->
            val existingIndex =
                updatedSetups.indexOfFirst { it.name.equals(newSetup.name, ignoreCase = true) }
            if (existingIndex != -1) {
                updatedSetups[existingIndex] = newSetup
            } else {
                updatedSetups.add(newSetup)
            }
        }
        loadedSetups = updatedSetups // Keep imported order, do not sort
        saveSetupsToFile()
    }
}