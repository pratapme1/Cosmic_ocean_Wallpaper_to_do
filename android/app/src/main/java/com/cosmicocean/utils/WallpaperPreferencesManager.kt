package com.cosmicocean.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.view.WindowManager
import com.cosmicocean.model.UserProfile

class WallpaperPreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "wallpaper_prefs"
        private const val KEY_THEME = "theme"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"

        const val DEFAULT_THEME = "cosmic"
        const val DEFAULT_RESOLUTION = "1080x1920"

        val AVAILABLE_THEMES = listOf("cosmic", "ocean", "fantasy")
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    fun setTheme(theme: String): Boolean {
        if (!AVAILABLE_THEMES.contains(theme)) {
            return false
        }
        return prefs.edit().putString(KEY_THEME, theme).commit()
    }

    fun getResolution(): String {
        val saved = prefs.getString(KEY_RESOLUTION, null)
        return saved ?: detectDeviceResolution()
    }

    fun setResolution(resolution: String): Boolean {
        // Validate format: WxH
        if (!resolution.matches(Regex("\\d+x\\d+"))) {
            return false
        }
        return prefs.edit().putString(KEY_RESOLUTION, resolution).commit()
    }

    fun detectDeviceResolution(): String {
        // CRITICAL FIX: Use currentWindowMetrics (API 30+) or getRealMetrics (Legacy) 
        // to get FULL physical display size including navigation/status bars.
        // This ensures wallpaper covers the entire screen without gaps or letterboxing.
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val width: Int
        val height: Int

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            // bounds includes system decorations
            width = metrics.bounds.width()
            height = metrics.bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics) // getRealMetrics includes system bars
            width = metrics.widthPixels
            height = metrics.heightPixels
        }

        // Always store resolution as portrait (width x height where height > width)
        val resolution = if (height > width) {
            "${width}x${height}"
        } else {
            "${height}x${width}"
        }

        // Cache the detected resolution
        setResolution(resolution)

        return resolution
    }

    fun syncFromUserProfile(userProfile: UserProfile) {
        prefs.edit().apply {
            putString(KEY_THEME, userProfile.theme)
            putString(KEY_RESOLUTION, userProfile.resolution)
            putLong(KEY_LAST_SYNC, System.currentTimeMillis())
        }.apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun isWallpaperEnabled(): Boolean {
        // Return null (not set) or actual value
        return prefs.getBoolean(KEY_WALLPAPER_ENABLED, false)
    }

    fun hasSetWallpaperPreference(): Boolean {
        return prefs.contains(KEY_WALLPAPER_ENABLED)
    }

    fun setWallpaperEnabled(enabled: Boolean): Boolean {
        return prefs.edit().putBoolean(KEY_WALLPAPER_ENABLED, enabled).commit()
    }

    fun needsSync(): Boolean {
        val lastSync = getLastSyncTime()
        if (lastSync == 0L) return true

        // Sync if last sync was more than 1 hour ago
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        return lastSync < oneHourAgo
    }
}
