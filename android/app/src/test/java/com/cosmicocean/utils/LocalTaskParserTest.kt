package com.cosmicocean.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LocalTaskParserTest {

    private val baseClock = Clock.fixed(
        Instant.parse("2026-02-06T10:00:00Z"),
        ZoneId.of("UTC")
    )

    @Test
    fun parsesTomorrowEveningKeepsTitle() {
        val result = LocalTaskParser.parse("tomorrow evening", baseClock)
        assertEquals("Tomorrow evening", result.title)
        assertEquals("2026-02-07", result.dueDate)
        assertEquals("19:00", result.dueTime)
    }

    @Test
    fun parsesTomorrowMorningKeepsTitle() {
        val result = LocalTaskParser.parse("tomorrow morning", baseClock)
        assertEquals("Tomorrow morning", result.title)
        assertEquals("2026-02-07", result.dueDate)
        assertEquals("09:00", result.dueTime)
    }

    @Test
    fun parsesTomorrowAfternoonKeepsTitle() {
        val result = LocalTaskParser.parse("tomorrow afternoon", baseClock)
        assertEquals("Tomorrow afternoon", result.title)
        assertEquals("2026-02-07", result.dueDate)
        assertEquals("15:00", result.dueTime)
    }

    @Test
    fun parsesTomorrowNightKeepsTitle() {
        val result = LocalTaskParser.parse("tomorrow night", baseClock)
        assertEquals("Tomorrow night", result.title)
        assertEquals("2026-02-07", result.dueDate)
        assertEquals("20:00", result.dueTime)
    }

    @Test
    fun explicitTimeOverridesTimeBucket() {
        val result = LocalTaskParser.parse("tomorrow 7pm evening", baseClock)
        assertEquals("Tomorrow 7pm evening", result.title)
        assertEquals("2026-02-07", result.dueDate)
        assertEquals("19:00", result.dueTime)
    }
}
