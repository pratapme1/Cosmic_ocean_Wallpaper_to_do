package com.cosmicocean.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cosmicocean.MainActivity
import com.cosmicocean.R
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.utils.UserSession
import com.cosmicocean.utils.WallpaperPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground Service for real-time wallpaper updates.
 * Updates wallpaper every minute for accurate clock display.
 *
 * FIXES APPLIED (2026-01-09):
 * - FIX 1: Removed isScreenOff() check - now updates regardless of screen state
 * - FIX 2: Added retry logic with exponential backoff on network failures
 * - FIX 3: Added partial wake lock to prevent service from being killed during updates
 * - FIX 4: Immediate update on screen OFF for fresh lock screen
 */
class RealTimeWallpaperService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isUpdating = false
    private var screenReceiver: BroadcastReceiver? = null
    private var retryCount = 0
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "RealTimeWallpaper"
        private const val CHANNEL_ID = "wallpaper_update_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 60_000L // 1 minute
        private const val MAX_RETRY_COUNT = 3
        private const val INITIAL_RETRY_DELAY_MS = 5_000L // 5 seconds
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L // 30 seconds max

        fun start(context: Context) {
            val intent = Intent(context, RealTimeWallpaperService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RealTimeWallpaperService::class.java))
        }
    }

    // FIX 1: Removed isScreenOff() check - always update wallpaper
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Always update wallpaper regardless of screen state
            // This ensures task changes are reflected immediately
            updateWallpaper()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        initWakeLock()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        startForeground(NOTIFICATION_ID, createNotification())
        startUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopUpdates()
        unregisterScreenReceiver()
        releaseWakeLock()
        Log.d(TAG, "Service destroyed")
    }

    // FIX 3: Initialize wake lock for reliable updates
    private fun initWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CosmicOcean:WallpaperUpdate"
        )
    }

    private fun acquireWakeLock() {
        try {
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(WAKE_LOCK_TIMEOUT_MS)
                    Log.d(TAG, "Wake lock acquired")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock: ${e.message}")
        }
    }

    private fun startUpdates() {
        isUpdating = true
        // Initial update immediately
        updateWallpaper()
        // Schedule periodic updates
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
        Log.d(TAG, "Updates scheduled every ${UPDATE_INTERVAL_MS / 1000}s (always, regardless of screen state)")
    }

    private fun stopUpdates() {
        isUpdating = false
        handler.removeCallbacks(updateRunnable)
        Log.d(TAG, "Updates stopped")
    }

    private fun updateWallpaper() {
        val userId = UserSession.getUserId(applicationContext)
        if (userId == null) {
            Log.w(TAG, "No user logged in, skipping update")
            return
        }

        // FIX 3: Acquire wake lock before starting update
        acquireWakeLock()

        serviceScope.launch {
            try {
                val prefsManager = WallpaperPreferencesManager(applicationContext)
                val theme = prefsManager.getTheme()
                val resolution = prefsManager.getResolution()
                val timestamp = System.currentTimeMillis()
                val timezone = java.util.TimeZone.getDefault().id // e.g., "Asia/Kolkata"

                Log.d(TAG, "Fetching wallpaper: theme=$theme, resolution=$resolution, timezone=$timezone, ts=$timestamp")

                val response = NetworkModule.getApi(applicationContext).getWallpaper(
                    theme = theme,
                    resolution = resolution,
                    enhanced = true,
                    timestamp = timestamp,
                    timezone = timezone
                )

                if (response.isSuccessful && response.body() != null) {
                    val inputStream = response.body()!!.byteStream()
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

                    if (bitmap != null) {
                        val wallpaperManager = WallpaperManager.getInstance(applicationContext)

                        // RACE CONDITION FIX (2026-01-09):
                        // Do NOT call clear() before setBitmap() - if setBitmap fails, wallpaper stays blank
                        // setBitmap() atomically replaces the existing wallpaper
                        val wallpaperFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK

                        wallpaperManager.setBitmap(
                            bitmap,
                            null,
                            true,
                            wallpaperFlags  // Update BOTH home and lock screen
                        )

                        Log.d(TAG, "Wallpaper updated successfully: ${bitmap.width}x${bitmap.height}")
                        // Reset retry count on success
                        retryCount = 0
                    } else {
                        Log.e(TAG, "Failed to decode bitmap")
                        scheduleRetry("bitmap decode failed")
                    }

                    response.body()?.close()
                } else {
                    Log.e(TAG, "API error: ${response.code()}")
                    // FIX 2: Retry on server errors (5xx)
                    if (response.code() in 500..599) {
                        scheduleRetry("server error ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update failed: ${e.message}")
                // FIX 2: Retry on network failures
                scheduleRetry("exception: ${e.message}")
            } finally {
                // FIX 3: Release wake lock after update completes
                releaseWakeLock()
            }
        }
    }

    // FIX 2: Retry logic with exponential backoff
    private fun scheduleRetry(reason: String) {
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            val delay = INITIAL_RETRY_DELAY_MS * retryCount // 5s, 10s, 15s
            Log.w(TAG, "Scheduling retry #$retryCount in ${delay}ms (reason: $reason)")
            handler.postDelayed({
                if (isUpdating) {
                    updateWallpaper()
                }
            }, delay)
        } else {
            Log.e(TAG, "Max retries ($MAX_RETRY_COUNT) reached. Will try again at next scheduled interval.")
            retryCount = 0 // Reset for next interval
        }
    }

    private fun isScreenOff(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isInteractive
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF - triggering immediate update for fresh lock screen")
                        // Immediate update when screen turns off for fresh lock screen
                        updateWallpaper()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen ON - triggering update for latest task state")
                        // FIX 1: Also update when screen turns on to show latest state
                        updateWallpaper()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        screenReceiver = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your lock screen wallpaper up to date"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cosmic Ocean")
            .setContentText("Keeping your lock screen updated")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
