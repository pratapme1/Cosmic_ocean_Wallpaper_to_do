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
import org.mockito.junit.MockitoJUnitRunner
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import retrofit2.Response

@RunWith(MockitoJUnitRunner::class)
class TaskRepositoryTest {

    @Mock
    lateinit var context: Context

    private lateinit var starDao: FakeStarDao
    private lateinit var apiService: FakeApiService
    private lateinit var repository: TaskRepository
    
    // Capture the wallpaper update call
    private var wallpaperUpdateCalled = false

    @Before
    fun setup() {
        wallpaperUpdateCalled = false
        starDao = FakeStarDao()
        apiService = FakeApiService()
        
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
    fun `addStar should trigger immediate wallpaper update via lambda`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = Star(
                x = 100f,
                y = 100f,
                title = "Test Star",
                urgency = 1,
                dueDate = null,
                id = "test-id"
            )

            // Act
            repository.addStar(star)

            // Assert
            assertTrue("Wallpaper update lambda should be called", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `updateStar should trigger immediate wallpaper update via lambda`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = Star(
                x = 100f,
                y = 100f,
                title = "Test Star",
                urgency = 1,
                dueDate = null,
                id = "test-id"
            )

            // Act
            repository.updateStar(star)

            // Assert
            assertTrue("Wallpaper update lambda should be called", wallpaperUpdateCalled)
        }
    }

    @Test
    fun `deleteStar should trigger immediate wallpaper update via lambda`() = runBlocking {
        Mockito.mockStatic(android.util.Log::class.java).use {
            // Arrange
            val star = Star(
                x = 100f,
                y = 100f,
                title = "Test Star",
                urgency = 1,
                dueDate = null,
                id = "test-id"
            )

            // Act
            repository.deleteStar(star)

            // Assert
            assertTrue("Wallpaper update lambda should be called", wallpaperUpdateCalled)
        }
    }
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

class FakeApiService : ApiService {
    override suspend fun createTask(body: Map<String, String>) = Response.error<TaskResponse>(500, ResponseBody.create(null, "Fake"))
    override suspend fun updateTask(id: String, body: Map<String, Any>) = Response.error<TaskResponse>(500, ResponseBody.create(null, "Fake"))
    override suspend fun deleteTask(id: String) = Response.error<Void>(500, ResponseBody.create(null, "Fake"))
    
    // Stub required members as TODO or minimal defaults
    override suspend fun register(body: Map<String, String>) = TODO()
    override suspend fun login(body: Map<String, String>) = TODO()
    override suspend fun refreshToken(body: Map<String, String>) = TODO()
    override suspend fun regenerateWallpaperToken() = TODO()
    override suspend fun getWallpaper(t: String?, r: String?, e: Boolean, ts: Long?, tz: String?) = TODO()
    override suspend fun uploadWallpaper(image: okhttp3.MultipartBody.Part) = TODO()
    override suspend fun parseTaskLLM(body: com.cosmicocean.model.ParseRequest) = TODO()
    override suspend fun getTasks() = TODO()
    override suspend fun getTask(id: String) = TODO()
    override suspend fun clearAllTasks() = TODO()
    override suspend fun snoozeTask(id: String, body: Map<String, Any>) = TODO()
    override suspend fun markDoneForToday() = TODO()
    override suspend fun reportAppOpen() = TODO()
    override suspend fun getUser() = TODO()
    override suspend fun updateUser(body: Map<String, String>) = TODO()
    override suspend fun deleteAccount() = TODO()
    override suspend fun getPreferences() = TODO()
    override suspend fun updatePreferences(body: Map<String, Any>) = TODO()
    override suspend fun exportUserData() = TODO()
    override suspend fun getWeeklyStats() = TODO()
    override suspend fun getGraduationStats() = TODO()
}
