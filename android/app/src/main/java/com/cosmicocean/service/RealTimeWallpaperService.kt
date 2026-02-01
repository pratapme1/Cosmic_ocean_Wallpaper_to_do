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
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.cosmicocean.MainActivity
import com.cosmicocean.R
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
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
    private var currentUpdateJob: Job? = null

    companion object {
        private const val TAG = "RealTimeWallpaper"
        private const val CHANNEL_ID = "wallpaper_update_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 60_000L // 1 minute
        private const val MAX_RETRY_COUNT = 3
        private const val INITIAL_RETRY_DELAY_MS = 5_000L // 5 seconds
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L // 30 seconds max

        private const val ACTION_FORCE_UPDATE = "com.cosmicocean.service.FORCE_UPDATE"

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

        @JvmStatic
        fun updateNow(context: Context) {
            Log.d(TAG, "Requesting immediate wallpaper update")
            val intent = Intent(context, RealTimeWallpaperService::class.java).apply {
                action = ACTION_FORCE_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
        Log.d(TAG, "Service onStartCommand: action=${intent?.action}")
        
        // ALWAYS call startForeground for all intents to satisfy system requirements
        startForeground(NOTIFICATION_ID, createNotification())

        if (intent?.action == ACTION_FORCE_UPDATE) {
            Log.d(TAG, "Force update requested - scheduling with 500ms delay for sync consistency")
            // Serialization: Cancel existing tick but schedule a fresh one after this update
            handler.removeCallbacks(updateRunnable)
            
            // Artificial delay (500ms) to ensure backend DB has committed task changes
            handler.postDelayed({
                updateWallpaper()
                handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
            }, 500)
        } else {
             if (!isUpdating) {
                 startUpdates()
             }
        }
        
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
        val tokenManager = TokenManager(applicationContext)
        val userId = tokenManager.getUserId()
        if (userId == null) {
            Log.w(TAG, "❌ No user logged in (TokenManager), skipping update")
            return
        }

        // FIX 3: Acquire wake lock before starting update
        acquireWakeLock()

        // SERALIZATION FIX: Cancel any existing update job to prevent overlapping downloads/sets
        currentUpdateJob?.cancel()

        currentUpdateJob = serviceScope.launch {
            try {
                val prefsManager = WallpaperPreferencesManager(applicationContext)
                val theme = prefsManager.getTheme()
                val resolution = prefsManager.getResolution()

                // LOCAL-FIRST: Check network and decide path
                val bitmap = if (isOnline()) {
                    // Online: Try backend first, fallback to local
                    fetchWallpaperFromBackend(theme, resolution)
                        ?: generateWallpaperLocally(theme)
                } else {
                    // Offline: Generate locally
                    Log.d(TAG, "Offline mode: generating wallpaper locally")
                    generateWallpaperLocally(theme)
                }

                if (bitmap != null) {
                    setWallpaperBitmap(bitmap)
                    retryCount = 0
                } else {
                    Log.e(TAG, "Failed to generate wallpaper")
                    scheduleRetry("wallpaper generation failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update failed: ${e.message}")
                // Try local generation as last resort
                try {
                    val prefsManager = WallpaperPreferencesManager(applicationContext)
                    val localBitmap = generateWallpaperLocally(prefsManager.getTheme())
                    if (localBitmap != null) {
                        setWallpaperBitmap(localBitmap)
                        Log.d(TAG, "Fallback to local generation succeeded")
                    } else {
                        scheduleRetry("exception: ${e.message}")
                    }
                } catch (e2: Exception) {
                    scheduleRetry("local fallback failed: ${e2.message}")
                }
            } finally {
                // FIX 3: Release wake lock after update completes
                releaseWakeLock()
            }
        }
    }

    /**
     * Fetch wallpaper from backend API
     */
    private suspend fun fetchWallpaperFromBackend(theme: String, resolution: String): Bitmap? {
        return try {
            val timestamp = System.currentTimeMillis()
            val timezone = java.util.TimeZone.getDefault().id

            Log.d(TAG, "Fetching wallpaper from backend: theme=$theme, resolution=$resolution")

            val response = NetworkModule.getApi(applicationContext).getWallpaper(
                theme = theme,
                resolution = resolution,
                enhanced = true,
                timestamp = timestamp,
                timezone = timezone
            )

            if (response.isSuccessful && response.body() != null) {
                val bytes = response.body()!!.bytes()
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                response.body()?.close()

                if (bitmap != null) {
                    Log.d(TAG, "Backend wallpaper fetched: ${bitmap.width}x${bitmap.height}")
                }
                bitmap
            } else {
                Log.e(TAG, "Backend API error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Generate wallpaper locally (offline capable)
     */
    private suspend fun generateWallpaperLocally(themeName: String): Bitmap? {
        return try {
            val db = CosmicDatabase.getDatabase(applicationContext)
            val topTask = db.starDao().getTopTask()
            val theme = WallpaperTheme.fromString(themeName)
            val (width, height) = getScreenResolution()

            Log.d(TAG, "Generating local wallpaper: ${width}x${height}, task=${topTask?.title}")

            LocalWallpaperGenerator.generate(
                task = topTask,
                theme = theme,
                width = width,
                height = height
            )
        } catch (e: Exception) {
            Log.e(TAG, "Local generation failed: ${e.message}")
            null
        }
    }

    /**
     * Set wallpaper bitmap
     */
    private fun setWallpaperBitmap(bitmap: Bitmap) {
        val wallpaperManager = WallpaperManager.getInstance(applicationContext)
        wallpaperManager.setBitmap(
            bitmap,
            null,
            true,
            WallpaperManager.FLAG_LOCK
        )
        Log.d(TAG, "Wallpaper set successfully: ${bitmap.width}x${bitmap.height}")
    }

    /**
     * Get screen resolution
     */
    private fun getScreenResolution(): Pair<Int, Int> {
        val windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * Check network connectivity
     */
    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // FIX 2: Retry logic with aggressive exponential backoff to prevent server spam
    private fun scheduleRetry(reason: String) {
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            // Increase initial delay to 10s, max 60s
            // Sequence: 10s, 30s, 60s
            val multiplier = if (retryCount == 1) 1 else if (retryCount == 2) 3 else 6
            val delay = 10_000L * multiplier
            
            Log.w(TAG, "Scheduling retry #$retryCount in ${delay}ms (reason: $reason)")
            handler.postDelayed({
                if (isUpdating) {
                    updateWallpaper()
                }
            }, delay)
        } else {
            Log.e(TAG, "Max retries ($MAX_RETRY_COUNT) reached. Will try again at next scheduled interval (60s).")
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
                        Log.d(TAG, "Screen OFF - scheduling update (debounced 2s)")
                        // Debounce updates to prevent spamming while toggling screen
                        handler.removeCallbacks(updateRunnable) 
                        handler.postDelayed({ 
                             updateWallpaper() 
                             // Restart periodic timer from this point
                             handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
                        }, 2000)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                         // Optional: Might skip update on SCREEN_ON if we trust background updates
                         // But for now, just debounce it too
                         Log.d(TAG, "Screen ON - scheduling update (debounced 2s)")
                         handler.removeCallbacks(updateRunnable)
                         handler.postDelayed({ 
                             updateWallpaper()
                             handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
                         }, 2000)
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
