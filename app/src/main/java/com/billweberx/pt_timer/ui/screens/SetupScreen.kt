package com.billweberx.pt_timer.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown // For the down arrow
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch as coroutineLaunch
import com.billweberx.pt_timer.SoundOption
import com.billweberx.pt_timer.TimerViewModel
import com.billweberx.pt_timer.data.ImageOption
import com.billweberx.pt_timer.data.SpinnerOption
import com.billweberx.pt_timer.pressable
import com.billweberx.pt_timer.util.AppSoundPlayer

import androidx.compose.material3.ButtonDefaults
import com.billweberx.pt_timer.data.BundleOption
import java.io.File
import java.util.Locale

@SuppressLint("LocalContextResourcesRead", "DiscouragedApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onGoBack: () -> Unit,
    viewModel: TimerViewModel,
) {
    val loadedSetups by viewModel.loadedSetups.collectAsStateWithLifecycle()
    var isManageSetupsExpanded by remember { mutableStateOf(false) }
    var newSetupName by remember { mutableStateOf("") }
    val (showClearConfirmDialog, setShowClearConfirmDialog) = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val toastMessage by viewModel.toastMessage.collectAsState()
    val exerciseTimeFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var isTimerConfigExpanded by remember { mutableStateOf(false) }
    var isSoundConfigExpanded by remember { mutableStateOf(false) }
    var isExerciseConfigExpanded by remember { mutableStateOf(false) }
    var isExerciseInstructionsExpanded by remember { mutableStateOf(false) }
    val (showDeleteBundleConfirmDialog, setShowDeleteBundleConfirmDialog) = remember {
        mutableStateOf(
            false
        )
    }
    val (showClearLogConfirmDialog, setShowClearLogConfirmDialog) = remember { mutableStateOf(false) }
    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            // Notify the ViewModel that the message has been shown to prevent it from re-appearing
            viewModel.onToastShown()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                Log.d("Import", "User cancelled file picker.")
                return@rememberLauncherForActivityResult
            }

            try {
                // Use the ContentResolver to robustly read the file's content into a string.
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }

                if (!jsonString.isNullOrBlank()) {
                    // Pass the clean, valid JSON string to the ViewModel.
                    viewModel.importSetupsFromJson(jsonString)
                } else {
                    Log.e("Import", "Selected file is empty or could not be read.")
                }
            } catch (e: Exception) {
                // If anything goes wrong, log the error instead of crashing the app.
                Log.e("Import", "Failed to read or parse file from URI: $uri", e)
            }
        }
    )
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                // Use the context we defined above
                scope.coroutineLaunch { // Use the scope from SetupScreen
                    viewModel.saveSetupsToUri(context, it)
                }
            }
        }
    )

    var imageUriToSave by remember { mutableStateOf<Uri?>(null) } // Stores the URI that triggered the save
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                viewModel.saveUserImage(it) // Call ViewModel to save the selected image
            } ?: Log.d("ImagePicker", "User cancelled image picker.")
        }
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onGoBack) // Use the passed-in lambda
                            .padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Home")
                    }
                },
                actions = {
                    // This is a good place for actions, but we can leave a spacer to balance the navigation icon
                    Spacer(modifier = Modifier.width(68.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->

        // --- Main Content with Padding ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // <-- 1. APPLY the padding from the Scaffold
                .verticalScroll(rememberScrollState()) // <-- 2. MAKE the column scrollable
                .padding(horizontal = 16.dp), // You can keep your horizontal padding
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- VVV --- NEW: Gym/PT Mode Selector --- VVV ---
            val isGymModeSelected by viewModel.isGymMode.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.setGymMode(false) }
                ) {
                    RadioButton(
                        selected = !isGymModeSelected,
                        onClick = { viewModel.setGymMode(false) }
                    )
                    Text("PT Mode")
                }
                Spacer(Modifier.width(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.setGymMode(true) }
                ) {
                    RadioButton(
                        selected = isGymModeSelected,
                        onClick = { viewModel.setGymMode(true) }
                    )
                    Text("Gym Mode")
                }
            }
            // --- End NEW: Gym/PT Mode Selector ---
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Add a divider for separation

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isSoundConfigExpanded = !isSoundConfigExpanded
                    } // Toggle the state on click
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sound Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f) // Take up available space
                )
                // This icon will rotate based on the expanded state
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isSoundConfigExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (isSoundConfigExpanded) 180f else 0f) // Animate rotation
                )
            }
            AnimatedVisibility(visible = isSoundConfigExpanded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // --- Rows 2-7: Sound Selections ---
                    SoundDropdown(
                        label = "Get Ready",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedGetReadySound,
                        onSoundSelected = { newSoundOption -> // <-- UPDATED LAMBDA
                            viewModel.selectedGetReadySound = newSoundOption
                            if (newSoundOption.resourceId != -1) { // Play sound if not "None"
                                AppSoundPlayer.playSound(context, newSoundOption.resourceId)
                            }
                        }
                    )
                    SoundDropdown(
                        label = "Start Reps",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedStartRepSound,
                        onSoundSelected = { newSoundOption -> // <-- UPDATED LAMBDA
                            viewModel.selectedStartRepSound = newSoundOption
                            if (newSoundOption.resourceId != -1) {
                                AppSoundPlayer.playSound(context, newSoundOption.resourceId)
                            }
                        }
                    )
                    SoundDropdown(
                        label = "Start Rest",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedStartRestSound,
                        onSoundSelected = { newSoundOption -> // <-- UPDATED LAMBDA
                            viewModel.selectedStartRestSound = newSoundOption
                            if (newSoundOption.resourceId != -1) {
                                AppSoundPlayer.playSound(context, newSoundOption.resourceId)
                            }
                        }
                    )
                    SoundDropdown(
                        label = "Start Set Rest",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedStartSetRestSound,
                        onSoundSelected = { newSoundOption -> // <-- UPDATED LAMBDA
                            viewModel.selectedStartSetRestSound = newSoundOption
                            if (newSoundOption.resourceId != -1) {
                                AppSoundPlayer.playSound(context, newSoundOption.resourceId)
                            }
                        }
                    )
                    SoundDropdown(
                        label = "Sets Complete",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedCompleteSound,
                        onSoundSelected = { newSoundOption -> // <-- UPDATED LAMBDA
                            viewModel.selectedCompleteSound = newSoundOption
                            if (newSoundOption.resourceId != -1) {
                                AppSoundPlayer.playSound(context, newSoundOption.resourceId)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isExerciseConfigExpanded = !isExerciseConfigExpanded
                    } // Toggle the state on click
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercise Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f) // Take up available space
                )
                // This icon will rotate based on the expanded state
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExerciseConfigExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (isExerciseConfigExpanded) 180f else 0f) // Animate rotation
                )
            }
            AnimatedVisibility(visible = isExerciseConfigExpanded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    ImageDropdown(
                        label = "Image",
                        options = viewModel.imageOptions,
                        selectedOption = viewModel.selectedImage,
                        onOptionSelected = { newSelection ->
                            //1. Update the UI state so the spinner shows the new selection
                            viewModel.selectedImage = newSelection
                            // 2. Update the central configState with the resourceName so the change will be saved
                            viewModel.configState =
                                viewModel.configState.copy(imageResName = newSelection.storageName)
                        },
                        onDeleteUserImage = { imageOption -> // <-- NEW PARAMETER
                            viewModel.deleteUserImage(imageOption)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp)) // Space above button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val addImageInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = addImageInteractionSource,
                                onClick = { imagePickerLauncher.launch("image/*") } // Launch image picker
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Add Image")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // Space below button
                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    //
                    // ---  Band Color and Weight Selections ---
                    //
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        EditableDropdown(
                            label = "Band Color",
                            options = viewModel.bandColorOptions,
                            selectedOption = viewModel.selectedBandColor,
                            onOptionSelected = { newSelection ->
                                // 1. Update the UI state so the spinner shows the new selection
                                viewModel.selectedBandColor = newSelection
                                // 2. Update the central configState so the change will be saved
                                viewModel.configState =
                                    viewModel.configState.copy(bandColor = newSelection.value)
                            },
                            onAddOption = { viewModel.addBandColorOption(it) },
                            onDeleteOption = { viewModel.deleteBandColorOption(it) },
                            modifier = Modifier.weight(1f)
                        )
                        //
                        // Weight-lbs
                        //
                        EditableDropdown(
                            label = "Weight-lbs",
                            options = viewModel.weightOptions,
                            selectedOption = viewModel.selectedWeight,
                            onOptionSelected = { newSelection ->
                                // 1. Update the UI state
                                viewModel.selectedWeight = newSelection
                                // 2. Update the central configState so the change will be saved
                                viewModel.configState =
                                    viewModel.configState.copy(weightLbs = newSelection.value)
                            },
                            onAddOption = {
                                viewModel.addWeightOption(it)
                                viewModel.selectedWeight = SpinnerOption(it)
                            },
                            onDeleteOption = { viewModel.deleteWeightOption(it) },
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // --- Times per day and Times per week settings ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Times per Day
                        OutlinedTextField(
                            value = viewModel.configState.timesPerDay,
                            onValueChange = { newValue ->
                                // Allow only digits and ensure it's a positive integer if not empty
                                if (newValue.all { it.isDigit() }) {
                                    val num = newValue.toIntOrNull()
                                    if (num == null || num > 0) { // Allow empty or positive
                                        viewModel.onConfigChange(
                                            viewModel.configState.copy(
                                                timesPerDay = newValue
                                            )
                                        )
                                    }
                                }
                            },
                            label = { Text("Times per Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        // Times per Week
                        OutlinedTextField(
                            value = viewModel.configState.timesPerWeek,
                            onValueChange = { newValue ->
                                // Allow only digits
                                if (newValue.all { it.isDigit() }) {
                                    val num = newValue.toIntOrNull()
                                    // Allow empty string, or numbers between 1 and 7
                                    if (newValue.isEmpty() || (num != null && num in 1..7)) {
                                        viewModel.onConfigChange(
                                            viewModel.configState.copy(
                                                timesPerWeek = newValue
                                            )
                                        )
                                    }
                                }
                            },
                            label = { Text("Times per Week") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- Rows 9-10: Input Fields ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isTimerConfigExpanded = !isTimerConfigExpanded
                    } // Toggle the state on click
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timer Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f) // Take up available space
                )
                // This icon will rotate based on the expanded state
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isTimerConfigExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (isTimerConfigExpanded) 180f else 0f) // Animate rotation
                )
            }
            AnimatedVisibility(visible = isTimerConfigExpanded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Corrected "Move To" call
                        TimerInputField(
                            label = "Move To",
                            textValue = viewModel.configState.moveToTime,
                            onTextChange = { newTextValue ->
                                // This regex allows for a valid decimal number (e.g., "30", "30.5", "30.")
                                if (newTextValue.matches(Regex("^\\d*\\.?\\d?$"))) {
                                    viewModel.onConfigChange(viewModel.configState.copy(moveToTime = newTextValue))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimerInputField(
                            label = "Exercise",
                            textValue = viewModel.configState.exerciseTime,
                            onTextChange = { newTextValue ->
                                if (newTextValue.matches(Regex("^\\d*\\.?\\d?$"))) {
                                    viewModel.onConfigChange(viewModel.configState.copy(exerciseTime = newTextValue))
                                }
                            },
                            focusRequester = exerciseTimeFocusRequester, // Link the requester
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        // When we lose focus, validate.
                                        if (!viewModel.validateExerciseTime()) {
                                            // If validation fails, launch a coroutine on the correct scope.
                                            // This is now unambiguous and will work correctly.
                                            scope.coroutineLaunch {
                                                exerciseTimeFocusRequester.requestFocus()
                                            }
                                        }
                                    }
                                }
                        )
                        TimerInputField(
                            label = "Move From",
                            textValue = viewModel.configState.moveFromTime,
                            onTextChange = { newTextValue ->
                                if (newTextValue.matches(Regex("^\\d*\\.?\\d?$"))) {
                                    viewModel.onConfigChange(viewModel.configState.copy(moveFromTime = newTextValue))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimerInputField(
                            label = "Rest",
                            textValue = viewModel.configState.restTime,
                            onTextChange = { newTextValue ->
                                if (newTextValue.matches(Regex("^\\d*\\.?\\d?$"))) {
                                    viewModel.onConfigChange(viewModel.configState.copy(restTime = newTextValue))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )


                        TimerInputField(
                            label = "Reps",
                            textValue = viewModel.configState.reps,
                            onTextChange = { newTextValue ->
                                viewModel.onConfigChange(viewModel.configState.copy(reps = newTextValue))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimerInputField(
                            label = "Total Time",
                            textValue = viewModel.configState.totalTime,
                            onTextChange = { newTextValue ->
                                if (newTextValue.matches(Regex("^\\d*\\.?\\d?$"))) {
                                    viewModel.onConfigChange(viewModel.configState.copy(totalTime = newTextValue))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimerInputField(
                            label = "Get Ready",
                            textValue = viewModel.configState.getReadyTime,
                            onTextChange = { newTextValue ->
                                viewModel.onConfigChange(viewModel.configState.copy(getReadyTime = newTextValue))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimerInputField(
                            label = "Sets",
                            textValue = viewModel.configState.sets,
                            onTextChange = { newTextValue ->
                                viewModel.onConfigChange(viewModel.configState.copy(sets = newTextValue))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimerInputField(
                            label = "Set Rest",
                            textValue = viewModel.configState.setRestTime,
                            onTextChange = { newTextValue ->
                                if (newTextValue.matches(Regex("^\\d*\\.?\\d?$"))) {
                                    viewModel.onConfigChange(viewModel.configState.copy(setRestTime = newTextValue))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            //
            // ---  Manage Setups ---
            //
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isManageSetupsExpanded = !isManageSetupsExpanded
                    } // Toggle the state on click
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manage Setups",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f) // Take up available space
                )
                // This icon will rotate based on the expanded state
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isManageSetupsExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (isManageSetupsExpanded) 180f else 0f) // Animate rotation
                )
            }
            AnimatedVisibility(visible = isManageSetupsExpanded) {
                // Date Range Fields
                var startDateText by remember { mutableStateOf("") }
                var endDateText by remember { mutableStateOf("") }
                val focusManager = LocalFocusManager.current
                var isStartDateError by remember { mutableStateOf(false) }
                var startDateErrorMessage by remember { mutableStateOf("") }
                var isEndDateError by remember { mutableStateOf(false) }
                var endDateErrorMessage by remember { mutableStateOf("") }
                val validateDate: (String) -> String? = { dateString ->
                    if (dateString.isBlank()) {
                        null // Blank is allowed, means no filter
                    } else {
                        val regex = Regex("^\\d{2}-\\d{2}-\\d{4}$") // MM-DD-YYYY
                        if (!regex.matches(dateString)) {
                            "Invalid format. Use MM-DD-YYYY."
                        } else {
                            try {
                                val parts = dateString.split("-")
                                val month = parts[0].toInt()
                                val day = parts[1].toInt()
                                val year = parts[2].toInt()

                                if (month !in 1..12) {
                                    "Invalid month (MM must be 01-12)."
                                } else if (day !in 1..31) { // Basic check
                                    "Invalid day (DD must be 01-31)."
                                } else if (year !in 1900..2100) { // Reasonable year range
                                    "Invalid year."
                                } else {
                                    // Further check for valid day in month (e.g., Feb 30)
                                    java.text.SimpleDateFormat("MM-dd-yyyy", Locale.US).apply {
                                        isLenient = false
                                    }.parse(dateString) // This will throw ParseException for invalid dates like Feb 30
                                    null // Valid date
                                }
                            } catch (_: Exception) {
                                "Invalid date."
                            }
                        }
                    }
                }
                val exportLogLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/csv"),
                    onResult = { uri: Uri? ->
                        uri?.let {
                            scope.coroutineLaunch {
                                // These variables (startDateText, endDateText) are now in scope
                                viewModel.exportWorkoutLogToCsv(context, it, startDateText, endDateText)
                            }
                        }
                    }
                )

                Column(modifier = Modifier.padding(vertical = 8.dp)) {

                    //
                    // Bundle Selector
                    //
                    ExposedDropdownMenuBox(
                        expanded = viewModel.isBundleDropdownExpanded,
                        onExpandedChange = {
                            viewModel.isBundleDropdownExpanded = it
                        }, // Always allow expanding/collapsing
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = viewModel.selectedBundle?.name ?: "Select a Bundle",
                            onValueChange = { /* Read-only */ },
                            readOnly = true,
                            label = { Text("Load Bundle") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.isBundleDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true // <-- ALWAYS ENABLE THE DROPDOWN
                                )
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = viewModel.isBundleDropdownExpanded,
                            onDismissRequest = { viewModel.isBundleDropdownExpanded = false }
                        ) {
                            viewModel.bundleOptions.forEach { bundle ->
                                DropdownMenuItem(
                                    text = { Text(bundle.name) },
                                    onClick = {
                                        viewModel.isBundleDropdownExpanded =
                                            false // Close dropdown first
                                        viewModel.showLoadBundleOptionsOrPerformReplace(bundle) // This will set selectedBundle
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))


                    if (loadedSetups.isEmpty()) {
                        Text(
                            "No saved exercises yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                    loadedSetups.forEachIndexed { index, setup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Text showing the setup name
                            Text(
                                text = setup.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { // Allow clicking the name to load it
                                        viewModel.applySetup(setup)
                                        newSetupName = setup.name
                                    },
                                style = if (viewModel.activeSetupName == setup.name) {
                                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                }
                            )
                            // "Move Up" Button
                            IconButton(
                                onClick = { viewModel.moveSetupUp(setup) },
                                enabled = index > 0 // Disable if it's the first item
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up"
                                )
                            }

                            // "Move Down" Button
                            IconButton(
                                onClick = { viewModel.moveSetupDown(setup) },
                                enabled = index < loadedSetups.size - 1 // Disable if it's the last item
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down"
                                )
                            }

                            // "Delete" Button for this specific item
                            IconButton(onClick = {
                                viewModel.deleteSetup(setup.name)
                                // If the deleted setup was the one in the text field, clear it
                                if (newSetupName == setup.name) {
                                    newSetupName = ""
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete ${setup.name}",
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                    // --- Row 12: New Setup Name Field ---
                    OutlinedTextField(
                        value = newSetupName,
                        onValueChange = { newSetupName = it },
                        label = { Text("New Exercise Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Input field for new bundle name
                    var newBundleName by remember { mutableStateOf("") } // Local state for the bundle name
                    OutlinedTextField(
                        value = newBundleName,
                        onValueChange = { newBundleName = it },
                        label = { Text("Save As Bundle Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Bundle Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val saveBundleInteractionSource = remember { MutableInteractionSource() }
                        val saveBundleButtonEnabled =
                            newBundleName.isNotBlank() && loadedSetups.isNotEmpty()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (saveBundleButtonEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (saveBundleButtonEnabled) MaterialTheme.colorScheme.onSecondary else Color.Gray,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = saveBundleInteractionSource,
                                enabled = saveBundleButtonEnabled,
                                onClick = {
                                    viewModel.saveBundle(newBundleName) // Call ViewModel to save
                                    newBundleName = "" // Clear input field after saving
                                }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Save Bundle")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Delete Bundle Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val deleteBundleInteractionSource = remember { MutableInteractionSource() }
                        // Enable only if a user-created bundle is currently selected
                        val deleteBundleButtonEnabled =
                            viewModel.selectedBundle != null && !viewModel.selectedBundle!!.isFactory
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (deleteBundleButtonEnabled) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (deleteBundleButtonEnabled) Color.White else Color.Gray,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = deleteBundleInteractionSource,
                                enabled = deleteBundleButtonEnabled,
                                onClick = {
                                    setShowDeleteBundleConfirmDialog(true)
                                } // Show confirm dialog on click
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Delete Bundle")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Row with the first Two buttons, using Surface
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // --- Save Exercise Button ---
                        val saveInteractionSource = remember { MutableInteractionSource() }
                        val saveButtonEnabled = newSetupName.isNotBlank()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (saveButtonEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (saveButtonEnabled) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = saveInteractionSource,
                                enabled = saveButtonEnabled,
                                onClick = { viewModel.addOrUpdateSetup(name = newSetupName) }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text("Save\nExercise", textAlign = TextAlign.Center)
                            }
                        }

                        // --- Clear Setup Button ---
                        val clearInteractionSource = remember { MutableInteractionSource() }
                        val clearButtonEnabled = loadedSetups.isNotEmpty()
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = if (clearButtonEnabled) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (clearButtonEnabled) Color.Black else Color.Gray,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = clearInteractionSource,
                                enabled = clearButtonEnabled,
                                onClick = { setShowClearConfirmDialog(true) }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text("Clear\nSetup", textAlign = TextAlign.Center)
                            }
                        }
                    }

                    // --- Row 14: Import/Export Buttons ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        // --- Import Button ---
                        val importInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = importInteractionSource,
                                onClick = { importLauncher.launch("application/json") }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Import")
                            }
                        }

                        // --- Export Button ---
                        val exportInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = exportInteractionSource,
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "Tap a filename once or type a new name to save. Avoid long-press on existing files.",
                                        Toast.LENGTH_LONG // <-- CHANGED TO LENGTH_LONG
                                    ).show()
                                    saveLauncher.launch("PT_Timer_Setups.json")
                                }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text("Export")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // --- VVV --- NEW: Workout Log Export Section --- VVV ---
                    Spacer(modifier = Modifier.height(20.dp)) // Space above new section
                    Text(
                        text = "Workout Log Export",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startDateText,
                            onValueChange = { newValue ->
                                // Allow only digits and hyphens, and ensure it follows MM-DD-YYYY basic structure
                                if (newValue.matches(Regex("[0-9-]*"))) {
                                    startDateText = newValue
                                    // Clear error immediately on change
                                    isStartDateError = false
                                    startDateErrorMessage = ""
                                }
                            },
                            label = { Text("Start Date (MM-DD-YYYY)") },
                            singleLine = true,
                            isError = isStartDateError, // <-- NEW: Link to error state
                            supportingText = { // <-- NEW: Display error message
                                if (isStartDateError) {
                                    Text(startDateErrorMessage, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState -> // <-- NEW: Validate on focus loss
                                    if (!focusState.isFocused) {
                                        val error = validateDate(startDateText)
                                        isStartDateError = error != null
                                        startDateErrorMessage = error ?: ""
                                    }
                                }
                        )
                        OutlinedTextField(
                            value = endDateText,
                            onValueChange = { newValue ->
                                if (newValue.matches(Regex("[0-9-]*"))) {
                                    endDateText = newValue
                                    isEndDateError = false
                                    endDateErrorMessage = ""
                                }
                            },
                            label = { Text("End Date (MM-DD-YYYY)") },
                            singleLine = true,
                            isError = isEndDateError,
                            supportingText = {
                                if (isEndDateError) {
                                    Text(endDateErrorMessage, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        val error = validateDate(endDateText)
                                        isEndDateError = error != null
                                        endDateErrorMessage = error ?: ""
                                    }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- VVV --- NEW: Export Log and Clear Log Buttons (Centered) --- VVV ---
                    Row(
                        modifier = Modifier.fillMaxWidth(), // Fill width to allow centering its content
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), // Center the two buttons
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Export Log Button
                        val exportLogInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = exportLogInteractionSource,
                                onClick = {
                                    val startError = validateDate(startDateText)
                                    val endError = validateDate(endDateText)

                                    isStartDateError = startError != null
                                    startDateErrorMessage = startError ?: ""
                                    isEndDateError = endError != null
                                    endDateErrorMessage = endError ?: ""

                                    if (!isStartDateError && !isEndDateError) {
                                        Toast.makeText(context, "Select a filename to save the log.", Toast.LENGTH_LONG).show()
                                        exportLogLauncher.launch("PT_Timer_WorkoutLog_${startDateText}_${endDateText}.csv")
                                    } else {
                                        Toast.makeText(context, "Please fix date format errors.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp) // Slightly less padding for two buttons
                            ) {
                                Text("Export Log")
                            }
                        }

                        val logEntries by viewModel.workoutLog.collectAsStateWithLifecycle()
                        val clearLogButtonEnabled = logEntries.isNotEmpty()
                        val clearLogInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (clearLogButtonEnabled) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (clearLogButtonEnabled) Color.White else Color.Gray,
                            tonalElevation = 2.dp,
                            modifier = Modifier.pressable(
                                interactionSource = clearLogInteractionSource,
                                enabled = clearLogButtonEnabled,
                                onClick = { setShowClearLogConfirmDialog(true) } // Show confirmation dialog
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text("Clear Log")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp)) // Space below the buttons
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // --- ^^^ --- End NEW: Workout Log Export Section --- ^^^ ---
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            //
            // Exercise Instructions
            //

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isExerciseInstructionsExpanded = !isExerciseInstructionsExpanded
                    } // Toggle the state on click
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercise Instructions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f) // Take up available space
                )
                // This icon will rotate based on the expanded state
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExerciseInstructionsExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (isExerciseInstructionsExpanded) 180f else 0f) // Animate rotation
                )
            }
            AnimatedVisibility(visible = isExerciseInstructionsExpanded) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {

                    val context = LocalContext.current // Obtain the context within the Composable
                    val selectedImageOption = viewModel.selectedImage

                    // Use AsyncImage for both drawable and user-added images for consistency and GIF support.
                    if (selectedImageOption.storageName != "none") {
                        val imageModel = if (selectedImageOption.resourceId != 0) {
                            // It's a factory (drawable) image
                            selectedImageOption.resourceId // Coil can load from R.drawable.id
                        } else {
                            // It's a user-added image (resourceId == 0)
                            File(
                                context.filesDir,
                                "${viewModel.userImagesDirectory}/${selectedImageOption.storageName}"
                            )
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel) // Load from R.drawable or File
                                .crossfade(true)
                                .allowHardware(false)
                                .build(),
                            contentDescription = "Exercise Image: ${selectedImageOption.displayName}",
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                            //     error = painterResource(id = android.R.drawable.ic_menu_gallery) // Fallback for loading errors
                        )
                    } else {
                        // selectedImageOption.storageName == "none", do not display an image
                        Text("No image selected.", color = Color.Gray)
                    }



                    OutlinedTextField(
                        value = viewModel.configState.instructions,
                        onValueChange = { newText ->// Create a copy of the current config with the new text
                            val newConfig = viewModel.configState.copy(instructions = newText)
                            // Call the handler in the ViewModel
                            viewModel.onConfigChange(newConfig)
                        },
                        label = { Text("Exercise Instructions") },
                        placeholder = { Text("Enter any notes for this exercise...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(50.dp))
                }  // end of column
            }  // end of AnimatedVisibility
        }  // end of column
        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { setShowClearConfirmDialog(false) },
                title = { Text("Confirm Clear") },
                text = { Text("Are you sure you want to delete the entire setup list?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllSetups()
                            newSetupName = ""
                            setShowClearConfirmDialog(false)
                        }
                    ) { Text("Yes, Clear All") }
                },
                dismissButton = {
                    Button(onClick = { setShowClearConfirmDialog(false) }) { Text("No") }
                }
            )
        }
        //
        // Load Bundle Options Dialog (Merge or Replace)
        //
        if (viewModel.showLoadBundleOptionsDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissLoadBundleOptions() },
                title = { Text("Load Bundle") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Instruction text
                        Text(
                            text = "Choose how to load '${viewModel.selectedBundle?.name ?: "Selected Bundle"}':", // <-- CHANGED TO selectedBundle
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Merge Option Button
                        Button(
                            onClick = {
                                // --- VVV --- THIS IS THE FINAL, CORRECT FIX --- VVV ---
                                // Pass the selected bundle (which is guaranteed non-null when this dialog is shown)
                                viewModel.performBundleLoad(
                                    viewModel.selectedBundle!!,
                                    TimerViewModel.BundleLoadAction.MERGE
                                )
                                // --- ^^^ --- END OF THE FIX --- ^^^ ---
                            },
                            modifier = Modifier.fillMaxWidth(0.8f) // Make buttons take 80% width
                        ) {
                            Text("Merge (Add to Current)")
                        }

                        // Replace Option Button
                        Button(
                            onClick = {
                                viewModel.dismissLoadBundleOptions() // Dismiss this dialog first
                                viewModel.showReplaceConfirmation() // Show confirmation dialog next
                            },
                            modifier = Modifier.fillMaxWidth(0.8f), // Make buttons take 80% width
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer, // Visually distinguish "Replace"
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Replace (Clear Current)")
                        }
                    }
                },
                confirmButton = { // Only the Cancel button is in the dedicated button slot
                    TextButton(onClick = {
                        viewModel.selectedBundle =
                            null // <-- Clear selected bundle on explicit cancel
                        viewModel.dismissLoadBundleOptions()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        //
        // Replace Confirmation Dialog
        //
        if (viewModel.showReplaceConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissReplaceConfirmation() },
                title = { Text("Confirm Replace") },
                text = { Text("Are you sure you want to replace all current exercises? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.performBundleLoad(
                            viewModel.selectedBundle!!,
                            TimerViewModel.BundleLoadAction.REPLACE
                        )
                        viewModel.dismissReplaceConfirmation()
                    }) {
                        Text("Replace")
                    }
                },
                dismissButton = { // Using dismissButton for cancel
                    TextButton(onClick = {
                        viewModel.selectedBundle = null // Clear selected bundle on explicit cancel
                        viewModel.dismissLoadBundleOptions()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        DeleteBundleConfirmationDialog(
            showDialog = showDeleteBundleConfirmDialog,
            onDismiss = { setShowDeleteBundleConfirmDialog(false) },
            onConfirm = { bundleToDelete ->
                viewModel.deleteUserBundle(bundleToDelete)
                // If the deleted bundle was the selected one, clear the spinner
                if (viewModel.selectedBundle?.filePath == bundleToDelete.filePath) {
                    viewModel.selectedBundle = null
                }
            },
            selectedBundle = viewModel.selectedBundle // Pass the currently selected bundle from ViewModel
        )

        ImageOverwriteConfirmationDialog(
            showDialog = viewModel.showOverwriteImageConfirmDialog.collectAsState().value != null,
            onDismiss = { viewModel.dismissOverwriteImageConfirmDialog() },
            onConfirm = { originalUri, _ ->
                viewModel.saveUserImage(
                    originalUri,
                    overwriteConfirmed = true
                ) // Re-attempt save with overwrite flag
            },
            imageToOverwrite = viewModel.showOverwriteImageConfirmDialog.collectAsState().value,
            originalImageUri = imageUriToSave // Pass the original URI
        )
        if (showClearLogConfirmDialog) {
            AlertDialog(
                onDismissRequest = { setShowClearLogConfirmDialog(false) },
                title = { Text("Confirm Clear Log") },
                text = { Text("Are you sure you want to delete ALL workout log entries? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearWorkoutLog() // Call ViewModel to clear the log
                        setShowClearLogConfirmDialog(false)
                        Toast.makeText(context, "Workout log cleared!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Yes, Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { setShowClearLogConfirmDialog(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }  // end of innerPadding lambda
}  // end of SetupScreen composable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteBundleConfirmationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BundleOption) -> Unit, // Takes the BundleOption to be deleted
    selectedBundle: BundleOption?
) {
    if (showDialog && selectedBundle != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Confirm Delete Bundle") },
            text = { Text("Are you sure you want to delete the bundle '${selectedBundle.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(selectedBundle) // Pass the bundle to the confirm action
                    onDismiss() // Dismiss the dialog after action
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageOverwriteConfirmationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Uri, ImageOption) -> Unit, // New callback: passes the original URI and the target ImageOption
    imageToOverwrite: ImageOption?, // The ImageOption to overwrite
    originalImageUri: Uri? // The URI of the image the user is currently trying to save
) {
    if (showDialog && imageToOverwrite != null && originalImageUri != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Confirm Overwrite Image") },
            text = { Text("An image named '${imageToOverwrite.displayName}' already exists. Do you want to replace it?") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(
                        originalImageUri,
                        imageToOverwrite
                    ) // Confirm overwrite with original URI
                    onDismiss()
                }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDropdown(
    label: String,
    soundOptions: List<SoundOption>,
    selectedSound: SoundOption,
    onSoundSelected: (SoundOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var (expanded, setExpanded) = remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { setExpanded(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSound.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true // Sound dropdowns can always be changed
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            soundOptions.forEach { sound ->
                DropdownMenuItem(
                    text = { Text(sound.displayName) },
                    onClick = {
                        onSoundSelected(sound)
                        expanded = false
                    }
                )
            }
        }
    }
}
// In a new file, or at the bottom of SetupScreen.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDropdown(
    label: String,
    options: List<ImageOption>,
    selectedOption: ImageOption,
    onOptionSelected: (ImageOption) -> Unit,
    onDeleteUserImage: (ImageOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val (isExpanded, setExpanded) = remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { setExpanded(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { setExpanded(false) }
        ) {
            options.forEach { option ->
                Row( // Wrap DropdownMenuItem and Icon in a Row
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onOptionSelected(option)
                            setExpanded(false)
                        },
                        modifier = Modifier.weight(1f) // Make text take available space
                    )
                    // Only show delete icon for user-added images (identified by "(User)" in name)
                    if (option.displayName.contains(" (User)")) { // Check for (User) tag
                        IconButton(
                            onClick = {
                                onDeleteUserImage(option)
                                setExpanded(false) // Close dropdown after deletion
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete ${option.displayName}",
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TimerInputField(
    label: String,
    textValue: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier, // Moved to be the first optional parameter
    onFocusChange: () -> Unit = {}, // Provide a default empty lambda
    focusRequester: FocusRequester? = null,
    isNumeric: Boolean = true
) {
    val keyboardType =
        if (isNumeric) KeyboardType.Decimal else KeyboardType.Text // Corrected to KeyboardType.Decimal
    val focusManager = LocalFocusManager.current

    // This modifier chain is the core of the change ---
    var finalModifier = modifier
        .onFocusChanged { focusState ->
            // If focus is lost, call the handler
            if (!focusState.isFocused) {
                onFocusChange()
            }
        }
    if (focusRequester != null) {
        // Chain the focusRequester if it was provided
        finalModifier = finalModifier.focusRequester(focusRequester)
    }
    // ----------------------------------------------------

    OutlinedTextField(
        value = textValue,
        onValueChange = onTextChange,
        modifier = finalModifier, // Use the new modifier with focus handling
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
        }),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
    )
}

