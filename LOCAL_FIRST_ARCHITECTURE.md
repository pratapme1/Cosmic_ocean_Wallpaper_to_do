# Local-First Architecture Redesign

> **Status:** PROPOSAL
> **Created:** 2026-02-01
> **Author:** Architecture Review
> **Priority:** CRITICAL

---

## Executive Summary

The current architecture has a **fatal flaw**: everything depends on network connectivity. This document proposes a complete redesign to a **local-first, hybrid architecture** where:

1. **All operations happen locally first** (instant response)
2. **Wallpaper generates on-device** (no network dependency)
3. **Background sync keeps cloud updated** (eventual consistency)
4. **App works 100% offline** (network is optional enhancement)

---

## Current Architecture Problems

### Problem 1: Task Operations Block on Network

```
CURRENT FLOW (SLOW):
User taps "Add Task"
    ↓
POST /api/tasks (WAIT 500ms-3s)
    ↓
Backend processes
    ↓
Response returns
    ↓
UI updates
    ↓
Trigger wallpaper update (ANOTHER network call)

TOTAL LATENCY: 1-5 seconds per action
```

**User Experience:** Sluggish, frustrating, feels broken on slow networks.

### Problem 2: Wallpaper Requires Backend

```
CURRENT FLOW:
GET /api/wallpaper (WAIT 1-3s)
    ↓
Backend generates PNG with Sharp/Satori
    ↓
Download 500KB-2MB image
    ↓
Set as system wallpaper

PROBLEMS:
- Requires network for CORE feature
- 1-3 second delay minimum
- Battery drain from downloads
- Backend compute costs
```

### Problem 3: Offline = Broken App

```
CURRENT STATE:
- Room database exists but is just a CACHE
- Source of truth is Supabase PostgreSQL
- No internet = Can't create tasks
- No internet = Wallpaper stuck on last state
- Sync endpoint exists but isn't used automatically
```

### Problem 4: Double-Parsing Waste

```
CURRENT FLOW:
1. Android parses input with LLM/local parser
2. Sends parsed data to backend
3. Backend RE-PARSES with NLP again
4. Double compute, double latency
```

### Problem 5: Real-Time Updates Are Fake

```
CURRENT STATE:
- RealTimeWallpaperService polls every 60 seconds
- Each poll = network call
- Not actually real-time
- Drains battery with constant network activity
```

---

## Proposed Architecture: Local-First Hybrid

### Core Principles

| Principle | Description |
|-----------|-------------|
| **Local First** | All operations happen in local SQLite immediately |
| **Optimistic UI** | UI updates instantly, sync happens in background |
| **Offline Capable** | App works 100% without internet |
| **Eventual Consistency** | Cloud syncs when network available |
| **On-Device Wallpaper** | Generate wallpaper locally, no network needed |
| **Smart Sync** | Only sync deltas, handle conflicts gracefully |

### New Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANDROID APP                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │   UI Layer   │───▶│  ViewModel   │───▶│  TaskRepository  │  │
│  │  (Compose)   │    │  (State)     │    │  (Local First)   │  │
│  └──────────────┘    └──────────────┘    └────────┬─────────┘  │
│                                                    │             │
│         ┌──────────────────────────────────────────┼─────┐      │
│         │              LOCAL LAYER                  │     │      │
│         │  ┌─────────────────┐  ┌────────────────┐ │     │      │
│         │  │   Room SQLite   │  │  Local NLP     │ │     │      │
│         │  │  (Source of     │  │  Parser        │ │     │      │
│         │  │   Truth)        │  │  (Instant)     │ │     │      │
│         │  └────────┬────────┘  └────────────────┘ │     │      │
│         │           │                               │     │      │
│         │  ┌────────▼────────┐  ┌────────────────┐ │     │      │
│         │  │  Wallpaper      │  │  Sync Engine   │ │     │      │
│         │  │  Generator      │  │  (Background)  │─┼─────┼──┐   │
│         │  │  (On-Device)    │  │                │ │     │  │   │
│         │  └─────────────────┘  └────────────────┘ │     │  │   │
│         └──────────────────────────────────────────┴─────┘  │   │
│                                                              │   │
└──────────────────────────────────────────────────────────────┼───┘
                                                               │
                              NETWORK (OPTIONAL)               │
                                                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                         BACKEND (Cloud)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │  Sync API   │  │  Auth API   │  │  LLM Enhancement API    │  │
│  │  (Deltas)   │  │  (JWT)      │  │  (Optional, for complex │  │
│  │             │  │             │  │   inputs only)          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                           │                                      │
│                    ┌──────▼──────┐                              │
│                    │  Supabase   │                              │
│                    │  PostgreSQL │                              │
│                    │  (Backup)   │                              │
│                    └─────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Redesigns

### 1. Local Database as Source of Truth

**Current:** Room is a cache, Supabase is source of truth
**New:** Room IS the source of truth, Supabase is backup/sync target

#### New Schema (Room)

