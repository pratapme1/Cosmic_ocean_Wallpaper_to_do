package com.cosmicocean

import com.cosmicocean.model.Star
import com.cosmicocean.physics.ZoneManager
import com.cosmicocean.physics.VerletEngine
import com.cosmicocean.utils.LabelPositioning
import org.junit.Test
import org.junit.Assert.*

/**
 * Star Placement & Physics Test Suite
 * Following NO-GO workflow: Test REAL user scenarios
 *
 * User Issues:
 * 1. All stars drifting to bottom (zone forces not working)
 * 2. Long task names messy (no truncation/wrapping)
 * 3. Stars clustering (collision detection broken)
 */
class StarPlacementTest {

    companion object {
        const val SCREEN_WIDTH = 1080f
        const val SCREEN_HEIGHT = 2400f
        const val DELTA = 0.016f // 60fps
    }

    // ============================================
    // TEST 1: Zone Force Application
    // ============================================

    @Test
    fun `zone forces - P1 urgent stars should stay relatively stable (Epic 9)`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create P1 urgent star in middle of screen
        val star = createTestStar(
            x = SCREEN_WIDTH / 2,
            y = SCREEN_HEIGHT / 2,
            urgency = 1,
            dueMinutes = -30f // Overdue
        )

        val initialY = star.particle.y

        // Simulate 1 second of updates (60 frames)
        repeat(60) {
            zoneManager.update(listOf(star), DELTA)
        }

        val finalY = star.particle.y

