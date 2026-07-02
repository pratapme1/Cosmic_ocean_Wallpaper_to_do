package com.cosmicocean.reminders

import com.cosmicocean.data.StarEntity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Remote reminders feed schema:
 * { "generated_at": ISO8601, "reminders": [ { "due": "YYYY-MM-DD", "text": "..." } ] }
 */
data class ViRemindersPayload(
    @SerializedName("generated_at") val generatedAt: String? = null,
    val reminders: List<ViReminder>? = null
)

data class ViReminder(
    val due: String? = null,
    val text: String? = null
)

/**
 * Parses the reminders JSON and maps entries to read-only StarEntity
 * instances for wallpaper rendering. These entities are never written to
 * Room and never enter the sync queue - they exist only in memory.
 */
object ViReminderMapper {
    const val REMOTE_ID_PREFIX = "vi_remote_"
    const val VI_CONTEXT_TAG = "vi"
    private const val VI_TITLE_PREFIX = "Vi · "

    private val gson = Gson()

    fun parsePayload(json: String): ViRemindersPayload? {
        return try {
            gson.fromJson(json, ViRemindersPayload::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun toStarEntities(
        payload: ViRemindersPayload?,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): List<StarEntity> {
        val reminders = payload?.reminders ?: return emptyList()
        val createdAt = parseGeneratedAt(payload.generatedAt) ?: now

        return reminders.mapIndexedNotNull { index, reminder ->
            val text = reminder.text?.trim().orEmpty()
            if (text.isEmpty()) return@mapIndexedNotNull null

            StarEntity(
                // Stable per content so the renderer's flows don't see churn
                localId = "$REMOTE_ID_PREFIX${index}_${(reminder.due + text).hashCode()}",
                serverId = null,
                title = VI_TITLE_PREFIX + text,
                urgency = 2,
                dueDate = parseDueDate(reminder.due, zone),
                x = 0.5f,
                y = 0.5f,
                createdAt = createdAt,
                isSubtask = false,
                parentId = null,
                isRecurring = false,
                echoInterval = null,
                isCompleted = false,
                completedAt = null,
                isArchived = false,
                archivedAt = null,
                contextTag = VI_CONTEXT_TAG,
                syncStatus = "remote"
            )
        }
    }

    fun isRemoteReminder(task: StarEntity): Boolean =
        task.localId.startsWith(REMOTE_ID_PREFIX)

    /**
     * "YYYY-MM-DD" -> end of that day in the given zone, matching how
     * SyncManager treats date-only due dates. Overdue rendering (red) kicks
     * in via the generator once the date is in the past.
     */
    private fun parseDueDate(due: String?, zone: ZoneId): Long? {
        if (due.isNullOrBlank()) return null
        return try {
            LocalDate.parse(due.trim())
                .atTime(LocalTime.of(23, 59, 59))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGeneratedAt(generatedAt: String?): Long? {
        if (generatedAt.isNullOrBlank()) return null
        val value = generatedAt.trim()
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            // Instant.parse only accepts "Z"; the feed may use offsets like +05:30
            try {
                java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
