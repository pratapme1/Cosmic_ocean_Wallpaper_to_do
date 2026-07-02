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
import com.cosmicocean.reminders.RemoteRemindersRepository
import com.cosmicocean.reminders.ViReminderMapper
import com.cosmicocean.sync.SyncManager
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
    private val syncManager: SyncManager
) {
    companion object {
        private const val TAG = "TaskRepository"
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

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // === Write Operations ===

    suspend fun addStar(star: Star): String {
        val localId = star.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        star.id = localId

        val entity = star.toEntity().copy(
            localId = localId,
            syncStatus = "pending",
            updatedAt = System.currentTimeMillis()
        )
        
        starDao.insertStarWithTransaction(entity)
        Log.d(TAG, "Saved star locally with ID: $localId (sync pending)")

        syncManager.queueCreate(
            localTaskId = localId,
            data = mapOf(
                "rawTitle" to star.title,
                "x" to star.particle.x,
                "y" to star.particle.y,
                "is_recurring" to star.isRecurring,
                "echo_interval" to (star.echoInterval?.name ?: ""),
                "is_subtask" to star.isSubtask,
                "parent_id" to (star.parentId ?: ""),
                "priority" to star.urgency,
                "due_date" to star.dueDate
            )
        )
        mirrorLocalReminderCreate(localId, star.title, star.dueDate)

        return localId
    }

    suspend fun updateStar(star: Star) {
        if (ViReminderMapper.isRemoteReminder(star.id)) {
            updateViReminder(star)
            return
        }
        val existing = starDao.getByLocalId(star.id)
        val entity = star.toEntity().copy(
            syncStatus = "pending",
            updatedAt = System.currentTimeMillis()
        )
        
        starDao.insertStarWithTransaction(entity)
        Log.d(TAG, "Updated star locally: ${star.id}")

        if (star.isCompleted && existing?.isCompleted != true) {
            try {
                val userId = resolveUserId()
                val trophyManager = TrophyManager(CosmicDatabase.getDatabase(context), userId)
                trophyManager.recordCompletion()
            } catch (e: Exception) {
                Log.w(TAG, "Achievement update failed: ${e.message}")
            }
        }
        if (star.isArchived && existing?.isArchived != true) {
            unlinkChildrenForParent(star.id)
        }

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
                "is_subtask" to star.isSubtask,
                "parent_id" to (star.parentId ?: "")
            )
        }
        
        syncManager.queueUpdate(star.id, updateData)
        when {
            star.isCompleted -> mirrorLocalReminderComplete(star.id, star.completedAt)
            star.isArchived -> mirrorLocalReminderDelete(star.id)
            else -> mirrorLocalReminderUpdate(star.id, star.title, star.dueDate)
        }
    }

    /**
     * Vi reminders are remote-owned rows synced from Supabase: edits stay in
     * Room only (title/due are re-asserted by the next sync), completion is
     * pushed back to the table, and nothing enters the backend sync queue.
     */
    private suspend fun updateViReminder(star: Star) {
        val existing = starDao.getByLocalId(star.id)
        val entity = star.toEntity().copy(
            contextTag = com.cosmicocean.reminders.ViReminderMapper.VI_CONTEXT_TAG,
            syncStatus = com.cosmicocean.reminders.ViReminderMapper.SYNC_STATUS_REMOTE,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStarWithTransaction(entity)

        if (star.isCompleted && existing?.isCompleted != true) {
            try {
                val userId = resolveUserId()
                val trophyManager = TrophyManager(CosmicDatabase.getDatabase(context), userId)
                trophyManager.recordCompletion()
            } catch (e: Exception) {
                Log.w(TAG, "Achievement update failed: ${e.message}")
            }
            mirrorRemoteReminderComplete(star.id)
        }
        if (star.isArchived && existing?.isArchived != true) {
            mirrorLocalReminderDelete(star.id)
        }
    }

    suspend fun deleteStar(star: Star) {
        starDao.softDelete(star.id)
        Log.d(TAG, "Soft deleted star locally: ${star.id}")
        unlinkChildrenForParent(star.id)
        mirrorLocalReminderDelete(star.id)
        if (!ViReminderMapper.isRemoteReminder(star.id)) {
            syncManager.queueDelete(star.id)
        }
    }

    suspend fun clearAllTasks() {
        starDao.deleteAllStars()
        Log.d(TAG, "Cleared all stars from local DB")
        syncManager.queueClearAll()
    }

    private fun resolveUserId(): String {
        return if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
            LocalOnlyUserStore(context).getOrCreateUser().id
        } else {
            TokenManager(context).getUserId()
                ?: LocalOnlyUserStore(context).getOrCreateUser().id
        }
    }

    private suspend fun unlinkChildrenForParent(parentId: String) {
        val children = starDao.getChildrenForParent(parentId)
        if (children.isEmpty()) return
        val now = System.currentTimeMillis()
        children.forEach { child ->
            val updated = child.copy(
                parentId = null,
                isSubtask = false,
                syncStatus = "pending",
                updatedAt = now
            )
            starDao.insertStarWithTransaction(updated)
            syncManager.queueUpdate(
                child.localId,
                mapOf(
                    "is_subtask" to false,
                    "parent_id" to ""
                )
            )
        }
    }

    private suspend fun mirrorLocalReminderCreate(localId: String, title: String, dueDate: Long?) {
        try {
            RemoteRemindersRepository.getInstance(context).mirrorLocalCreate(localId, title, dueDate)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase reminder create mirror skipped: ${e.message}")
        }
    }

    private suspend fun mirrorLocalReminderUpdate(localId: String, title: String, dueDate: Long?) {
        try {
            RemoteRemindersRepository.getInstance(context).mirrorLocalUpdate(localId, title, dueDate)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase reminder update mirror skipped: ${e.message}")
        }
    }

    private suspend fun mirrorLocalReminderComplete(localId: String, completedAt: Long?) {
        try {
            RemoteRemindersRepository.getInstance(context).mirrorLocalComplete(localId, completedAt)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase reminder completion mirror skipped: ${e.message}")
        }
    }

    private suspend fun mirrorRemoteReminderComplete(localId: String) {
        try {
            RemoteRemindersRepository.getInstance(context).completeReminder(localId)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase remote reminder completion skipped: ${e.message}")
        }
    }

    private suspend fun mirrorLocalReminderDelete(localId: String) {
        try {
            RemoteRemindersRepository.getInstance(context).mirrorLocalDelete(localId)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase reminder delete mirror skipped: ${e.message}")
        }
    }

    private fun StarEntity.toDomain(): Star {
        val star = Star(
            x = x,
            y = y,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            contextTag = contextTag,
            isSubtask = isSubtask,
            parentId = parentId,
            isRecurring = isRecurring,
            echoInterval = echoInterval?.let { EchoInterval.valueOf(it) },
            createdAt = createdAt,
            id = localId
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
            serverId = null,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            x = particle.x,
            y = particle.y,
            createdAt = createdAt,
            isSubtask = isSubtask,
            parentId = parentId,
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
