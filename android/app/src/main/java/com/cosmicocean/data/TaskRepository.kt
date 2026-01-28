package com.cosmicocean.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cosmicocean.model.EchoInterval
import com.cosmicocean.model.ParseRequest
import com.cosmicocean.model.ParsedTaskResult
import com.cosmicocean.model.Star
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.network.ApiService
import com.cosmicocean.worker.WallpaperUpdateWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val starDao: StarDao,
    private val apiService: ApiService,
    private val context: Context
) {
    companion object {
        private const val TAG = "TaskRepository"
    }

    /**
     * Trigger immediate wallpaper update after task changes
     * This ensures lock screen reflects the latest task list without waiting for scheduled update
     */
    private fun triggerImmediateWallpaperUpdate() {
        try {
            val updateRequest = OneTimeWorkRequestBuilder<WallpaperUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(updateRequest)
            Log.d(TAG, "Triggered immediate wallpaper update")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger wallpaper update: ${e.message}")
        }
    }
    fun getAllActiveStars(): Flow<List<Star>> {
        return starDao.getAllActiveStars().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Epic 8: LLM Intelligence Enhancement
     * Parse task input using LLM with graceful fallback
     *
     * Decision logic:
     * 1. Check network connectivity
     * 2. Check if LLM enabled in user preferences (default: true)
     * 3. If both true, call LLM endpoint
     * 4. On any error, return local fallback
     *
     * @param input User's natural language input
     * @return ParsedTaskResult with structured data
     */
    suspend fun parseTaskInput(input: String): ParsedTaskResult {
        // Check network connectivity
        if (!isNetworkAvailable()) {
            Log.d(TAG, "LLM Parser: Network unavailable, using local fallback")
            return createLocalFallback(input, "network_unavailable")
        }

        // TODO: Check user preferences (default: enabled)
        // For now, always attempt LLM parsing if network is available
        val llmEnabled = true

        if (!llmEnabled) {
            Log.d(TAG, "LLM Parser: Disabled by user preferences")
            return createLocalFallback(input, "user_disabled")
        }

        // Attempt LLM parsing
        return try {
            val response = apiService.parseTaskLLM(ParseRequest(title = input))

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!.parsed
                Log.d(TAG, "LLM Parser: Success (source=${result.source}, confidence=${result.confidence})")
                result
            } else {
                Log.e(TAG, "LLM Parser: API error ${response.code()}")
                createLocalFallback(input, "api_error_${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM Parser: Exception - ${e.message}")
            createLocalFallback(input, "exception")
        }
    }

    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Create local fallback parse result
     * This is a simplified version - the backend's local parser is more comprehensive
     */
    private fun createLocalFallback(input: String, reason: String): ParsedTaskResult {
        return ParsedTaskResult(
            title = input.trim(),
            dueDate = null,
            dueTime = null,
            estimateMinutes = null,
            priority = 2, // Default medium priority
            category = null,
            energyLevel = "medium",
            contextTags = extractContextTags(input),
            isRecurring = false,
            recurringPattern = null,
            confidence = 0.5,
            source = "local_fallback",
            reason = reason
        )
    }

    /**
     * Extract @tags from input (simple regex)
     */
    private fun extractContextTags(input: String): List<String> {
        val regex = Regex("@\\w+")
        return regex.findAll(input).map { it.value }.toList()
    }

    suspend fun addStar(star: Star) {
        // Save to local database first with local ID
        val oldId = star.id
        starDao.insertStar(star.toEntity())
        Log.d(TAG, "Saved star locally with ID: $oldId")

        // Sync new task to backend API
        try {
            val response = apiService.createTask(
                mapOf(
                    "rawTitle" to star.title,  // Use rawTitle for NLP parsing!
                    "x" to star.particle.x.toString(),
                    "y" to star.particle.y.toString(),
                    "is_recurring" to star.isRecurring.toString(),
                    "echo_interval" to (star.echoInterval?.name ?: ""),
                    "is_subtask" to star.isSubtask.toString()
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val backendTask = response.body()!!

                // Delete old record with local star-xxx ID
                starDao.deleteStarById(oldId)
                Log.d(TAG, "Deleted old local star: $oldId")

                // Update star object with backend UUID and parsed values
                star.id = backendTask.id
                star.urgency = backendTask.priority

                // Parse due date from backend if available
                if (!backendTask.dueDate.isNullOrEmpty()) {
                    try {
                        // FIX: Handle both formats:
                        // - Date-only: "2026-01-15" (from PostgreSQL DATE)
                        // - ISO string: "2026-01-15T10:00:00.000Z" (full timestamp)
                        val dueDateMs = if (backendTask.dueDate.contains("T")) {
                            // Full ISO timestamp
                            java.time.Instant.parse(backendTask.dueDate).toEpochMilli()
                        } else {
                            // Date-only: combine with due_time or use end of day
                            val datePart = java.time.LocalDate.parse(backendTask.dueDate)
                            val timePart = if (!backendTask.dueTime.isNullOrEmpty()) {
                                // Handle both "HH:MM" and "HH:MM:SS" formats
                                val timeStr = backendTask.dueTime
                                if (timeStr.count { it == ':' } == 1) {
                                    java.time.LocalTime.parse("$timeStr:00") // Add seconds
                                } else {
                                    java.time.LocalTime.parse(timeStr)
                                }
                            } else {
                                java.time.LocalTime.of(23, 59, 59) // Default to end of day
                            }
                            java.time.LocalDateTime.of(datePart, timePart)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        star.dueDate = dueDateMs
                        Log.d(TAG, "Parsed due date: ${backendTask.dueDate} + ${backendTask.dueTime ?: "EOD"} -> ${star.dueDate}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse due date: ${backendTask.dueDate} - ${e.message}")
                    }
                }

                // Insert with new UUID
                starDao.insertStar(star.toEntity())
                Log.d(TAG, "✅ Task synced: local=$oldId -> backend=${star.id}")
            } else {
                Log.e(TAG, "Backend create failed: ${response.code()} - ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            // Log error but don't fail - offline mode
            Log.e(TAG, "Failed to sync new star $oldId to backend: ${e.message}", e)
        }

        // Trigger immediate wallpaper update to reflect new task
        triggerImmediateWallpaperUpdate()
    }

    suspend fun updateStar(star: Star) {
        // EPIC 9 FIX: Update local database with current positions
        starDao.insertStar(star.toEntity())

        // CRITICAL FIX: Sync to backend API (including positions)
        try {
            // If star is completed, call the complete endpoint
            if (star.isCompleted && star.completedAt != null) {
                android.util.Log.d("TaskRepository", "Sending PATCH for completed task: ${star.id}")
                val response = apiService.updateTask(
                    star.id,
                    mapOf(
                        "completed" to true,
                        "completed_at" to star.completedAt.toString(),
                        "x" to star.particle.x.toString(),  // EPIC 9: Save positions
                        "y" to star.particle.y.toString()
                    )
                )
                android.util.Log.d("TaskRepository", "PATCH response: ${response.code()}")
            }
            // If star is archived, call the update endpoint with archived flag
            else if (star.isArchived && star.archivedAt != null) {
                apiService.updateTask(
                    star.id,
                    mapOf(
                        "archived" to true,
                        "archived_at" to star.archivedAt.toString(),
                        "x" to star.particle.x.toString(),  // EPIC 9: Save positions
                        "y" to star.particle.y.toString()
                    )
                )
            }
            // Otherwise, just update task details
            else {
                apiService.updateTask(
                    star.id,
                    mapOf(
                        "rawTitle" to star.title,  // FIX: Use rawTitle to trigger NLP re-parsing
                        "priority" to star.urgency.toString(),
                        "x" to star.particle.x.toString(),  // EPIC 9: Save positions
                        "y" to star.particle.y.toString()
                    )
                )
            }
        } catch (e: Exception) {
            // Log error but don't fail - offline mode
            android.util.Log.e("TaskRepository", "Failed to sync star ${star.id} to backend: ${e.message}", e)
            println("Failed to sync star ${star.id} to backend: ${e.message}")
        }

        // Trigger immediate wallpaper update to reflect task changes
        triggerImmediateWallpaperUpdate()
    }

    suspend fun deleteStar(star: Star) {
        // CRITICAL FIX: Use deleteStarById to ensure deletion by Primary Key
        // Prevents issues where entity fields mismatch (e.g. float precision) causes silent fail
        starDao.deleteStarById(star.id)

        // CRITICAL FIX: Sync deletion to backend API
        try {
            apiService.deleteTask(star.id)
        } catch (e: Exception) {
            // Log error but don't fail - offline mode
            println("Failed to sync deletion of star ${star.id} to backend: ${e.message}")
        }

        // Trigger immediate wallpaper update to reflect task deletion
        triggerImmediateWallpaperUpdate()
    }

    private fun StarEntity.toDomain(): Star {
        val star = Star(
            x = x,
            y = y,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            isSubtask = isSubtask,
            isRecurring = isRecurring,
            echoInterval = echoInterval?.let { EchoInterval.valueOf(it) },
            createdAt = createdAt,
            id = id
        )
        star.isCompleted = isCompleted
        star.completedAt = completedAt
        star.isArchived = isArchived
        star.archivedAt = archivedAt
        return star
    }

    private fun Star.toEntity(): StarEntity {
        return StarEntity(
            id = id,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            x = particle.x,
            y = particle.y,
            createdAt = createdAt,
            isSubtask = isSubtask,
            isRecurring = isRecurring,
            echoInterval = echoInterval?.name,
            isCompleted = isCompleted,
            completedAt = completedAt,
            isArchived = isArchived,
            archivedAt = archivedAt
        )
    }
}
