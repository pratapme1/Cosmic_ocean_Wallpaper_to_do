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
 * - Task display with "RIGHT NOW" header
 * - Achievement display
 * - Dark overlay for custom backgrounds
 * - Auto font contrast adjustment
 * - WCAG-compliant text rendering
 */
object LocalWallpaperGenerator {
    private const val TAG = "LocalWallpaperGen"

    // Particle system constants
    private const val STAR_COUNT = 50
    private const val PARTICLE_MIN_SIZE = 1f
    private const val PARTICLE_MAX_SIZE = 3f

    // Layout constants (matching backend layout-system.js)
    private const val SAFE_ZONE_TOP_RATIO = 0.12f  // Clock/status bar area
    private const val TASK_ZONE_START_RATIO = 0.35f
    private const val TASK_ZONE_END_RATIO = 0.75f
    private const val MARGIN_HORIZONTAL_RATIO = 0.06f

    /**
     * Generate wallpaper bitmap with generated theme
     * Shows top 3 tasks with +more indicator
     */
    fun generate(
        tasks: List<StarEntity>,
        totalTaskCount: Int,
        theme: WallpaperTheme,
        width: Int,
        height: Int,
        achievementCount: Int = 0,
        streakDays: Int = 0
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper: ${width}x${height}, theme=$theme, tasks=${tasks.size}, total=$totalTaskCount")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // CRITICAL FIX: Fill with solid base color first to prevent black screen
        val urgency = if (tasks.isNotEmpty()) calculateUrgency(tasks[0]) else UrgencyLevel.CLEAR
        val colors = theme.getColors(urgency)
        canvas.drawColor(colors.gradientEnd)

        // Layer 1: Gradient background
        drawGradientBackground(canvas, colors, width, height)

        // Layer 2: Particle system
        drawParticles(canvas, colors, width, height, theme)

        // Layer 3: Achievement badge (if any)
        if (achievementCount > 0 || streakDays > 0) {
            drawAchievementBadge(canvas, achievementCount, streakDays, colors, width, height)
        }

        // Layer 4: Task display
        if (tasks.isNotEmpty()) {
            drawTaskListEnhanced(canvas, tasks, totalTaskCount, colors, width, height)
        } else {
            drawClearState(canvas, colors, width, height)
        }

        return bitmap
    }

