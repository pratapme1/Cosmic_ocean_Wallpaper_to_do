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
    val updatedAt: String? = null,
    // Epic 10: Privacy fields
    @SerializedName("is_private")
    val isPrivate: Boolean = false,
    @SerializedName("privacy_level")
    val privacyLevel: String? = "public",  // public, category, initials, hidden, custom
    @SerializedName("privacy_display")
    val privacyDisplay: String? = null     // Custom display text for 'custom' privacy level
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

/**
 * Epic 10: User Preferences Response
 * Response from GET/PATCH /api/user/preferences
 */
data class UserPreferencesResponse(
    val theme: String? = "cosmic",
    val resolution: String? = null,
    @SerializedName("display_mode")
    val displayMode: String? = "one_thing",
    val timezone: String? = null,
    // Privacy settings
    @SerializedName("default_privacy_level")
    val defaultPrivacyLevel: String? = "public",
    @SerializedName("auto_hide_work_tasks")
    val autoHideWorkTasks: Boolean = false,
    @SerializedName("work_hours_start")
    val workHoursStart: String? = "09:00",
    @SerializedName("work_hours_end")
    val workHoursEnd: String? = "17:00",
    @SerializedName("biometric_reveal_enabled")
    val biometricRevealEnabled: Boolean = true,
    @SerializedName("hide_all_tasks_mode")
    val hideAllTasksMode: Boolean = false,
    // Epic 10 Phase 3: Environment settings
    @SerializedName("time_of_day_mode")
    val timeOfDayMode: String? = "auto",  // auto, manual
    @SerializedName("manual_time_period")
    val manualTimePeriod: String? = "morning",  // dawn, morning, afternoon, evening, night
    @SerializedName("weather_overlay_enabled")
    val weatherOverlayEnabled: Boolean? = true,
    @SerializedName("environment_enabled")
    val environmentEnabled: Boolean? = true,
    @SerializedName("particle_intensity")
    val particleIntensity: String? = "medium",  // low, medium, high
    // Due haptics settings
    @SerializedName("due_haptics_enabled")
    val dueHapticsEnabled: Boolean? = true,
    @SerializedName("due_soon_minutes")
    val dueSoonMinutes: Int? = 30,
    @SerializedName("urgent_due_minutes")
    val urgentDueMinutes: Int? = 10,
    @SerializedName("overdue_minutes")
    val overdueMinutes: Int? = 60,
    @SerializedName("quiet_hours_enabled")
    val quietHoursEnabled: Boolean? = true,
    @SerializedName("quiet_hours_start")
    val quietHoursStart: Int? = 22,
    @SerializedName("quiet_hours_end")
    val quietHoursEnd: Int? = 7,
    @SerializedName("respect_dnd")
    val respectDnd: Boolean? = true,
    @SerializedName("haptics_rate_limit_minutes")
    val hapticsRateLimitMinutes: Int? = 30,
    // Context mode + tutorial
    @SerializedName("context_mode")
    val contextMode: String? = "auto",
    @SerializedName("manual_context")
    val manualContext: String? = "home",
    @SerializedName("focus_mode_enabled")
    val focusModeEnabled: Boolean? = false,
    @SerializedName("overdue_heatmap_enabled")
    val overdueHeatmapEnabled: Boolean? = true,
    @SerializedName("ambient_reminders_enabled")
    val ambientRemindersEnabled: Boolean? = true,
    @SerializedName("tutorial_seen")
    val tutorialSeen: Boolean? = false,
    // Epic 11: Custom Wallpaper
    @SerializedName("wallpaper_mode")
    val wallpaperMode: String? = "generated", // generated, custom
    @SerializedName("custom_wallpaper_path")
    val customWallpaperPath: String? = null
)

// ==================== Local-First Sync Models ====================

/**
 * Request for POST /api/sync
 * Sends pending local changes to server
 * CRITICAL FIX: Includes deviceTime for reference (server is source of truth)
 */
data class SyncRequest(
    @SerializedName("lastSyncAt")
    val lastSyncAt: Long?,  // CRITICAL FIX: Server timestamp, not device
    @SerializedName("pendingChanges")
    val pendingChanges: List<SyncChange>,
    @SerializedName("deviceTime")
    val deviceTime: Long? = null  // For debugging/logging only
)

/**
 * Individual change to sync
 */
data class SyncChange(
    val type: String,        // create, update, delete
    val clientId: String,
    val data: Map<String, Any?>,
    val timestamp: Long
)

/**
 * Response from POST /api/sync
 * CRITICAL FIX: Includes mappings for clientId → serverId translation
 */
data class SyncResponse(
    @SerializedName("syncedAt")
    val syncedAt: Long,
    val tasks: List<TaskResponse>,
    val results: SyncResults,
    val conflicts: List<SyncConflict>,
    @SerializedName("mappings")
    val mappings: List<SyncMapping>? = null  // NEW: ClientId → ServerId mappings for creates
)

/**
 * Sync results summary
 */
data class SyncResults(
    val applied: Int,
    val rejected: Int,
    val skipped: Int = 0
)

/**
 * Sync conflict details
 */
data class SyncConflict(
    val clientId: String,
    val reason: String,          // already_exists, stale_data, task_not_found, server_error
    @SerializedName("serverData")
    val serverData: TaskResponse? = null,
    val error: String? = null,
    @SerializedName("serverId")
    val serverId: String? = null  // For already_exists, return the existing server ID
)

/**
 * Sync mapping entry (for successful creates)
 * Maps clientId (local) to serverId (backend UUID)
 */
data class SyncMapping(
    val clientId: String,
    @SerializedName("serverId")
    val serverId: String,
    @SerializedName("serverData")
    val serverData: TaskResponse
)

/**
 * Response from GET /api/sync/status
 */
data class SyncStatusResponse(
    @SerializedName("taskCount")
    val taskCount: Int,
    @SerializedName("lastModified")
    val lastModified: Long?,
    @SerializedName("serverTime")
    val serverTime: Long
)
