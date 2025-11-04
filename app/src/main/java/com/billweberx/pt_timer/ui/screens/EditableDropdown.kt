// In C:/.../pt_timer/ui/screens/EditableDropdown.kt

package com.billweberx.pt_timer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions // <-- ADD THIS IMPORT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType // <-- ADD THIS IMPORT
import androidx.compose.ui.unit.dp
import com.billweberx.pt_timer.data.SpinnerOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableDropdown(
    label: String,
    options: List<SpinnerOption>,
    selectedOption: SpinnerOption,
    onOptionSelected: (SpinnerOption) -> Unit,
    onAddOption: (String) -> Unit,
    onDeleteOption: (SpinnerOption) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text // <-- 3. ADD NEW PARAMETER
) {
    var isExpanded by remember { mutableStateOf(false) }
    var newOptionText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.TopStart)
    ) {
        Box {
            OutlinedTextField(
                value = selectedOption.value,
                onValueChange = { },
                readOnly = true,
                label = { Text(label) },
                modifier = Modifier
                    .fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Dropdown"
                    )
                }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { isExpanded = true }
            )
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            LaunchedEffect(isExpanded) {
                if (isExpanded) {
                    scope.launch {
                        delay(100)
                        focusRequester.requestFocus()
                    }
                }
            }

            options.forEach { option ->
                if (option.value != "N/A") {
                    DropdownMenuItem(
                        text = { Text(option.value) },
                        onClick = {
                            onOptionSelected(option)
                            isExpanded = false
                        },
                        trailingIcon = {
                            IconButton(onClick = { onDeleteOption(option) }) {
                                Icon(Icons.Default.Delete, "Delete ${option.value}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }

            if (options.any { it.value != "N/A" }) {
                HorizontalDivider()
            }

            OutlinedTextField(
                value = newOptionText,
                onValueChange = { newOptionText = it },
                label = { Text("Add new...") },
                // 4. APPLY THE KEYBOARD TYPE HERE
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (newOptionText.isNotBlank()) {
                                onAddOption(newOptionText)
                                onOptionSelected(SpinnerOption(newOptionText))
                                newOptionText = ""
                                isExpanded = false
                            }
                        },
                        enabled = newOptionText.isNotBlank() && options.none { it.value.equals(newOptionText, true) }
                    ) {
                        Text("ADD")
                    }
                }
            )
        }
    }
}