```kotlin
// TaskEntity.kt - Enhanced for local-first
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Core fields
    val title: String,
    val rawInput: String,  // Original user input for re-parsing
    val dueDate: Long?,    // Epoch millis
    val dueTime: String?,  // "HH:mm" format
    val estimateMinutes: Int?,
    val priority: Int = 2,
    val category: String?,

    // State
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val snoozedUntil: Long? = null,

    // Privacy
    val isPrivate: Boolean = false,
    val privacyLevel: String = "public",

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // === NEW: Sync Metadata ===
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncVersion: Long = 0,  // Increments on each local change
    val serverVersion: Long? = null,  // Last known server version
    val lastSyncedAt: Long? = null,
    val syncError: String? = null,
    val isDeleted: Boolean = false  // Soft delete for sync
)

enum class SyncStatus {
    SYNCED,      // Matches server
    PENDING,     // Local changes not synced
    SYNCING,     // Currently syncing
    CONFLICT,    // Conflict detected
    ERROR        // Sync failed
}

// SyncQueueEntity.kt - Track pending operations
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,
    val operation: SyncOperation,
    val payload: String,  // JSON of changes
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE,
    COMPLETE
}
```

#### New DAO Operations

```kotlin
@Dao
interface TaskDao {
    // === LOCAL OPERATIONS (Instant) ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isDeleted = 1, syncStatus = 'PENDING', updatedAt = :now WHERE id = :taskId")
    suspend fun softDelete(taskId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET completed = 1, completedAt = :completedAt, syncStatus = 'PENDING', updatedAt = :now WHERE id = :taskId")
    suspend fun markCompleted(taskId: String, completedAt: Long, now: Long = System.currentTimeMillis())

    // === QUERIES ===

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND archived = 0 ORDER BY dueDate ASC, priority ASC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND completed = 0 AND archived = 0 ORDER BY priority ASC, dueDate ASC LIMIT 1")
    fun getTopTask(): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncTasks(): List<TaskEntity>

    // === SYNC OPERATIONS ===

    @Query("UPDATE tasks SET syncStatus = :status, lastSyncedAt = :syncedAt, serverVersion = :serverVersion WHERE id = :taskId")
    suspend fun updateSyncStatus(taskId: String, status: SyncStatus, syncedAt: Long, serverVersion: Long)

    @Query("UPDATE tasks SET syncStatus = 'ERROR', syncError = :error WHERE id = :taskId")
    suspend fun markSyncError(taskId: String, error: String)
}
```

### 2. Local-First Task Repository

```kotlin
// TaskRepository.kt - Redesigned for local-first

class TaskRepository(
    private val taskDao: TaskDao,
    private val syncQueueDao: SyncQueueDao,
    private val localParser: LocalTaskParser,
    private val syncEngine: SyncEngine,
    private val wallpaperGenerator: LocalWallpaperGenerator
) {

    // === TASK CREATION (Instant, Local-First) ===

    suspend fun createTask(rawInput: String): Result<TaskEntity> {
        // 1. Parse locally (INSTANT - no network)
        val parsed = localParser.parse(rawInput)

        // 2. Create task entity
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = parsed.title,
            rawInput = rawInput,
            dueDate = parsed.dueDate?.toEpochMilli(),
            dueTime = parsed.dueTime,
            estimateMinutes = parsed.estimateMinutes,
            priority = parsed.priority,
            category = parsed.category,
            syncStatus = SyncStatus.PENDING
        )

        // 3. Insert locally (INSTANT)
        taskDao.insert(task)

        // 4. Queue for background sync
        syncQueueDao.enqueue(SyncQueueEntity(
            taskId = task.id,
            operation = SyncOperation.CREATE,
            payload = task.toJson()
        ))

        // 5. Regenerate wallpaper locally (INSTANT)
        wallpaperGenerator.regenerate()

        // 6. Trigger background sync (non-blocking)
        syncEngine.syncNow()

        return Result.success(task)
    }

    // === TASK UPDATE (Instant, Local-First) ===

    suspend fun updateTask(taskId: String, updates: TaskUpdates): Result<TaskEntity> {
        // 1. Get current task
        val task = taskDao.getById(taskId) ?: return Result.failure(TaskNotFound())

        // 2. Apply updates locally
        val updatedTask = task.copy(
            title = updates.title ?: task.title,
            dueDate = updates.dueDate ?: task.dueDate,
            dueTime = updates.dueTime ?: task.dueTime,
            priority = updates.priority ?: task.priority,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            syncVersion = task.syncVersion + 1
        )

        // 3. Save locally (INSTANT)
        taskDao.update(updatedTask)

        // 4. Queue for sync
        syncQueueDao.enqueue(SyncQueueEntity(
            taskId = taskId,
            operation = SyncOperation.UPDATE,
            payload = updates.toJson()
        ))

        // 5. Regenerate wallpaper
        wallpaperGenerator.regenerate()

        // 6. Background sync
        syncEngine.syncNow()

        return Result.success(updatedTask)
    }

    // === TASK COMPLETION (Instant, Local-First) ===

    suspend fun completeTask(taskId: String): Result<Unit> {
        val now = System.currentTimeMillis()

        // 1. Mark complete locally (INSTANT)
        taskDao.markCompleted(taskId, now)

        // 2. Queue for sync
        syncQueueDao.enqueue(SyncQueueEntity(
            taskId = taskId,
            operation = SyncOperation.COMPLETE,
            payload = """{"completedAt": $now}"""
        ))

        // 3. Regenerate wallpaper
        wallpaperGenerator.regenerate()

        // 4. Background sync
        syncEngine.syncNow()

        return Result.success(Unit)
    }

    // === TASK DELETION (Instant, Soft Delete) ===

    suspend fun deleteTask(taskId: String): Result<Unit> {
        // 1. Soft delete locally (INSTANT)
        taskDao.softDelete(taskId)

        // 2. Queue for sync
        syncQueueDao.enqueue(SyncQueueEntity(
            taskId = taskId,
            operation = SyncOperation.DELETE,
            payload = "{}"
        ))

        // 3. Regenerate wallpaper
        wallpaperGenerator.regenerate()

        // 4. Background sync
        syncEngine.syncNow()

        return Result.success(Unit)
    }
}
```

