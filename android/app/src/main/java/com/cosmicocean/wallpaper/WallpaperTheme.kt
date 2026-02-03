package com.cosmicocean.wallpaper

import android.graphics.Color

/**
 * Wallpaper Theme System
 * Ported from backend/services/wallpaper-generator-enhanced.js
 *
 * Provides color palettes for different urgency levels and themes.
 */
enum class WallpaperTheme {
    COSMIC,
    DEEP_OCEAN,
    FOREST,
    MINIMAL,
    OCEAN,    // Legacy alias for DEEP_OCEAN
    FANTASY;  // Legacy alias for FOREST

    /**
     * Get colors for a given urgency level
     */
    fun getColors(urgency: UrgencyLevel): ThemeColors {
        return when (this) {
            COSMIC -> getCosmicColors(urgency)
            DEEP_OCEAN, OCEAN -> getDeepOceanColors(urgency)
            FOREST, FANTASY -> getForestColors(urgency)
            MINIMAL -> getMinimalColors(urgency)
        }
    }

    private fun getCosmicColors(urgency: UrgencyLevel): ThemeColors = when (urgency) {
        UrgencyLevel.CLEAR -> ThemeColors(
            gradientStart = 0xFF6495ED.toInt(),
            gradientEnd = 0xFF4169E1.toInt(),
            taskCircle = 0xFF87CEEB.toInt(),
            taskCircleGlow = Color.argb(128, 135, 206, 235),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFF0F8FF.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFF2D1B5E.toInt(),
            gradientEnd = 0xFF1A0F3C.toInt(),
            taskCircle = 0xFF7B68EE.toInt(),
            taskCircleGlow = Color.argb(102, 123, 104, 238),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(217, 255, 255, 255),
            particleColor = 0xFFE6E6FA.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFF3F2A7A.toInt(),
            gradientEnd = 0xFF2D1B5E.toInt(),
            taskCircle = 0xFF8B7BFF.toInt(),
            taskCircleGlow = Color.argb(115, 139, 123, 255),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(217, 255, 255, 255),
            particleColor = 0xFFD8BFD8.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFF5E4A9E.toInt(),
            gradientEnd = 0xFF4B3682.toInt(),
            taskCircle = 0xFF9D7FFF.toInt(),
            taskCircleGlow = Color.argb(128, 157, 127, 255),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFDDA0DD.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF8B6FBD.toInt(),
            gradientEnd = 0xFF6A4C9C.toInt(),
            taskCircle = 0xFFBA55D3.toInt(),
            taskCircleGlow = Color.argb(153, 186, 85, 211),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(242, 255, 255, 255),
            particleColor = 0xFFEE82EE.toInt()
        )
    }

    private fun getDeepOceanColors(urgency: UrgencyLevel): ThemeColors = when (urgency) {
        UrgencyLevel.CLEAR -> ThemeColors(
            gradientStart = 0xFF87CEFA.toInt(),
            gradientEnd = 0xFF87CEEB.toInt(),
            taskCircle = 0xFFADD8E6.toInt(),
            taskCircleGlow = Color.argb(153, 173, 216, 230),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFF0F8FF.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFF4682B4.toInt(),
            gradientEnd = 0xFF1E90FF.toInt(),
            taskCircle = 0xFF00CED1.toInt(),
            taskCircleGlow = Color.argb(128, 0, 206, 209),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFE0FFFF.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFF1E90FF.toInt(),
            gradientEnd = 0xFF00BFFF.toInt(),
            taskCircle = 0xFF40E0D0.toInt(),
            taskCircleGlow = Color.argb(140, 64, 224, 208),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFAFEEEE.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFF20B2AA.toInt(),
            gradientEnd = 0xFF00CED1.toInt(),
            taskCircle = 0xFF7FFFD4.toInt(),
            taskCircleGlow = Color.argb(153, 127, 255, 212),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(242, 255, 255, 255),
            particleColor = 0xFFB0E0E6.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF008BA3.toInt(),
            gradientEnd = 0xFF006080.toInt(),
            taskCircle = 0xFF00CED1.toInt(),
            taskCircleGlow = Color.argb(179, 0, 206, 209),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(242, 255, 255, 255),
            particleColor = 0xFFE0FFFF.toInt()
        )
    }

