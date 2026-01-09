package com.cosmicocean.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.data.PrivacyLevel

/**
 * Epic 10: Task Privacy & Masking
 * Dialog for setting privacy options on individual tasks
 *
 * Used when:
 * - Creating a new task (optional privacy settings)
 * - Editing an existing task
 * - Quick privacy toggle from task list
 */

data class TaskPrivacySettings(
    val isPrivate: Boolean = false,
    val privacyLevel: PrivacyLevel = PrivacyLevel.PUBLIC,
    val privacyDisplay: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPrivacyDialog(
    currentSettings: TaskPrivacySettings,
    onSettingsChanged: (TaskPrivacySettings) -> Unit,
    onDismiss: () -> Unit
) {
    var isPrivate by remember { mutableStateOf(currentSettings.isPrivate) }
    var privacyLevel by remember { mutableStateOf(currentSettings.privacyLevel) }
    var customDisplay by remember { mutableStateOf(currentSettings.privacyDisplay ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🔒", fontSize = 24.sp)
                Text("Task Privacy", color = Color.White)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Private Toggle
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPrivate)
                            Color(0xFF9C27B0).copy(alpha = 0.2f)
                        else
                            Color(0xFF2A2A3E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Make Private",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Hide or mask this task on wallpaper",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF9C27B0),
                                checkedTrackColor = Color(0xFF9C27B0).copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Privacy Level Selection (only when private is enabled)
                if (isPrivate) {
                    Text(
                        text = "Privacy Level",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9C27B0)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrivacyLevel.values().forEach { level ->
                            PrivacyLevelOptionCompact(
                                level = level,
                                isSelected = level == privacyLevel,
                                onClick = { privacyLevel = level }
                            )
                        }
                    }

                    // Custom Display Text (only when custom level selected)
                    if (privacyLevel == PrivacyLevel.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customDisplay,
                            onValueChange = { customDisplay = it },
                            label = { Text("Custom Display Text") },
                            placeholder = { Text("e.g., 'Appointment'") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF9C27B0),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = Color(0xFF9C27B0),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                cursorColor = Color(0xFF9C27B0)
                            )
                        )
                        Text(
                            text = "This text will be shown instead of the task title",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Preview
                    Spacer(modifier = Modifier.height(8.dp))
                    PreviewCard(
                        taskTitle = "Buy groceries for dinner",
                        privacyLevel = privacyLevel,
                        customDisplay = customDisplay.ifEmpty { "Private task" }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSettingsChanged(
                        TaskPrivacySettings(
                            isPrivate = isPrivate,
                            privacyLevel = if (isPrivate) privacyLevel else PrivacyLevel.PUBLIC,
                            privacyDisplay = if (isPrivate && privacyLevel == PrivacyLevel.CUSTOM)
                                customDisplay.ifEmpty { null }
                            else null
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
private fun PrivacyLevelOptionCompact(
    level: PrivacyLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (level) {
        PrivacyLevel.PUBLIC -> "👁️"
        PrivacyLevel.CATEGORY -> "📁"
        PrivacyLevel.INITIALS -> "🔤"
        PrivacyLevel.HIDDEN -> "🙈"
        PrivacyLevel.CUSTOM -> "✏️"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF9C27B0).copy(alpha = 0.3f)
            else
                Color(0xFF2A2A3E)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 18.sp)
                Text(
                    text = level.displayName,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color.White
                )
            }
            if (isSelected) {
                Text(text = "✓", fontSize = 18.sp, color = Color(0xFF9C27B0))
            }
        }
    }
}

@Composable
private fun PreviewCard(
    taskTitle: String,
    privacyLevel: PrivacyLevel,
    customDisplay: String
) {
    val displayText = when (privacyLevel) {
        PrivacyLevel.PUBLIC -> taskTitle
        PrivacyLevel.CATEGORY -> "Personal task"
        PrivacyLevel.INITIALS -> "${taskTitle.firstOrNull()?.uppercaseChar() ?: 'T'}..."
        PrivacyLevel.HIDDEN -> "(Not shown on wallpaper)"
        PrivacyLevel.CUSTOM -> customDisplay
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0A14)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Preview on wallpaper:",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayText,
                fontSize = 16.sp,
                color = if (privacyLevel == PrivacyLevel.HIDDEN)
                    Color.White.copy(alpha = 0.3f)
                else
                    Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Quick privacy toggle button for task cards/list items
 */
@Composable
fun TaskPrivacyToggle(
    isPrivate: Boolean,
    privacyLevel: PrivacyLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when {
        !isPrivate -> "👁️"
        privacyLevel == PrivacyLevel.HIDDEN -> "🙈"
        privacyLevel == PrivacyLevel.CATEGORY -> "📁"
        privacyLevel == PrivacyLevel.INITIALS -> "🔤"
        privacyLevel == PrivacyLevel.CUSTOM -> "✏️"
        else -> "🔒"
    }

    val tint = if (isPrivate) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.5f)

    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
    }
}

/**
 * Privacy badge to show on task cards
 */
@Composable
fun PrivacyBadge(
    privacyLevel: PrivacyLevel,
    modifier: Modifier = Modifier
) {
    if (privacyLevel == PrivacyLevel.PUBLIC) return

    val (icon, label) = when (privacyLevel) {
        PrivacyLevel.CATEGORY -> "📁" to "Category"
        PrivacyLevel.INITIALS -> "🔤" to "Initials"
        PrivacyLevel.HIDDEN -> "🙈" to "Hidden"
        PrivacyLevel.CUSTOM -> "✏️" to "Custom"
        else -> return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF9C27B0).copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 10.sp)
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF9C27B0)
            )
        }
    }
}
