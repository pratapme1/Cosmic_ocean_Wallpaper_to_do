package com.cosmicocean.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.ui.state.ParticleIntensity
import com.cosmicocean.ui.state.TimeOfDayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * LOCAL-FIRST: Environment Preferences Repository
 *
 * Stores environment settings locally using Jetpack DataStore.
 * Settings are applied immediately to local wallpaper generation.
 * Sync with backend happens in background via SyncManager.
 *
 * Settings:
 * - Time of day mode (auto/manual)
 * - Manual time period (morning/afternoon/evening/night)
 * - Weather overlay enabled
 * - Particle intensity (low/medium/high)
 * - Wallpaper mode (generated/custom)
 * - Wallpaper enabled
 */

// DataStore instance for environment preferences
private val Context.environmentDataStore: DataStore<Preferences> by preferencesDataStore(name = "environment_preferences")

/**
 * Repository for environment preferences
 * LOCAL-FIRST: All writes go to local DataStore first
 */
class EnvironmentPreferencesRepository(private val context: Context) {

    companion object {
        private val ENVIRONMENT_ENABLED = booleanPreferencesKey("environment_enabled")
        private val TIME_OF_DAY_MODE = stringPreferencesKey("time_of_day_mode")
        private val MANUAL_TIME_PERIOD = stringPreferencesKey("manual_time_period")
        private val WEATHER_OVERLAY_ENABLED = booleanPreferencesKey("weather_overlay_enabled")
        private val PARTICLE_INTENSITY = stringPreferencesKey("particle_intensity")
        private val WALLPAPER_MODE = stringPreferencesKey("wallpaper_mode")
        private val WALLPAPER_ENABLED = booleanPreferencesKey("wallpaper_enabled")
        private val CONTEXT_MODE = stringPreferencesKey("context_mode")
        private val MANUAL_CONTEXT = stringPreferencesKey("manual_context")
        private val FOCUS_MODE_ENABLED = booleanPreferencesKey("focus_mode_enabled")
        private val DUE_HAPTICS_ENABLED = booleanPreferencesKey("due_haptics_enabled")
        private val DUE_SOON_MINUTES = intPreferencesKey("due_soon_minutes")
        private val URGENT_DUE_MINUTES = intPreferencesKey("urgent_due_minutes")
        private val OVERDUE_MINUTES = intPreferencesKey("overdue_minutes")
        private val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        private val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
        private val RESPECT_DND = booleanPreferencesKey("respect_dnd")
        private val HAPTICS_RATE_LIMIT = intPreferencesKey("haptics_rate_limit_minutes")
        private val OVERDUE_HEATMAP_ENABLED = booleanPreferencesKey("overdue_heatmap_enabled")
        private val AMBIENT_REMINDERS_ENABLED = booleanPreferencesKey("ambient_reminders_enabled")
        private val HIGH_CONTRAST_TEXT_ENABLED = booleanPreferencesKey("high_contrast_text_enabled")
        private val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
        private val TUTORIAL_STEP = intPreferencesKey("tutorial_step")
    }

    /**
     * Flow of environment preferences
     * Automatically updates when preferences change
     */
    val preferencesFlow: Flow<EnvironmentPreferences> = context.environmentDataStore.data.map { preferences ->
        EnvironmentPreferences(
            environmentEnabled = preferences[ENVIRONMENT_ENABLED] ?: false,
            timeOfDayMode = try {
                TimeOfDayMode.valueOf(
                    preferences[TIME_OF_DAY_MODE] ?: TimeOfDayMode.AUTO.name
                )
            } catch (e: IllegalArgumentException) {
                TimeOfDayMode.AUTO
            },
            manualTimePeriod = preferences[MANUAL_TIME_PERIOD] ?: "morning",
            weatherOverlayEnabled = preferences[WEATHER_OVERLAY_ENABLED] ?: false,
            particleIntensity = try {
                ParticleIntensity.valueOf(
                    preferences[PARTICLE_INTENSITY] ?: ParticleIntensity.LOW.name
                )
            } catch (e: IllegalArgumentException) {
                ParticleIntensity.MEDIUM
            },
            wallpaperMode = preferences[WALLPAPER_MODE] ?: "generated",
            isWallpaperEnabled = preferences[WALLPAPER_ENABLED] ?: false,
            contextMode = try {
                com.cosmicocean.ui.state.ContextMode.valueOf(
                    preferences[CONTEXT_MODE] ?: com.cosmicocean.ui.state.ContextMode.AUTO.name
                )
            } catch (e: IllegalArgumentException) {
                com.cosmicocean.ui.state.ContextMode.AUTO
            },
            manualContext = preferences[MANUAL_CONTEXT] ?: "home",
            focusModeEnabled = preferences[FOCUS_MODE_ENABLED] ?: false,
            dueHapticsEnabled = preferences[DUE_HAPTICS_ENABLED] ?: true,
            dueSoonMinutes = preferences[DUE_SOON_MINUTES] ?: 30,
            urgentDueMinutes = preferences[URGENT_DUE_MINUTES] ?: 10,
            overdueMinutes = preferences[OVERDUE_MINUTES] ?: 60,
            quietHoursEnabled = preferences[QUIET_HOURS_ENABLED] ?: true,
            quietHoursStart = preferences[QUIET_HOURS_START] ?: 22,
            quietHoursEnd = preferences[QUIET_HOURS_END] ?: 7,
            respectDnd = preferences[RESPECT_DND] ?: true,
            hapticsRateLimitMinutes = preferences[HAPTICS_RATE_LIMIT] ?: 30,
            overdueHeatmapEnabled = preferences[OVERDUE_HEATMAP_ENABLED] ?: false,
            ambientRemindersEnabled = preferences[AMBIENT_REMINDERS_ENABLED] ?: false,
            highContrastTextEnabled = preferences[HIGH_CONTRAST_TEXT_ENABLED] ?: false,
            tutorialSeen = preferences[TUTORIAL_SEEN] ?: false,
            tutorialStep = preferences[TUTORIAL_STEP] ?: 0
        )
    }

