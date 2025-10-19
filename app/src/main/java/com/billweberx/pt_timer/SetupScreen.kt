// In file: app/src/main/java/com/billweberx/pt_timer/SetupScreen.kt

package com.billweberx.pt_timer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: TimerViewModel,
    onImport: (String) -> Unit,
    onExport: () -> String
) {
    // --- THIS IS THE FIX ---
    // The local state variables are removed.
    // The UI will now directly read the state from the viewModel,
    // ensuring it's always up-to-date.

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Setups") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Default Sounds", style = MaterialTheme.typography.titleMedium)

            // Corrected: The dropdowns now read directly from the ViewModel's state.
            SoundDropdown(
                label = "Start Sound",
                soundOptions = viewModel.soundOptions,
                selectedSound = viewModel.selectedStartSound,
                onSoundSelected = {
                    viewModel.selectedStartSound = it
                },
                modifier = Modifier.fillMaxWidth()
            )

            SoundDropdown(
                label = "Rest Sound",
                soundOptions = viewModel.soundOptions,
                selectedSound = viewModel.selectedRestSound,
                onSoundSelected = {
                    viewModel.selectedRestSound = it
                },
                modifier = Modifier.fillMaxWidth()
            )

            SoundDropdown(
                label = "Complete Sound",
                soundOptions = viewModel.soundOptions,
                selectedSound = viewModel.selectedCompleteSound,
                onSoundSelected = {
                    viewModel.selectedCompleteSound = it
                },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Manage Setups", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { importLauncher.launch("application/json") }) {
                    Text("Import")
                }
                Button(onClick = { exportLauncher.launch("PT_Timer_Setups.json") }) {
                    Text("Export")
                }
            }
        }
    }
}

// The SoundDropdown composable remains unchanged
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
            modifier = Modifier.menuAnchor().fillMaxWidth()
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
