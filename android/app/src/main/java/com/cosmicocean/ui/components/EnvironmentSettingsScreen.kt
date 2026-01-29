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
import kotlinx.coroutines.launch

private const val TAG = "EnvironmentSettings"

data class EnvironmentPreferences(
    val timeOfDayMode: TimeOfDayMode = TimeOfDayMode.AUTO,
    val manualTimePeriod: String = "morning",  // Used when mode is MANUAL
    val weatherOverlayEnabled: Boolean = true,
    val particleIntensity: ParticleIntensity = ParticleIntensity.MEDIUM,
    val wallpaperMode: String = "generated" // generated, custom
)

enum class TimeOfDayMode(val displayName: String, val description: String) {
    AUTO("Automatic", "Environment changes based on your local time"),
    MANUAL("Manual", "Choose a fixed time-of-day environment")
}

enum class TimePeriod(
    val id: String,
    val displayName: String,
    val icon: String,
    val description: String,
    val gradientColors: List<Color>
) {
    DAWN("dawn", "Dawn", "🌅", "5-7 AM • Warm sunrise rays", listOf(Color(0xFF4a1942), Color(0xFFf2994a))),
    MORNING("morning", "Morning", "☀️", "7 AM-12 PM • Bright and clear", listOf(Color(0xFF87ceeb), Color(0xFFe0f4ff))),
    AFTERNOON("afternoon", "Afternoon", "🌤️", "12-5 PM • Deep blue sky", listOf(Color(0xFF1e3c72), Color(0xFF87ceeb))),
    EVENING("evening", "Evening", "🌆", "5-8 PM • Sunset glow", listOf(Color(0xFF614385), Color(0xFFf8b500))),
    NIGHT("night", "Night", "🌙", "8 PM-5 AM • Starry sky", listOf(Color(0xFF0d1b2a), Color(0xFF3a506b)))
}

enum class ParticleIntensity(val displayName: String, val description: String, val multiplier: Float) {
    LOW("Low", "Subtle particles for minimal distraction", 0.5f),
    MEDIUM("Medium", "Balanced particle effects", 1.0f),
    HIGH("High", "Rich particle atmosphere", 1.5f)
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

            // ===== CUSTOM WALLPAPER SECTION (Epic 11) =====
            EnvironmentSectionHeader(title = "Wallpaper Source", icon = "🖼️")

