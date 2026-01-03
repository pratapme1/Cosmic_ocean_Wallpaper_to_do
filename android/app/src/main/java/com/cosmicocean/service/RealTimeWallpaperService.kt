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
 * Battery optimization:
 * - Only runs when screen is OFF (lock screen visible)
 * - Stops when screen is ON (user is using phone)
 * - Uses efficient scheduling with Handler
 */
class RealTimeWallpaperService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isUpdating = false
    private var screenReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "RealTimeWallpaper"
        private const val CHANNEL_ID = "wallpaper_update_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 60_000L // 1 minute

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

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isScreenOff()) {
                updateWallpaper()
            }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
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
        Log.d(TAG, "Service destroyed")
    }

    private fun startUpdates() {
        isUpdating = true
        // Initial update
        if (isScreenOff()) {
            updateWallpaper()
        }
        // Schedule periodic updates
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
        Log.d(TAG, "Updates scheduled every ${UPDATE_INTERVAL_MS / 1000}s")
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

        serviceScope.launch {
            try {
                val prefsManager = WallpaperPreferencesManager(applicationContext)
                val theme = prefsManager.getTheme()
                val resolution = prefsManager.getResolution()
                val timestamp = System.currentTimeMillis()

                Log.d(TAG, "Fetching wallpaper: theme=$theme, resolution=$resolution, ts=$timestamp")

                val response = NetworkModule.getApi(applicationContext).getWallpaper(
                    theme = theme,
                    resolution = resolution,
                    enhanced = true,
                    timestamp = timestamp
                )

                if (response.isSuccessful && response.body() != null) {
                    val inputStream = response.body()!!.byteStream()
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

                    if (bitmap != null) {
                        val wallpaperManager = WallpaperManager.getInstance(applicationContext)

                        // Clear and set for reliable update
                        try {
                            wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                            Thread.sleep(50)
                        } catch (e: Exception) {
                            // Ignore clear errors
                        }

                        wallpaperManager.setBitmap(
                            bitmap,
                            null,
                            true,
                            WallpaperManager.FLAG_LOCK
                        )

                        Log.d(TAG, "Wallpaper updated: ${bitmap.width}x${bitmap.height}")
                    } else {
                        Log.e(TAG, "Failed to decode bitmap")
                    }

                    response.body()?.close()
                } else {
                    Log.e(TAG, "API error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update failed: ${e.message}")
            }
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
                        Log.d(TAG, "Screen OFF - starting updates")
                        // Immediate update when screen turns off
                        updateWallpaper()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen ON - updates continue in background")
                        // Keep updating even when screen is on, for task changes
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
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
