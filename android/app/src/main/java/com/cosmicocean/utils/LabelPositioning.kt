package com.cosmicocean.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

/**
 * Smart label positioning system
 * Ensures labels don't get clipped by screen edges
 */
object LabelPositioning {

    /**
     * Calculate optimal label position avoiding screen edges
     * PWA-accurate implementation
     *
     * @param starX Star X position
     * @param starY Star Y position
     * @param labelWidth Label width in pixels
     * @param labelHeight Label height in pixels
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param starRadius Star radius for offset calculation
     * @return Offset for label top-left corner
     */
    fun calculateLabelPosition(
        starX: Float,
        starY: Float,
        labelWidth: Float,
        labelHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        starRadius: Float
    ): Offset {
        val padding = 20f // Minimum distance from edges
        val offset = 60f  // Distance from star center

        // Try right side first (preferred position)
        var x = starX + offset
        var y = starY - labelHeight / 2

        // Check if clipped on right
        if (x + labelWidth + padding > screenWidth) {
            // Try left side
            x = starX - labelWidth - offset
        }

        // Check if still clipped on left
        if (x < padding) {
            // Center label horizontally at star
            x = starX - labelWidth / 2
            // Clamp to screen bounds
            x = x.coerceIn(padding, screenWidth - labelWidth - padding)
        }

        // Vertical bounds checking
        if (y < padding) {
            // Too high - move below star
            y = starY + starRadius + 10f
        } else if (y + labelHeight + padding > screenHeight) {
            // Too low - move above star
            y = starY - starRadius - labelHeight - 10f
        }

        // Final vertical clamp
        y = y.coerceIn(padding, screenHeight - labelHeight - padding)

        return Offset(x, y)
    }

    /**
     * Calculate label anchor point for smooth transitions
     */
    fun calculateLabelAnchor(
        starX: Float,
        starY: Float,
        labelX: Float,
        labelY: Float,
        labelHeight: Float
    ): LabelAnchor {
        val isRight = labelX > starX
        val isBelow = labelY > starY

        return when {
            isRight && !isBelow -> LabelAnchor.RIGHT
            !isRight && !isBelow -> LabelAnchor.LEFT
            isBelow && labelX < starX -> LabelAnchor.BOTTOM_LEFT
            isBelow -> LabelAnchor.BOTTOM_RIGHT
            else -> LabelAnchor.RIGHT
        }
    }

    /**
     * Measure text size for positioning calculations
     * Approximation based on text length and font size
     */
    fun estimateTextSize(
        text: String,
        fontSize: Float,
        maxWidth: Float = Float.MAX_VALUE
    ): Size {
        // Rough estimate: average character width is ~0.55 * fontSize
        val charWidth = fontSize * 0.55f
        val estimatedWidth = (text.length * charWidth).coerceAtMost(maxWidth)

        // Height is typically 1.2 * fontSize to account for line height
        val height = fontSize * 1.2f

        return Size(estimatedWidth, height)
    }

    /**
     * Get connection point on star for label line
     */
    fun getConnectionPoint(
        starX: Float,
        starY: Float,
        starRadius: Float,
        anchor: LabelAnchor
    ): Offset {
        val offset = starRadius + 5f

        return when (anchor) {
            LabelAnchor.RIGHT -> Offset(starX + offset, starY)
            LabelAnchor.LEFT -> Offset(starX - offset, starY)
            LabelAnchor.BOTTOM_LEFT -> Offset(
                starX - offset * 0.7f,
                starY + offset * 0.7f
            )
            LabelAnchor.BOTTOM_RIGHT -> Offset(
                starX + offset * 0.7f,
                starY + offset * 0.7f
            )
        }
    }
}

/**
 * Label anchor position relative to star
 */
enum class LabelAnchor {
    RIGHT,
    LEFT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}
