package com.cosmicocean.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicocean.network.ApiService
import com.cosmicocean.ui.state.*
import com.cosmicocean.service.RealTimeWallpaperService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnvironmentSettingsViewModel(
    private val apiService: ApiService,
    private val wallpaperPreferences: com.cosmicocean.utils.WallpaperPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvironmentSettingsUiState())
    val uiState: StateFlow<EnvironmentSettingsUiState> = _uiState.asStateFlow()

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
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
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load: ${e.message}") }
            }
        }
    }

    fun updatePreference(key: String, value: Any, context: Context? = null) {
        viewModelScope.launch {
            try {
                // Optimistic update could go here
                val body = mapOf(key to value)
                val response = apiService.updatePreferences(body)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(successMessage = "Saved") }
                    context?.let { RealTimeWallpaperService.updateNow(it) }
                    // Reload to ensure synchronization
                    loadPreferences()
                } else {
                    _uiState.update { it.copy(errorMessage = "Save failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error: ${e.message}") }
            }
        }
    }

    fun setWallpaperEnabled(enabled: Boolean, context: android.content.Context) {
        wallpaperPreferences.setWallpaperEnabled(enabled)
        _uiState.update { 
            it.copy(preferences = it.preferences.copy(isWallpaperEnabled = enabled))
        }
        if (enabled) {
            com.cosmicocean.service.RealTimeWallpaperService.start(context)
        } else {
            com.cosmicocean.service.RealTimeWallpaperService.stop(context)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
