package com.cosmicocean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverlay(
    onDismiss: () -> Unit,
    onDoneForToday: () -> Unit,
    onClearAll: () -> Unit,
    onLogout: (() -> Unit)? = null,
    userEmail: String? = null,
    currentTheme: String = "cosmic",
    onThemeChange: (String) -> Unit = {}
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
            Text("Settings & Guide", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF3AA0FF))

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
            Text(
                "Wallpaper Theme",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF3AA0FF)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cosmic Theme
                OutlinedButton(
                    onClick = { onThemeChange("cosmic") },
                    modifier = Modifier.weight(1f),
                    colors = if (currentTheme == "cosmic") {
                        ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF3AA0FF).copy(alpha = 0.2f))
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTheme == "cosmic") Color(0xFF3AA0FF) else Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("🌌 Cosmic", color = Color.White)
                }

                // Ocean Theme
                OutlinedButton(
                    onClick = { onThemeChange("ocean") },
                    modifier = Modifier.weight(1f),
                    colors = if (currentTheme == "ocean") {
                        ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF00CED1).copy(alpha = 0.2f))
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTheme == "ocean") Color(0xFF00CED1) else Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("🌊 Ocean", color = Color.White)
                }

                // Fantasy Theme
                OutlinedButton(
                    onClick = { onThemeChange("fantasy") },
                    modifier = Modifier.weight(1f),
                    colors = if (currentTheme == "fantasy") {
                        ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDA70D6).copy(alpha = 0.2f))
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentTheme == "fantasy") Color(0xFFDA70D6) else Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("✨ Fantasy", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                onClick = { /* Export Logic */ },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3AA0FF))
            ) {
                Text("💾 Export Data", color = Color(0xFF3AA0FF))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button (if logged in)
            if (onLogout != null && userEmail != null) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9500))
                ) {
                    Text("🚪 Logout", color = Color(0xFFFF9500))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

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
