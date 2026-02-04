package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.ui.state.*
import com.cosmicocean.viewmodel.EnvironmentSettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosmicocean.utils.HapticsUtil

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
    onEnvironmentEnabledChanged: (Boolean) -> Unit,
    onTimeOfDayModeChanged: (TimeOfDayMode) -> Unit,
    onManualTimePeriodChanged: (String) -> Unit,
    onWeatherOverlayChanged: (Boolean) -> Unit,
    onParticleIntensityChanged: (ParticleIntensity) -> Unit,
    onWallpaperModeChanged: (String) -> Unit,
    onWallpaperEnabledChanged: (Boolean) -> Unit,
    onContextModeChanged: (ContextMode) -> Unit,
    onManualContextChanged: (String) -> Unit,
    onDueHapticsEnabledChanged: (Boolean) -> Unit,
    onDueSoonMinutesChanged: (Int) -> Unit,
    onUrgentDueMinutesChanged: (Int) -> Unit,
    onOverdueMinutesChanged: (Int) -> Unit,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    onQuietHoursStartChanged: (Int) -> Unit,
    onQuietHoursEndChanged: (Int) -> Unit,
    onRespectDndChanged: (Boolean) -> Unit,
    onHapticsRateLimitChanged: (Int) -> Unit,
    onOverdueHeatmapEnabledChanged: (Boolean) -> Unit,
    onAmbientRemindersEnabledChanged: (Boolean) -> Unit,
    onUploadWallpaperClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePeriodDialog by remember { mutableStateOf(false) }
    var showParticleDialog by remember { mutableStateOf(false) }
    var showManualContextDialog by remember { mutableStateOf(false) }
    var showDueSoonDialog by remember { mutableStateOf(false) }
    var showUrgentDialog by remember { mutableStateOf(false) }
    var showOverdueDialog by remember { mutableStateOf(false) }
    var showQuietStartDialog by remember { mutableStateOf(false) }
    var showQuietEndDialog by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    val environmentEnabled = preferences.environmentEnabled
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 120.dp.toPx() }
    var swipeAccumulated by remember { mutableStateOf(0f) }
    var swipeTriggered by remember { mutableStateOf(false) }

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
                    text = "Customize your wallpaper's dynamic environment",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

            // ===== MASTER TOGGLE =====
            EnvironmentSectionHeader(title = "Environment Effects", icon = "🌍")

            EnvironmentSettingCard(
                title = "Enable Environment",
                subtitle = if (environmentEnabled) "On • Dynamic effects active" else "Off • Static wallpaper only",
                icon = "⚡"
            ) {
                Switch(
                    checked = environmentEnabled,
                    onCheckedChange = onEnvironmentEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentInfoCard(
                text = if (environmentEnabled)
                    "Dynamic environment effects are enabled. Time-of-day, weather, and particles will be applied."
                else
                    "Environment effects are disabled. Your wallpaper will stay static until you turn this back on.",
                color = if (environmentEnabled) Color(0xFF1A3A5F) else Color(0xFF3E3E3E)
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== TIME OF DAY SECTION =====
            EnvironmentSectionHeader(title = "Time of Day", icon = "🕐")

            // Auto vs Manual Mode
            EnvironmentSettingCard(
                title = "Environment Mode",
                subtitle = preferences.timeOfDayMode.displayName,
                icon = if (preferences.timeOfDayMode == TimeOfDayMode.AUTO) "🔄" else "🎛️",
                enabled = environmentEnabled
            ) {
                Switch(
                    checked = preferences.timeOfDayMode == TimeOfDayMode.AUTO,
                    onCheckedChange = { isAuto ->
                        onTimeOfDayModeChanged(if (isAuto) TimeOfDayMode.AUTO else TimeOfDayMode.MANUAL)
                    },
                    enabled = environmentEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentInfoCard(
                text = if (!environmentEnabled)
                    "Enable environment effects to use time-of-day settings."
                else if (preferences.timeOfDayMode == TimeOfDayMode.AUTO)
                    "🔄 Environment automatically changes based on your local time: Dawn, Morning, Afternoon, Evening, Night"
                else
                    "🎛️ Environment is fixed to your chosen time period",
                color = if (environmentEnabled) Color(0xFF1A3A5F) else Color(0xFF3E3E3E)
            )

            // Manual Time Period Selection (only when manual mode)
            if (environmentEnabled && preferences.timeOfDayMode == TimeOfDayMode.MANUAL) {
                val currentPeriod = TimePeriod.values().find { it.id == preferences.manualTimePeriod }
                    ?: TimePeriod.MORNING

                EnvironmentSettingCard(
                    title = "Fixed Environment",
                    subtitle = "${currentPeriod.icon} ${currentPeriod.displayName}",
                    icon = "🎨",
                    onClick = { showTimePeriodDialog = true },
                    enabled = environmentEnabled
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
                icon = "☁️",
                enabled = environmentEnabled
            ) {
                Switch(
                    checked = preferences.weatherOverlayEnabled,
                    onCheckedChange = onWeatherOverlayChanged,
                    enabled = environmentEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }

            if (!environmentEnabled) {
                EnvironmentInfoCard(
                    text = "Enable environment effects to use the weather overlay.",
                    color = Color(0xFF3E3E3E)
                )
            } else if (preferences.weatherOverlayEnabled) {
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

            EnvironmentSettingCard(
                title = "Overdue Heatmap",
                subtitle = if (preferences.overdueHeatmapEnabled) "On • Visual urgency overlay" else "Off",
                icon = "🔥",
                enabled = environmentEnabled
            ) {
                Switch(
                    checked = preferences.overdueHeatmapEnabled,
                    onCheckedChange = onOverdueHeatmapEnabledChanged,
                    enabled = environmentEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF7043),
                        checkedTrackColor = Color(0xFFFF7043).copy(alpha = 0.5f)
                    )
                )
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== CONTEXT MODE SECTION =====
            EnvironmentSectionHeader(title = "Context Mode", icon = "📍")

            EnvironmentSettingCard(
                title = "Context Mode",
                subtitle = preferences.contextMode.displayName,
                icon = if (preferences.contextMode == ContextMode.AUTO) "🛰️" else "🖐️",
                enabled = true
            ) {
                Switch(
                    checked = preferences.contextMode == ContextMode.AUTO,
                    onCheckedChange = { isAuto ->
                        onContextModeChanged(if (isAuto) ContextMode.AUTO else ContextMode.MANUAL)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5FF),
                        checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.contextMode == ContextMode.MANUAL) {
                EnvironmentSettingCard(
                    title = "Manual Context",
                    subtitle = preferences.manualContext.replaceFirstChar { it.uppercase() },
                    icon = "🏷️",
                    onClick = { showManualContextDialog = true }
                ) {
                    Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== DUE ALERTS SECTION =====
            EnvironmentSectionHeader(title = "Due Alerts", icon = "🔔")

            EnvironmentSettingCard(
                title = "Due Haptics",
                subtitle = if (preferences.dueHapticsEnabled) "On • Vibration alerts active" else "Off • No vibration alerts",
                icon = "📳"
            ) {
                Switch(
                    checked = preferences.dueHapticsEnabled,
                    onCheckedChange = onDueHapticsEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFB74D),
                        checkedTrackColor = Color(0xFFFFB74D).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentSettingCard(
                title = "Due Soon Threshold",
                subtitle = "${preferences.dueSoonMinutes} minutes",
                icon = "⏳",
                enabled = preferences.dueHapticsEnabled,
                onClick = { showDueSoonDialog = true }
            ) {
                Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }

            EnvironmentSettingCard(
                title = "Urgent Threshold",
                subtitle = "${preferences.urgentDueMinutes} minutes",
                icon = "⚠️",
                enabled = preferences.dueHapticsEnabled,
                onClick = { showUrgentDialog = true }
            ) {
                Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }

            EnvironmentSettingCard(
                title = "Overdue Threshold",
                subtitle = "${preferences.overdueMinutes} minutes after due",
                icon = "⏰",
                enabled = preferences.dueHapticsEnabled,
                onClick = { showOverdueDialog = true }
            ) {
                Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }

            EnvironmentSettingCard(
                title = "Quiet Hours",
                subtitle = if (preferences.quietHoursEnabled) "On • ${formatHour(preferences.quietHoursStart)} to ${formatHour(preferences.quietHoursEnd)}" else "Off",
                icon = "🌙"
            ) {
                Switch(
                    checked = preferences.quietHoursEnabled,
                    onCheckedChange = onQuietHoursEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF7E57C2),
                        checkedTrackColor = Color(0xFF7E57C2).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.quietHoursEnabled) {
                EnvironmentSettingCard(
                    title = "Quiet Hours Start",
                    subtitle = formatHour(preferences.quietHoursStart),
                    icon = "🕙",
                    enabled = preferences.dueHapticsEnabled,
                    onClick = { showQuietStartDialog = true }
                ) {
                    Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
                }
                EnvironmentSettingCard(
                    title = "Quiet Hours End",
                    subtitle = formatHour(preferences.quietHoursEnd),
                    icon = "🕖",
                    enabled = preferences.dueHapticsEnabled,
                    onClick = { showQuietEndDialog = true }
                ) {
                    Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
                }
            }

            EnvironmentSettingCard(
                title = "Respect DND",
                subtitle = if (preferences.respectDnd) "On • Haptics muted during Do Not Disturb" else "Off",
                icon = "🚫",
                enabled = preferences.dueHapticsEnabled
            ) {
                Switch(
                    checked = preferences.respectDnd,
                    onCheckedChange = onRespectDndChanged,
                    enabled = preferences.dueHapticsEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFEF5350),
                        checkedTrackColor = Color(0xFFEF5350).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentSettingCard(
                title = "Haptics Rate Limit",
                subtitle = "${preferences.hapticsRateLimitMinutes} minutes",
                icon = "⏱️",
                enabled = preferences.dueHapticsEnabled,
                onClick = { showRateLimitDialog = true }
            ) {
                Text(text = "Change", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }

            EnvironmentSettingCard(
                title = "Ambient Reminders",
                subtitle = if (preferences.ambientRemindersEnabled) "On • Subtle reminders when idle" else "Off",
                icon = "🔔",
                enabled = true
            ) {
                Switch(
                    checked = preferences.ambientRemindersEnabled,
                    onCheckedChange = onAmbientRemindersEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF81C784),
                        checkedTrackColor = Color(0xFF81C784).copy(alpha = 0.5f)
                    )
                )
            }

            EnvironmentSettingCard(
                title = "Test Vibration",
                subtitle = "Tap to test your haptics",
                icon = "🧪",
                enabled = preferences.dueHapticsEnabled,
                onClick = { HapticsUtil.vibrate(context, durationMs = 70) }
            ) {
                Text(text = "Test", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // ===== PARTICLE EFFECTS SECTION =====
            EnvironmentSectionHeader(title = "Particle Effects", icon = "✨")

            EnvironmentSettingCard(
                title = "Particle Intensity",
                subtitle = preferences.particleIntensity.displayName,
                icon = "🎆",
                onClick = { showParticleDialog = true },
                enabled = environmentEnabled
            ) {
                Text(
                    text = "Change",
                    color = Color(0xFFDA70D6),
                    fontSize = 14.sp
                )
            }

            EnvironmentInfoCard(
                text = if (!environmentEnabled)
                    "Enable environment effects to adjust particle intensity."
                else when (preferences.particleIntensity) {
                    ParticleIntensity.LOW -> "✨ Subtle particles - minimal visual distraction, great for focus"
                    ParticleIntensity.MEDIUM -> "✨ Balanced particles - pleasant atmosphere without overwhelming"
                    ParticleIntensity.HIGH -> "✨ Rich particles - immersive cosmic experience"
                },
                color = if (environmentEnabled) Color(0xFF3A1E5F) else Color(0xFF3E3E3E)
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

    if (showManualContextDialog) {
        ContextDialog(
            selected = preferences.manualContext,
            onSelect = {
                onManualContextChanged(it)
                showManualContextDialog = false
            },
            onDismiss = { showManualContextDialog = false }
        )
    }

    if (showDueSoonDialog) {
        ThresholdDialog(
            title = "Due Soon Threshold",
            options = listOf(10, 30, 60),
            selected = preferences.dueSoonMinutes,
            onSelect = {
                onDueSoonMinutesChanged(it)
                showDueSoonDialog = false
            },
            onDismiss = { showDueSoonDialog = false }
        )
    }

    if (showUrgentDialog) {
        ThresholdDialog(
            title = "Urgent Threshold",
            options = listOf(5, 10, 15),
            selected = preferences.urgentDueMinutes,
            onSelect = {
                onUrgentDueMinutesChanged(it)
                showUrgentDialog = false
            },
            onDismiss = { showUrgentDialog = false }
        )
    }

    if (showOverdueDialog) {
        ThresholdDialog(
            title = "Overdue Threshold",
            options = listOf(30, 60, 120),
            selected = preferences.overdueMinutes,
            suffix = "minutes after due",
            onSelect = {
                onOverdueMinutesChanged(it)
                showOverdueDialog = false
            },
            onDismiss = { showOverdueDialog = false }
        )
    }

    if (showQuietStartDialog) {
        HourDialog(
            title = "Quiet Hours Start",
            selected = preferences.quietHoursStart,
            onSelect = {
                onQuietHoursStartChanged(it)
                showQuietStartDialog = false
            },
            onDismiss = { showQuietStartDialog = false }
        )
    }

    if (showQuietEndDialog) {
        HourDialog(
            title = "Quiet Hours End",
            selected = preferences.quietHoursEnd,
            onSelect = {
                onQuietHoursEndChanged(it)
                showQuietEndDialog = false
            },
            onDismiss = { showQuietEndDialog = false }
        )
    }

    if (showRateLimitDialog) {
        ThresholdDialog(
            title = "Haptics Rate Limit",
            options = listOf(10, 30, 60),
            selected = preferences.hapticsRateLimitMinutes,
            suffix = "minutes",
            onSelect = {
                onHapticsRateLimitChanged(it)
                showRateLimitDialog = false
            },
            onDismiss = { showRateLimitDialog = false }
        )
    }
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
    enabled: Boolean = true,
    trailing: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (onClick != null && enabled) it.clickable(onClick = onClick) else it
            }
            .alpha(if (enabled) 1f else 0.45f),
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

@Composable
private fun ThresholdDialog(
    title: String,
    options: List<Int>,
    selected: Int,
    suffix: String = "minutes",
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$value $suffix")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun HourDialog(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hours = (0..23).toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hours.forEach { hour ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hour) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = hour == selected,
                            onClick = { onSelect(hour) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(formatHour(hour))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatHour(hour: Int): String {
    val normalized = ((hour % 24) + 24) % 24
    val suffix = if (normalized < 12) "AM" else "PM"
    val display = when (val h = normalized % 12) {
        0 -> 12
        else -> h
    }
    return "$display $suffix"
}

@Composable
private fun ContextDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("home", "work", "grocery", "gym", "commute", "health", "custom")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Context") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
                    onEnvironmentEnabledChanged = { enabled ->
                        viewModel.updatePreference("environment_enabled", enabled, context)
                    },
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
                    onContextModeChanged = { mode ->
                        viewModel.updatePreference("context_mode", mode.name.lowercase(), context)
                    },
                    onManualContextChanged = { contextValue ->
                        viewModel.updatePreference("manual_context", contextValue, context)
                    },
                    onDueHapticsEnabledChanged = { enabled ->
                        viewModel.updatePreference("due_haptics_enabled", enabled, context)
                    },
                    onDueSoonMinutesChanged = { minutes ->
                        viewModel.updatePreference("due_soon_minutes", minutes, context)
                    },
                    onUrgentDueMinutesChanged = { minutes ->
                        viewModel.updatePreference("urgent_due_minutes", minutes, context)
                    },
                    onOverdueMinutesChanged = { minutes ->
                        viewModel.updatePreference("overdue_minutes", minutes, context)
                    },
                    onQuietHoursEnabledChanged = { enabled ->
                        viewModel.updatePreference("quiet_hours_enabled", enabled, context)
                    },
                    onQuietHoursStartChanged = { hour ->
                        viewModel.updatePreference("quiet_hours_start", hour, context)
                    },
                    onQuietHoursEndChanged = { hour ->
                        viewModel.updatePreference("quiet_hours_end", hour, context)
                    },
                    onRespectDndChanged = { enabled ->
                        viewModel.updatePreference("respect_dnd", enabled, context)
                    },
                    onHapticsRateLimitChanged = { minutes ->
                        viewModel.updatePreference("haptics_rate_limit_minutes", minutes, context)
                    },
                    onOverdueHeatmapEnabledChanged = { enabled ->
                        viewModel.updatePreference("overdue_heatmap_enabled", enabled, context)
                    },
                    onAmbientRemindersEnabledChanged = { enabled ->
                        viewModel.updatePreference("ambient_reminders_enabled", enabled, context)
                    },
                    onUploadWallpaperClick = {},
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}
