package com.cosmicocean.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.cosmicocean.data.StarEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * LocalWallpaperGenerator - On-Device Wallpaper Generation
 *
 * Generates wallpaper locally without network dependency.
 * Ported from backend/services/wallpaper-generator-enhanced.js
 *
 * Features:
 * - Gradient backgrounds based on urgency
 * - Particle systems (stars/bubbles)
 * - Task display with circular indicator
 * - Time-based theming
 */
object LocalWallpaperGenerator {
    private const val TAG = "LocalWallpaperGen"

    // Particle system constants
    private const val STAR_COUNT = 50
    private const val PARTICLE_MIN_SIZE = 1f
    private const val PARTICLE_MAX_SIZE = 3f

    /**
     * Generate wallpaper bitmap
     */
    fun generate(
        task: StarEntity?,
        theme: WallpaperTheme,
        width: Int,
        height: Int
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper: ${width}x${height}, theme=$theme, task=${task?.title}")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Calculate urgency
        val urgency = calculateUrgency(task)
        val colors = theme.getColors(urgency)

        // Layer 1: Gradient background
        drawGradientBackground(canvas, colors, width, height)

        // Layer 2: Particle system
        drawParticles(canvas, colors, width, height, theme)

        // Layer 3: Task display
        if (task != null) {
            drawTaskCard(canvas, task, colors, width, height, urgency)
        } else {
            drawClearState(canvas, colors, width, height)
        }

        // Layer 4: Time display (optional)
        drawTimeDisplay(canvas, width, height)

        return bitmap
    }

    /**
     * Calculate urgency level from task
     */
    private fun calculateUrgency(task: StarEntity?): UrgencyLevel {
        if (task == null) return UrgencyLevel.CLEAR

        val dueDate = task.dueDate ?: return UrgencyLevel.CALM

        val now = System.currentTimeMillis()
        val hoursUntilDue = (dueDate - now) / (1000 * 60 * 60)

        return when {
            hoursUntilDue < 0 -> UrgencyLevel.CRITICAL    // Overdue
            hoursUntilDue < 4 -> UrgencyLevel.CRITICAL    // Due in 4 hours
            hoursUntilDue < 24 -> UrgencyLevel.URGENT     // Due today
            hoursUntilDue < 48 -> UrgencyLevel.ATTENTION  // Due tomorrow
            else -> UrgencyLevel.CALM
        }
    }

    /**
     * Draw gradient background
     */
    private fun drawGradientBackground(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val gradient = LinearGradient(
            0f, 0f,
            0f, height.toFloat(),
            colors.gradientStart,
            colors.gradientEnd,
            Shader.TileMode.CLAMP
        )

        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * Draw particle system (stars, bubbles, etc.)
     */
    private fun drawParticles(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int,
        theme: WallpaperTheme
    ) {
        val random = Random(System.currentTimeMillis() / 60000) // Changes every minute
        val paint = Paint().apply {
            isAntiAlias = true
        }

        for (i in 0 until STAR_COUNT) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val size = PARTICLE_MIN_SIZE + random.nextFloat() * (PARTICLE_MAX_SIZE - PARTICLE_MIN_SIZE)
            val alpha = (100 + random.nextInt(155))

            paint.color = colors.particleColor
            paint.alpha = alpha

            when (theme) {
                WallpaperTheme.COSMIC -> {
                    // Draw as star (4-point)
                    drawStar(canvas, x, y, size, paint)
                }
                WallpaperTheme.OCEAN -> {
                    // Draw as bubble
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawCircle(x, y, size * 1.5f, paint)
                    paint.style = Paint.Style.FILL
                }
                WallpaperTheme.FANTASY -> {
                    // Draw as sparkle
                    canvas.drawCircle(x, y, size, paint)
                    if (random.nextFloat() > 0.7f) {
                        // Add glow to some particles
                        paint.alpha = alpha / 3
                        canvas.drawCircle(x, y, size * 2, paint)
                    }
                }
            }
        }
    }

