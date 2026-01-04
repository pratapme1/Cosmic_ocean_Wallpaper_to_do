package com.cosmicocean.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {
    // ==================== Authentication ====================
    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String>): Response<com.cosmicocean.model.AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): Response<com.cosmicocean.model.AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body body: Map<String, String>): Response<com.cosmicocean.model.TokenRefreshResponse>

    @POST("api/auth/wallpaper-token")
    suspend fun regenerateWallpaperToken(): Response<com.cosmicocean.model.WallpaperTokenResponse>

    // ==================== Wallpaper ====================
    @GET("api/wallpaper")
    @Streaming
    suspend fun getWallpaper(
        @Query("theme") theme: String? = null,
        @Query("resolution") resolution: String? = null,
        @Query("enhanced") enhanced: Boolean = true,
        @Query("timestamp") timestamp: Long? = null,
        @Query("timezone") timezone: String? = null
    ): Response<ResponseBody>

    // ==================== Tasks ====================
    @POST("api/tasks")
    suspend fun createTask(@Body body: Map<String, String>): Response<com.cosmicocean.model.TaskResponse>

    // Epic 8: LLM Intelligence Enhancement
    @POST("api/tasks/parse-llm")
    suspend fun parseTaskLLM(@Body body: com.cosmicocean.model.ParseRequest): Response<com.cosmicocean.model.ParseLLMResponse>

    @GET("api/tasks")
    suspend fun getTasks(): Response<List<com.cosmicocean.model.TaskResponse>>

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<com.cosmicocean.model.TaskResponse>

    @PATCH("api/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body body: @JvmSuppressWildcards Map<String, Any>): Response<com.cosmicocean.model.TaskResponse>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Void>

    @DELETE("api/tasks")
    suspend fun clearAllTasks(): Response<Void>

    @POST("api/tasks/{id}/snooze")
    suspend fun snoozeTask(
        @Path("id") id: String,
        @Body body: Map<String, Any> = emptyMap()
    ): Response<com.cosmicocean.model.TaskResponse>

    // ==================== Task Actions ====================
    @POST("api/done-for-today")
    suspend fun markDoneForToday(): Response<Void>

    @POST("api/metrics/app-open")
    suspend fun reportAppOpen(): Response<Void>

    // ==================== User Management ====================
    @GET("api/user")
    suspend fun getUser(): Response<com.cosmicocean.model.UserProfile>

    @PATCH("api/user")
    suspend fun updateUser(@Body body: Map<String, String>): Response<com.cosmicocean.model.UserProfile>

    @DELETE("api/user")
    suspend fun deleteAccount(): Response<com.cosmicocean.model.DeleteAccountResponse>

    @GET("api/user/export")
    suspend fun exportUserData(): Response<ResponseBody>

    // ==================== Statistics ====================
    @GET("api/user/stats/weekly")
    suspend fun getWeeklyStats(): Response<com.cosmicocean.model.WeeklyStats>

    @GET("api/user/stats/graduation")
    suspend fun getGraduationStats(): Response<com.cosmicocean.model.GraduationStats>
}
