package com.cosmicocean.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Epic 10: Task Privacy & Masking
 * User preferences for task privacy on wallpaper display
 *
 * Privacy Levels:
 * - public: Show full task title (default)
 * - category: Show "{Category} task" instead of title
 * - initials: Show first character + "..."
 * - hidden: Don't show on wallpaper at all
 * - custom: Show privacy_display field
 *
 * Stored using Jetpack DataStore
 */

// DataStore instance for privacy preferences
private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_preferences")

/**
 * Privacy level options for task display on wallpaper
 */
enum class PrivacyLevel(val displayName: String, val description: String) {
    PUBLIC("Public", "Show full task title"),
    CATEGORY("Category Only", "Show category instead of title (e.g., 'Work task')"),
    INITIALS("Initials", "Show first letter only (e.g., 'M...')"),
    HIDDEN("Hidden", "Don't show on wallpaper"),
    CUSTOM("Custom Text", "Show your own custom display text")
}

/**
 * Privacy preferences data class
 */
data class PrivacyPreferences(
    val defaultPrivacyLevel: PrivacyLevel = PrivacyLevel.PUBLIC,  // Default for new tasks
    val autoHideWorkTasks: Boolean = false,                        // Auto-hide work tasks outside hours
    val workHoursStart: String = "09:00",                          // Work hours start (HH:mm)
    val workHoursEnd: String = "17:00",                            // Work hours end (HH:mm)
    val biometricRevealEnabled: Boolean = false,                   // Enable biometric reveal
    val hideAllTasksMode: Boolean = false                          // Master switch: hide all tasks
)

/**
 * Repository for privacy preferences
 * Handles local persistence via DataStore and sync with backend
 */
class PrivacyPreferencesRepository(private val context: Context) {

    companion object {
        private val DEFAULT_PRIVACY_LEVEL = stringPreferencesKey("default_privacy_level")
        private val AUTO_HIDE_WORK_TASKS = booleanPreferencesKey("auto_hide_work_tasks")
        private val WORK_HOURS_START = stringPreferencesKey("work_hours_start")
        private val WORK_HOURS_END = stringPreferencesKey("work_hours_end")
        private val BIOMETRIC_REVEAL_ENABLED = booleanPreferencesKey("biometric_reveal_enabled")
        private val HIDE_ALL_TASKS_MODE = booleanPreferencesKey("hide_all_tasks_mode")
    }

    /**
     * Flow of privacy preferences
     * Automatically updates when preferences change
     */
    val preferencesFlow: Flow<PrivacyPreferences> = context.privacyDataStore.data.map { preferences ->
        PrivacyPreferences(
            defaultPrivacyLevel = try {
                PrivacyLevel.valueOf(
                    preferences[DEFAULT_PRIVACY_LEVEL] ?: PrivacyLevel.PUBLIC.name
                )
            } catch (e: IllegalArgumentException) {
                PrivacyLevel.PUBLIC
            },
            autoHideWorkTasks = preferences[AUTO_HIDE_WORK_TASKS] ?: false,
            workHoursStart = preferences[WORK_HOURS_START] ?: "09:00",
            workHoursEnd = preferences[WORK_HOURS_END] ?: "17:00",
            biometricRevealEnabled = preferences[BIOMETRIC_REVEAL_ENABLED] ?: false,
            hideAllTasksMode = preferences[HIDE_ALL_TASKS_MODE] ?: false
        )
    }

    /**
     * Update default privacy level for new tasks
     */
    suspend fun setDefaultPrivacyLevel(level: PrivacyLevel) {
        context.privacyDataStore.edit { preferences ->
            preferences[DEFAULT_PRIVACY_LEVEL] = level.name
        }
    }

    /**
     * Update auto-hide work tasks setting
     */
    suspend fun setAutoHideWorkTasks(enabled: Boolean) {
        context.privacyDataStore.edit { preferences ->
            preferences[AUTO_HIDE_WORK_TASKS] = enabled
        }
    }

    /**
     * Update work hours start time
     */
    suspend fun setWorkHoursStart(time: String) {
        context.privacyDataStore.edit { preferences ->
            preferences[WORK_HOURS_START] = time
        }
    }

    /**
     * Update work hours end time
     */
    suspend fun setWorkHoursEnd(time: String) {
        context.privacyDataStore.edit { preferences ->
            preferences[WORK_HOURS_END] = time
        }
    }

    /**
     * Update biometric reveal setting
     */
    suspend fun setBiometricRevealEnabled(enabled: Boolean) {
        context.privacyDataStore.edit { preferences ->
            preferences[BIOMETRIC_REVEAL_ENABLED] = enabled
        }
    }

    /**
     * Update hide all tasks mode (master switch)
     */
    suspend fun setHideAllTasksMode(enabled: Boolean) {
        context.privacyDataStore.edit { preferences ->
            preferences[HIDE_ALL_TASKS_MODE] = enabled
        }
    }

    /**
     * Update all preferences at once
     */
    suspend fun updateAll(prefs: PrivacyPreferences) {
        context.privacyDataStore.edit { preferences ->
            preferences[DEFAULT_PRIVACY_LEVEL] = prefs.defaultPrivacyLevel.name
            preferences[AUTO_HIDE_WORK_TASKS] = prefs.autoHideWorkTasks
            preferences[WORK_HOURS_START] = prefs.workHoursStart
            preferences[WORK_HOURS_END] = prefs.workHoursEnd
            preferences[BIOMETRIC_REVEAL_ENABLED] = prefs.biometricRevealEnabled
            preferences[HIDE_ALL_TASKS_MODE] = prefs.hideAllTasksMode
        }
    }

    /**
     * Reset all privacy preferences to defaults
     */
    suspend fun resetToDefaults() {
        context.privacyDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Convert preferences to backend API format
     */
    fun toApiMap(prefs: PrivacyPreferences): Map<String, Any> {
        return mapOf(
            "default_privacy_level" to prefs.defaultPrivacyLevel.name.lowercase(),
            "auto_hide_work_tasks" to prefs.autoHideWorkTasks,
            "work_hours_start" to prefs.workHoursStart,
            "work_hours_end" to prefs.workHoursEnd,
            "biometric_reveal_enabled" to prefs.biometricRevealEnabled,
            "hide_all_tasks_mode" to prefs.hideAllTasksMode
        )
    }

    /**
     * Parse privacy level from backend API response
     */
    fun parsePrivacyLevelFromApi(value: String?): PrivacyLevel {
        return when (value?.lowercase()) {
            "public" -> PrivacyLevel.PUBLIC
            "category" -> PrivacyLevel.CATEGORY
            "initials" -> PrivacyLevel.INITIALS
            "hidden" -> PrivacyLevel.HIDDEN
            "custom" -> PrivacyLevel.CUSTOM
            else -> PrivacyLevel.PUBLIC
        }
    }
}
