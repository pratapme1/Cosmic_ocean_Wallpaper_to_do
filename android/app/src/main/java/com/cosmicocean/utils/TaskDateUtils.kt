package com.cosmicocean.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object TaskDateUtils {
    fun parseToMillis(dateValue: Any?, timeValue: Any?): Long? {
        val dateStr = dateValue?.toString()?.trim()
        val timeStr = timeValue?.toString()?.trim()

        if (dateStr.isNullOrEmpty() || dateStr.equals("null", ignoreCase = true)) return null

        return try {
            // Millisecond timestamp (string or number)
            if (dateStr.matches(Regex("^\\d{10,13}$"))) {
                val ms = dateStr.toLong()
                if (dateStr.length == 10) ms * 1000 else ms
            } else if (dateStr.contains("T")) {
                Instant.parse(dateStr).toEpochMilli()
            } else {
                val datePart = LocalDate.parse(dateStr)
                val timePart = if (!timeStr.isNullOrEmpty()) {
                    parseTime(timeStr)
                } else {
                    LocalTime.of(23, 59, 59)
                }
                LocalDateTime.of(datePart, timePart)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(timeStr: String): LocalTime {
        return try {
            if (timeStr.count { it == ':' } == 1) {
                LocalTime.parse("$timeStr:00")
            } else {
                LocalTime.parse(timeStr)
            }
        } catch (e: Exception) {
            LocalTime.of(23, 59, 59)
        }
    }
}
