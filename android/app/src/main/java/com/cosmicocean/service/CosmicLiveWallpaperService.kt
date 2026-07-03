package com.cosmicocean.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.util.Log
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.reminders.RemoteRemindersRepository
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.utils.WallpaperRenderPreferences
import com.cosmicocean.utils.AchievementUtils
import com.cosmicocean.utils.AchievementSnapshot
import com.cosmicocean.wallpaper.HudOverlayRenderConfig
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * The Native Live Wallpaper Engine.
 * Provides 100% parity with legacy bitmap generation but with battery efficiency and real-time reactivity.
 */
class CosmicLiveWallpaperService : WallpaperService() {
    companion object {
        const val REMOTE_REMINDER_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }

    override fun onCreateEngine(): Engine {
        return CosmicEngine()
    }

    inner class CosmicEngine : Engine() {
        private val TAG = "CosmicEngine"
        private var serviceScope = newServiceScope()
        private var renderJob: Job? = null
        private var remoteRefreshJob: Job? = null
        
        private lateinit var database: CosmicDatabase
        private lateinit var envRepo: EnvironmentPreferencesRepository
        private lateinit var prefsManager: WallpaperPreferencesManager

        // Cached bitmap for custom backgrounds to prevent re-decoding
        private var cachedBitmap: Bitmap? = null
        private var lastBitmapPath: String? = null
        private var cachedHudBitmap: Bitmap? = null
        private var lastHudUri: String? = null
        private var lastHudSurfaceWidth: Int = 0

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            database = CosmicDatabase.getDatabase(applicationContext)
            envRepo = EnvironmentPreferencesRepository(applicationContext)
            prefsManager = WallpaperPreferencesManager(applicationContext)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                startRendering()
            } else {
                stopRendering()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopRendering()
            recycleCachedBitmap()
        }

        override fun onDestroy() {
            stopRendering()
            serviceScope.cancel()
            recycleCachedBitmap()
            super.onDestroy()
        }

        private fun newServiceScope(): CoroutineScope {
            return CoroutineScope(Dispatchers.Default + SupervisorJob())
        }

        private fun ensureServiceScopeActive() {
            if (!serviceScope.isActive) {
                serviceScope = newServiceScope()
            }
        }

        private fun recycleCachedBitmap() {
            cachedBitmap?.recycle()
            cachedBitmap = null
            lastBitmapPath = null
            cachedHudBitmap?.recycle()
            cachedHudBitmap = null
            lastHudUri = null
            lastHudSurfaceWidth = 0
        }

        private fun startRendering() {
            if (renderJob?.isActive == true) return
            ensureServiceScopeActive()

            renderJob = serviceScope.launch {
                // Remote "Vi" reminders sync into Room on a five-minute cadence while
                // the wallpaper runs; the Room flows below pick them up like any task.
                val remindersRepo = RemoteRemindersRepository.getInstance(applicationContext)
                startRemoteRefreshLoop(remindersRepo)

                // Reactive data stream
                val tasksFlow = database.starDao().getTop3TasksFlow() // Use specific flow for list
                val allTasksFlow = database.starDao().getAllActiveStarsSyncFlow()
                val totalCountFlow = database.starDao().getActiveTaskCountFlow()
                val completionAtFlow = database.starDao().getLatestCompletionTimestampFlow()
                val envFlow = envRepo.preferencesFlow
                val wallpaperPrefsFlow = prefsManager.renderPreferencesFlow()

                // Clock ticker
                val tickerFlow = flow {
                    while (currentCoroutineContext().isActive) {
                        emit(System.currentTimeMillis())
                        delay(60_000L)
                    }
                }

                combine(
                    tasksFlow, envFlow, totalCountFlow, completionAtFlow, allTasksFlow, wallpaperPrefsFlow, tickerFlow
                ) { arr ->
                    @Suppress("UNCHECKED_CAST")
                    val tasks = arr[0] as List<com.cosmicocean.data.StarEntity>
                    val env = arr[1] as EnvironmentPreferences
                    val total = arr[2] as Int
                    val completionAt = arr[3] as? Long
                    @Suppress("UNCHECKED_CAST")
                    val allTasks = arr[4] as List<com.cosmicocean.data.StarEntity>
                    val wallpaperPrefs = arr[5] as WallpaperRenderPreferences

                    // Fetch achievements
                    val achievements = AchievementUtils.getSnapshot(applicationContext)

                    RenderFrame(
                        tasks = tasks,
                        env = env,
                        preferences = wallpaperPrefs,
                        totalCount = total,
                        completionAt = completionAt,
                        achievements = achievements,
                        allTasks = allTasks
                    )
                }.collect { frame ->
                    draw(frame)
                }
            }
        }

        private fun stopRendering() {
            renderJob?.cancel()
            renderJob = null
            remoteRefreshJob?.cancel()
            remoteRefreshJob = null
        }

