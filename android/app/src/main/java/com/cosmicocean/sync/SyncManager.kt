package com.cosmicocean.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.cosmicocean.data.StarDao
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.SyncQueueDao
import com.cosmicocean.data.SyncQueueEntity
// import com.cosmicocean.model.ConflictInfo  // Not used - using SyncConflict instead
import com.cosmicocean.model.SyncChange
import com.cosmicocean.model.SyncRequest
import com.cosmicocean.model.SyncResponse
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.network.ApiService
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
import java.util.UUID

/**
 * CRITICAL FIX: SyncManager - Unified Local-First Sync Engine
 * 
 * Architecture Changes:
 * 1. ALL operations go through SyncManager (no direct API calls)
 * 2. Server timestamps used for conflict resolution (not device time)
 * 3. localId is stable, serverId assigned after sync
 * 4. Conflict tracking exposed for UI
 * 5. Automatic cleanup of old error entries
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
        private const val SYNC_THROTTLE_MS = 5000L  // Min 5s between syncs
        
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
    
    // Debounced sync trigger channel
    private val syncTriggerChannel = Channel<Unit>(Channel.CONFLATED)
    
    // Sync state observable
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Pending count observable
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    
    // CRITICAL FIX: Conflict tracking for UI
    private val _conflicts = MutableStateFlow<List<ConflictResolution>>(emptyList())
    val conflicts: StateFlow<List<ConflictResolution>> = _conflicts.asStateFlow()
    
    // Throttling
    private var lastSyncTime = 0L

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
        
        // CRITICAL FIX: Cleanup old errors on init
        scope.launch {
            cleanupOldErrors()
        }

        Log.d(TAG, "SyncManager initialized (v2.0 - Unified)")
    }

    /**
     * CRITICAL FIX: Create operation - ALWAYS goes through sync queue
     * TaskRepository should call this, not direct API
     */
    suspend fun queueCreate(
        localTaskId: String,
        data: Map<String, Any?>,
        priority: Int = 0
    ) {
        val entity = SyncQueueEntity(
            localTaskId = localTaskId,
            operation = "create",
            payload = gson.toJson(data),
            clientTimestamp = System.currentTimeMillis()
        )
        syncQueueDao.insert(entity)
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued CREATE for task: $localTaskId")
    }

    /**
     * CRITICAL FIX: Update operation - ALWAYS goes through sync queue
     * Don't merge with pending creates (Issue #5)
     */
    suspend fun queueUpdate(localTaskId: String, data: Map<String, Any?>) {
        // CRITICAL FIX: Don't merge with creates - let server handle it
        val entity = SyncQueueEntity(
            localTaskId = localTaskId,
            operation = "update",
            payload = gson.toJson(data),
            clientTimestamp = System.currentTimeMillis()
        )
        syncQueueDao.insert(entity)
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued UPDATE for task: $localTaskId")
    }

    /**
     * CRITICAL FIX: Delete operation
     */
    suspend fun queueDelete(localTaskId: String) {
        // CRITICAL FIX: Remove ALL pending operations for this task
        syncQueueDao.deleteByLocalTaskId(localTaskId)

        val entity = SyncQueueEntity(
            localTaskId = localTaskId,
            operation = "delete",
            payload = "{}",
            clientTimestamp = System.currentTimeMillis()
        )
        syncQueueDao.insert(entity)
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued DELETE for task: $localTaskId")
    }

    /**
     * LOCAL-FIRST FIX: Clear all tasks operation
     * Clears sync queue and queues a clear_all sync request
     */
    suspend fun queueClearAll() {
        // Clear all pending sync operations
        syncQueueDao.deleteAll()

        val entity = SyncQueueEntity(
            localTaskId = "clear_all_${System.currentTimeMillis()}",
            operation = "clear_all",
            payload = "{}",
            clientTimestamp = System.currentTimeMillis()
        )
        syncQueueDao.insert(entity)
        updatePendingCount()
        triggerSync()
        Log.d(TAG, "Queued CLEAR_ALL operation")
    }

    /**
     * Trigger a sync (debounced)
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
     * CRITICAL FIX: Perform sync with throttling and server timestamps
     */
    private suspend fun performSync() {
        if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
            applyLocalOnlySync()
            return
        }
        // CRITICAL FIX: Throttle syncs (Issue #9)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < SYNC_THROTTLE_MS) {
            Log.d(TAG, "Sync throttled - too soon since last sync")
            return
        }
        
        if (!isOnline()) {
            _syncState.value = SyncState.Offline
            Log.d(TAG, "Sync skipped: offline")
            return
        }

        syncMutex.withLock {
            try {
                lastSyncTime = System.currentTimeMillis()
                _syncState.value = SyncState.Syncing

                // Get pending changes
                val pendingChanges = syncQueueDao.getAllPending()
                
                // CRITICAL FIX: Get last SERVER sync timestamp (not client)
                val lastServerSync = starDao.getLastServerSyncTimestamp()

                if (pendingChanges.isEmpty()) {
                    // Nothing to push, just pull latest
                    pullFromServer(lastServerSync)
                    _syncState.value = SyncState.Synced(System.currentTimeMillis())
                    return
                }

                // Convert to API format
                val syncChanges = pendingChanges.map { entity ->
                    SyncChange(
                        type = entity.operation,
                        clientId = entity.localTaskId,
                        data = gson.fromJson(entity.payload, Map::class.java) as Map<String, Any?>,
                        timestamp = entity.clientTimestamp
                    )
                }

                // Build request with server timestamp
                val request = SyncRequest(
                    lastSyncAt = lastServerSync,
                    pendingChanges = syncChanges,
                    deviceTime = System.currentTimeMillis()  // For reference only
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
                        if (entity.retryCount + 1 >= MAX_RETRY_COUNT) {
                            starDao.markSyncError(entity.localTaskId)
                            syncQueueDao.deleteById(entity.id)
                            Log.w(TAG, "Max retries reached for task ${entity.localTaskId}")
                        } else {
                            syncQueueDao.incrementRetry(entity.id, error)
                        }
                    }
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                _syncState.value = SyncState.Error(error)
                Log.e(TAG, "Sync exception: $error", e)
                scheduleRetry()
            } finally {
                updatePendingCount()
            }
        }
    }

    private suspend fun applyLocalOnlySync() {
        syncMutex.withLock {
            _syncState.value = SyncState.Syncing
            val pending = syncQueueDao.getAllPending()
            if (pending.isEmpty()) {
                _syncState.value = SyncState.Idle
                return
            }

            val now = System.currentTimeMillis()
            pending.forEach { entry ->
                when (entry.operation) {
                    "delete" -> starDao.deleteStarByLocalId(entry.localTaskId)
                    else -> starDao.updateSyncStatus(entry.localTaskId, "synced", now)
                }
                syncQueueDao.deleteById(entry.id)
            }

            updatePendingCount()
            _syncState.value = SyncState.Idle
            Log.d(TAG, "Local-only sync applied: cleared ${pending.size} queued changes")
        }
    }

    /**
     * CRITICAL FIX: Handle sync response with server timestamps and ID mappings
     */
    private suspend fun handleSyncResponse(response: SyncResponse, pendingChanges: List<SyncQueueEntity>) {
        val newConflicts = mutableListOf<ConflictResolution>()
        
        // CRITICAL FIX: Process ID mappings from successful creates
        response.mappings?.forEach { mapping ->
            Log.d(TAG, "Mapping received: ${mapping.clientId} -> ${mapping.serverId}")
            
            // Update local record with serverId
            val localTask = starDao.getByLocalId(mapping.clientId)
            if (localTask != null) {
                // Update the local record with serverId and mark as synced
                val updatedTask = localTask.copy(
                    serverId = mapping.serverId,
                    syncStatus = "synced",
                    serverUpdatedAt = response.syncedAt
                )
                starDao.insertStar(updatedTask)
                Log.d(TAG, "Updated local task ${mapping.clientId} with serverId ${mapping.serverId}")
            }
        }
        
        // Handle conflicts
        response.conflicts.forEach { conflict ->
            Log.w(TAG, "Conflict for ${conflict.clientId}: ${conflict.reason}")

            when (conflict.reason) {
                "already_exists" -> {
                    syncQueueDao.deleteByLocalTaskId(conflict.clientId)
                    
                    // CRITICAL FIX: If server returned a serverId for already_exists, update local record
                    if (conflict.serverId != null) {
                        val localTask = starDao.getByLocalId(conflict.clientId)
                        if (localTask != null) {
                            val updatedTask = localTask.copy(
                                serverId = conflict.serverId,
                                syncStatus = "synced",
                                serverUpdatedAt = response.syncedAt
                            )
                            starDao.insertStar(updatedTask)
                            Log.d(TAG, "Updated already-exists task ${conflict.clientId} with serverId ${conflict.serverId}")
                        }
                    }
                    
                    // CRITICAL FIX: Track conflict for UI (don't merge - we already updated local task above)
                    conflict.serverData?.let { serverData ->
                        val localTask = starDao.getByLocalId(conflict.clientId)
                        if (localTask != null) {
                            newConflicts.add(ConflictResolution.AutoResolved(
                                localId = conflict.clientId,
                                reason = "Task already exists on server",
                                localData = localTask,
                                serverData = serverData
                            ))
                        }
                    }
                }
                "stale_data" -> {
                    // CRITICAL FIX: Track conflict requiring user attention
                    val localTask = starDao.getByLocalId(conflict.clientId)
                    if (localTask != null && conflict.serverData != null) {
                        newConflicts.add(ConflictResolution.RequiresUserChoice(
                            localId = conflict.clientId,
                            localData = localTask,
                            serverData = conflict.serverData
                        ))
                        
                        // CRITICAL FIX: Update existing local task instead of creating duplicate
                        val updatedTask = localTask.copy(
                            serverId = conflict.serverData.id ?: localTask.serverId,
                            title = conflict.serverData.title ?: localTask.title,
                            urgency = conflict.serverData.priority ?: localTask.urgency,
                            dueDate = parseDueDate(conflict.serverData.dueDate, conflict.serverData.dueTime) ?: localTask.dueDate,
                            isCompleted = conflict.serverData.completed ?: localTask.isCompleted,
                            completedAt = conflict.serverData.completedAt?.let { parseTimestamp(it) } ?: localTask.completedAt,
                            isArchived = conflict.serverData.archived ?: localTask.isArchived,
                            archivedAt = conflict.serverData.archivedAt?.let { parseTimestamp(it) } ?: localTask.archivedAt,
                            syncStatus = "synced",
                            serverUpdatedAt = response.syncedAt
                        )
                        starDao.insertStar(updatedTask)
                        Log.d(TAG, "Updated stale_data task: ${localTask.localId}")
                    }
                    
                    syncQueueDao.deleteByLocalTaskId(conflict.clientId)
                }
                "task_not_found" -> {
                    syncQueueDao.deleteByLocalTaskId(conflict.clientId)
                    starDao.deleteStarByLocalId(conflict.clientId)
                }
                else -> {
                    val entity = pendingChanges.find { it.localTaskId == conflict.clientId }
                    entity?.let {
                        if (it.retryCount + 1 >= MAX_RETRY_COUNT) {
                            starDao.markSyncError(it.localTaskId)
                            syncQueueDao.deleteById(it.id)
                        } else {
                            syncQueueDao.incrementRetry(it.id, conflict.reason)
                        }
                    }
                }
            }
        }

        // Update conflict flow
        if (newConflicts.isNotEmpty()) {
            _conflicts.value = _conflicts.value + newConflicts
        }

        // Remove successfully synced items from queue (including those handled via mappings)
        val mappedClientIds = response.mappings?.map { it.clientId }?.toSet() ?: emptySet()
        val successfulIds = pendingChanges
            .filter { entity -> 
                response.conflicts.none { it.clientId == entity.localTaskId } ||
                mappedClientIds.contains(entity.localTaskId)  // Also remove if successfully mapped
            }
            .filter { entity ->
                // Only remove if not in conflicts OR if it was successfully mapped
                response.conflicts.none { it.clientId == entity.localTaskId } ||
                mappedClientIds.contains(entity.localTaskId)
            }
            .map { it.id }

        if (successfulIds.isNotEmpty()) {
            syncQueueDao.deleteByIds(successfulIds)
        }
    }

    /**
     * CRITICAL FIX: Merge server task with local preservation
     */
    private suspend fun mergeServerTask(serverTask: TaskResponse, serverTimestamp: Long) {
        // Try to find by serverId first, then by localId
        var localTask = serverTask.id?.let { starDao.getByServerId(it) }
        
        if (localTask == null) {
            // New task from server
            val newLocalId = UUID.randomUUID().toString()
            starDao.insertStar(serverTask.toStarEntity(newLocalId, serverTimestamp))
            Log.d(TAG, "Inserted new server task with localId: $newLocalId")
        } else {
            // CRITICAL FIX: Smart merge preserving local-only fields
            val mergedEntity = localTask.copy(
                serverId = serverTask.id,
                title = serverTask.title ?: localTask.title,
                urgency = serverTask.priority ?: localTask.urgency,
                dueDate = parseDueDate(serverTask.dueDate, serverTask.dueTime) ?: localTask.dueDate,
                // CRITICAL FIX: Preserve local position (Issue #11)
                x = if (localTask.syncStatus == "pending") localTask.x else serverTask.x?.toFloat() ?: localTask.x,
                y = if (localTask.syncStatus == "pending") localTask.y else serverTask.y?.toFloat() ?: localTask.y,
                isCompleted = serverTask.completed ?: localTask.isCompleted,
                completedAt = serverTask.completedAt?.let { parseTimestamp(it) } ?: localTask.completedAt,
                isArchived = serverTask.archived ?: localTask.isArchived,
                archivedAt = serverTask.archivedAt?.let { parseTimestamp(it) } ?: localTask.archivedAt,
                syncStatus = "synced",
                serverUpdatedAt = serverTimestamp
            )
            
            starDao.insertStar(mergedEntity)
            Log.d(TAG, "Merged server task: ${localTask.localId}")
        }
    }

    /**
     * Pull latest changes from server
     */
    private suspend fun pullFromServer(lastServerSync: Long?) {
        try {
            val request = SyncRequest(
                lastSyncAt = lastServerSync,
                pendingChanges = emptyList(),
                deviceTime = System.currentTimeMillis()
            )

            val response = apiService.sync(request)

            if (response.isSuccessful && response.body() != null) {
                val syncResponse = response.body()!!

                // Merge server tasks
                syncResponse.tasks.forEach { serverTask ->
                    mergeServerTask(serverTask, syncResponse.syncedAt)
                }

                Log.d(TAG, "Pulled ${syncResponse.tasks.size} tasks from server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull from server failed: ${e.message}")
        }
    }

    /**
     * CRITICAL FIX: Cleanup old error entries (Issue #7)
     */
    private suspend fun cleanupOldErrors(olderThanDays: Int = 7) {
        try {
            val cutoff = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000)
            val oldErrors = starDao.getOldErrorTasks(cutoff)
            
            oldErrors.forEach { task ->
                starDao.deleteStarByLocalId(task.localId)
                syncQueueDao.deleteByLocalTaskId(task.localId)
                Log.d(TAG, "Cleaned up old error task: ${task.localId}")
            }
            
            if (oldErrors.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${oldErrors.size} old error tasks")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}")
        }
    }

    /**
     * User resolved a conflict
     */
    suspend fun resolveConflict(localId: String, useLocalVersion: Boolean) {
        val conflict = _conflicts.value.find { it.localId == localId }
        if (conflict == null) return
        
        if (useLocalVersion && conflict is ConflictResolution.RequiresUserChoice) {
            // Force re-sync of local version
            starDao.updateSyncStatus(localId, "pending")
            queueUpdate(localId, conflict.localData.toSyncPayload())
        }
        
        // Remove from conflicts list
        _conflicts.value = _conflicts.value.filter { it.localId != localId }
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
     * Update pending count
     */
    private suspend fun updatePendingCount() {
        _pendingCount.value = syncQueueDao.getPendingCount()
    }

    // Helper functions
    private fun parseDueDate(dateStr: String?, timeStr: String?): Long? {
        if (dateStr.isNullOrEmpty()) return null
        
        return try {
            if (dateStr.contains("T")) {
                java.time.Instant.parse(dateStr).toEpochMilli()
            } else {
                val datePart = java.time.LocalDate.parse(dateStr)
                val timePart = if (!timeStr.isNullOrEmpty()) {
                    if (timeStr.count { it == ':' } == 1) {
                        java.time.LocalTime.parse("$timeStr:00")
                    } else {
                        java.time.LocalTime.parse(timeStr)
                    }
                } else {
                    java.time.LocalTime.of(23, 59, 59)
                }
                java.time.LocalDateTime.of(datePart, timePart)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTimestamp(timestamp: String): Long? {
        return try {
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun TaskResponse.toStarEntity(localId: String, serverTimestamp: Long): StarEntity {
        return StarEntity(
            localId = localId,
            serverId = this.id,
            title = this.title ?: "",
            urgency = this.priority ?: 0,
            dueDate = parseDueDate(this.dueDate, this.dueTime),
            x = this.x?.toFloat() ?: 0.5f,
            y = this.y?.toFloat() ?: 0.5f,
            createdAt = this.createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis(),
            isSubtask = this.isSubtask ?: false,
            isRecurring = this.isRecurring ?: false,
            echoInterval = this.echoInterval,
            isCompleted = this.completed ?: false,
            completedAt = this.completedAt?.let { parseTimestamp(it) },
            isArchived = this.archived ?: false,
            archivedAt = this.archivedAt?.let { parseTimestamp(it) },
            syncStatus = "synced",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            serverUpdatedAt = serverTimestamp
        )
    }

    private fun StarEntity.toSyncPayload(): Map<String, Any?> {
        return mapOf(
            "title" to title,
            "priority" to urgency,
            "x" to x,
            "y" to y,
            "is_recurring" to isRecurring,
            "is_subtask" to isSubtask
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

/**
 * CRITICAL FIX: Conflict resolution tracking for UI
 */
sealed class ConflictResolution {
    abstract val localId: String
    
    data class AutoResolved(
        override val localId: String,
        val reason: String,
        val localData: StarEntity,
        val serverData: TaskResponse
    ) : ConflictResolution()
    
    data class RequiresUserChoice(
        override val localId: String,
        val localData: StarEntity,
        val serverData: TaskResponse
    ) : ConflictResolution()
}
