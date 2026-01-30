package com.cosmicocean.ui.state

import androidx.compose.ui.graphics.Color

data class EnvironmentPreferences(
    val timeOfDayMode: TimeOfDayMode = TimeOfDayMode.AUTO,
    val manualTimePeriod: String = "morning",
    val weatherOverlayEnabled: Boolean = true,
    val particleIntensity: ParticleIntensity = ParticleIntensity.MEDIUM,
    val wallpaperMode: String = "generated",
    val isWallpaperEnabled: Boolean = false
)

enum class TimeOfDayMode(val displayName: String, val description: String) {
    AUTO("Automatic", "Environment changes based on your local time"),
    MANUAL("Manual", "Choose a fixed time-of-day environment")
}

enum class ParticleIntensity(val displayName: String, val description: String, val multiplier: Float) {
    LOW("Low", "Subtle particles for minimal distraction", 0.5f),
    MEDIUM("Medium", "Balanced particle effects", 1.0f),
    HIGH("High", "Rich particle atmosphere", 1.5f)
}

data class EnvironmentSettingsUiState(
    val preferences: EnvironmentPreferences = EnvironmentPreferences(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