    /**
     * Draw a 4-point star
     */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // Horizontal line
        canvas.drawLine(cx - size, cy, cx + size, cy, paint)
        // Vertical line
        canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        // Center dot
        canvas.drawCircle(cx, cy, size * 0.3f, paint)
    }

    /**
     * Draw task card with circular indicator
     */
    private fun drawTaskCard(
        canvas: Canvas,
        task: StarEntity,
        colors: ThemeColors,
        width: Int,
        height: Int,
        urgency: UrgencyLevel
    ) {
        val centerX = width / 2f
        val centerY = height * 0.35f  // Upper third for lock screen visibility

        // Task circle radius based on urgency
        val baseRadius = width * 0.12f
        val circleRadius = when (urgency) {
            UrgencyLevel.CRITICAL -> baseRadius * 1.3f
            UrgencyLevel.URGENT -> baseRadius * 1.15f
            else -> baseRadius
        }

        // Draw glow
        val glowPaint = Paint().apply {
            isAntiAlias = true
            color = colors.taskCircleGlow
            alpha = 60
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, circleRadius * 1.4f, glowPaint)

        // Draw main circle
        val circlePaint = Paint().apply {
            isAntiAlias = true
            color = colors.taskCircle
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

        // Draw priority indicator (inner ring for critical/urgent)
        if (urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.URGENT) {
            val ringPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                alpha = 200
                style = Paint.Style.STROKE
                strokeWidth = circleRadius * 0.08f
            }
            canvas.drawCircle(centerX, centerY, circleRadius * 0.75f, ringPaint)
        }

        // Draw task title
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = width * 0.045f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val maxWidth = (width * 0.8f).toInt()
        val titleLayout = StaticLayout.Builder
            .obtain(task.title, 0, task.title.length, titlePaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        val titleY = centerY + circleRadius + (width * 0.08f)
        canvas.save()
        canvas.translate(centerX, titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // Draw due date/time
        val dueDateText = formatDueDate(task.dueDate)
        if (dueDateText != null) {
            val datePaint = Paint().apply {
                isAntiAlias = true
                color = colors.subtitleColor
                textSize = width * 0.032f
                textAlign = Paint.Align.CENTER
            }

            val dateY = titleY + titleLayout.height + (width * 0.04f)
            canvas.drawText(dueDateText, centerX, dateY, datePaint)

            // Draw urgency label for critical/urgent
            if (urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.URGENT) {
                val labelText = if (urgency == UrgencyLevel.CRITICAL) "OVERDUE" else "DUE SOON"
                val labelPaint = Paint().apply {
                    isAntiAlias = true
                    color = colors.taskCircle
                    textSize = width * 0.028f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(labelText, centerX, dateY + (width * 0.05f), labelPaint)
            }
        }
    }

    /**
     * Draw clear state (no tasks)
     */
    private fun drawClearState(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val centerX = width / 2f
        val centerY = height * 0.4f

        // Draw checkmark circle
        val circleRadius = width * 0.1f
        val circlePaint = Paint().apply {
            isAntiAlias = true
            color = colors.taskCircle
            alpha = 180
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

        // Draw checkmark
        val checkPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = circleRadius * 0.15f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val checkSize = circleRadius * 0.5f
        canvas.drawLine(
            centerX - checkSize * 0.5f,
            centerY,
            centerX - checkSize * 0.1f,
            centerY + checkSize * 0.4f,
            checkPaint
        )
        canvas.drawLine(
            centerX - checkSize * 0.1f,
            centerY + checkSize * 0.4f,
            centerX + checkSize * 0.5f,
            centerY - checkSize * 0.3f,
            checkPaint
        )

        // Draw message
        val messagePaint = Paint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = width * 0.04f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "All clear!",
            centerX,
            centerY + circleRadius + (width * 0.08f),
            messagePaint
        )

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = width * 0.03f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Enjoy your moment",
            centerX,
            centerY + circleRadius + (width * 0.13f),
            subtitlePaint
        )
    }

    /**
     * Draw current time (top of screen)
     */
    private fun drawTimeDisplay(canvas: Canvas, width: Int, height: Int) {
        // Note: Lock screen already shows time, so we keep this subtle
        // This is optional and can be disabled
    }

    /**
     * Format due date for display
     */
    private fun formatDueDate(dueDateMs: Long?): String? {
        if (dueDateMs == null) return null

        val now = System.currentTimeMillis()
        val diff = dueDateMs - now

        // Already passed
        if (diff < 0) {
            val hoursAgo = -diff / (1000 * 60 * 60)
            return when {
                hoursAgo < 1 -> "Overdue"
                hoursAgo < 24 -> "Overdue by ${hoursAgo}h"
                else -> "Overdue by ${hoursAgo / 24}d"
            }
        }

        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60

        return when {
            hours < 1 -> "Due in ${minutes}m"
            hours < 24 -> "Due in ${hours}h ${if (minutes > 0) "${minutes}m" else ""}"
            hours < 48 -> "Due tomorrow"
            else -> {
                val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                "Due ${dateFormat.format(Date(dueDateMs))}"
            }
        }
    }
}
