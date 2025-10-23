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

    // Durations
    private val exercisePhaseDuration: Long get() = (exerciseTime.toLongOrNull() ?: 0L) + (moveToTime.toLongOrNull() ?: 0L)
    private val restPhaseDuration: Long get() = (restTime.toLongOrNull() ?: 0L) + (moveFromTime.toLongOrNull() ?: 0L)

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
    fun addOrUpdateSetup(setupName: String) {
        if (setupName.isBlank()) return

        // First, create the nested SetupConfig object from the ViewModel's state.
        val config = SetupConfig(
            moveToTime = this.moveToTime,
            exerciseTime = this.exerciseTime,
            moveFromTime = this.moveFromTime,
            restTime = this.restTime,
            reps = this.reps,
            sets = this.sets,
            setRestTime = this.setRestTime,
            totalTime = this.totalTime
        )

        // Then, create the parent TimerSetup object, passing the config object in.
        val newOrUpdatedSetup = TimerSetup(
            name = setupName,
            config = config, // Pass the nested object here
            startRepSoundId = selectedStartRepSound.resourceId,
            startRestSoundId = selectedStartRestSound.resourceId,
            startSetRestSoundId = selectedStartSetRestSound.resourceId,
            completeSoundId = selectedCompleteSound.resourceId
        )

        // The rest of the function remains the same
        val currentList = _setups.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.equals(setupName, ignoreCase = true) }

        if (existingIndex != -1) {
            currentList[existingIndex] = newOrUpdatedSetup
        } else {
            currentList.add(newOrUpdatedSetup)
        }
        _setups.value = currentList
        saveSetupsToFile()
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
    fun startTimer(onPlaySound: (Int) -> Unit) {
        // If a job is already active, do nothing.
        if (timerJob?.isActive == true) return

        // A fresh start ALWAYS sets isPaused to false.
        isPaused = false

        timerJob = viewModelScope.launch {
            try {
                runTimer(onPlaySound)
            } finally {
                if (!isPaused) {
                    stopTimer()
                }
            }
        }
    }


    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        isPaused = false
        _timerScreenState.update { TimerScreenState() }
    }

    fun pauseTimer() {
        if (timerJob?.isActive == true) {
            // Get the current state values synchronously
            val currentStatus = _timerScreenState.value.status
            val remainingTime = _timerScreenState.value.remainingTime
            val currentSet = _timerScreenState.value.currentSet
            val currentRep = _timerScreenState.value.currentRep
            val currentProgressDisplay = _timerScreenState.value.progressDisplay
            // Now cancel the job
            isPaused = true
            timerJob?.cancel()

            // Update the state with the values we captured
            _timerScreenState.value = _timerScreenState.value.copy(
                activePhase = currentStatus, // Save the phase we were in
                status = "Paused",
                // Re-assert the values to ensure they are saved
                remainingTime = remainingTime,
                currentSet = currentSet,
                currentRep = currentRep,
                progressDisplay = currentProgressDisplay
            )
        }
    }


    // In TimerViewModel.kt
    fun resumeTimer(onPlaySound: (Int) -> Unit) {
        if (isPaused) {
            // DO NOT set isPaused = false here.
            // Simply launch a new timer job. The 'isPaused' flag being true is our signal.
            if (timerJob?.isActive == true) return

            timerJob = viewModelScope.launch {
                try {
                    runTimer(onPlaySound)
                } finally {
                    // This finally block runs after the coroutine is over.
                    // If it finished because it was paused again, !isPaused will be false.
                    // If it finished naturally, !isPaused will be true, and we stop everything.
                    if (!isPaused) {
                        stopTimer()
                    }
                }
            }
        }
    }
    private suspend fun runTimer(onPlaySound: (Int) -> Unit) {
        // --- Phase 1: Determine Mode and Get Values ---
        val totalReps = this.reps.toIntOrNull() ?: 0
        val isTotalTimeMode = totalReps <= 0

        // --- Phase 2: Execute Logic Based on Mode ---
        if (isTotalTimeMode) {
            //===================================================================
            //==   TOTAL TIME MODE LOGIC (ANR FIX for Set Rest Pause)          ==
            //===================================================================
            Log.d("TIMER_DEBUG", "Starting TOTAL TIME mode.")
            val totalSets = this.sets.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val setRestSec = this.setRestTime.toLongOrNull() ?: 0L

            // --- RESUME LOGIC ---
            val startSet = if (isPaused) _timerScreenState.value.currentSet.coerceAtLeast(1) else 1
            var resumePhase = if (isPaused) _timerScreenState.value.activePhase else null
            var resumeTime = if (isPaused) _timerScreenState.value.remainingTime.toLong() else null
            val resumeMasterClock = if (isPaused) _timerScreenState.value.progressDisplay.filter { it.isDigit() }.toLongOrNull() else null
            Log.d("TIMER_DEBUG", "runTimer(TotalTime). isPaused=$isPaused, Phase:$resumePhase, PTime:$resumeTime, MTime:$resumeMasterClock")
            isPaused = false
            // --- END RESUME LOGIC ---
            for (currentSet in startSet..totalSets) {
                if (!coroutineContext.isActive) return
                if (resumePhase == "Set Rest") {
                    _timerScreenState.update { it.copy(status = "Set Rest", progressDisplay = "Set Rest") }
                    if (resumeTime == null) { onPlaySound(selectedStartSetRestSound.resourceId) }

                    val duration = resumeTime ?: setRestSec
                    for (t in duration downTo 1) {
                        if (!coroutineContext.isActive) return
                        _timerScreenState.update { it.copy(remainingTime = t.toInt()) }
                        delay(1000)
                    }
                    // Clear the resume state and 'continue' to the next iteration of the 'for' loop.
                    resumeTime = null; resumePhase = null
                    continue
                }

                val totalDurationForSet = this.totalTime.toLongOrNull() ?: 0L
                if (totalDurationForSet <= 0) continue

                var timeRemainingInMasterClock = resumeMasterClock ?: totalDurationForSet

                while (timeRemainingInMasterClock > 0 && coroutineContext.isActive) {
                    // --- 1. EXERCISE PHASE ---
                    if (resumePhase == null || resumePhase.startsWith("Exercise")) {
                        _timerScreenState.update { it.copy(status = "Exercise!") }
                        if (resumeTime == null) { onPlaySound(selectedStartRepSound.resourceId) }
                        val duration = resumeTime ?: exercisePhaseDuration
                        for (t in duration downTo 1) {
                            if (!coroutineContext.isActive || timeRemainingInMasterClock <= 0) break
                            _timerScreenState.update { it.copy(
                                remainingTime = t.toInt(),
                                progressDisplay = "Time Remaining: $timeRemainingInMasterClock sec",
                                currentSet = currentSet
                            )}
                            timeRemainingInMasterClock--
                            delay(1000)
                        }
                        resumeTime = null; resumePhase = null
                    }

                    if (!coroutineContext.isActive || timeRemainingInMasterClock <= 0) break

                    // --- 2. REST PHASE ---
                    if (restPhaseDuration > 0 && (resumePhase == null || resumePhase == "Rest")) {
                        _timerScreenState.update { it.copy(status = "Rest") }
                        if (resumeTime == null) { onPlaySound(selectedStartRestSound.resourceId) }
                        val duration = resumeTime ?: restPhaseDuration
                        for (t in duration downTo 1) {
                            if (!coroutineContext.isActive || timeRemainingInMasterClock <= 0) break
                            _timerScreenState.update { it.copy(
                                remainingTime = t.toInt(),
                                progressDisplay = "Time Remaining: $timeRemainingInMasterClock sec",
                                currentSet = currentSet
                            )}
                            timeRemainingInMasterClock--
                            delay(1000)
                        }
                        resumeTime = null; resumePhase = null
                    }
                } // --- End of inner timed loop ---

                // --- 3. SET REST PHASE (for natural transition) ---
                val isLastSet = currentSet == totalSets
                if (!isLastSet && setRestSec > 0 && coroutineContext.isActive) {
                    _timerScreenState.update { it.copy(status = "Set Rest", progressDisplay = "Set Rest") }
                    onPlaySound(selectedStartSetRestSound.resourceId)
                    for (t in setRestSec downTo 1) {
                        if (!coroutineContext.isActive) return
                        _timerScreenState.update { it.copy(remainingTime = t.toInt()) }
                        delay(1000)
                    }
                }
            } // --- End of outer 'for' loop for sets ---
        } else {
            //==============================================================
            //==                  EXISTING: REPS MODE LOGIC                 ==
            //==============================================================
            Log.d("TIMER_DEBUG", "Starting REPS mode.")
            val totalSets = this.sets.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val setRestSec = this.setRestTime.toLongOrNull() ?: 0L

            // --- RESUME LOGIC (for Reps mode) ---
            val startSet = if (isPaused) _timerScreenState.value.currentSet.coerceAtLeast(1) else 1
            val startRep = if (isPaused) _timerScreenState.value.currentRep.coerceAtLeast(1) else 1
            var resumePhase = if (isPaused) _timerScreenState.value.activePhase else null
            var resumeTime = if (isPaused) _timerScreenState.value.remainingTime.toLong() else null
            Log.d("TIMER_DEBUG", "runTimer(Reps) started. isPaused = $isPaused, Phase: $resumePhase, Time: $resumeTime")
            isPaused = false
            // --- END RESUME LOGIC ---

            for (currentSet in startSet..totalSets) {
                if (!coroutineContext.isActive) return
                val initialRepInLoop = if (currentSet == startSet && resumePhase != null) startRep else 1

                for (currentRep in initialRepInLoop..totalReps) {
                    if (!coroutineContext.isActive) return

                    // --- 1. EXERCISE PHASE ---
                    if (resumePhase == null || resumePhase.startsWith("Exercise")) {
                        _timerScreenState.update { it.copy(status = "Exercise!", currentSet = currentSet, currentRep = currentRep) }
                        if (resumeTime == null) { onPlaySound(selectedStartRepSound.resourceId) }
                        val duration = resumeTime ?: exercisePhaseDuration
                        for (t in duration downTo 1) {
                            if (!coroutineContext.isActive) return
                            _timerScreenState.update { it.copy(remainingTime = t.toInt()) }
                            delay(1000)
                        }
                        resumeTime = null
                        resumePhase = null
                    }

                    val isLastRepOfLastSet = (currentRep == totalReps && currentSet == totalSets)
                    if (isLastRepOfLastSet) break

                    // --- 2. REP/SET REST PHASES ---
                    if (currentRep < totalReps) { // Rep Rest
                        if (restPhaseDuration > 0 && (resumePhase == null || resumePhase == "Rest")) {
                            _timerScreenState.update { it.copy(status = "Rest") }
                            if (resumeTime == null) { onPlaySound(selectedStartRestSound.resourceId) }
                            val duration = resumeTime ?: restPhaseDuration
                            for (t in duration downTo 1) {
                                if (!coroutineContext.isActive) return
                                _timerScreenState.update { it.copy(remainingTime = t.toInt()) }
                                delay(1000)
                            }
                            resumeTime = null; resumePhase = null
                        }
                    } else { // Set Rest
                        if (setRestSec > 0 && (resumePhase == null || resumePhase == "Set Rest")) {
                            _timerScreenState.update { it.copy(status = "Set Rest") }
                            if (resumeTime == null) { onPlaySound(selectedStartSetRestSound.resourceId) }
                            val duration = resumeTime ?: setRestSec
                            for (t in duration downTo 1) {
                                if (!coroutineContext.isActive) return
                                _timerScreenState.update { it.copy(remainingTime = t.toInt()) }
                                delay(1000)
                            }
                            resumeTime = null; resumePhase = null
                        }
                    }
                }
            }
        }

        // --- FINISH (common to both modes) ---
        if (coroutineContext.isActive) {
            Log.d("TIMER_DEBUG", "Timer finished naturally.")
            onPlaySound(selectedCompleteSound.resourceId)
            _timerScreenState.update { it.copy(status = "Finished", progressDisplay = "") }
        }
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

