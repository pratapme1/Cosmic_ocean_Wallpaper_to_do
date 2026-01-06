package com.cosmicocean.utils

import androidx.compose.ui.geometry.Size
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for LabelCollisionDetector
 *
 * Tests collision detection algorithms:
 * - Rectangle overlap detection
 * - Label-to-star collision detection
 * - Safe position calculation
 *
 * Epic 9.1: Smart Text Rendering & Collision Detection
 * Created: 2026-01-06
 */
class LabelCollisionDetectorTest {

    // ========== Rectangle Overlap Tests ==========

    @Test
    fun testRectsOverlap_NoCollision_HorizontallySeparated() {
        val rect1 = Rectangle(0f, 0f, 100f, 50f)
        val rect2 = Rectangle(150f, 0f, 100f, 50f)  // left=150, right=250

        val result = LabelCollisionDetector.rectsOverlap(rect1, rect2)

        assertFalse("Horizontally separated rects should NOT overlap", result)
    }

    @Test
    fun testRectsOverlap_NoCollision_VerticallySeparated() {
        val rect1 = Rectangle(0f, 0f, 100f, 50f)
        val rect2 = Rectangle(0f, 100f, 100f, 150f)

        val result = LabelCollisionDetector.rectsOverlap(rect1, rect2)

        assertFalse("Vertically separated rects should NOT overlap", result)
    }

    @Test
    fun testRectsOverlap_WithCollision_PartialOverlap() {
        val rect1 = Rectangle(0f, 0f, 100f, 50f)
        val rect2 = Rectangle(50f, 0f, 150f, 50f)  // Overlaps 50-100px

        val result = LabelCollisionDetector.rectsOverlap(rect1, rect2)

        assertTrue("Partially overlapping rects should collide", result)
    }

    @Test
    fun testRectsOverlap_WithCollision_CompleteOverlap() {
        val rect1 = Rectangle(0f, 0f, 100f, 50f)
        val rect2 = Rectangle(20f, 10f, 80f, 40f)  // Inside rect1

        val result = LabelCollisionDetector.rectsOverlap(rect1, rect2)

        assertTrue("Nested rects should collide", result)
    }

    @Test
    fun testRectsOverlap_WithBuffer_JustTouching() {
        val rect1 = Rectangle(0f, 0f, 100f, 50f)
        val rect2 = Rectangle(105f, 0f, 205f, 50f)  // 5px gap

        // Without buffer: should NOT overlap
        val withoutBuffer = LabelCollisionDetector.rectsOverlap(rect1, rect2, buffer = 0f)
        assertFalse("Rects with 5px gap should NOT overlap (no buffer)", withoutBuffer)

        // With 10px buffer: should overlap
        val withBuffer = LabelCollisionDetector.rectsOverlap(rect1, rect2, buffer = 10f)
        assertTrue("Rects with 5px gap should overlap (10px buffer)", withBuffer)
    }

    @Test
    fun testRectsOverlap_EdgeCase_IdenticalRects() {
        val rect1 = Rectangle(0f, 0f, 100f, 50f)
        val rect2 = Rectangle(0f, 0f, 100f, 50f)

        val result = LabelCollisionDetector.rectsOverlap(rect1, rect2)

        assertTrue("Identical rects should overlap", result)
    }

    // ========== Label-to-Star Collision Tests ==========

    @Test
    fun testLabelObscuresStar_StarCenterInsideLabel() {
        val labelRect = Rectangle(100f, 100f, 200f, 150f)
        val starX = 150f
        val starY = 125f  // Center of label
        val starRadius = 30f

        val result = LabelCollisionDetector.labelObscuresStar(labelRect, starX, starY, starRadius)

        assertTrue("Label should obscure star when center is inside", result)
    }

    @Test
    fun testLabelObscuresStar_StarFarAway() {
        val labelRect = Rectangle(100f, 100f, 200f, 150f)
        val starX = 500f  // Far right
        val starY = 500f  // Far down
        val starRadius = 30f

        val result = LabelCollisionDetector.labelObscuresStar(labelRect, starX, starY, starRadius)

        assertFalse("Label should NOT obscure far-away star", result)
    }

    @Test
    fun testLabelObscuresStar_StarJustOutside() {
        val labelRect = Rectangle(100f, 100f, 200f, 150f)
        val starX = 250f  // 50px from right edge
        val starY = 125f
        val starRadius = 20f
        // Effective radius = 20 + 50 (buffer) = 70px
        // Distance to closest point (200px) = 50px
        // 50px < 70px → should collide

        val result = LabelCollisionDetector.labelObscuresStar(labelRect, starX, starY, starRadius)

        assertTrue("Label should obscure star within buffer zone", result)
    }

    @Test
    fun testLabelObscuresStar_StarBarelyOutsideBuffer() {
        val labelRect = Rectangle(100f, 100f, 200f, 150f)
        val starX = 280f  // 80px from right edge
        val starY = 125f
        val starRadius = 20f
        // Effective radius = 20 + 50 (buffer) = 70px
        // Distance to closest point (200px) = 80px
        // 80px > 70px → should NOT collide

        val result = LabelCollisionDetector.labelObscuresStar(labelRect, starX, starY, starRadius)

        assertFalse("Label should NOT obscure star outside buffer zone", result)
    }

