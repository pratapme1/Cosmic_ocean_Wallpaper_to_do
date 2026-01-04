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
 * Epic 8: LLM Intelligence Enhancement
 * User preferences for LLM parsing and message generation
 *
 * Stored using Jetpack DataStore (replaces SharedPreferences)
 */

// DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_preferences")

data class LLMPreferences(
    val advancedParsingEnabled: Boolean = true,        // Enable LLM parsing
    val showParsePreview: Boolean = true,              // Show preview before creating task
    val messageVoice: MessageVoice = MessageVoice.BALANCED, // Message generation tone
    val analyticsEnabled: Boolean = true               // Track parse accuracy for improvements
)

enum class MessageVoice(val displayName: String, val description: String) {
    MOTIVATIONAL("Motivational", "Encouraging and positive messages"),
    DIRECT("Direct", "Clear and concise messages"),
    BALANCED("Balanced", "Mix of motivation and directness"),
    HUMOROUS("Humorous", "Light-hearted and fun messages"),
    MINIMAL("Minimal", "Brief, essential information only")
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val ADVANCED_PARSING_ENABLED = booleanPreferencesKey("advanced_parsing_enabled")
        private val SHOW_PARSE_PREVIEW = booleanPreferencesKey("show_parse_preview")
        private val MESSAGE_VOICE = stringPreferencesKey("message_voice")
        private val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
    }

    /**
     * Flow of user preferences
     * Automatically updates when preferences change
     */
    val preferencesFlow: Flow<LLMPreferences> = context.dataStore.data.map { preferences ->
        LLMPreferences(
            advancedParsingEnabled = preferences[ADVANCED_PARSING_ENABLED] ?: true,
            showParsePreview = preferences[SHOW_PARSE_PREVIEW] ?: true,
            messageVoice = MessageVoice.valueOf(
                preferences[MESSAGE_VOICE] ?: MessageVoice.BALANCED.name
            ),
            analyticsEnabled = preferences[ANALYTICS_ENABLED] ?: true
        )
    }

    /**
     * Update advanced parsing enabled
     */
    suspend fun setAdvancedParsingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ADVANCED_PARSING_ENABLED] = enabled
        }
    }

    /**
     * Update show parse preview
     */
    suspend fun setShowParsePreview(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_PARSE_PREVIEW] = show
        }
    }

    /**
     * Update message voice
     */
    suspend fun setMessageVoice(voice: MessageVoice) {
        context.dataStore.edit { preferences ->
            preferences[MESSAGE_VOICE] = voice.name
        }
    }

    /**
     * Update analytics enabled
     */
    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ANALYTICS_ENABLED] = enabled
        }
    }

    /**
     * Reset all preferences to defaults
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
