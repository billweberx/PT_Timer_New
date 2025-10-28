package com.billweberx.pt_timer.ui.screens

import android.net.Uri
import android.util.Log
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.billweberx.pt_timer.SoundOption
import com.billweberx.pt_timer.TimerViewModel
import com.billweberx.pt_timer.pressable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: TimerViewModel,
) {
    val loadedSetups by viewModel.loadedSetups.collectAsStateWithLifecycle()
    var isManageSetupsExpanded by remember { mutableStateOf(false) }
    var newSetupName by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { navController.popBackStack() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home Icon"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Home")
            }

            Text("Settings", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.width(68.dp))
        }

        // --- Main Content with Padding ---
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Rows 2-7: Sound Selections ---

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isManageSetupsExpanded = !isManageSetupsExpanded
                    } // Toggle the state on click
                    .padding(vertical = 8.dp),
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
                }
            }

            // --- Rows 9-10: Input Fields ---
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerInputField(
                    "Move To",
                    viewModel.moveToTime,
                    { viewModel.moveToTime = it },
                    true,
                    Modifier.weight(1f)
                )
                TimerInputField(
                    "Exercise",
                    viewModel.exerciseTime,
                    { viewModel.exerciseTime = it },
                    true,
                    Modifier.weight(1f)
                )
                TimerInputField(
                    "Move From",
                    viewModel.moveFromTime,
                    { viewModel.moveFromTime = it },
                    true,
                    Modifier.weight(1f)
                )
                TimerInputField(
                    "Rest",
                    viewModel.restTime,
                    { viewModel.restTime = it },
                    true,
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerInputField(
                    "Sets",
                    viewModel.sets,
                    { viewModel.sets = it },
                    true,
                    Modifier.weight(1f)
                )
                TimerInputField(
                    "Set Rest",
                    viewModel.setRestTime,
                    { viewModel.setRestTime = it },
                    true,
                    Modifier.weight(1f)
                )
                TimerInputField(
                    "Reps",
                    viewModel.reps,
                    { viewModel.reps = it },
                    true,
                    Modifier.weight(1f)
                )
                TimerInputField(
                    "Total Time",
                    viewModel.totalTime,
                    { viewModel.totalTime = it },
                    true,
                    Modifier.weight(1f)
                )
            }

            // --- Row 12: New Setup Name Field ---
            OutlinedTextField(
                value = newSetupName,
                onValueChange = { newSetupName = it },
                label = { Text("New Exercise Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            // Row with the first three buttons, using Surface
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
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
                    modifier = Modifier.Companion.pressable(
                        interactionSource = saveInteractionSource,
                        enabled = saveButtonEnabled,
                        onClick = {
                            // This is more robust. It uses the current ViewModel state.
                            viewModel.addOrUpdateSetup(
                                name = newSetupName,
                                moveToTime = viewModel.moveToTime,
                                exerciseTime = viewModel.exerciseTime,
                                moveFromTime = viewModel.moveFromTime,
                                restTime = viewModel.restTime,
                                sets = viewModel.sets,
                                setRestTime = viewModel.setRestTime,
                                reps = viewModel.reps
                            )
                        }
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
                        onClick = { showClearConfirmDialog = true }
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
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
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
                        onClick = { saveLauncher.launch("PT_Timer_Setups.json") }
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
            Spacer(modifier = Modifier.height(50.dp))
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Confirm Clear") },
            text = { Text("Are you sure you want to delete the entire setup list?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllSetups()
                        newSetupName = ""
                        showClearConfirmDialog = false
                    }
                ) { Text("Yes, Clear All") }
            },
            dismissButton = {
                Button(onClick = { showClearConfirmDialog = false }) { Text("No") }
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
            // --- THE FIX IS HERE ---
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


@Composable
fun TimerInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        singleLine = true,
        enabled = enabled,
        modifier = modifier
    )
}
