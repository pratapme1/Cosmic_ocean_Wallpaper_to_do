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
    private lateinit var wallpaperUpdateLatch: CountDownLatch

    @Before
    fun setup() {
        starDao = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        context = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        wallpaperUpdateLatch = CountDownLatch(1)

        repository = TaskRepository(
            starDao = starDao,
            apiService = apiService,
            context = context,
            syncManager = syncManager,
            wallpaperUpdater = { wallpaperUpdateLatch.countDown() }
        )
    }

    @Test
    fun `addStar queues create and triggers wallpaper update`() = runTest {
        val star = Star(x = 100f, y = 100f, title = "Test Star", urgency = 1, dueDate = null)

        coEvery { starDao.insertStarWithTransaction(any()) } returns Unit
        coEvery { syncManager.queueCreate(any(), any(), any()) } returns Unit

        val localId = repository.addStar(star)

        coVerify { starDao.insertStarWithTransaction(match { it.localId == localId && it.syncStatus == "pending" }) }
        coVerify { syncManager.queueCreate(localId, any(), any()) }
        assertTrue("Wallpaper update should be triggered", wallpaperUpdateLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `updateStar queues update and triggers wallpaper update`() = runTest {
        val star = Star(x = 100f, y = 100f, title = "Test Star", urgency = 1, dueDate = null, id = "local-1")

        coEvery { starDao.insertStarWithTransaction(any()) } returns Unit
        coEvery { syncManager.queueUpdate(any(), any()) } returns Unit

        repository.updateStar(star)

        coVerify { starDao.insertStarWithTransaction(match { it.localId == "local-1" && it.syncStatus == "pending" }) }
        coVerify { syncManager.queueUpdate("local-1", any()) }
        assertTrue("Wallpaper update should be triggered", wallpaperUpdateLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `deleteStar queues delete and triggers wallpaper update`() = runTest {
        val star = Star(x = 100f, y = 100f, title = "Test Star", urgency = 1, dueDate = null, id = "local-1")

        coEvery { starDao.softDelete(any(), any()) } returns Unit
        coEvery { syncManager.queueDelete(any()) } returns Unit

        repository.deleteStar(star)

        coVerify { starDao.softDelete("local-1", any()) }
        coVerify { syncManager.queueDelete("local-1") }
        assertTrue("Wallpaper update should be triggered", wallpaperUpdateLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `clearAllTasks queues clear and triggers wallpaper update`() = runTest {
        coEvery { starDao.deleteAllStars() } returns Unit
        coEvery { syncManager.queueClearAll() } returns Unit

        repository.clearAllTasks()

        coVerify { starDao.deleteAllStars() }
        coVerify { syncManager.queueClearAll() }
        assertTrue("Wallpaper update should be triggered", wallpaperUpdateLatch.await(1, TimeUnit.SECONDS))
    }
}
