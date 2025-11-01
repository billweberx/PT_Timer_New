package com.billweberx.pt_timer.ui.screens // Make sure this line is at the very top

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.billweberx.pt_timer.TimerViewModel
import com.billweberx.pt_timer.pressable


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTimerScreen(
    viewModel: TimerViewModel,
    onGoToSettings: () -> Unit
) {

    // State from ViewModel
    val timerState by viewModel.timerScreenState.collectAsStateWithLifecycle()
    val loadedSetups by viewModel.loadedSetups.collectAsStateWithLifecycle()
    var isSetupDropdownExpanded by remember { mutableStateOf(false) }

    // Determine if the timer has valid parameters to start
    val hasReps = (viewModel.configState.reps.toDoubleOrNull()?.toInt() ?: 0) > 0
    val hasSets = (viewModel.configState.sets.toDoubleOrNull()?.toInt() ?: 0) > 0
    val hasTotalTime = (viewModel.configState.totalTime.toDoubleOrNull()?.toInt() ?: 0) > 0

    val isRepsModeValid = hasReps && hasSets
    val isTimeModeValid = hasTotalTime
    val isReadyToStart = timerState.status == "Ready"
    val isStartEnabled = isReadyToStart && (isRepsModeValid || isTimeModeValid)
    val isRunning by remember { derivedStateOf { timerState.status != "Ready" && timerState.status != "Finished" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // --- Row 1: App Title and Settings Icon ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PT Timer", style = MaterialTheme.typography.titleLarge)
            Text("Home", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onGoToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // --- Row 2: Phase Status ---
        Text(timerState.status, style = MaterialTheme.typography.headlineMedium)

        // --- Row 3: Set, Rep, and Countdown Timer (FIXED) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LEFT SIDE: SETS DISPLAY (RESTORED) ---
            Text(
                text = if (isRunning || timerState.isPaused) {
                    val totalSets = viewModel.configState.sets.toIntOrNull() ?: 0
                    // Display sets remaining instead of a countdown
                    "Set: ${timerState.currentSet}/$totalSets"
                } else {
                    val totalSets = viewModel.configState.sets.toIntOrNull() ?: 0
                    if (totalSets > 0) "Sets: $totalSets" else ""
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            // --- CENTER: MAIN COUNTDOWN TIMER (UNCHANGED) ---
            Text(
                text = timerState.remainingTime.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )

            // --- RIGHT SIDE: REPS OR TIME DISPLAY (CORRECT LOGIC) ---
            Text(
                text = if (hasReps) {
                    // Reps Mode: Show the rep counter
                    if (isRunning || timerState.isPaused) {
                        val totalReps = viewModel.configState.reps.toIntOrNull() ?: 0
                        "Rep: ${timerState.currentRep}/$totalReps"
                    } else {
                        val totalReps = viewModel.configState.reps.toIntOrNull() ?: 0
                        if (totalReps > 0) "Reps: $totalReps" else ""
                    }
                } else {
                    // Total Time Mode: Show the progressDisplay text from the ViewModel
                    timerState.progressDisplay
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        // Row 4: Start/Pause and Stop Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            //  Start/Pause Button
            val startInteractionSource = remember { MutableInteractionSource() }
            val isStartButtonEnabled = isStartEnabled || isRunning || timerState.isPaused
            Surface(
                shape = CircleShape,
                // The color now also reflects the enabled state
                color = if (isStartButtonEnabled) {
                    if (isRunning && !timerState.isPaused) Color(0xFFFFF9C4) else Color(0xFFC8E6C9)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // A disabled look
                },
                contentColor = if (isStartButtonEnabled) Color.Black else Color.Gray,
                modifier = Modifier
                    .size(64.dp)
                    .pressable(
                        interactionSource = startInteractionSource,
                        enabled = isStartButtonEnabled, // <-- Pass the enabled state here
                        onClick = {
                            // No if-check needed here anymore!
                            if (isRunning && !timerState.isPaused) {
                                viewModel.pauseTimer()
                            } else {
                                if (timerState.isPaused) {
                                    viewModel.resumeTimer()
                                } else {
                                    viewModel.startTimer()
                                }
                            }
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRunning && !timerState.isPaused) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = if (timerState.isPaused) "Resume" else "Start")
                    }
                }
            }

            //  Stop Button
            val stopInteractionSource = remember { MutableInteractionSource() }
            val isStopButtonEnabled = isRunning || timerState.isPaused
            Surface(
                shape = CircleShape,
                // The color now also reflects the enabled state
                color = if (isStopButtonEnabled) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (isStopButtonEnabled) Color.Black else Color.Gray,
                modifier = Modifier
                    .size(64.dp)
                    .pressable(
                        interactionSource = stopInteractionSource,
                        enabled = isStopButtonEnabled, // <-- Pass the enabled state here
                        onClick = {
                            // No if-check needed here anymore!
                            viewModel.stopTimer()
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }


        }
        // Rows 5 - 7: Timer Configuration

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadOnlyField(label = "Move To", value = viewModel.configState.moveToTime, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Exercise", value = viewModel.configState.exerciseTime, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Move From", value = viewModel.configState.moveFromTime, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadOnlyField(label = "Rest", value = viewModel.configState.restTime, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Sets", value = viewModel.configState.sets, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Set Rest", value = viewModel.configState.setRestTime, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadOnlyField(label = "Reps", value = viewModel.configState.reps, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Total Time", value = viewModel.configState.totalTime, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
        // Row 8:  the Setups dropdown
        ExposedDropdownMenuBox(
            expanded = isSetupDropdownExpanded,
            onExpandedChange = {
                // Only allow opening the menu if the timer is not running
                if (!isRunning) {
                    isSetupDropdownExpanded = !isSetupDropdownExpanded
                }
            }
        ) {
            OutlinedTextField(
                value = viewModel.activeSetup?.name ?: "Select a Setup",
                onValueChange = {},
                readOnly = true,
                label = { Text("Setups") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSetupDropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        // Pass the enabled state to the new modifier
                        enabled = !isRunning
                    )
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isSetupDropdownExpanded,
                onDismissRequest = { isSetupDropdownExpanded = false }
            ) {
                // The DropdownMenuItems for your setups go here...
                // No changes are needed inside this menu.
                loadedSetups.forEach { setup ->
                    DropdownMenuItem(
                        text = { Text(setup.name) },
                        onClick = {
                            viewModel.applySetup(setup)
                            isSetupDropdownExpanded = false
                        }
                    )
                }
            }
        }

    }
}






// Helper composable for the read-only display fields
@Composable
fun ReadOnlyField(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = modifier,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        singleLine = true
    )
}