    @Test
    fun testLabelObscuresStar_StarAboveLabel() {
        val labelRect = Rectangle(100f, 100f, 200f, 150f)
        val starX = 150f
        val starY = 20f  // Above label
        val starRadius = 15f

        val result = LabelCollisionDetector.labelObscuresStar(labelRect, starX, starY, starRadius)

        assertFalse("Label should NOT obscure star above (>50px buffer)", result)
    }

    // ========== Truncation Tests ==========

    @Test
    fun testTruncateTaskName_ShortName() {
        val taskName = "Email client"
        val result = LabelCollisionDetector.truncateTaskName(taskName, maxChars = 25)

        assertEquals("Short name should not be truncated", "Email client", result)
    }

    @Test
    fun testTruncateTaskName_ExactlyMaxChars() {
        val taskName = "A".repeat(25)
        val result = LabelCollisionDetector.truncateTaskName(taskName, maxChars = 25)

        assertEquals("Name at max length should not be truncated", taskName, result)
    }

    @Test
    fun testTruncateTaskName_LongName() {
        val taskName = "Email manager about project status update for Q4 deliverables"
        val result = LabelCollisionDetector.truncateTaskName(taskName, maxChars = 25)

        assertEquals("Long name should be truncated with ellipsis",
            "Email manager about pr...", result)
        assertTrue("Truncated name should end with ...", result.endsWith("..."))
        assertTrue("Truncated name should be ≤25 chars", result.length <= 25)
    }

    @Test
    fun testTruncateTaskName_CustomMaxChars() {
        val taskName = "This is a very long task name"
        val result = LabelCollisionDetector.truncateTaskName(taskName, maxChars = 15)

        assertEquals("Should truncate to custom length", "This is a ve...", result)
        assertEquals("Should be exactly 15 chars", 15, result.length)
    }

    // ========== Text Size Estimation Tests ==========

    @Test
    fun testEstimateTextSize_ShortText() {
        val text = "Hello"
        val fontSize = 14f

        val result = LabelCollisionDetector.estimateTextSize(text, fontSize)

        // Expected: 5 chars * 0.55 * 14 = 38.5px width
        assertTrue("Width should be ~38.5px", result.width > 35f && result.width < 42f)
        // Expected: 14 * 1.2 = 16.8px height
        assertTrue("Height should be ~16.8px", result.height > 15f && result.height < 18f)
    }

    @Test
    fun testEstimateTextSize_LongText() {
        val text = "Email manager about project"
        val fontSize = 14f

        val result = LabelCollisionDetector.estimateTextSize(text, fontSize)

        // Expected: 27 chars * 0.55 * 14 = 208px width (approx)
        assertTrue("Width should be ~208px", result.width > 200f && result.width < 220f)
        assertTrue("Height should be ~16.8px", result.height > 15f && result.height < 18f)
    }

    // ========== Max Label Width Tests ==========

    @Test
    fun testCalculateMaxLabelWidth_NoNearbyStars() {
        // Single star case - should use default max width
        val star = createTestStar(x = 500f, y = 500f)
        val allStars = listOf(star)
        val screenWidth = 1080f
        val defaultMaxWidth = screenWidth * 0.3f  // 324px

        val result = LabelCollisionDetector.calculateMaxLabelWidth(
            star, allStars, screenWidth, defaultMaxWidth
        )

        assertEquals("Should use default max width when no nearby stars",
            defaultMaxWidth, result, 0.1f)
    }

    @Test
    fun testCalculateMaxLabelWidth_NearbyStarLimitsWidth() {
        val star1 = createTestStar(x = 200f, y = 500f, radius = 30f)
        val star2 = createTestStar(x = 400f, y = 500f, radius = 30f)  // 200px away
        val allStars = listOf(star1, star2)
        val screenWidth = 1080f
        val defaultMaxWidth = screenWidth * 0.3f  // 324px

        val result = LabelCollisionDetector.calculateMaxLabelWidth(
            star1, allStars, screenWidth, defaultMaxWidth
        )

        // Available space = 200 - 30 - 30 - 80 = 60px (less than default)
        // But minimum is 100px, so should return 100px
        assertEquals("Should limit width based on nearby star (min 100px)",
            100f, result, 0.1f)
    }

    // ========== Helper Methods ==========

    /**
     * Create a test star with specified position and radius
     */
    private fun createTestStar(
        x: Float,
        y: Float,
        radius: Float = 30f,
        title: String = "Test Task"
    ): com.cosmicocean.model.Star {
        return com.cosmicocean.model.Star(
            x = x,
            y = y,
            title = title,
            urgency = 2,
            dueDate = null
        ).apply {
            // Set particle radius manually for testing
            particle.radius = radius
        }
    }
}
