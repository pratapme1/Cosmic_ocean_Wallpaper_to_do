package com.cosmicocean.reminders

import com.cosmicocean.data.StarEntity
import com.cosmicocean.network.ViSupabaseReminderRow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Maps rows of the Supabase vi_assistant_reminders table to StarEntity rows.
 * Vi reminders live in Room like normal tasks (so the app canvas, widget and
 * wallpaper all see them), but are remote-owned: title/due date follow the
 * table on every sync, completion is pushed back as a PATCH, and rows vanish
 * once the table no longer returns their id.
 */
object ViReminderMapper {
    const val REMOTE_ID_PREFIX = "vi_remote_"
    const val VI_CONTEXT_TAG = "vi"
    const val SYNC_STATUS_REMOTE = "remote"
    private const val VI_TITLE_PREFIX = "Vi · "

    fun localIdFor(remoteId: String): String = REMOTE_ID_PREFIX + remoteId

    /** Inverse of [localIdFor]; null when the id is not a Vi reminder. */
    fun remoteIdOf(localId: String): String? =
        if (localId.startsWith(REMOTE_ID_PREFIX)) {
            localId.removePrefix(REMOTE_ID_PREFIX).takeIf { it.isNotBlank() }
        } else {
            null
        }

    fun isRemoteReminder(localId: String): Boolean = localId.startsWith(REMOTE_ID_PREFIX)

    fun isRemoteReminder(task: StarEntity): Boolean = isRemoteReminder(task.localId)

    /**
     * Maps one table row to a fresh StarEntity. Returns null for rows the app
     * cannot render (missing id or blank text). The x/y scatter is derived
     * from the id so a reminder keeps its spot on the app canvas across syncs.
     */
    fun toStarEntity(
        row: ViSupabaseReminderRow,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): StarEntity? {
        val remoteId = row.id?.trim().orEmpty()
        val text = row.text?.trim().orEmpty()
        if (remoteId.isEmpty() || text.isEmpty()) return null

        val hash = abs(remoteId.hashCode())
        return StarEntity(
            localId = localIdFor(remoteId),
            serverId = null,
            title = VI_TITLE_PREFIX + text,
            urgency = 2,
            dueDate = parseDueDate(row.due, zone),
            x = 150f + (hash % 700),
            y = 400f + ((hash / 7) % 900),
            createdAt = now,
            isSubtask = false,
            parentId = null,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            contextTag = VI_CONTEXT_TAG,
            syncStatus = SYNC_STATUS_REMOTE
        )
    }

    /**
     * "YYYY-MM-DD" -> end of that day in the given zone, matching how
     * SyncManager treats date-only due dates. Overdue rendering (red) kicks
     * in via the generator once the date is in the past.
     */
    fun parseDueDate(due: String?, zone: ZoneId): Long? {
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
}
