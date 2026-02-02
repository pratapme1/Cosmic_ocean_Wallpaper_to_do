package com.cosmicocean.integration

import android.content.Context
import android.content.SharedPreferences
import com.cosmicocean.data.StarDao
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.SyncQueueDao
import com.cosmicocean.data.SyncQueueEntity
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.Star
import com.cosmicocean.network.ApiService
import com.cosmicocean.sync.SyncManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
@RunWith(MockitoJUnitRunner.Silent::class)
class LocalFirstArchitectureTest {

    @Mock
    private lateinit var mockStarDao: StarDao

    @Mock
    private lateinit var mockSyncQueueDao: SyncQueueDao

    @Mock
    private lateinit var mockApiService: ApiService

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSyncManager: SyncManager

    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Setup default mock behaviors
        whenever(mockStarDao.getAllActiveStars()).thenReturn(flowOf(emptyList()))

        repository = TaskRepository(
            starDao = mockStarDao,
            apiService = mockApiService,
            context = mockContext,
            syncManager = mockSyncManager,
            wallpaperUpdater = { } // No-op for tests
        )
    }

    // ==================== SNOOZE TESTS ====================

    @Test
    fun `snoozeStar should save snooze to local DB first`() = runBlocking {
        // Given
        val star = createTestStar("task1")
        val snoozeDurationMinutes = 30
        val expectedSnoozeUntil = System.currentTimeMillis() + (snoozeDurationMinutes * 60 * 1000)

        // When
        repository.snoozeStar(star, snoozeDurationMinutes)

        // Then - verify local DB was updated
        verify(mockStarDao).updateSnooze(
            eq(star.id),
            eq(true),
            any() // snoozeUntil timestamp
        )
    }

    @Test
    fun `snoozeStar should queue to SyncManager after local save`() = runBlocking {
        // Given
        val star = createTestStar("task1")
        val snoozeDurationMinutes = 60

        // When
        repository.snoozeStar(star, snoozeDurationMinutes)

        // Then - verify queued to SyncManager
        verify(mockSyncManager).queueSnooze(
            eq(star.id),
            eq(snoozeDurationMinutes)
        )
    }

    @Test
    fun `snoozeStar should NOT call API directly`() = runBlocking {
        // Given
        val star = createTestStar("task1")

        // When
        repository.snoozeStar(star, 30)

        // Then - verify NO direct API calls
        verifyNoInteractions(mockApiService)
    }

    @Test
    fun `snoozeStar should update in-memory star state`() = runBlocking {
        // Given
        val star = createTestStar("task1")
        assertFalse(star.isSnoozed)
        assertNull(star.snoozeUntil)

        // When
        repository.snoozeStar(star, 30)

        // Then - in-memory state should be updated
        assertTrue(star.isSnoozed)
        assertNotNull(star.snoozeUntil)
    }

    // ==================== UPDATE STAR TESTS ====================

    @Test
    fun `updateStar should save to local DB first`() = runBlocking {
        // Given
        val star = createTestStar("task1")
        star.title = "Updated Title"
        star.urgency = 3

        // When
        repository.updateStar(star)

        // Then - verify local DB was updated
        verify(mockStarDao).insertStarWithTransaction(any())
    }

    @Test
    fun `updateStar should queue to SyncManager`() = runBlocking {
        // Given
        val star = createTestStar("task1")
        star.title = "Updated Title"

        // When
        repository.updateStar(star)

        // Then - verify queued to SyncManager
        verify(mockSyncManager).queueUpdate(eq(star.id), any())
    }

    @Test
    fun `updateStar should NOT call API directly`() = runBlocking {
        // Given
        val star = createTestStar("task1")

        // When
        repository.updateStar(star)

        // Then - NO direct API calls
        verifyNoInteractions(mockApiService)
    }

    // ==================== DELETE STAR TESTS ====================

    @Test
    fun `deleteStar should soft delete locally first`() = runBlocking {
        // Given
        val star = createTestStar("task1")

        // When
        repository.deleteStar(star)

        // Then - verify soft delete in local DB
        verify(mockStarDao).softDelete(star.id)
    }

    @Test
    fun `deleteStar should queue to SyncManager`() = runBlocking {
        // Given
        val star = createTestStar("task1")

        // When
        repository.deleteStar(star)

        // Then - verify queued to SyncManager
        verify(mockSyncManager).queueDelete(star.id)
    }

    // ==================== CLEAR ALL TESTS ====================

    @Test
    fun `clearAllTasks should clear local DB first`() = runBlocking {
        // When
        repository.clearAllTasks()

        // Then - local DB cleared FIRST
        val inOrder = inOrder(mockStarDao, mockSyncManager)
        inOrder.verify(mockStarDao).deleteAllStars()
        inOrder.verify(mockSyncManager).queueClearAll()
    }

    @Test
    fun `clearAllTasks should queue sync after local clear`() = runBlocking {
        // When
        repository.clearAllTasks()

        // Then - sync queued
        verify(mockSyncManager).queueClearAll()
    }

    @Test
    fun `clearAllTasks should NOT call API directly`() = runBlocking {
        // When
        repository.clearAllTasks()

        // Then - NO direct API calls
        verifyNoInteractions(mockApiService)
    }

    // ==================== ADD STAR TESTS ====================

    @Test
    fun `addStar should generate localId immediately`() = runBlocking {
        // Given
        val star = Star(100f, 200f, "Test Task", 2, null)
        assertTrue(star.id.isEmpty() || star.id.isBlank())

        // When
        val localId = repository.addStar(star)

        // Then - localId should be generated
        assertNotNull(localId)
        assertTrue(localId.isNotBlank())
        assertEquals(star.id, localId)
    }

    @Test
    fun `addStar should save to local DB with pending status`() = runBlocking {
        // Given
        val star = Star(100f, 200f, "Test Task", 2, null)

        // When
        repository.addStar(star)

        // Then - verify saved with pending status
        val captor = argumentCaptor<StarEntity>()
        verify(mockStarDao).insertStarWithTransaction(captor.capture())
        assertEquals("pending", captor.firstValue.syncStatus)
    }

    @Test
    fun `addStar should queue to SyncManager`() = runBlocking {
        // Given
        val star = Star(100f, 200f, "Test Task", 2, null)

        // When
        val localId = repository.addStar(star)

        // Then - verify queued
        verify(mockSyncManager).queueCreate(eq(localId), any(), any())
    }

    // ==================== HELPER METHODS ====================

    private fun createTestStar(id: String): Star {
        return Star(100f, 200f, "Test Task", 2, null).apply {
            this.id = id
        }
    }
}
