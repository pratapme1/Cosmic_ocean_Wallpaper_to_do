# Local-First Architecture Review

> **Date:** 2026-02-01
> **Scope:** Android local-first implementation
> **Status:** 12 Critical Issues Identified

---

## Executive Summary

The current local-first architecture has **12 critical issues** that could lead to:
- Data loss or duplication
- Sync inconsistencies
- Poor offline experience
- Confusing user behavior

**Recommendation:** Address these issues before shipping to production.

---

## Architecture Overview

### Current Flow
```
User Action → Local DB (pending) → UI Update → Background Sync → Server
                                        ↓
                                  On Failure → Sync Queue → Retry
```

### Key Components
1. **TaskRepository.kt** - Data access layer with local-first writes
2. **SyncManager.kt** - Background sync coordinator
3. **Room Database** - Local persistence with sync metadata
4. **Backend Sync API** - POST /api/sync with conflict resolution

---

## Critical Issues Identified

### 🔴 Issue #1: Inconsistent Sync Integration (CRITICAL)

**Problem:** TaskRepository has dual sync paths that conflict with each other.

**Current Behavior:**
```kotlin
// In TaskRepository.addStar():
// Path 1: Direct API call
try {
    val response = apiService.createTask(...)
    if (response.isSuccessful) {
        // Replace local ID with backend UUID
        starDao.deleteStarById(oldId)
        starDao.insertStar(star.toEntity())
    }
} catch (e: Exception) {
    // Path 2: Only on failure, queue to SyncManager
    syncManager?.queueCreate(oldId, data)
}
```

**Problems:**
1. Direct API calls bypass the sync queue entirely
2. If API succeeds but DB operations fail, data is inconsistent
3. SyncManager and TaskRepository maintain separate retry logic
4. No single source of truth for sync state

**Impact:**
- Tasks created online may not go through sync queue
- Duplicates possible if retry logic conflicts
- Unclear sync status to user

**Fix:**
```kotlin
// Always use SyncManager for all operations
suspend fun addStar(star: Star) {
    // 1. Save to local DB with pending status
    val entity = star.toEntity().copy(syncStatus = "pending")
    starDao.insertStar(entity)
    
    // 2. ALWAYS queue to SyncManager (not direct API)
    syncManager.queueCreate(star.id, mapOf(...))
    
    // 3. UI is updated via Flow from local DB
}
```

---

### 🔴 Issue #2: ID Management Chaos (CRITICAL)

**Problem:** Task IDs change after sync, breaking references.

**Current Flow:**
1. Create task locally → ID = "star-abc123" (local)
2. Sync to backend → Success
3. Delete old local record, insert with UUID = "550e8400-e29b-41d4-a716-446655440000"
4. Sync queue still has "star-abc123" reference

**Problems:**
1. SyncQueueEntity stores taskId which becomes invalid after sync
2. Any pending operations for old ID are orphaned
3. Constellation links break (they reference star IDs)
4. Orbit relationships break

**Impact:**
- Broken task relationships after sync
- Orphaned sync queue entries
- Data integrity issues

**Fix Options:**

**Option A: Keep Local ID as client_id, add server_id field**
```kotlin
data class StarEntity(
    @PrimaryKey val localId: String,  // Never changes
    val serverId: String?,            // Null until synced
    // ... other fields
)
```

**Option B: Use UUID locally from the start**
```kotlin
// Generate UUID locally, send to server
val localId = UUID.randomUUID().toString()
// Server accepts client-provided ID or returns conflict
```

**Recommendation:** Option A - maintains backward compatibility

---

### 🔴 Issue #3: No Transaction Boundaries (CRITICAL)

**Problem:** Database operations lack transactions, risking corruption.

**Example in TaskRepository:**
```kotlin
// Lines 166-173
starDao.deleteStarById(oldId)  // Success
Log.d(TAG, "Deleted old local star: $oldId")

// What if app crashes HERE?

star.id = backendTask.id
starDao.insertStar(star.toEntity())  // Never executed
```

**Impact:**
- Data loss if crash mid-sync
- Inconsistent state (deleted locally but not re-inserted)
- User sees task disappear

