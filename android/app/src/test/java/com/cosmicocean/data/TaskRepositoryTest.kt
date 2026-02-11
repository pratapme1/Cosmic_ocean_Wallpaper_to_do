package com.cosmicocean.data

import android.content.Context
import com.cosmicocean.model.Star
import com.cosmicocean.network.ApiService
import com.cosmicocean.sync.SyncManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TaskRepositoryTest {

    private lateinit var starDao: StarDao
    private lateinit var apiService: ApiService
    private lateinit var context: Context
    private lateinit var syncManager: SyncManager
    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        starDao = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        context = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)

        repository = TaskRepository(
            starDao = starDao,
            apiService = apiService,
            context = context,
            syncManager = syncManager
        )
    }

    @Test
    fun `addStar queues create`() = runTest {
        val star = Star(x = 100f, y = 100f, title = "Test Star", urgency = 1, dueDate = null)

        coEvery { starDao.insertStarWithTransaction(any()) } returns Unit
        coEvery { syncManager.queueCreate(any(), any(), any()) } returns Unit

        val localId = repository.addStar(star)

        coVerify { starDao.insertStarWithTransaction(match { it.localId == localId && it.syncStatus == "pending" }) }
        coVerify { syncManager.queueCreate(localId, any(), any()) }
    }

    @Test
    fun `updateStar queues update`() = runTest {
        val star = Star(x = 100f, y = 100f, title = "Test Star", urgency = 1, dueDate = null, id = "local-1")

        coEvery { starDao.insertStarWithTransaction(any()) } returns Unit
        coEvery { syncManager.queueUpdate(any(), any()) } returns Unit

        repository.updateStar(star)

        coVerify { starDao.insertStarWithTransaction(match { it.localId == "local-1" && it.syncStatus == "pending" }) }
        coVerify { syncManager.queueUpdate("local-1", any()) }
    }

    @Test
    fun `deleteStar queues delete`() = runTest {
        val star = Star(x = 100f, y = 100f, title = "Test Star", urgency = 1, dueDate = null, id = "local-1")

        coEvery { starDao.softDelete(any(), any()) } returns Unit
        coEvery { syncManager.queueDelete(any()) } returns Unit

        repository.deleteStar(star)

        coVerify { starDao.softDelete("local-1", any()) }
        coVerify { syncManager.queueDelete("local-1") }
    }

    @Test
    fun `clearAllTasks queues clear`() = runTest {
        coEvery { starDao.deleteAllStars() } returns Unit
        coEvery { syncManager.queueClearAll() } returns Unit

        repository.clearAllTasks()

        coVerify { starDao.deleteAllStars() }
        coVerify { syncManager.queueClearAll() }
    }

    @Test
    fun `completing parent star does NOT unlink children`() = runTest {
        val parent = Star(x = 100f, y = 100f, title = "Parent", urgency = 1, dueDate = null, id = "parent-1")
        
        val parentEntity = parent.toEntity().copy(localId = "parent-1")
        
        // Mark as completed
        parent.isCompleted = true
        parent.completedAt = 1000L
        
        coEvery { starDao.getByLocalId("parent-1") } returns parentEntity
        coEvery { starDao.insertStarWithTransaction(any()) } returns Unit
        coEvery { starDao.getChildrenForParent("parent-1") } returns emptyList() 

        repository.updateStar(parent)

        coVerify { starDao.insertStarWithTransaction(match { it.localId == "parent-1" && it.isCompleted }) }
        coVerify(exactly = 0) { starDao.getChildrenForParent("parent-1") }
    }

    @Test
    fun `archiving parent star DOES unlink children`() = runTest {
        val parent = Star(x = 100f, y = 100f, title = "Parent", urgency = 1, dueDate = null, id = "parent-1")
        val child = Star(x = 110f, y = 110f, title = "Child", urgency = 1, dueDate = null, id = "child-1", isSubtask = true, parentId = "parent-1")
        val childEntity = child.toEntity().copy(localId = "child-1")

        val parentEntity = parent.toEntity().copy(localId = "parent-1")
        
        // Mark as archived
        parent.isArchived = true
        parent.archivedAt = 1000L
        
        coEvery { starDao.getByLocalId("parent-1") } returns parentEntity
        coEvery { starDao.insertStarWithTransaction(any()) } returns Unit
        coEvery { starDao.getChildrenForParent("parent-1") } returns listOf(childEntity)
        coEvery { syncManager.queueUpdate(any(), any()) } returns Unit

        repository.updateStar(parent)

        coVerify { starDao.insertStarWithTransaction(match { it.localId == "parent-1" && it.isArchived }) }
        coVerify { starDao.getChildrenForParent("parent-1") }
        coVerify { starDao.insertStarWithTransaction(match { it.localId == "child-1" && !it.isSubtask && it.parentId == null }) }
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