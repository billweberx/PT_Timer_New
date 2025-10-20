// In file: app/src/main/java/com/billweberx/pt_timer/TimerViewModel.kt

package com.billweberx.pt_timer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.coroutines.coroutineContext

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("PTTimerState", Application.MODE_PRIVATE)
    private val setupsFilename = "pt_timer_setups.json"
    private val gson = Gson()

    // --- UI State Properties ---
    var exerciseTime by mutableStateOf("30")
    var restTime by mutableStateOf("10")
    var sets by mutableStateOf("1")
    var delayTime by mutableStateOf("5")
    var totalTime by mutableStateOf("0")
    // --- Reactive State for the Timer Screen ---
    private val _timerScreenState = MutableStateFlow(TimerScreenState())
    val timerScreenState = _timerScreenState.asStateFlow()

    // --- Sound Management ---
    var soundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    val defaultSound: SoundOption get() = soundOptions.firstOrNull { it.resourceId == -1 } ?: SoundOption("None", -1)

    var selectedStartSound by mutableStateOf(defaultSound)
    var selectedRestSound by mutableStateOf(defaultSound)
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
            val lastActiveSetup = loadedSetups.find { it.name.equals(lastActiveSetupName, ignoreCase = true) }
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
        exerciseTime = prefs.getString(MainActivity.KEY_EXERCISE_TIME, "30") ?: "30"
        restTime = prefs.getString(MainActivity.KEY_REST_TIME, "10") ?: "10"
        sets = prefs.getString(MainActivity.KEY_SETS, "1") ?: "1"
        totalTime = prefs.getString(MainActivity.KEY_TOTAL_TIME, "0") ?: "0"
        delayTime = prefs.getString(MainActivity.KEY_DELAY_TIME, "5") ?: "5"

        val startSoundId = prefs.getInt(MainActivity.KEY_START_SOUND_ID, defaultSound.resourceId)
        val restSoundId = prefs.getInt(MainActivity.KEY_REST_SOUND_ID, defaultSound.resourceId)
        val completeSoundId = prefs.getInt(MainActivity.KEY_COMPLETE_SOUND_ID, defaultSound.resourceId)

        selectedStartSound = soundOptions.find { it.resourceId == startSoundId } ?: defaultSound
        selectedRestSound = soundOptions.find { it.resourceId == restSoundId } ?: defaultSound
        selectedCompleteSound = soundOptions.find { it.resourceId == completeSoundId } ?: defaultSound
    }

    fun saveStateToPrefs() {
        prefs.edit {
            putString(MainActivity.KEY_EXERCISE_TIME, exerciseTime)
            putString(MainActivity.KEY_REST_TIME, restTime)
            putString(MainActivity.KEY_SETS, sets)
            putString(MainActivity.KEY_TOTAL_TIME, totalTime)
            putString(MainActivity.KEY_DELAY_TIME, delayTime)
            putString(MainActivity.KEY_ACTIVE_SETUP_NAME, activeSetupName)
            putInt(MainActivity.KEY_START_SOUND_ID, selectedStartSound.resourceId)
            putInt(MainActivity.KEY_REST_SOUND_ID, selectedRestSound.resourceId)
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
        } catch (_: Exception) { /* Log error */ }
    }

    private fun saveSetupsToFile() {
        try {
            val json = gson.toJson(loadedSetups)
            File(getApplication<Application>().filesDir, setupsFilename).writeText(json)
        } catch (_: Exception) { /* Log error */ }
    }

    fun addOrUpdateSetup(setup: TimerSetup) {
        val existingIndex = loadedSetups.indexOfFirst { it.name.equals(setup.name, ignoreCase = true) }
        val updatedList = loadedSetups.toMutableList()
        if (existingIndex != -1) {
            // If the item exists, just update it in its current position.
            updatedList[existingIndex] = setup
        } else {
            // If it's a new item, add it to the end of the list.
            updatedList.add(setup)
        }
        // The list is now in the correct creation order. No more sorting.
        loadedSetups = updatedList
        saveSetupsToFile()
    }
    fun clearAllSetups() {
        loadedSetups = emptyList() // Set the list to be empty
        activeSetup = null          // Clear the currently active setup
        activeSetupName = null
        saveSetupsToFile()          // Persist the empty list to the file
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
        exerciseTime = setup.config.exerciseTime
        restTime = setup.config.restTime
        sets = setup.config.sets
        totalTime = setup.config.totalTime
        delayTime = setup.config.delayTime
        activeSetupName = setup.name
        activeSetup = setup
        saveStateToPrefs()
    }

    fun pauseTimer() {
        isPaused = true
    }

    fun stopTimer() {
        isPaused = false
        _timerScreenState.update {
            it.copy(
                status = "Ready",
                remainingTime = 0,
                setsRemaining = 0
            )
        }
    }

    suspend fun runTimer(
        onPlaySound: (Int) -> Unit
    ) {
        // --- Step 1: Get all values from the UI state ---
        val exerciseSec: Int
        val restSec: Int
        val delaySec: Int
        var totalTimeSec: Int // Use var for the master clock in total time mode
        val numberOfSets: Int
        val startFromPaused: Boolean = isPaused

        if (startFromPaused) {
            // If resuming, use the current state values
            // Note: Resuming in total time mode can be complex; this is a best-effort approach.
            exerciseSec = timerScreenState.value.remainingTime
            restSec = this.restTime.toFloatOrNull()?.toInt() ?: 0
            delaySec = 0
            totalTimeSec = this.totalTime.toFloatOrNull()?.toInt() ?: 0
            numberOfSets = timerScreenState.value.setsRemaining
            isPaused = false
        } else {
            // If starting fresh, parse from text fields
            exerciseSec = this.exerciseTime.toFloatOrNull()?.toInt() ?: 0
            restSec = this.restTime.toFloatOrNull()?.toInt() ?: 0
            delaySec = this.delayTime.toFloatOrNull()?.toInt() ?: 0
            totalTimeSec = this.totalTime.toFloatOrNull()?.toInt() ?: 0
            numberOfSets = this.sets.toFloatOrNull()?.toInt() ?: 0
        }

        // --- Step 2: The Main Timer Logic ---
        if (numberOfSets > 0) {
            // --- SETS MODE --- (Total Time is ignored)

            // CORRECTED LOGIC: Determine the starting set number for the countdown.
            val setCounterStart = if (startFromPaused) {
                numberOfSets // If paused, 'numberOfSets' holds the *remaining* set number.
            } else {
                // If starting fresh, get the full number of sets from the text field.
                this.sets.toFloatOrNull()?.toInt() ?: 0
            }

            // Use a 'for' loop that counts down automatically. This is much safer.
            for (currentSet in setCounterStart downTo 1) {
                if (!coroutineContext.isActive) return

                // 1. Exercise Phase

                // *** THE FIX IS HERE ***
                // When resuming, the first "set" might be a partial one.
                val isResumingExercise = startFromPaused && currentSet == setCounterStart && timerScreenState.value.status != "Rest"

                val currentExerciseDuration = if (isResumingExercise) {
                    // On resume, the timer loop uses only the remaining time. The 'delaySec' is ignored for this partial set.
                    exerciseSec
                } else {
                    // For all normal sets, calculate the full duration including delay.
                    (this.exerciseTime.toFloatOrNull()?.toInt() ?: 0) + (this.delayTime.toFloatOrNull()?.toInt() ?: 0)
                }


                if (currentExerciseDuration > 0) {
                    _timerScreenState.update { it.copy(
                        status = "Exercise!",
                        setsRemaining = currentSet, // This is now a countdown value.
                        progressDisplay = "Remaining Sets: $currentSet"
                    ) }
                    // Only play the start sound for a full, new set, not a resumed one.
                    if (!isResumingExercise) {
                        onPlaySound(selectedStartSound.resourceId)
                        delay(200)
                    }

                    for (t in currentExerciseDuration downTo 1) {
                        if (!coroutineContext.isActive) return
                        _timerScreenState.update { it.copy(remainingTime = t) }
                        delay(1000)
                    }
                }

                // 2. Rest Phase (only if this isn't the final set)
                if (currentSet > 1 && restSec > 0) {
                    if (!coroutineContext.isActive) return

                    val isResumingRest = startFromPaused && currentSet == setCounterStart && timerScreenState.value.status == "Rest"

                    // Check if resuming from a paused rest phase.
                    val currentRestDuration = if (isResumingRest) {
                        exerciseSec // 'exerciseSec' holds the remaining time when paused.
                    } else {
                        restSec
                    }

                    _timerScreenState.update { it.copy(
                        status = "Rest",
                        setsRemaining = currentSet,
                        // Show sets remaining *after* this rest is complete.
                        progressDisplay = "Remaining Sets: ${currentSet - 1}"
                    ) }
                    // Only play sound for a new rest, not a resumed one.
                    if (!isResumingRest) {
                        onPlaySound(selectedRestSound.resourceId)
                        delay(200)
                    }

                    for (t in currentRestDuration downTo 1) {
                        if (!coroutineContext.isActive) return
                        _timerScreenState.update { it.copy(remainingTime = t) }
                        delay(1000)
                    }
                }
            }

        } else if (totalTimeSec > 0) { // Keep the existing, correct "Total Time" logic
            // ... The code for this block remains unchanged ...

            // --- TOTAL TIME MODE --- (Only runs if sets is 0)
            val exerciseDuration = exerciseSec + delaySec

            // Loop "forever" until totalTimeSec runs out
            while (coroutineContext.isActive && totalTimeSec > 0) {

                // 1. Exercise Phase
                if (exerciseDuration > 0) {
                    _timerScreenState.update { it.copy(
                        status = "Exercise!",
                        setsRemaining = 0,
                        progressDisplay = "Remaining Time: $totalTimeSec sec"
                    ) }
                    onPlaySound(selectedStartSound.resourceId)
                    delay(200)

                    for (t in exerciseDuration downTo 1) {
                        if (!coroutineContext.isActive || totalTimeSec <= 0) break
                        _timerScreenState.update { it.copy(
                            remainingTime = t,
                            progressDisplay = "Remaining Time: $totalTimeSec sec"
                        ) }
                        delay(1000)
                        totalTimeSec--
                    }
                }
                if (!coroutineContext.isActive || totalTimeSec <= 0) break

                // 2. Rest Phase
                if (restSec > 0) {
                    _timerScreenState.update { it.copy(
                        status = "Rest",
                        progressDisplay = "Remaining Time: $totalTimeSec sec"
                    ) }
                    onPlaySound(selectedRestSound.resourceId)
                    delay(200)

                    for (t in restSec downTo 1) {
                        if (!coroutineContext.isActive || totalTimeSec <= 0) break
                        _timerScreenState.update { it.copy(
                            remainingTime = t,
                            progressDisplay = "Remaining Time: $totalTimeSec sec"
                        ) }
                        delay(1000)
                        totalTimeSec--
                    }
                }
            }
        }

        // --- Step 3: Timer Finished ---
        if (coroutineContext.isActive) {
            onPlaySound(selectedCompleteSound.resourceId)
            delay(1000)
            val finalMessage = if ((this.sets.toFloatOrNull()?.toInt() ?: 0) > 1) "Finished" else "Finished"
            _timerScreenState.update { it.copy(
                status = finalMessage,
                progressDisplay = "" // Clear the progress display
            ) }
        }
    }

    fun exportSetupsToJson(): String = gson.toJson(loadedSetups)

    fun importSetupsFromJson(json: String) {
        val type = object : TypeToken<List<TimerSetup>>() {}.type
        val imported: List<TimerSetup> = gson.fromJson(json, type) ?: emptyList()
        val updatedSetups = loadedSetups.toMutableList()
        imported.forEach { newSetup ->
            val existingIndex = updatedSetups.indexOfFirst { it.name.equals(newSetup.name, ignoreCase = true) }
            if (existingIndex != -1) {
                updatedSetups[existingIndex] = newSetup
            } else {
                updatedSetups.add(newSetup)
            }
        }
        loadedSetups = updatedSetups.sortedBy { it.name }
        saveSetupsToFile()
    }
}
