package com.cosmicocean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cosmicocean.model.Star

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStarOverlay(
    star: Star,
    onDismiss: () -> Unit,
    availableParents: List<Star>,
    onSave: (String, Int, Float, Boolean, com.cosmicocean.model.EchoInterval?, Boolean, String?) -> Unit,
    onStartFocus: (Int) -> Unit
) {
    var title by remember { mutableStateOf(star.title) }
    var urgency by remember { mutableIntStateOf(star.urgency) }
    var isRecurring by remember { mutableStateOf(star.isRecurring) }
    var isSubtask by remember { mutableStateOf(star.isSubtask) }
    var selectedParentId by remember { mutableStateOf(star.parentId) }
    var showParentMenu by remember { mutableStateOf(false) }
    var showParentError by remember { mutableStateOf(false) }
    val parentOptions = remember(availableParents) { availableParents.sortedBy { it.dueDate ?: Long.MAX_VALUE } }
    var recurrencePattern by remember {
        mutableStateOf(star.echoInterval ?: com.cosmicocean.model.EchoInterval.DAILY)
    }
    var showRecurrenceMenu by remember { mutableStateOf(false) }

    // Calculate initial due value and unit from star.dueIn (minutes)
    val initialDue = remember {
        when {
            star.dueIn == 0f -> Pair(0f, "hours")
            kotlin.math.abs(star.dueIn) < 60 -> Pair(star.dueIn, "minutes")
            kotlin.math.abs(star.dueIn) < 1440 -> Pair(star.dueIn / 60, "hours")
            else -> Pair(star.dueIn / 1440, "days")
        }
    }

    var dueValue by remember { mutableStateOf(initialDue.first.toString()) }
    var dueUnit by remember { mutableStateOf(initialDue.second) }
    var showUnitDropdown by remember { mutableStateOf(false) }
    var showFocusDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 48.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Task", style = MaterialTheme.typography.titleLarge, color = Color(0xFF3AA0FF))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_title"),
                textStyle = LocalTextStyle.current.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3AA0FF),
                    unfocusedBorderColor = Color.Gray
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Urgency", color = Color.Gray)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(1, 2, 3).forEach { level ->
                    FilterChip(
                        selected = urgency == level,
                        onClick = { urgency = level },
                        label = { Text("P$level", color = if (urgency == level) Color.Black else Color.White) },
                        modifier = Modifier.testTag("urgency_$level"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF3AA0FF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Due In Field (PWA-accurate)
            Text("Due in", color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dueValue,
                    onValueChange = { dueValue = it },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3AA0FF),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = showUnitDropdown,
                    onExpandedChange = { showUnitDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = dueUnit.capitalize(),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitDropdown) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3AA0FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = showUnitDropdown,
                        onDismissRequest = { showUnitDropdown = false }
                    ) {
                        listOf("minutes", "hours", "days").forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.capitalize()) },
                                onClick = {
                                    dueUnit = unit
                                    showUnitDropdown = false
                                }
                            )
                        }
                    }
                }
            }
            Text(
                "Negative values = overdue",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Recurring", color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRecurring) "Enabled" else "Disabled",
                    color = Color.White.copy(alpha = 0.8f)
                )
                Switch(
                    checked = isRecurring,
                    onCheckedChange = { isRecurring = it },
                    modifier = Modifier.testTag("edit_recurring_toggle"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF3AA0FF),
                        checkedTrackColor = Color(0xFF3AA0FF).copy(alpha = 0.5f)
                    )
                )
            }

            if (isRecurring) {
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
                            .testTag("edit_recurrence_field"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3AA0FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showRecurrenceMenu,
                        onDismissRequest = { showRecurrenceMenu = false }
                    ) {
                        listOf(
                            com.cosmicocean.model.EchoInterval.DAILY,
                            com.cosmicocean.model.EchoInterval.WEEKLY,
                            com.cosmicocean.model.EchoInterval.MONTHLY
                        ).forEach { option ->
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
                verticalAlignment = Alignment.CenterVertically
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
                    modifier = Modifier.testTag("edit_subtask_toggle"),
                    enabled = parentOptions.isNotEmpty(),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF3AA0FF),
                        checkedTrackColor = Color(0xFF3AA0FF).copy(alpha = 0.5f)
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
                            .testTag("edit_parent_field"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3AA0FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showParentMenu,
                        onDismissRequest = { showParentMenu = false }
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
                        modifier = Modifier.testTag("edit_parent_error")
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = { showFocusDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
            ) {
                Text("Start Focus Session")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (isSubtask && selectedParentId == null) {
                        showParentError = true
                        return@Button
                    }
                    // Convert due value to minutes
                    val dueInMinutes = (dueValue.toFloatOrNull() ?: 0f) * when (dueUnit) {
                        "minutes" -> 1f
                        "hours" -> 60f
                        "days" -> 1440f
                        else -> 1f
                    }
                    onSave(
                        title,
                        urgency,
                        dueInMinutes,
                        isRecurring,
                        if (isRecurring) recurrencePattern else null,
                        isSubtask,
                        selectedParentId
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("update_star"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3AA0FF))
            ) {
                Text("Update Star", color = Color.Black)
            }
        }
    }

    if (showFocusDialog) {
        AlertDialog(
            onDismissRequest = { showFocusDialog = false },
            title = { Text("Focus Duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60).forEach { minutes ->
                        Button(
                            onClick = {
                                onStartFocus(minutes)
                                showFocusDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${minutes} minutes")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFocusDialog = false }) { Text("Close") }
            }
        )
    }
}

fun String.capitalize() = replaceFirstChar { it.uppercase() }