    /**
     * Update time of day mode
     */
    suspend fun setTimeOfDayMode(mode: TimeOfDayMode) {
        context.environmentDataStore.edit { preferences ->
            preferences[TIME_OF_DAY_MODE] = mode.name
        }
    }

    /**
     * Update environment enabled state
     */
    suspend fun setEnvironmentEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[ENVIRONMENT_ENABLED] = enabled
        }
    }

    /**
     * Update manual time period
     */
    suspend fun setManualTimePeriod(period: String) {
        context.environmentDataStore.edit { preferences ->
            preferences[MANUAL_TIME_PERIOD] = period
        }
    }

    /**
     * Update weather overlay setting
     */
    suspend fun setWeatherOverlayEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[WEATHER_OVERLAY_ENABLED] = enabled
        }
    }

    /**
     * Update particle intensity
     */
    suspend fun setParticleIntensity(intensity: ParticleIntensity) {
        context.environmentDataStore.edit { preferences ->
            preferences[PARTICLE_INTENSITY] = intensity.name
        }
    }

    /**
     * Update wallpaper mode (generated/custom)
     */
    suspend fun setWallpaperMode(mode: String) {
        context.environmentDataStore.edit { preferences ->
            preferences[WALLPAPER_MODE] = mode
        }
    }

    /**
     * Update wallpaper enabled state
     */
    suspend fun setWallpaperEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[WALLPAPER_ENABLED] = enabled
        }
    }

    suspend fun setContextMode(mode: com.cosmicocean.ui.state.ContextMode) {
        context.environmentDataStore.edit { preferences ->
            preferences[CONTEXT_MODE] = mode.name
        }
    }

    suspend fun setManualContext(contextValue: String) {
        context.environmentDataStore.edit { preferences ->
            preferences[MANUAL_CONTEXT] = contextValue
        }
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[FOCUS_MODE_ENABLED] = enabled
        }
    }

    suspend fun setDueHapticsEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[DUE_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setDueSoonMinutes(minutes: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[DUE_SOON_MINUTES] = minutes
        }
    }

    suspend fun setUrgentDueMinutes(minutes: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[URGENT_DUE_MINUTES] = minutes
        }
    }

    suspend fun setOverdueMinutes(minutes: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[OVERDUE_MINUTES] = minutes
        }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[QUIET_HOURS_ENABLED] = enabled
        }
    }

    suspend fun setQuietHoursStart(hour: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[QUIET_HOURS_START] = hour
        }
    }

    suspend fun setQuietHoursEnd(hour: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[QUIET_HOURS_END] = hour
        }
    }

    suspend fun setRespectDnd(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[RESPECT_DND] = enabled
        }
    }

    suspend fun setHapticsRateLimit(minutes: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[HAPTICS_RATE_LIMIT] = minutes
        }
    }

    suspend fun setOverdueHeatmapEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[OVERDUE_HEATMAP_ENABLED] = enabled
        }
    }

    suspend fun setAmbientRemindersEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[AMBIENT_REMINDERS_ENABLED] = enabled
        }
    }

    suspend fun setHighContrastTextEnabled(enabled: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[HIGH_CONTRAST_TEXT_ENABLED] = enabled
        }
    }

    suspend fun setTutorialSeen(seen: Boolean) {
        context.environmentDataStore.edit { preferences ->
            preferences[TUTORIAL_SEEN] = seen
        }
    }

    suspend fun setTutorialStep(step: Int) {
        context.environmentDataStore.edit { preferences ->
            preferences[TUTORIAL_STEP] = step
        }
    }

    /**
     * Update all preferences at once
     */
    suspend fun updateAll(prefs: EnvironmentPreferences) {
        context.environmentDataStore.edit { preferences ->
            preferences[ENVIRONMENT_ENABLED] = prefs.environmentEnabled
            preferences[TIME_OF_DAY_MODE] = prefs.timeOfDayMode.name
            preferences[MANUAL_TIME_PERIOD] = prefs.manualTimePeriod
            preferences[WEATHER_OVERLAY_ENABLED] = prefs.weatherOverlayEnabled
            preferences[PARTICLE_INTENSITY] = prefs.particleIntensity.name
            preferences[WALLPAPER_MODE] = prefs.wallpaperMode
            preferences[WALLPAPER_ENABLED] = prefs.isWallpaperEnabled
            preferences[CONTEXT_MODE] = prefs.contextMode.name
            preferences[MANUAL_CONTEXT] = prefs.manualContext
            preferences[FOCUS_MODE_ENABLED] = prefs.focusModeEnabled
            preferences[DUE_HAPTICS_ENABLED] = prefs.dueHapticsEnabled
            preferences[DUE_SOON_MINUTES] = prefs.dueSoonMinutes
            preferences[URGENT_DUE_MINUTES] = prefs.urgentDueMinutes
            preferences[OVERDUE_MINUTES] = prefs.overdueMinutes
            preferences[QUIET_HOURS_ENABLED] = prefs.quietHoursEnabled
            preferences[QUIET_HOURS_START] = prefs.quietHoursStart
            preferences[QUIET_HOURS_END] = prefs.quietHoursEnd
            preferences[RESPECT_DND] = prefs.respectDnd
            preferences[HAPTICS_RATE_LIMIT] = prefs.hapticsRateLimitMinutes
            preferences[OVERDUE_HEATMAP_ENABLED] = prefs.overdueHeatmapEnabled
            preferences[AMBIENT_REMINDERS_ENABLED] = prefs.ambientRemindersEnabled
            preferences[HIGH_CONTRAST_TEXT_ENABLED] = prefs.highContrastTextEnabled
            preferences[TUTORIAL_SEEN] = prefs.tutorialSeen
            preferences[TUTORIAL_STEP] = prefs.tutorialStep
        }
    }

    /**
     * Reset all environment preferences to defaults
     */
    suspend fun resetToDefaults() {
        context.environmentDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Convert preferences to backend API format
     */
    fun toApiMap(prefs: EnvironmentPreferences): Map<String, Any> {
        return mapOf(
            "environment_enabled" to prefs.environmentEnabled,
            "time_of_day_mode" to prefs.timeOfDayMode.name.lowercase(),
            "manual_time_period" to prefs.manualTimePeriod,
            "weather_overlay_enabled" to prefs.weatherOverlayEnabled,
            "particle_intensity" to prefs.particleIntensity.name.lowercase(),
            "wallpaper_mode" to prefs.wallpaperMode,
            "context_mode" to prefs.contextMode.name.lowercase(),
            "manual_context" to prefs.manualContext,
            "focus_mode_enabled" to prefs.focusModeEnabled,
            "due_haptics_enabled" to prefs.dueHapticsEnabled,
            "due_soon_minutes" to prefs.dueSoonMinutes,
            "urgent_due_minutes" to prefs.urgentDueMinutes,
            "overdue_minutes" to prefs.overdueMinutes,
            "quiet_hours_enabled" to prefs.quietHoursEnabled,
            "quiet_hours_start" to prefs.quietHoursStart,
            "quiet_hours_end" to prefs.quietHoursEnd,
            "respect_dnd" to prefs.respectDnd,
            "haptics_rate_limit_minutes" to prefs.hapticsRateLimitMinutes,
            "overdue_heatmap_enabled" to prefs.overdueHeatmapEnabled,
            "ambient_reminders_enabled" to prefs.ambientRemindersEnabled,
            "high_contrast_text_enabled" to prefs.highContrastTextEnabled,
            "tutorial_seen" to prefs.tutorialSeen,
            "tutorial_step" to prefs.tutorialStep
        )
    }
}