### 3. On-Device Wallpaper Generation

**Current:** Backend generates PNG, downloads over network
**New:** Generate wallpaper on-device using Compose Canvas

```kotlin
// LocalWallpaperGenerator.kt

class LocalWallpaperGenerator(
    private val context: Context,
    private val taskDao: TaskDao,
    private val userPreferences: UserPreferences
) {
    private val wallpaperManager = WallpaperManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Debounce rapid updates
    private val regenerateChannel = Channel<Unit>(Channel.CONFLATED)

    init {
        // Process regeneration requests with debouncing
        scope.launch {
            regenerateChannel.receiveAsFlow()
                .debounce(300) // Wait 300ms for rapid changes to settle
                .collect { generateAndSetWallpaper() }
        }
    }

    fun regenerate() {
        regenerateChannel.trySend(Unit)
    }

    private suspend fun generateAndSetWallpaper() {
        try {
            // 1. Get current task and settings
            val topTask = taskDao.getTopTaskSync()
            val theme = userPreferences.theme
            val resolution = getScreenResolution()

            // 2. Generate bitmap on device
            val bitmap = generateWallpaperBitmap(
                task = topTask,
                theme = theme,
                width = resolution.width,
                height = resolution.height
            )

            // 3. Set as system wallpaper
            withContext(Dispatchers.Main) {
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK
                )
            }

            // 4. Recycle bitmap
            bitmap.recycle()

        } catch (e: Exception) {
            Log.e("WallpaperGenerator", "Failed to generate wallpaper", e)
        }
    }

    private suspend fun generateWallpaperBitmap(
        task: TaskEntity?,
        theme: WallpaperTheme,
        width: Int,
        height: Int
    ): Bitmap = withContext(Dispatchers.Default) {

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Calculate urgency level
        val urgency = task?.let { calculateUrgency(it) } ?: UrgencyLevel.CLEAR

        // Get theme colors
        val colors = theme.getColors(urgency)

        // Layer 1: Background gradient
        drawGradientBackground(canvas, colors, width, height)

        // Layer 2: Particle system (stars, bubbles, etc.)
        drawParticles(canvas, theme, width, height)

        // Layer 3: Task display
        if (task != null) {
            drawTaskCard(canvas, task, colors, width, height)
        } else {
            drawClearState(canvas, colors, width, height)
        }

        // Layer 4: Status indicators
        drawStatusIndicators(canvas, width, height)

        bitmap
    }

    private fun drawGradientBackground(
        canvas: android.graphics.Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            colors.gradientStart,
            colors.gradientEnd,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawTaskCard(
        canvas: android.graphics.Canvas,
        task: TaskEntity,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val centerX = width / 2f
        val centerY = height * 0.4f // Upper third for visibility above notifications

        // Task circle
        val circleRadius = width * 0.15f
        val circlePaint = Paint().apply {
            color = colors.taskCircle
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

        // Task title
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = width * 0.05f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Word wrap title
        val maxWidth = width * 0.8f
        val lines = wrapText(task.title, titlePaint, maxWidth)
        var yOffset = centerY + circleRadius + 60f

        for (line in lines) {
            canvas.drawText(line, centerX, yOffset, titlePaint)
            yOffset += titlePaint.textSize * 1.3f
        }

        // Due date/time
        task.dueDate?.let { dueDate ->
            val duePaint = Paint().apply {
                color = colors.dueText
                textSize = width * 0.035f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val dueText = formatDueDate(dueDate, task.dueTime)
            canvas.drawText(dueText, centerX, yOffset + 20f, duePaint)
        }
    }

    private fun calculateUrgency(task: TaskEntity): UrgencyLevel {
        val now = System.currentTimeMillis()
        val dueDate = task.dueDate ?: return UrgencyLevel.CALM

        val hoursUntilDue = (dueDate - now) / (1000 * 60 * 60)

        return when {
            hoursUntilDue < 0 -> UrgencyLevel.CRITICAL    // Overdue
            hoursUntilDue < 4 -> UrgencyLevel.URGENT      // Due in 4 hours
            hoursUntilDue < 24 -> UrgencyLevel.ATTENTION  // Due today
            hoursUntilDue < 48 -> UrgencyLevel.CALM       // Due tomorrow
            else -> UrgencyLevel.CALM
        }
    }
}

// Theme system
enum class WallpaperTheme {
    COSMIC, OCEAN, FANTASY;

    fun getColors(urgency: UrgencyLevel): ThemeColors {
        return when (this) {
            COSMIC -> when (urgency) {
                UrgencyLevel.CLEAR -> ThemeColors(0xFF0A1628.toInt(), 0xFF1A2F4A.toInt(), 0xFF00CED1.toInt(), 0xFF87CEEB.toInt())
                UrgencyLevel.CALM -> ThemeColors(0xFF0D1B2A.toInt(), 0xFF1B3A5F.toInt(), 0xFF4169E1.toInt(), 0xFFADD8E6.toInt())
                UrgencyLevel.ATTENTION -> ThemeColors(0xFF1A1A2E.toInt(), 0xFF2D2D44.toInt(), 0xFFFF8C00.toInt(), 0xFFFFD700.toInt())
                UrgencyLevel.URGENT -> ThemeColors(0xFF1A0A0A.toInt(), 0xFF2D1A1A.toInt(), 0xFFFF4500.toInt(), 0xFFFF6347.toInt())
                UrgencyLevel.CRITICAL -> ThemeColors(0xFF1A0000.toInt(), 0xFF330000.toInt(), 0xFFDC143C.toInt(), 0xFFFF0000.toInt())
            }
            OCEAN -> when (urgency) {
                UrgencyLevel.CLEAR -> ThemeColors(0xFF006994.toInt(), 0xFF00496B.toInt(), 0xFF20B2AA.toInt(), 0xFF7FFFD4.toInt())
                UrgencyLevel.CALM -> ThemeColors(0xFF003366.toInt(), 0xFF001F4D.toInt(), 0xFF4682B4.toInt(), 0xFF87CEEB.toInt())
                UrgencyLevel.ATTENTION -> ThemeColors(0xFF003344.toInt(), 0xFF002233.toInt(), 0xFFDAA520.toInt(), 0xFFFFD700.toInt())
                UrgencyLevel.URGENT -> ThemeColors(0xFF002222.toInt(), 0xFF001111.toInt(), 0xFFFF8C00.toInt(), 0xFFFFA500.toInt())
                UrgencyLevel.CRITICAL -> ThemeColors(0xFF1A0505.toInt(), 0xFF0D0202.toInt(), 0xFFB22222.toInt(), 0xFFDC143C.toInt())
            }
            FANTASY -> when (urgency) {
                UrgencyLevel.CLEAR -> ThemeColors(0xFF2E1A47.toInt(), 0xFF1A0D2E.toInt(), 0xFFFFD700.toInt(), 0xFFFAFAD2.toInt())
                UrgencyLevel.CALM -> ThemeColors(0xFF4A1A6B.toInt(), 0xFF2E0D47.toInt(), 0xFF9370DB.toInt(), 0xFFE6E6FA.toInt())
                UrgencyLevel.ATTENTION -> ThemeColors(0xFF3D1A47.toInt(), 0xFF2A0D33.toInt(), 0xFFFF8C00.toInt(), 0xFFFFD700.toInt())
                UrgencyLevel.URGENT -> ThemeColors(0xFF2E0A1A.toInt(), 0xFF1A050D.toInt(), 0xFFFF6347.toInt(), 0xFFFF7F50.toInt())
                UrgencyLevel.CRITICAL -> ThemeColors(0xFF1A0000.toInt(), 0xFF0D0000.toInt(), 0xFFDC143C.toInt(), 0xFFFF0000.toInt())
            }
        }
    }
}

enum class UrgencyLevel {
    CLEAR,      // No tasks
    CALM,       // Tasks but not urgent
    ATTENTION,  // Due within 24h
    URGENT,     // Due within 4h
    CRITICAL    // Overdue
}

data class ThemeColors(
    val gradientStart: Int,
    val gradientEnd: Int,
    val taskCircle: Int,
    val dueText: Int
)
```