**Fix:**
```kotlin
// Wrap in transaction
@Transaction
suspend fun replaceStar(oldId: String, newStar: Star) {
    deleteStarById(oldId)
    insertStar(newStar.toEntity())
}

// In repository
try {
    val response = apiService.createTask(...)
    if (response.isSuccessful) {
        starDao.replaceStar(oldId, star)  // Atomic operation
    }
}
```

---

### 🔴 Issue #4: Clock Skew & Timestamp Issues (CRITICAL)

**Problem:** Last-write-wins relies on device timestamps which can be wrong.

**Current Logic:**
```kotlin
// TaskRepository.kt line 44
val entity = star.toEntity().copy(
    syncStatus = "pending",
    updatedAt = System.currentTimeMillis()  // Device time!
)
```

**Problems:**
1. User's device clock could be wrong (fast/slow)
2. Timezone changes while traveling
3. Different devices have different times
4. Last-write-wins becomes unpredictable

**Impact:**
- Wrong conflict resolution
- Recent changes overwritten by older ones
- User confusion: "Why did my edit disappear?"

**Fix:**
```kotlin
// Use server timestamps only
// Option 1: Hybrid Logical Clock (HLC)
// Option 2: Vector clocks (complex)
// Option 3: Server as timestamp authority

// In SyncManager.performSync():
val request = SyncRequest(
    lastSyncAt = getLastSyncTimestamp(),
    pendingChanges = syncChanges,
    deviceTime = System.currentTimeMillis()  // For reference only
)

// Server returns serverTimestamp in response
// Client uses server timestamp for next sync
```

---

### 🟡 Issue #5: Sync Queue Data Loss on Merge (HIGH)

**Problem:** queueUpdate() merges data but doesn't handle failures.

**Current Code:**
```kotlin
// SyncManager.kt lines 143-161
suspend fun queueUpdate(taskId: String, data: Map<String, Any?>) {
    val existing = syncQueueDao.getLatestForTask(taskId)
    if (existing?.operation == "create") {
        // Merge update into create
        val existingData = gson.fromJson(existing.payload, Map::class.java)
        existingData.putAll(data)
        syncQueueDao.deleteById(existing.id)
        syncQueueDao.insert(existing.copy(payload = gson.toJson(existingData)))
    }
    // ...
}
```

**Problem:**
If the create fails and is marked as error, the merged updates are lost forever.

**Fix:**
```kotlin
// Keep updates separate even if create fails
suspend fun queueUpdate(taskId: String, data: Map<String, Any?>) {
    // Always queue as separate update
    // Server handles merging on its end
    val entity = SyncQueueEntity(
        taskId = taskId,
        operation = "update",
        payload = gson.toJson(data)
    )
    syncQueueDao.insert(entity)
}

// Let server handle merging:
// Server sees: [create(id=X, data=A), update(id=X, data=B)]
// Server merges: data = A + B
```

---

### 🟡 Issue #6: No Visibility into Conflicts (HIGH)

**Problem:** Conflicts are resolved silently without user knowledge.

**Current Code:**
```kotlin
// SyncManager.kt lines 274-308
response.conflicts.forEach { conflict ->
    Log.w(TAG, "Conflict for ${conflict.clientId}: ${conflict.reason}")
    when (conflict.reason) {
        "already_exists", "stale_data" -> {
            syncQueueDao.deleteByTaskId(conflict.clientId)
            conflict.serverData?.let { mergeServerTask(it) }
        }
    }
}
```

**Problems:**
1. User never knows their data was overwritten
2. No UI to show conflict resolution
3. Important edits could be lost silently

**Fix:**
```kotlin
// Add conflict tracking
sealed class ConflictResolution {
    data class AutoResolved(
        val taskId: String,
        val reason: String,
        val localData: StarEntity,
        val serverData: TaskResponse
    ) : ConflictResolution()
    
    data class RequiresUserChoice(
        val taskId: String,
        val localData: StarEntity,
        val serverData: TaskResponse
    ) : ConflictResolution()
}

// Expose conflicts as Flow
val conflictFlow: StateFlow<List<ConflictResolution>> = ...

// UI can show: "Your edit was overwritten by server version. Restore?"
```

---

### 🟡 Issue #7: Sync Queue Cleanup Missing (HIGH)

**Problem:** Error entries accumulate without cleanup.

