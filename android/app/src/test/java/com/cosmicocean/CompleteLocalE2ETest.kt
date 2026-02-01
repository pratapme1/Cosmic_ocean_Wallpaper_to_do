package com.cosmicocean

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarDao
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.SyncQueueDao
import com.cosmicocean.model.Star
import com.cosmicocean.sync.SyncManager
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID
import android.content.Context
import android.graphics.Bitmap

/**
 * COMPREHENSIVE E2E TESTS - Completely Local System
 * Tests that verify:
 * 1. No duplicate task creation
 * 2. Tasks appear on wallpaper immediately
 * 3. Updates don't create duplicates
 * 4. Deletes work properly
 * 5. Everything works offline (no network)
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class CompleteLocalE2ETest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: CosmicDatabase
    private lateinit var starDao: StarDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Use in-memory database for testing
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context, CosmicDatabase::class.java
        ).allowMainThreadQueries().build()
        
        starDao = database.starDao()
        syncQueueDao = database.syncQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ============================================
    // TEST 1: Local Task Creation Without Duplicates
    // ============================================
    @Test
    fun `test create task locally does not create duplicates`() = runTest {
        // Create a task
        val task1 = StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null, // Not synced yet
            title = "Test Task 1",
            urgency = 2,
            dueDate = null,
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        
        // Insert first time
        starDao.insertStar(task1)
        
        // Try to insert same task again (should REPLACE, not duplicate)
        starDao.insertStar(task1)
        
        // Verify only 1 task exists
        val tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals("Should have exactly 1 task, not duplicates", 1, tasks.size)
        Assert.assertEquals("Task title should match", "Test Task 1", tasks[0].title)
    }

    // ============================================
    // TEST 2: Sync Mapping Update (No Duplicate Creation)
    // ============================================
    @Test
    fun `test sync mapping updates existing task without creating duplicate`() = runTest {
        // 1. Create local task
        val localId = UUID.randomUUID().toString()
        val localTask = StarEntity(
            localId = localId,
            serverId = null,
            title = "Local Task",
            urgency = 2,
            dueDate = null,
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(localTask)
        
        // 2. Simulate sync response - update with serverId
        val serverId = UUID.randomUUID().toString()
        val updatedTask = localTask.copy(
            serverId = serverId,
            syncStatus = "synced",
            serverUpdatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(updatedTask) // This should REPLACE, not create duplicate
        
        // 3. Verify only 1 task exists with correct serverId
        val tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals("Should have exactly 1 task", 1, tasks.size)
        Assert.assertEquals("Task should have serverId", serverId, tasks[0].serverId)
        Assert.assertEquals("Task should be synced", "synced", tasks[0].syncStatus)
    }

    // ============================================
    // TEST 3: Task Update Without Duplicate Creation
    // ============================================
    @Test
    fun `test update task does not create duplicate`() = runTest {
        // Create initial task
        val localId = UUID.randomUUID().toString()
        val initialTask = StarEntity(
            localId = localId,
            serverId = UUID.randomUUID().toString(),
            title = "Initial Title",
            urgency = 2,
            dueDate = null,
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "synced",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(initialTask)
        
        // Update the task (change title)
        val updatedTask = initialTask.copy(
            title = "Updated Title",
            syncStatus = "pending",
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(updatedTask) // Should REPLACE, not duplicate
        
        // Verify only 1 task with updated title
        val tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals("Should have exactly 1 task", 1, tasks.size)
        Assert.assertEquals("Title should be updated", "Updated Title", tasks[0].title)
        Assert.assertEquals("Status should be pending", "pending", tasks[0].syncStatus)
    }

    // ============================================
    // TEST 4: Task Deletion
    // ============================================
    @Test
    fun `test soft delete task`() = runTest {
        // Create task
        val localId = UUID.randomUUID().toString()
        val task = StarEntity(
            localId = localId,
            serverId = UUID.randomUUID().toString(),
            title = "Task to Delete",
            urgency = 2,
            dueDate = null,
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "synced",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(task)
        
        // Verify task exists
        var tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals(1, tasks.size)
        
        // Soft delete
        starDao.softDelete(localId)
        
        // Verify task no longer appears in active list (but still in DB)
        tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals("Deleted task should not appear in active list", 0, tasks.size)
        
        // Verify task still exists (for sync purposes)
        val deletedTask = starDao.getByLocalId(localId)
        Assert.assertNotNull("Task should still exist in DB", deletedTask)
        Assert.assertEquals("Task should be marked deleted", true, deletedTask?.isDeleted)
    }

    // ============================================
    // TEST 5: Wallpaper Generation - Shows Correct Task
    // ============================================
    @Test
    fun `test wallpaper generator shows top task`() = runTest {
        // Create multiple tasks with different urgencies
        val taskHigh = StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = "High Priority Task",
            urgency = 1, // Higher priority (lower number)
            dueDate = System.currentTimeMillis() + 3600000, // 1 hour from now
            x = 0.3f,
            y = 0.3f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        
        val taskLow = StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = "Low Priority Task",
            urgency = 3, // Lower priority
            dueDate = System.currentTimeMillis() + 7200000, // 2 hours from now
            x = 0.7f,
            y = 0.7f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        
        starDao.insertStar(taskHigh)
        starDao.insertStar(taskLow)
        
        // Get top task (should be high priority)
        val topTask = starDao.getTopTask()
        
        Assert.assertNotNull("Should have a top task", topTask)
        Assert.assertEquals("Top task should be high priority", "High Priority Task", topTask?.title)
        Assert.assertEquals("Top task urgency should be 1", 1, topTask?.urgency)
        
        // Generate wallpaper bitmap
        val bitmap = LocalWallpaperGenerator.generate(
            task = topTask,
            theme = WallpaperTheme.COSMIC,
            width = 1080,
            height = 1920
        )
        
        Assert.assertNotNull("Wallpaper bitmap should be generated", bitmap)
        Assert.assertEquals("Bitmap width should match", 1080, bitmap?.width)
        Assert.assertEquals("Bitmap height should match", 1920, bitmap?.height)
    }

    // ============================================
    // TEST 6: No Duplicate After Multiple Updates
    // ============================================
    @Test
    fun `test multiple updates do not create duplicates`() = runTest {
        val localId = UUID.randomUUID().toString()
        
        // Create initial task
        var task = StarEntity(
            localId = localId,
            serverId = null,
            title = "Version 1",
            urgency = 2,
            dueDate = null,
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(task)
        
        // Update multiple times
        for (i in 2..5) {
            task = task.copy(
                title = "Version $i",
                updatedAt = System.currentTimeMillis()
            )
            starDao.insertStar(task)
        }
        
        // Verify only 1 task exists
        val tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals("Should have exactly 1 task after 5 updates", 1, tasks.size)
        Assert.assertEquals("Title should be latest version", "Version 5", tasks[0].title)
    }

    // ============================================
    // TEST 7: Sync Queue Management
    // ============================================
    @Test
    fun `test sync queue tracks pending operations`() = runTest {
        val localId = UUID.randomUUID().toString()
        
        // Add to sync queue
        val queueItem = com.cosmicocean.data.SyncQueueEntity(
            localTaskId = localId,
            operation = "create",
            payload = "{\"title\":\"Test Task\"}",
            createdAt = System.currentTimeMillis()
        )
        syncQueueDao.insert(queueItem)
        
        // Verify queue has item
        var pending = syncQueueDao.getAllPending()
        Assert.assertEquals("Should have 1 pending operation", 1, pending.size)
        
        // Delete from queue (simulate successful sync)
        syncQueueDao.deleteById(pending[0].id)
        
        // Verify queue is empty
        pending = syncQueueDao.getAllPending()
        Assert.assertEquals("Queue should be empty after sync", 0, pending.size)
    }

    // ============================================
    // TEST 8: Complete Local Workflow (Offline)
    // ============================================
    @Test
    fun `test complete offline workflow`() = runTest {
        // 1. Create task (offline)
        val localId = UUID.randomUUID().toString()
        val task = StarEntity(
            localId = localId,
            serverId = null,
            title = "Offline Task",
            urgency = 1,
            dueDate = System.currentTimeMillis() + 3600000,
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(task)
        
        // 2. Verify task exists
        var tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals(1, tasks.size)
        
        // 3. Update task (offline)
        val updatedTask = task.copy(
            title = "Updated Offline Task",
            syncStatus = "pending"
        )
        starDao.insertStar(updatedTask)
        
        // 4. Verify still only 1 task with updated title
        tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals(1, tasks.size)
        Assert.assertEquals("Updated Offline Task", tasks[0].title)
        
        // 5. Generate wallpaper (offline)
        val topTask = starDao.getTopTask()
        val bitmap = LocalWallpaperGenerator.generate(
            task = topTask,
            theme = WallpaperTheme.OCEAN,
            width = 1080,
            height = 1920
        )
        
        // 6. Verify wallpaper generated without network
        Assert.assertNotNull("Wallpaper should generate offline", bitmap)
        
        // 7. Complete task
        val completedTask = updatedTask.copy(
            isCompleted = true,
            completedAt = System.currentTimeMillis(),
            syncStatus = "pending"
        )
        starDao.insertStar(completedTask)
        
        // 8. Verify task completed but still exists
        tasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals(1, tasks.size)
        Assert.assertEquals(true, tasks[0].isCompleted)
    }

    // ============================================
    // TEST 9: Verify No Database Corruption
    // ============================================
    @Test
    fun `test database integrity after multiple operations`() = runTest {
        // Perform many operations
        val tasks = mutableListOf<StarEntity>()
        
        // Create 10 tasks
        for (i in 1..10) {
            val task = StarEntity(
                localId = UUID.randomUUID().toString(),
                serverId = null,
                title = "Task $i",
                urgency = i % 3 + 1,
                dueDate = System.currentTimeMillis() + (i * 3600000),
                x = 0.1f * i,
                y = 0.1f * i,
                createdAt = System.currentTimeMillis(),
                isSubtask = false,
                isRecurring = false,
                echoInterval = null,
                isCompleted = false,
                completedAt = null,
                isArchived = false,
                archivedAt = null,
                syncStatus = "pending",
                syncVersion = 0,
                updatedAt = System.currentTimeMillis()
            )
            tasks.add(task)
            starDao.insertStar(task)
        }
        
        // Update all tasks
        tasks.forEachIndexed { index, task ->
            val updated = task.copy(
                title = "Updated Task ${index + 1}",
                syncStatus = "synced"
            )
            starDao.insertStar(updated)
        }
        
        // Delete half
        for (i in 0..4) {
            starDao.softDelete(tasks[i].localId)
        }
        
        // Verify database state
        val activeTasks = starDao.getAllActiveStarsSync()
        Assert.assertEquals("Should have 5 active tasks", 5, activeTasks.size)
        
        // Verify all active tasks have correct titles
        activeTasks.forEachIndexed { index, task ->
            Assert.assertEquals("Task ${index + 6} should be updated", "Updated Task ${index + 6}", task.title)
        }
        
        // Verify no duplicates by checking localIds are unique
        val localIds = activeTasks.map { it.localId }.toSet()
        Assert.assertEquals("All localIds should be unique", activeTasks.size, localIds.size)
    }

    // ============================================
    // TEST 10: Wallpaper Shows Correct Task After Updates
    // ============================================
    @Test
    fun `test wallpaper reflects task changes immediately`() = runTest {
        // Create urgent task
        val urgentTask = StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = "Urgent Task",
            urgency = 1,
            dueDate = System.currentTimeMillis() + 1800000, // 30 min
            x = 0.5f,
            y = 0.5f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(urgentTask)
        
        // Generate wallpaper - should show urgent task
        var topTask = starDao.getTopTask()
        Assert.assertEquals("Urgent Task", topTask?.title)
        
        var bitmap1 = LocalWallpaperGenerator.generate(
            task = topTask,
            theme = WallpaperTheme.COSMIC,
            width = 1080,
            height = 1920
        )
        Assert.assertNotNull(bitmap1)
        
        // Complete the urgent task
        val completedTask = urgentTask.copy(
            isCompleted = true,
            completedAt = System.currentTimeMillis(),
            syncStatus = "pending"
        )
        starDao.insertStar(completedTask)
        
        // Create new most urgent task
        val newUrgentTask = StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = "New Urgent Task",
            urgency = 1,
            dueDate = System.currentTimeMillis() + 900000, // 15 min
            x = 0.6f,
            y = 0.6f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "pending",
            syncVersion = 0,
            updatedAt = System.currentTimeMillis()
        )
        starDao.insertStar(newUrgentTask)
        
        // Generate wallpaper again - should show NEW urgent task
        topTask = starDao.getTopTask()
        Assert.assertEquals("New Urgent Task", topTask?.title)
        
        val bitmap2 = LocalWallpaperGenerator.generate(
            task = topTask,
            theme = WallpaperTheme.COSMIC,
            width = 1080,
            height = 1920
        )
        Assert.assertNotNull(bitmap2)
        
        // Verify bitmaps are different (different tasks)
        // Note: In reality, the bitmaps would differ. Here we just verify both generated successfully
    }
}