        // P1 stars should NOT move significantly (random placement rule)
        val movement = kotlin.math.abs(finalY - initialY)
        assertTrue(
            "P1 star should stay stable. Movement: $movement",
            movement < 10.0f
        )
    }

    @Test
    fun `zone forces - P3 low priority stars should stay relatively stable (Epic 9)`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create P3 low priority star in middle of screen
        val star = createTestStar(
            x = SCREEN_WIDTH / 2,
            y = SCREEN_HEIGHT / 2,
            urgency = 3,
            dueMinutes = 2880f // 2 days away
        )

        val initialY = star.particle.y

        // Simulate 1 second of updates (60 frames)
        repeat(60) {
            zoneManager.update(listOf(star), DELTA)
        }

        val finalY = star.particle.y

        // P3 stars should NOT move significantly
        val movement = kotlin.math.abs(finalY - initialY)
        assertTrue(
            "P3 star should stay stable. Movement: $movement",
            movement < 10.0f
        )
    }

    @Test
    fun `zone forces - P2 stars with far due date should stay in middle`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create P2 star with due date > 24 hours
        val star = createTestStar(
            x = SCREEN_WIDTH / 2,
            y = SCREEN_HEIGHT / 2,
            urgency = 2,
            dueMinutes = 1500f // ~1 day away
        )

        val initialY = star.particle.y

        // Simulate 1 second of updates
        repeat(60) {
            zoneManager.update(listOf(star), DELTA)
        }

        val finalY = star.particle.y
        val movement = kotlin.math.abs(finalY - initialY)

        // P2 stars with far due date should have minimal movement
        assertTrue(
            "P2 star should stay relatively stable. Movement: $movement",
            movement < SCREEN_HEIGHT * 0.1f // Less than 10% screen height
        )
    }

    @Test
    fun `zone forces - throttling should not prevent updates`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)

        val star = createTestStar(
            x = SCREEN_WIDTH / 2,
            y = SCREEN_HEIGHT / 2,
            urgency = 1,
            dueMinutes = -30f
        )

        val initialY = star.particle.y

        // Simulate very small delta (< update interval)
        // Should still accumulate and eventually update
        repeat(100) {
            zoneManager.update(listOf(star), 0.001f) // 1ms
        }

        val finalY = star.particle.y

        // Even with small deltas, forces should eventually apply (or not, if forces are 0)
        // For Epic 9, forces are 0, so position should NOT change
        assertTrue(
            "Forces should be effectively zero. Initial: $initialY, Final: $finalY",
            kotlin.math.abs(finalY - initialY) < 1.0f
        )
    }

    // ============================================
    // TEST 2: Label Text Handling
    // ============================================

    @Test
    fun `label positioning - long task names should have limited width`() {
        val longTaskName = "This is a very long task name that should be wrapped or truncated to prevent messy display and overlap with other elements"

        val textSize = LabelPositioning.estimateTextSize(
            text = longTaskName,
            fontSize = 16f,
            maxWidth = SCREEN_WIDTH * 0.4f // Max 40% of screen width
        )

        // Width should be limited to max width
        assertTrue(
            "Label width should be limited. Width: ${textSize.width}, Max: ${SCREEN_WIDTH * 0.4f}",
            textSize.width <= SCREEN_WIDTH * 0.4f
        )
    }

    @Test
    fun `label positioning - should avoid screen edges`() {
        // Star near right edge
        val starX = SCREEN_WIDTH - 50f
        val starY = SCREEN_HEIGHT / 2

        val labelPos = LabelPositioning.calculateLabelPosition(
            starX = starX,
            starY = starY,
            labelWidth = 200f,
            labelHeight = 40f,
            screenWidth = SCREEN_WIDTH,
            screenHeight = SCREEN_HEIGHT,
            starRadius = 26f
        )

        // Label should not extend beyond screen
        assertTrue(
            "Label should not go off right edge. Label right edge: ${labelPos.x + 200f}",
            labelPos.x + 200f < SCREEN_WIDTH
        )

        // Label should stay within screen bounds
        assertTrue("Label X should be >= 0", labelPos.x >= 0)
        assertTrue("Label Y should be >= 0", labelPos.y >= 0)
    }

    @Test
    fun `label positioning - should handle star at top edge`() {
        val starX = SCREEN_WIDTH / 2
        val starY = 50f // Near top

        val labelPos = LabelPositioning.calculateLabelPosition(
            starX = starX,
            starY = starY,
            labelWidth = 150f,
            labelHeight = 40f,
            screenWidth = SCREEN_WIDTH,
            screenHeight = SCREEN_HEIGHT,
            starRadius = 26f
        )

        // Label should not go off top edge
        assertTrue(
            "Label should not go off top. Label Y: ${labelPos.y}",
            labelPos.y >= 20f // Padding
        )
    }

    // ============================================
    // TEST 3: Collision Detection & Spacing
    // ============================================

    @Test
    fun `collision - stars should maintain minimum distance`() {
        val engine = VerletEngine()
        engine.setBounds(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create two stars very close together
        val star1 = createTestStar(x = 500f, y = 500f, urgency = 2)
        val star2 = createTestStar(x = 520f, y = 500f, urgency = 2) // Only 20px apart

        engine.addParticle(star1.particle)
        engine.addParticle(star2.particle)

        val initialDistance = getDistance(star1, star2)

        // Run physics for 1 second
        repeat(60) {
            engine.update(DELTA)
        }

        val finalDistance = getDistance(star1, star2)

        // Stars should have moved apart due to repulsion
        assertTrue(
            "Stars should repel each other. Initial: $initialDistance, Final: $finalDistance",
            finalDistance > initialDistance
        )

        // Final distance should be at least sum of radii
        val minDistance = star1.particle.radius + star2.particle.radius
        assertTrue(
            "Stars should maintain minimum distance. Final: $finalDistance, Min: $minDistance",
            finalDistance >= minDistance * 0.9f // Allow 10% tolerance
        )
    }

    @Test
    fun `collision - multiple stars should distribute evenly`() {
        val engine = VerletEngine()
        engine.setBounds(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create 5 stars in same location (worst case clustering)
        val stars = List(5) { i ->
            createTestStar(
                x = SCREEN_WIDTH / 2 + i * 5f, // Slight offset
                y = SCREEN_HEIGHT / 2,
                urgency = 2
            )
        }

        stars.forEach { engine.addParticle(it.particle) }

        // Run physics for 3 seconds to allow distribution
        repeat(180) {
            engine.update(DELTA)
        }

        // Check that stars have spread out
        val avgDistance = stars.sumOf { s1 ->
            stars.filter { it !== s1 }.sumOf { s2 ->
                getDistance(s1, s2).toDouble()
            }
        } / (stars.size * (stars.size - 1))

        // Average distance should be reasonably large (not clustered)
        assertTrue(
            "Stars should distribute evenly. Average distance: $avgDistance",
            avgDistance > 80.0 // At least 80px average separation
        )
    }

    @Test
    fun `collision - stars should not overlap`() {
        val engine = VerletEngine()
        engine.setBounds(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create grid of stars to test clustering
        val stars = mutableListOf<Star>()
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                val star = createTestStar(
                    x = 300f + i * 100f,
                    y = 600f + j * 100f,
                    urgency = 2
                )
                stars.add(star)
                engine.addParticle(star.particle)
            }
        }

        // Run physics
        repeat(120) {
            engine.update(DELTA)
        }

        // Check no overlaps
        var overlaps = 0
        for (i in 0 until stars.size) {
            for (j in i + 1 until stars.size) {
                val dist = getDistance(stars[i], stars[j])
                val minDist = stars[i].particle.radius + stars[j].particle.radius
                if (dist < minDist) {
                    overlaps++
                }
            }
        }

        assertEquals(
            "Stars should not overlap. Overlaps found: $overlaps",
            0,
            overlaps
        )
    }

    // ============================================
    // TEST 4: Integration Tests
    // ============================================

    @Test
    fun `integration - stars should maintain position relative to each other (Epic 9)`() {
        val engine = VerletEngine()
        engine.setBounds(SCREEN_WIDTH, SCREEN_HEIGHT)
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)

        // Create P1 urgent star and P3 low priority star
        val urgentStar = createTestStar(
            x = SCREEN_WIDTH / 2,
            y = SCREEN_HEIGHT / 2,
            urgency = 1,
            dueMinutes = -30f
        )

        val lowPriorityStar = createTestStar(
            x = SCREEN_WIDTH / 2 + 50f,
            y = SCREEN_HEIGHT / 2,
            urgency = 3,
            dueMinutes = 2880f
        )

        engine.addParticle(urgentStar.particle)
        engine.addParticle(lowPriorityStar.particle)

        val stars = listOf(urgentStar, lowPriorityStar)
        val initialDiffY = urgentStar.particle.y - lowPriorityStar.particle.y

        // Run for 3 seconds
        repeat(180) {
            zoneManager.update(stars, DELTA)
            engine.update(DELTA)
        }
        
        val finalDiffY = urgentStar.particle.y - lowPriorityStar.particle.y

        // Should not have drastically changed relative positions (minor physics jitter ok)
        assertTrue(
            "Relative positions should be similar. Diff: ${kotlin.math.abs(finalDiffY - initialDiffY)}",
            kotlin.math.abs(finalDiffY - initialDiffY) < 20.0f
        )
    }

    @Test
    fun `safe area clamp - avoids reserved HUD zones`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)
        val reserved = listOf(
            ZoneManager.ZoneRect(
                left = SCREEN_WIDTH - 200f,
                top = 0f,
                right = SCREEN_WIDTH.toFloat(),
                bottom = 200f
            )
        )
        zoneManager.updateReservedZones(reserved)

        val (x, y) = zoneManager.clampToSafeArea(
            x = SCREEN_WIDTH - 100f,
            y = 100f,
            radius = 20f
        )

        val strictlyInside = x > reserved[0].left &&
            x < reserved[0].right &&
            y > reserved[0].top &&
            y < reserved[0].bottom
        assertFalse(
            "Clamped position should not remain strictly inside reserved zone",
            strictlyInside
        )
    }

    @Test
    fun `reserved zones - dragging stars are not pushed out`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)
        val reserved = listOf(
            ZoneManager.ZoneRect(
                left = 0f,
                top = SCREEN_HEIGHT / 2f - 200f,
                right = 200f,
                bottom = SCREEN_HEIGHT / 2f + 200f
            )
        )
        zoneManager.updateReservedZones(reserved)

        val star = createTestStar(
            x = 100f,
            y = SCREEN_HEIGHT / 2f,
            urgency = 2,
            dueMinutes = 60f
        )
        star.isDragging = true
        val beforeX = star.particle.x
        val beforeY = star.particle.y

        zoneManager.update(listOf(star), DELTA)

        assertEquals("Dragging star should keep X position", beforeX, star.particle.x, 0.01f)
        assertEquals("Dragging star should keep Y position", beforeY, star.particle.y, 0.01f)
    }

    // ============================================
    // Helper Functions
    // ============================================

    private fun createTestStar(
        x: Float,
        y: Float,
        urgency: Int = 2,
        dueMinutes: Float? = null
    ): Star {
        val dueDate = dueMinutes?.let {
            System.currentTimeMillis() + (it * 60 * 1000).toLong()
        }

        return Star(
            x = x,
            y = y,
            title = "Test Task",
            urgency = urgency,
            dueDate = dueDate
        ).apply {
            updateDueIn()
        }
    }

    private fun getDistance(star1: Star, star2: Star): Float {
        val dx = star2.particle.x - star1.particle.x
        val dy = star2.particle.y - star1.particle.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
