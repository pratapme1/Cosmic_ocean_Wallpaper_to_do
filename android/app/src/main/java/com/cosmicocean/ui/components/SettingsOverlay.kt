package com.cosmicocean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverlay(
    onDismiss: () -> Unit,
    onDoneForToday: () -> Unit,
    onSnoozeOverdue: () -> Unit,
    onClearAll: () -> Unit,
    userEmail: String? = null,
    currentTheme: String = "cosmic",
    isCustomWallpaper: Boolean = false,
    onThemeChange: (String) -> Unit = {},
    currentTaskPlacement: String = "auto",
    onTaskPlacementChange: (String) -> Unit = {},
    onOpenPrivacySettings: (() -> Unit)? = null,
    onOpenEnvironmentSettings: (() -> Unit)? = null,
    hasViKey: Boolean = false,
    onSaveViKey: ((String) -> Unit)? = null,
    onClearViKey: (() -> Unit)? = null,
    hudOverlayUri: String? = null,
    hudOverlayVerticalPercent: Int = 80,
    hudOverlayOpacityPercent: Int = 90,
    hudOverlayMissing: Boolean = false,
    onPickHudOverlay: (() -> Unit)? = null,
    onHudOverlayVerticalChange: (Int) -> Unit = {},
    onHudOverlayOpacityChange: (Int) -> Unit = {},
    onClearHudOverlay: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF3AA0FF))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // User Info
            if (userEmail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Logged in as: $userEmail",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Guest Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme Selection
            Column {
                Text(
                    "Wallpaper Theme",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCustomWallpaper) Color.Gray else Color(0xFF3AA0FF)
                )
                if (isCustomWallpaper) {
                    Text(
                        "Disabled when using custom wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cosmic Theme
                OutlinedButton(
                    onClick = { onThemeChange("cosmic") },
                    enabled = !isCustomWallpaper,
                    modifier = Modifier.weight(1f),
                    colors = if (currentTheme == "cosmic" && !isCustomWallpaper) {
                        ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF3AA0FF).copy(alpha = 0.2f))
                    } else {
                        ButtonDefaults.outlinedButtonColors(
                            disabledContentColor = Color.Gray.copy(alpha = 0.5f),
                            disabledContainerColor = Color.Transparent
                        )
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTheme == "cosmic" && !isCustomWallpaper) Color(0xFF3AA0FF) 
                        else if (isCustomWallpaper) Color.Gray.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("🌌 Cosmic", color = if (isCustomWallpaper) Color.Gray else Color.White)
                }

                // Ocean Theme
                OutlinedButton(
                    onClick = { onThemeChange("ocean") },
                    enabled = !isCustomWallpaper,
                    modifier = Modifier.weight(1f),
                    colors = if (currentTheme == "ocean" && !isCustomWallpaper) {
                        ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF00CED1).copy(alpha = 0.2f))
                    } else {
                        ButtonDefaults.outlinedButtonColors(
                            disabledContentColor = Color.Gray.copy(alpha = 0.5f),
                            disabledContainerColor = Color.Transparent
                        )
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTheme == "ocean" && !isCustomWallpaper) Color(0xFF00CED1)
                        else if (isCustomWallpaper) Color.Gray.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("🌊 Ocean", color = if (isCustomWallpaper) Color.Gray else Color.White)
                }

                // Fantasy Theme
                OutlinedButton(
                    onClick = { onThemeChange("fantasy") },
                    enabled = !isCustomWallpaper,
                    modifier = Modifier.weight(1f),
                    colors = if (currentTheme == "fantasy" && !isCustomWallpaper) {
                        ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDA70D6).copy(alpha = 0.2f))
                    } else {
                        ButtonDefaults.outlinedButtonColors(
                            disabledContentColor = Color.Gray.copy(alpha = 0.5f),
                            disabledContainerColor = Color.Transparent
                        )
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTheme == "fantasy" && !isCustomWallpaper) Color(0xFFDA70D6)
                        else if (isCustomWallpaper) Color.Gray.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("✨ Fantasy", color = if (isCustomWallpaper) Color.Gray else Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Task position on the wallpaper (keeps tasks clear of lock screen notifications)
            Text(
                "Task Position",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF3AA0FF)
            )
            Text(
                "Auto keeps tasks clear of lock screen notifications on your Android version",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("auto", "✨ Auto", Color(0xFF3AA0FF)),
                    Triple("top", "⬆️ Top", Color(0xFF00CED1)),
                    Triple("bottom", "⬇️ Bottom", Color(0xFFDA70D6))
                ).forEach { (value, label, accent) ->
                    val selected = currentTaskPlacement == value
                    OutlinedButton(
                        onClick = { onTaskPlacementChange(value) },
                        modifier = Modifier.weight(1f),
                        colors = if (selected) {
                            ButtonDefaults.outlinedButtonColors(containerColor = accent.copy(alpha = 0.2f))
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) accent else Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(label, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (onPickHudOverlay != null) {
                Text(
                    "HUD Overlay Image",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF3AA0FF)
                )
                Text(
                    "Use a wide transparent PNG named hud-overlay.png from Pictures; replacing that file updates after wallpaper restart/redraw.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickHudOverlay,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3AA0FF))
                    ) {
                        Text(if (hudOverlayUri == null) "Pick PNG" else "Replace PNG", color = Color(0xFF3AA0FF))
                    }
                    if (hudOverlayUri != null && onClearHudOverlay != null) {
                        TextButton(onClick = onClearHudOverlay) {
                            Text("Clear overlay", color = Color(0xFFFF3B30))
                        }
                    }
                }
                if (hudOverlayMissing) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Overlay image not found",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB74D)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Vertical position ${hudOverlayVerticalPercent.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Slider(
                    value = hudOverlayVerticalPercent.coerceIn(0, 100).toFloat(),
                    onValueChange = { onHudOverlayVerticalChange(it.roundToInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    steps = 99
                )
                Text(
                    "Opacity ${hudOverlayOpacityPercent.coerceIn(10, 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Slider(
                    value = hudOverlayOpacityPercent.coerceIn(10, 100).toFloat(),
                    onValueChange = { onHudOverlayOpacityChange(it.roundToInt().coerceIn(10, 100)) },
                    valueRange = 10f..100f,
                    steps = 89
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Privacy Settings
            if (onOpenPrivacySettings != null) {
                OutlinedButton(
                    onClick = onOpenPrivacySettings,
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9C27B0))
                ) {
                    Text("🔒 Privacy Settings", color = Color(0xFF9C27B0))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Environment Settings (Epic 10 Phase 3)
            if (onOpenEnvironmentSettings != null) {
                OutlinedButton(
                    onClick = onOpenEnvironmentSettings,
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Text("🌤️ Environment Settings", color = Color(0xFF00E5FF))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Vi Reminders (remote tasks synced from Supabase; complete from the app)
            if (onSaveViKey != null) {
                Text(
                    "Vi Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF3AA0FF)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (hasViKey) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "✅ Connected — reminders sync with wallpaper refresh, completions sync back",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        if (onClearViKey != null) {
                            TextButton(onClick = onClearViKey) {
                                Text("Disconnect", color = Color(0xFFFF3B30))
                            }
                        }
                    }
                } else {
                    var keyInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Supabase key (entered once)", color = Color.White.copy(alpha = 0.6f)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3AA0FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (keyInput.isNotBlank()) {
                                onSaveViKey(keyInput.trim())
                                keyInput = ""
                            }
                        },
                        enabled = keyInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3AA0FF))
                    ) {
                        Text("🔑 Save Key & Sync", color = Color(0xFF3AA0FF))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Actions
            Button(
                onClick = onDoneForToday,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("✨ Done For Today", color = Color.Black)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSnoozeOverdue,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D))
            ) {
                Text("😴 Snooze Overdue", color = Color(0xFFFFB74D))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Danger Zone
            Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onClearAll,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
            ) {
                Text("⚠️ Clear All Data", color = Color.White)
            }
        }
    }
}
