package com.cosmicocean.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.cosmicocean.data.StarDao
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.SyncQueueDao
import com.cosmicocean.data.SyncQueueEntity
import com.cosmicocean.model.SyncChange
import com.cosmicocean.model.SyncRequest
import com.cosmicocean.model.SyncResponse
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.network.ApiService
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SyncManager - Local-First Sync Engine
 *
 * Coordinates background synchronization between local Room database and backend API.
 * Uses the existing backend/routes/sync.js endpoint.
 *
 * Key features:
 * - Debounced sync (waits 1 second for batch changes)
 * - Retry with exponential backoff
 * - Conflict resolution (last-write-wins)
 * - Offline queue persistence
 */
class SyncManager(
    private val syncQueueDao: SyncQueueDao,
    private val starDao: StarDao,
    private val apiService: ApiService,
    private val context: Context
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val DEBOUNCE_MS = 1000L
        private const val MAX_RETRY_COUNT = 3
        private const val INITIAL_RETRY_DELAY_MS = 5000L
        private const val LAST_SYNC_KEY = "last_sync_timestamp"

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(
            syncQueueDao: SyncQueueDao,
            starDao: StarDao,
            apiService: ApiService,
            context: Context
        ): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(syncQueueDao, starDao, apiService, context).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    private val gson = Gson()
    private val prefsManager = WallpaperPreferencesManager(context)

    // Debounced sync trigger channel
    private val syncTriggerChannel = Channel<Unit>(Channel.CONFLATED)

    // Sync state observable
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Pending count observable
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        // Start debounced sync listener
        scope.launch {
            syncTriggerChannel.receiveAsFlow()
                .collect {
                    delay(DEBOUNCE_MS)
                    performSync()
                }
        }

        // Update pending count on init
        scope.launch {
            updatePendingCount()
        }

        Log.d(TAG, "SyncManager initialized")
    }

    /**
     * Trigger a sync (debounced)
     * Call this after any local change to queue a background sync
     */
    fun triggerSync() {
        syncTriggerChannel.trySend(Unit)
        Log.d(TAG, "Sync triggered (debounced)")
    }

    /**
     * Force immediate sync (no debounce)
     */
    fun forceSync() {
        scope.launch {
            performSync()
        }
    }

    /**
     * Queue a create operation
     */
    suspend fun queueCreate(taskId: String, data: Map<String, Any?>) {
        val entity = SyncQueueEntity(
            taskId = taskId,
            operation = "create",
            payload = gson.toJson(data)
        )
        syncQueueDao.insert(entity)
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued CREATE for task: $taskId")
    }

    /**
     * Queue an update operation
     */
    suspend fun queueUpdate(taskId: String, data: Map<String, Any?>) {
        // Check if there's already a pending create for this task
        val existing = syncQueueDao.getLatestForTask(taskId)
        if (existing?.operation == "create") {
            // Merge update into create
            val existingData = gson.fromJson(existing.payload, Map::class.java) as MutableMap<String, Any?>
            existingData.putAll(data)
            syncQueueDao.deleteById(existing.id)
            syncQueueDao.insert(existing.copy(payload = gson.toJson(existingData)))
        } else {
            val entity = SyncQueueEntity(
                taskId = taskId,
                operation = "update",
                payload = gson.toJson(data)
            )
            syncQueueDao.insert(entity)
        }
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued UPDATE for task: $taskId")
    }

    /**
     * Queue a delete operation
     */
    suspend fun queueDelete(taskId: String) {
        // Remove any pending operations for this task first
        syncQueueDao.deleteByTaskId(taskId)

        val entity = SyncQueueEntity(
            taskId = taskId,
            operation = "delete",
            payload = "{}"
        )
        syncQueueDao.insert(entity)
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued DELETE for task: $taskId")
    }

    /**
     * Perform the actual sync
     */
    private suspend fun performSync() {
        if (!isOnline()) {
            _syncState.value = SyncState.Offline
            Log.d(TAG, "Sync skipped: offline")
            return
        }

        syncMutex.withLock {
            try {
                _syncState.value = SyncState.Syncing

                // Get pending changes
                val pendingChanges = syncQueueDao.getAllPending()
                if (pendingChanges.isEmpty()) {
                    // Nothing to push, just pull latest
                    pullFromServer()
                    _syncState.value = SyncState.Synced(System.currentTimeMillis())
                    Log.d(TAG, "Sync complete: no pending changes, pulled latest")
                    return
                }

                // Convert to API format
                val syncChanges = pendingChanges.map { entity ->
                    SyncChange(
                        type = entity.operation,
                        clientId = entity.taskId,
                        data = gson.fromJson(entity.payload, Map::class.java) as Map<String, Any?>,
                        timestamp = entity.createdAt
                    )
                }

                // Build request
                val request = SyncRequest(
                    lastSyncAt = getLastSyncTimestamp(),
                    pendingChanges = syncChanges
                )

                // Call sync API
                val response = apiService.sync(request)

                if (response.isSuccessful && response.body() != null) {
                    val syncResponse = response.body()!!
                    handleSyncResponse(syncResponse, pendingChanges)
                    _syncState.value = SyncState.Synced(syncResponse.syncedAt)
                    Log.d(TAG, "Sync complete: ${syncResponse.results.applied} applied, ${syncResponse.results.rejected} rejected")
                } else {
                    val error = "API error: ${response.code()}"
                    _syncState.value = SyncState.Error(error)
                    Log.e(TAG, "Sync failed: $error")

                    // Increment retry count for failed items
                    pendingChanges.forEach { entity ->
                        if (entity.retryCount < MAX_RETRY_COUNT) {
                            syncQueueDao.incrementRetry(entity.id, error)
                        }
                    }
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                _syncState.value = SyncState.Error(error)
                Log.e(TAG, "Sync exception: $error", e)

                // Schedule retry with exponential backoff
                scheduleRetry()
            } finally {
                updatePendingCount()
            }
        }
    }

    /**
     * Handle sync response from server
     */
    private suspend fun handleSyncResponse(response: SyncResponse, pendingChanges: List<SyncQueueEntity>) {
        // Save last sync timestamp
        setLastSyncTimestamp(response.syncedAt)

        // Process returned tasks (merge from server)
        response.tasks.forEach { serverTask ->
            mergeServerTask(serverTask)
        }

        // Handle conflicts
        response.conflicts.forEach { conflict ->
            Log.w(TAG, "Conflict for ${conflict.clientId}: ${conflict.reason}")

            when (conflict.reason) {
                "already_exists" -> {
                    // Task exists on server, remove from queue
                    syncQueueDao.deleteByTaskId(conflict.clientId)
                    // Merge server version
                    conflict.serverData?.let { mergeServerTask(it) }
                }
                "stale_data" -> {
                    // Server has newer data, accept server version
                    syncQueueDao.deleteByTaskId(conflict.clientId)
                    conflict.serverData?.let { mergeServerTask(it) }
                }
                "task_not_found" -> {
                    // Task deleted on server, remove local
                    syncQueueDao.deleteByTaskId(conflict.clientId)
                    starDao.deleteStarById(conflict.clientId)
                }
                else -> {
                    // Unknown conflict, increment retry
                    val entity = pendingChanges.find { it.taskId == conflict.clientId }
                    entity?.let {
                        if (it.retryCount < MAX_RETRY_COUNT) {
                            syncQueueDao.incrementRetry(it.id, conflict.reason)
                        }
                    }
                }
            }
        }

        // Remove successfully synced items from queue
        val successfulIds = pendingChanges
            .filter { entity -> response.conflicts.none { it.clientId == entity.taskId } }
            .map { it.id }

        if (successfulIds.isNotEmpty()) {
            syncQueueDao.deleteByIds(successfulIds)
        }

        // Update sync status on local tasks
        pendingChanges
            .filter { entity -> response.conflicts.none { it.clientId == entity.taskId } }
            .forEach { entity ->
                starDao.updateSyncStatus(entity.taskId, "synced")
            }
    }

    /**
     * Merge a task from server into local database
     */
    private suspend fun mergeServerTask(serverTask: TaskResponse) {
        val localTask = starDao.getById(serverTask.id)

        if (localTask == null) {
            // New task from server, insert
            starDao.insertStar(serverTask.toStarEntity())
        } else if (localTask.syncStatus == "synced") {
            // Local is synced, safe to update
            starDao.insertStar(serverTask.toStarEntity().copy(
                // Preserve local-only fields
                x = localTask.x,
                y = localTask.y
            ))
        }
        // If local has pending changes, don't overwrite (will sync on next push)
    }

    /**
     * Pull latest changes from server
     */
    private suspend fun pullFromServer() {
        try {
            val lastSync = getLastSyncTimestamp()

            val request = SyncRequest(
                lastSyncAt = lastSync,
                pendingChanges = emptyList()
            )

            val response = apiService.sync(request)

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!
                setLastSyncTimestamp(syncResponse.syncedAt)

                // Merge server tasks
                syncResponse.tasks.forEach { serverTask ->
                    mergeServerTask(serverTask)
                }

                Log.d(TAG, "Pulled ${syncResponse.tasks.size} tasks from server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull from server failed: ${e.message}")
        }
    }

    /**
     * Schedule retry with exponential backoff
     */
    private fun scheduleRetry() {
        scope.launch {
            delay(INITIAL_RETRY_DELAY_MS)
            if (isOnline()) {
                performSync()
            }
        }
    }

    /**
     * Check network connectivity
     */
    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get last sync timestamp from preferences
     */
    private fun getLastSyncTimestamp(): Long? {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(LAST_SYNC_KEY, -1)
        return if (timestamp == -1L) null else timestamp
    }

    /**
     * Save last sync timestamp to preferences
     */
    private fun setLastSyncTimestamp(timestamp: Long) {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(LAST_SYNC_KEY, timestamp).apply()
    }

    /**
     * Update pending count
     */
    private suspend fun updatePendingCount() {
        _pendingCount.value = syncQueueDao.getPendingCount()
    }

    /**
     * Convert TaskResponse to StarEntity
     */
    private fun TaskResponse.toStarEntity(): StarEntity {
        // Parse due date
        val dueDateMs = dueDate?.let { dateStr ->
            try {
                if (dateStr.contains("T")) {
                    java.time.Instant.parse(dateStr).toEpochMilli()
                } else {
                    val datePart = java.time.LocalDate.parse(dateStr)
                    val timePart = dueTime?.let { timeStr ->
                        if (timeStr.count { it == ':' } == 1) {
                            java.time.LocalTime.parse("$timeStr:00")
                        } else {
                            java.time.LocalTime.parse(timeStr)
                        }
                    } ?: java.time.LocalTime.of(23, 59, 59)
                    java.time.LocalDateTime.of(datePart, timePart)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
            } catch (e: Exception) {
                null
            }
        }

        // Parse timestamps
        val createdAtMs = createdAt?.let {
            try { java.time.Instant.parse(it).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
        } ?: System.currentTimeMillis()

        val updatedAtMs = updatedAt?.let {
            try { java.time.Instant.parse(it).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
        } ?: System.currentTimeMillis()

        val completedAtMs = completedAt?.let {
            try { java.time.Instant.parse(it).toEpochMilli() } catch (e: Exception) { null }
        }

        val archivedAtMs = archivedAt?.let {
            try { java.time.Instant.parse(it).toEpochMilli() } catch (e: Exception) { null }
        }

        return StarEntity(
            id = id,
            title = title,
            urgency = priority,
            dueDate = dueDateMs,
            x = x?.toFloat() ?: 0.5f,
            y = y?.toFloat() ?: 0.5f,
            createdAt = createdAtMs,
            isSubtask = isSubtask,
            isRecurring = isRecurring,
            echoInterval = echoInterval,
            isCompleted = completed,
            completedAt = completedAtMs,
            isArchived = archived,
            archivedAt = archivedAtMs,
            syncStatus = "synced",
            syncVersion = 0,
            updatedAt = updatedAtMs,
            isDeleted = false
        )
    }
}

/**
 * Sync state sealed class
 */
sealed class SyncState {
    object Idle : SyncState()
    object Offline : SyncState()
    object Syncing : SyncState()
    data class Synced(val timestamp: Long) : SyncState()
    data class Error(val message: String) : SyncState()
}
