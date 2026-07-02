package com.cosmicocean.reminders

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
    fun `parses valid payload`() {
        val json = """
            {
              "generated_at": "2026-07-01T10:00:00Z",
              "reminders": [
                { "due": "2026-07-04", "text": "Renew passport" },
                { "due": "2026-06-30", "text": "Pay rent" }
              ]
            }
        """.trimIndent()

        val payload = ViReminderMapper.parsePayload(json)
        assertNotNull(payload)
        assertEquals(2, payload!!.reminders?.size)
        assertEquals("2026-07-01T10:00:00Z", payload.generatedAt)
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(ViReminderMapper.parsePayload("not json {{{"))
    }

    @Test
    fun `maps reminders to read-only Vi entities`() {
        val payload = ViRemindersPayload(
            generatedAt = "2026-07-01T10:00:00Z",
            reminders = listOf(ViReminder(due = "2026-07-04", text = "Renew passport"))
        )

        val entities = ViReminderMapper.toStarEntities(payload, zone)
        assertEquals(1, entities.size)

        val entity = entities[0]
        assertTrue(entity.localId.startsWith(ViReminderMapper.REMOTE_ID_PREFIX))
        assertTrue(ViReminderMapper.isRemoteReminder(entity))
        assertEquals("Vi · Renew passport", entity.title)
        assertEquals(ViReminderMapper.VI_CONTEXT_TAG, entity.contextTag)
        assertEquals("remote", entity.syncStatus)
        assertFalse(entity.isCompleted)
        assertFalse(entity.isArchived)

        // Due date is end of day in the given zone
        val expected = LocalDate.parse("2026-07-04")
            .atTime(LocalTime.of(23, 59, 59))
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, entity.dueDate)
    }

    @Test
    fun `skips reminders with blank text and tolerates bad dates`() {
        val payload = ViRemindersPayload(
            reminders = listOf(
                ViReminder(due = "2026-07-04", text = "  "),
                ViReminder(due = "not-a-date", text = "Call dentist"),
                ViReminder(due = null, text = "No due date")
            )
        )

        val entities = ViReminderMapper.toStarEntities(payload, zone)
        assertEquals(2, entities.size)
        assertNull(entities[0].dueDate)
        assertNull(entities[1].dueDate)
    }

    @Test
    fun `null payload or reminders maps to empty list`() {
        assertTrue(ViReminderMapper.toStarEntities(null, zone).isEmpty())
        assertTrue(ViReminderMapper.toStarEntities(ViRemindersPayload(), zone).isEmpty())
    }

    @Test
    fun `handles generated_at with non-UTC offset`() {
        val payload = ViRemindersPayload(
            generatedAt = "2026-07-02T16:23:47+05:30",
            reminders = listOf(ViReminder(due = "2026-07-04", text = "Dry run"))
        )
        val entities = ViReminderMapper.toStarEntities(payload, zone)
        // 16:23:47+05:30 == 10:53:47Z
        assertEquals(1782989627000L, entities[0].createdAt)
    }

    @Test
    fun `entity ids are stable across identical fetches`() {
        val payload = ViRemindersPayload(
            reminders = listOf(ViReminder(due = "2026-07-04", text = "Renew passport"))
        )
        val first = ViReminderMapper.toStarEntities(payload, zone)
        val second = ViReminderMapper.toStarEntities(payload, zone)
        assertEquals(first[0].localId, second[0].localId)
    }
}
