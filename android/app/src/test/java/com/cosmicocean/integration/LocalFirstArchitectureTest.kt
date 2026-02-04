package com.cosmicocean.integration

import android.content.Context
import com.cosmicocean.data.StarDao
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.Star
import com.cosmicocean.network.ApiService
import com.cosmicocean.sync.SyncManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TDD Tests for Local-First Architecture
 *
 * These tests verify that ALL operations go through the local-first pattern:
 * 1. Write to local DB first
 * 2. Queue to SyncManager
 * 3. Sync happens in background
 *
 * Tests written BEFORE implementation (TDD)
 */
class LocalFirstArchitectureTest {

    private lateinit var mockStarDao: StarDao
    private lateinit var mockApiService: ApiService
    private lateinit var mockContext: Context
    private lateinit var mockSyncManager: SyncManager

    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        mockStarDao = mockk(relaxed = true)
        mockApiService = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockSyncManager = mockk(relaxed = true)

        every { mockStarDao.getAllActiveStars() } returns flowOf(emptyList())

        repository = TaskRepository(
            starDao = mockStarDao,
            apiService = mockApiService,
            context = mockContext,
            syncManager = mockSyncManager,
            wallpaperUpdater = { } // No-op for tests
        )
    }

    // ==================== UPDATE STAR TESTS ====================

    @Test
    fun `updateStar should save to local DB first`() = runBlocking {
        val star = createTestStar("task1")
        star.title = "Updated Title"
        star.urgency = 3

        repository.updateStar(star)

        coVerify { mockStarDao.insertStarWithTransaction(any()) }
    }

    @Test
    fun `updateStar should queue to SyncManager`() = runBlocking {
        val star = createTestStar("task1")
        star.title = "Updated Title"

        repository.updateStar(star)

        coVerify { mockSyncManager.queueUpdate(star.id, any()) }
    }

    @Test
    fun `updateStar should NOT call API directly`() = runBlocking {
        val star = createTestStar("task1")

        repository.updateStar(star)

        verify { mockApiService wasNot Called }
    }

    // ==================== DELETE STAR TESTS ====================

    @Test
    fun `deleteStar should soft delete locally first`() = runBlocking {
        val star = createTestStar("task1")

        repository.deleteStar(star)

        coVerify { mockStarDao.softDelete(star.id, any()) }
    }

    @Test
    fun `deleteStar should queue to SyncManager`() = runBlocking {
        val star = createTestStar("task1")

        repository.deleteStar(star)

        coVerify { mockSyncManager.queueDelete(star.id) }
    }

    // ==================== CLEAR ALL TESTS ====================

    @Test
    fun `clearAllTasks should clear local DB first`() = runBlocking {
        repository.clearAllTasks()

        coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
            mockStarDao.deleteAllStars()
            mockSyncManager.queueClearAll()
        }
    }

    @Test
    fun `clearAllTasks should queue sync after local clear`() = runBlocking {
        repository.clearAllTasks()

        coVerify { mockSyncManager.queueClearAll() }
    }

    @Test
    fun `clearAllTasks should NOT call API directly`() = runBlocking {
        repository.clearAllTasks()

        verify { mockApiService wasNot Called }
    }

    // ==================== ADD STAR TESTS ====================

    @Test
    fun `addStar should generate localId immediately`() = runBlocking {
        val star = Star(100f, 200f, "Test Task", 2, null)

        val localId = repository.addStar(star)

        assertNotNull(localId)
        assertTrue(localId.isNotBlank())
        assertEquals(star.id, localId)
    }

    @Test
    fun `addStar should save to local DB with pending status`() = runBlocking {
        val star = Star(100f, 200f, "Test Task", 2, null)

        repository.addStar(star)

        val captor = slot<StarEntity>()
        coVerify { mockStarDao.insertStarWithTransaction(capture(captor)) }
        assertEquals("pending", captor.captured.syncStatus)
    }

    @Test
    fun `addStar should queue to SyncManager`() = runBlocking {
        val star = Star(100f, 200f, "Test Task", 2, null)

        val localId = repository.addStar(star)

        coVerify { mockSyncManager.queueCreate(localId, any(), any()) }
    }

    // ==================== HELPER METHODS ====================

    private fun createTestStar(id: String): Star {
        return Star(100f, 200f, "Test Task", 2, null).apply {
            this.id = id
        }
    }
}