### 4. Background Sync Engine

```kotlin
// SyncEngine.kt

class SyncEngine(
    private val context: Context,
    private val taskDao: TaskDao,
    private val syncQueueDao: SyncQueueDao,
    private val apiService: ApiService,
    private val connectivityManager: ConnectivityManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()

    // Sync state
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Trigger sync (debounced, non-blocking)
    private val syncTrigger = Channel<Unit>(Channel.CONFLATED)

    init {
        // Process sync triggers with debounce
        scope.launch {
            syncTrigger.receiveAsFlow()
                .debounce(1000) // Wait 1 second for batch changes
                .collect { performSync() }
        }

        // Monitor connectivity and sync when online
        scope.launch {
            connectivityManager.networkCallback.collect { isConnected ->
                if (isConnected) {
                    syncNow()
                }
            }
        }

        // Periodic sync every 5 minutes when online
        scope.launch {
            while (true) {
                delay(5 * 60 * 1000) // 5 minutes
                if (isOnline()) {
                    syncNow()
                }
            }
        }
    }

    fun syncNow() {
        syncTrigger.trySend(Unit)
    }

    private suspend fun performSync() {
        if (!isOnline()) {
            _syncState.value = SyncState.Offline
            return
        }

        syncMutex.withLock {
            try {
                _syncState.value = SyncState.Syncing

                // 1. Get pending changes from queue
                val pendingChanges = syncQueueDao.getPendingChanges()

                if (pendingChanges.isEmpty()) {
                    // Just pull latest from server
                    pullFromServer()
                    _syncState.value = SyncState.Synced(System.currentTimeMillis())
                    return
                }

                // 2. Push local changes to server
                val pushResult = pushChanges(pendingChanges)

                // 3. Handle conflicts
                if (pushResult.conflicts.isNotEmpty()) {
                    resolveConflicts(pushResult.conflicts)
                }

                // 4. Pull latest from server
                pullFromServer()

                // 5. Clear synced items from queue
                syncQueueDao.removeProcessed(pushResult.processedIds)

                _syncState.value = SyncState.Synced(System.currentTimeMillis())

            } catch (e: Exception) {
                Log.e("SyncEngine", "Sync failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Sync failed")

                // Retry with exponential backoff
                scheduleRetry()
            }
        }
    }

    private suspend fun pushChanges(changes: List<SyncQueueEntity>): PushResult {
        val request = SyncPushRequest(
            changes = changes.map { change ->
                SyncChange(
                    taskId = change.taskId,
                    operation = change.operation.name,
                    payload = change.payload,
                    clientTimestamp = change.createdAt
                )
            }
        )

        val response = apiService.pushChanges(request)

        // Update local tasks with server versions
        response.results.forEach { result ->
            when (result.status) {
                "success" -> {
                    taskDao.updateSyncStatus(
                        taskId = result.taskId,
                        status = SyncStatus.SYNCED,
                        syncedAt = System.currentTimeMillis(),
                        serverVersion = result.serverVersion
                    )
                }
                "conflict" -> {
                    taskDao.updateSyncStatus(
                        taskId = result.taskId,
                        status = SyncStatus.CONFLICT,
                        syncedAt = System.currentTimeMillis(),
                        serverVersion = result.serverVersion
                    )
                }
                "error" -> {
                    taskDao.markSyncError(result.taskId, result.error ?: "Unknown error")
                }
            }
        }

        return PushResult(
            processedIds = response.results.filter { it.status == "success" }.map { it.taskId },
            conflicts = response.results.filter { it.status == "conflict" }
        )
    }

    private suspend fun pullFromServer() {
        val lastSync = taskDao.getLastSyncTimestamp() ?: 0

        val response = apiService.pullChanges(lastSync)

        response.tasks.forEach { serverTask ->
            val localTask = taskDao.getById(serverTask.id)

            if (localTask == null) {
                // New task from server
                taskDao.insert(serverTask.toEntity().copy(syncStatus = SyncStatus.SYNCED))
            } else if (localTask.syncStatus == SyncStatus.SYNCED) {
                // Local is synced, update with server version
                taskDao.update(serverTask.toEntity().copy(syncStatus = SyncStatus.SYNCED))
            }
            // If local has pending changes, keep local (will push on next sync)
        }
    }

    private suspend fun resolveConflicts(conflicts: List<SyncResult>) {
        // Default strategy: Last-Write-Wins based on timestamp
        // User can configure different strategies

        conflicts.forEach { conflict ->
            val localTask = taskDao.getById(conflict.taskId) ?: return@forEach
            val serverTask = conflict.serverData?.toEntity() ?: return@forEach

            if (localTask.updatedAt > serverTask.updatedAt) {
                // Local is newer, re-queue for push
                syncQueueDao.enqueue(SyncQueueEntity(
                    taskId = localTask.id,
                    operation = SyncOperation.UPDATE,
                    payload = localTask.toJson()
                ))
            } else {
                // Server is newer, accept server version
                taskDao.update(serverTask.copy(syncStatus = SyncStatus.SYNCED))
            }
        }
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Offline : SyncState()
    object Syncing : SyncState()
    data class Synced(val timestamp: Long) : SyncState()
    data class Error(val message: String) : SyncState()
}
```

