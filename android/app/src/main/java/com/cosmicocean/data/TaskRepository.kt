package com.cosmicocean.data

import android.content.Context
import android.util.Log
import com.cosmicocean.model.EchoInterval
import com.cosmicocean.model.Star
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val starDao: StarDao,
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "TaskRepository"
    }
    fun getAllActiveStars(): Flow<List<Star>> {
        return starDao.getAllActiveStars().map { entities ->
            entities.map { it.toDomain() }
        }
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
    }

    suspend fun updateStar(star: Star) {
        // Update local database
        starDao.insertStar(star.toEntity())

        // CRITICAL FIX: Sync to backend API
        try {
            // If star is completed, call the complete endpoint
            if (star.isCompleted && star.completedAt != null) {
                android.util.Log.d("TaskRepository", "Sending PATCH for completed task: ${star.id}")
                val response = apiService.updateTask(
                    star.id,
                    mapOf(
                        "completed" to true,  // CRITICAL FIX: Boolean, not string!
                        "completed_at" to star.completedAt.toString()
                    )
                )
                android.util.Log.d("TaskRepository", "PATCH response: ${response.code()}")
            }
            // If star is archived, call the update endpoint with archived flag
            else if (star.isArchived && star.archivedAt != null) {
                apiService.updateTask(
                    star.id,
                    mapOf(
                        "archived" to true,  // CRITICAL FIX: Boolean, not string!
                        "archived_at" to star.archivedAt.toString()
                    )
                )
            }
            // Otherwise, just update task details
            else {
                apiService.updateTask(
                    star.id,
                    mapOf(
                        "title" to star.title,
                        "priority" to star.urgency.toString(),
                        "due_date" to (star.dueDate?.toString() ?: "")
                    )
                )
            }
        } catch (e: Exception) {
            // Log error but don't fail - offline mode
            android.util.Log.e("TaskRepository", "Failed to sync star ${star.id} to backend: ${e.message}", e)
            println("Failed to sync star ${star.id} to backend: ${e.message}")
        }
    }

    suspend fun deleteStar(star: Star) {
        // Delete from local database
        starDao.deleteStar(star.toEntity())

        // CRITICAL FIX: Sync deletion to backend API
        try {
            apiService.deleteTask(star.id)
        } catch (e: Exception) {
            // Log error but don't fail - offline mode
            println("Failed to sync deletion of star ${star.id} to backend: ${e.message}")
        }
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
