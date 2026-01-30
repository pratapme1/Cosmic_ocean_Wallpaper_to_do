package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.cosmicocean.network.ApiService
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.ui.state.*
import com.cosmicocean.viewmodel.EnvironmentSettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

enum class TimePeriod(
    val id: String,
    val displayName: String,
    val icon: String,
    val description: String,
    val gradientColors: List<Color>
) {
    DAWN("dawn", "Dawn", "🌅", "5-7 AM • Warm sunrise rays", listOf(Color(0xFF4a1942), Color(0xFFf2994a))),
    MORNING("morning", "Morning", "☀️", "7 AM-12 PM • Bright and clear", listOf(Color(0xFF87ceeb), Color(0xFFe0f4ff))),
    AFTERNOON("afternoon", "afternoon", "🌤️", "12-5 PM • Deep blue sky", listOf(Color(0xFF1e3c72), Color(0xFF87ceeb))),
    EVENING("evening", "Evening", "🌆", "5-8 PM • Sunset glow", listOf(Color(0xFF614385), Color(0xFFf8b500))),
    NIGHT("night", "Night", "🌙", "8 PM-5 AM • Starry sky", listOf(Color(0xFF0d1b2a), Color(0xFF3a506b)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentSettingsScreen(
    preferences: EnvironmentPreferences,
    onTimeOfDayModeChanged: (TimeOfDayMode) -> Unit,
    onManualTimePeriodChanged: (String) -> Unit,
    onWeatherOverlayChanged: (Boolean) -> Unit,
    onParticleIntensityChanged: (ParticleIntensity) -> Unit,
    onWallpaperModeChanged: (String) -> Unit,
    onWallpaperEnabledChanged: (Boolean) -> Unit,
    onUploadWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePeriodDialog by remember { mutableStateOf(false) }
    var showParticleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Environment Settings") },
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
                text = "Customize your wallpaper's dynamic environment",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ===== TIME OF DAY SECTION =====
            EnvironmentSectionHeader(title = "Time of Day", icon = "🕐")

            // Auto vs Manual Mode
            EnvironmentSettingCard(
                title = "Environment Mode",
                subtitle = preferences.timeOfDayMode.displayName,
                icon = if (preferences.timeOfDayMode == TimeOfDayMode.AUTO) "🔄" else "🎛️"
            ) {
                Switch(
                    checked = preferences.timeOfDayMode == TimeOfDayMode.AUTO,
                    onCheckedChange = { isAuto ->
                        onTimeOfDayModeChanged(if (isAuto) TimeOfDayMode.AUTO else TimeOfDayMode.MANUAL)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentInfoCard(
                text = if (preferences.timeOfDayMode == TimeOfDayMode.AUTO)
                    "🔄 Environment automatically changes based on your local time: Dawn, Morning, Afternoon, Evening, Night"
                else
                    "🎛️ Environment is fixed to your chosen time period",
                color = Color(0xFF1A3A5F)
            )

            // Manual Time Period Selection (only when manual mode)
            if (preferences.timeOfDayMode == TimeOfDayMode.MANUAL) {
                val currentPeriod = TimePeriod.values().find { it.id == preferences.manualTimePeriod }
                    ?: TimePeriod.MORNING

                EnvironmentSettingCard(
                    title = "Fixed Environment",
                    subtitle = "${currentPeriod.icon} ${currentPeriod.displayName}",
                    icon = "🎨",
                    onClick = { showTimePeriodDialog = true }
                ) {
                    Text(
                        text = "Change",
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp
                    )
                }

                // Preview Card
                EnvironmentPreviewCard(period = currentPeriod)
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== WEATHER OVERLAY SECTION =====
            EnvironmentSectionHeader(title = "Weather Overlay", icon = "🌤️")

            EnvironmentSettingCard(
                title = "Productivity Weather",
                subtitle = "Show weather based on task status",
                icon = "☁️"
            ) {
                Switch(
                    checked = preferences.weatherOverlayEnabled,
                    onCheckedChange = onWeatherOverlayChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.weatherOverlayEnabled) {
                EnvironmentInfoCard(
                    text = """
                        |Weather reflects your productivity:
                        |🌈 Rainbow - All tasks completed!
                        |☀️ Clear - On track, no overdue tasks
                        |☁️ Cloudy - Slightly behind (1-2 overdue)
                        |🌧️ Overcast - Falling behind (3-5 overdue)
                        |⛈️ Storm - Critical state (5+ overdue)
                    """.trimMargin(),
                    color = Color(0xFF1E5F3A)
                )
            } else {
                EnvironmentInfoCard(
                    text = "Weather overlay is disabled. Enable to see productivity-based weather effects on your wallpaper.",
                    color = Color(0xFF3E3E3E)
                )
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== PARTICLE EFFECTS SECTION =====
            EnvironmentSectionHeader(title = "Particle Effects", icon = "✨")

            EnvironmentSettingCard(
                title = "Particle Intensity",
                subtitle = preferences.particleIntensity.displayName,
                icon = "🎆",
                onClick = { showParticleDialog = true }
            ) {
                Text(
                    text = "Change",
                    color = Color(0xFFDA70D6),
                    fontSize = 14.sp
                )
            }

            EnvironmentInfoCard(
                text = when (preferences.particleIntensity) {
                    ParticleIntensity.LOW -> "✨ Subtle particles - minimal visual distraction, great for focus"
                    ParticleIntensity.MEDIUM -> "✨ Balanced particles - pleasant atmosphere without overwhelming"
                    ParticleIntensity.HIGH -> "✨ Rich particles - immersive cosmic experience"
                },
                color = Color(0xFF3A1E5F)
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== WALLPAPER INTEGRATION SECTION =====
            EnvironmentSectionHeader(title = "System Integration", icon = "🖼️")

            EnvironmentSettingCard(
                title = "Live Wallpaper Sync",
                subtitle = if (preferences.isWallpaperEnabled) "Enabled • Updating every minute" else "Disabled • Manual use only",
                icon = "⚙️"
            ) {
                Switch(
                    checked = preferences.isWallpaperEnabled,
                    onCheckedChange = onWallpaperEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentInfoCard(
                text = if (preferences.isWallpaperEnabled)
                    "✅ Your system wallpaper is being updated automatically to reflect your cosmic ocean state."
                else
                    "❌ Automatic wallpaper updates are disabled. You can still manually refresh from the home screen.",
                color = if (preferences.isWallpaperEnabled) Color(0xFF1A5F5F) else Color(0xFF5F1A1A)
            )

            // Footer
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Changes apply on next wallpaper refresh",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Time Period Selection Dialog
    if (showTimePeriodDialog) {
        TimePeriodDialog(
            currentPeriod = preferences.manualTimePeriod,
            onPeriodSelected = { period ->
                onManualTimePeriodChanged(period)
                showTimePeriodDialog = false
            },
            onDismiss = { showTimePeriodDialog = false }
        )
    }

    // Particle Intensity Dialog
    if (showParticleDialog) {
        ParticleIntensityDialog(
            currentIntensity = preferences.particleIntensity,
            onIntensitySelected = { intensity ->
                onParticleIntensityChanged(intensity)
                showParticleDialog = false
            },
            onDismiss = { showParticleDialog = false }
        )
    }
}

@Composable
private fun EnvironmentSectionHeader(title: String, icon: String) {
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
            color = Color(0xFF00E5FF)
        )
    }
}

@Composable
private fun EnvironmentSettingCard(
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
private fun EnvironmentInfoCard(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun EnvironmentPreviewCard(period: TimePeriod) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(period.gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = period.icon,
                    fontSize = 40.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = period.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = period.description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TimePeriodDialog(
    currentPeriod: String,
    onPeriodSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Environment", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (period in TimePeriod.values()) {
                    TimePeriodOption(
                        period = period,
                        isSelected = period.id == currentPeriod,
                        onClick = { onPeriodSelected(period.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
private fun TimePeriodOption(
    period: TimePeriod,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF00E5FF).copy(alpha = 0.2f)
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
                // Mini gradient preview
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.verticalGradient(period.gradientColors)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = period.icon, fontSize = 20.sp)
                }
                Column {
                    Text(
                        text = period.displayName,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White
                    )
                    Text(
                        text = period.description,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            if (isSelected) {
                Text(text = "✓", fontSize = 20.sp, color = Color(0xFF00E5FF))
            }
        }
    }
}

@Composable
private fun ParticleIntensityDialog(
    currentIntensity: ParticleIntensity,
    onIntensitySelected: (ParticleIntensity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Particle Intensity", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (intensity in ParticleIntensity.values()) {
                    ParticleIntensityOption(
                        intensity = intensity,
                        isSelected = intensity == currentIntensity,
                        onClick = { onIntensitySelected(intensity) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFDA70D6))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
private fun ParticleIntensityOption(
    intensity: ParticleIntensity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (intensity) {
        ParticleIntensity.LOW -> "✨"
        ParticleIntensity.MEDIUM -> "🌟"
        ParticleIntensity.HIGH -> "💫"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFFDA70D6).copy(alpha = 0.2f)
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
                Text(text = icon, fontSize = 24.sp)
                Column {
                    Text(
                        text = intensity.displayName,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White
                    )
                    Text(
                        text = intensity.description,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            if (isSelected) {
                Text(text = "✓", fontSize = 20.sp, color = Color(0xFFDA70D6))
            }
        }
    }
}



/**
 * Stateful wrapper for EnvironmentSettingsScreen that handles state internally
 * Loads preferences from API and saves changes back to API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentSettingsWrapper(
    viewModel: EnvironmentSettingsViewModel,
    onNavigateBack: () -> Unit,
    onWallpaperEnabledChanged: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Trigger loading on first composition
    LaunchedEffect(Unit) {
        viewModel.loadPreferences()
    }

    // Show snackbar for messages
    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F0F1E)
    ) { scaffoldPadding ->
        Box(modifier = Modifier.padding(scaffoldPadding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Loading environment settings...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                EnvironmentSettingsScreen(
                    preferences = uiState.preferences,
                    onTimeOfDayModeChanged = { mode ->
                        viewModel.updatePreference("time_of_day_mode", mode.name.lowercase(), context)
                    },
                    onManualTimePeriodChanged = { period ->
                        viewModel.updatePreference("manual_time_period", period, context)
                    },
                    onWeatherOverlayChanged = { enabled ->
                        viewModel.updatePreference("weather_overlay_enabled", enabled, context)
                    },
                    onParticleIntensityChanged = { intensity ->
                        viewModel.updatePreference("particle_intensity", intensity.name.lowercase(), context)
                    },
                    onWallpaperModeChanged = { mode ->
                        viewModel.updatePreference("wallpaper_mode", mode, context)
                    },
                    onWallpaperEnabledChanged = { enabled ->
                        viewModel.setWallpaperEnabled(enabled, context)
                        onWallpaperEnabledChanged(enabled)
                    },
                    onUploadWallpaperClick = {},
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