    private fun getForestColors(urgency: UrgencyLevel): ThemeColors = when (urgency) {
        UrgencyLevel.CLEAR -> ThemeColors(
            gradientStart = 0xFFFFC0CB.toInt(),
            gradientEnd = 0xFFFFB6C1.toInt(),
            taskCircle = 0xFFFFD700.toInt(),
            taskCircleGlow = Color.argb(153, 255, 215, 0),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFFFF5EE.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFFDA70D6.toInt(),
            gradientEnd = 0xFFC71585.toInt(),
            taskCircle = 0xFFFF1493.toInt(),
            taskCircleGlow = Color.argb(128, 255, 20, 147),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFFFB6C1.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFFEE82EE.toInt(),
            gradientEnd = 0xFFDA70D6.toInt(),
            taskCircle = 0xFFFF1493.toInt(),
            taskCircleGlow = Color.argb(153, 255, 20, 147),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(230, 255, 255, 255),
            particleColor = 0xFFFFB6C1.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFFFF69B4.toInt(),
            gradientEnd = 0xFFFF1493.toInt(),
            taskCircle = 0xFFFF00FF.toInt(),
            taskCircleGlow = Color.argb(179, 255, 0, 255),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(242, 255, 255, 255),
            particleColor = 0xFFFFBBFF.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF9B0063.toInt(),
            gradientEnd = 0xFF8B008B.toInt(),
            taskCircle = 0xFFFF00FF.toInt(),
            taskCircleGlow = Color.argb(204, 255, 0, 255),
            titleColor = Color.WHITE,
            subtitleColor = Color.argb(242, 255, 255, 255),
            particleColor = 0xFFFFE4E1.toInt()
        )
    }

    private fun getMinimalColors(urgency: UrgencyLevel): ThemeColors = when (urgency) {
        UrgencyLevel.CLEAR -> ThemeColors(
            gradientStart = 0xFF1A1A1A.toInt(),
            gradientEnd = 0xFF0D0D0D.toInt(),
            taskCircle = 0xFF888888.toInt(),
            taskCircleGlow = 0xFFAAAAAA.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFCCCCCC.toInt(),
            particleColor = 0xFF666666.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFF1A1A1A.toInt(),
            gradientEnd = 0xFF0D0D0D.toInt(),
            taskCircle = 0xFF4A90D9.toInt(),
            taskCircleGlow = 0xFF6BA3E0.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFCCCCCC.toInt(),
            particleColor = 0xFF555555.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFF1A1A1A.toInt(),
            gradientEnd = 0xFF0D0D0D.toInt(),
            taskCircle = 0xFFE6A700.toInt(),
            taskCircleGlow = 0xFFFFCC00.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFDDDDDD.toInt(),
            particleColor = 0xFF555555.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFF1A1A1A.toInt(),
            gradientEnd = 0xFF0D0D0D.toInt(),
            taskCircle = 0xFFE65C00.toInt(),
            taskCircleGlow = 0xFFFF8800.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFDDDDDD.toInt(),
            particleColor = 0xFF555555.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF1A1A1A.toInt(),
            gradientEnd = 0xFF0D0D0D.toInt(),
            taskCircle = 0xFFE64545.toInt(),
            taskCircleGlow = 0xFFFF6666.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFDDDDDD.toInt(),
            particleColor = 0xFF555555.toInt()
        )
    }

    companion object {
        fun fromString(name: String): WallpaperTheme {
            return when (name.lowercase()) {
                "cosmic" -> COSMIC
                "deep_ocean", "ocean" -> DEEP_OCEAN
                "forest", "fantasy" -> FOREST
                "minimal" -> MINIMAL
                else -> COSMIC
            }
        }
    }
}

/**
 * Urgency level for task display
 */
enum class UrgencyLevel {
    CLEAR,      // No tasks
    CALM,       // Tasks but not urgent (>48h)
    ATTENTION,  // Due within 24-48h
    URGENT,     // Due within 4-24h
    CRITICAL    // Overdue or due within 4h
}

/**
 * Theme color palette
 */
data class ThemeColors(
    val gradientStart: Int,
    val gradientEnd: Int,
    val taskCircle: Int,
    val taskCircleGlow: Int,
    val titleColor: Int,
    val subtitleColor: Int,
    val particleColor: Int
)