            EnvironmentSettingCard(
                title = "Use Custom Image",
                subtitle = if (preferences.wallpaperMode == "custom") "Showing your uploaded image" else "Use generated cosmic theme",
                icon = "📸"
            ) {
                Switch(
                    checked = preferences.wallpaperMode == "custom",
                    onCheckedChange = { isCustom ->
                        onWallpaperModeChanged(if (isCustom) "custom" else "generated")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFE040FB),
                        checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.5f)
                    )
                )
            }

            if (preferences.wallpaperMode == "custom") {
                Button(
                    onClick = onUploadWallpaperClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A3E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFE040FB))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload New Image", color = Color.White)
                }

                EnvironmentInfoCard(
                    text = "Your image will have a subtle dark overlay added to ensure text remains readable.",
                    color = Color(0xFF3E2A5F)
                )
            } else {
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
                TimePeriod.values().forEach { period ->
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
                ParticleIntensity.values().forEach { intensity ->
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
    apiService: ApiService,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(EnvironmentPreferences()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var loadedFromApi by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // File picker for wallpaper upload - defined at top level
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                isLoading = true
                try {
                    // 1. Create temporary file from URI
                    val inputStream = contentResolver.openInputStream(selectedUri)
                    val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
                    tempFile.outputStream().use { output ->
                        inputStream?.copyTo(output)
                    }

                    // 2. Prepare Multipart body
                    val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", tempFile.name, requestFile)

                    // 3. Upload to API
                    val response = apiService.uploadWallpaper(body)

                    if (response.isSuccessful) {
                        Log.d(TAG, "Wallpaper upload successful")
                        successMessage = "Wallpaper uploaded!"
                        preferences = preferences.copy(wallpaperMode = "custom")
                    } else {
                        Log.e(TAG, "Upload failed: ${response.code()}")
                        errorMessage = "Upload failed"
                    }

                    // Cleanup
                    tempFile.delete()

                } catch (e: Exception) {
                    Log.e(TAG, "Exception uploading wallpaper", e)
                    errorMessage = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Load preferences from API on first composition
    LaunchedEffect(Unit) {
        Log.d(TAG, "Loading environment preferences from API...")
        try {
            val response = apiService.getPreferences()
            Log.d(TAG, "API Response: ${response.code()} - isSuccessful: ${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val apiPrefs = response.body()!!
                Log.d(TAG, "Loaded from API:")
                Log.d(TAG, "   time_of_day_mode: ${apiPrefs.timeOfDayMode}")
                Log.d(TAG, "   manual_time_period: ${apiPrefs.manualTimePeriod}")
                Log.d(TAG, "   weather_overlay_enabled: ${apiPrefs.weatherOverlayEnabled}")
                Log.d(TAG, "   particle_intensity: ${apiPrefs.particleIntensity}")
                Log.d(TAG, "   wallpaper_mode: ${apiPrefs.wallpaperMode}")

                preferences = EnvironmentPreferences(
                    timeOfDayMode = when (apiPrefs.timeOfDayMode?.lowercase()) {
                        "manual" -> TimeOfDayMode.MANUAL
                        else -> TimeOfDayMode.AUTO
                    },
                    manualTimePeriod = apiPrefs.manualTimePeriod ?: "morning",
                    weatherOverlayEnabled = apiPrefs.weatherOverlayEnabled ?: true,
                    particleIntensity = when (apiPrefs.particleIntensity?.lowercase()) {
                        "low" -> ParticleIntensity.LOW
                        "high" -> ParticleIntensity.HIGH
                        else -> ParticleIntensity.MEDIUM
                    },
                    wallpaperMode = apiPrefs.wallpaperMode ?: "generated"
                )
                loadedFromApi = true
                successMessage = "Settings loaded"
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API Error: ${response.code()} - $errorBody")
                loadedFromApi = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading preferences", e)
            loadedFromApi = true
        } finally {
            isLoading = false
        }
    }

    // Helper function to save preferences to API
    fun savePreference(key: String, value: Any) {
        Log.d(TAG, "Saving to API: $key = $value")
        scope.launch {
            try {
                val body = mapOf(key to value)
                val response = apiService.updatePreferences(body)

                if (response.isSuccessful) {
                    Log.d(TAG, "Saved successfully: $key")
                    successMessage = "Saved"
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Save failed: ${response.code()} - $errorBody")
                    errorMessage = "Save failed"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saving preference", e)
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
            if (isLoading && !loadedFromApi) {
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
                Column {
                    // Connection status banner
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
                                "Connected - changes sync automatically",
                                modifier = Modifier.padding(8.dp),
                                color = Color(0xFF81C784),
                                fontSize = 12.sp
                            )
                        }
                    }

                    EnvironmentSettingsScreen(
                        preferences = preferences,
                        onTimeOfDayModeChanged = { mode ->
                            Log.d(TAG, "Time of day mode changed: $mode")
                            preferences = preferences.copy(timeOfDayMode = mode)
                            savePreference("time_of_day_mode", mode.name.lowercase())
                        },
                        onManualTimePeriodChanged = { period ->
                            Log.d(TAG, "Manual time period changed: $period")
                            preferences = preferences.copy(manualTimePeriod = period)
                            savePreference("manual_time_period", period)
                        },
                        onWeatherOverlayChanged = { enabled ->
                            Log.d(TAG, "Weather overlay changed: $enabled")
                            preferences = preferences.copy(weatherOverlayEnabled = enabled)
                            savePreference("weather_overlay_enabled", enabled)
                        },
                        onParticleIntensityChanged = { intensity ->
                            Log.d(TAG, "Particle intensity changed: $intensity")
                            preferences = preferences.copy(particleIntensity = intensity)
                            savePreference("particle_intensity", intensity.name.lowercase())
                        },
                        onWallpaperModeChanged = { mode ->
                            Log.d(TAG, "Wallpaper mode changed: $mode")
                            preferences = preferences.copy(wallpaperMode = mode)
                            savePreference("wallpaper_mode", mode)
                        },
                        onUploadWallpaperClick = {
                            launcher.launch("image/*")
                        },
                        onNavigateBack = onNavigateBack
                    )
                }
            }
        }
    }
}

