package com.billweberx.pt_timer // <-- ADD THIS LINE

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

// In file: app/src/main/java/com/billweberx/pt_timer/SetupScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: TimerViewModel,
    onImport: (String) -> Unit,
    onExport: () -> String
) {
    // Local state for the "New Exercise Name" field and the confirmation dialog
    var newSetupName by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // --- Activity Result Launchers for Import/Export ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                val json = onExport()
                navController.context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(json.toByteArray())
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                navController.context.contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.reader().readText()
                    onImport(json)
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Add padding at the top to create space below the system status bar
            .padding(top = 40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Use a clickable Row for a custom "Icon + Text" button without a background
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { navController.popBackStack() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home Icon" // Accessibility for the icon
                )
                Spacer(modifier = Modifier.width(4.dp)) // A small space between icon and text
                Text(text = "Home")
            }

            Text("Settings", style = MaterialTheme.typography.titleLarge)

            // A spacer on the right to balance the layout and keep "Settings" centered
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
            Text("Manage Setups", style = MaterialTheme.typography.titleMedium)

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

            // --- Row 11: Setups Spinner ---
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = viewModel.activeSetup?.name ?: "Select a Setup",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Setups") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    viewModel.loadedSetups.forEach { setup ->
                        DropdownMenuItem(
                            text = { Text(setup.name) },
                            onClick = {
                                viewModel.applySetup(setup)
                                newSetupName = setup.name // Populate name field for easy editing
                                expanded = false
                            }
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

            // --- Row 13: Save, Delete, Clear Buttons ---
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                Button(
                    onClick = {
                        val setupToSave = TimerSetup(
                            name = newSetupName,
                            config = SetupConfig(
                                moveToTime = viewModel.moveToTime,
                                exerciseTime = viewModel.exerciseTime,
                                moveFromTime = viewModel.moveFromTime,
                                restTime = viewModel.restTime,
                                sets = viewModel.sets,
                                setRestTime = viewModel.setRestTime,
                                reps = viewModel.reps,
                                totalTime = viewModel.totalTime
                            )
                        )
                        viewModel.addOrUpdateSetup(setupToSave)
                    },
                    enabled = newSetupName.isNotBlank()
                ) {
                    Text("Save\nExercise", textAlign = TextAlign.Center)
                }

                Button(
                    onClick = {
                        viewModel.activeSetup?.name?.let {
                            viewModel.deleteSetup(it)
                            newSetupName = "" // Clear name field after deleting
                        }
                    },
                    enabled = viewModel.activeSetup != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))
                ) {
                    Text("Delete\nExercise", textAlign = TextAlign.Center)
                }

                Button(
                    onClick = { showClearConfirmDialog = true },
                    enabled = viewModel.loadedSetups.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))
                ) {
                    Text("Clear\nSetup", textAlign = TextAlign.Center)
                }
            }

            // --- Row 14: Import/Export Buttons ---
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                Button(onClick = { importLauncher.launch("application/json") }) {
                    Text("Import")
                }
                Button(onClick = { exportLauncher.launch("PT_Timer_Setups.json") }) {
                    Text("Export")
                }
            }
        }
    }

    // --- Confirmation Dialog for Clearing Setups ---
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

// --- Re-usable Composables for this screen ---
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
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

// This is the same TimerInputField from the old PTTimerScreen, now used here.
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