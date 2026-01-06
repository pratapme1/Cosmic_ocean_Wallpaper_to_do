package com.cosmicocean

import com.cosmicocean.model.Star
import org.junit.Test
import org.junit.Assert.*

/**
 * Search Functionality Test Suite
 * Following NO-GO workflow: Test REAL user inputs, not ideal cases
 *
 * User Requirements:
 * 1. Search all tasks (title, category, context_tags)
 * 2. Real-time status display (Overdue, Due Soon, In Progress, Complete)
 * 3. Smart sorting by status
 * 4. Fuzzy/semantic search
 */
class SearchFunctionalityTest {

    // Helper: Create test stars with realistic data
    private fun createTestStar(
        title: String,
        urgency: Int = 2,
        dueMinutes: Float? = null,
        status: String = "active"
    ): Star {
        val dueDate = dueMinutes?.let {
            System.currentTimeMillis() + (it * 60 * 1000).toLong()
        }

        return Star(
            x = 0f,
            y = 0f,
            title = title,
            urgency = urgency,
            dueDate = dueDate
        ).apply {
            if (status == "completed") {
                isCompleted = true
                completedAt = System.currentTimeMillis()
            }
            updateDueIn()
        }
    }

    // REAL USER INPUTS (from user feedback)
    private val realUserTasks = listOf(
        // User types partial/typo
        "email manager about project" to "emgr",
        "call mom urgently" to "call",
        "buy groceries for dinner" to "groc",
        "finish report by friday" to "rprt",

        // User searches by status
        "overdue task 1" to "overdue",
        "due soon task" to "due",
        "completed task" to "complete",

        // User searches by priority
        "urgent meeting" to "urgent",
        "low priority email" to "low"
    )

    // ============================================
    // TEST 1: Fuzzy Search (Real User Inputs)
    // ============================================

    @Test
    fun `fuzzy search - emgr should match Email manager`() {
        val star = createTestStar("Email manager about project")
        assertTrue("Fuzzy match failed for 'emgr'",
            fuzzyMatch("email manager about project", "emgr"))
    }

    @Test
    fun `fuzzy search - groc should match buy groceries`() {
        val star = createTestStar("buy groceries for dinner")
        assertTrue("Fuzzy match failed for 'groc'",
            fuzzyMatch("buy groceries for dinner", "groc"))
    }

    @Test
    fun `fuzzy search - rprt should match finish report`() {
        val star = createTestStar("finish report by friday")
        assertTrue("Fuzzy match failed for 'rprt'",
            fuzzyMatch("finish report by friday", "rprt"))
    }

    @Test
    fun `fuzzy search - handles case insensitive`() {
        assertTrue(fuzzyMatch("Email Manager", "emgr"))
        assertTrue(fuzzyMatch("email manager", "EMGR"))
        assertTrue(fuzzyMatch("EMAIL MANAGER", "emgr"))
    }

    // ============================================
    // TEST 2: Status Classification (Real-Time)
    // ============================================

    @Test
    fun `status - overdue task shows OVERDUE`() {
        val star = createTestStar("Call client", dueMinutes = -30f)
        assertEquals("OVERDUE", getTaskStatus(star))
        assertEquals(0, getStatusPriority(star)) // Highest priority
    }

    @Test
    fun `status - task due in 1 hour shows DUE SOON`() {
        val star = createTestStar("Team meeting", dueMinutes = 60f)
        assertEquals("DUE SOON", getTaskStatus(star))
        assertEquals(1, getStatusPriority(star))
    }

    @Test
    fun `status - task due tomorrow shows ACTIVE`() {
        val star = createTestStar("Prepare presentation", dueMinutes = 1440f)
        assertEquals("ACTIVE", getTaskStatus(star))
        assertEquals(2, getStatusPriority(star))
    }

    @Test
    fun `status - completed task shows COMPLETE`() {
        val star = createTestStar("Finished task", status = "completed")
        assertEquals("COMPLETE", getTaskStatus(star))
        assertEquals(3, getStatusPriority(star)) // Lowest priority
    }