**Current Code:**
```kotlin
// Lines 240-242
if (entity.retryCount + 1 >= MAX_RETRY_COUNT) {
    starDao.markSyncError(entity.taskId)
    syncQueueDao.deleteById(entity.id)  // Removed from queue
    Log.w(TAG, "Max retries reached for task ${entity.taskId}")
}
```

**Problem:**
Task is marked with `syncStatus = "error"` but never cleaned up.

**Impact:**
- Tasks with error status accumulate in DB
- User sees sync errors that are old
- Database grows indefinitely

**Fix:**
```kotlin
// Add cleanup logic
// Option 1: Auto-retry on app restart
// Option 2: Age-based cleanup (delete errors older than 7 days)
// Option 3: User action required (show retry button)

suspend fun cleanupOldErrors(olderThanDays: Int = 7) {
    val cutoff = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000)
    val oldErrors = starDao.getErrorTasksBefore(cutoff)
    oldErrors.forEach { starDao.deleteStarById(it.id) }
}
```

---

### 🟡 Issue #8: Migration Risk for Existing Data (MEDIUM)

**Problem:** Migration 2→3 could trigger mass re-sync.

**Migration:**
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE stars ADD COLUMN syncStatus TEXT DEFAULT 'synced'")
        // ... more fields
    }
}
```

**Problem:**
Existing tasks get `syncStatus = 'synced'` but may not actually be synced.

**Impact:**
- App update triggers unexpected sync behavior
- Old tasks may not push to server
- Or may all push at once (performance issue)

**Fix:**
```kotlin
// Better migration - mark existing as needing sync
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add columns
        database.execSQL("ALTER TABLE stars ADD COLUMN syncStatus TEXT DEFAULT 'pending'")
        // ...
        
        // Mark all existing as needing sync
        database.execSQL("UPDATE stars SET syncStatus = 'pending' WHERE serverId IS NULL")
    }
}
```

---

### 🟡 Issue #9: Race Conditions in Sync Trigger (MEDIUM)

**Problem:** Rapid changes can trigger multiple syncs.

**Current Code:**
```kotlin
// SyncManager.kt lines 89-96
scope.launch {
    syncTriggerChannel.receiveAsFlow()
        .collect {
            delay(DEBOUNCE_MS)  // 1 second
            performSync()
        }
}
```

**Problem:**
1. Change 1 at T=0 → Sync scheduled for T=1s
2. Change 2 at T=0.5s → Another sync scheduled for T=1.5s
3. First sync runs at T=1s, processes both changes
4. Second sync runs at T=1.5s, nothing to do

This is wasteful but not harmful.

**Bigger Problem:**
If performSync() takes longer than DEBOUNCE_MS, concurrent syncs could run.

**Fix:**
```kotlin
// Already has syncMutex, but check timing
private suspend fun performSync() {
    if (!isOnline()) {
        _syncState.value = SyncState.Offline
        return
    }
    
    syncMutex.withLock {
        // Check if another sync completed while we were waiting
        val lastSync = getLastSyncTimestamp()
        val pending = syncQueueDao.getAllPending()
        
        // Skip if already synced recently
        if (pending.isEmpty() && lastSync != null && 
            (System.currentTimeMillis() - lastSync) < 5000) {
            Log.d(TAG, "Skipping sync - already synced recently")
            return
        }
        
        // ... rest of sync
    }
}
```

---

### 🟡 Issue #10: No Sync Progress Indication (MEDIUM)

**Problem:** Users don't know sync status.

**Current State:**
- `SyncState` is exposed but not used in UI
- No visual indicator of pending changes
- No way to force sync or see last sync time

**Fix:**
```kotlin
// In MainActivity or Settings
@Composable
fun SyncStatusIndicator() {
    val syncState by syncManager.syncState.collectAsState()
    val pendingCount by syncManager.pendingCount.collectAsState()
    
    when (syncState) {
        is SyncState.Syncing -> CircularProgressIndicator()
        is SyncState.Error -> Icon(Icons.Default.Warning, "Sync error")
        is SyncState.Offline -> Icon(Icons.Default.CloudOff, "Offline")
        is SyncState.Synced -> {
            if (pendingCount > 0) {
                Badge { Text(pendingCount.toString()) }
            }
        }
    }
}
```

---

### 🟢 Issue #11: Position Preservation Logic Could Fail (LOW)

**Problem:** Position preservation in mergeServerTask() has gaps.

**Current Code:**
```kotlin
// Lines 341-358
if (localTask.syncStatus == "synced") {
    starDao.insertStar(serverTask.toStarEntity().copy(
        x = localTask.x,  // Preserve position
        y = localTask.y
    ))
} else {
    val hasPendingChange = syncQueueDao.getLatestForTask(serverTask.id) != null
    if (!hasPendingChange) {
        starDao.insertStar(serverTask.toStarEntity().copy(
            x = localTask.x,
            y = localTask.y
        ))
    }
    // If has pending change, don't overwrite!
}
```

**Problem:**
If server task has position updates (e.g., from another device), they're lost.

**Better Logic:**
```kotlin
// Merge positions intelligently
val mergedX = if (localTask.syncStatus == "pending" && 
                  localTask.x != serverTask.x) {
    localTask.x  // User moved locally, keep local position
} else {
    serverTask.x?.toFloat() ?: localTask.x  // Use server position
}
```

---

### 🟢 Issue #12: Wallpaper Trigger Race Condition (LOW)

**Problem:** Multiple wallpaper updates can fire simultaneously.

**Current Code:**
```kotlin
// TaskRepository.kt line 35-48
private fun triggerImmediateWallpaperUpdate() {
    try {
        // 1. Try instant update via Foreground Service
        wallpaperUpdater(context)
        
        // 2. WorkManager disabled (commented out)
        // val updateRequest = OneTimeWorkRequestBuilder<WallpaperUpdateWorker>().build()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to trigger wallpaper update")
    }
}
```

**Problem:**
- Called after every task change
- No throttling
- Could spam wallpaper updates

**Fix:**
```kotlin
// Add throttling
private var lastWallpaperUpdate = 0L
private val WALLPAPER_THROTTLE_MS = 2000  // Max 1 update per 2 seconds

