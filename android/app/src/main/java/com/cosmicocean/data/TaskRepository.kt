package com.cosmicocean.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.cosmicocean.model.EchoInterval
import com.cosmicocean.model.ParseRequest
import com.cosmicocean.model.ParsedTaskResult
import com.cosmicocean.model.Star
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.network.ApiService
import com.cosmicocean.network.LocalOnlyUserStore
import com.cosmicocean.sync.SyncManager
import com.cosmicocean.service.RealTimeWallpaperService
import com.cosmicocean.utils.HybridTaskParser
import com.cosmicocean.systems.TrophyManager
import com.cosmicocean.auth.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import com.cosmicocean.data.CosmicDatabase

/**
 * CRITICAL FIX: TaskRepository - Unified Local-First Repository
 * 
 * Architecture:
 * 1. ALL writes go to local DB first with pending status
 * 2. ALL sync operations go through SyncManager (no direct API calls)
 * 3. UI updates via Flow from local DB (immediate feedback)
 * 4. Sync happens asynchronously in background
 * 
 * This fixes Issues #1, #3, and provides clean local-first architecture
 */
class TaskRepository(
    private val starDao: StarDao,
    private val apiService: ApiService,
    private val context: Context,
    private val syncManager: SyncManager,
    private val wallpaperUpdater: (Context) -> Unit = { ctx ->
        try {
            RealTimeWallpaperService.updateNow(ctx)
        } catch (e: Exception) {
            Log.w("TaskRepository", "Wallpaper update failed: ${e.message}")
        }
    }
) {
    companion object {
        private const val TAG = "TaskRepository"
        // REMOVED: No throttling for instant wallpaper updates
        // All task changes trigger immediate wallpaper refresh
    }

    private val hybridParser = HybridTaskParser(context)


    // === Query Methods ===
    
    fun getAllActiveStars(): Flow<List<Star>> {
        return starDao.getAllActiveStars().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getByLocalId(localId: String): Star? {
        return starDao.getByLocalId(localId)?.toDomain()
    }

    // === LLM Parsing ===

    suspend fun parseTaskInput(input: String): ParsedTaskResult {
        if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
            Log.d(TAG, "LLM Parser: Local-only mode, using local fallback")
            return createLocalFallback(input, "local_only")
        }
        if (!isNetworkAvailable()) {
            Log.d(TAG, "LLM Parser: Network unavailable, using local fallback")
            return createLocalFallback(input, "network_unavailable")
        }

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

    private fun createLocalFallback(input: String, reason: String): ParsedTaskResult {
        val parsed = hybridParser.parse(input)
        return parsed.copy(
            source = parsed.source.ifBlank { "local_parser" },
            reason = reason
        )
    }

    private fun extractContextTags(input: String): List<String> {
        val regex = Regex("@\\w+")
        return regex.findAll(input).map { it.value }.toList()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // === CRITICAL FIX: Unified Write Operations (Issue #1) ===

    /**
     * CRITICAL FIX: Add star - Local-first with unified sync
     * 
     * 1. Generate localId immediately
     * 2. Save to local DB with pending status
     * 3. Queue to SyncManager (handles all API calls)
     * 4. UI updates via Flow
     */
    suspend fun addStar(star: Star): String {
        // CRITICAL FIX: Generate localId immediately (never changes)
        val localId = star.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        star.id = localId

        // Save to local DB first with pending status
        val entity = star.toEntity().copy(
            localId = localId,
            syncStatus = "pending",
            updatedAt = System.currentTimeMillis()
        )
        
        // CRITICAL FIX: Use transaction for atomic insert
        starDao.insertStarWithTransaction(entity)
        Log.d(TAG, "Saved star locally with ID: $localId (sync pending)")

        // CRITICAL FIX: ALWAYS queue to SyncManager (unified sync path)
        syncManager.queueCreate(
            localTaskId = localId,
            data = mapOf(
                "rawTitle" to star.title,
                "x" to star.particle.x,
                "y" to star.particle.y,
                "is_recurring" to star.isRecurring,
                "echo_interval" to (star.echoInterval?.name ?: ""),
                "is_subtask" to star.isSubtask,
                "priority" to star.urgency,
                "due_date" to star.dueDate
            )
        )

        // Trigger wallpaper update (throttled)
        triggerImmediateWallpaperUpdate()

        // Return localId for immediate use
        return localId
    }

    /**
     * CRITICAL FIX: Update star - Local-first with unified sync
     */
    suspend fun updateStar(star: Star) {
        val existing = starDao.getByLocalId(star.id)
        // Update local DB immediately with pending status
        val entity = star.toEntity().copy(
            syncStatus = "pending",
            updatedAt = System.currentTimeMillis()
        )
        
        starDao.insertStarWithTransaction(entity)
        Log.d(TAG, "Updated star locally: ${star.id}")

        // Achievement tracking for new completions
        if (star.isCompleted && existing?.isCompleted != true) {
            try {
                val userId = resolveUserId()
                val trophyManager = TrophyManager(CosmicDatabase.getDatabase(context), userId)
                trophyManager.recordCompletion()
            } catch (e: Exception) {
                Log.w(TAG, "Achievement update failed: ${e.message}")
            }
        }

        // Queue to SyncManager
        val updateData = when {
            star.isCompleted -> mapOf(
                "completed" to true,
                "completed_at" to (star.completedAt?.toString() ?: System.currentTimeMillis().toString()),
                "x" to star.particle.x,
                "y" to star.particle.y
            )
            star.isArchived -> mapOf(
                "archived" to true,
                "archived_at" to (star.archivedAt?.toString() ?: System.currentTimeMillis().toString()),
                "x" to star.particle.x,
                "y" to star.particle.y
            )
            else -> mapOf(
                "rawTitle" to star.title,
                "priority" to star.urgency,
                "x" to star.particle.x,
                "y" to star.particle.y,
                "due_date" to star.dueDate,
                "is_recurring" to star.isRecurring,
                "echo_interval" to (star.echoInterval?.name ?: ""),
                "is_subtask" to star.isSubtask
            )
        }
        
        syncManager.queueUpdate(star.id, updateData)
        
        // Trigger wallpaper update (throttled)
        triggerImmediateWallpaperUpdate()
    }

    /**
     * CRITICAL FIX: Delete star - Local-first with unified sync
     */
    suspend fun deleteStar(star: Star) {
        // Soft delete locally for sync
        starDao.softDelete(star.id)
        Log.d(TAG, "Soft deleted star locally: ${star.id}")

        // Queue to SyncManager
        syncManager.queueDelete(star.id)

        // Trigger wallpaper update immediately (no throttling)
        triggerImmediateWallpaperUpdate()
    }

    /**
     * LOCAL-FIRST FIX: Clear all tasks
     *
     * 1. Clear local DB FIRST (instant UI feedback)
     * 2. Queue clear_all to SyncManager (background sync)
     * 3. Trigger wallpaper update
     */
    suspend fun clearAllTasks() {
        // CRITICAL: Clear local DB FIRST for instant UI feedback
        starDao.deleteAllStars()
        Log.d(TAG, "Cleared all stars from local DB")

        // Queue to SyncManager for backend sync
        syncManager.queueClearAll()

        // Trigger wallpaper update
        triggerImmediateWallpaperUpdate()
    }

    /**
     * CRITICAL FIX: Immediate wallpaper update (NO throttling)
     * Every task change triggers instant wallpaper refresh
     */
    private fun triggerImmediateWallpaperUpdate() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                wallpaperUpdater(context)
                Log.d(TAG, "Triggered immediate wallpaper update after DB commit")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger wallpaper update: ${e.message}")
            }
        }
    }

    private fun resolveUserId(): String {
        return if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
            LocalOnlyUserStore(context).getOrCreateUser().id
        } else {
            TokenManager(context).getUserId()
                ?: LocalOnlyUserStore(context).getOrCreateUser().id
        }
    }

    // === Entity Conversions ===

    private fun StarEntity.toDomain(): Star {
        val star = Star(
            x = x,
            y = y,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            contextTag = contextTag,
            isSubtask = isSubtask,
            isRecurring = isRecurring,
            echoInterval = echoInterval?.let { EchoInterval.valueOf(it) },
            createdAt = createdAt,
            id = localId  // Use localId for domain model
        )
        star.isCompleted = isCompleted
        star.completedAt = completedAt
        star.isArchived = isArchived
        star.archivedAt = archivedAt
        return star
    }

    private fun Star.toEntity(): StarEntity {
        return StarEntity(
            localId = id,
            serverId = null,  // Will be set after sync
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
            archivedAt = archivedAt,
            contextTag = contextTag,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            serverUpdatedAt = null
        )
    }
}