### 5. Enhanced Local NLP Parser

**Current:** Basic pattern matching
**New:** Improved local parser that handles 95% of inputs without LLM

```kotlin
// LocalTaskParser.kt

class LocalTaskParser {

    // Compiled regex patterns for performance
    private val timePatterns = listOf(
        // Absolute times
        Regex("""(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,2}):(\d{2})"""),
        // Relative times
        Regex("""in\s+(\d+)\s*(min(?:ute)?s?|hrs?|hours?)""", RegexOption.IGNORE_CASE),
    )

    private val datePatterns = listOf(
        // Named days
        Regex("""(today|tonight|tomorrow|tom)""", RegexOption.IGNORE_CASE),
        Regex("""(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)""", RegexOption.IGNORE_CASE),
        // Relative
        Regex("""in\s+(\d+)\s*(day|week|month)s?""", RegexOption.IGNORE_CASE),
        // Absolute
        Regex("""(\d{1,2})[/\-](\d{1,2})(?:[/\-](\d{2,4}))?"""),
        Regex("""(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+(\d{1,2})(?:st|nd|rd|th)?""", RegexOption.IGNORE_CASE),
    )

    private val durationPatterns = listOf(
        Regex("""(\d+)\s*(min(?:ute)?s?|m)\b""", RegexOption.IGNORE_CASE),
        Regex("""(\d+)\s*(hrs?|hours?|h)\b""", RegexOption.IGNORE_CASE),
        Regex("""(\d+(?:\.\d+)?)\s*h(?:ours?)?""", RegexOption.IGNORE_CASE),
    )

    private val priorityPatterns = listOf(
        Regex("""!{3}|urgent(?:ly)?|asap|critical|emergency""", RegexOption.IGNORE_CASE) to 1,
        Regex("""!{2}|important|high\s*priority""", RegexOption.IGNORE_CASE) to 1,
        Regex("""!|priority""", RegexOption.IGNORE_CASE) to 2,
        Regex("""low\s*priority|whenever|eventually|someday""", RegexOption.IGNORE_CASE) to 3,
    )

    private val categoryPatterns = mapOf(
        "work" to Regex("""@work|work|meeting|email|call|project|deadline|boss|client|office""", RegexOption.IGNORE_CASE),
        "personal" to Regex("""@personal|personal|home|family|friend""", RegexOption.IGNORE_CASE),
        "health" to Regex("""@health|health|gym|exercise|workout|doctor|medicine|run|walk""", RegexOption.IGNORE_CASE),
        "shopping" to Regex("""@shopping|buy|shop|store|grocery|amazon""", RegexOption.IGNORE_CASE),
        "finance" to Regex("""@finance|pay|bill|bank|money|budget|invoice""", RegexOption.IGNORE_CASE),
    )

    fun parse(input: String): ParsedTask {
        var remainingInput = input.trim()

        // Extract components
        val (time, inputAfterTime) = extractTime(remainingInput)
        remainingInput = inputAfterTime

        val (date, inputAfterDate) = extractDate(remainingInput)
        remainingInput = inputAfterDate

        val (duration, inputAfterDuration) = extractDuration(remainingInput)
        remainingInput = inputAfterDuration

        val priority = extractPriority(input)
        val category = extractCategory(input)

        // Clean up title (remove extracted patterns)
        val title = cleanTitle(remainingInput)

        return ParsedTask(
            title = title.ifBlank { input },
            dueDate = date,
            dueTime = time,
            estimateMinutes = duration,
            priority = priority,
            category = category,
            confidence = calculateConfidence(time, date, duration)
        )
    }

    private fun extractTime(input: String): Pair<String?, String> {
        // Check for "in X minutes" pattern
        val relativeMatch = Regex("""in\s+(\d+)\s*(min(?:ute)?s?)""", RegexOption.IGNORE_CASE).find(input)
        if (relativeMatch != null) {
            val minutes = relativeMatch.groupValues[1].toInt()
            val futureTime = LocalTime.now().plusMinutes(minutes.toLong())
            return Pair(
                futureTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                input.replace(relativeMatch.value, "").trim()
            )
        }

        // Check for absolute time
        for (pattern in timePatterns) {
            val match = pattern.find(input)
            if (match != null) {
                val time = parseTimeMatch(match)
                if (time != null) {
                    return Pair(time, input.replace(match.value, "").trim())
                }
            }
        }

        return Pair(null, input)
    }

    private fun extractDate(input: String): Pair<LocalDate?, String> {
        val today = LocalDate.now()

        // Today/tonight
        if (input.contains(Regex("""today|tonight""", RegexOption.IGNORE_CASE))) {
            val cleaned = input.replace(Regex("""today|tonight""", RegexOption.IGNORE_CASE), "").trim()
            return Pair(today, cleaned)
        }

        // Tomorrow
        if (input.contains(Regex("""tomorrow|tom\b""", RegexOption.IGNORE_CASE))) {
            val cleaned = input.replace(Regex("""tomorrow|tom\b""", RegexOption.IGNORE_CASE), "").trim()
            return Pair(today.plusDays(1), cleaned)
        }

        // Day names
        val dayMatch = Regex("""(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)""", RegexOption.IGNORE_CASE).find(input)
        if (dayMatch != null) {
            val targetDay = parseDayOfWeek(dayMatch.value)
            val date = getNextDayOfWeek(today, targetDay)
            return Pair(date, input.replace(dayMatch.value, "").trim())
        }

        // "in X days/weeks"
        val relativeMatch = Regex("""in\s+(\d+)\s*(day|week|month)s?""", RegexOption.IGNORE_CASE).find(input)
        if (relativeMatch != null) {
            val amount = relativeMatch.groupValues[1].toLong()
            val unit = relativeMatch.groupValues[2].lowercase()
            val date = when (unit) {
                "day" -> today.plusDays(amount)
                "week" -> today.plusWeeks(amount)
                "month" -> today.plusMonths(amount)
                else -> today
            }
            return Pair(date, input.replace(relativeMatch.value, "").trim())
        }

        return Pair(null, input)
    }

    private fun extractDuration(input: String): Pair<Int?, String> {
        // Minutes
        val minMatch = Regex("""(\d+)\s*(min(?:ute)?s?|m)\b""", RegexOption.IGNORE_CASE).find(input)
        if (minMatch != null) {
            val minutes = minMatch.groupValues[1].toInt()
            return Pair(minutes, input.replace(minMatch.value, "").trim())
        }

        // Hours
        val hourMatch = Regex("""(\d+(?:\.\d+)?)\s*(hrs?|hours?|h)\b""", RegexOption.IGNORE_CASE).find(input)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toDouble()
            return Pair((hours * 60).toInt(), input.replace(hourMatch.value, "").trim())
        }

        return Pair(null, input)
    }

    private fun extractPriority(input: String): Int {
        for ((pattern, priority) in priorityPatterns) {
            if (pattern.containsMatchIn(input)) {
                return priority
            }
        }
        return 2 // Default normal priority
    }

    private fun extractCategory(input: String): String? {
        for ((category, pattern) in categoryPatterns) {
            if (pattern.containsMatchIn(input)) {
                return category
            }
        }
        return null
    }

    private fun cleanTitle(input: String): String {
        var title = input

        // Remove common filler words at start
        title = title.replace(Regex("""^(to|need to|have to|must|should|gonna|going to)\s+""", RegexOption.IGNORE_CASE), "")

        // Remove trailing prepositions
        title = title.replace(Regex("""\s+(at|by|on|in|for)$""", RegexOption.IGNORE_CASE), "")

        // Clean up extra spaces
        title = title.replace(Regex("""\s+"""), " ").trim()

        // Capitalize first letter
        return title.replaceFirstChar { it.uppercase() }
    }

    private fun calculateConfidence(time: String?, date: LocalDate?, duration: Int?): Float {
        var confidence = 0.5f
        if (time != null) confidence += 0.15f
        if (date != null) confidence += 0.2f
        if (duration != null) confidence += 0.1f
        return confidence.coerceAtMost(0.95f)
    }
}

data class ParsedTask(
    val title: String,
    val dueDate: LocalDate?,
    val dueTime: String?,
    val estimateMinutes: Int?,
    val priority: Int,
    val category: String?,
    val confidence: Float
)
```