        private fun startRemoteRefreshLoop(remindersRepo: RemoteRemindersRepository) {
            if (remoteRefreshJob?.isActive == true) return
            remoteRefreshJob = serviceScope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        remindersRepo.refresh()
                    } catch (e: Exception) {
                        Log.w(TAG, "Remote reminder refresh failed: ${e.message}")
                    }
                    delay(REMOTE_REMINDER_REFRESH_INTERVAL_MS)
                }
            }
        }

        private fun draw(frame: RenderFrame) {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            
            try {
                val width = canvas.width
                val height = canvas.height
                val theme = WallpaperTheme.fromString(frame.preferences.theme)

                val placement = com.cosmicocean.wallpaper.TaskPlacement.fromPref(frame.preferences.taskPlacement)
                val hudOverlay = getOrLoadHudOverlay(frame.preferences.hudOverlayUri, width)?.let { bitmap ->
                    HudOverlayRenderConfig(
                        bitmap = bitmap,
                        verticalPositionPercent = frame.preferences.hudOverlayVerticalPercent,
                        opacityPercent = frame.preferences.hudOverlayOpacityPercent
                    )
                }

                if (frame.preferences.wallpaperMode == WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM) {
                    val bitmap = frame.preferences.customWallpaperPath?.let { getOrLoadBitmap(it) }
                    if (bitmap != null) {
                        LocalWallpaperGenerator.renderWithCustomBackground(
                            canvas, width, height, frame.tasks, frame.totalCount, bitmap,
                            frame.achievements.achievementCount, frame.achievements.streakDays,
                            theme, frame.env, frame.allTasks, frame.completionAt, placement, hudOverlay
                        )
                    } else {
                        renderGenerated(canvas, width, height, frame, theme, placement, hudOverlay)
                    }
                } else {
                    renderGenerated(canvas, width, height, frame, theme, placement, hudOverlay)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Render failed", e)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun renderGenerated(
            canvas: android.graphics.Canvas,
            width: Int,
            height: Int,
            frame: RenderFrame,
            theme: WallpaperTheme,
            placement: com.cosmicocean.wallpaper.TaskPlacement,
            hudOverlay: HudOverlayRenderConfig?
        ) {
            LocalWallpaperGenerator.render(
                canvas, width, height, frame.tasks, frame.totalCount, theme,
                frame.achievements.achievementCount, frame.achievements.streakDays,
                frame.env, frame.allTasks, frame.completionAt, placement, hudOverlay
            )
        }

        private fun getOrLoadBitmap(path: String): Bitmap? {
            if (path == lastBitmapPath && cachedBitmap != null && !cachedBitmap!!.isRecycled) {
                return cachedBitmap
            }
            
            cachedBitmap?.recycle()
            cachedBitmap = null
            
            return try {
                val file = File(path)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                    cachedBitmap = bitmap
                    lastBitmapPath = path
                    bitmap
                } else null
            } catch (e: Exception) {
                null
            }
        }

        private fun getOrLoadHudOverlay(uriString: String?, surfaceWidth: Int): Bitmap? {
            if (uriString.isNullOrBlank()) {
                cachedHudBitmap?.recycle()
                cachedHudBitmap = null
                lastHudUri = null
                lastHudSurfaceWidth = 0
                return null
            }

            val cached = cachedHudBitmap
            if (
                cached != null &&
                !cached.isRecycled &&
                uriString == lastHudUri &&
                surfaceWidth == lastHudSurfaceWidth
            ) {
                return cached
            }

            cachedHudBitmap?.recycle()
            cachedHudBitmap = null
            lastHudUri = null
            lastHudSurfaceWidth = 0

            return try {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                val maxDecodedWidth = (surfaceWidth * 2).coerceAtLeast(1)
                val sampleSize = calculateHudSampleSize(bounds.outWidth, maxDecodedWidth)
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                }

                if (decoded != null && !decoded.isRecycled) {
                    cachedHudBitmap = decoded
                    lastHudUri = uriString
                    lastHudSurfaceWidth = surfaceWidth
                }
                decoded
            } catch (e: Exception) {
                Log.w(TAG, "HUD overlay decode skipped: ${e.message}")
                null
            }
        }

        private fun calculateHudSampleSize(sourceWidth: Int, maxWidth: Int): Int {
            var sampleSize = 1
            while (sourceWidth / sampleSize > maxWidth) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}

private data class RenderFrame(
    val tasks: List<com.cosmicocean.data.StarEntity>,
    val env: EnvironmentPreferences,
    val preferences: WallpaperRenderPreferences,
    val totalCount: Int,
    val completionAt: Long?,
    val achievements: AchievementSnapshot,
    val allTasks: List<com.cosmicocean.data.StarEntity>
)