    @Test
    fun `status - task without due date shows NO DUE DATE`() {
        val star = createTestStar("Someday maybe task", dueMinutes = null)
        // When dueDate is null, Star.dueIn remains 0f (default)
        // So we check dueDate == null FIRST in getTaskStatus()
        assertEquals("NO DUE DATE", getTaskStatus(star))
        // Priority should be 4 (last) when no due date
        assertEquals(4, getStatusPriority(star))
    }

    // ============================================
    // TEST 3: Smart Sorting by Status
    // ============================================

    @Test
    fun `sorting - overdue tasks appear first`() {
        val tasks = listOf(
            createTestStar("Active task", dueMinutes = 1440f),
            createTestStar("Overdue task", dueMinutes = -30f),
            createTestStar("Due soon", dueMinutes = 60f)
        )

        val sorted = tasks.sortedWith(compareBy(
            { getStatusPriority(it) },
            { it.dueIn }
        ))

        assertEquals("Overdue task", sorted[0].title)
        assertEquals("Due soon", sorted[1].title)
        assertEquals("Active task", sorted[2].title)
    }

    @Test
    fun `sorting - completed tasks appear last`() {
        val tasks = listOf(
            createTestStar("Completed task", status = "completed"),
            createTestStar("Active task", dueMinutes = 1440f),
            createTestStar("Overdue task", dueMinutes = -30f)
        )

        val sorted = tasks.sortedWith(compareBy(
            { getStatusPriority(it) },
            { it.dueIn }
        ))

        assertEquals("Completed task", sorted.last().title)
    }

    @Test
    fun `sorting - multiple overdue sorted by time`() {
        val tasks = listOf(
            createTestStar("Overdue 1 hour", dueMinutes = -60f),
            createTestStar("Overdue 2 days", dueMinutes = -2880f),
            createTestStar("Overdue 10 min", dueMinutes = -10f)
        )

        val sorted = tasks.sortedWith(compareBy(
            { getStatusPriority(it) },
            { it.dueIn }
        ))

        // Most overdue (most negative) first
        assertEquals("Overdue 2 days", sorted[0].title)
        assertEquals("Overdue 1 hour", sorted[1].title)
        assertEquals("Overdue 10 min", sorted[2].title)
    }

    // ============================================
    // TEST 4: Time Display (Real-Time Countdown)
    // ============================================

    @Test
    fun `time display - overdue shows minutes`() {
        val star = createTestStar("Task", dueMinutes = -45f)
        val display = getTimeDisplay(star)
        assertEquals("45 min overdue", display)
    }

    @Test
    fun `time display - overdue shows hours and minutes`() {
        val star = createTestStar("Task", dueMinutes = -90f)
        val display = getTimeDisplay(star)
        assertEquals("1h 30m overdue", display)
    }

    @Test
    fun `time display - overdue shows days`() {
        val star = createTestStar("Task", dueMinutes = -1440f)
        val display = getTimeDisplay(star)
        assertEquals("1 days overdue", display)
    }

    @Test
    fun `time display - due soon shows minutes`() {
        val star = createTestStar("Task", dueMinutes = 45f)
        val display = getTimeDisplay(star)
        // Float precision might result in 44 or 45 minutes
        assertTrue("Display should show minutes",
            display == "Due in 45 min" || display == "Due in 44 min")
    }

    @Test
    fun `time display - due soon shows hours`() {
        val star = createTestStar("Task", dueMinutes = 90f)
        val display = getTimeDisplay(star)
        // Note: 90 minutes might round to "1h 29m" due to float precision
        // Both "1h 29m" and "1h 30m" are acceptable
        assertTrue("Display should show hours and minutes",
            display == "Due in 1h 30m" || display == "Due in 1h 29m")
    }

