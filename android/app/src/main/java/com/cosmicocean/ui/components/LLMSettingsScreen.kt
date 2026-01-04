package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.data.LLMPreferences
import com.cosmicocean.data.MessageVoice

/**
 * Epic 8: LLM Intelligence Enhancement
 * Settings screen for LLM preferences
 *
 * Allows users to:
 * - Enable/disable advanced parsing
 * - Toggle parse preview
 * - Choose message voice/tone
 * - Enable/disable analytics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMSettingsScreen(
    preferences: LLMPreferences,
    onAdvancedParsingChanged: (Boolean) -> Unit,
    onShowPreviewChanged: (Boolean) -> Unit,
    onMessageVoiceChanged: (MessageVoice) -> Unit,
    onAnalyticsChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVoiceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Intelligence Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = Color(0xFF0F0F1E)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Customize how AI helps you create tasks",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Advanced Parsing Section
            SettingCard(
                title = "Advanced Parsing",
                subtitle = "Use AI to understand natural language",
                icon = "🤖"
            ) {
                Switch(
                    checked = preferences.advancedParsingEnabled,
                    onCheckedChange = onAdvancedParsingChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            // Info card for Advanced Parsing
            if (preferences.advancedParsingEnabled) {
                InfoCard(
                    text = "✨ AI will extract dates, times, priorities, and categories from your task descriptions automatically.",
                    color = Color(0xFF1E3A5F)
                )
            } else {
                InfoCard(
                    text = "ℹ️ Quick parsing mode: basic keyword matching only.",
                    color = Color(0xFF3E3E3E)
                )
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Show Preview Section
            SettingCard(
                title = "Show Parse Preview",
                subtitle = "Review AI suggestions before creating tasks",
                icon = "👁️"
            ) {
                Switch(
                    checked = preferences.showParsePreview,
                    onCheckedChange = onShowPreviewChanged,
                    enabled = preferences.advancedParsingEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (!preferences.advancedParsingEnabled) {
                InfoCard(
                    text = "⚠️ Enable Advanced Parsing to use preview feature",
                    color = Color(0xFF5E3A00)
                )
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Message Voice Section
            SettingCard(
                title = "Message Voice",
                subtitle = preferences.messageVoice.displayName,
                icon = "💬",
                onClick = { showVoiceDialog = true }
            ) {
                Text(
                    text = "Change",
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp
                )
            }

            InfoCard(
                text = preferences.messageVoice.description,
                color = Color(0xFF1E3A5F)
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Analytics Section
            SettingCard(
                title = "Help Improve AI",
                subtitle = "Share anonymous usage data",
                icon = "📊"
            ) {
                Switch(
                    checked = preferences.analyticsEnabled,
                    onCheckedChange = onAnalyticsChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.analyticsEnabled) {
                InfoCard(
                    text = "Thank you! Your data helps improve AI accuracy for everyone.",
                    color = Color(0xFF1E3A5F)
                )
            } else {
                InfoCard(
                    text = "Analytics disabled. No usage data will be collected.",
                    color = Color(0xFF3E3E3E)
                )
            }

            // Footer
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Changes take effect immediately",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Voice Selection Dialog
    if (showVoiceDialog) {
        VoiceSelectionDialog(
            currentVoice = preferences.messageVoice,
            onVoiceSelected = { voice ->
                onMessageVoiceChanged(voice)
                showVoiceDialog = false
            },
            onDismiss = { showVoiceDialog = false }
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            trailing()
        }
    }
}

@Composable
private fun InfoCard(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun VoiceSelectionDialog(
    currentVoice: MessageVoice,
    onVoiceSelected: (MessageVoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Message Voice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageVoice.values().forEach { voice ->
                    VoiceOption(
                        voice = voice,
                        isSelected = voice == currentVoice,
                        onClick = { onVoiceSelected(voice) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
private fun VoiceOption(
    voice: MessageVoice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF4CAF50).copy(alpha = 0.2f)
            else
                Color(0xFF2A2A3E)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = voice.displayName,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White
                )
                if (isSelected) {
                    Text(text = "✓", fontSize = 20.sp, color = Color(0xFF4CAF50))
                }
            }
            Text(
                text = voice.description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
