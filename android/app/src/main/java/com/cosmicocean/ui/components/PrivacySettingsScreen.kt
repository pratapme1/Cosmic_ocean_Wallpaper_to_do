package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.data.PrivacyPreferences
import com.cosmicocean.data.PrivacyLevel
import kotlinx.coroutines.launch

/**
 * Epic 10: Task Privacy & Masking
 * Settings screen for privacy preferences
 *
 * Allows users to:
 * - Set default privacy level for wallpaper tasks
 * - Toggle hide all tasks mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    preferences: PrivacyPreferences,
    onDefaultPrivacyLevelChanged: (PrivacyLevel) -> Unit,
    onHideAllTasksModeChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPrivacyLevelDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 120.dp.toPx() }
    var swipeAccumulated by remember { mutableStateOf(0f) }
    var swipeTriggered by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = Color(0xFF0F0F1E)
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(scrollState.value) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (scrollState.value != 0) {
                                swipeAccumulated = 0f
                                return@detectVerticalDragGestures
                            }
                            if (dragAmount > 0) {
                                swipeAccumulated += dragAmount
                                if (!swipeTriggered && swipeAccumulated > swipeThresholdPx) {
                                    swipeTriggered = true
                                    onNavigateBack()
                                }
                            } else {
                                swipeAccumulated = 0f
                            }
                        },
                        onDragEnd = {
                            swipeAccumulated = 0f
                            swipeTriggered = false
                        },
                        onDragCancel = {
                            swipeAccumulated = 0f
                            swipeTriggered = false
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Control how your tasks appear on the wallpaper",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

            // ===== MASTER CONTROLS SECTION =====
            SectionHeader(title = "Master Controls", icon = "🔐")

            // Hide All Tasks Mode
            PrivacySettingCard(
                title = "Hide All Tasks",
                subtitle = "Show no tasks on wallpaper",
                icon = "🙈"
            ) {
                Switch(
                    checked = preferences.hideAllTasksMode,
                    onCheckedChange = onHideAllTasksModeChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFE91E63),
                        checkedTrackColor = Color(0xFFE91E63).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.hideAllTasksMode) {
                PrivacyInfoCard(
                    text = "🛡️ Maximum privacy mode active. No tasks will appear on your wallpaper.",
                    color = Color(0xFF5E1A3A)
                )
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== DEFAULT PRIVACY SECTION =====
            SectionHeader(title = "Default Privacy Level", icon = "📝")

            // Privacy Level Selector
            PrivacySettingCard(
                title = "Wallpaper Privacy",
                subtitle = preferences.defaultPrivacyLevel.displayName,
                icon = "🔒",
                onClick = { showPrivacyLevelDialog = true }
            ) {
                Text(
                    text = "Change",
                    color = Color(0xFF9C27B0),
                    fontSize = 14.sp
                )
            }

            PrivacyInfoCard(
                text = getPrivacyLevelDescription(preferences.defaultPrivacyLevel),
                color = Color(0xFF3A1E5F)
            )

        }
    }

    // Privacy Level Selection Dialog
    if (showPrivacyLevelDialog) {
        PrivacyLevelDialog(
            currentLevel = preferences.defaultPrivacyLevel,
            onLevelSelected = { level ->
                onDefaultPrivacyLevelChanged(level)
                showPrivacyLevelDialog = false
            },
            onDismiss = { showPrivacyLevelDialog = false }
        )
    }

    }
}

@Composable
private fun SectionHeader(title: String, icon: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9C27B0)
        )
    }
}

@Composable
private fun PrivacySettingCard(
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
private fun PrivacyInfoCard(text: String, color: Color) {
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
private fun PrivacyLevelDialog(
    currentLevel: PrivacyLevel,
    onLevelSelected: (PrivacyLevel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Privacy Level", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PrivacyLevel.PUBLIC,
                    PrivacyLevel.INITIALS,
                    PrivacyLevel.HIDDEN
                ).forEach { level ->
                    PrivacyLevelOption(
                        level = level,
                        isSelected = level == currentLevel,
                        onClick = { onLevelSelected(level) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9C27B0))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
private fun PrivacyLevelOption(
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
                Color(0xFF9C27B0).copy(alpha = 0.2f)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = icon, fontSize = 20.sp)
                Column {
                    Text(
                        text = level.displayName,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White
                    )
                    Text(
                        text = level.description,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            if (isSelected) {
                Text(text = "✓", fontSize = 20.sp, color = Color(0xFF9C27B0))
            }
        }
    }
}

private fun getPrivacyLevelDescription(level: PrivacyLevel): String {
    return when (level) {
        PrivacyLevel.PUBLIC -> "👁️ Tasks will show their full title on the wallpaper."
        PrivacyLevel.CATEGORY -> "📁 Tasks will show as 'Task'."
        PrivacyLevel.INITIALS -> "🔤 Tasks will show only the first letter (e.g., 'M...')."
        PrivacyLevel.HIDDEN -> "🙈 Private tasks won't appear on the wallpaper at all."
        PrivacyLevel.CUSTOM -> "✏️ Tasks will show as 'Private task'."
    }
}

/**
 * Stateful wrapper for PrivacySettingsScreen that handles state internally
 * Local-only: loads and saves preferences via DataStore
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsWrapper(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { com.cosmicocean.data.PrivacyPreferencesRepository(context) }
    val preferences by repo.preferencesFlow.collectAsState(initial = PrivacyPreferences())

    fun triggerWallpaperUpdate() {
        scope.launch {
            try {
                com.cosmicocean.service.RealTimeWallpaperService.updateNow(context)
            } catch (_: Exception) {
                // Silent failure: privacy settings should still persist locally
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F0F1E)
    ) { scaffoldPadding ->
        Box(modifier = Modifier.padding(scaffoldPadding)) {
            PrivacySettingsScreen(
                preferences = preferences,
                onDefaultPrivacyLevelChanged = { level ->
                    scope.launch { repo.setDefaultPrivacyLevel(level) }
                    triggerWallpaperUpdate()
                },
                onHideAllTasksModeChanged = { enabled ->
                    scope.launch { repo.setHideAllTasksMode(enabled) }
                    triggerWallpaperUpdate()
                },
                onNavigateBack = onNavigateBack
            )
        }
    }
}
