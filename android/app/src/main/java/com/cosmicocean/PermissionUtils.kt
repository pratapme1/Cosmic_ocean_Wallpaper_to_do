package com.cosmicocean

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {
    /**
     * Checks if the app has the SET_WALLPAPER permission.
     * Note: SET_WALLPAPER is a normal permission, granted at install time.
     * However, it's good practice to check.
     */
    fun hasWallpaperPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SET_WALLPAPER
        ) == PackageManager.PERMISSION_GRANTED
    }
}
