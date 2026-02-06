package com.cosmicocean.ui.state

import androidx.compose.ui.graphics.Color

data class EnvironmentPreferences(
    val environmentEnabled: Boolean = false,
    val timeOfDayMode: TimeOfDayMode = TimeOfDayMode.AUTO,
    val manualTimePeriod: String = "morning",
    val weatherOverlayEnabled: Boolean = false,
    val particleIntensity: ParticleIntensity = ParticleIntensity.LOW,
    val wallpaperMode: String = "generated",
    val isWallpaperEnabled: Boolean = false,
    val contextMode: ContextMode = ContextMode.AUTO,
    val manualContext: String = "home",
    val focusModeEnabled: Boolean = false,
    val dueHapticsEnabled: Boolean = true,
    val dueSoonMinutes: Int = 30,
    val urgentDueMinutes: Int = 10,
    val overdueMinutes: Int = 60,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 7,
    val respectDnd: Boolean = true,
    val hapticsRateLimitMinutes: Int = 30,
    val overdueHeatmapEnabled: Boolean = false,
    val ambientRemindersEnabled: Boolean = false,
    val tutorialSeen: Boolean = false
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

enum class ContextMode(val displayName: String, val description: String) {
    AUTO("Automatic", "Uses location when available"),
    MANUAL("Manual", "You choose the current context")
}

data class EnvironmentSettingsUiState(
    val preferences: EnvironmentPreferences = EnvironmentPreferences(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
