package com.billweberx.pt_timer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.billweberx.pt_timer.data.AppState
import com.billweberx.pt_timer.data.BundleOption
import com.billweberx.pt_timer.data.SetupConfig
import com.billweberx.pt_timer.data.TimerScreenState
import com.billweberx.pt_timer.data.TimerSetup
import com.billweberx.pt_timer.util.AppSoundPlayer
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.billweberx.pt_timer.data.ImageOption
import com.billweberx.pt_timer.data.SpinnerOption
import com.google.gson.JsonParser


class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    private val appStateFilename = "app_state.json"

    val userImagesDirectory = "user_images"

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
    var selectedImage by mutableStateOf(defaultImage)
    val defaultImage: ImageOption
        get() = imageOptions.firstOrNull { it.resourceId == 0 } ?: ImageOption("None", 0, "none")

    // --- UI Properties ---
    var configState by mutableStateOf(SetupConfig())
    var soundOptions by mutableStateOf<List<SoundOption>>(emptyList())
        private set
    val defaultSound: SoundOption
        get() = soundOptions.find { it.displayName.equals("None", ignoreCase = true) }
            ?: SoundOption("None", -1, "none")

    var selectedStartRepSound by mutableStateOf(defaultSound)
    var selectedStartRestSound by mutableStateOf(defaultSound)
    var selectedStartSetRestSound by mutableStateOf(defaultSound)
    var selectedCompleteSound by mutableStateOf(defaultSound)
    var selectedGetReadySound by mutableStateOf(defaultSound)
    var activeSetupName by mutableStateOf<String?>("")
    var activeSetup by mutableStateOf<TimerSetup?>(null)
    private val _isExerciseListDirty = MutableStateFlow(false)

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

    // Bundle properties
    var bundleOptions by mutableStateOf<List<BundleOption>>(emptyList())
        private set
    var selectedBundle by mutableStateOf<BundleOption?>(null) // Nullable as no bundle may be selected initially
    var isBundleDropdownExpanded by mutableStateOf(false)

    var continueToNextExercise by mutableStateOf(false)
    var currentSetupIndex by mutableStateOf(-1) // Index of the currently active setup in _setups list
        private set

    // State for managing the bundle loading process
    var showLoadBundleOptionsDialog by mutableStateOf(false)
        private set
    var showReplaceConfirmationDialog by mutableStateOf(false)
        private set
    private val _showOverwriteImageConfirmDialog = MutableStateFlow<ImageOption?>(null)
    val showOverwriteImageConfirmDialog: StateFlow<ImageOption?> =
        _showOverwriteImageConfirmDialog.asStateFlow()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val defaultOption = SpinnerOption("N/A")

    init {
        initializeSounds()
        initializeImages()
        initializeBundles()
        loadAppState() // Simplified initialization
    }

    fun dismissOverwriteImageConfirmDialog() {
        _showOverwriteImageConfirmDialog.value = null
    }

    fun showOverwriteImageConfirmDialog(imageOption: ImageOption) {
        _showOverwriteImageConfirmDialog.value = imageOption
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

    fun dismissLoadBundleOptions() {
        showLoadBundleOptionsDialog = false
    }

    fun showReplaceConfirmation() {
        showReplaceConfirmationDialog = true
    }

    fun dismissReplaceConfirmation() {
        showReplaceConfirmationDialog = false
    }

    fun performBundleLoad(
        bundle: BundleOption,
        action: BundleLoadAction
    ) { // Add bundle as argument
        // No need for `bundleToLoadForAction?.let` anymore
        Log.d(
            "BundleLoad",
            "performBundleLoad called. Action: $action, Bundle: ${bundle.name}"
        ) // Diagnostic log

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = if (bundle.isFactory) {
                    Log.d("BundleLoad", "Reading factory bundle from assets: ${bundle.filePath}")
                    getApplication<Application>().assets.open(bundle.filePath).bufferedReader()
                        .use { it.readText() }
                } else {
                    Log.d("BundleLoad", "Reading user bundle from file: ${bundle.filePath}")
                    File(getApplication<Application>().filesDir, bundle.filePath).readText()
                }
                Log.d("BundleLoad", "JSON string length: ${jsonString.length}")

                val jsonElement = JsonParser.parseString(jsonString)
                val rootObject = jsonElement.asJsonObject
                val exercisesArray = rootObject.getAsJsonArray("allSetups") // Corrected key
                val type = object : TypeToken<List<TimerSetup>>() {}.type
                val loadedSetups: List<TimerSetup> =
                    gson.fromJson(exercisesArray, type) ?: emptyList()
                Log.d("BundleLoad", "Deserialized ${loadedSetups.size} setups from bundle.")

                if (loadedSetups.isEmpty()) {
                    Log.w(
                        "BundleLoad",
                        "Loaded bundle '${bundle.name}' is empty. No exercises to apply."
                    )
                    withContext(Dispatchers.Main) {
                        _isExerciseListDirty.value = false // It's clean (empty)
                        selectedBundle =
                            null // <-- Clear selected bundle as it's an empty load (like an error)
                        Toast.makeText(
                            getApplication(),
                            "Bundle '${bundle.name}' is empty. No exercises loaded.",
                            Toast.LENGTH_SHORT
                        ).show()
                        dismissLoadBundleOptions()
                        dismissReplaceConfirmation()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    when (action) {
                        BundleLoadAction.MERGE -> {
                            val currentNames =
                                _setups.value.map { it.name.trim().lowercase() }.toSet()
                            val newSetupsToAdd = loadedSetups.filter {
                                !currentNames.contains(
                                    it.name.trim().lowercase()
                                )
                            }
                            _setups.value = (_setups.value + newSetupsToAdd)
                            Log.d(
                                "BundleLoad",
                                "Merged bundle '${bundle.name}'. Added ${newSetupsToAdd.size} new exercises."
                            )
                            selectedBundle =
                                null // Clear selected bundle after merge (list is now custom)
                        }

                        BundleLoadAction.REPLACE -> {
                            _setups.value = loadedSetups
                            Log.d(
                                "BundleLoad",
                                "Replaced current exercises with bundle '${bundle.name}'. Total ${loadedSetups.size} exercises."
                            )
                            selectedBundle =
                                bundle // <-- SET selectedBundle here for REPLACE success!
                        }
                    }

                    val firstSetup = _setups.value.firstOrNull()
                    if (firstSetup != null) {
                        applySetup(firstSetup)
                    } else {
                        handleFirstLaunchOrEmptyState(shouldSave = false)
                    }
                    _isExerciseListDirty.value =
                        false // Successfully loaded a bundle, so list is clean.
                    Toast.makeText(
                        getApplication(),
                        "Bundle '${bundle.name}' loaded successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    dismissLoadBundleOptions()
                    dismissReplaceConfirmation()
                }

            } catch (e: Exception) {
                Log.e("BundleLoad", "Failed to load bundle '${bundle.name}': ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to load bundle: ${bundle.name}. Error: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                    selectedBundle = null // Clear selected bundle if loading failed
                    dismissLoadBundleOptions()
                    dismissReplaceConfirmation()
                }
            }
        }
    }

    fun showLoadBundleOptionsOrPerformReplace(bundle: BundleOption) {
        // --- VVV --- THIS IS THE FINAL, CORRECT FIX --- VVV ---
        // Set selectedBundle immediately, as it's the item the user chose.
        selectedBundle = bundle

        if (_setups.value.isEmpty()) {
            // If current list is empty, proceed directly to load/replace without dialog.
            performBundleLoad(bundle, BundleLoadAction.REPLACE)
            // Dialog is not shown, so no need to dismiss.
        } else {
            // If list is not empty, then we DO need to show the options dialog.
            showLoadBundleOptionsDialog = true // This is only set if list is NOT empty.
        }
    }

    // New enum to define the loading actions for bundles
    enum class BundleLoadAction {
        MERGE,
        REPLACE
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
                    bandColorOptions = appState.bandColorOptions.map { SpinnerOption(it) }
                        .ifEmpty { listOf(defaultOption) }
                    weightOptions = appState.weightOptions.map { SpinnerOption(it) }
                        .ifEmpty { listOf(defaultOption) }

                    // 1. Check if the loaded data is from an old file.
                    //    We map over the setups to create a new, corrected list.
                    val migratedSetups = appState.allSetups.map { setup ->
                        // If the new `imageResName` is "none" BUT the old `imageResId` exists...
                        if (setup.config.imageResName == "none" && setup.config.imageResId != 0 && setup.config.imageResId != null) {
                            // ...it means we loaded an old file. Find the correct resourceName from the old ID.
                            val foundImage =
                                imageOptions.find { it.resourceId == setup.config.imageResId }
                            // Create a new config with the correct resourceName.
                            val migratedConfig =
                                setup.config.copy(imageResName = foundImage?.storageName ?: "none")
                            // Return a new setup object with the migrated config.
                            setup.copy(config = migratedConfig)
                        } else {
                            // This setup is already in the new format, so just return it as is.
                            setup
                        }
                    }
                    // 2. Load the *migrated* list of setups into our state.
                    _setups.value = migratedSetups

                    // 3. Now, find and apply the active setup from the corrected list.
                    val setupToLoad = if (migratedSetups.isEmpty()) {
                        // If the list is empty after migration, create a fresh default state in memory.
                        handleFirstLaunchOrEmptyState(shouldSave = false)
                        // Return the `activeSetup` property that was just populated by the function above.
                    } else {
                        // If the list is NOT empty, find the active setup or default to the first one.
                        migratedSetups.find { it.name == appState.activeSetupName }
                            ?: migratedSetups.first()
                    }
                    applySetup(setupToLoad, isInitialLoad = true)
                }
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
            Log.d(
                "SaveAppState",
                "Generated JSON for app state: ${json.take(500)}..."
            ) // Log first 500 chars to avoid truncation
            File(getApplication<Application>().filesDir, appStateFilename).writeText(json)
        } catch (e: Exception) {
            Log.e("SaveAppState", "Error writing app state to file", e)
        }
    }

    private fun handleFirstLaunchOrEmptyState(shouldSave: Boolean = true): TimerSetup {
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
        return initialSetup
    }

    // --- Setup Management Functions (now use saveAppState) ---

    fun addOrUpdateSetup(name: String) {
        if (name.isBlank()) return
        val newConfig = configState.copy(
            imageResName = selectedImage.storageName,
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
        _setups.value = currentList.toList()
        _isExerciseListDirty.value = true
        selectedBundle = null
        applySetup(newOrUpdatedSetup) // Apply the new/updated setup as the active one
    }

    fun deleteSetup(setupName: String) {
        val currentList = _setups.value.toMutableList()
        val initialSize = currentList.size // Capture initial size to know if a change occurred
        currentList.removeAll { it.name.equals(setupName, ignoreCase = true) }

        // Check if any item was actually removed
        val itemWasRemoved = initialSize > currentList.size

        if (currentList.isEmpty()) {
            _setups.value = emptyList()
            selectedBundle = null // Clear selected bundle
            clearAllSetups() // This will also handle setting _isExerciseListDirty
        } else {
            _setups.value = currentList.toList() // Ensure new list instance
            selectedBundle =
                null // Clear selected bundle, as the list no longer matches the original bundle
            if (activeSetupName.equals(setupName, ignoreCase = true)) {
                applySetup(currentList.first())
            } else {
                saveAppState()
            }
            if (itemWasRemoved) { // If an item was removed, the list state is now 'dirty' compared to any loaded bundle.
                _isExerciseListDirty.value = true
            }
        }
    }

    fun clearAllSetups() {
        _setups.value = emptyList()
        _isExerciseListDirty.value = true
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
        selectedBundle = null
        saveAppState() // Persist the now-empty list of setups
    }

    fun applySetup(setup: TimerSetup, isInitialLoad: Boolean = false, isUnsaved: Boolean = false) {
        configState = setup.config
        selectedStartRepSound =
            soundOptions.find { it.resourceName == setup.startRepSound } ?: defaultSound
        selectedStartRestSound =
            soundOptions.find { it.resourceName == setup.startRestSound } ?: defaultSound
        selectedStartSetRestSound =
            soundOptions.find { it.resourceName == setup.startSetRestSound } ?: defaultSound
        selectedCompleteSound =
            soundOptions.find { it.resourceName == setup.completeSound } ?: defaultSound
        selectedGetReadySound =
            soundOptions.find { it.resourceName == setup.getReadySound } ?: defaultSound
        selectedImage =
            imageOptions.find { it.storageName == setup.config.imageResName } ?: defaultImage
        selectedBandColor =
            bandColorOptions.find { it.value == setup.config.bandColor } ?: defaultOption
        selectedWeight = weightOptions.find { it.value == setup.config.weightLbs } ?: defaultOption
        activeSetupName = setup.name
        activeSetup = setup
        currentSetupIndex = _setups.value.indexOfFirst { it.name == setup.name }

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
        if (color.isNotBlank() && color != "N/A" && bandColorOptions.none {
                it.value.equals(
                    color,
                    ignoreCase = true
                )
            }) {
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
        if (weightNum != null && weightOptions.none {
                it.value.equals(
                    weight,
                    ignoreCase = true
                )
            }) {
            val newOption = SpinnerOption(weight)
            // 1. Add the new option to the master list
            weightOptions = (weightOptions + newOption).sortedBy {
                it.value.toDoubleOrNull() ?: Double.MAX_VALUE
            }

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
                bandColorOptions = appState.bandColorOptions.map { SpinnerOption(it) }
                    .ifEmpty { listOf(defaultOption) }
                weightOptions = appState.weightOptions.map { SpinnerOption(it) }
                    .ifEmpty { listOf(defaultOption) }


                // 2. Now that the lists are populated, load the setups.
                _setups.value = appState.allSetups
                _isExerciseListDirty.value

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
            Log.d(
                "SaveToUri",
                "Generated JSON for URI: ${json.take(500)}..."
            ) // Log first 500 chars
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.e("SaveToUri", "Failed to write setups to URI: $uri", e)
        }
    }

    fun getFileName(uri: Uri): String? {
        val contentResolver = getApplication<Application>().contentResolver
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    fun saveUserImage(imageUri: Uri, overwriteConfirmed: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val contentResolver = getApplication<Application>().contentResolver
            val imagesDir = File(getApplication<Application>().filesDir, userImagesDirectory)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs() // Create the directory if it doesn't exist
            }

            // Derive the target display name and filename based on user input
            val originalBaseName = getFileName(imageUri)?.substringBeforeLast('.') ?: "User Image"
            val fileExtension = getFileName(imageUri)?.substringAfterLast('.', "")
            val targetDisplayName = "$originalBaseName (User)" // The desired display name
            val targetStorageFileName = "${
                originalBaseName.replace(
                    ' ',
                    '_'
                )
            }_user.${fileExtension?.ifBlank { "jpg" }}" // Unique, but without timestamp

            val outputFile = File(imagesDir, targetStorageFileName)

            if (outputFile.exists() && !overwriteConfirmed) {
                // File exists and overwrite not confirmed, show dialog
                withContext(Dispatchers.Main) {
                    // Find the existing ImageOption that corresponds to this file name for the dialog
                    val existingImageOption =
                        imageOptions.find { it.storageName == targetStorageFileName }
                    showOverwriteImageConfirmDialog(
                        existingImageOption ?: ImageOption(
                            targetDisplayName,
                            0,
                            targetStorageFileName
                        )
                    )
                }
                return@launch
            }
            performSaveUserImage(imageUri, targetDisplayName, targetStorageFileName)
            try {
                contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val newImageOption = ImageOption(targetDisplayName, 0, targetStorageFileName)
                withContext(Dispatchers.Main) {
                    // Remove old entry if it existed and was overwritten
                    imageOptions =
                        imageOptions.filter { it.storageName != newImageOption.storageName }
                    // Add the new/updated image and sort
                    imageOptions = (imageOptions + newImageOption).sortedBy { it.displayName }
                    selectedImage = newImageOption // Select the newly added/overwritten image
                    configState = configState.copy(
                        imageResName = newImageOption.storageName,
                        imageDisplayName = newImageOption.displayName
                    ) // Update config state
                    _toastMessage.value = "Image '${targetDisplayName}' saved successfully!"
                    // Dismiss dialog if it was showing
                    dismissOverwriteImageConfirmDialog()
                }
            } catch (e: Exception) {
                Log.e("SaveUserImage", "Failed to save user image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Failed to save image: ${e.localizedMessage}"
                    dismissOverwriteImageConfirmDialog()
                }
            }
        }
    }

    // Private helper function to perform the actual saving, used after confirmation
    private suspend fun performSaveUserImage(
        imageUri: Uri,
        targetDisplayName: String,
        targetStorageFileName: String
    ) {
        val contentResolver = getApplication<Application>().contentResolver
        val imagesDir = File(getApplication<Application>().filesDir, userImagesDirectory)
        val outputFile = File(imagesDir, targetStorageFileName)

        try {
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val newImageOption = ImageOption(targetDisplayName, 0, targetStorageFileName)
            withContext(Dispatchers.Main) {
                // Remove old entry if it existed and was overwritten
                imageOptions = imageOptions.filter { it.storageName != newImageOption.storageName }
                // Add the new/updated image and sort
                imageOptions = (imageOptions + newImageOption).sortedBy { it.displayName }
                selectedImage = newImageOption
                configState = configState.copy(
                    imageResName = newImageOption.storageName,
                    imageDisplayName = newImageOption.displayName
                )
                _toastMessage.value = "Image '${targetDisplayName}' saved successfully!"
                dismissOverwriteImageConfirmDialog()
            }
        } catch (e: Exception) {
            Log.e("PerformSaveUserImage", "Failed to save user image: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _toastMessage.value = "Failed to save image: ${e.localizedMessage}"
                dismissOverwriteImageConfirmDialog()
            }
        }
    }

    fun deleteUserImage(imageOption: ImageOption) {
        if (imageOption.resourceId != 0 || !imageOption.displayName.contains("(User)")) {
            _toastMessage.value = "Only user-added images (ending with '(User)') can be deleted."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val imagesDir = File(getApplication<Application>().filesDir, userImagesDirectory)
            val imageFile =
                File(imagesDir, imageOption.storageName) // resourceName stores the unique filename

            if (imageFile.exists()) {
                try {
                    val deleted = imageFile.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            _toastMessage.value = "Image '${imageOption.displayName}' deleted."
                            // Remove from options list
                            imageOptions =
                                imageOptions.filter { it.storageName != imageOption.storageName }
                                    .sortedBy { it.displayName }
                            // If the deleted image was active, revert to defaultImage and update config
                            if (selectedImage.storageName == imageOption.storageName) {
                                selectedImage = defaultImage
                                configState =
                                    configState.copy(imageResName = defaultImage.storageName)
                            }
                            // Update any setups that were using this image
                            _setups.value = _setups.value.map { setup ->
                                if (setup.config.imageResName == imageOption.storageName) {
                                    setup.copy(config = setup.config.copy(imageResName = defaultImage.storageName))
                                } else {
                                    setup
                                }
                            }
                            saveAppState() // Save state after removing image reference from setups
                        } else {
                            _toastMessage.value =
                                "Failed to delete image file: '${imageOption.displayName}'."
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "DeleteUserImage",
                        "Error deleting image '${imageOption.displayName}': ${e.message}",
                        e
                    )
                    withContext(Dispatchers.Main) {
                        _toastMessage.value = "Error deleting image: ${e.localizedMessage}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Image file '${imageOption.displayName}' not found."
                }
            }
        }
    }

    fun saveBundle(bundleName: String) {
        if (bundleName.isBlank()) {
            _toastMessage.value = "Bundle name cannot be empty."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val userBundlesDir = File(getApplication<Application>().filesDir, "user_bundles")
            if (!userBundlesDir.exists()) {
                userBundlesDir.mkdirs() // Create the directory if it doesn't exist
            }

            val filename = "${bundleName.trim()}.json"
            val bundleFile = File(userBundlesDir, filename)

            if (bundleFile.exists()) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value =
                        "Bundle '$bundleName' already exists. Please choose a different name."
                }
                return@launch
            }

            try {
                val currentState = AppState(
                    allSetups = _setups.value,
                    activeSetupName = activeSetup?.name,
                    bandColorOptions = bandColorOptions.map { it.value },
                    weightOptions = weightOptions.map { it.value }
                )
                val json = gson.toJson(currentState)
                bundleFile.writeText(json)

                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Bundle '$bundleName' saved successfully!"
                    // Update bundleOptions to include the new user bundle
                    val newBundleOption = BundleOption(
                        name = bundleName.trim(), // Use plain name for user bundles
                        filePath = "user_bundles/$filename", // Relative path for user bundle
                        isFactory = false
                    )
                    bundleOptions = (bundleOptions + newBundleOption).sortedBy { it.name }
                    selectedBundle = newBundleOption // Make the newly saved bundle the selected one
                    _isExerciseListDirty.value =
                        false // Saving makes the current list "clean" relative to this bundle
                }
            } catch (e: Exception) {
                Log.e("SaveBundle", "Failed to save bundle '$bundleName': ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Failed to save bundle: ${e.localizedMessage}"
                }
            }
        }
    }

    fun deleteUserBundle(bundle: BundleOption) {
        viewModelScope.launch(Dispatchers.IO) { // All suspend calls go inside this scope
            if (bundle.isFactory) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Cannot delete factory bundles."
                }
                return@launch // Return from the coroutine
            }

            val userBundlesDir = File(getApplication<Application>().filesDir, "user_bundles")
            val filename =
                bundle.filePath.substringAfterLast("/") // Get just the filename from the relative path
            val bundleFile = File(userBundlesDir, filename)

            if (bundleFile.exists()) {
                try {
                    val deleted = bundleFile.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            _toastMessage.value = "Bundle '${bundle.name}' deleted successfully!"
                            // Remove from options list
                            bundleOptions = bundleOptions.filter { it.filePath != bundle.filePath }
                                .sortedBy { it.name }
                            // Clear selected bundle if the deleted one was active
                            if (selectedBundle?.filePath == bundle.filePath) {
                                selectedBundle = null
                            }
                            // If _setups was loaded from this bundle, it's now dirty relative to no bundle
                            _isExerciseListDirty.value =
                                true // List is modified relative to any bundle
                        } else {
                            _toastMessage.value = "Failed to delete bundle file: '${bundle.name}'."
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DeleteBundle", "Error deleting bundle '${bundle.name}': ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        _toastMessage.value = "Error deleting bundle: ${e.localizedMessage}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Bundle file '${bundle.name}' not found."
                }
            }
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
                        AppSoundPlayer.playSound(
                            getApplication(),
                            selectedStartRestSound.resourceId
                        )
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
                    AppSoundPlayer.playSound(getApplication(), selectedCompleteSound.resourceId)
                    if (continueToNextExercise && currentSetupIndex != -1 && currentSetupIndex < _setups.value.size - 1) {
                        val nextIndex = currentSetupIndex + 1
                        val nextSetup = _setups.value[nextIndex]

                        // Reset internal timer states for the new exercise
                        currentRepNumber = 1
                        currentSetNumber = 1
                        setMasterClock = 0L // Reset total time mode clock

                        // Apply the next setup and transition to the "Getting Ready" phase for it
                        withContext(Dispatchers.Main) { // Ensure UI updates on main thread
                            applySetup(nextSetup) // This will update activeSetup and currentSetupIndex
                            currentState =
                                TimerState.Ready // Set to Ready, waiting for user to press Start
                            _timerScreenState.update {
                                it.copy(
                                    status = "Ready", // Display Ready status
                                    remainingTime = 0, // Reset timer display
                                    progressDisplay = "Next: ${nextSetup.name}" // Optional: show next exercise name
                                )
                            }
                            // Call stopTimer to clean up the current timerJob, leaving the system in a Ready state.
                            stopTimer() // Cleans up the timerJob, but currentState is already 'Ready'
                            Log.d(
                                "TimerState",
                                "Automatically switched to next exercise: ${nextSetup.name}. Waiting for Start."
                            )
                        }
                    } else {
                        // No auto-continue, or this was the last exercise.
                        _timerScreenState.update {
                            it.copy(
                                status = "Finished!",
                                remainingTime = 0,
                                progressDisplay = ""
                            )
                        }
                        stopTimer() // This will cancel the parent timerJob and exit the loop
                        Log.d("TimerState", "Workout finished.")
                    }
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
        val allImages = mutableListOf<ImageOption>()
        val defaultImage = ImageOption("None", 0, "none")
        allImages.add(defaultImage) // Use the defaultImage object
        R.drawable::class.java.fields
            // The incorrect filter line has been DELETED from here
            .forEach { field ->
                try {
                    if (field.name.startsWith("abc_") ||
                        field.name.startsWith("ic_") ||
                        field.name.startsWith("common_") ||
                        field.name.startsWith("googleg_") ||
                        field.name.startsWith("notification_") ||
                        field.name.contains("$")
                    ) return@forEach

                    val resourceId = field.getInt(null)
                    val displayName =
                        field.name.replace('_', ' ').replaceFirstChar { it.titlecase() }
                    allImages.add(ImageOption(displayName, resourceId, field.name))
                } catch (_: Exception) {
                }
            }
        imageOptions = allImages.sortedBy { it.storageName }
        viewModelScope.launch(Dispatchers.IO) {
            val imagesDir = File(getApplication<Application>().filesDir, userImagesDirectory)
            if (imagesDir.exists() && imagesDir.isDirectory) {
                val userFiles = imagesDir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".jpg", true) || file.name.endsWith(
                        ".png",
                        true
                    ))
                } ?: arrayOf()

                val userImageOptions = userFiles.map { file ->
                    ImageOption(
                        file.nameWithoutExtension.replace('_', ' ')
                        .replaceFirstChar { it.titlecase() } + " (User)", 0, file.name)
                }
                withContext(Dispatchers.Main) {
                    // Add user images to the existing list and re-sort
                    imageOptions = (imageOptions + userImageOptions).sortedBy { it.displayName }
                }
            }
        }
    }

    private fun initializeBundles() {
        val assetManager = getApplication<Application>().assets
        val foundBundles = mutableListOf<BundleOption>()

        try {
            // List files directly in the assets root.
            // Adjust "bundles" or other subfolder name if your JSONs are nested.
            val assetFiles = assetManager.list("") ?: arrayOf() // List all files in assets root
            val factoryBundles = assetFiles.filter { it.endsWith(".json") }.map { filename ->
                // Remove ".json" for a cleaner display name, then add "(factory)"
                val displayName = filename.removeSuffix(".json").replace('_', ' ')
                    .replaceFirstChar { it.uppercase() } + " (factory)"
                BundleOption(
                    name = displayName,
                    filePath = filename, // Path relative to assets root
                    isFactory = true
                )
            }

            foundBundles.addAll(factoryBundles)

            // Load user-created bundles from internal storage
            val userBundlesDir = File(getApplication<Application>().filesDir, "user_bundles")
            if (userBundlesDir.exists() && userBundlesDir.isDirectory) {
                val userFiles =
                    userBundlesDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
                        ?: arrayOf()
                val userBundles = userFiles.map { file ->
                    val displayName = file.nameWithoutExtension.replace('_', ' ')
                        .replaceFirstChar { it.uppercase() }
                    BundleOption(
                        name = displayName,
                        filePath = "user_bundles/${file.name}", // Path relative to filesDir
                        isFactory = false
                    )
                }
                foundBundles.addAll(userBundles)
            }

            bundleOptions = foundBundles.toList() // Update the observable state
        } catch (e: Exception) {
            Log.e("TimerViewModel", "Error listing asset bundles: ${e.message}", e)
            bundleOptions = emptyList() // Ensure list is empty on error
        }
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

