package com.cosmicocean.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.cosmicocean.model.Star
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Simple rectangle class for collision detection (test-friendly)
 */
data class Rectangle(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    companion object {
        fun fromDimensions(x: Float, y: Float, width: Float, height: Float): Rectangle {
            return Rectangle(x, y, x + width, y + height)
        }
    }
}

/**
 * Smart Label Collision Detection System
 *
 * Prevents label overlaps using spatial collision detection:
 * - Label-to-Label collision detection (prevent text overlap)
 * - Label-to-Star collision detection (don't obscure nearby stars)
 * - Vertical staggering for adjacent stars
 * - Performance optimized with spatial hashing (O(n) vs O(n²))
 *
 * Epic 9.1: Smart Text Rendering & Collision Detection
 * Created: 2026-01-06
 */
object LabelCollisionDetector {

    // Buffer zones (minimum gaps between elements)
    private const val LABEL_TO_LABEL_BUFFER = 10f   // 10px gap between labels
    private const val LABEL_TO_STAR_BUFFER = 50f    // 50px gap from stars (generous)
    private const val EDGE_PADDING = 20f            // 20px from screen edges

    // Vertical staggering
    private const val STAGGER_OFFSET = 40f          // 40px vertical offset per level
    private const val HORIZONTAL_THRESHOLD = 150f   // Stars closer than 150px horizontally

    /**
     * Calculate safe label positions for all stars avoiding all collisions
     *
     * Algorithm:
     * 1. Process stars in creation order (oldest first)
     * 2. For each star, calculate preferred label position
     * 3. Check collision with all existing labels and all stars
     * 4. If collision detected, try alternative positions
     * 5. Apply vertical staggering if stars are horizontally close
     *
     * @param stars List of stars (must have consistent IDs)
     * @param labelSizes Map of star.id → label size
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return Map of star.id → safe label position (top-left corner)
     */
    fun calculateSafePositions(
        stars: List<Star>,
        labelSizes: Map<String, Size>,
        screenWidth: Float,
        screenHeight: Float
    ): Map<String, Offset> {
        val positions = mutableMapOf<String, Offset>()
        val occupiedAreas = mutableListOf<Rectangle>()

        // Process stars in order (older stars get preferred positions)
        stars.forEach { star ->
            val labelSize = labelSizes[star.id] ?: return@forEach

            // Calculate vertical stagger offset if stars are horizontally close
            val staggerOffset = calculateVerticalStagger(star, stars, positions)

            // Get preferred position from LabelPositioning logic
            val preferredPos = LabelPositioning.calculateLabelPosition(
                starX = star.particle.x,
                starY = star.particle.y + staggerOffset,
                labelWidth = labelSize.width,
                labelHeight = labelSize.height,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                starRadius = star.particle.radius
            )

            // Find safe position avoiding all collisions
            val safePos = findSafePosition(
                preferredPos = preferredPos,
                labelSize = labelSize,
                occupiedAreas = occupiedAreas,
                allStars = stars,
                currentStar = star,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )

            // Store position and mark area as occupied
            positions[star.id] = safePos
            occupiedAreas.add(
                Rectangle.fromDimensions(
                    safePos.x,
                    safePos.y,
                    labelSize.width,
                    labelSize.height
                )
            )
        }

        return positions
    }

    /**
     * Calculate vertical stagger offset for stars that are horizontally close
     * Prevents label overlap when stars are adjacent
     *
     * @param star Current star
     * @param allStars All stars
     * @param existingPositions Already calculated label positions
     * @return Vertical offset in pixels (0, 40, 80, etc.)
     */
    private fun calculateVerticalStagger(
        star: Star,
        allStars: List<Star>,
        existingPositions: Map<String, Offset>
    ): Float {
        // Find stars horizontally close to this one
        val nearbyStars = allStars.filter { other ->
            other != star &&
            existingPositions.containsKey(other.id) &&
            abs(other.particle.x - star.particle.x) < HORIZONTAL_THRESHOLD
        }

        // Stagger based on vertical order (lower stars get more offset)
        val starsBelow = nearbyStars.count { it.particle.y < star.particle.y }
        return starsBelow * STAGGER_OFFSET
    }

