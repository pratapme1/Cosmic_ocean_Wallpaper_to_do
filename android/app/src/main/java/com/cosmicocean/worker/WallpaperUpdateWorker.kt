package com.cosmicocean.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import java.io.File
import java.io.IOException

/**
 * LOCAL-FIRST FIX: WallpaperUpdateWorker
 *
 * Now uses LocalWallpaperGenerator instead of backend API.
 * Generates wallpaper on-device from local database.
 */
class WallpaperUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "======== WALLPAPER UPDATE WORKER STARTING (LOCAL-FIRST) ========")

            // 1. Get user preferences
            val prefsManager = WallpaperPreferencesManager(applicationContext)

            if (!prefsManager.isWallpaperEnabled()) {
                Log.d(TAG, "Wallpaper update skipped: Consent not granted.")
                return Result.success()
            }

            val theme = prefsManager.getTheme()
            val wallpaperMode = prefsManager.getWallpaperMode()

            Log.d(TAG, "Preferences - Theme: $theme, Mode: $wallpaperMode")

            // 2. Get screen dimensions
            val displayMetrics = applicationContext.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")

            // 3. Get tasks from LOCAL database (no network needed!)
            val database = CosmicDatabase.getDatabase(applicationContext)
            val topTasks = database.starDao().getTop3Tasks()
            val totalCount = database.starDao().getActiveTaskCount()

            Log.d(TAG, "LOCAL-FIRST: Got ${topTasks.size} top tasks, total: $totalCount (from local DB)")

            // 4. Generate wallpaper locally
            val bitmap = if (wallpaperMode == WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM) {
                // Custom wallpaper mode - load custom background
                val customPath = prefsManager.getCustomWallpaperPath()
                if (customPath != null && File(customPath).exists()) {
                    val customBackground = BitmapFactory.decodeFile(customPath)
                    if (customBackground != null) {
                        Log.d(TAG, "Using custom background: $customPath")
                        LocalWallpaperGenerator.generateWithCustomBackground(
                            tasks = topTasks,
                            totalTaskCount = totalCount,
                            customBackground = customBackground,
                            width = screenWidth,
                            height = screenHeight
                        )
                    } else {
                        Log.w(TAG, "Custom background decode failed, falling back to generated")
                        generateThemedWallpaper(topTasks, totalCount, theme, screenWidth, screenHeight)
                    }
                } else {
                    Log.w(TAG, "Custom wallpaper path invalid, falling back to generated")
                    generateThemedWallpaper(topTasks, totalCount, theme, screenWidth, screenHeight)
                }
            } else {
                // Generated wallpaper mode
                generateThemedWallpaper(topTasks, totalCount, theme, screenWidth, screenHeight)
            }

            // 5. Set wallpaper
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)

            try {
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK  // Update LOCK screen only
                )

                Log.d(TAG, "✅ Wallpaper set successfully! Size: ${bitmap.width}x${bitmap.height}")
                Log.d(TAG, "======== WALLPAPER UPDATE COMPLETE (LOCAL-FIRST) ========")
                return Result.success()
            } catch (e: IOException) {
                Log.e(TAG, "❌ IOException while setting wallpaper: ${e.message}", e)
                return Result.failure()
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException - Missing SET_WALLPAPER permission: ${e.message}", e)
                return Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating wallpaper: ${e.message}", e)
            return Result.retry()
        }
    }

    private fun generateThemedWallpaper(
        tasks: List<com.cosmicocean.data.StarEntity>,
        totalCount: Int,
        theme: String,
        width: Int,
        height: Int
    ): android.graphics.Bitmap {
        val wallpaperTheme = when (theme.lowercase()) {
            "deep_ocean" -> WallpaperTheme.DEEP_OCEAN
            "cosmic" -> WallpaperTheme.COSMIC
            "forest" -> WallpaperTheme.FOREST
            "minimal" -> WallpaperTheme.MINIMAL
            else -> WallpaperTheme.DEEP_OCEAN
        }

        return LocalWallpaperGenerator.generate(
            tasks = tasks,
            totalTaskCount = totalCount,
            theme = wallpaperTheme,
            width = width,
            height = height
        )
    }

    companion object {
        const val TAG = "WallpaperUpdateWorker"
    }
}
