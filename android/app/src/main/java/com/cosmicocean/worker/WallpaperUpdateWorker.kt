package com.cosmicocean.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.utils.AchievementUtils
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.utils.applyWallpaperPrivacy
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first

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

            // 2. Get target dimensions (use stored resolution for backend alignment)
            val (screenWidth, screenHeight) = getTargetResolution(prefsManager)
            Log.d(TAG, "Target dimensions: ${screenWidth}x${screenHeight}")

            // 3. Get tasks from LOCAL database (no network needed!)
            val database = CosmicDatabase.getDatabase(applicationContext)
            val topTasks = database.starDao().getTop3Tasks()
            val allTasks = database.starDao().getAllActiveStarsSync()
            val totalCount = database.starDao().getActiveTaskCount()
            val recentCompletionAt = database.starDao().getLatestCompletionTimestamp()
            val privacyMasked = applyWallpaperPrivacy(applicationContext, topTasks, totalCount)
            val achievements = AchievementUtils.getSnapshot(applicationContext)
            val environmentPrefs = runCatching {
                EnvironmentPreferencesRepository(applicationContext).preferencesFlow.first()
            }.getOrElse { EnvironmentPreferences() }

            Log.d(TAG, "LOCAL-FIRST: Got ${privacyMasked.tasks.size} top tasks, total: ${privacyMasked.totalTaskCount} (from local DB)")

            // 4. Generate wallpaper locally
            val bitmap = if (wallpaperMode == WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM) {
                // Custom wallpaper mode - load custom background
                val customPath = prefsManager.getCustomWallpaperPath()
                if (customPath != null && File(customPath).exists()) {
                    val decoded = BitmapFactory.decodeFile(customPath)
                    val customBackground = decoded?.let { applyExifOrientation(it, customPath) }
                    if (customBackground != null && customBackground != decoded && !decoded.isRecycled) {
                        decoded.recycle()
                    }
                    if (customBackground != null) {
                        Log.d(TAG, "Using custom background: $customPath")
                        LocalWallpaperGenerator.generateWithCustomBackground(
                            tasks = privacyMasked.tasks,
                            totalTaskCount = privacyMasked.totalTaskCount,
                            customBackground = customBackground,
                            width = screenWidth,
                            height = screenHeight,
                            achievementCount = achievements.achievementCount,
                            streakDays = achievements.streakDays,
                            theme = WallpaperTheme.fromString(theme),
                            environmentPreferences = environmentPrefs,
                            weatherTasks = allTasks,
                            recentCompletionAt = recentCompletionAt
                        )
                    } else {
                        Log.w(TAG, "Custom background decode failed, falling back to generated")
                        generateThemedWallpaper(
                            privacyMasked.tasks,
                            privacyMasked.totalTaskCount,
                            theme,
                            screenWidth,
                            screenHeight,
                            achievements.achievementCount,
                            achievements.streakDays,
                            environmentPrefs,
                            allTasks,
                            recentCompletionAt
                        )
                    }
                } else {
                    Log.w(TAG, "Custom wallpaper path invalid, falling back to generated")
                    generateThemedWallpaper(
                        privacyMasked.tasks,
                        privacyMasked.totalTaskCount,
                        theme,
                        screenWidth,
                        screenHeight,
                        achievements.achievementCount,
                        achievements.streakDays,
                        environmentPrefs,
                        allTasks,
                        recentCompletionAt
                    )
                }
            } else {
                // Generated wallpaper mode
                generateThemedWallpaper(
                    privacyMasked.tasks,
                    privacyMasked.totalTaskCount,
                    theme,
                    screenWidth,
                    screenHeight,
                    achievements.achievementCount,
                    achievements.streakDays,
                    environmentPrefs,
                    allTasks,
                    recentCompletionAt
                )
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
            Result.retry()
        }
    }

    private fun generateThemedWallpaper(
        tasks: List<com.cosmicocean.data.StarEntity>,
        totalCount: Int,
        theme: String,
        width: Int,
        height: Int,
        achievementCount: Int,
        streakDays: Int,
        environmentPreferences: EnvironmentPreferences,
        weatherTasks: List<com.cosmicocean.data.StarEntity>,
        recentCompletionAt: Long?
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
            height = height,
            achievementCount = achievementCount,
            streakDays = streakDays,
            environmentPreferences = environmentPreferences,
            weatherTasks = weatherTasks,
            recentCompletionAt = recentCompletionAt
        )
    }

    private fun applyExifOrientation(bitmap: android.graphics.Bitmap, path: String): android.graphics.Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> Unit
            }

            if (matrix.isIdentity) {
                bitmap
            } else {
                android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "EXIF orientation read failed: ${e.message}")
            bitmap
        }
    }

    companion object {
        const val TAG = "WallpaperUpdateWorker"
    }

    private fun getTargetResolution(prefsManager: WallpaperPreferencesManager): Pair<Int, Int> {
        val parsed = parseResolution(prefsManager.getResolution())
        val (rawWidth, rawHeight) = if (parsed != null) {
            parsed
        } else {
            val displayMetrics = applicationContext.resources.displayMetrics
            Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

        // Always return portrait for lock screen alignment
        return if (rawHeight >= rawWidth) {
            Pair(rawWidth, rawHeight)
        } else {
            Pair(rawHeight, rawWidth)
        }
    }

    private fun parseResolution(resolution: String): Pair<Int, Int>? {
        val parts = resolution.lowercase().split("x")
        if (parts.size != 2) return null
        val width = parts[0].toIntOrNull() ?: return null
        val height = parts[1].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        return Pair(width, height)
    }
}
