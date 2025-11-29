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

            ExpandableHelpSection(
                title = "Read this first...",
                content = {
                    Column(modifier = Modifier.padding(top = 8.dp)) { // Padding for the whole content column
                        newTitleLine("Introduction")
                        newTextLine(
                            "Please make note that you should not take the example exercises provided in this app " +
                                    "as doctor or physical therapy advice.  They are provided as a template for you to use " +
                                    "as a starting point for your own workouts.  Your workouts should be directed by your doctor and " +
                                    "physical therapist.  Always take their advice over any that is provided here.  " +
                                    "These examples are actual physical therapy exercises prescribed for post rotator cuff tear " +
                                    "surgery.  Additional exercises were performed at a physical therapy facility that are are not " +
                                    "all included.  Please use these exercises as a starting point for your own workouts."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        newTitleLine("Example Workout Setup")
                        newTextLine(
                            "Many times the settings for one exercise will be similar to another.  It is not unusual " +
                                    "to repeat the settings with some minor changes.  An example for creating your own exercise is as follows: "
                        )
                        newTextLine("1.  Go to the Settings screen. ")
                        newTextLine("2.  Tap 'Manage Setups'. ")
                        newTextLine(
                            "3.  If you want to start with some of the provided exercise examples, tap 'Select a Bundle'.  " +
                                    "If not, tap the 'Clear Setup' button to start with a blank slate."
                        )
                        newTextLine("4.  If you chose to select a bundle, tap one of the choices in the dropdown list. ")
                        newTextLine(
                            "   a) Review each exercise in the list that is shown and tap the delete icon next" +
                                    "      to any that you do not want to include in your workout."
                        )
                        newTextLine("   b) Tap on the first exercise that remains.")
                        newTextLine(
                            "   c) Most likely you will not want to change the 'Sound Configuration'.  " +
                                    "If you do, review the 'Settings Screen Instructions' section."
                        )
                        newTextLine(
                            "   d) Tap the 'Exercise Configuration' drop down.  Unless there is an appropriate " +
                                    "image for your exercise, choose 'None' for Image.  If you wish to add an image to the list, " + "" +
                                    "see the 'Settings Screen Instructions' for instructions.  If you are using bands, tap the " +
                                    "'Band Color' dropdown and select the color you are using.  If it is not an available " +
                                    "choice, type your color and tap 'Add'.  If you are using weights, tap the 'Weight' dropdown " +
                                    "and select the weight you are using.  If the weight choice is not available, type your weight " +
                                    "and tap 'Add'.  Enter the number of times you perform the exercise per day and week.  " +
                                    "Note that the selections in this section have no impact on the timer.  It's only for your reference."
                        )
                        newTextLine(
                            "   e) Tap the 'Timer Configuration'.  This section may take some trial and error to get right.  " +
                                    "Think about your exercise movements.  How long will it take to go from the starting position to the " +
                                    "final position?  That is the amount to enter into the 'Move To' field.  How long to hold the final position?  " +
                                    "That is the amount to enter into the 'Exercise' field.  How long to move to the start (rest) position?  " +
                                    "That is the amount to enter into the 'Move From' field. How long do you want to rest.  Enter that amount " +
                                    "in the 'Rest' field.  Now enter the number of repetitions in the 'Reps' field.  " +
                                    "If you are not counting reps, you can put a zero in the 'Reps' field and enter the total time.  Total time " +
                                    "replaces Reps with time.  Enter the amount of time needed to complete all the reps in one set.  " +
                                    "The 'Get Ready' field is the amount of time to wait before starting the workout.  " +
                                    "It gives you time to get set up.  Make your best guess.  Enter the number " +
                                    "of sets in the 'Sets' field.  This will be the number of times you repeat all the reps or the total time blocks.  " +
                                    "Enter the amount of time to wait between sets in the 'Set Rest' field."
                        )
                        newTextLine(
                            "   f) Tap 'Manage Setups'.  If you previously cleared all the exercises, Enter your exercise name " +
                                    "in the 'New Exercise Name' field and tap 'Save'.  If you are editing an exercise, the name should still  " +
                                    "be in the 'New Exercise Name' field.  If not, enter the new name.  Tap 'Save' to save the changes. "
                        )
                        newTextLine(
                            "   g) Repeat all the steps above for as many times as you have exercises.  When you are finished, " +
                                    "you can export your collection of exercises to a JSON file and save it to your device.  Do this by  " +
                                    "tapping on the 'Export' button, navigate to your folder choice, enter a filename, and tap 'Save'. " +
                                    "Later when you want to retrieve your collection of exercises, you can import them with the 'Import' button."
                        )
                        newTextLine(
                            "   h) Rather than exporting the exercise group, you can save it as a bundle.  It's stored in local app memory.  " +
                                    "The only issue here is local memory can be deleted if you reinstall the app or clear it's storage space.  " +
                                    "It's convenient to use bundles, but you may want to back them up periodically using the Export feature." +
                                    "To save the exercise group in a bundle, Enter a bundle name in the 'Save As Bundle Name' field and tap 'Save Bundle'.  " +
                                    "Now your bundle name will show up in the bundle dropdown where it can be selected for future use."
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Expandable Section: Main Screen Operation ---
            ExpandableHelpSection(
                title = "Main Screen Operation",
                content = {
                    Column(modifier = Modifier.padding(top = 8.dp)) { // Padding for the whole content column
                        newTitleLine("Introduction")
                        newTextLine(
                            "This is your main workout screen. It displays your current exercise, " +
                                    "countdown timer, and controls to manage your workout session."
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- NEW SECTION: Typical Workout Flow ---
                        newTitleLine("Typical Workout Flow")
                        newTextLine("Here's a typical process for using the PT Timer:")
                        newTextLine("1.  Select an exercise from the 'Manage Setups' list in the Settings screen.")
                        newTextLine("2.  Tap the Start button (▶️) on the Main screen to begin the selected exercise.")
                        newTextLine("3.  Perform all the sets and reps as guided by the timer. Audio cues will indicate phase changes.")
                        newTextLine("4.  Once all sets are complete for the current exercise, tap Stop (⏹️).")
                        newTextLine(
                            "5.  Select the next exercise from the 'Manage Setups' list in Settings to continue your workout.  " +
                                    "If you previously switched the 'Auto Select Next Exercise', then the next exercise will already " +
                                    "be showing.  Just press 'Start' to begin."
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- NEW SECTION: Workout Phases & Terminology ---
                        newTitleLine("Workout Phases & Terminology")
                        newTextLine(
                            "Understanding the different phases and settings is key to customizing your workouts. " +
                                    "These are specific to each exercise and configured in the Settings screen."
                        )
                        newTextLine("•   Move to Start: Time allotted to prepare for the first set.")
                        newTextLine("•   Move To: Offset time to transition into the exercise position.")
                        newTextLine("•   Exercise: Time allotted to perform the exercise.")
                        newTextLine("•   Move From: Offset time to transition from the exercise position to the rest position.")
                        newTextLine("•   Rest: Time allotted to rest between repetitions.")
                        newTextLine("•   Reps: The number of times an exercise is repeated. If set to '0', reps will be replaced with a 'Total Time' period.")
                        newTextLine("•   Total Time: If 'Reps' is zero, this is the amount of time to repeat the exercise phase (including MoveTo, Exercise, MoveFrom, Rest).")
                        newTextLine("•   Sets: The number of times the full exercise cycle (Reps or Total Time) is repeated.")
                        newTextLine("•   Set Rest: The allotted time between sets as a rest period (take a break).")
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- EXISTING SECTION: Timer Controls ---
                        newTitleLine("Timer Controls")
                        newTextLine("•  Start Button (▶️): Tap to begin your workout. The timer will start from the 'Get Ready' phase.")
                        newTextLine("•  Pause/Resume Button (⏸️/▶️): Tap to temporarily halt the timer. Tap again to resume from where you left off.")
                        newTextLine("•  Stop Button (⏹️): Tap to end the current workout session completely and reset the timer.")
                        Spacer(modifier = Modifier.height(16.dp))
                        newTitleLine("Display Information")
                        newTextLine("•  Exercise Details: Shows the name of the current exercise, followed by Set and Rep counts.")
                        newTextLine("•  Countdown Timer: Displays the remaining time for the current phase (Get Ready, Exercise, Rest, Set Rest).")
                        newTextLine(
                            "•  Exercise Image & Instructions: A visual guide and detailed instructions for the active exercise. " +
                                    "Instructions can be expanded/collapsed by tapping on the 'Exercise Instructions' header."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        newTitleLine("Audio Cues")
                        newTextLine(
                            "Customizable sounds are played automatically to indicate transitions between phases " +
                                    "(e.g., 'Get Ready', 'Start Reps', 'Rest', 'Set Rest', 'Sets Complete')."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        newTitleLine("Navigation")
                        newTextLine("Use the three-dot menu (⋮) in the top right corner to access 'Settings' (to configure workouts) and 'Help' (this screen).")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp)) // Space between sections

            // --- Expandable Section: Settings Screen Instructions ---
            ExpandableHelpSection(
                title = "Settings Screen Instructions",
                content = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        newTitleLine("Introduction")
                        newTextLine(
                            "The Settings screen allows you to fully customize your workout exercises and manage your saved setups. " +
                                    "Changes made here will be applied to the currently active exercise or saved for future use."
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Sound Configuration ---
                        newTitleLine("Sound Configuration")
                        newTextLine(
                            "This section lets you choose unique audio cues for different phases of your workout. " +
                                    "Tap on each dropdown (Get Ready, Start Reps, Start Rest, Start Set Rest, Sets Complete) " +
                                    "to select your preferred sound. Choose 'None' if you prefer no sound for a specific cue."
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Exercise Configuration ---
                        newTitleLine("Exercise Configuration")
                        newTextLine("Customize the visual and tracking details for your exercise:")
                        newTextLine(
                            "•   Image: Select a visual representation for your exercise. If your image isn't available, you can choose 'None'.  " +
                                    "To add your own images, tap the 'Add Image' button.  If the image is in Google Photos, just navigate to the photo.  " +
                                    "If your photo is in a folder, tap the 3 dot icon and select 'Browse'.  Navigate to the folder containing your images.  " +
                                    "Tap the image.  Only one image is allowed per exercise so if you want to show multiple exercise positions, combine " +
                                    "them into one image.  If you wish to delete an image that you have added, tap the trashcan icon next to the image name " +
                                    "in the dropdown list."
                        )
                        newTextLine(
                            "•   Band Color / Weight-lbs: Record the resistance used for the exercise. Tap the dropdowns to select an existing option, " +
                                    "or tap '+' to add a new option. Use the trashcan icon to delete custom options (excluding 'N/A')."
                        )
                        newTextLine(
                            "•   Times per Day / Times per Week: Track how often you perform this specific exercise. Input numerical values.  " +
                                    "These have no impact on the timer.  They are only for your reference."
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Timer Configuration ---
                        newTitleLine("Timer Configuration")
                        newTextLine(
                            "Adjust the timing and repetition settings for your exercise. " +
                                    "'MoveTo' is the time needed to reach the exercise position from rest.  'Exercise is the " +
                                    "wait time while in the exercise position.  'MoveFrom' is the time needed to reach the " +
                                    "rest position from the exercise position.  " +
                                    "Enter positive numerical values for time fields. If 'Reps' is 0, 'Total Time' will be used."
                        )
                        newTextLine(
                            "•   Move To / Exercise / Move From / Rest / Reps / Total Time / Get Ready / Sets / Set Rest: " +
                                    "Refer to the 'Workout Phases & Terminology' section in 'Main Screen Operation' help for definitions of these fields. " +
                                    "Input your desired durations or counts."
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Manage Setups ---
                        newTitleLine("Manage Setups")
                        newTextLine("This powerful section allows you to load, save, organize, import, and export entire collections of exercises.")
                        newTextLine(
                            "•   Load Bundle: Use the dropdown to select pre-defined (factory) or user-created bundles. " +
                                    "You will be asked to 'Merge' (add to current list) or 'Replace' (overwrite current list) your exercises. " +
                                    "Canceling a load or merging will clear the selection."
                        )
                        newTextLine(
                            "•   Save As Bundle Name: Enter a unique name for your current collection of exercises. " +
                                    "This will save them as a new user-created bundle to your device, making them available in the 'Load Bundle' dropdown. " +
                                    "If a bundle with that name already exists, it will not be saved."
                        )
                        newTextLine(
                            "•   Save Bundle: Tap this button to save your current collection of exercises under the name provided in 'Save As Bundle Name'. " +
                                    "The newly saved bundle will automatically be selected in the 'Load Bundle' dropdown."
                        )
                        newTextLine(
                            "•   Delete Bundle: If a user-created bundle is currently selected in the 'Load Bundle' dropdown, " +
                                    "this button will be active. Tap it to permanently delete the selected user bundle from your device. " +
                                    "You will be asked for confirmation. Factory bundles cannot be deleted."
                        )
                        newTextLine("•   Exercise List: Below the 'Load Bundle' selector, you'll see your current list of saved exercises.")
                        newTextLine("    •   Select: Tap an exercise name to load its settings into the configuration fields above.")
                        newTextLine("    •   Move Up/Down (⬆️/⬇️): Reorder exercises in your list.")
                        newTextLine("    •   Delete (🗑️): Remove a specific exercise from your list. This will clear the bundle selection.")
                        newTextLine(
                            "•   New Exercise Name: Enter a name for a new exercise or to update the active exercise.  " +
                                    "This field is filled with the current exercise name if you tap on it in the Exercise List section. " +
                                    "This way you can edit the current name and save it as a new exercise."
                        )
                        newTextLine(
                            "•   Save Exercise: Saves the current configuration fields (including sound, image, timer settings, instructions) " +
                                    "under the 'New Exercise Name'. If the name already exists, it will update that exercise. This will clear the bundle selection."
                        )
                        newTextLine("•   Clear Setup: Deletes ALL exercises from your list. This will reset the settings to a default 'Unsaved Workout'.")
                        newTextLine("•   Import: Load exercises from a JSON file stored on your device. This will clear the bundle selection.")
                        newTextLine("•   Export: Save your current exercise list to a JSON file on your device.")
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Exercise Instructions ---
                        newTitleLine("Exercise Instructions")
                        newTextLine(
                            "This section displays the image and detailed instructions for the currently active exercise. " +
                                    "You can input or modify text notes here. The image displayed corresponds to your 'Image' selection " +
                                    "under 'Exercise Configuration'."
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp)) // Spacer at the bottom
        }
    }
}

@Composable
fun newTextLine(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun newTitleLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary

    )
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


