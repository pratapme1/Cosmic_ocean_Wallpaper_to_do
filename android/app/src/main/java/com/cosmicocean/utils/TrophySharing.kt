package com.cosmicocean.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import com.cosmicocean.data.TrophyEntity
import com.cosmicocean.systems.TrophyManager
import java.io.File
import java.io.FileOutputStream

/**
 * Trophy Sharing Utility
 * Generates shareable images of trophy achievements
 */
object TrophySharing {

    /**
     * Generate a trophy share image
     */
    fun generateTrophyImage(
        context: Context,
        trophy: TrophyEntity,
        totalCompletions: Int
    ): Bitmap {
        val width = 1080
        val height = 1080

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                getTierColorInt(trophy.tier),
                darkenColor(getTierColorInt(trophy.tier)),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Trophy emoji (large)
        val emojiPaint = Paint().apply {
            textSize = 200f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val emoji = getTierEmoji(trophy.tier)
        canvas.drawText(emoji, width / 2f, height / 2f - 150f, emojiPaint)

        // Trophy name
        val namePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 64f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText(
            TrophyManager.getTrophyName(trophy.milestone),
            width / 2f,
            height / 2f + 50f,
            namePaint
        )

        // Description
        val descPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            alpha = 200
        }
        canvas.drawText(
            TrophyManager.getTrophyDescription(trophy.milestone),
            width / 2f,
            height / 2f + 120f,
            descPaint
        )

        // Total completions
        val totalPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText(
            "$totalCompletions Tasks Completed",
            width / 2f,
            height / 2f + 250f,
            totalPaint
        )

        // App name/watermark
        val watermarkPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            alpha = 150
        }
        canvas.drawText(
            "Cosmic Ocean",
            width / 2f,
            height - 100f,
            watermarkPaint
        )

        return bitmap
    }

    /**
     * Share trophy via system share sheet
     */
    fun shareTrophy(
        context: Context,
        trophy: TrophyEntity,
        totalCompletions: Int
    ) {
        try {
            // Generate image
            val bitmap = generateTrophyImage(context, trophy, totalCompletions)

            // Save to cache
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "trophy_${trophy.id}.png")

            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            // Get URI via FileProvider
            val imageUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Create share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "I just unlocked the \"${TrophyManager.getTrophyName(trophy.milestone)}\" " +
                            "trophy in Cosmic Ocean! 🏆 $totalCompletions tasks completed!"
                )
                type = "image/png"
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Trophy"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get tier color as Int
     */
    private fun getTierColorInt(tier: String): Int {
        return when (tier.lowercase()) {
            "bronze" -> 0xFFCD7F32.toInt()
            "silver" -> 0xFFC0C0C0.toInt()
            "gold" -> 0xFFFFD700.toInt()
            "platinum" -> 0xFFE5E4E2.toInt()
            "cosmic" -> 0xFF9B59B6.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    /**
     * Get tier emoji
     */
    private fun getTierEmoji(tier: String): String {
        return when (tier.lowercase()) {
            "bronze" -> "🥉"
            "silver" -> "🥈"
            "gold" -> "🥇"
            "platinum" -> "💎"
            "cosmic" -> "🌌"
            else -> "🏆"
        }
    }

    /**
     * Darken a color for gradient effect
     */
    private fun darkenColor(color: Int): Int {
        val factor = 0.7f
        val a = android.graphics.Color.alpha(color)
        val r = (android.graphics.Color.red(color) * factor).toInt()
        val g = (android.graphics.Color.green(color) * factor).toInt()
        val b = (android.graphics.Color.blue(color) * factor).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }
}
