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
import android.util.Log
import com.cosmicocean.data.PrivacyPreferences
import com.cosmicocean.data.PrivacyLevel
import com.cosmicocean.network.ApiService
import kotlinx.coroutines.launch

/**
 * Epic 10: Task Privacy & Masking
 * Settings screen for privacy preferences
 *
 * Allows users to:
 * - Set default privacy level for new tasks
 * - Enable auto-hide for work tasks outside work hours
 * - Configure work hours
 * - Enable biometric reveal
 * - Toggle hide all tasks mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    preferences: PrivacyPreferences,
    onDefaultPrivacyLevelChanged: (PrivacyLevel) -> Unit,
    onAutoHideWorkTasksChanged: (Boolean) -> Unit,
    onWorkHoursStartChanged: (String) -> Unit,
    onWorkHoursEndChanged: (String) -> Unit,
    onBiometricRevealChanged: (Boolean) -> Unit,
    onHideAllTasksModeChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPrivacyLevelDialog by remember { mutableStateOf(false) }
    var showStartTimeDialog by remember { mutableStateOf(false) }
    var showEndTimeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Settings") },
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
                title = "New Task Privacy",
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

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== WORK HOURS SECTION =====
            SectionHeader(title = "Work Hours Privacy", icon = "🏢")

            // Auto-hide Work Tasks
            PrivacySettingCard(
                title = "Auto-hide Work Tasks",
                subtitle = "Hide work tasks outside work hours",
                icon = "⏰"
            ) {
                Switch(
                    checked = preferences.autoHideWorkTasks,
                    onCheckedChange = onAutoHideWorkTasksChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2196F3),
                        checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.autoHideWorkTasks) {
                PrivacyInfoCard(
                    text = "Work category tasks will be hidden on your wallpaper outside the defined work hours.",
                    color = Color(0xFF1A3A5F)
                )

                // Work Hours Start
                PrivacySettingCard(
                    title = "Work Hours Start",
                    subtitle = formatTime(preferences.workHoursStart),
                    icon = "🌅",
                    onClick = { showStartTimeDialog = true }
                ) {
                    Text(
                        text = "Set",
                        color = Color(0xFF64B5F6),
                        fontSize = 14.sp
                    )
                }

                // Work Hours End
                PrivacySettingCard(
                    title = "Work Hours End",
                    subtitle = formatTime(preferences.workHoursEnd),
                    icon = "🌆",
                    onClick = { showEndTimeDialog = true }
                ) {
                    Text(
                        text = "Set",
                        color = Color(0xFF64B5F6),
                        fontSize = 14.sp
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== BIOMETRIC SECTION =====
            SectionHeader(title = "Quick Reveal", icon = "👆")

            // Biometric Reveal
            PrivacySettingCard(
                title = "Biometric Reveal",
                subtitle = "Use fingerprint to temporarily show tasks",
                icon = "🔓"
            ) {
                Switch(
                    checked = preferences.biometricRevealEnabled,
                    onCheckedChange = onBiometricRevealChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.biometricRevealEnabled) {
                PrivacyInfoCard(
                    text = "✨ Tap and authenticate to temporarily reveal hidden tasks on your wallpaper.",
                    color = Color(0xFF1E5F3A)
                )
            } else {
                PrivacyInfoCard(
                    text = "ℹ️ Enable to quickly reveal hidden tasks using your fingerprint.",
                    color = Color(0xFF3E3E3E)
                )
            }

            // Footer
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Privacy settings sync with your account",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
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

    // Time Picker Dialogs
    if (showStartTimeDialog) {
        TimePickerDialog(
            title = "Work Hours Start",
            currentTime = preferences.workHoursStart,
            onTimeSelected = { time ->
                onWorkHoursStartChanged(time)
                showStartTimeDialog = false
            },
            onDismiss = { showStartTimeDialog = false }
        )
    }

    if (showEndTimeDialog) {
        TimePickerDialog(
            title = "Work Hours End",
            currentTime = preferences.workHoursEnd,
            onTimeSelected = { time ->
                onWorkHoursEndChanged(time)
                showEndTimeDialog = false
            },
            onDismiss = { showEndTimeDialog = false }
        )
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
                PrivacyLevel.values().forEach { level ->
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

@Composable
private fun TimePickerDialog(
    title: String,
    currentTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeParts = currentTime.split(":")
    var selectedHour by remember { mutableStateOf(timeParts.getOrNull(0)?.toIntOrNull() ?: 9) }
    var selectedMinute by remember { mutableStateOf(timeParts.getOrNull(1)?.toIntOrNull() ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedHour = (selectedHour - 1 + 24) % 24 }
                            ) {
                                Text("-", fontSize = 24.sp, color = Color.White)
                            }
                            Text(
                                text = String.format("%02d", selectedHour),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9C27B0)
                            )
                            IconButton(
                                onClick = { selectedHour = (selectedHour + 1) % 24 }
                            ) {
                                Text("+", fontSize = 24.sp, color = Color.White)
                            }
                        }
                    }

                    Text(":", fontSize = 32.sp, color = Color.White)

                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedMinute = (selectedMinute - 5 + 60) % 60 }
                            ) {
                                Text("-", fontSize = 24.sp, color = Color.White)
                            }
                            Text(
                                text = String.format("%02d", selectedMinute),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9C27B0)
                            )
                            IconButton(
                                onClick = { selectedMinute = (selectedMinute + 5) % 60 }
                            ) {
                                Text("+", fontSize = 24.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
                    onTimeSelected(timeString)
                }
            ) {
                Text("Set", color = Color(0xFF9C27B0))
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

private fun getPrivacyLevelDescription(level: PrivacyLevel): String {
    return when (level) {
        PrivacyLevel.PUBLIC -> "👁️ Tasks will show their full title on the wallpaper."
        PrivacyLevel.CATEGORY -> "📁 Tasks will show as 'Work task', 'Personal task', etc."
        PrivacyLevel.INITIALS -> "🔤 Tasks will show only the first letter (e.g., 'M...')."
        PrivacyLevel.HIDDEN -> "🙈 Private tasks won't appear on the wallpaper at all."
        PrivacyLevel.CUSTOM -> "✏️ You can set custom display text for each private task."
    }
}

private fun formatTime(time: String): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", displayHour, minute, amPm)
}

private const val TAG = "PrivacySettings"

/**
 * Stateful wrapper for PrivacySettingsScreen that handles state internally
 * Loads preferences from API and saves changes back to API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsWrapper(
    apiService: ApiService,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(PrivacyPreferences()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var loadedFromApi by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Load preferences from API on first composition
    LaunchedEffect(Unit) {
        Log.d(TAG, "🔄 Loading preferences ${if (com.cosmicocean.BuildConfig.LOCAL_ONLY) "locally" else "from API"}...")
        try {
            val response = apiService.getPreferences()
            Log.d(TAG, "📥 API Response: ${response.code()} - isSuccessful: ${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val apiPrefs = response.body()!!
                Log.d(TAG, "✅ Loaded from API:")
                Log.d(TAG, "   default_privacy_level: ${apiPrefs.defaultPrivacyLevel}")
                Log.d(TAG, "   auto_hide_work_tasks: ${apiPrefs.autoHideWorkTasks}")
                Log.d(TAG, "   hide_all_tasks_mode: ${apiPrefs.hideAllTasksMode}")
                Log.d(TAG, "   work_hours: ${apiPrefs.workHoursStart} - ${apiPrefs.workHoursEnd}")

                // Parse work hours - handle "HH:MM:SS" format from backend
                val startTime = apiPrefs.workHoursStart?.take(5) ?: "09:00"
                val endTime = apiPrefs.workHoursEnd?.take(5) ?: "17:00"

                preferences = PrivacyPreferences(
                    defaultPrivacyLevel = PrivacyLevel.fromString(apiPrefs.defaultPrivacyLevel ?: "public"),
                    autoHideWorkTasks = apiPrefs.autoHideWorkTasks,
                    workHoursStart = startTime,
                    workHoursEnd = endTime,
                    biometricRevealEnabled = apiPrefs.biometricRevealEnabled,
                    hideAllTasksMode = apiPrefs.hideAllTasksMode
                )
                loadedFromApi = !com.cosmicocean.BuildConfig.LOCAL_ONLY
                successMessage = if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
                    "Settings loaded locally"
                } else {
                    "Settings loaded from server"
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ API Error: ${response.code()} - $errorBody")
                errorMessage = "Failed to load (${response.code()}): $errorBody"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception loading preferences", e)
            errorMessage = "Network error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Helper function to save preferences to API
    fun savePreference(key: String, value: Any) {
        Log.d(TAG, "💾 Saving ${if (com.cosmicocean.BuildConfig.LOCAL_ONLY) "locally" else "to API"}: $key = $value")
        scope.launch {
            try {
                val body = mapOf(key to value)
                val response = apiService.updatePreferences(body)

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Saved successfully: $key")
                    successMessage = if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
                        "Saved locally: $key"
                    } else {
                        "Saved: $key"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ Save failed: ${response.code()} - $errorBody")
                    errorMessage = "Save failed: $errorBody"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception saving preference", e)
                errorMessage = "Save error: ${e.message}"
            }
        }
    }

    // Show snackbar for messages
    LaunchedEffect(errorMessage, successMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            errorMessage = null
        }
        successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            successMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F0F1E)
    ) { scaffoldPadding ->
        Box(modifier = Modifier.padding(scaffoldPadding)) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF9C27B0))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
                                "Loading settings locally..."
                            } else {
                                "Loading settings from server..."
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Column {
                    // Debug info banner (remove in production)
                    if (loadedFromApi) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "✅ Connected to server - changes sync automatically",
                                modifier = Modifier.padding(8.dp),
                                color = Color(0xFF81C784),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1B1B2F).copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "🔒 Local-only mode - settings stay on this device",
                                modifier = Modifier.padding(8.dp),
                                color = Color(0xFF90CAF9),
                                fontSize = 12.sp
                            )
                        }
                    }

                    PrivacySettingsScreen(
                        preferences = preferences,
                        onDefaultPrivacyLevelChanged = { level ->
                            Log.d(TAG, "🔄 Privacy level changed: $level")
                            preferences = preferences.copy(defaultPrivacyLevel = level)
                            savePreference("default_privacy_level", level.name.lowercase())
                        },
                        onAutoHideWorkTasksChanged = { enabled ->
                            Log.d(TAG, "🔄 Auto-hide work tasks changed: $enabled")
                            preferences = preferences.copy(autoHideWorkTasks = enabled)
                            savePreference("auto_hide_work_tasks", enabled)
                        },
                        onWorkHoursStartChanged = { time ->
                            Log.d(TAG, "🔄 Work hours start changed: $time")
                            preferences = preferences.copy(workHoursStart = time)
                            savePreference("work_hours_start", time)
                        },
                        onWorkHoursEndChanged = { time ->
                            Log.d(TAG, "🔄 Work hours end changed: $time")
                            preferences = preferences.copy(workHoursEnd = time)
                            savePreference("work_hours_end", time)
                        },
                        onBiometricRevealChanged = { enabled ->
                            Log.d(TAG, "🔄 Biometric reveal changed: $enabled")
                            preferences = preferences.copy(biometricRevealEnabled = enabled)
                            savePreference("biometric_reveal_enabled", enabled)
                        },
                        onHideAllTasksModeChanged = { enabled ->
                            Log.d(TAG, "🔄 Hide all tasks mode changed: $enabled")
                            preferences = preferences.copy(hideAllTasksMode = enabled)
                            savePreference("hide_all_tasks_mode", enabled)
                        },
                        onNavigateBack = onNavigateBack
                    )
                }
            }
        }
    }
}
