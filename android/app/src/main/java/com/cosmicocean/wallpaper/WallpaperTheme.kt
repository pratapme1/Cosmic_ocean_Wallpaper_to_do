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
            gradientStart = 0xFF0A1628.toInt(),
            gradientEnd = 0xFF1A2F4A.toInt(),
            taskCircle = 0xFF00CED1.toInt(),
            taskCircleGlow = 0xFF00FFFF.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFADD8E6.toInt(),
            particleColor = 0xFFFFFFFF.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFF0D1B2A.toInt(),
            gradientEnd = 0xFF1B3A5F.toInt(),
            taskCircle = 0xFF4169E1.toInt(),
            taskCircleGlow = 0xFF6495ED.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFADD8E6.toInt(),
            particleColor = 0xFFE0E0FF.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFF1A1A2E.toInt(),
            gradientEnd = 0xFF2D2D44.toInt(),
            taskCircle = 0xFFFF8C00.toInt(),
            taskCircleGlow = 0xFFFFAA00.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFFD700.toInt(),
            particleColor = 0xFFFFE4B5.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFF1A0A0A.toInt(),
            gradientEnd = 0xFF2D1A1A.toInt(),
            taskCircle = 0xFFFF4500.toInt(),
            taskCircleGlow = 0xFFFF6347.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFF6347.toInt(),
            particleColor = 0xFFFFB6C1.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF1A0000.toInt(),
            gradientEnd = 0xFF330000.toInt(),
            taskCircle = 0xFFDC143C.toInt(),
            taskCircleGlow = 0xFFFF0000.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFF6B6B.toInt(),
            particleColor = 0xFFFF9999.toInt()
        )
    }

    private fun getDeepOceanColors(urgency: UrgencyLevel): ThemeColors = when (urgency) {
        UrgencyLevel.CLEAR -> ThemeColors(
            gradientStart = 0xFF006994.toInt(),
            gradientEnd = 0xFF00496B.toInt(),
            taskCircle = 0xFF20B2AA.toInt(),
            taskCircleGlow = 0xFF40E0D0.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFF7FFFD4.toInt(),
            particleColor = 0xFFE0FFFF.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFF003366.toInt(),
            gradientEnd = 0xFF001F4D.toInt(),
            taskCircle = 0xFF4682B4.toInt(),
            taskCircleGlow = 0xFF5F9EA0.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFF87CEEB.toInt(),
            particleColor = 0xFFB0E0E6.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFF003344.toInt(),
            gradientEnd = 0xFF002233.toInt(),
            taskCircle = 0xFFDAA520.toInt(),
            taskCircleGlow = 0xFFFFD700.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFFD700.toInt(),
            particleColor = 0xFFFFF8DC.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFF002222.toInt(),
            gradientEnd = 0xFF001111.toInt(),
            taskCircle = 0xFFFF8C00.toInt(),
            taskCircleGlow = 0xFFFFA500.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFFA500.toInt(),
            particleColor = 0xFFFFDAB9.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF1A0505.toInt(),
            gradientEnd = 0xFF0D0202.toInt(),
            taskCircle = 0xFFB22222.toInt(),
            taskCircleGlow = 0xFFDC143C.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFF6B6B.toInt(),
            particleColor = 0xFFFFCCCC.toInt()
        )
    }

    private fun getForestColors(urgency: UrgencyLevel): ThemeColors = when (urgency) {
        UrgencyLevel.CLEAR -> ThemeColors(
            gradientStart = 0xFF2E1A47.toInt(),
            gradientEnd = 0xFF1A0D2E.toInt(),
            taskCircle = 0xFFFFD700.toInt(),
            taskCircleGlow = 0xFFFFFACD.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFAFAD2.toInt(),
            particleColor = 0xFFFFFFE0.toInt()
        )
        UrgencyLevel.CALM -> ThemeColors(
            gradientStart = 0xFF4A1A6B.toInt(),
            gradientEnd = 0xFF2E0D47.toInt(),
            taskCircle = 0xFF9370DB.toInt(),
            taskCircleGlow = 0xFFBA55D3.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFE6E6FA.toInt(),
            particleColor = 0xFFDDA0DD.toInt()
        )
        UrgencyLevel.ATTENTION -> ThemeColors(
            gradientStart = 0xFF3D1A47.toInt(),
            gradientEnd = 0xFF2A0D33.toInt(),
            taskCircle = 0xFFFF8C00.toInt(),
            taskCircleGlow = 0xFFFFAA00.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFFD700.toInt(),
            particleColor = 0xFFFFE4B5.toInt()
        )
        UrgencyLevel.URGENT -> ThemeColors(
            gradientStart = 0xFF2E0A1A.toInt(),
            gradientEnd = 0xFF1A050D.toInt(),
            taskCircle = 0xFFFF6347.toInt(),
            taskCircleGlow = 0xFFFF7F50.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFF7F50.toInt(),
            particleColor = 0xFFFFB6C1.toInt()
        )
        UrgencyLevel.CRITICAL -> ThemeColors(
            gradientStart = 0xFF1A0000.toInt(),
            gradientEnd = 0xFF0D0000.toInt(),
            taskCircle = 0xFFDC143C.toInt(),
            taskCircleGlow = 0xFFFF0000.toInt(),
            titleColor = Color.WHITE,
            subtitleColor = 0xFFFF6B6B.toInt(),
            particleColor = 0xFFFF9999.toInt()
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
