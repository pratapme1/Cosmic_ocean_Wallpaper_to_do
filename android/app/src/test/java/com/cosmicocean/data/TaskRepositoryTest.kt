package com.cosmicocean.data

import android.content.Context
import com.cosmicocean.model.Star
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.network.ApiService
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.MockitoJUnitRunner
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import retrofit2.Response

@RunWith(MockitoJUnitRunner::class)
class TaskRepositoryTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var apiService: ApiService

    private lateinit var starDao: FakeStarDao
    private lateinit var repository: TaskRepository
    
    // Capture the wallpaper update call
    private var wallpaperUpdateCalled = false

    @Before
    fun setup() {
        wallpaperUpdateCalled = false
        starDao = FakeStarDao()
        
        // Inject lambda that sets flag
        repository = TaskRepository(
            starDao, 
            apiService, 
            context,
            wallpaperUpdater = { ctx -> 
                wallpaperUpdateCalled = true 
            }
        )
    }

    @Test
    fun `addStar should trigger wallpaper update ONLY on success`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = createStar()
            val successfulResponse = Response.success(
                TaskResponse(
                    id = "new-id",
                    title = "Test Star",
                    dueDate = "2026-01-01"
                )
            )
            Mockito.`when`(apiService.createTask(anyMap())).thenReturn(successfulResponse)

            // Act
            repository.addStar(star)

            // Assert
            assertTrue("Wallpaper update SHOULD be called on sync success", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `addStar should NOT trigger wallpaper update on failure`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = createStar()
            val errorResponse = Response.error<TaskResponse>(500, ResponseBody.create(null, "Error"))
            Mockito.`when`(apiService.createTask(anyMap())).thenReturn(errorResponse)

            // Act
            repository.addStar(star)

            // Assert
            assertFalse("Wallpaper update should NOT be called on sync failure", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `updateStar should trigger wallpaper update ONLY on success`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = createStar()
            val successfulResponse = Response.success(
                TaskResponse(
                    id = "test-id",
                    title = "Test Star",
                    dueDate = "2026-01-01"
                )
            )
            Mockito.`when`(apiService.updateTask(anyString(), anyMap())).thenReturn(successfulResponse)

            // Act
            repository.updateStar(star)

            // Assert
            assertTrue("Wallpaper update SHOULD be called on sync success", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `updateStar should NOT trigger wallpaper update on failure`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = createStar()
            val errorResponse = Response.error<TaskResponse>(500, ResponseBody.create(null, "Error"))
            Mockito.`when`(apiService.updateTask(anyString(), anyMap())).thenReturn(errorResponse)

            // Act
            repository.updateStar(star)

            // Assert
            assertFalse("Wallpaper update should NOT be called on sync failure", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `deleteStar should trigger wallpaper update ONLY on success`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = createStar()
            val successfulResponse = Response.success<Void>(null)
            Mockito.`when`(apiService.deleteTask(anyString())).thenReturn(successfulResponse)

            // Act
            repository.deleteStar(star)

            // Assert
            assertTrue("Wallpaper update SHOULD be called on sync success", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `deleteStar should NOT trigger wallpaper update on failure`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = createStar()
            val errorResponse = Response.error<Void>(500, ResponseBody.create(null, "Error"))
            Mockito.`when`(apiService.deleteTask(anyString())).thenReturn(errorResponse)

            // Act
            repository.deleteStar(star)

            // Assert
            assertFalse("Wallpaper update should NOT be called on sync failure", wallpaperUpdateCalled)
        }
    }

    private fun createStar() = Star(
        x = 100f,
        y = 100f,
        title = "Test Star",
        urgency = 1,
        dueDate = null,
        id = "test-id"
    )
}

// Minimal Fakes needed for test to run without errors
class FakeStarDao : StarDao {
    override fun getAllActiveStars() = emptyFlow<List<StarEntity>>()
    override suspend fun insertStar(star: StarEntity) {}
    override suspend fun insertStars(stars: List<StarEntity>) {}
    override suspend fun deleteStar(star: StarEntity) {}
    override suspend fun deleteStarById(id: String) {}
    override suspend fun deleteAllStars() {}
}
