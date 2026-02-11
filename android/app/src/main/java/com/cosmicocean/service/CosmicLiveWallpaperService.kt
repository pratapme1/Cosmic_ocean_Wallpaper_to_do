package com.cosmicocean.service

import android.graphics.Bitmap
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.util.Log
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.utils.AchievementUtils
import com.cosmicocean.utils.AchievementSnapshot
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

    override fun onCreateEngine(): Engine {
        return CosmicEngine()
    }

    inner class CosmicEngine : Engine() {
        private val TAG = "CosmicEngine"
        private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var renderJob: Job? = null
        
        private lateinit var database: CosmicDatabase
        private lateinit var envRepo: EnvironmentPreferencesRepository
        private lateinit var prefsManager: WallpaperPreferencesManager

        // Cached bitmap for custom backgrounds to prevent re-decoding
        private var cachedBitmap: Bitmap? = null
        private var lastBitmapPath: String? = null

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
            serviceScope.cancel()
            cachedBitmap?.recycle()
            cachedBitmap = null
        }

        private fun startRendering() {
            if (renderJob?.isActive == true) return
            
            renderJob = serviceScope.launch {
                // Reactive data stream
                val tasksFlow = database.starDao().getTop3TasksFlow() // Use specific flow for list
                val allTasksFlow = database.starDao().getAllActiveStarsSyncFlow()
                val totalCountFlow = database.starDao().getActiveTaskCountFlow()
                val completionAtFlow = database.starDao().getLatestCompletionTimestampFlow()
                val envFlow = envRepo.preferencesFlow
                
                // Clock ticker
                val tickerFlow = flow {
                    while (currentCoroutineContext().isActive) {
                        emit(System.currentTimeMillis())
                        delay(60_000L) 
                    }
                }

                combine(
                    tasksFlow, envFlow, totalCountFlow, completionAtFlow, allTasksFlow, tickerFlow
                ) { arr ->
                    @Suppress("UNCHECKED_CAST")
                    val tasks = arr[0] as List<com.cosmicocean.data.StarEntity>
                    val env = arr[1] as EnvironmentPreferences
                    val total = arr[2] as Int
                    val completionAt = arr[3] as? Long
                    val allTasks = arr[4] as List<com.cosmicocean.data.StarEntity>
                    
                    val mode = prefsManager.getWallpaperMode()
                    val theme = WallpaperTheme.fromString(prefsManager.getTheme())
                    
                    // Fetch achievements
                    val achievements = AchievementUtils.getSnapshot(applicationContext)
                    
                    val metadata = mutableMapOf<String, Any>()
                    metadata["mode"] = mode
                    metadata["theme"] = theme
                    metadata["total"] = total
                    if (completionAt != null) metadata["completionAt"] = completionAt
                    metadata["achievements"] = achievements
                    metadata["allTasks"] = allTasks
                    
                    Triple(tasks, env, metadata.toMap())
                }.collect { (tasks, env, metadata) ->
                    draw(tasks, env, metadata)
                }
            }
        }

        private fun stopRendering() {
            renderJob?.cancel()
            renderJob = null
        }

        private fun draw(tasks: List<com.cosmicocean.data.StarEntity>, env: EnvironmentPreferences, metadata: Map<String, Any>) {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            
            try {
                val width = canvas.width
                val height = canvas.height
                val theme = metadata["theme"] as WallpaperTheme
                val totalCount = metadata["total"] as Int
                val completionAt = metadata["completionAt"] as? Long
                val achievements = metadata["achievements"] as AchievementSnapshot
                val allTasks = metadata["allTasks"] as List<com.cosmicocean.data.StarEntity>
                val mode = metadata["mode"] as String

                if (mode == WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM) {
                    val path = prefsManager.getCustomWallpaperPath()
                    if (path != null) {
                        val bitmap = getOrLoadBitmap(path)
                        if (bitmap != null) {
                            LocalWallpaperGenerator.renderWithCustomBackground(
                                canvas, width, height, tasks, totalCount, bitmap,
                                achievements.achievementCount, achievements.streakDays,
                                theme, env, allTasks, completionAt
                            )
                        } else {
                            LocalWallpaperGenerator.render(
                                canvas, width, height, tasks, totalCount, theme,
                                achievements.achievementCount, achievements.streakDays,
                                env, allTasks, completionAt
                            )
                        }
                    }
                } else {
                    LocalWallpaperGenerator.render(
                        canvas, width, height, tasks, totalCount, theme,
                        achievements.achievementCount, achievements.streakDays,
                        env, allTasks, completionAt
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Render failed", e)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
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
    }
}