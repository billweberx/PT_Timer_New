package com.billweberx.pt_timer // Make sure this line is at the very top

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTimerScreen(
    viewModel: TimerViewModel,
    onGoToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State from ViewModel
    val timerState by viewModel.timerScreenState.collectAsStateWithLifecycle()


    val playSound: (Int) -> Unit = { resourceId ->
        if (resourceId != -1) {
            coroutineScope.launch {
                try {
                    MediaPlayer.create(context, resourceId)?.apply {
                        setOnCompletionListener { it.release() }
                        start()
                    }
                } catch (_: Exception) { /* Handle error */
                }
            }
        }
    }
    // Determine if the timer has valid parameters to start
    val hasReps = (viewModel.reps.toDoubleOrNull()?.toInt() ?: 0) > 0
    val hasSets = (viewModel.sets.toDoubleOrNull()?.toInt() ?: 0) > 0
    val hasTotalTime = (viewModel.totalTime.toDoubleOrNull()?.toInt() ?: 0) > 0

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

        // --- Row 3: Set, Rep, and Countdown Timer ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunning || viewModel.isPaused) {
                Text(
                    text = if (isRunning || viewModel.isPaused) {
                        // If timer is active, calculate the countdown
                        val totalSets = viewModel.sets.toDoubleOrNull()?.toInt() ?: 0
                        val displaySet = (totalSets - timerState.currentSet + 1).coerceAtLeast(1)
                        "Set: $displaySet"
                    } else {
                        // If timer is stopped, show the total sets from the read-only field
                        val totalSets = viewModel.sets.toDoubleOrNull()?.toInt() ?: 0
                        // Only show if the value is valid
                        if (totalSets > 0) "Set: $totalSets" else ""
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = timerState.remainingTime.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            // Placeholder to keep the timer centered, you can add Rep count here if desired
            Spacer(modifier = Modifier.width(40.dp))
        }

        // --- Row 4: Progress Display ---
        if (isRunning || viewModel.isPaused) {
            Text(
                text = timerState.progressDisplay,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            // Start/Pause Button
            Button(
                onClick = {
                    if (isRunning && !viewModel.isPaused) {
                        // If it's running and NOT paused, then we should pause it.
                        viewModel.pauseTimer()
                    } else {
                        // Otherwise (if it's not running, or if it IS running but is paused), we should start/resume it.
                        viewModel.startTimer(playSound)
                    }
                },

                enabled = isStartEnabled || isRunning || viewModel.isPaused,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning && !viewModel.isPaused) Color(0xFFFFF9C4) else Color(
                        0xFFC8E6C9
                    )
                )
            ) {
                if (isRunning && !viewModel.isPaused) {
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
                    viewModel.stopTimer() // Just call the simple stop function
                },
                enabled = isRunning || viewModel.isPaused,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }
        }

        // --- Read-only values Section (NEW 3-ROW LAYOUT) ---
        // First row of read-only fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadOnlyField(label = "Move To", value = viewModel.moveToTime, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Exercise", value = viewModel.exerciseTime, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Move From", value = viewModel.moveFromTime, modifier = Modifier.weight(1f))
        }

        // Second row of read-only fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadOnlyField(label = "Rest", value = viewModel.restTime, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Sets", value = viewModel.sets, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Set Rest", value = viewModel.setRestTime, modifier = Modifier.weight(1f))
        }

        // Third row of read-only fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReadOnlyField(label = "Reps", value = viewModel.reps, modifier = Modifier.weight(1f))
            ReadOnlyField(label = "Total Time", value = viewModel.totalTime, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f)) // Add a spacer to align with the rows above
        }


        // --- Row 8: Setups Spinner Box ---
        ExposedDropdownMenuBox(
            expanded = false, // This is a placeholder, real state is needed
            onExpandedChange = { }
        ) {
            OutlinedTextField(
                value = viewModel.activeSetup?.name ?: "Select a Setup",
                onValueChange = {},
                readOnly = true,
                label = { Text("Setups") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                enabled = !isRunning // Disable while timer is running
            )
            ExposedDropdownMenu(
                expanded = false,
                onDismissRequest = { }
            ) {
                viewModel.loadedSetups.forEach { setup ->
                    DropdownMenuItem(
                        text = { Text(setup.name) },
                        onClick = {
                            viewModel.applySetup(setup)
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
