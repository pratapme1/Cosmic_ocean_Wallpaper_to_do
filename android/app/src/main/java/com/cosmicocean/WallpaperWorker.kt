package com.cosmicocean

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.app.WallpaperManager
import android.util.Log
import kotlin.random.Random

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("WallpaperWorker", "Starting background wallpaper update...")

        // Refresh remote "Vi" reminders on every cycle. Guarded so an offline
        // device or a bad fetch can never crash or fail the worker - the
        // repository keeps serving the last cached copy.
        try {
            com.cosmicocean.reminders.RemoteRemindersRepository
                .getInstance(applicationContext)
                .refresh()
        } catch (e: Exception) {
            Log.w("WallpaperWorker", "Vi reminders refresh failed: ${e.message}")
        }

        // The live wallpaper engine renders reactively (including remote
        // reminders); overwriting it with a static bitmap would clobber it.
        if (isLiveWallpaperActive()) {
            Log.d("WallpaperWorker", "Live wallpaper active - skipping static update.")
            return Result.success()
        }

        val service = WallpaperUpdateService(applicationContext)

        // Generate a "Cosmic" bitmap
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw Gradient Background (Deep Space)
        val paint = Paint()
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.parseColor("#0D47A1"), // Dark Blue
            Color.parseColor("#000000"), // Black
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw some "Stars"
        paint.shader = null
        paint.color = Color.WHITE
        val random = Random(System.currentTimeMillis())
        repeat(100) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val radius = random.nextFloat() * 3f
            canvas.drawCircle(x, y, radius, paint)
        }
        
        // Set wallpaper for BOTH Home and Lock screen
        val success = service.updateWallpaper(bitmap, WallpaperManager.FLAG_LOCK)
        
        return if (success) {
            Log.d("WallpaperWorker", "Background wallpaper update successful.")
            Result.success()
        } else {
            Log.e("WallpaperWorker", "Background wallpaper update failed.")
            Result.retry()
        }
    }

    private fun isLiveWallpaperActive(): Boolean {
        return try {
            val info = WallpaperManager.getInstance(applicationContext).wallpaperInfo
            info?.serviceName == com.cosmicocean.service.CosmicLiveWallpaperService::class.java.name
        } catch (e: Exception) {
            false
        }
    }
}
