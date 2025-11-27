package com.billweberx.pt_timer.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.res.painterResource
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

import androidx.compose.material3.ButtonDefaults
import com.billweberx.pt_timer.data.BundleOption

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
                viewModel.saveSetupsToUri(context, it)
            }
        }
    )
    Scaffold(
        topBar = {
            // --- 2. MOVE: The top bar content is now here, fixed at the top ---
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
                        onSoundSelected = { viewModel.selectedGetReadySound = it }
                    )
                    SoundDropdown(
                        label = "Start Reps",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedStartRepSound,
                        onSoundSelected = { viewModel.selectedStartRepSound = it }
                    )
                    SoundDropdown(
                        label = "Start Rest",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedStartRestSound,
                        onSoundSelected = { viewModel.selectedStartRestSound = it }
                    )
                    SoundDropdown(
                        label = "Start Set Rest",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedStartSetRestSound,
                        onSoundSelected = { viewModel.selectedStartSetRestSound = it }
                    )
                    SoundDropdown(
                        label = "Sets Complete",
                        soundOptions = viewModel.soundOptions,
                        selectedSound = viewModel.selectedCompleteSound,
                        onSoundSelected = { viewModel.selectedCompleteSound = it }
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
                                viewModel.configState.copy(imageResName = newSelection.resourceName)
                        }
                    )
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
            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                    // Dynamically get the drawable ID from the resourceName.
                    val imageResourceId = context.resources.getIdentifier(
                        viewModel.selectedImage.resourceName, // The resource name (e.g., "dowel_assisted_overhead_reach")
                        "drawable", // The type of resource
                        context.packageName // The package name
                    )

                    // ONLY display the Image composable if a valid (non-zero) resource ID is found.
                    if (imageResourceId != 0) {
                        val painter = painterResource(id = imageResourceId)
                        Image(
                            painter = painter,
                            contentDescription = "Exercise Image: ${viewModel.selectedImage.name}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
                            contentScale = ContentScale.FillWidth
                        )
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
                }
            }
        }
    }
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
                    viewModel.selectedBundle = null // <-- Clear selected bundle on explicit cancel
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
}

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
    modifier: Modifier = Modifier
) {
    val (isExpanded, setExpanded) = remember { mutableStateOf(false) }
    // var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { setExpanded(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption.name,
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
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onOptionSelected(option)
                        setExpanded(false)
                    }
                )
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

