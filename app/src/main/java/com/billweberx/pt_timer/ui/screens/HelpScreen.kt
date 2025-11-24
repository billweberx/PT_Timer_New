package com.billweberx.pt_timer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onGoBack: () -> Unit // Lambda to navigate back to the Main screen
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Help",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onGoBack) // Navigate back on click
                            .padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Main")
                    }
                },
                actions = {
                    // A spacer to balance the navigation icon, if desired
                    Spacer(modifier = Modifier.width(68.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        // --- Main content area for Help ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()), // Make content scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Align content to the top
        ) {
            // Spacer below the TopAppBar
            Spacer(modifier = Modifier.height(16.dp))

            // --- Expandable Section: Main Screen Operation ---
            ExpandableHelpSection(
                title = "Main Screen Operation",
                content = {
                    Column(modifier = Modifier.padding(top = 8.dp)) { // Padding for the whole content column
                        Text(
                            text = "Introduction",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This is your main workout screen. It displays your current exercise, " +
                                    "countdown timer, and controls to manage your workout session.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- NEW SECTION: Typical Workout Flow ---
                        Text(
                            text = "Typical Workout Flow",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Here's a typical process for using the PT Timer:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1.  Select an exercise from the 'Manage Setups' list in the Settings screen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "2.  Tap the Start button (▶️) on the Main screen to begin the selected exercise.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "3.  Perform all the sets and reps as guided by the timer. Audio cues will indicate phase changes.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "4.  Once all sets are complete for the current exercise, tap Stop (⏹️).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "5.  Select the next exercise from the 'Manage Setups' list in Settings to continue your workout.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- NEW SECTION: Workout Phases & Terminology ---
                        Text(
                            text = "Workout Phases & Terminology",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Understanding the different phases and settings is key to customizing your workouts. " +
                                    "These are specific to each exercise and configured in the Settings screen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "•   Move to Start: Time allotted to prepare for the first set.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Move To: Offset time to transition into the exercise position.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Exercise: Time allotted to perform the exercise.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Move From: Offset time to transition from the exercise position to the rest position.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Rest: Time allotted to rest between repetitions.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Reps: The number of times an exercise is repeated. If set to '0', reps will be replaced with a 'Total Time' period.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Total Time: If 'Reps' is zero, this is the amount of time to repeat the exercise phase (including MoveTo, Exercise, MoveFrom, Rest).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Sets: The number of times the full exercise cycle (Reps or Total Time) is repeated.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Set Rest: The allotted time between sets as a rest period (take a break).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- EXISTING SECTION: Timer Controls ---
                        Text(
                            text = "Timer Controls",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "•  Start Button (▶️): Tap to begin your workout. The timer will start from the 'Get Ready' phase.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•  Pause/Resume Button (⏸️/▶️): Tap to temporarily halt the timer. Tap again to resume from where you left off.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•  Stop Button (⏹️): Tap to end the current workout session completely and reset the timer.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Display Information",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "•  Exercise Details: Shows the name of the current exercise, followed by Set and Rep counts.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•  Countdown Timer: Displays the remaining time for the current phase (Get Ready, Exercise, Rest, Set Rest).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•  Exercise Image & Instructions: A visual guide and detailed instructions for the active exercise. " +
                                    "Instructions can be expanded/collapsed by tapping on the 'Exercise Instructions' header.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Audio Cues",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Customizable sounds are played automatically to indicate transitions between phases " +
                                    "(e.g., 'Get Ready', 'Start Reps', 'Rest', 'Set Rest', 'Sets Complete').",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Navigation",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use the three-dot menu (⋮) in the top right corner to access 'Settings' (to configure workouts) and 'Help' (this screen).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp)) // Space between sections

            // --- Expandable Section: Settings Screen Instructions ---
            ExpandableHelpSection(
                title = "Settings Screen Instructions",
                content = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Introduction",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The Settings screen allows you to fully customize your workout exercises and manage your saved setups. " +
                                    "Changes made here will be applied to the currently active exercise or saved for future use.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Sound Configuration ---
                        Text(
                            text = "Sound Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This section lets you choose unique audio cues for different phases of your workout. " +
                                    "Tap on each dropdown (Get Ready, Start Reps, Start Rest, Start Set Rest, Sets Complete) " +
                                    "to select your preferred sound. Choose 'None' if you prefer no sound for a specific cue.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Exercise Configuration ---
                        Text(
                            text = "Exercise Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Customize the visual and tracking details for your exercise:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "•   Image: Select a visual representation for your exercise. If your image isn't available, you can choose 'None'.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Band Color / Weight-lbs: Record the resistance used for the exercise. Tap the dropdowns to select an existing option, " +
                                    "or tap '+' to add a new option. Use the trashcan icon to delete custom options (excluding 'N/A').",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Times per Day / Times per Week: Track how often you perform this specific exercise. Input numerical values.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Timer Configuration ---
                        Text(
                            text = "Timer Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adjust the timing and repetition settings for your exercise. " +
                                    "Enter positive numerical values for time fields. If 'Reps' is 0, 'Total Time' will be used.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "•   Move To / Exercise / Move From / Rest / Reps / Total Time / Get Ready / Sets / Set Rest: " +
                                    "Refer to the 'Workout Phases & Terminology' section in 'Main Screen Operation' help for definitions of these fields. " +
                                    "Input your desired durations or counts.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Manage Setups ---
                        Text(
                            text = "Manage Setups",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This powerful section allows you to load, save, organize, import, and export entire collections of exercises.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "•   Load Bundle: Use the dropdown to select pre-defined (factory) or user-created bundles. " +
                                    "You will be asked to 'Merge' (add to current list) or 'Replace' (overwrite current list) your exercises. " +
                                    "Canceling a load or merging will clear the selection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Exercise List: Below the 'Load Bundle' selector, you'll see your current list of saved exercises.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "    •   Select: Tap an exercise name to load its settings into the configuration fields above.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "    •   Move Up/Down (⬆️/⬇️): Reorder exercises in your list.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "    •   Delete (🗑️): Remove a specific exercise from your list. This will clear the bundle selection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   New Exercise Name: Enter a name for a new exercise or to update the active exercise.  " +
                                    "This field is filled with the current exercise name if you tap on it in the Exercise List section. " +
                                    "This way you can edit the current name and save it as a new exercise.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Save Exercise: Saves the current configuration fields (including sound, image, timer settings, instructions) " +
                                    "under the 'New Exercise Name'. If the name already exists, it will update that exercise. This will clear the bundle selection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Clear Setup: Deletes ALL exercises from your list. This will reset the settings to a default 'Unsaved Workout'.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Import: Load exercises from a JSON file stored on your device. This will clear the bundle selection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "•   Export: Save your current exercise list to a JSON file on your device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Exercise Instructions ---
                        Text(
                            text = "Exercise Instructions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This section displays the image and detailed instructions for the currently active exercise. " +
                                    "You can input or modify text notes here. The image displayed corresponds to your 'Image' selection " +
                                    "under 'Exercise Configuration'.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp)) // Spacer at the bottom
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableHelpSection(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        // Remove 'indication = rememberRipple()' to use the default Material 3 ripple
                        // or define a custom Indication if specific customization is needed
                        // For a simple clickable, removing 'indication' often defaults to themed ripple.
                    ) { expanded = !expanded }, // Clicking the header area toggles expansion
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content() // Display the content passed in
                }
            }
        }
    }
}


