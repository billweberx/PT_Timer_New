package com.billweberx.pt_timer.ui.screens // Make sure this line is at the very top

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import com.billweberx.pt_timer.TimerViewModel
import com.billweberx.pt_timer.pressable


@SuppressLint("LocalContextResourcesRead", "DiscouragedApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTimerScreen(
    viewModel: TimerViewModel,
    onGoToSettings: () -> Unit,
    onGoToHelp: () -> Unit,
    onGoToAbout: () -> Unit
) {

    // State from ViewModel
    val context = LocalContext.current
    val timerState by viewModel.timerScreenState.collectAsStateWithLifecycle()
    val loadedSetups by viewModel.loadedSetups.collectAsStateWithLifecycle()
    var isSetupDropdownExpanded by remember { mutableStateOf(false) }
    var instructionsExpanded by remember { mutableStateOf(false) }
    var timerConfigExpanded by remember { mutableStateOf(false) }


    // Determine if the timer has valid parameters to start
    val hasReps = (viewModel.configState.reps.toDoubleOrNull()?.toInt() ?: 0) > 0
    val hasSets = (viewModel.configState.sets.toDoubleOrNull()?.toInt() ?: 0) > 0
    val hasTotalTime = (viewModel.configState.totalTime.toDoubleOrNull()?.toInt() ?: 0) > 0

    val isRepsModeValid = hasReps && hasSets
    val isReadyToStart = timerState.status == "Ready"
    val isStartEnabled = isReadyToStart && (isRepsModeValid || hasTotalTime)
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
            Box { // Box to anchor the dropdown menu
                var showMenu by remember { mutableStateOf(false) } // State to control menu visibility

                IconButton(onClick = {
                    showMenu = !showMenu
                }) { // Tapping this button toggles the menu (3 dot icon)
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false } // Close menu when dismissed
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            showMenu = false
                            onGoToSettings() // Call the existing settings navigation
                        }
                    )
                    HorizontalDivider() // Separator line for clarity
                    DropdownMenuItem(
                        text = { Text("Help") },
                        onClick = {
                            showMenu = false
                            onGoToHelp() // Call the existing help navigation
                        }
                    )
                    HorizontalDivider() // Separator line for clarity
                    DropdownMenuItem(
                        text = { Text("About") }, // <-- ADD THIS NEW MENU ITEM
                        onClick = {
                            showMenu = false
                            onGoToAbout() // Call the new about navigation
                        }
                    )
                }
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

