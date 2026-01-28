package com.cosmicocean.worker

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.utils.WallpaperPreferencesManager
import java.io.IOException

class WallpaperUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val userId = com.cosmicocean.utils.UserSession.getUserId(applicationContext) ?: "none"
            Log.e(TAG, "======== WALLPAPER UPDATE STARTING ========")
            Log.e(TAG, "User ID: $userId")

            // 1. Get user preferences
            val prefsManager = WallpaperPreferencesManager(applicationContext)
            val theme = prefsManager.getTheme()
            val resolution = prefsManager.getResolution()

            Log.e(TAG, "Reading local preferences - Theme: $theme, Resolution: $resolution")

            // 2. Fetch from API with preferences + timestamp to bust cache
            val timestamp = System.currentTimeMillis()
            val timezone = java.util.TimeZone.getDefault().id // e.g., "Asia/Kolkata"
            Log.e(TAG, "Requesting wallpaper with timestamp: $timestamp, timezone: $timezone (cache-busting)")

            val response = NetworkModule.getApi(applicationContext).getWallpaper(
                theme = theme,
                resolution = resolution,
                enhanced = true,
                timestamp = timestamp,  // Force fresh generation
                timezone = timezone
            )

            if (!response.isSuccessful || response.body() == null) {
                when (response.code()) {
                    401 -> {
                        Log.w(TAG, "Unauthorized: User not logged in or token expired. Skipping wallpaper update.")
                        return Result.failure() // Don't retry - auth issue needs user action
                    }
                    403 -> {
                        Log.w(TAG, "Forbidden: Access denied. Skipping wallpaper update.")
                        return Result.failure()
                    }
                    404 -> {
                        Log.e(TAG, "Wallpaper endpoint not found (404). Check backend URL.")
                        return Result.failure()
                    }
                    500, 502, 503, 504 -> {
                        Log.e(TAG, "Server error (${response.code()}). Will retry later.")
                        return Result.retry()
                    }
                    else -> {
                        Log.e(TAG, "API call failed: ${response.code()}")
                        return Result.retry()
                    }
                }
            }

            // 3. Set Wallpaper
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val responseBody = response.body()!!

            try {
                // Validate content type
                val contentType = response.headers()["Content-Type"]
                Log.e(TAG, "Response Content-Type: $contentType")

                if (contentType != null && !contentType.contains("image/png") && !contentType.contains("image/")) {
                    Log.e(TAG, "❌ Invalid content type: $contentType (expected image/png)")
                    return Result.failure()
                }

                Log.e(TAG, "Decoding wallpaper bitmap from response...")
                val inputStream = responseBody.byteStream()
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

                if (bitmap != null) {
                    Log.e(TAG, "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

                    // Get actual screen dimensions
                    val displayMetrics = applicationContext.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    Log.e(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")
                    Log.e(TAG, "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")

                    // For lock screen, use actual screen dimensions (no parallax needed)
                    // Don't use WallpaperManager.desiredMinimum* - those are for home screen with 2x width!
                    Log.e(TAG, "Setting wallpaper for LOCK screen using original bitmap (no scaling)...")

                    // RACE CONDITION FIX (2026-01-09):
                    // Do NOT call clear() before setBitmap() - if setBitmap fails, wallpaper stays blank
                    // setBitmap() with flags will replace existing wallpaper atomically
                    val wallpaperFlags = WallpaperManager.FLAG_LOCK

                    Log.e(TAG, "Setting wallpaper on BOTH home and lock screens...")

                    // Set wallpaper using setBitmap without clearing first
                    // This atomically replaces the existing wallpaper
                    wallpaperManager.setBitmap(
                        bitmap,
                        null,
                        true,
                        wallpaperFlags  // Update LOCK screen only
                    )

                    Log.e(TAG, "✅ Wallpaper set successfully! Theme: $theme, Size: ${bitmap.width}x${bitmap.height}, Timestamp: $timestamp")
                    Log.e(TAG, "======== WALLPAPER UPDATE COMPLETE ========")
                    return Result.success()
                } else {
                    Log.e(TAG, "❌ Failed to decode wallpaper bitmap. Response may not be a valid PNG image.")
                    Log.e(TAG, "Attempting to read response as text for debugging...")
                    try {
                        // Try to read first 500 chars to see what we got
                        val errorBody = responseBody.string().take(500)
                        Log.e(TAG, "Response body (first 500 chars): $errorBody")
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not read response body: ${e.message}")
                    }
                    return Result.failure() // Don't retry invalid image data
                }
            } catch (e: IOException) {
                Log.e(TAG, "❌ IOException while setting wallpaper: ${e.message}", e)
                return Result.failure()
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException - Missing SET_WALLPAPER permission: ${e.message}", e)
                return Result.failure()
            } finally {
                responseBody.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating wallpaper: ${e.message}", e)
            return Result.retry()
        }
    }

    companion object {
        const val TAG = "WallpaperUpdateWorker"
    }
}