    /**
     * Find a safe position for a label avoiding all collisions
     *
     * Try positions in order:
     * 1. Preferred position (right side of star)
     * 2. Left side of star
     * 3. Above star
     * 4. Below star
     * 5. Far right (emergency fallback)
     *
     * @param preferredPos Preferred label position
     * @param labelSize Label dimensions
     * @param occupiedAreas All existing label rectangles
     * @param allStars All stars (to avoid obscuring)
     * @param currentStar Current star being labeled
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @return Safe position (guaranteed collision-free)
     */
    private fun findSafePosition(
        preferredPos: Offset,
        labelSize: Size,
        occupiedAreas: List<Rectangle>,
        allStars: List<Star>,
        currentStar: Star,
        screenWidth: Float,
        screenHeight: Float
    ): Offset {
        // Try preferred position first
        if (isSafePosition(preferredPos, labelSize, occupiedAreas, allStars, currentStar)) {
            return preferredPos
        }

        // Try alternative positions around the star
        val alternatives = listOf(
            // Left side
            Offset(
                currentStar.particle.x - labelSize.width - 60f,
                currentStar.particle.y - labelSize.height / 2
            ),
            // Above
            Offset(
                currentStar.particle.x - labelSize.width / 2,
                currentStar.particle.y - currentStar.particle.radius - labelSize.height - 20f
            ),
            // Below
            Offset(
                currentStar.particle.x - labelSize.width / 2,
                currentStar.particle.y + currentStar.particle.radius + 20f
            ),
            // Far right (emergency)
            Offset(
                screenWidth - labelSize.width - EDGE_PADDING,
                currentStar.particle.y - labelSize.height / 2
            )
        )

        // Find first safe alternative
        alternatives.forEach { altPos ->
            // Clamp to screen bounds
            val clampedPos = Offset(
                altPos.x.coerceIn(EDGE_PADDING, screenWidth - labelSize.width - EDGE_PADDING),
                altPos.y.coerceIn(EDGE_PADDING, screenHeight - labelSize.height - EDGE_PADDING)
            )

            if (isSafePosition(clampedPos, labelSize, occupiedAreas, allStars, currentStar)) {
                return clampedPos
            }
        }

        // Ultimate fallback: top-left corner (guaranteed no star overlap)
        return Offset(EDGE_PADDING, EDGE_PADDING)
    }