### 6. Backend API Changes

The backend becomes a **sync target** rather than the source of truth.

```javascript
// New sync endpoints for backend/server.js

// POST /api/sync/push - Receive changes from client
app.post('/api/sync/push', authMiddleware, async (req, res) => {
    const { changes } = req.body;
    const userId = req.user.id;

    const results = [];

    for (const change of changes) {
        try {
            const result = await processChange(userId, change);
            results.push(result);
        } catch (error) {
            results.push({
                taskId: change.taskId,
                status: 'error',
                error: error.message
            });
        }
    }

    res.json({ results });
});

async function processChange(userId, change) {
    const { taskId, operation, payload, clientTimestamp } = change;

    switch (operation) {
        case 'CREATE':
            return await createTask(userId, taskId, JSON.parse(payload));
        case 'UPDATE':
            return await updateTask(userId, taskId, JSON.parse(payload), clientTimestamp);
        case 'DELETE':
            return await deleteTask(userId, taskId);
        case 'COMPLETE':
            return await completeTask(userId, taskId, JSON.parse(payload));
        default:
            throw new Error(`Unknown operation: ${operation}`);
    }
}

async function updateTask(userId, taskId, updates, clientTimestamp) {
    // Get current server state
    const { rows: [serverTask] } = await pool.query(
        'SELECT * FROM tasks WHERE id = $1 AND user_id = $2',
        [taskId, userId]
    );

    if (!serverTask) {
        return { taskId, status: 'error', error: 'Task not found' };
    }

    // Check for conflict (server modified after client change)
    const serverUpdatedAt = new Date(serverTask.updated_at).getTime();
    if (serverUpdatedAt > clientTimestamp) {
        return {
            taskId,
            status: 'conflict',
            serverVersion: serverTask.version,
            serverData: serverTask
        };
    }

    // Apply update
    const { rows: [updated] } = await pool.query(`
        UPDATE tasks
        SET
            title = COALESCE($1, title),
            due_date = COALESCE($2, due_date),
            due_time = COALESCE($3, due_time),
            priority = COALESCE($4, priority),
            updated_at = NOW(),
            version = version + 1
        WHERE id = $5 AND user_id = $6
        RETURNING *
    `, [updates.title, updates.dueDate, updates.dueTime, updates.priority, taskId, userId]);

    return {
        taskId,
        status: 'success',
        serverVersion: updated.version
    };
}

// GET /api/sync/pull - Send changes to client
app.get('/api/sync/pull', authMiddleware, async (req, res) => {
    const { since } = req.query; // Timestamp of last sync
    const userId = req.user.id;

    const { rows: tasks } = await pool.query(`
        SELECT * FROM tasks
        WHERE user_id = $1 AND updated_at > to_timestamp($2 / 1000.0)
        ORDER BY updated_at ASC
    `, [userId, since || 0]);

    res.json({
        tasks,
        serverTimestamp: Date.now()
    });
});
```

