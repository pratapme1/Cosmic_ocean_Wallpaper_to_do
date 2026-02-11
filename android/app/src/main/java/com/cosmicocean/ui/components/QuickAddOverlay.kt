package com.cosmicocean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cosmicocean.model.EchoInterval
import com.cosmicocean.model.Star

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddOverlay(
    onDismiss: () -> Unit,
    availableParents: List<Star>,
    onSave: (String, Boolean?, EchoInterval?, Boolean, String?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var recurringEnabled by remember { mutableStateOf(false) }
    var recurringTouched by remember { mutableStateOf(false) }
    var recurrencePattern by remember { mutableStateOf(EchoInterval.DAILY) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }
    var isSubtask by remember { mutableStateOf(false) }
    var selectedParentId by remember { mutableStateOf<String?>(null) }
    var showParentMenu by remember { mutableStateOf(false) }
    var showParentError by remember { mutableStateOf(false) }
    val parentOptions = remember(availableParents) { availableParents.sortedBy { it.dueDate ?: Long.MAX_VALUE } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E), // Dark Background
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 48.dp) 
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "New Cosmic Task",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF00E5FF)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // CRITICAL FIX: Explicit colors for visibility
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. Record demo 30m", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_add_title"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF00E5FF)
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Recurring", color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = if (recurringEnabled) "Enabled" else "Disabled",
                    color = Color.White.copy(alpha = 0.8f)
                )
                Switch(
                    checked = recurringEnabled,
                    onCheckedChange = {
                        recurringEnabled = it
                        recurringTouched = true
                    },
                    modifier = Modifier.testTag("quick_add_recurring_toggle"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            if (recurringEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = showRecurrenceMenu,
                    onExpandedChange = { showRecurrenceMenu = it }
                ) {
                    OutlinedTextField(
                        value = recurrencePattern.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRecurrenceMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("quick_add_recurrence_field"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showRecurrenceMenu,
                        onDismissRequest = { showRecurrenceMenu = false }
                    ) {
                        listOf(EchoInterval.DAILY, EchoInterval.WEEKLY, EchoInterval.MONTHLY).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    recurrencePattern = option
                                    showRecurrenceMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Subtask", color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSubtask) "Enabled" else "Disabled",
                    color = Color.White.copy(alpha = 0.8f)
                )
                Switch(
                    checked = isSubtask,
                    onCheckedChange = {
                        if (parentOptions.isEmpty()) return@Switch
                        isSubtask = it
                        if (!isSubtask) {
                            selectedParentId = null
                            showParentError = false
                        }
                    },
                    modifier = Modifier.testTag("quick_add_subtask_toggle"),
                    enabled = parentOptions.isNotEmpty(),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            if (parentOptions.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No parent tasks available",
                    color = Color.Gray.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (isSubtask) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Parent Task", color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = showParentMenu,
                    onExpandedChange = { showParentMenu = it }
                ) {
                    val selectedLabel = parentOptions.firstOrNull { it.id == selectedParentId }?.title ?: "Select parent"
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showParentMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("quick_add_parent_field"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showParentMenu,
                        onDismissRequest = { showParentMenu = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        parentOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.title) },
                                onClick = {
                                    selectedParentId = option.id
                                    showParentError = false
                                    showParentMenu = false
                                }
                            )
                        }
                    }
                }
                if (showParentError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Pick a parent task to save",
                        color = Color(0xFFFFB74D),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("quick_add_parent_error")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        if (isSubtask && selectedParentId == null) {
                            showParentError = true
                            return@Button
                        }
                        val recurringOverride = if (recurringTouched) recurringEnabled else null
                        val echoInterval = if (recurringEnabled) recurrencePattern else null
                        onSave(text, recurringOverride, echoInterval, isSubtask, selectedParentId)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Text("Release Star", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
