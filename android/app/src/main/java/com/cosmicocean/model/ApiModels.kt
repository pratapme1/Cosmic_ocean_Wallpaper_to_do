package com.cosmicocean.model

import com.google.gson.annotations.SerializedName

/**
 * Response from POST /api/auth/refresh
 */
data class TokenRefreshResponse(
    val accessToken: String
)

/**
 * Response from POST /api/auth/wallpaper-token
 */
data class WallpaperTokenResponse(
    val wallpaperToken: String
)

/**
 * Response from DELETE /api/user
 */
data class DeleteAccountResponse(
    val success: Boolean,
    val message: String
)

/**
 * Task response from backend API
 * Matches the PostgreSQL tasks table structure
 */
data class TaskResponse(
    val id: String,
    @SerializedName("user_id")
    val userId: String? = null,
    val title: String,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("due_time")
    val dueTime: String? = null,
    @SerializedName("estimate_minutes")
    val estimateMinutes: Int? = null,
    val priority: Int = 0,
    val completed: Boolean = false,
    @SerializedName("completed_at")
    val completedAt: String? = null,
    @SerializedName("snoozed_until")
    val snoozedUntil: String? = null,
    @SerializedName("context_location")
    val contextLocation: String? = null,
    @SerializedName("context_time")
    val contextTime: String? = null,
    @SerializedName("energy_required")
    val energyRequired: String? = null,
    @SerializedName("decay_prompted")
    val decayPrompted: Boolean = false,
    @SerializedName("original_due_date")
    val originalDueDate: String? = null,
    @SerializedName("times_rescheduled")
    val timesRescheduled: Int = 0,
    val x: Double? = null,
    val y: Double? = null,
    @SerializedName("is_subtask")
    val isSubtask: Boolean = false,
    @SerializedName("is_recurring")
    val isRecurring: Boolean = false,
    @SerializedName("echo_interval")
    val echoInterval: String? = null,
    val archived: Boolean = false,
    @SerializedName("archived_at")
    val archivedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

/**
 * Response from GET /api/user/stats/graduation
 */
data class GraduationStats(
    val months: List<MonthlyStats>,
    val graduation: GraduationProgress
)

data class MonthlyStats(
    val month: String,
    @SerializedName("total_app_opens")
    val totalAppOpens: Int = 0,
    @SerializedName("total_widget_interactions")
    val totalWidgetInteractions: Int = 0,
    @SerializedName("total_tasks_completed")
    val totalTasksCompleted: Int = 0,
    @SerializedName("total_tasks_completed_via_widget")
    val totalTasksCompletedViaWidget: Int = 0
)

data class GraduationProgress(
    val score: Int = 0,
    val message: String? = null,
    val widgetCompletionRate: Int = 0
)

/**
 * Weekly stats response from GET /api/user/stats/weekly
 */
data class WeeklyStatsResponse(
    val weeks: List<WeekStats>,
    val trends: StatsTrends
)

data class WeekStats(
    @SerializedName("week_start")
    val weekStart: String,
    @SerializedName("app_opens")
    val appOpens: Int = 0,
    @SerializedName("widget_interactions")
    val widgetInteractions: Int = 0,
    @SerializedName("tasks_created")
    val tasksCreated: Int = 0,
    @SerializedName("tasks_completed")
    val tasksCompleted: Int = 0,
    @SerializedName("tasks_completed_via_widget")
    val tasksCompletedViaWidget: Int = 0
)

data class StatsTrends(
    val appOpenTrend: Int? = null,
    val widgetCompletionRate: Int? = null
)

/**
 * Epic 8: LLM Intelligence Enhancement
 * Request for POST /api/tasks/parse-llm
 */
data class ParseRequest(
    val title: String
)

/**
 * Parsed task result from LLM parser
 * Response from POST /api/tasks/parse-llm
 */
data class ParsedTaskResult(
    val title: String,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("due_time")
    val dueTime: String? = null,
    @SerializedName("estimate_minutes")
    val estimateMinutes: Int? = null,
    val priority: Int = 2,
    val category: String? = null,
    @SerializedName("energy_level")
    val energyLevel: String? = null,
    @SerializedName("context_tags")
    val contextTags: List<String>? = null,
    @SerializedName("is_recurring")
    val isRecurring: Boolean = false,
    @SerializedName("recurring_pattern")
    val recurringPattern: String? = null,
    val confidence: Double = 0.0,
    val source: String = "local_fallback", // "llm" or "local_fallback"
    val reason: String? = null,            // Fallback reason if applicable
    @SerializedName("rate_limit_info")
    val rateLimitInfo: RateLimitInfo? = null
)

/**
 * Rate limit information (if rate limited)
 */
data class RateLimitInfo(
    val window: String,      // "minute" or "day"
    val limit: Int,          // Rate limit value
    @SerializedName("reset_in")
    val resetIn: Int,        // Seconds until reset
    val message: String
)

/**
 * Full response wrapper from parse-llm endpoint
 */
data class ParseLLMResponse(
    val success: Boolean,
    val parsed: ParsedTaskResult,
    @SerializedName("original_input")
    val originalInput: String,
    val timestamp: String
)