---

## Migration Plan

### Phase 1: Local Database Enhancement (Week 1)

**Goal:** Make Room the source of truth without breaking existing functionality.

| Task | Description | Risk |
|------|-------------|------|
| 1.1 | Add sync metadata columns to TaskEntity | Low |
| 1.2 | Create SyncQueueEntity and DAO | Low |
| 1.3 | Implement soft delete (isDeleted flag) | Low |
| 1.4 | Database migration for existing data | Medium |

**Rollback:** Revert database schema, no data loss.

### Phase 2: Local-First Repository (Week 2)

**Goal:** All CRUD operations happen locally first.

| Task | Description | Risk |
|------|-------------|------|
| 2.1 | Refactor TaskRepository for local-first | Medium |
| 2.2 | Implement sync queue insertion | Low |
| 2.3 | Add optimistic UI updates | Low |
| 2.4 | Keep existing API calls as background sync | Low |

**Rollback:** Feature flag to revert to network-first.

### Phase 3: On-Device Wallpaper Generation (Week 3)

**Goal:** Generate wallpaper without network.

| Task | Description | Risk |
|------|-------------|------|
| 3.1 | Create LocalWallpaperGenerator with Canvas | High |
| 3.2 | Port theme colors from backend | Low |
| 3.3 | Implement particle systems | Medium |
| 3.4 | Add text rendering with wrapping | Medium |
| 3.5 | Integration with WallpaperManager | Low |

**Rollback:** Feature flag to use backend wallpaper.

### Phase 4: Sync Engine (Week 4)

**Goal:** Robust background synchronization.

| Task | Description | Risk |
|------|-------------|------|
| 4.1 | Create SyncEngine class | Medium |
| 4.2 | Implement push changes to server | Medium |
| 4.3 | Implement pull changes from server | Medium |
| 4.4 | Conflict resolution (last-write-wins) | Medium |
| 4.5 | Retry with exponential backoff | Low |
| 4.6 | Sync state UI indicator | Low |

**Rollback:** Disable sync engine, use manual sync.

### Phase 5: Enhanced Local Parser (Week 5)

**Goal:** Handle 95% of inputs without LLM.

| Task | Description | Risk |
|------|-------------|------|
| 5.1 | Expand time pattern matching | Low |
| 5.2 | Add relative date parsing | Low |
| 5.3 | Improve duration extraction | Low |
| 5.4 | Add category detection | Low |
| 5.5 | LLM only for low-confidence inputs | Medium |

**Rollback:** Feature flag to use LLM always.

### Phase 6: Backend Simplification (Week 6)

**Goal:** Backend becomes sync server only.

