package com.cosmicocean.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        private val TIME_OF_DAY_MODE = stringPreferencesKey("time_of_day_mode")
        private val MANUAL_TIME_PERIOD = stringPreferencesKey("manual_time_period")
        private val WEATHER_OVERLAY_ENABLED = booleanPreferencesKey("weather_overlay_enabled")
        private val PARTICLE_INTENSITY = stringPreferencesKey("particle_intensity")
        private val WALLPAPER_MODE = stringPreferencesKey("wallpaper_mode")
        private val WALLPAPER_ENABLED = booleanPreferencesKey("wallpaper_enabled")
    }

    /**
     * Flow of environment preferences
     * Automatically updates when preferences change
     */
    val preferencesFlow: Flow<EnvironmentPreferences> = context.environmentDataStore.data.map { preferences ->
        EnvironmentPreferences(
            timeOfDayMode = try {
                TimeOfDayMode.valueOf(
                    preferences[TIME_OF_DAY_MODE] ?: TimeOfDayMode.AUTO.name
                )
            } catch (e: IllegalArgumentException) {
                TimeOfDayMode.AUTO
            },
            manualTimePeriod = preferences[MANUAL_TIME_PERIOD] ?: "morning",
            weatherOverlayEnabled = preferences[WEATHER_OVERLAY_ENABLED] ?: true,
            particleIntensity = try {
                ParticleIntensity.valueOf(
                    preferences[PARTICLE_INTENSITY] ?: ParticleIntensity.MEDIUM.name
                )
            } catch (e: IllegalArgumentException) {
                ParticleIntensity.MEDIUM
            },
            wallpaperMode = preferences[WALLPAPER_MODE] ?: "generated",
            isWallpaperEnabled = preferences[WALLPAPER_ENABLED] ?: false
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

    /**
     * Update all preferences at once
     */
    suspend fun updateAll(prefs: EnvironmentPreferences) {
        context.environmentDataStore.edit { preferences ->
            preferences[TIME_OF_DAY_MODE] = prefs.timeOfDayMode.name
            preferences[MANUAL_TIME_PERIOD] = prefs.manualTimePeriod
            preferences[WEATHER_OVERLAY_ENABLED] = prefs.weatherOverlayEnabled
            preferences[PARTICLE_INTENSITY] = prefs.particleIntensity.name
            preferences[WALLPAPER_MODE] = prefs.wallpaperMode
            preferences[WALLPAPER_ENABLED] = prefs.isWallpaperEnabled
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
            "time_of_day_mode" to prefs.timeOfDayMode.name.lowercase(),
            "manual_time_period" to prefs.manualTimePeriod,
            "weather_overlay_enabled" to prefs.weatherOverlayEnabled,
            "particle_intensity" to prefs.particleIntensity.name.lowercase(),
            "wallpaper_mode" to prefs.wallpaperMode
        )
    }
}