    @Test
    fun `time display - completed shows checkmark`() {
        val star = createTestStar("Task", status = "completed")
        val display = getTimeDisplay(star)
        assertEquals("✓ Completed", display)
    }

    // ============================================
    // TEST 5: Search + Filter Integration
    // ============================================

    @Test
    fun `integration - search and sort together`() {
        val tasks = listOf(
            createTestStar("Email manager about project", dueMinutes = -30f),
            createTestStar("Email client about invoice", dueMinutes = 60f),
            createTestStar("Call manager tomorrow", dueMinutes = 1440f)
        )

        // Search for "email"
        val filtered = tasks.filter { star ->
            fuzzyMatch(star.title.lowercase(), "email") ||
            star.title.contains("email", ignoreCase = true)
        }

        // Sort by status
        val sorted = filtered.sortedWith(compareBy(
            { getStatusPriority(it) },
            { it.dueIn }
        ))

        assertEquals(2, sorted.size)
        assertEquals("Email manager about project", sorted[0].title) // Overdue first
        assertEquals("Email client about invoice", sorted[1].title) // Due soon second
    }

    @Test
    fun `integration - empty search shows all tasks sorted`() {
        val tasks = listOf(
            createTestStar("Task C", dueMinutes = 1440f),
            createTestStar("Task A", dueMinutes = -30f),
            createTestStar("Task B", dueMinutes = 60f)
        )

        // Empty search = show all
        val filtered = tasks // No filter

        // Sort by status
        val sorted = filtered.sortedWith(compareBy(
            { getStatusPriority(it) },
            { it.dueIn }
        ))

        assertEquals(3, sorted.size)
        assertEquals("Task A", sorted[0].title) // Overdue
        assertEquals("Task B", sorted[1].title) // Due soon
        assertEquals("Task C", sorted[2].title) // Active
    }

    // ============================================
    // Helper Functions (Implementation Under Test)
    // ============================================

    /**
     * Fuzzy search: checks if all characters in query appear in text in order
     * Example: "emgr" matches "Email manager"
     */
    private fun fuzzyMatch(text: String, query: String): Boolean {
        if (query.isEmpty()) return true

        var queryIndex = 0
        for (char in text) {
            if (char.lowercaseChar() == query[queryIndex].lowercaseChar()) {
                queryIndex++
                if (queryIndex == query.length) return true
            }
        }
        return false
    }

    private fun getTaskStatus(star: Star): String {
        return when {
            star.isCompleted -> "COMPLETE"
            star.dueDate == null -> "NO DUE DATE" // Check null FIRST
            star.dueIn < 0 -> "OVERDUE"
            star.dueIn < 120 -> "DUE SOON" // < 2 hours
            else -> "ACTIVE"
        }
    }

    private fun getStatusPriority(star: Star): Int {
        return when {
            star.isCompleted -> 3 // Completed FIRST - always last in sort
            star.dueDate == null -> 4 // No due date - check BEFORE dueIn
            star.dueIn < 0 -> 0 // Overdue first
            star.dueIn < 120 -> 1 // Due soon second
            else -> 2 // Active task (has due date, not urgent)
        }
    }

    private fun getTimeDisplay(star: Star): String {
        return when {
            star.isCompleted -> "✓ Completed"
            star.dueIn < 0 -> {
                val overdue = (-star.dueIn).toInt()
                when {
                    overdue < 60 -> "$overdue min overdue"
                    overdue < 1440 -> "${overdue / 60}h ${overdue % 60}m overdue"
                    else -> "${overdue / 1440} days overdue"
                }
            }
            star.dueIn < 1440 -> {
                val minutes = star.dueIn.toInt()
                when {
                    minutes < 60 -> "Due in $minutes min"
                    minutes < 1440 -> "Due in ${minutes / 60}h ${minutes % 60}m"
                    else -> "Due in ${minutes / 1440} days"
                }
            }
            star.dueDate != null -> "Due later"
            else -> "No due date"
        }
    }
}
