package com.cosmicocean.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cosmicocean.interactions.GestureState

/**
 * Swirl Visual Feedback Component
 * PWA-accurate: Growing circle + trail effect during swirl gesture
 */
@Composable
fun SwirlFeedback(
    gestureState: GestureState,
    modifier: Modifier = Modifier
) {
    if (!gestureState.isActive || gestureState.centerPoint == null) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        drawSwirlFeedback(gestureState, textMeasurer)
    }
}

/**
 * Draw swirl visual feedback
 */
private fun DrawScope.drawSwirlFeedback(state: GestureState, textMeasurer: TextMeasurer) {
    val center = state.centerPoint ?: return

    // Color scheme based on progress
    val baseColor = Color(0xFF64B5F6) // Light blue
    val successColor = Color(0xFF4CAF50) // Green
    val color = if (state.progress >= 0.9f) successColor else baseColor

    // Draw growing circle indicator
    drawCircle(
        color = color.copy(alpha = 0.3f * state.trailAlpha),
        radius = state.feedbackRadius,
        center = center,
        style = Stroke(width = 3f)
    )

    // Draw inner pulsing circle
    val innerRadius = state.feedbackRadius * 0.6f
    drawCircle(
        color = color.copy(alpha = 0.5f * state.trailAlpha),
        radius = innerRadius,
        center = center,
        style = Stroke(width = 2f)
    )

    // Draw path trail
    if (state.pathPoints.size > 1) {
        drawPathTrail(state.pathPoints, color, state.trailAlpha)
    }

    // Draw progress arc
    drawProgressArc(center, state.feedbackRadius, state.progress, color)

    // Draw direction arrows (every 90°)
    if (state.progress < 1.0f) {
        drawDirectionIndicators(center, state.feedbackRadius * 1.2f, color, state.trailAlpha)
    }

    // Draw snooze duration text
    if (state.snoozeDuration.isNotEmpty()) {
        val textStyle = TextStyle(
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        val measuredText = textMeasurer.measure(
            text = state.snoozeDuration,
            style = textStyle
        )

        val textX = center.x - measuredText.size.width / 2
        val textY = center.y - measuredText.size.height / 2

        // Background circle for text
        drawCircle(
            color = Color.Black.copy(alpha = 0.6f),
            radius = 25f,
            center = center
        )

        // Draw text
        drawText(
            textLayoutResult = measuredText,
            topLeft = Offset(textX, textY)
        )
    }
}

/**
 * Draw trail path behind touch
 */
private fun DrawScope.drawPathTrail(
    points: List<Offset>,
    color: Color,
    alpha: Float
) {
    if (points.size < 2) return

    val path = Path()
    path.moveTo(points[0].x, points[0].y)

    // Draw smooth curve through points
    for (i in 1 until points.size) {
        val point = points[i]

        // Calculate alpha gradient (fade older points)
        val pointAlpha = (i.toFloat() / points.size) * alpha

        // Draw line segment with varying alpha
        if (i < points.size - 1) {
            drawLine(
                color = color.copy(alpha = pointAlpha * 0.4f),
                start = points[i - 1],
                end = point,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Draw progress arc showing swirl completion
 */
private fun DrawScope.drawProgressArc(
    center: Offset,
    radius: Float,
    progress: Float,
    color: Color
) {
    val sweepAngle = 360f * progress

    drawArc(
        color = color.copy(alpha = 0.7f),
        startAngle = -90f, // Start at top
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(
            width = 4f,
            cap = StrokeCap.Round
        )
    )
}

/**
 * Draw directional arrow indicators
 */
private fun DrawScope.drawDirectionIndicators(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    val arrowSize = 8f
    val angles = listOf(0f, 90f, 180f, 270f)

    for (angle in angles) {
        val radians = Math.toRadians(angle.toDouble())
        val x = center.x + radius * kotlin.math.cos(radians).toFloat()
        val y = center.y + radius * kotlin.math.sin(radians).toFloat()

        // Draw small arrow pointing clockwise
        val arrowPath = Path().apply {
            // Arrow point
            moveTo(x, y)

            // Calculate arrow direction (tangent to circle, clockwise)
            val tangentAngle = angle + 90f
            val tangentRadians = Math.toRadians(tangentAngle.toDouble())

            val dx = kotlin.math.cos(tangentRadians).toFloat()
            val dy = kotlin.math.sin(tangentRadians).toFloat()

            // Arrow wings
            lineTo(x - dx * arrowSize - dy * arrowSize * 0.5f, y - dy * arrowSize + dx * arrowSize * 0.5f)
            moveTo(x, y)
            lineTo(x - dx * arrowSize + dy * arrowSize * 0.5f, y - dy * arrowSize - dx * arrowSize * 0.5f)
        }

        drawPath(
            path = arrowPath,
            color = color.copy(alpha = alpha * 0.6f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Success feedback when swirl completes
 */
@Composable
fun SwirlSuccessFeedback(
    center: Offset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw expanding success ring
        val successColor = Color(0xFF4CAF50)

        drawCircle(
            color = successColor.copy(alpha = 0.5f),
            radius = 80f,
            center = center,
            style = Stroke(width = 4f)
        )

        drawCircle(
            color = successColor.copy(alpha = 0.3f),
            radius = 100f,
            center = center,
            style = Stroke(width = 3f)
        )

        // Draw checkmark or success icon at center
        drawCircle(
            color = successColor.copy(alpha = 0.8f),
            radius = 20f,
            center = center
        )
    }
}
