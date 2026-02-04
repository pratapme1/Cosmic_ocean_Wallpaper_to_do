package com.cosmicocean.utils

import com.cosmicocean.model.ParsedTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class UserParsingMatrixTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 2, 4, 9, 0)
            .atZone(zone)
            .toInstant(),
        zone
    )
    private val classifier = LocalIntentClassifier()

    private data class PhraseExpectation(
        val phrase: String,
        val expectDueDate: String? = null,
        val expectDueTime: String? = null,
        val expectDueTimeNull: Boolean = false,
        val expectContext: String? = null,
        val expectPriority: Int? = null,
        val expectRecurring: String? = null,
        val expectEstimate: Int? = null
    )

    @Test
    fun `generate parsing results for common user phrases`() {
        val cases = listOf(
            // Time & date (30)
            PhraseExpectation("Pay rent tomorrow 9am", expectDueDate = "2026-02-05", expectDueTime = "09:00", expectPriority = 2),
            PhraseExpectation("Submit report today by 5pm", expectDueDate = "2026-02-04", expectDueTime = "17:00", expectPriority = 1),
            PhraseExpectation("Call mom tonight", expectDueDate = "2026-02-04", expectDueTime = "20:00", expectPriority = 1),
            PhraseExpectation("Dentist appointment on 2026-02-10 at 14:30", expectDueDate = "2026-02-10", expectDueTime = "14:30", expectPriority = 3),
            PhraseExpectation("Team meeting next Tue at 3pm", expectDueDate = "2026-02-10", expectDueTime = "15:00", expectPriority = 3, expectContext = "work"),
            PhraseExpectation("Pick up groceries this Friday 6pm", expectDueDate = "2026-02-06", expectDueTime = "18:00", expectPriority = 3, expectContext = "grocery"),
            PhraseExpectation("Pay credit card by 02/15/2026", expectDueDate = "2026-02-15", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Pick up meds at pharmacy tomorrow", expectDueDate = "2026-02-05", expectDueTimeNull = true, expectPriority = 2),
            PhraseExpectation("Pay electricity bill by Friday", expectDueDate = "2026-02-06", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Doctor visit March 12, 2026 at 8:15am", expectDueDate = "2026-03-12", expectDueTime = "08:15", expectPriority = 3),
            PhraseExpectation("File taxes by April 15", expectDueDate = "2026-04-15", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Gym class at 6am", expectDueDate = "2026-02-05", expectDueTime = "06:00", expectPriority = 2, expectContext = "gym"),
            PhraseExpectation("Standup at 09:30", expectDueDate = "2026-02-04", expectDueTime = "09:30", expectPriority = 1),
            PhraseExpectation("Lunch at noon", expectDueDate = "2026-02-04", expectDueTime = "12:00", expectPriority = 1),
            PhraseExpectation("Pick up kids from school at 3:15pm", expectDueDate = "2026-02-04", expectDueTime = "15:15", expectPriority = 1),
            PhraseExpectation("Send weekly report by EOD", expectDueDate = "2026-02-04", expectDueTime = "17:00", expectPriority = 1, expectRecurring = "weekly"),
            PhraseExpectation("Plan sprint by EOW", expectDueDate = "2026-02-08", expectDueTime = "23:59", expectPriority = 3),
            PhraseExpectation("Pay rent EOM", expectDueDate = "2026-02-28", expectDueTime = "23:59", expectPriority = 3),
            PhraseExpectation("Year review EOY", expectDueDate = "2026-12-31", expectDueTime = "23:59", expectPriority = 3),
            PhraseExpectation("Take meds in 2 hours", expectDueDate = "2026-02-04", expectDueTime = "11:00", expectPriority = 1),
            PhraseExpectation("Start laundry in 45 min", expectDueDate = "2026-02-04", expectDueTime = "09:45", expectPriority = 1),
            PhraseExpectation("Call client in 1.5 hours", expectDueDate = "2026-02-04", expectDueTime = "10:30", expectPriority = 1, expectContext = "work"),
            PhraseExpectation("Submit assignment in 2 days", expectDueDate = "2026-02-06", expectDueTime = "09:00", expectPriority = 3),
            PhraseExpectation("Call bank in 20 mins", expectDueDate = "2026-02-04", expectDueTime = "09:20", expectPriority = 1),
            PhraseExpectation("Doctor follow-up next Monday morning", expectDueDate = "2026-02-09", expectDueTime = "09:00", expectPriority = 3),
            PhraseExpectation("Pay phone bill next week", expectDueDate = "2026-02-11", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Grocery delivery on 2/8 at 10am", expectDueDate = "2026-02-08", expectDueTime = "10:00", expectPriority = 3, expectContext = "grocery"),
            PhraseExpectation("Renew car registration next month", expectDueDate = "2026-03-04", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Team retro this afternoon", expectDueDate = "2026-02-04", expectDueTime = "15:00", expectPriority = 1),
            PhraseExpectation("Submit PTO request by end of day", expectDueDate = "2026-02-04", expectDueTime = "17:00", expectPriority = 1),
            PhraseExpectation("Remind me to call mom tmrw at 9am", expectDueDate = "2026-02-05", expectDueTime = "09:00", expectPriority = 2),
            PhraseExpectation("Pay rent tmr 9am", expectDueDate = "2026-02-05", expectDueTime = "09:00", expectPriority = 2),
            PhraseExpectation("Send invoice by EOD", expectDueDate = "2026-02-04", expectDueTime = "17:00", expectPriority = 1),
            PhraseExpectation("Pick up groceries tonight 7pm", expectDueDate = "2026-02-04", expectDueTime = "19:00", expectPriority = 1, expectContext = "grocery"),
            PhraseExpectation("Bus to work tomorrow 8am", expectDueDate = "2026-02-05", expectDueTime = "08:00", expectPriority = 2),
            PhraseExpectation("Oil change on 02-14", expectDueDate = "2026-02-14", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Pick up package 14-2-2026", expectDueDate = "2026-02-14", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Call dad at 7pm", expectDueDate = "2026-02-04", expectDueTime = "19:00", expectPriority = 1),
            PhraseExpectation("Take meds in 90 mins", expectDueDate = "2026-02-04", expectDueTime = "10:30", expectPriority = 1),
            PhraseExpectation("Team sync weekly on Monday 10am", expectDueDate = "2026-02-09", expectDueTime = "10:00", expectPriority = 3, expectRecurring = "weekly"),
            PhraseExpectation("Pay rent end of month", expectDueDate = "2026-02-28", expectDueTime = "23:59", expectPriority = 3),
            PhraseExpectation("Run 5k tomorrow morning", expectDueDate = "2026-02-05", expectDueTime = "09:00", expectPriority = 2),
            PhraseExpectation("Gym workout in 3 hrs", expectDueDate = "2026-02-04", expectDueTime = "12:00", expectPriority = 1, expectContext = "gym"),
            PhraseExpectation("Drop off dry cleaning tomorrow", expectDueDate = "2026-02-05", expectDueTimeNull = true, expectPriority = 2),
            PhraseExpectation("Submit expense report in 3 days", expectDueDate = "2026-02-07", expectDueTime = "09:00", expectPriority = 3),
            PhraseExpectation("Buy groceries at market tomorrow 6pm", expectDueDate = "2026-02-05", expectDueTime = "18:00", expectPriority = 2, expectContext = "grocery"),
            PhraseExpectation("Commute to office by train tomorrow 8am", expectDueDate = "2026-02-05", expectDueTime = "08:00", expectPriority = 2, expectContext = "commute"),
            PhraseExpectation("Gym yoga class this evening", expectDueDate = "2026-02-04", expectDueTime = "19:00", expectPriority = 1, expectContext = "gym"),
            PhraseExpectation("Review PR ASAP", expectPriority = 1),
            PhraseExpectation("Not urgent: clean desk", expectPriority = 3),
            PhraseExpectation("Call doctor tomorrow morning", expectDueDate = "2026-02-05", expectDueTime = "09:00", expectPriority = 2),
            PhraseExpectation("Water plants at 6pm", expectDueDate = "2026-02-04", expectDueTime = "18:00", expectPriority = 1),
            PhraseExpectation("Take out trash tonight", expectDueDate = "2026-02-04", expectDueTime = "20:00", expectPriority = 1),
            PhraseExpectation("Doctor appointment 2/29/2026"),
            PhraseExpectation("Pay rent 2026-02-04 09:00", expectDueDate = "2026-02-04", expectDueTime = "09:00", expectPriority = 1),

            // Recurring & estimate (9)
            PhraseExpectation("Water plants every day", expectRecurring = "daily"),
            PhraseExpectation("Backup photos daily", expectRecurring = "daily"),
            PhraseExpectation("Team sync weekly", expectRecurring = "weekly"),
            PhraseExpectation("Pay rent monthly", expectRecurring = "monthly"),
            PhraseExpectation("Run report every week", expectRecurring = "weekly"),
            PhraseExpectation("Write blog post 1h 30m", expectEstimate = 90),
            PhraseExpectation("Clean garage 45m", expectEstimate = 45),
            PhraseExpectation("Deep clean 2 hours", expectEstimate = 120),
            PhraseExpectation("Study math for 1.5h", expectEstimate = 90),

            // Context (24)
            PhraseExpectation("Email client about proposal", expectContext = "work"),
            PhraseExpectation("Prepare project presentation", expectContext = "work"),
            PhraseExpectation("Review jira tickets for sprint", expectContext = "work"),
            PhraseExpectation("Schedule meeting with team", expectContext = "work"),
            PhraseExpectation("Finish report before deadline", expectContext = "work"),
            PhraseExpectation("Slack update to client", expectContext = "work"),
            PhraseExpectation("Do laundry and dishes", expectContext = "home"),
            PhraseExpectation("Clean the kitchen at home", expectContext = "home"),
            PhraseExpectation("Cook dinner for family", expectContext = "home"),
            PhraseExpectation("Repair leaking sink"),
            PhraseExpectation("Organize kids room", expectContext = "home"),
            PhraseExpectation("Sort home mail", expectContext = "home"),
            PhraseExpectation("Buy groceries at store", expectContext = "grocery"),
            PhraseExpectation("Pick up milk and eggs", expectContext = "grocery"),
            PhraseExpectation("Grab bread and fruit at market", expectContext = "grocery"),
            PhraseExpectation("Grocery list: vegetables and snacks", expectContext = "grocery"),
            PhraseExpectation("Go to gym for workout", expectContext = "gym"),
            PhraseExpectation("Morning run cardio", expectContext = "gym"),
            PhraseExpectation("Yoga workout session", expectContext = "gym"),
            PhraseExpectation("Lift weights training", expectContext = "gym"),
            PhraseExpectation("Commute by train", expectContext = "commute"),
            PhraseExpectation("Bus to station", expectContext = "commute"),
            PhraseExpectation("Drive through traffic", expectContext = "commute"),
            PhraseExpectation("Subway ride home", expectContext = "commute"),

            // Priority (12)
            PhraseExpectation("Urgent: pay invoice now", expectDueDate = "2026-02-04", expectDueTime = "09:00", expectPriority = 1),
            PhraseExpectation("ASAP fix critical bug", expectPriority = 1),
            PhraseExpectation("Overdue report review", expectPriority = 1),
            PhraseExpectation("Optional read later", expectPriority = 3),
            PhraseExpectation("Someday organize old photos", expectPriority = 3),
            PhraseExpectation("Follow up on proposal next week", expectDueDate = "2026-02-11", expectDueTimeNull = true, expectPriority = 3),
            PhraseExpectation("Not urgent, review when possible", expectPriority = 3),
            PhraseExpectation("Important: prepare budget", expectPriority = 1),
            PhraseExpectation("P2 update documentation", expectPriority = 2),
            PhraseExpectation("P1 fix prod issue", expectPriority = 1),
            PhraseExpectation("Low priority: tidy desktop", expectPriority = 3),
            PhraseExpectation("Maybe buy new monitor", expectPriority = 3)
        )

        val results = cases.map { expectation ->
            expectation to hybridParse(expectation.phrase)
        }

        results.forEach { (expectation, result) ->
            expectation.expectDueDate?.let { expected ->
                assertEquals("Due date mismatch for '${expectation.phrase}'", expected, result.dueDate)
            }
            expectation.expectDueTime?.let { expected ->
                assertEquals("Due time mismatch for '${expectation.phrase}'", expected, result.dueTime)
            }
            if (expectation.expectDueTimeNull) {
                assertNull("Expected no due time for '${expectation.phrase}'", result.dueTime)
            }
            expectation.expectPriority?.let { expected ->
                assertEquals("Priority mismatch for '${expectation.phrase}'", expected, result.priority)
            }
            expectation.expectContext?.let { expected ->
                val tags = result.contextTags ?: emptyList()
                assertNotNull("Expected context tag for '${expectation.phrase}'", tags)
                assertEquals(
                    "Context mismatch for '${expectation.phrase}'",
                    "@$expected",
                    tags.firstOrNull()
                )
            }
            expectation.expectRecurring?.let { expected ->
                assertEquals("Recurring pattern mismatch for '${expectation.phrase}'", expected, result.recurringPattern)
            }
            expectation.expectEstimate?.let { expected ->
                assertEquals("Estimate mismatch for '${expectation.phrase}'", expected, result.estimateMinutes)
            }
        }

        writeResultsTable(results)
    }

    private fun hybridParse(input: String): ParsedTaskResult {
        val base = LocalTaskParser.parse(input, clock)
        val contextPrediction = classifier.predictContext(input)
        val priorityPrediction = classifier.predictPriority(input)
        val energyPrediction = classifier.predictEnergy(input)

        val contextTags = when {
            !base.contextTags.isNullOrEmpty() -> base.contextTags
            contextPrediction != null && isContextConfident(contextPrediction) -> listOf("@${contextPrediction.label}")
            else -> emptyList()
        }

        val priority = if (base.priority == 2 && priorityPrediction != null && isPriorityConfident(priorityPrediction)) {
            when (priorityPrediction.label) {
                "high" -> 1
                "low" -> 3
                else -> 2
            }
        } else {
            base.priority
        }

        val energyLevel = when {
            base.energyLevel != null && base.energyLevel != "medium" -> base.energyLevel
            energyPrediction != null && isEnergyConfident(energyPrediction) -> energyPrediction.label
            else -> base.energyLevel
        }

        val category = base.category ?: contextPrediction?.takeIf { isContextConfident(it) }?.let {
            when (it.label) {
                "work" -> "work"
                "home", "grocery", "commute" -> "personal"
                "gym", "health" -> "health"
                else -> null
            }
        }

        val confidence = maxOf(
            base.confidence,
            contextPrediction?.confidence ?: 0.0,
            priorityPrediction?.confidence ?: 0.0,
            energyPrediction?.confidence ?: 0.0
        )

        return base.copy(
            priority = priority,
            category = category,
            energyLevel = energyLevel,
            contextTags = contextTags,
            confidence = confidence,
            source = "local_hybrid_test"
        )
    }

    private fun isContextConfident(prediction: IntentPrediction): Boolean {
        return prediction.rawScore >= 1.1 && prediction.rawMargin >= 0.4
    }

    private fun isPriorityConfident(prediction: IntentPrediction): Boolean {
        return prediction.rawScore >= 1.1 && prediction.rawMargin >= 0.3
    }

    private fun isEnergyConfident(prediction: IntentPrediction): Boolean {
        return prediction.rawScore >= 1.0 && prediction.rawMargin >= 0.2
    }

    private fun writeResultsTable(results: List<Pair<PhraseExpectation, ParsedTaskResult>>) {
        val root = findRepoRoot()
        val outputPath = root.resolve(Paths.get("docs", "qa", "parsing-results.md"))
        Files.createDirectories(outputPath.parent)

        val builder = StringBuilder()
        builder.append("# Parsing Results (Fixed Clock)\n\n")
        builder.append("- Baseline clock: 2026-02-04 09:00 America/Los_Angeles\n")
        builder.append("- Total phrases: ${results.size}\n\n")
        builder.append("| # | Phrase | Title | Due Date | Due Time | Priority | Context Tags | Category | Energy | Recurring | Estimate | Confidence | Source |\n")
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n")

        results.forEachIndexed { index, (expectation, result) ->
            val rowPhrase = escape(expectation.phrase)
            val title = escape(result.title)
            val dueDate = result.dueDate ?: "-"
            val dueTime = result.dueTime ?: "-"
            val priority = result.priority.toString()
            val contextTags = result.contextTags?.joinToString(", ") ?: "-"
            val category = result.category ?: "-"
            val energy = result.energyLevel ?: "-"
            val recurring = result.recurringPattern ?: if (result.isRecurring) "recurring" else "-"
            val estimate = result.estimateMinutes?.toString() ?: "-"
            val confidence = String.format("%.2f", result.confidence)
            val source = result.source

            builder.append("| ${index + 1} | $rowPhrase | $title | $dueDate | $dueTime | $priority | $contextTags | $category | $energy | $recurring | $estimate | $confidence | $source |\n")
        }

        Files.write(outputPath, builder.toString().toByteArray())
    }

    private fun escape(value: String): String {
        return value.replace("|", "\\|").trim()
    }

    private fun findRepoRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            if (Files.exists(current.resolve("AGENTS.md"))) return current
            current = current.parent ?: return current
        }
        return current
    }
}
