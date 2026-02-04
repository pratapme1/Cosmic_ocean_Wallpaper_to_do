package com.cosmicocean.network

import android.content.Context
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.data.PrivacyLevel
import com.cosmicocean.data.PrivacyPreferencesRepository
import com.cosmicocean.model.AuthResponse
import com.cosmicocean.model.DeleteAccountResponse
import com.cosmicocean.model.GraduationProgress
import com.cosmicocean.model.GraduationStats
import com.cosmicocean.model.MonthlyStats
import com.cosmicocean.model.ParseLLMResponse
import com.cosmicocean.model.ParseRequest
import com.cosmicocean.model.ParsedTaskResult
import com.cosmicocean.model.SyncResponse
import com.cosmicocean.model.SyncResults
import com.cosmicocean.model.SyncStatusResponse
import com.cosmicocean.model.TaskResponse
import com.cosmicocean.model.TokenRefreshResponse
import com.cosmicocean.model.User
import com.cosmicocean.model.UserPreferencesResponse
import com.cosmicocean.model.UserProfile
import com.cosmicocean.model.WallpaperTokenResponse
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.utils.LocalTaskParser
import com.cosmicocean.utils.TaskDateUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.Instant
import java.util.UUID

class LocalOnlyApiService(
    private val context: Context
) : ApiService {
    private val db = CosmicDatabase.getDatabase(context)
    private val userStore = LocalOnlyUserStore(context)
    private val privacyRepo = PrivacyPreferencesRepository(context)
    private val environmentRepo = EnvironmentPreferencesRepository(context)
    private val wallpaperPrefs = WallpaperPreferencesManager(context)
    private val gson = Gson()

    override suspend fun register(body: Map<String, String>): Response<AuthResponse> {
        val email = body["email"] ?: "local@device"
        val user = userStore.createUser(email)
        return Response.success(buildAuthResponse(user))
    }

    override suspend fun login(body: Map<String, String>): Response<AuthResponse> {
        val email = body["email"] ?: "local@device"
        val user = userStore.getOrCreateUser(email)
        return Response.success(buildAuthResponse(user))
    }

    override suspend fun refreshToken(body: Map<String, String>): Response<TokenRefreshResponse> {
        val token = "local-access-${UUID.randomUUID()}"
        return Response.success(TokenRefreshResponse(accessToken = token))
    }

    override suspend fun regenerateWallpaperToken(): Response<WallpaperTokenResponse> {
        val user = userStore.regenerateWallpaperToken()
        return Response.success(WallpaperTokenResponse(wallpaperToken = user.wallpaperToken))
    }

    override suspend fun getWallpaper(
        theme: String?,
        resolution: String?,
        enhanced: Boolean,
        timestamp: Long?,
        timezone: String?
    ): Response<ResponseBody> {
        return Response.error(503, "Local-only mode".toResponseBody("text/plain".toMediaType()))
    }

    override suspend fun uploadWallpaper(image: MultipartBody.Part): Response<UserPreferencesResponse> {
        val preferences = buildPreferencesResponse()
        return Response.success(preferences)
    }

    override suspend fun createTask(body: Map<String, String>): Response<TaskResponse> {
        val title = body["rawTitle"] ?: body["title"] ?: "New Task"
        val priority = body["priority"]?.toIntOrNull() ?: 2
        val dueDateMs = TaskDateUtils.parseToMillis(body["due_date"] ?: body["dueDate"], body["due_time"] ?: body["dueTime"])
        val localId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = com.cosmicocean.data.StarEntity(
            localId = localId,
            serverId = null,
            title = title,
            urgency = priority,
            dueDate = dueDateMs,
            x = 0f,
            y = 0f,
            createdAt = now,
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            syncStatus = "synced",
            syncVersion = 0,
            updatedAt = now,
            isDeleted = false,
            serverUpdatedAt = null
        )
        db.starDao().insertStar(entity)
        return Response.success(entity.toTaskResponse())
    }

    override suspend fun parseTaskLLM(body: ParseRequest): Response<ParseLLMResponse> {
        val fallback = LocalTaskParser.parse(body.title)
        return Response.success(
            ParseLLMResponse(
                success = true,
                parsed = fallback,
                originalInput = body.title,
                timestamp = Instant.now().toString()
            )
        )
    }

    override suspend fun getTasks(): Response<List<TaskResponse>> {
        val tasks = db.starDao().getAllActiveStarsSync().map { it.toTaskResponse() }
        return Response.success(tasks)
    }

    override suspend fun getTask(id: String): Response<TaskResponse> {
        val task = db.starDao().getById(id) ?: return Response.error(404, emptyResponse())
        return Response.success(task.toTaskResponse())
    }

    override suspend fun updateTask(id: String, body: Map<String, Any>): Response<TaskResponse> {
        val existing = db.starDao().getById(id) ?: return Response.error(404, emptyResponse())
        val dueDateValue = body["due_date"] ?: body["dueDate"]
        val dueTimeValue = body["due_time"] ?: body["dueTime"]
        val parsedDueDate = TaskDateUtils.parseToMillis(dueDateValue, dueTimeValue)
        val updated = existing.copy(
            title = body["rawTitle"]?.toString() ?: body["title"]?.toString() ?: existing.title,
            urgency = (body["priority"] as? Number)?.toInt() ?: existing.urgency,
            isCompleted = (body["completed"] as? Boolean) ?: existing.isCompleted,
            completedAt = if (body["completed"] as? Boolean == true) System.currentTimeMillis() else existing.completedAt,
            isArchived = (body["archived"] as? Boolean) ?: existing.isArchived,
            archivedAt = if (body["archived"] as? Boolean == true) System.currentTimeMillis() else existing.archivedAt,
            x = (body["x"] as? Number)?.toFloat() ?: existing.x,
            y = (body["y"] as? Number)?.toFloat() ?: existing.y,
            dueDate = parsedDueDate ?: existing.dueDate,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "synced"
        )
        db.starDao().insertStar(updated)
        return Response.success(updated.toTaskResponse())
    }

    override suspend fun deleteTask(id: String): Response<Void> {
        db.starDao().getById(id)?.let { db.starDao().deleteStarByLocalId(it.localId) }
        return Response.success(null)
    }

    override suspend fun clearAllTasks(): Response<Void> {
        db.starDao().deleteAllStars()
        return Response.success(null)
    }

    override suspend fun snoozeTask(id: String, body: Map<String, Any>): Response<TaskResponse> {
        val existing = db.starDao().getById(id) ?: return Response.error(404, emptyResponse())
        val snoozeUntil = System.currentTimeMillis() + 60 * 60 * 1000
        val updated = existing.copy(
            isSnoozed = true,
            snoozeUntil = snoozeUntil,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "synced"
        )
        db.starDao().insertStar(updated)
        return Response.success(updated.toTaskResponse())
    }

    override suspend fun markDoneForToday(): Response<Void> {
        userStore.markDoneForToday()
        return Response.success(null)
    }

    override suspend fun reportAppOpen(): Response<Void> {
        return Response.success(null)
    }

    override suspend fun getUser(): Response<UserProfile> {
        val user = userStore.getOrCreateUser()
        return Response.success(
            UserProfile(
                id = user.id,
                email = user.email,
                theme = user.theme,
                resolution = user.resolution,
                doneForToday = user.doneForToday,
                doneForTodayAt = user.doneForTodayAt
            )
        )
    }

    override suspend fun updateUser(body: Map<String, String>): Response<UserProfile> {
        val user = userStore.updateUser(body)
        return Response.success(
            UserProfile(
                id = user.id,
                email = user.email,
                theme = user.theme,
                resolution = user.resolution,
                doneForToday = user.doneForToday,
                doneForTodayAt = user.doneForTodayAt
            )
        )
    }

    override suspend fun deleteAccount(): Response<DeleteAccountResponse> {
        userStore.clear()
        db.starDao().deleteAllStars()
        return Response.success(
            DeleteAccountResponse(
                success = true,
                message = "Local account removed"
            )
        )
    }

    override suspend fun getPreferences(): Response<UserPreferencesResponse> {
        return Response.success(buildPreferencesResponse())
    }

    override suspend fun updatePreferences(body: Map<String, Any>): Response<UserPreferencesResponse> {
        body["default_privacy_level"]?.toString()?.let {
            privacyRepo.setDefaultPrivacyLevel(PrivacyLevel.fromString(it))
        }
        (body["hide_all_tasks_mode"] as? Boolean)?.let {
            privacyRepo.setHideAllTasksMode(it)
        }
        body["time_of_day_mode"]?.toString()?.let {
            val mode = if (it.lowercase() == "manual") com.cosmicocean.ui.state.TimeOfDayMode.MANUAL else com.cosmicocean.ui.state.TimeOfDayMode.AUTO
            environmentRepo.setTimeOfDayMode(mode)
        }
        body["manual_time_period"]?.toString()?.let {
            environmentRepo.setManualTimePeriod(it)
        }
        (body["weather_overlay_enabled"] as? Boolean)?.let {
            environmentRepo.setWeatherOverlayEnabled(it)
        }
        body["particle_intensity"]?.toString()?.let {
            val intensity = when (it.lowercase()) {
                "low" -> com.cosmicocean.ui.state.ParticleIntensity.LOW
                "high" -> com.cosmicocean.ui.state.ParticleIntensity.HIGH
                else -> com.cosmicocean.ui.state.ParticleIntensity.MEDIUM
            }
            environmentRepo.setParticleIntensity(intensity)
        }
        body["wallpaper_mode"]?.toString()?.let {
            environmentRepo.setWallpaperMode(it)
            wallpaperPrefs.setWallpaperMode(it)
        }
        return Response.success(buildPreferencesResponse())
    }

    override suspend fun exportUserData(): Response<ResponseBody> {
        val user = userStore.getOrCreateUser()
        val tasks = db.starDao().getAllActiveStarsSync().map { it.toTaskResponse() }
        val export = mapOf(
            "exportedAt" to Instant.now().toString(),
            "user" to user,
            "tasks" to tasks
        )
        val json = gson.toJson(export)
        return Response.success(json.toResponseBody("application/json".toMediaType()))
    }

    override suspend fun getWeeklyStats(): Response<com.cosmicocean.model.WeeklyStats> {
        return Response.success(com.cosmicocean.model.WeeklyStats(weekStart = Instant.now().toString()))
    }

    override suspend fun getGraduationStats(): Response<GraduationStats> {
        return Response.success(
            GraduationStats(
                months = listOf(
                    MonthlyStats(
                        month = Instant.now().toString(),
                        totalAppOpens = 0,
                        totalWidgetInteractions = 0,
                        totalTasksCompleted = 0,
                        totalTasksCompletedViaWidget = 0
                    )
                ),
                graduation = GraduationProgress(score = 0, message = "Local-only mode", widgetCompletionRate = 0)
            )
        )
    }

    override suspend fun sync(request: com.cosmicocean.model.SyncRequest): Response<SyncResponse> {
        val tasks = db.starDao().getAllActiveStarsSync().map { it.toTaskResponse() }
        return Response.success(
            SyncResponse(
                syncedAt = System.currentTimeMillis(),
                tasks = tasks,
                results = SyncResults(applied = 0, rejected = 0, skipped = 0),
                conflicts = emptyList(),
                mappings = emptyList()
            )
        )
    }

    override suspend fun getSyncStatus(): Response<SyncStatusResponse> {
        val tasks = db.starDao().getAllActiveStarsSync()
        val lastModified = tasks.maxOfOrNull { it.updatedAt }
        return Response.success(
            SyncStatusResponse(
                taskCount = tasks.size,
                lastModified = lastModified,
                serverTime = System.currentTimeMillis()
            )
        )
    }

    private fun buildAuthResponse(user: LocalOnlyUser): AuthResponse {
        return AuthResponse(
            accessToken = "local-access-${user.id}",
            refreshToken = "local-refresh-${user.id}",
            user = User(
                id = user.id,
                email = user.email,
                theme = user.theme,
                resolution = user.resolution
            )
        )
    }

    private suspend fun buildPreferencesResponse(): UserPreferencesResponse {
        val privacy = privacyRepo.preferencesFlow.first()
        val env = environmentRepo.preferencesFlow.first()
        return UserPreferencesResponse(
            theme = userStore.getOrCreateUser().theme,
            resolution = userStore.getOrCreateUser().resolution,
            displayMode = "one_thing",
            timezone = java.util.TimeZone.getDefault().id,
            defaultPrivacyLevel = privacy.defaultPrivacyLevel.name.lowercase(),
            hideAllTasksMode = privacy.hideAllTasksMode,
            timeOfDayMode = env.timeOfDayMode.name.lowercase(),
            manualTimePeriod = env.manualTimePeriod,
            weatherOverlayEnabled = env.weatherOverlayEnabled,
            particleIntensity = env.particleIntensity.name.lowercase(),
            wallpaperMode = env.wallpaperMode,
            customWallpaperPath = wallpaperPrefs.getCustomWallpaperPath()
        )
    }

    private fun extractContextTags(input: String): List<String> {
        val regex = Regex("@\\w+")
        return regex.findAll(input).map { it.value }.toList()
    }

    private fun emptyResponse(): ResponseBody {
        return "".toResponseBody("text/plain".toMediaType())
    }

    private fun com.cosmicocean.data.StarEntity.toTaskResponse(): TaskResponse {
        return TaskResponse(
            id = localId,
            userId = userStore.getOrCreateUser().id,
            title = title,
            dueDate = dueDate?.toString(),
            dueTime = null,
            estimateMinutes = null,
            priority = urgency,
            completed = isCompleted,
            completedAt = completedAt?.toString(),
            snoozedUntil = snoozeUntil?.toString(),
            contextLocation = null,
            contextTime = null,
            energyRequired = null,
            decayPrompted = false,
            originalDueDate = null,
            timesRescheduled = 0,
            x = x.toDouble(),
            y = y.toDouble(),
            isSubtask = isSubtask,
            isRecurring = isRecurring,
            echoInterval = echoInterval,
            archived = isArchived,
            archivedAt = archivedAt?.toString(),
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
            isPrivate = false,
            privacyLevel = "public",
            privacyDisplay = null
        )
    }
}