| Task | Description | Risk |
|------|-------------|------|
| 6.1 | Create /api/sync/push endpoint | Medium |
| 6.2 | Create /api/sync/pull endpoint | Medium |
| 6.3 | Remove wallpaper generation (optional) | Low |
| 6.4 | Keep legacy endpoints for gradual migration | Low |

**Rollback:** Full backward compatibility maintained.

---

## Performance Comparison

### Task Creation

| Metric | Current | Local-First | Improvement |
|--------|---------|-------------|-------------|
| Time to UI update | 1-5 seconds | <100ms | **50x faster** |
| Network calls | 2 (create + wallpaper) | 0 (async sync) | **100% offline** |
| Battery usage | High | Low | **Significant** |

### Wallpaper Update

| Metric | Current | Local-First | Improvement |
|--------|---------|-------------|-------------|
| Time to update | 2-5 seconds | <500ms | **10x faster** |
| Network calls | 1 per update | 0 | **100% offline** |
| Data usage | 0.5-2MB per image | 0 | **No data usage** |

### Offline Capability

| Scenario | Current | Local-First |
|----------|---------|-------------|
| Create task offline | ❌ Fails | ✅ Works instantly |
| Complete task offline | ❌ Fails | ✅ Works instantly |
| Update wallpaper offline | ❌ Stuck | ✅ Works instantly |
| View tasks offline | ⚠️ Cached only | ✅ Full functionality |

---

## Testing Strategy

### Unit Tests

```kotlin
// TaskRepositoryTest.kt
@Test
fun `createTask saves locally and queues sync`() = runTest {
    // Given
    val input = "Email manager tomorrow at 3pm"

    // When
    val result = repository.createTask(input)

    // Then
    assertTrue(result.isSuccess)

    // Verify saved locally
    val task = taskDao.getById(result.getOrThrow().id)
    assertNotNull(task)
    assertEquals("Email manager", task.title)
    assertEquals(SyncStatus.PENDING, task.syncStatus)

    // Verify queued for sync
    val queue = syncQueueDao.getPendingChanges()
    assertEquals(1, queue.size)
    assertEquals(SyncOperation.CREATE, queue[0].operation)
}

// LocalWallpaperGeneratorTest.kt
@Test
fun `generateWallpaper creates valid bitmap`() = runTest {
    // Given
    val task = TaskEntity(title = "Test Task", priority = 1)

    // When
    val bitmap = generator.generateWallpaperBitmap(task, WallpaperTheme.COSMIC, 1080, 2400)

    // Then
    assertNotNull(bitmap)
    assertEquals(1080, bitmap.width)
    assertEquals(2400, bitmap.height)
}
```

### Integration Tests

```kotlin
// SyncEngineIntegrationTest.kt
@Test
fun `sync engine handles offline then online`() = runTest {
    // Given - Create tasks while offline
    networkMonitor.setOffline()
    repository.createTask("Task 1")
    repository.createTask("Task 2")

    // When - Come back online
    networkMonitor.setOnline()
    syncEngine.syncNow()
    advanceUntilIdle()

    // Then - Tasks synced to server
    assertEquals(SyncState.Synced::class, syncEngine.syncState.value::class)

    val serverTasks = apiService.getTasks()
    assertEquals(2, serverTasks.size)
}
```

---

## Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Data loss during sync | High | Low | Soft delete, conflict detection, backup before sync |
| Wallpaper quality issues | Medium | Medium | Side-by-side comparison testing, fallback to backend |
| Sync conflicts | Medium | Medium | Last-write-wins with user notification |
| Battery drain from local generation | Low | Low | Debouncing, efficient bitmap operations |
| Database migration failures | High | Low | Backup, phased rollout, rollback plan |

---

## Success Metrics

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Task creation latency | <100ms | Instrumentation |
| Wallpaper update latency | <500ms | Instrumentation |
| Offline task creation success | 100% | Automated testing |
| Sync success rate | >99% | Server-side logging |
| Battery usage reduction | >50% | Android Profiler |
| Data usage reduction | >90% | Network profiler |

---

## Conclusion

The local-first architecture fundamentally improves the user experience by:

1. **Instant Response:** All operations happen locally in <100ms
2. **100% Offline:** Full functionality without internet
3. **Battery Efficient:** No constant network polling
4. **Data Efficient:** No wallpaper downloads
5. **Reliable:** Works in poor network conditions

The migration is phased over 6 weeks with feature flags and rollback plans at each stage. The backend remains as a sync target for data backup and multi-device support.

**Recommendation:** Proceed with Phase 1 implementation immediately.

---

## Appendix: File Changes Summary

### New Files to Create

```
android/app/src/main/java/com/cosmicocean/
├── sync/
│   ├── SyncEngine.kt
│   ├── SyncState.kt
│   ├── SyncQueueDao.kt
│   └── SyncQueueEntity.kt
├── wallpaper/
│   ├── LocalWallpaperGenerator.kt
│   ├── WallpaperTheme.kt
│   ├── ThemeColors.kt
│   ├── ParticleRenderer.kt
│   └── TextRenderer.kt
├── parser/
│   └── LocalTaskParser.kt (enhanced)
└── data/
    └── TaskEntity.kt (modified with sync fields)

backend/
├── routes/
│   └── sync.js (new)
└── server.js (modified)
```

### Files to Modify

```
android/
├── TaskRepository.kt → Local-first operations
├── CosmicDatabase.kt → New entities, migrations
├── RealTimeWallpaperService.kt → Use local generator
└── MainActivity.kt → Sync state UI

backend/
└── server.js → Add sync endpoints
```