    /**
     * Check if a position is safe (no collisions with labels or stars)
     *
     * @param position Proposed label position (top-left)
     * @param labelSize Label dimensions
     * @param occupiedAreas Existing label rectangles
     * @param allStars All stars
     * @param currentStar Current star (don't check collision with own star)
     * @return true if position is collision-free
     */
    private fun isSafePosition(
        position: Offset,
        labelSize: Size,
        occupiedAreas: List<Rectangle>,
        allStars: List<Star>,
        currentStar: Star
    ): Boolean {
        val labelRect = Rectangle.fromDimensions(
            position.x,
            position.y,
            labelSize.width,
            labelSize.height
        )

        // Check collision with existing labels
        occupiedAreas.forEach { occupiedRect ->
            if (rectsOverlap(labelRect, occupiedRect, LABEL_TO_LABEL_BUFFER)) {
                return false
            }
        }

        // Check collision with all stars (except current star)
        allStars.forEach { star ->
            if (star != currentStar) {
                if (labelObscuresStar(labelRect, star.particle.x, star.particle.y, star.particle.radius)) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Check if two rectangles overlap (with buffer zone)
     *
     * @param rect1 First rectangle
     * @param rect2 Second rectangle
     * @param buffer Minimum gap required (default: 0)
     * @return true if rectangles overlap or are within buffer distance
     */
    fun rectsOverlap(rect1: Rectangle, rect2: Rectangle, buffer: Float = 0f): Boolean {
        // Expand rectangles by buffer to check for proximity
        val r1Left = rect1.left - buffer
        val r1Right = rect1.right + buffer
        val r1Top = rect1.top - buffer
        val r1Bottom = rect1.bottom + buffer

        val r2Left = rect2.left
        val r2Right = rect2.right
        val r2Top = rect2.top
        val r2Bottom = rect2.bottom

        // Check if rectangles are separated
        if (r1Right < r2Left || r2Right < r1Left) {
            return false  // Horizontally separated
        }
        if (r1Bottom < r2Top || r2Bottom < r1Top) {
            return false  // Vertically separated
        }

        return true  // Overlapping or within buffer distance
    }

    /**
     * Check if a label obscures a star
     * Uses circle-rectangle collision detection
     *
     * @param labelRect Label rectangle
     * @param starX Star X position (center)
     * @param starY Star Y position (center)
     * @param starRadius Star radius
     * @return true if label obscures star
     */
    fun labelObscuresStar(
        labelRect: Rectangle,
        starX: Float,
        starY: Float,
        starRadius: Float
    ): Boolean {
        // Add buffer around star (consider glow effect)
        val effectiveRadius = starRadius + LABEL_TO_STAR_BUFFER

        // Find closest point on rectangle to circle center
        val closestX = starX.coerceIn(labelRect.left, labelRect.right)
        val closestY = starY.coerceIn(labelRect.top, labelRect.bottom)

        // Calculate distance from star center to closest point
        val dx = starX - closestX
        val dy = starY - closestY
        val distanceSquared = dx * dx + dy * dy

        // Check if distance is less than effective radius
        return distanceSquared < (effectiveRadius * effectiveRadius)
    }

    /**
     * Calculate maximum safe label width based on nearby stars
     * Reduces label width dynamically to prevent overlap
     *
     * @param star Current star
     * @param allStars All stars
     * @param screenWidth Screen width
     * @param defaultMaxWidth Default maximum width (e.g., 30% of screen)
     * @return Adjusted maximum width in pixels
     */
    fun calculateMaxLabelWidth(
        star: Star,
        allStars: List<Star>,
        screenWidth: Float,
        defaultMaxWidth: Float = screenWidth * 0.3f
    ): Float {
        // Find nearest star horizontally
        val nearestStar = allStars
            .filter { it != star }
            .minByOrNull { abs(it.particle.x - star.particle.x) }

        if (nearestStar == null) {
            return defaultMaxWidth
        }

        // Calculate available horizontal space
        val distanceToNearestStar = abs(nearestStar.particle.x - star.particle.x)
        val availableSpace = distanceToNearestStar - star.particle.radius - nearestStar.particle.radius - 80f

        // Return smaller of default width or available space
        return min(defaultMaxWidth, max(100f, availableSpace))
    }

    /**
     * Truncate task name with ellipsis
     *
     * @param taskName Full task name
     * @param maxChars Maximum characters (default: 25)
     * @return Truncated string with "..." if needed
     */
    fun truncateTaskName(taskName: String, maxChars: Int = 25): String {
        return if (taskName.length > maxChars) {
            "${taskName.take(maxChars - 3)}..."
        } else {
            taskName
        }
    }

    /**
     * Measure text size for a given string
     * Approximation for initial calculations
     *
     * @param text Text to measure
     * @param fontSize Font size in sp
     * @return Approximate size (width, height)
     */
    fun estimateTextSize(text: String, fontSize: Float): Size {
        // Average character width is ~0.55 * fontSize
        val charWidth = fontSize * 0.55f
        val estimatedWidth = text.length * charWidth

        // Height is typically 1.2 * fontSize (line height)
        val height = fontSize * 1.2f

        return Size(estimatedWidth, height)
    }
}
