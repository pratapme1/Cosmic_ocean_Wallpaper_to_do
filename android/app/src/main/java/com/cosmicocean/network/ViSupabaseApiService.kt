package com.cosmicocean.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * One row of the vi_assistant_reminders table (PostgREST returns a JSON array).
 */
data class ViSupabaseReminderRow(
    val id: String? = null,
    val due: String? = null,
    val text: String? = null
)

data class ViCompletePayload(
    val done: Boolean = true,
    @SerializedName("completed_at") val completedAt: String
)

data class ViReminderWritePayload(
    val id: String? = null,
    val due: String? = null,
    val text: String,
    val done: Boolean = false
)

/**
 * Supabase REST (PostgREST) client for the Vi reminders table.
 * Query params use PostgREST filter syntax, e.g. done=eq.false / id=eq.<id>.
 */
interface ViSupabaseApiService {
    @GET("vi_assistant_reminders")
    suspend fun getActiveReminders(
        @Query("done") doneFilter: String = "eq.false",
        @Query("select") select: String = "id,due,text",
        @Query("order") order: String = "due.asc"
    ): Response<List<ViSupabaseReminderRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("vi_assistant_reminders")
    suspend fun createReminder(
        @Query("on_conflict") onConflict: String = "id",
        @Body body: ViReminderWritePayload
    ): Response<Unit>

    @Headers("Prefer: return=minimal")
    @PATCH("vi_assistant_reminders")
    suspend fun updateReminder(
        @Query("id") idFilter: String,
        @Body body: ViReminderWritePayload
    ): Response<Unit>

    @Headers("Prefer: return=minimal")
    @PATCH("vi_assistant_reminders")
    suspend fun completeReminder(
        @Query("id") idFilter: String,
        @Body body: ViCompletePayload
    ): Response<Unit>

    @Headers("Prefer: return=minimal")
    @DELETE("vi_assistant_reminders")
    suspend fun deleteReminder(
        @Query("id") idFilter: String
    ): Response<Unit>
}
