package com.billweberx.pt_timer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.BufferedReader
import java.io.InputStreamReader


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: TimerViewModel,
    onImport: (String) -> Unit,
    onExport: () -> String
) {
    var selectedStartSound by viewModel::selectedStartSound
    var selectedRestSound by viewModel::selectedRestSound
    var selectedCompleteSound by viewModel::selectedCompleteSound
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    val contentResolver = context.contentResolver
                    contentResolver.openInputStream(it)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val content = reader.readText()
                            onImport(content)
                        }
                    }
                } catch (_: Exception) { /* Handle exception */ }
            }
        }
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    val contentResolver = context.contentResolver
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        val jsonString = onExport()
                        outputStream.write(jsonString.toByteArray())
                    }
                } catch (_: Exception) { /* Handle exception */ }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp), // Main padding for left/right
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom Top Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // <-- THIS IS THE KEY FIX
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Settings",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium
            )
            // Spacer to balance the IconButton and keep the title centered
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp)) // Space below the top bar

        Text("Select Sound Files", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        SoundDropdown(
            label = "Start Sound",
            soundOptions = viewModel.startSoundOptions,
            selectedSound = selectedStartSound,
            onSoundSelected = {viewModel.selectedStartSound = it
                viewModel.saveState()
            },

            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoundDropdown(
            label = "Rest Sound",
            soundOptions = viewModel.restSoundOptions,
            selectedSound = selectedRestSound,
            onSoundSelected = {viewModel.selectedRestSound = it
                viewModel.saveState()
            },

            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoundDropdown(
            label = "End Sound",
            soundOptions = viewModel.completeSoundOptions,
            selectedSound = selectedCompleteSound,
            onSoundSelected = {viewModel.selectedCompleteSound = it
                viewModel.saveState()
            },

            modifier = Modifier.fillMaxWidth()
        )


        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import Setups") }
            Button(onClick = { exportLauncher.launch("pt_timer_setups.json") }) { Text("Export Setups") }
        }
    }
}
