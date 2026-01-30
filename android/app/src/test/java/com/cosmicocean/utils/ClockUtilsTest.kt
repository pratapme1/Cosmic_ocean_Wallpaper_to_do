package com.cosmicocean.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ClockUtilsTest {

    @Test
    fun `formatTime should return correct HH mm for 24h format`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 35)
        }
        val result = ClockUtils.formatTime(calendar.timeInMillis, is24h = true)
        assertEquals("14:35", result)
    }

    @Test
    fun `formatTime should return correct hh mm aa for 12h format`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 5)
        }
        val result = ClockUtils.formatTime(calendar.timeInMillis, is24h = false)
        assertEquals("02:05 PM", result)
    }

    @Test
    fun `formatDate should return correct EEEE dd MMMM`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 30)
        }
        val result = ClockUtils.formatDate(calendar.timeInMillis)
        // Adjusting based on locale might be tricky, but we expect a specific format
        assertEquals("Friday, 30 January", result)
    }
}
