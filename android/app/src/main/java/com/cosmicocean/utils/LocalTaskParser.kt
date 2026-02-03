package com.cosmicocean.utils

import com.cosmicocean.model.ParsedTaskResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

object LocalTaskParser {
    private val contextRegex = Regex("@\\w+")
    private val whitespaceRegex = Regex("\\s+")

    private val relativeRegex = Regex("\\b(in\\s+)(\\d+(?:\\.\\d+)?)\\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days|week|weeks|month|months)\\b", RegexOption.IGNORE_CASE)
    private val todayRegex = Regex("\\b(today|tomorrow|tonight|yesterday|now|tmr|tmrw|tom|tdy|tod|yday|yest|tonite)\\b", RegexOption.IGNORE_CASE)
    private val nextRangeRegex = Regex("\\bnext\\s+(week|month|year)\\b", RegexOption.IGNORE_CASE)
    private val weekdayRegex = Regex("\\b((?:next|this)\\s+)?(mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\\b", RegexOption.IGNORE_CASE)
    private val isoDateRegex = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    private val usDateRegex = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
    private val monthNameRegex = Regex(
        "\\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,\\s*(\\d{4}))?\\b",
        RegexOption.IGNORE_CASE
    )
    private val time12hRegex = Regex("\\b(?:at|by)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
    private val time24hRegex = Regex("\\b(?:at|by)?\\s*(\\d{1,2}):(\\d{2})\\b", RegexOption.IGNORE_CASE)
    private val timeContextRegex = listOf(
        Regex("\\b(morning|this morning|early morning)\\b", RegexOption.IGNORE_CASE) to 9,
        Regex("\\b(afternoon|this afternoon)\\b", RegexOption.IGNORE_CASE) to 15,
        Regex("\\b(evening|this evening)\\b", RegexOption.IGNORE_CASE) to 19,
        Regex("\\b(tonight)\\b", RegexOption.IGNORE_CASE) to 20,
        Regex("\\b(noon)\\b", RegexOption.IGNORE_CASE) to 12,
        Regex("\\b(midnight)\\b", RegexOption.IGNORE_CASE) to 0,
        Regex("\\b(eod|end of day)\\b", RegexOption.IGNORE_CASE) to 17
    )

    private val estimateRegexes = listOf(
        Regex("\\b(\\d+(?:\\.\\d+)?)\\s*h(?:ours?)?\\s*(\\d+)?\\s*m(?:in(?:ute)?s?)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(\\d+)\\s*m(?:in(?:ute)?s?)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(\\d+(?:\\.\\d+)?)\\s*h(?:ours?)?\\b", RegexOption.IGNORE_CASE)
    )

    private val highPriorityKeywords = listOf("urgent", "asap", "critical", "important", "priority 1", "p1")
    private val mediumPriorityKeywords = listOf("priority 2", "p2", "medium priority", "normal priority")
    private val lowPriorityKeywords = listOf("low priority", "later", "someday", "whenever", "maybe", "not urgent", "p3", "priority 3")

    private val endOfRegexes = listOf(
        Regex("\\b(eod|end of day)\\b", RegexOption.IGNORE_CASE) to "eod",
        Regex("\\b(eow|end of week)\\b", RegexOption.IGNORE_CASE) to "eow",
        Regex("\\b(eom|end of month)\\b", RegexOption.IGNORE_CASE) to "eom",
        Regex("\\b(eoy|end of year)\\b", RegexOption.IGNORE_CASE) to "eoy"
    )

    private val dayMonthRegex = Regex("\\b(\\d{1,2})-(\\d{1,2})(?:-(\\d{2,4}))?\\b")

    private val recurringPatterns = listOf(
        Regex("\\bevery\\s+day\\b", RegexOption.IGNORE_CASE) to "daily",
        Regex("\\bdaily\\b", RegexOption.IGNORE_CASE) to "daily",
        Regex("\\bweekly\\b", RegexOption.IGNORE_CASE) to "weekly",
        Regex("\\bmonthly\\b", RegexOption.IGNORE_CASE) to "monthly",
        Regex("\\bevery\\s+week\\b", RegexOption.IGNORE_CASE) to "weekly"
    )

    fun parse(input: String?): ParsedTaskResult {
        if (input.isNullOrBlank()) {
            return ParsedTaskResult(
                title = "New Task",
                dueDate = null,
                dueTime = null,
                estimateMinutes = null,
                priority = 2,
                category = null,
                energyLevel = "medium",
                contextTags = emptyList(),
                isRecurring = false,
                recurringPattern = null,
                confidence = 0.0,
                source = "local_parser",
                reason = "empty_input"
            )
        }

        val original = input.trim()
        var working = original
        val extractions = mutableListOf<String>()

        // Context tags
        val contextTags = contextRegex.findAll(working).map { it.value }.toList()
        if (contextTags.isNotEmpty()) {
            working = contextRegex.replace(working, " ").trim()
            extractions.add("context")
        }

        // Recurring
        var recurringPattern: String? = null
        for ((regex, pattern) in recurringPatterns) {
            val match = regex.find(working)
            if (match != null) {
                recurringPattern = pattern
                working = removeRange(working, match.range)
                extractions.add("recurring")
                break
            }
        }
        val isRecurring = recurringPattern != null

        // Time context
        var suggestedHour: Int? = null
        for ((regex, hour) in timeContextRegex) {
            val match = regex.find(working)
            if (match != null) {
                suggestedHour = hour
                working = removeRange(working, match.range)
                extractions.add("time_context")
                break
            }
        }

        val now = ZonedDateTime.now(ZoneId.systemDefault())

        var dueDateTime: LocalDateTime? = null
        var dueTime: LocalTime? = null
        var explicitTime = false

        // Relative date/time like "in 2 hours"
        val relativeMatch = relativeRegex.find(working)
        if (relativeMatch != null) {
            val amount = relativeMatch.groupValues[2].toDoubleOrNull() ?: 0.0
            val unit = relativeMatch.groupValues[3].lowercase()
            dueDateTime = when {
                unit.startsWith("min") -> now.plusMinutes(amount.roundToInt().toLong()).toLocalDateTime()
                unit.startsWith("hour") || unit.startsWith("hr") -> now.plusMinutes((amount * 60).roundToInt().toLong()).toLocalDateTime()
                unit.startsWith("day") -> now.plusDays(amount.roundToInt().toLong()).toLocalDateTime()
                unit.startsWith("week") -> now.plusWeeks(amount.roundToInt().toLong()).toLocalDateTime()
                unit.startsWith("month") -> now.plusMonths(amount.roundToInt().toLong()).toLocalDateTime()
                else -> null
            }
            if (dueDateTime != null) {
                dueTime = dueDateTime?.toLocalTime()
                explicitTime = true
                working = removeRange(working, relativeMatch.range)
                extractions.add("due_date")
            }
        }

        if (dueDateTime == null) {
            for ((regex, kind) in endOfRegexes) {
                val match = regex.find(working) ?: continue
                val date = when (kind) {
                    "eod" -> now.toLocalDate()
                    "eow" -> now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    "eom" -> now.toLocalDate().with(TemporalAdjusters.lastDayOfMonth())
                    "eoy" -> now.toLocalDate().with(TemporalAdjusters.lastDayOfYear())
                    else -> now.toLocalDate()
                }
                val time = if (kind == "eod") LocalTime.of(17, 0) else LocalTime.of(23, 59)
                dueDateTime = LocalDateTime.of(date, time)
                dueTime = time
                explicitTime = true
                working = removeRange(working, match.range)
                extractions.add("due_date")
                break
            }
        }

        if (dueDateTime == null) {
            // Date keywords
            val keywordMatch = todayRegex.find(working)
            if (keywordMatch != null) {
                val keyword = keywordMatch.value.lowercase()
                val date = when (keyword) {
                    "today", "tonight" -> now.toLocalDate()
                    "tomorrow" -> now.plusDays(1).toLocalDate()
                    "yesterday" -> now.minusDays(1).toLocalDate()
                    "now" -> now.toLocalDate()
                    "tmr", "tmrw", "tom" -> now.plusDays(1).toLocalDate()
                    "tdy", "tod" -> now.toLocalDate()
                    "yday", "yest" -> now.minusDays(1).toLocalDate()
                    "tonite" -> now.toLocalDate()
                    else -> now.toLocalDate()
                }
                dueDateTime = if (keyword == "now") {
                    explicitTime = true
                    dueTime = now.toLocalTime()
                    now.toLocalDateTime()
                } else {
                    LocalDateTime.of(date, LocalTime.MIDNIGHT)
                }
                working = removeRange(working, keywordMatch.range)
                extractions.add("due_date")
            }
        }

        if (dueDateTime == null) {
            val nextRangeMatch = nextRangeRegex.find(working)
            if (nextRangeMatch != null) {
                val range = nextRangeMatch.groupValues[1].lowercase()
                val date = when (range) {
                    "week" -> now.plusWeeks(1).toLocalDate()
                    "month" -> now.plusMonths(1).toLocalDate()
                    "year" -> now.plusYears(1).toLocalDate()
                    else -> now.toLocalDate()
                }
                dueDateTime = LocalDateTime.of(date, LocalTime.MIDNIGHT)
                working = removeRange(working, nextRangeMatch.range)
                extractions.add("due_date")
            }
        }

        if (dueDateTime == null) {
            val isoMatch = isoDateRegex.find(working)
            if (isoMatch != null) {
                try {
                    val date = LocalDate.of(
                        isoMatch.groupValues[1].toInt(),
                        isoMatch.groupValues[2].toInt(),
                        isoMatch.groupValues[3].toInt()
                    )
                    dueDateTime = LocalDateTime.of(date, LocalTime.MIDNIGHT)
                    working = removeRange(working, isoMatch.range)
                    extractions.add("due_date")
                } catch (e: Exception) {
                    // Ignore invalid date
                }
            }
        }

        if (dueDateTime == null) {
            val usMatch = usDateRegex.find(working)
            if (usMatch != null) {
                try {
                    val month = usMatch.groupValues[1].toInt()
                    val day = usMatch.groupValues[2].toInt()
                    val year = usMatch.groupValues[3].ifBlank { now.year.toString() }.let { normalizeYear(it) }
                    val date = LocalDate.of(year, month, day)
                    dueDateTime = LocalDateTime.of(date, LocalTime.MIDNIGHT)
                    working = removeRange(working, usMatch.range)
                    extractions.add("due_date")
                } catch (e: Exception) {
                    // Ignore invalid date
                }
            }
        }

        if (dueDateTime == null) {
            val dmMatch = dayMonthRegex.find(working)
            if (dmMatch != null) {
                try {
                    val first = dmMatch.groupValues[1].toInt()
                    val second = dmMatch.groupValues[2].toInt()
                    val year = dmMatch.groupValues[3].ifBlank { now.year.toString() }.let { normalizeYear(it) }

                    val (day, month) = if (first > 12 && second <= 12) {
                        first to second
                    } else if (second > 12 && first <= 12) {
                        second to first
                    } else {
                        // Ambiguous, default to month/day for US locale
                        second to first
                    }

                    val date = LocalDate.of(year, month, day)
                    dueDateTime = LocalDateTime.of(date, LocalTime.MIDNIGHT)
                    working = removeRange(working, dmMatch.range)
                    extractions.add("due_date")
                } catch (e: Exception) {
                    // Ignore invalid date
                }
            }
        }

        if (dueDateTime == null) {
            val monthMatch = monthNameRegex.find(working)
            if (monthMatch != null) {
                try {
                    val month = monthNameToNumber(monthMatch.groupValues[1])
                    val day = monthMatch.groupValues[2].toInt()
                    val year = monthMatch.groupValues[3].ifBlank { now.year.toString() }.let { normalizeYear(it) }
                    val date = LocalDate.of(year, month, day)
                    dueDateTime = LocalDateTime.of(date, LocalTime.MIDNIGHT)
                    working = removeRange(working, monthMatch.range)
                    extractions.add("due_date")
                } catch (e: Exception) {
                    // Ignore invalid date
                }
            }
        }

        if (dueDateTime == null) {
            val weekdayMatch = weekdayRegex.find(working)
            if (weekdayMatch != null) {
                val qualifier = weekdayMatch.groupValues[1].trim().lowercase()
                val isNext = qualifier.startsWith("next")
                val dayOfWeek = parseDayOfWeek(weekdayMatch.groupValues[2])
                val base = now.toLocalDate()
                val target = if (isNext) {
                    base.with(TemporalAdjusters.next(dayOfWeek))
                } else {
                    base.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                }
                dueDateTime = LocalDateTime.of(target, LocalTime.MIDNIGHT)
                working = removeRange(working, weekdayMatch.range)
                extractions.add("due_date")
            }
        }

        // Time parsing
        val timeMatch12 = time12hRegex.find(working)
        if (timeMatch12 != null) {
            try {
                val hour = timeMatch12.groupValues[1].toInt()
                val minutes = timeMatch12.groupValues[2].ifBlank { "0" }.toInt()
                val amPm = timeMatch12.groupValues[3].lowercase()
                dueTime = to24Hour(hour, minutes, amPm)
                explicitTime = true
                working = removeRange(working, timeMatch12.range)
                extractions.add("due_time")
            } catch (e: Exception) {
                // Ignore invalid time
            }
        } else {
            val timeMatch24 = time24hRegex.find(working)
            if (timeMatch24 != null) {
                try {
                    val hour = timeMatch24.groupValues[1].toInt()
                    val minutes = timeMatch24.groupValues[2].toInt()
                    dueTime = LocalTime.of(hour, minutes)
                    explicitTime = true
                    working = removeRange(working, timeMatch24.range)
                    extractions.add("due_time")
                } catch (e: Exception) {
                    // Ignore invalid time
                }
            }
        }

        if (dueTime == null && suggestedHour != null) {
            dueTime = LocalTime.of(suggestedHour, 0)
            explicitTime = true
        }

        if (dueDateTime == null && dueTime != null) {
            val date = if (now.toLocalTime().isBefore(dueTime)) {
                now.toLocalDate()
            } else {
                now.plusDays(1).toLocalDate()
            }
            dueDateTime = LocalDateTime.of(date, dueTime)
            extractions.add("due_date")
        } else if (dueDateTime != null && dueTime != null) {
            dueDateTime = LocalDateTime.of(dueDateTime.toLocalDate(), dueTime)
        } else if (dueDateTime != null && dueTime == null && suggestedHour != null) {
            dueDateTime = LocalDateTime.of(dueDateTime.toLocalDate(), LocalTime.of(suggestedHour, 0))
        }

        val estimateMinutes = extractEstimateMinutes(working)
        if (estimateMinutes != null) {
            extractions.add("estimate")
        }

        val priority = inferPriority(original, dueDateTime)
        if (priority != 2) {
            extractions.add("priority")
        }

        val cleanedTitle = cleanTitle(working)
        val dueDateStr = dueDateTime?.toLocalDate()?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dueTimeStr = if (explicitTime) {
            dueTime?.let { DateTimeFormatter.ofPattern("HH:mm").format(it) }
        } else {
            null
        }

        val confidence = calculateConfidence(extractions, cleanedTitle, original)

        return ParsedTaskResult(
            title = cleanedTitle,
            dueDate = dueDateStr,
            dueTime = dueTimeStr,
            estimateMinutes = estimateMinutes,
            priority = priority,
            category = null,
            energyLevel = "medium",
            contextTags = contextTags.distinct(),
            isRecurring = isRecurring,
            recurringPattern = recurringPattern,
            confidence = confidence,
            source = "local_parser",
            reason = "local_only"
        )
    }

    private fun extractEstimateMinutes(text: String): Int? {
        var working = text
        for (regex in estimateRegexes) {
            val match = regex.find(working) ?: continue
            val minutes = when (regex) {
                estimateRegexes[0] -> {
                    val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    val mins = match.groupValues[2].ifBlank { "0" }.toInt()
                    (hours * 60 + mins).roundToInt()
                }
                estimateRegexes[1] -> match.groupValues[1].toInt()
                else -> {
                    val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    (hours * 60).roundToInt()
                }
            }
            working = removeRange(working, match.range)
            return minutes
        }
        return null
    }

    private fun inferPriority(text: String, dueDateTime: LocalDateTime?): Int {
        val lower = text.lowercase()

        for (keyword in lowPriorityKeywords) {
            if (lower.contains(keyword)) return 3
        }
        for (keyword in mediumPriorityKeywords) {
            if (lower.contains(keyword)) return 2
        }
        for (keyword in highPriorityKeywords) {
            if (lower.contains(keyword)) return 1
        }

        if (dueDateTime != null) {
            val today = LocalDate.now()
            val dueDate = dueDateTime.toLocalDate()
            return when {
                dueDate.isBefore(today) -> 1
                dueDate.isEqual(today) -> 1
                dueDate.isEqual(today.plusDays(1)) -> 2
                else -> 3
            }
        }

        // Default to future priority (P3) when no due date is detected.
        return 3
    }

    private fun cleanTitle(text: String): String {
        val normalized = whitespaceRegex.replace(text, " ").trim()
        val stripped = normalized.replace(Regex("^[,:;.\\-!?]+|[,:;.\\-!?]+$"), "").trim()
        if (stripped.isBlank()) return "New Task"
        return stripped.replaceFirstChar { it.uppercase() }
    }

    private fun calculateConfidence(extractions: List<String>, title: String, original: String): Double {
        var confidence = 0.6
        if (title.length < 3) confidence -= 0.2
        if (title.equals(original, ignoreCase = true) && extractions.isEmpty()) confidence -= 0.1
        confidence += extractions.distinct().size * 0.05
        return confidence.coerceIn(0.0, 1.0)
    }

    private fun removeRange(text: String, range: IntRange): String {
        if (range.first < 0 || range.last >= text.length) return text
        val cleaned = text.removeRange(range).replace(whitespaceRegex, " ").trim()
        return cleaned
    }

    private fun monthNameToNumber(name: String): Int {
        return when (name.lowercase()) {
            "jan", "january" -> 1
            "feb", "february" -> 2
            "mar", "march" -> 3
            "apr", "april" -> 4
            "may" -> 5
            "jun", "june" -> 6
            "jul", "july" -> 7
            "aug", "august" -> 8
            "sep", "sept", "september" -> 9
            "oct", "october" -> 10
            "nov", "november" -> 11
            "dec", "december" -> 12
            else -> 1
        }
    }

    private fun normalizeYear(yearStr: String): Int {
        val year = yearStr.toInt()
        return if (year < 100) 2000 + year else year
    }

    private fun parseDayOfWeek(text: String): DayOfWeek {
        return when (text.lowercase()) {
            "mon", "monday" -> DayOfWeek.MONDAY
            "tue", "tues", "tuesday" -> DayOfWeek.TUESDAY
            "wed", "weds", "wednesday" -> DayOfWeek.WEDNESDAY
            "thu", "thur", "thurs", "thursday" -> DayOfWeek.THURSDAY
            "fri", "friday" -> DayOfWeek.FRIDAY
            "sat", "saturday" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
    }

    private fun to24Hour(hour: Int, minutes: Int, amPm: String): LocalTime {
        var h = hour % 12
        if (amPm == "pm") {
            h += 12
        }
        return LocalTime.of(h, minutes)
    }
}
