package com.cosmicocean.reminders

import com.cosmicocean.network.ViSupabaseReminderRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ViReminderMapperTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `maps a table row to a remote Vi entity`() {
        val entity = ViReminderMapper.toStarEntity(
            ViSupabaseReminderRow(id = "443f961d", due = "2026-07-06", text = "Carry Form 11"),
            zone
        )

        assertNotNull(entity)
        assertEquals("vi_remote_443f961d", entity!!.localId)
        assertTrue(ViReminderMapper.isRemoteReminder(entity))
        assertEquals("Vi · Carry Form 11", entity.title)
        assertEquals(ViReminderMapper.VI_CONTEXT_TAG, entity.contextTag)
        assertEquals(ViReminderMapper.SYNC_STATUS_REMOTE, entity.syncStatus)
        assertFalse(entity.isCompleted)
        assertFalse(entity.isArchived)

        // Due date is end of day in the given zone
        val expected = LocalDate.parse("2026-07-06")
            .atTime(LocalTime.of(23, 59, 59))
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, entity.dueDate)
    }

    @Test
    fun `rows without id or text are skipped`() {
        assertNull(ViReminderMapper.toStarEntity(ViSupabaseReminderRow(id = null, due = "2026-07-06", text = "x"), zone))
        assertNull(ViReminderMapper.toStarEntity(ViSupabaseReminderRow(id = "abc", due = "2026-07-06", text = "  "), zone))
        assertNull(ViReminderMapper.toStarEntity(ViSupabaseReminderRow(id = " ", due = null, text = "x"), zone))
    }

    @Test
    fun `bad or missing due dates map to null`() {
        assertNull(
            ViReminderMapper.toStarEntity(ViSupabaseReminderRow(id = "a1", due = "not-a-date", text = "Call dentist"), zone)!!.dueDate
        )
        assertNull(
            ViReminderMapper.toStarEntity(ViSupabaseReminderRow(id = "a2", due = null, text = "No due date"), zone)!!.dueDate
        )
    }

    @Test
    fun `local ids and canvas positions are stable across syncs`() {
        val row = ViSupabaseReminderRow(id = "443f961d", due = "2026-07-06", text = "Carry Form 11")
        val first = ViReminderMapper.toStarEntity(row, zone)!!
        val second = ViReminderMapper.toStarEntity(row, zone)!!
        assertEquals(first.localId, second.localId)
        assertEquals(first.x, second.x, 0f)
        assertEquals(first.y, second.y, 0f)
    }

    @Test
    fun `remote id round-trips through the local id`() {
        assertEquals("443f961d", ViReminderMapper.remoteIdOf(ViReminderMapper.localIdFor("443f961d")))
        assertNull(ViReminderMapper.remoteIdOf("star-12345"))
        assertNull(ViReminderMapper.remoteIdOf("vi_remote_"))
        assertTrue(ViReminderMapper.isRemoteReminder("vi_remote_abc"))
        assertFalse(ViReminderMapper.isRemoteReminder("vi_task"))
    }
}
