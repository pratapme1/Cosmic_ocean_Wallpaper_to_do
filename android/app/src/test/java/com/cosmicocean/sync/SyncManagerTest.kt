package com.cosmicocean.sync

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit tests for local-first sync logic
 * Tests the sync engine behavior without network dependencies
 */
class SyncManagerTest {

    // In-memory test database
    private lateinit var testDb: TestDatabase

    @Before
    fun setup() {
        testDb = TestDatabase()
    }

    // =========================================================================
    // Test Suite 1: Basic Sync Operations
    // =========================================================================

    @Test
    fun `sync with no pending changes returns empty results`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val result = testDb.sync(userId, 0, emptyList())

        assertEquals(0, result.applied)
        assertEquals(0, result.rejected)
        assertEquals(0, result.tasks.size)
        assertTrue(result.syncedAt > 0)
    }

    @Test
    fun `sync returns tasks created after lastSyncAt`() {
        val userId = "user_1"
        testDb.createUser(userId)

        // Create task at time 1000
        testDb.createTask(userId, mapOf("title" to "Old Task"), 1000)
        // Create task at time 2000
        testDb.createTask(userId, mapOf("title" to "New Task"), 2000)

        // Sync from time 1500 - should only get the new task
        val result = testDb.sync(userId, 1500, emptyList())

        assertEquals(1, result.tasks.size)
        assertEquals("New Task", result.tasks[0].title)
    }

    // =========================================================================
    // Test Suite 2: Create Operations
    // =========================================================================

    @Test
    fun `create new task from pending changes`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val changes = listOf(
            SyncChange(
                type = "create",
                clientId = "client_task_1",
                timestamp = System.currentTimeMillis(),
                data = mapOf(
                    "title" to "Offline Task",
                    "priority" to 2,
                    "estimate_minutes" to 30
                )
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)
        assertEquals(0, result.rejected)

        val tasks = testDb.getTasks(userId)
        assertEquals(1, tasks.size)
        assertEquals("Offline Task", tasks[0].title)
        assertEquals(2, tasks[0].priority)
    }

    @Test
    fun `create with duplicate title returns conflict`() {
        val userId = "user_1"
        testDb.createUser(userId)

        // Create existing task
        testDb.createTask(userId, mapOf("title" to "Existing Task"), System.currentTimeMillis() - 1000)

        // Try to create duplicate
        val changes = listOf(
            SyncChange(
                type = "create",
                clientId = "client_task_2",
                timestamp = System.currentTimeMillis(),
                data = mapOf("title" to "Existing Task")
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(0, result.applied)
        assertEquals(1, result.rejected)
        assertEquals("already_exists", result.conflicts[0].reason)
    }

    @Test
    fun `create multiple tasks in one sync`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val baseTime = System.currentTimeMillis()
        val changes = listOf(
            SyncChange("create", "task_1", baseTime, mapOf("title" to "Task 1", "priority" to 0)),
            SyncChange("create", "task_2", baseTime + 1, mapOf("title" to "Task 2", "priority" to 1)),
            SyncChange("create", "task_3", baseTime + 2, mapOf("title" to "Task 3", "priority" to 2))
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(3, result.applied)
        assertEquals(3, testDb.getTasks(userId).size)
    }

    // =========================================================================
    // Test Suite 3: Update Operations
    // =========================================================================

    @Test
    fun `update existing task from pending changes`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val task = testDb.createTask(userId, mapOf("title" to "Original Title"), System.currentTimeMillis() - 1000)

        val changes = listOf(
            SyncChange(
                type = "update",
                clientId = "update_1",
                timestamp = System.currentTimeMillis(),
                data = mapOf("id" to task.id, "title" to "Updated Title", "priority" to 3)
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)
        assertEquals(0, result.rejected)

        val updated = testDb.getTask(task.id, userId)
        assertEquals("Updated Title", updated?.title)
        assertEquals(3, updated?.priority)
    }

    @Test
    fun `update non-existent task returns task_not_found conflict`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val changes = listOf(
            SyncChange(
                type = "update",
                clientId = "update_1",
                timestamp = System.currentTimeMillis(),
                data = mapOf("id" to "non_existent_task", "title" to "Updated Title")
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(0, result.applied)
        assertEquals(1, result.rejected)
        assertEquals("task_not_found", result.conflicts[0].reason)
    }

    // =========================================================================
    // Test Suite 4: Delete Operations
    // =========================================================================

    @Test
    fun `delete existing task from pending changes`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val task = testDb.createTask(userId, mapOf("title" to "Task to Delete"), System.currentTimeMillis() - 1000)

        val changes = listOf(
            SyncChange(
                type = "delete",
                clientId = "delete_1",
                timestamp = System.currentTimeMillis(),
                data = mapOf("id" to task.id)
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)
        assertNull(testDb.getTask(task.id, userId))
    }

    @Test
    fun `delete already deleted task succeeds (idempotent)`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val changes = listOf(
            SyncChange(
                type = "delete",
                clientId = "delete_1",
                timestamp = System.currentTimeMillis(),
                data = mapOf("id" to "never_existed")
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)
        assertEquals(0, result.rejected)
    }

    // =========================================================================
    // Test Suite 5: Last-Write-Wins Conflict Resolution
    // =========================================================================

    @Test
    fun `client update with older timestamp gets rejected (stale_data)`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val serverTime = System.currentTimeMillis()
        val clientTime = serverTime - 10000 // 10 seconds older

        // Create and update task on "server"
        val task = testDb.createTask(userId, mapOf("title" to "Original"), serverTime - 20000)
        testDb.updateTask(task.id, userId, mapOf("title" to "Server Update"), serverTime)

        // Try to sync with older client change
        val changes = listOf(
            SyncChange(
                type = "update",
                clientId = "update_1",
                timestamp = clientTime,
                data = mapOf("id" to task.id, "title" to "Client Update")
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(0, result.applied)
        assertEquals(1, result.rejected)
        assertEquals("stale_data", result.conflicts[0].reason)

        // Verify server version wins
        val current = testDb.getTask(task.id, userId)
        assertEquals("Server Update", current?.title)
    }

    @Test
    fun `client update with newer timestamp gets applied`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val serverTime = System.currentTimeMillis() - 10000
        val clientTime = System.currentTimeMillis() // Newer

        val task = testDb.createTask(userId, mapOf("title" to "Original"), serverTime)

        val changes = listOf(
            SyncChange(
                type = "update",
                clientId = "update_1",
                timestamp = clientTime,
                data = mapOf("id" to task.id, "title" to "Client Wins")
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)
        assertEquals(0, result.rejected)

        val current = testDb.getTask(task.id, userId)
        assertEquals("Client Wins", current?.title)
    }

    @Test
    fun `client delete with older timestamp gets rejected`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val serverTime = System.currentTimeMillis()
        val clientTime = serverTime - 10000 // Older

        val task = testDb.createTask(userId, mapOf("title" to "Task"), serverTime)

        val changes = listOf(
            SyncChange(
                type = "delete",
                clientId = "delete_1",
                timestamp = clientTime,
                data = mapOf("id" to task.id)
            )
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(0, result.applied)
        assertEquals(1, result.rejected)
        assertEquals("stale_data", result.conflicts[0].reason)

        // Task should still exist
        assertNotNull(testDb.getTask(task.id, userId))
    }

    // =========================================================================
    // Test Suite 6: Mixed Operations
    // =========================================================================

    @Test
    fun `mixed operations create update delete in one sync`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val existingTask = testDb.createTask(userId, mapOf("title" to "Existing Task"), System.currentTimeMillis() - 10000)

        val now = System.currentTimeMillis()
        val changes = listOf(
            SyncChange("create", "new_task", now, mapOf("title" to "New Task")),
            SyncChange("update", "update_1", now + 1, mapOf("id" to existingTask.id, "title" to "Updated Task")),
            SyncChange("delete", "delete_1", now + 2, mapOf("id" to existingTask.id))
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(3, result.applied)

        // Only the new task should remain
        val tasks = testDb.getTasks(userId)
        assertEquals(1, tasks.size)
        assertEquals("New Task", tasks[0].title)
    }

    @Test
    fun `conflict and success in same sync batch`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val conflictTask = testDb.createTask(userId, mapOf("title" to "Conflict Task"), System.currentTimeMillis())

        val oldTime = System.currentTimeMillis() - 20000
        val newTime = System.currentTimeMillis() + 1000

        val changes = listOf(
            SyncChange("update", "conflict", oldTime, mapOf("id" to conflictTask.id, "title" to "Old Update")),
            SyncChange("create", "new_task", newTime, mapOf("title" to "New Task"))
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)
        assertEquals(1, result.rejected)
        assertEquals(1, result.conflicts.size)
    }

    // =========================================================================
    // Test Suite 7: Edge Cases
    // =========================================================================

    @Test
    fun `invalid action type is skipped`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val changes = listOf(
            SyncChange("invalid_action", "test", System.currentTimeMillis(), emptyMap())
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.skipped)
        assertEquals(0, result.applied)
        assertEquals(0, result.rejected)
    }

    @Test
    fun `empty data object creates task with defaults`() {
        val userId = "user_1"
        testDb.createUser(userId)

        val changes = listOf(
            SyncChange("create", "empty_task", System.currentTimeMillis(), emptyMap())
        )

        val result = testDb.sync(userId, 0, changes)

        assertEquals(1, result.applied)

        val task = testDb.getTasks(userId)[0]
        assertEquals("New Task", task.title) // Default title
        assertEquals(0, task.priority) // Default priority
    }

    // =========================================================================
    // Test Suite 8: User Isolation
    // =========================================================================

    @Test
    fun `users cannot see each other's tasks`() {
        val user1 = "user_1"
        val user2 = "user_2"
        testDb.createUser(user1)
        testDb.createUser(user2)

        // User 1 creates task
        testDb.createTask(user1, mapOf("title" to "User 1 Task"), System.currentTimeMillis())

        // User 2 syncs - should not see user 1's task
        val result = testDb.sync(user2, 0, emptyList())
        assertEquals(0, result.tasks.size)
    }

    @Test
    fun `user cannot update another user's task`() {
        val user1 = "user_1"
        val user2 = "user_2"
        testDb.createUser(user1)
        testDb.createUser(user2)

        val task = testDb.createTask(user1, mapOf("title" to "User 1 Task"), System.currentTimeMillis() - 1000)

        // User 2 tries to update
        val changes = listOf(
            SyncChange(
                type = "update",
                clientId = "attack",
                timestamp = System.currentTimeMillis(),
                data = mapOf("id" to task.id, "title" to "Hacked")
            )
        )

        val result = testDb.sync(user2, 0, changes)

        assertEquals(1, result.rejected)
        assertEquals("task_not_found", result.conflicts[0].reason)

        // Original task unchanged
        val original = testDb.getTask(task.id, user1)
        assertEquals("User 1 Task", original?.title)
    }
}

// =========================================================================
// Test Helper Classes
// =========================================================================

data class SyncChange(
    val type: String,
    val clientId: String,
    val timestamp: Long,
    val data: Map<String, Any?>
)

data class SyncConflict(
    val clientId: String,
    val reason: String,
    val serverData: TestTask? = null
)

data class SyncResult(
    val syncedAt: Long,
    val tasks: List<TestTask>,
    val applied: Int,
    val rejected: Int,
    val skipped: Int,
    val conflicts: List<SyncConflict>
)

data class TestTask(
    val id: String,
    val userId: String,
    val title: String,
    val priority: Int,
    val estimateMinutes: Int?,
    val dueDate: String?,
    val dueTime: String?,
    val completed: Boolean,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * In-memory test database that mirrors real sync behavior
 */
class TestDatabase {
    private val tasks = ConcurrentHashMap<String, TestTask>()
    private val users = ConcurrentHashMap<String, String>()
    private var taskIdCounter = 1

    fun createUser(userId: String) {
        users[userId] = userId
    }

    fun getTasks(userId: String): List<TestTask> {
        return tasks.values.filter { it.userId == userId && !it.deleted }
    }

    fun getTask(taskId: String, userId: String): TestTask? {
        val task = tasks[taskId]
        return if (task != null && task.userId == userId && !task.deleted) task else null
    }

    fun createTask(userId: String, data: Map<String, Any?>, timestamp: Long): TestTask {
        val id = data["id"]?.toString() ?: "task_${taskIdCounter++}"
        val task = TestTask(
            id = id,
            userId = userId,
            title = data["title"]?.toString() ?: "New Task",
            priority = (data["priority"] as? Number)?.toInt() ?: 0,
            estimateMinutes = (data["estimate_minutes"] as? Number)?.toInt(),
            dueDate = data["due_date"]?.toString(),
            dueTime = data["due_time"]?.toString(),
            completed = false,
            deleted = false,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        tasks[id] = task
        return task
    }

    fun updateTask(taskId: String, userId: String, data: Map<String, Any?>, timestamp: Long): TestTask? {
        val task = getTask(taskId, userId) ?: return null
        val updated = task.copy(
            title = data["title"]?.toString() ?: task.title,
            priority = (data["priority"] as? Number)?.toInt() ?: task.priority,
            estimateMinutes = (data["estimate_minutes"] as? Number)?.toInt() ?: task.estimateMinutes,
            dueDate = data["due_date"]?.toString() ?: task.dueDate,
            dueTime = data["due_time"]?.toString() ?: task.dueTime,
            updatedAt = timestamp
        )
        tasks[taskId] = updated
        return updated
    }

    fun deleteTask(taskId: String, userId: String): Boolean {
        val task = getTask(taskId, userId) ?: return false
        tasks[taskId] = task.copy(deleted = true)
        return true
    }

    fun getTasksSince(userId: String, since: Long): List<TestTask> {
        return tasks.values
            .filter { it.userId == userId && !it.deleted }
            .filter { it.updatedAt > since || it.createdAt > since }
    }

    fun sync(userId: String, lastSyncAt: Long, changes: List<SyncChange>): SyncResult {
        var applied = 0
        var rejected = 0
        var skipped = 0
        val conflicts = mutableListOf<SyncConflict>()

        for (change in changes) {
            when (change.type) {
                "create" -> {
                    val result = applyCreate(userId, change)
                    if (result.first) applied++ else {
                        rejected++
                        result.second?.let { conflicts.add(it) }
                    }
                }
                "update" -> {
                    val result = applyUpdate(userId, change)
                    if (result.first) applied++ else {
                        rejected++
                        result.second?.let { conflicts.add(it) }
                    }
                }
                "delete" -> {
                    val result = applyDelete(userId, change)
                    if (result.first) applied++ else {
                        rejected++
                        result.second?.let { conflicts.add(it) }
                    }
                }
                else -> skipped++
            }
        }

        return SyncResult(
            syncedAt = System.currentTimeMillis(),
            tasks = getTasksSince(userId, lastSyncAt),
            applied = applied,
            rejected = rejected,
            skipped = skipped,
            conflicts = conflicts
        )
    }

    private fun applyCreate(userId: String, change: SyncChange): Pair<Boolean, SyncConflict?> {
        val taskId = change.data["id"]?.toString() ?: change.clientId

        // Check if task already exists
        if (getTask(taskId, userId) != null) {
            return false to SyncConflict(change.clientId, "already_exists", getTask(taskId, userId))
        }

        // Check for duplicate title
        val title = change.data["title"]?.toString() ?: "New Task"
        if (getTasks(userId).any { it.title == title }) {
            return false to SyncConflict(change.clientId, "already_exists")
        }

        createTask(userId, change.data + ("id" to taskId), change.timestamp)
        return true to null
    }

    private fun applyUpdate(userId: String, change: SyncChange): Pair<Boolean, SyncConflict?> {
        val taskId = change.data["id"]?.toString() ?: change.clientId
        val serverTask = getTask(taskId, userId)
            ?: return false to SyncConflict(change.clientId, "task_not_found")

        if (change.timestamp < serverTask.updatedAt) {
            return false to SyncConflict(change.clientId, "stale_data", serverTask)
        }

        updateTask(taskId, userId, change.data, change.timestamp)
        return true to null
    }

    private fun applyDelete(userId: String, change: SyncChange): Pair<Boolean, SyncConflict?> {
        val taskId = change.data["id"]?.toString() ?: change.clientId
        val serverTask = getTask(taskId, userId)
            ?: return true to null // Already deleted

        if (change.timestamp < serverTask.updatedAt) {
            return false to SyncConflict(change.clientId, "stale_data", serverTask)
        }

        deleteTask(taskId, userId)
        return true to null
    }
}