    /**
     * Generate wallpaper bitmap with custom background image
     * Shows top 3 tasks with +more indicator
     * Includes dark overlay for text readability
     */
    fun generateWithCustomBackground(
        tasks: List<StarEntity>,
        totalTaskCount: Int,
        customBackground: Bitmap,
        width: Int,
        height: Int,
        achievementCount: Int = 0,
        streakDays: Int = 0
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper with custom background: ${width}x${height}, tasks=${tasks.size}")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // CRITICAL FIX: Fill with solid black first to prevent transparency issues
        canvas.drawColor(Color.BLACK)

        // Layer 1: Custom background image (scaled to cover)
        try {
            val scaledBackground = scaleToCover(customBackground, width, height)
            Log.d(TAG, "Scaled background: ${scaledBackground.width}x${scaledBackground.height}")
            canvas.drawBitmap(scaledBackground, 0f, 0f, null)

            if (scaledBackground != customBackground && !scaledBackground.isRecycled) {
                scaledBackground.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing custom background: ${e.message}", e)
            canvas.drawColor(Color.parseColor("#1A1A2E"))
        }

        // Layer 2: Dark overlay for text readability (40% opacity like backend)
        val overlayPaint = Paint().apply {
            color = Color.BLACK
            alpha = 102  // 40% of 255
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Calculate urgency for styling
        val urgency = if (tasks.isNotEmpty()) calculateUrgency(tasks[0]) else UrgencyLevel.CLEAR

        // Use high contrast colors for custom backgrounds
        val colors = getCustomBackgroundColors(urgency)

        // Layer 3: Achievement badge (if any)
        if (achievementCount > 0 || streakDays > 0) {
            drawAchievementBadge(canvas, achievementCount, streakDays, colors, width, height)
        }

        // Layer 4: Task display
        if (tasks.isNotEmpty()) {
            drawTaskListEnhanced(canvas, tasks, totalTaskCount, colors, width, height)
        } else {
            drawClearState(canvas, colors, width, height)
        }

        return bitmap
    }

    /**
     * Scale bitmap to cover (crop to fit) like CSS background-size: cover
     */
    private fun scaleToCover(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceRatio = source.width.toFloat() / source.height
        val targetRatio = targetWidth.toFloat() / targetHeight

        val scaledWidth: Int
        val scaledHeight: Int

        if (sourceRatio > targetRatio) {
            // Source is wider - scale by height, crop width
            scaledHeight = targetHeight
            scaledWidth = (source.width * (targetHeight.toFloat() / source.height)).toInt()
        } else {
            // Source is taller - scale by width, crop height
            scaledWidth = targetWidth
            scaledHeight = (source.height * (targetWidth.toFloat() / source.width)).toInt()
        }

        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        // Crop to center
        val x = (scaledWidth - targetWidth) / 2
        val y = (scaledHeight - targetHeight) / 2

        return Bitmap.createBitmap(scaled, x.coerceAtLeast(0), y.coerceAtLeast(0), targetWidth, targetHeight)
    }

    /**
     * High contrast colors for custom backgrounds
     */
    private fun getCustomBackgroundColors(urgency: UrgencyLevel): ThemeColors {
        return ThemeColors(
            gradientStart = Color.TRANSPARENT,
            gradientEnd = Color.TRANSPARENT,
            taskCircle = when (urgency) {
                UrgencyLevel.CRITICAL -> Color.parseColor("#FF4444")
                UrgencyLevel.URGENT -> Color.parseColor("#FF8800")
                UrgencyLevel.ATTENTION -> Color.parseColor("#FFCC00")
                else -> Color.parseColor("#00CED1")
            },
            taskCircleGlow = Color.WHITE,
            titleColor = Color.WHITE,
            subtitleColor = Color.parseColor("#E0E0E0"),
            particleColor = Color.WHITE
        )
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
            width / 2f, 0f,
            width / 2f, height.toFloat(),
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
                    drawStar(canvas, x, y, size, paint)
                }
                WallpaperTheme.DEEP_OCEAN -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawCircle(x, y, size * 1.5f, paint)
                    paint.style = Paint.Style.FILL
                }
                WallpaperTheme.FOREST -> {
                    canvas.drawCircle(x, y, size, paint)
                    if (random.nextFloat() > 0.7f) {
                        paint.alpha = alpha / 3
                        canvas.drawCircle(x, y, size * 2, paint)
                    }
                }
                WallpaperTheme.MINIMAL -> {
                    canvas.drawCircle(x, y, size * 0.5f, paint)
                }
                else -> {
                    canvas.drawCircle(x, y, size, paint)
                }
            }
        }
    }

    /**
     * Draw a 4-point star
     */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        canvas.drawLine(cx - size, cy, cx + size, cy, paint)
        canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        canvas.drawCircle(cx, cy, size * 0.3f, paint)
    }

    /**
     * Draw achievement badge at top of screen
     */
    private fun drawAchievementBadge(
        canvas: Canvas,
        achievementCount: Int,
        streakDays: Int,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val marginH = width * MARGIN_HORIZONTAL_RATIO
        val badgeY = height * SAFE_ZONE_TOP_RATIO + 20

        // Badge background
        val badgePaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            alpha = 120
        }

        val badgeWidth = width * 0.35f
        val badgeHeight = height * 0.04f
        val badgeRect = RectF(marginH, badgeY, marginH + badgeWidth, badgeY + badgeHeight)
        canvas.drawRoundRect(badgeRect, 20f, 20f, badgePaint)

        // Badge text
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = width * 0.028f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val badgeText = when {
            streakDays > 0 && achievementCount > 0 -> "🔥 $streakDays day streak • $achievementCount 🏆"
            streakDays > 0 -> "🔥 $streakDays day streak"
            achievementCount > 0 -> "$achievementCount achievements 🏆"
            else -> ""
        }

        val textX = marginH + 15
        val textY = badgeY + badgeHeight * 0.7f
        canvas.drawText(badgeText, textX, textY, textPaint)
    }

    /**
     * Draw enhanced task list matching backend format
     * "RIGHT NOW" header with tasks below
     */
    private fun drawTaskListEnhanced(
        canvas: Canvas,
        tasks: List<StarEntity>,
        totalTaskCount: Int,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val marginH = width * MARGIN_HORIZONTAL_RATIO
        val taskZoneStart = height * TASK_ZONE_START_RATIO
        val taskZoneEnd = height * TASK_ZONE_END_RATIO
        val taskZoneHeight = taskZoneEnd - taskZoneStart

        var currentY = taskZoneStart

        // "RIGHT NOW" header (matching backend style)
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = width * 0.032f
            letterSpacing = 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Add text shadow for readability
        headerPaint.setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0))
        canvas.drawText("RIGHT NOW", marginH, currentY, headerPaint)

        currentY += height * 0.05f

        // Draw up to 3 tasks
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = width * 0.048f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0))
        }

        val metaPaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = width * 0.032f
            setShadowLayer(3f, 1f, 1f, Color.argb(80, 0, 0, 0))
        }

        val maxWidth = (width - marginH * 2).toInt()
        val circleRadius = width * 0.012f

        tasks.take(3).forEachIndexed { index, task ->
            val taskUrgency = calculateUrgency(task)

            // Priority indicator circle
            val circleColor = when (taskUrgency) {
                UrgencyLevel.CRITICAL -> Color.parseColor("#FF4444")
                UrgencyLevel.URGENT -> Color.parseColor("#FF8800")
                UrgencyLevel.ATTENTION -> Color.parseColor("#FFCC00")
                else -> colors.taskCircle
            }

            val circlePaint = Paint().apply {
                isAntiAlias = true
                color = circleColor
                style = Paint.Style.FILL
            }

            val circleY = currentY + titlePaint.textSize / 3
            canvas.drawCircle(marginH, circleY, circleRadius, circlePaint)

            // Task title with wrapping
            val titleLayout = StaticLayout.Builder
                .obtain(task.title, 0, task.title.length, titlePaint, maxWidth - (circleRadius * 3).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()

            canvas.save()
            canvas.translate(marginH + circleRadius * 2.5f, currentY)
            titleLayout.draw(canvas)
            canvas.restore()

            currentY += titleLayout.height + 5

            // Time estimate / due date
            val metaText = formatDueDate(task.dueDate)
            if (metaText != null) {
                canvas.drawText(metaText, marginH + circleRadius * 2.5f, currentY, metaPaint)
                currentY += metaPaint.textSize + 10
            }

            currentY += height * 0.025f
        }

        // Remaining count
        val remainingCount = totalTaskCount - tasks.size.coerceAtMost(3)
        if (remainingCount > 0) {
            canvas.drawText("+ $remainingCount more today", marginH, currentY, metaPaint)
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
        val centerY = height * 0.45f

        // Draw checkmark circle
        val circleRadius = width * 0.12f
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
            centerX - checkSize * 0.5f, centerY,
            centerX - checkSize * 0.1f, centerY + checkSize * 0.4f,
            checkPaint
        )
        canvas.drawLine(
            centerX - checkSize * 0.1f, centerY + checkSize * 0.4f,
            centerX + checkSize * 0.5f, centerY - checkSize * 0.3f,
            checkPaint
        )

        // "All clear ✨" text with glow effect
        val messagePaint = Paint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = width * 0.055f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(8f, 0f, 0f, colors.taskCircleGlow)
        }

        canvas.drawText(
            "All clear ✨",
            centerX,
            centerY + circleRadius + (width * 0.1f),
            messagePaint
        )

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = width * 0.035f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Rest. You earned it.",
            centerX,
            centerY + circleRadius + (width * 0.16f),
            subtitlePaint
        )
    }

    /**
     * Format due date for display
     */
    private fun formatDueDate(dueDateMs: Long?): String? {
        if (dueDateMs == null) return null

        val now = System.currentTimeMillis()
        val diff = dueDateMs - now

        if (diff < 0) {
            val hoursAgo = -diff / (1000 * 60 * 60)
            return when {
                hoursAgo < 1 -> "⚠️ Overdue"
                hoursAgo < 24 -> "⚠️ Overdue by ${hoursAgo}h"
                else -> "⚠️ Overdue by ${hoursAgo / 24}d"
            }
        }

        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60

        return when {
            hours < 1 -> "Due in ${minutes}min"
            hours < 24 -> "Due in ${hours}h"
            hours < 48 -> "Due tomorrow"
            else -> {
                val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                "Due ${dateFormat.format(Date(dueDateMs))}"
            }
        }
    }
}