// --- CENTER: MAIN COUNTDOWN TIMER (FIXED) ---
            Text(
                text = formatTime(timerState.remainingTime),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                // Center the text within its available space
                modifier = Modifier.weight(1.5f), // Give it more space than the side texts
                textAlign = TextAlign.Center
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
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = if (timerState.isPaused) "Resume" else "Start"
                        )
                    }
                }
            }

            //  Stop Button
            val stopInteractionSource = remember { MutableInteractionSource() }
            val isStopButtonEnabled = isRunning || timerState.isPaused
            Surface(
                shape = CircleShape,
                // The color now also reflects the enabled state
                color = if (isStopButtonEnabled) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.5f
                ),
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
        // --- Row 5: Display Selected Color and Weight ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp), // Add some space around it
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Display Band Color if it's not "N/A"
            if (viewModel.selectedBandColor.value != "N/A") {
                Text(
                    text = "Band: ${viewModel.selectedBandColor.value}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Display Weight if it's not "N/A"
            if (viewModel.selectedWeight.value != "N/A") {
                Text(
                    text = "Weight: ${viewModel.selectedWeight.value} lbs",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
// --- Row 6: Display Frequency ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp), // Space below this new row
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val timesPerDay = viewModel.configState.timesPerDay
            val timesPerWeek = viewModel.configState.timesPerWeek

            // Display Times/Day if it has a meaningful value
            if (timesPerDay.isNotBlank() && timesPerDay != "0") {
                Text(
                    text = "Times/Day: $timesPerDay",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Display Times/Wk if it has a meaningful value
            if (timesPerWeek.isNotBlank() && timesPerWeek != "0") {
                Text(
                    text = "Times/Wk: $timesPerWeek",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        // Row for "Auto Select next exercise" switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Auto Select Next Exercise",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f) // Take up available space
            )
            Switch(
                checked = viewModel.continueToNextExercise, // Bind to ViewModel state
                onCheckedChange = { newValue ->
                    viewModel.continueToNextExercise = newValue
                }
            )
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
                        text = {
                            Text(
                                text = setup.name,
                                modifier = Modifier,
                                style = if (viewModel.activeSetupName == setup.name) {
                                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                }
                            )
                        },
                        onClick = {
                            viewModel.applySetup(setup)
                            isSetupDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Rows 5 - 7: Timer Configuration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    timerConfigExpanded = !timerConfigExpanded
                } // Toggle the state on click
                .padding(vertical = 8.dp),
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
                contentDescription = if (timerConfigExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(if (timerConfigExpanded) 180f else 0f) // Animate rotation
            )
        }
        // 2. The animated text field
        AnimatedVisibility(visible = timerConfigExpanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadOnlyField(
                        label = "Move To",
                        value = viewModel.configState.moveToTime,
                        modifier = Modifier.weight(1f)
                    )
                    ReadOnlyField(
                        label = "Exercise",
                        value = viewModel.configState.exerciseTime,
                        modifier = Modifier.weight(1f)
                    )
                    ReadOnlyField(
                        label = "Move From",
                        value = viewModel.configState.moveFromTime,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadOnlyField(
                        label = "Rest",
                        value = viewModel.configState.restTime,
                        modifier = Modifier.weight(1f)
                    )
                    ReadOnlyField(
                        label = "Reps",
                        value = viewModel.configState.reps,
                        modifier = Modifier.weight(1f)
                    )
                    ReadOnlyField(
                        label = "Total Time",
                        value = viewModel.configState.totalTime,
                        modifier = Modifier.weight(1f)
                    )

                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReadOnlyField(
                        label = "Get Ready!",
                        value = viewModel.configState.getReadyTime,
                        modifier = Modifier.weight(1f)
                    )
                    ReadOnlyField(
                        label = "Sets",
                        value = viewModel.configState.sets,
                        modifier = Modifier.weight(1f)
                    )
                    ReadOnlyField(
                        label = "Set Rest",
                        value = viewModel.configState.setRestTime,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Row 9: Instructions ---
        if (viewModel.configState.instructions.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        instructionsExpanded = !instructionsExpanded
                    } // Toggle the state on click
                    .padding(vertical = 8.dp),
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
                    contentDescription = if (instructionsExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (instructionsExpanded) 180f else 0f) // Animate rotation
                )
            }
            //2. The animated content (Image and Text Field)
            AnimatedVisibility(visible = instructionsExpanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Adds space between items
                ) {
                    // 1. Display the image FIRST, if it exists.
                    val imageResourceId = context.resources.getIdentifier(
                        viewModel.selectedImage.storageName, // The resource name (e.g., "dowel_assisted_overhead_reach")
                        "drawable", // The type of resource
                        context.packageName // The package name
                    )

                    // ONLY display the Image composable if a valid (non-zero) resource ID is found.
                    if (imageResourceId != 0) {
                        val painter = painterResource(id = imageResourceId)
                        Image(
                            painter = painter,
                            contentDescription = "Exercise Image: ${viewModel.selectedImage.displayName}", // Use selectedImage.name for accurate content description
                            modifier = Modifier
                                .fillMaxWidth() // Force the width to match the screen.
                                .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height), // Force height based on aspect ratio.
                            contentScale = ContentScale.FillWidth // Ensure it scales correctly to the new bounds.
                        )
                    }
                    // 2. Display the instructions text field SECOND.
                    OutlinedTextField(
                        value = viewModel.configState.instructions,
                        onValueChange = {}, // Empty lambda makes it read-only
                        readOnly = true,
                        label = { Text("Instructions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp) // Padding at the bottom
                    )
                }  // column
            }  // AnimatedVisibility block
        }
    }  // <-- This is the closing brace of the main Column
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
// In PTTimerScreen.kt

// ... (end of your PTTimerScreen composable function)
// } // This brace closes the PTTimerScreen function

// --- ADD THIS NEW FUNCTION HERE ---
private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0.0"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    // For times under a minute, show seconds and tenths of a second
    return if (totalSeconds < 60) {
        val tenths = (millis % 1000) / 100
        String.format(Locale.US, "%d.%d", seconds, tenths)
    } else {
        // For times a minute or over, show MM:SS
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

