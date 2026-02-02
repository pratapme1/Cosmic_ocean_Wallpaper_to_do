package com.cosmicocean.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.network.ApiService
import com.cosmicocean.ui.state.*
import com.cosmicocean.service.RealTimeWallpaperService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * LOCAL-FIRST: EnvironmentSettingsViewModel
 *
 * All preference changes:
 * 1. Save to local DataStore FIRST (instant UI feedback)
 * 2. Trigger wallpaper update immediately
 * 3. Sync to backend in background (non-blocking)
 */
class EnvironmentSettingsViewModel(
    private val apiService: ApiService,
    private val wallpaperPreferences: com.cosmicocean.utils.WallpaperPreferencesManager,
    private val environmentRepository: EnvironmentPreferencesRepository? = null
) : ViewModel() {

    private val TAG = "EnvSettingsVM"

    private val _uiState = MutableStateFlow(EnvironmentSettingsUiState())
    val uiState: StateFlow<EnvironmentSettingsUiState> = _uiState.asStateFlow()

    init {
        // LOCAL-FIRST: Observe local preferences
        environmentRepository?.let { repo ->
            viewModelScope.launch {
                repo.preferencesFlow.collectLatest { prefs ->
                    _uiState.update {
                        it.copy(
                            preferences = prefs,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    /**
     * LOCAL-FIRST: Load preferences from local storage first
     * Optionally sync with backend in background
     */
    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // LOCAL-FIRST: If we have repository, preferences are already loaded via Flow
            if (environmentRepository != null) {
                _uiState.update { it.copy(isLoading = false) }
                // Background sync with backend (non-blocking)
                syncWithBackend()
                return@launch
            }

            // Fallback: Load from API if no local repository
            try {
                val response = apiService.getPreferences()
                if (response.isSuccessful && response.body() != null) {
                    val apiPrefs = response.body()!!
                    _uiState.update {
                        it.copy(
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
                                wallpaperMode = apiPrefs.wallpaperMode ?: "generated",
                                isWallpaperEnabled = wallpaperPreferences.isWallpaperEnabled()
                            ),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load from API: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Background sync with backend (non-blocking)
     */
    private fun syncWithBackend() {
        viewModelScope.launch {
            try {
                val response = apiService.getPreferences()
                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Backend sync successful")
                    // Could merge with local preferences if needed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend sync failed (offline?): ${e.message}")
                // Silent failure - local-first continues working
            }
        }
    }

    /**
     * LOCAL-FIRST: Update preference
     * 1. Save to local storage FIRST
     * 2. Trigger wallpaper update
     * 3. Sync to backend in background
     */
    fun updatePreference(key: String, value: Any, context: Context? = null) {
        viewModelScope.launch {
            // LOCAL-FIRST: Save to local storage first
            environmentRepository?.let { repo ->
                try {
                    when (key) {
                        "time_of_day_mode" -> {
                            val mode = if (value.toString().lowercase() == "manual")
                                TimeOfDayMode.MANUAL else TimeOfDayMode.AUTO
                            repo.setTimeOfDayMode(mode)
                        }
                        "manual_time_period" -> repo.setManualTimePeriod(value.toString())
                        "weather_overlay_enabled" -> repo.setWeatherOverlayEnabled(value as Boolean)
                        "particle_intensity" -> {
                            val intensity = when (value.toString().lowercase()) {
                                "low" -> ParticleIntensity.LOW
                                "high" -> ParticleIntensity.HIGH
                                else -> ParticleIntensity.MEDIUM
                            }
                            repo.setParticleIntensity(intensity)
                        }
                        "wallpaper_mode" -> repo.setWallpaperMode(value.toString())
                    }
                    Log.d(TAG, "LOCAL-FIRST: Saved $key = $value locally")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save locally: ${e.message}")
                }
            }

            // Trigger immediate wallpaper update
            context?.let { RealTimeWallpaperService.updateNow(it) }

            // Background sync to backend (non-blocking)
            try {
                val body = mapOf(key to value)
                val response = apiService.updatePreferences(body)
                if (response.isSuccessful) {
                    Log.d(TAG, "Backend sync successful for $key")
                } else {
                    Log.w(TAG, "Backend sync failed for $key: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend sync failed (offline?): ${e.message}")
                // Silent failure - local-first continues working
            }
        }
    }

    /**
     * LOCAL-FIRST: Set wallpaper enabled
     */
    fun setWallpaperEnabled(enabled: Boolean, context: android.content.Context) {
        viewModelScope.launch {
            // Save to local storage
            wallpaperPreferences.setWallpaperEnabled(enabled)
            environmentRepository?.setWallpaperEnabled(enabled)

            _uiState.update {
                it.copy(preferences = it.preferences.copy(isWallpaperEnabled = enabled))
            }

            if (enabled) {
                RealTimeWallpaperService.start(context)
            } else {
                RealTimeWallpaperService.stop(context)
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
