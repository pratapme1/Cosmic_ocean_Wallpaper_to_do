package com.cosmicocean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cosmicocean.model.Star

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStarOverlay(
    star: Star,
    onDismiss: () -> Unit,
    onSave: (String, Int, Float) -> Unit
) {
    var title by remember { mutableStateOf(star.title) }
    var urgency by remember { mutableIntStateOf(star.urgency) }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 48.dp)) {
            Text("Edit Task", style = MaterialTheme.typography.titleLarge, color = Color(0xFF3AA0FF))
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    // Convert due value to minutes
                    val dueInMinutes = (dueValue.toFloatOrNull() ?: 0f) * when (dueUnit) {
                        "minutes" -> 1f
                        "hours" -> 60f
                        "days" -> 1440f
                        else -> 1f
                    }
                    onSave(title, urgency, dueInMinutes)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3AA0FF))
            ) {
                Text("Update Star", color = Color.Black)
            }
        }
    }
}

fun String.capitalize() = replaceFirstChar { it.uppercase() }