private fun triggerImmediateWallpaperUpdate() {
    val now = System.currentTimeMillis()
    if (now - lastWallpaperUpdate < WALLPAPER_THROTTLE_MS) {
        return  // Too soon, skip
    }
    lastWallpaperUpdate = now
    
    // ... rest of code
}
```

---

## Priority Matrix

| Issue | Severity | Effort | Priority | Recommendation |
|-------|----------|--------|----------|----------------|
| #1 Inconsistent Sync | Critical | 3 days | P0 | Fix before shipping |
| #2 ID Management | Critical | 5 days | P0 | Fix before shipping |
| #3 No Transactions | Critical | 2 days | P0 | Fix before shipping |
| #4 Clock Skew | Critical | 3 days | P0 | Use server timestamps |
| #5 Queue Data Loss | High | 2 days | P1 | Fix soon |
| #6 No Conflict UI | High | 4 days | P1 | Add conflict resolution UI |
| #7 No Cleanup | High | 1 day | P1 | Add cleanup worker |
| #8 Migration Risk | Medium | 1 day | P2 | Update migration |
| #9 Race Conditions | Medium | 2 days | P2 | Add guards |
| #10 No Progress UI | Medium | 2 days | P2 | Add sync indicator |
| #11 Position Logic | Low | 1 day | P3 | Improve merging |
| #12 Wallpaper Race | Low | 1 day | P3 | Add throttling |

---

## Recommended Action Plan

### Phase 1: Critical Fixes (2 weeks)
1. **Fix Issue #2 (ID Management)** - Foundation for everything
2. **Fix Issue #3 (Transactions)** - Data integrity
3. **Fix Issue #1 (Consistent Sync)** - Unified sync path
4. **Fix Issue #4 (Clock Skew)** - Reliable conflict resolution

### Phase 2: User Experience (1 week)
1. **Fix Issue #6 (Conflict UI)** - User trust
2. **Fix Issue #10 (Progress UI)** - Visibility
3. **Fix Issue #7 (Cleanup)** - Maintenance

### Phase 3: Polish (3 days)
1. Fix remaining issues (#5, #8, #9, #11, #12)

---

## Conclusion

The local-first architecture is **functionally correct** but has **12 issues** that will cause problems at scale. The most critical are:

1. **ID management chaos** - Will break relationships
2. **No transactions** - Risk of data loss
3. **Inconsistent sync** - Unpredictable behavior

**Recommendation:** Fix critical issues before production release.

---

*Generated: 2026-02-01*
*Reviewer: Claude*
