package com.cosmicocean.analytics

import android.content.Context
import android.util.Log
import com.cosmicocean.model.ParsedTaskResult
import com.cosmicocean.network.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

/**
 * Epic 8: LLM Intelligence Enhancement
 * Analytics tracker for LLM parsing events
 *
 * Tracks:
 * - Parse success/failure rates
 * - User edit frequency (indicates parse accuracy)
 * - Confidence scores
 * - Fallback reasons
 * - Parse duration
 *
 * Data helps improve AI accuracy over time
 */
class LLMAnalytics(
    private val context: Context,
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "LLMAnalytics"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Track parse attempt
     * Called when user submits task input
     */
    fun trackParseAttempt(
        input: String,
        result: ParsedTaskResult,
        durationMs: Long
    ) {
        scope.launch {
            try {
                val event = createParseEvent(
                    eventType = "parse_attempt",
                    input = input,
                    result = result,
                    durationMs = durationMs
                )

                logEvent(event)

                // TODO: Send to backend analytics endpoint
                // apiService.trackEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to track parse attempt: ${e.message}")
            }
        }
    }

    /**
     * Track when user edits parsed result
     * High edit rate = low accuracy
     */
    fun trackUserEdit(
        originalResult: ParsedTaskResult,
        editedFields: List<String>,
        reason: String = "user_correction"
    ) {
        scope.launch {
            try {
                val event = JSONObject().apply {
                    put("event_type", "user_edit")
                    put("timestamp", System.currentTimeMillis())
                    put("source", originalResult.source)
                    put("confidence", originalResult.confidence)
                    put("edited_fields", editedFields.joinToString(","))
                    put("edit_reason", reason)
                }

                logEvent(event)

                // TODO: Send to backend
                // apiService.trackEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to track user edit: ${e.message}")
            }
        }
    }

    /**
     * Track task creation (after confirmation)
     */
    fun trackTaskCreated(
        result: ParsedTaskResult,
        wasEdited: Boolean
    ) {
        scope.launch {
            try {
                val event = JSONObject().apply {
                    put("event_type", "task_created")
                    put("timestamp", System.currentTimeMillis())
                    put("source", result.source)
                    put("confidence", result.confidence)
                    put("was_edited", wasEdited)
                    put("category", result.category ?: "none")
                    put("priority", result.priority)
                    put("has_due_date", result.dueDate != null)
                    put("has_due_time", result.dueTime != null)
                }

                logEvent(event)

                // TODO: Send to backend
                // apiService.trackEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to track task creation: ${e.message}")
            }
        }
    }

    /**
     * Track parse failure/fallback
     */
    fun trackParseFallback(
        input: String,
        reason: String,
        errorMessage: String? = null
    ) {
        scope.launch {
            try {
                val event = JSONObject().apply {
                    put("event_type", "parse_fallback")
                    put("timestamp", System.currentTimeMillis())
                    put("input_length", input.length)
                    put("fallback_reason", reason)
                    if (errorMessage != null) {
                        put("error_message", errorMessage)
                    }
                }

                logEvent(event)

                // TODO: Send to backend
                // apiService.trackEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to track fallback: ${e.message}")
            }
        }
    }

    /**
     * Get summary statistics
     * For internal testing/debugging
     */
    fun getStats(): AnalyticsStats {
        // TODO: Implement local stats aggregation
        return AnalyticsStats(
            totalParses = 0,
            llmSuccessRate = 0.0,
            averageConfidence = 0.0,
            editRate = 0.0
        )
    }

    /**
     * Create parse event JSON
     */
    private fun createParseEvent(
        eventType: String,
        input: String,
        result: ParsedTaskResult,
        durationMs: Long
    ): JSONObject {
        return JSONObject().apply {
            put("event_type", eventType)
            put("timestamp", System.currentTimeMillis())
            put("input_length", input.length)
            put("parse_duration_ms", durationMs)
            put("source", result.source)
            put("confidence", result.confidence)
            put("category", result.category ?: "none")
            put("priority", result.priority)
            put("has_due_date", result.dueDate != null)
            put("has_due_time", result.dueTime != null)
            put("has_context_tags", !result.contextTags.isNullOrEmpty())
            put("is_recurring", result.isRecurring)

            // Fallback info
            if (result.source == "local_fallback") {
                put("fallback_reason", result.reason ?: "unknown")
            }

            // Rate limit info
            result.rateLimitInfo?.let { rateLimit ->
                put("rate_limited", true)
                put("rate_limit_window", rateLimit.window)
            }
        }
    }

    /**
     * Log event to Android logcat
     */
    private fun logEvent(event: JSONObject) {
        Log.d(TAG, "Analytics Event: ${event.getString("event_type")}")
        Log.d(TAG, "Data: $event")
    }
}

/**
 * Analytics statistics summary
 */
data class AnalyticsStats(
    val totalParses: Int,
    val llmSuccessRate: Double,   // % of parses that used LLM successfully
    val averageConfidence: Double, // Average LLM confidence score
    val editRate: Double           // % of tasks that were edited after parsing
)

/**
 * Parse event for backend API
 * TODO: Add endpoint POST /api/analytics/event
 */
data class AnalyticsEvent(
    val eventType: String,
    val timestamp: Long,
    val data: Map<String, Any>
)
