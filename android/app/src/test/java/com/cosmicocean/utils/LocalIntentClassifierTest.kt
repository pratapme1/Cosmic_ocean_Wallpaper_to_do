package com.cosmicocean.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalIntentClassifierTest {
    private val classifier = LocalIntentClassifier()

    private data class Expectation(
        val phrase: String,
        val context: String? = null,
        val priority: String? = null,
        val energy: String? = null
    )

    @Test
    fun `predicts context and priority across common phrases`() {
        val cases = listOf(
            // Work (easy)
            Expectation("Email client about proposal", context = "work"),
            Expectation("Prepare project presentation", context = "work"),
            Expectation("Review jira tickets for sprint", context = "work"),
            Expectation("Schedule meeting with team", context = "work"),
            Expectation("Finish report before deadline", context = "work"),
            Expectation("Slack update to client", context = "work"),
            // Home (easy)
            Expectation("Do laundry and dishes", context = "home"),
            Expectation("Clean the kitchen", context = "home"),
            Expectation("Cook dinner for family", context = "home"),
            Expectation("Repair leaking sink", context = "home"),
            Expectation("Organize kids room", context = "home"),
            Expectation("Sort home mail", context = "home"),
            // Grocery (medium)
            Expectation("Buy groceries at store", context = "grocery"),
            Expectation("Pick up milk and eggs", context = "grocery"),
            Expectation("Grab bread and fruit at market", context = "grocery"),
            Expectation("Grocery list: vegetables and snacks", context = "grocery"),
            // Gym (medium)
            Expectation("Go to gym for workout", context = "gym"),
            Expectation("Morning run cardio", context = "gym"),
            Expectation("Yoga session tonight", context = "gym"),
            Expectation("Lift weights training", context = "gym"),
            // Commute (medium)
            Expectation("Commute by train", context = "commute"),
            Expectation("Bus to station", context = "commute"),
            Expectation("Drive through traffic", context = "commute"),
            Expectation("Subway ride home", context = "commute"),
            // Priority (harder, mixed phrasing)
            Expectation("Urgent: pay invoice now", priority = "high"),
            Expectation("ASAP fix critical bug", priority = "high"),
            Expectation("Overdue report review", priority = "high"),
            Expectation("Optional read later", priority = "low"),
            Expectation("Someday organize old photos", priority = "low"),
            Expectation("Follow up on proposal next week", priority = "medium")
        )

        cases.forEach { expectation ->
            expectation.context?.let { expected ->
                val prediction = classifier.predictContext(expectation.phrase)
                assertNotNull("No context prediction for '${expectation.phrase}'", prediction)
                assertEquals(
                    "Unexpected context for '${expectation.phrase}'",
                    expected,
                    prediction!!.label
                )
            }
            expectation.priority?.let { expected ->
                val prediction = classifier.predictPriority(expectation.phrase)
                assertNotNull("No priority prediction for '${expectation.phrase}'", prediction)
                assertEquals(
                    "Unexpected priority for '${expectation.phrase}'",
                    expected,
                    prediction!!.label
                )
            }
        }
    }

    @Test
    fun `predicts energy levels for common phrases`() {
        val cases = listOf(
            Expectation("Write design document for project", energy = "high"),
            Expectation("Brainstorm product direction", energy = "high"),
            Expectation("Build prototype for presentation", energy = "high"),
            Expectation("Call bank about fee", energy = "low"),
            Expectation("Check email and review notes", energy = "low"),
            Expectation("Organize schedule and prep agenda", energy = "medium")
        )

        cases.forEach { expectation ->
            val prediction = classifier.predictEnergy(expectation.phrase)
            assertNotNull("No energy prediction for '${expectation.phrase}'", prediction)
            assertEquals(
                "Unexpected energy for '${expectation.phrase}'",
                expectation.energy,
                prediction!!.label
            )
        }
    }
}
