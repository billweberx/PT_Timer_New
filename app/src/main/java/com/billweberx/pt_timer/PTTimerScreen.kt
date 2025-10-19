// In file: app/src/main/java/com/billweberx/pt_timer/PTTimerScreen.kt

package com.billweberx.pt_timer

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Data class for the main timer screen's reactive state
data class TimerScreenState(
    val status: String = "Ready",
    val remainingTime: Int = 0,
    val setsRemaining: Int = 0,
    val progressDisplay: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTimerScreen(
    viewModel: TimerViewModel,
    onGoToSettings: () -> Unit,
    onSaveSetup: (TimerSetup) -> Unit,
    onDeleteSetup: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State from ViewModel
    val timerState by viewModel.timerScreenState.collectAsStateWithLifecycle()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val isRunning by remember { derivedStateOf { timerJob?.isActive == true } }

    var newSetupName by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    fun playSound(resourceId: Int) {
        if (resourceId != -1) {
            try {
                MediaPlayer.create(context, resourceId)?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            } catch (_: Exception) { /* Handle error */
            }
        }
    }

    fun timerCoroutine(): Job = coroutineScope.launch {
        try {
            viewModel.runTimer { soundId -> playSound(soundId) }
        } finally {
            // This 'finally' block ensures that no matter how the coroutine
            // finishes (completes normally or is cancelled), we nullify the job.
            // This is the key to re-enabling the UI.
            timerJob = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // --- Row 1: App Title and Settings Icon ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PT Timer", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onGoToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        // --- Row 2: Progress Display ---
        if (isRunning && timerState.progressDisplay.isNotBlank()) {
            Text(
                text = timerState.progressDisplay,
                style = MaterialTheme.typography.bodyMedium, // Slightly smaller than the phase status
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        // --- Row 3: Phase Status ---
        Text(timerState.status, style = MaterialTheme.typography.headlineMedium)

        // --- Row 4: Remaining Time ---
        Text(timerState.remainingTime.toString(), style = MaterialTheme.typography.displayLarge)

        // --- Row 5: Delay, Exercise, Rest Time ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerInputField(
                "Delay",
                viewModel.delayTime,
                { viewModel.delayTime = it },
                !isRunning,
                Modifier.weight(1f)
            )
            TimerInputField(
                "Exercise",
                viewModel.exerciseTime,
                { viewModel.exerciseTime = it },
                !isRunning,
                Modifier.weight(1f)
            )
            TimerInputField(
                "Rest",
                viewModel.restTime,
                { viewModel.restTime = it },
                !isRunning,
                Modifier.weight(1f)
            )
        }

        // --- Row 6: Sets and Total Time ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerInputField(
                "Sets",
                viewModel.sets,
                { viewModel.sets = it },
                !isRunning,
                Modifier.weight(1f)
            )
            TimerInputField(
                "Total Time",
                viewModel.totalTime,
                { viewModel.totalTime = it },
                !isRunning,
                Modifier.weight(1f)
            )
        }

        // --- Row 7: Start/Pause and Stop Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            // Start/Pause Button
            Button(
                onClick = {
                    if (isRunning) { // If running, pause it
                        viewModel.pauseTimer()
                        timerJob?.cancel()
                        timerJob = null
                    } else { // If not running, start it
                        timerJob = timerCoroutine()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(
                        0xFFFFF9C4
                    ) else Color(0xFFC8E6C9)
                ) // Yellow if running, Green if not
            ) {
                if (isRunning) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = if (viewModel.isPaused) "Resume" else "Start"
                    )
                }
            }
            // Stop Button
            Button(
                onClick = {
                    timerJob?.cancel()
                    timerJob = null
                    viewModel.stopTimer()
                },
                enabled = isRunning || viewModel.isPaused,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)) // Light Red
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }
        }

        // --- Row 8: Setups Spinner Box ---
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
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
                            newSetupName = setup.name
                            expanded = false
                        }
                    )
                }
            }
        }

        // --- Row 9: New Setup Name Text Box ---
        OutlinedTextField(
            value = newSetupName,
            onValueChange = { newSetupName = it },
            label = { Text("New Setup Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // --- Row 10: Save, Delete, and Clear Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically // Good practice to align items in a row
        ) {
            // "Save Exercise" Button
            Button(
                onClick = {
                    val setupToSave = TimerSetup(
                        name = newSetupName,
                        config = SetupConfig(
                            exerciseTime = viewModel.exerciseTime,
                            restTime = viewModel.restTime,
                            sets = viewModel.sets,
                            totalTime = viewModel.totalTime,
                            delayTime = viewModel.delayTime
                        )
                    )
                    onSaveSetup(setupToSave)
                },
                enabled = newSetupName.isNotBlank()
            ) {
                // Use \n for a newline and center the text
                Text("Save\nExercise", textAlign = TextAlign.Center)
            }

            // "Delete Exercise" Button
            Button(
                onClick = {
                    viewModel.activeSetup?.name?.let {
                        onDeleteSetup(it)
                        newSetupName = "" // Clear name field after deleting
                    }
                },
                enabled = viewModel.activeSetup != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)) // Light Red
            ) {
                Text("Delete\nExercise", textAlign = TextAlign.Center)
            }

            // --- "Clear Setup" BUTTON ---
            Button(
                onClick = {
                    showClearConfirmDialog = true
                },
                enabled = viewModel.loadedSetups.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)) // Light Red
            ) {
                Text("Clear\nSetup", textAlign = TextAlign.Center)
            }
        }


    }

    // --- ADD THE CONFIRMATION DIALOG LOGIC HERE ---
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                // This is called when the user clicks outside the dialog.
                showClearConfirmDialog = false
            },
            title = {
                Text(text = "Confirm Clear")
            },
            text = {
                Text("Are you sure you want to delete the entire setup?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllSetups()
                        newSetupName = ""
                        showClearConfirmDialog = false // Close the dialog
                    }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false // Close the dialog
                    }) {
                    Text("No")
                }
            }
        )
    }

}

// Helper composable for the input text fields
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
