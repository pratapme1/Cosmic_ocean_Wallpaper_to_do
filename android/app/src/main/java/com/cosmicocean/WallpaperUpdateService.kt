package com.cosmicocean

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log

class WallpaperUpdateService(private val context: Context) {

    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)

    companion object {
        private const val TAG = "WallpaperUpdateService"
    }

    /**
     * Updates the system wallpaper with the provided bitmap.
     * @param bitmap The image to set as wallpaper.
     * @param flag The wallpaper flag (FLAG_SYSTEM, FLAG_LOCK, or both). Defaults to FLAG_LOCK.
     * @return Boolean indicating success.
     */
    fun updateWallpaper(bitmap: Bitmap, flag: Int = WallpaperManager.FLAG_LOCK): Boolean {
        if (!PermissionUtils.hasWallpaperPermission(context)) {
            Log.e(TAG, "Cannot update wallpaper: Missing SET_WALLPAPER permission.")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, flag)
                Log.d(TAG, "Successfully updated wallpaper (flag=$flag).")
                true
            } else {
                // Fallback for older devices (sets both by default)
                wallpaperManager.setBitmap(bitmap)
                Log.d(TAG, "Successfully updated wallpaper (legacy).")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update wallpaper: ${e.message}", e)
            false
        }
    }
}
