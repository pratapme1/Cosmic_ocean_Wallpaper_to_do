package com.cosmicocean.reminders

import android.content.Context
import android.util.Log
import com.cosmicocean.data.StarEntity
import com.cosmicocean.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Remote reminders source ("Vi" tasks).
 *
 * Fetches reminders.json from GitHub on the WallpaperWorker refresh cycle,
 * caches the last good copy on disk, and exposes the reminders as read-only
 * StarEntity instances for the wallpaper renderer. Fetch failures never
 * propagate: the cached copy keeps being served.
 */
class RemoteRemindersRepository private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "RemoteReminders"
        private const val REPO_OWNER = "pratapme1"
        private const val REPO_NAME = "Vi_assistant-documentation"
        private const val REPO_FILE_PATH = "reminders.json"
        private const val CACHE_FILE_NAME = "vi_reminders_cache.json"

        @Volatile
        private var INSTANCE: RemoteRemindersRepository? = null

        fun getInstance(context: Context): RemoteRemindersRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteRemindersRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val patManager = ViPatManager(appContext)
    private val cacheFile: File get() = File(appContext.filesDir, CACHE_FILE_NAME)

    private val _reminders = MutableStateFlow<List<StarEntity>>(emptyList())
    val remindersFlow: StateFlow<List<StarEntity>> = _reminders.asStateFlow()

    @Volatile
    private var cacheLoaded = false

    /**
     * Loads the last cached copy into the flow. Cheap and idempotent; called
     * before rendering so reminders survive process restarts while offline.
     */
    suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        cacheLoaded = true
        withContext(Dispatchers.IO) {
            try {
                if (cacheFile.exists()) {
                    val payload = ViReminderMapper.parsePayload(cacheFile.readText())
                    _reminders.value = ViReminderMapper.toStarEntities(payload)
                    Log.d(TAG, "Loaded ${_reminders.value.size} reminders from cache")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load reminders cache: ${e.message}")
            }
            Unit
        }
    }

    /**
     * Fetches the latest reminders from GitHub. Never throws. On any failure
     * (offline, bad PAT, malformed JSON) the previous cache stays in effect.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        ensureCacheLoaded()

        if (!patManager.hasPat()) {
            Log.d(TAG, "No PAT configured - skipping reminders fetch")
            return@withContext false
        }

        try {
            val response = NetworkModule.getGitHubApi(appContext)
                .getRawFile(REPO_OWNER, REPO_NAME, REPO_FILE_PATH)

            if (!response.isSuccessful) {
                Log.w(TAG, "Reminders fetch failed: HTTP ${response.code()} - keeping cache")
                return@withContext false
            }

            val body = response.body()?.string()
            if (body.isNullOrBlank()) {
                Log.w(TAG, "Reminders fetch returned empty body - keeping cache")
                return@withContext false
            }

            val payload = ViReminderMapper.parsePayload(body)
            if (payload?.reminders == null) {
                Log.w(TAG, "Reminders JSON malformed - keeping cache")
                return@withContext false
            }

            cacheFile.writeText(body)
            _reminders.value = ViReminderMapper.toStarEntities(payload)
            Log.d(TAG, "Fetched ${_reminders.value.size} reminders (generated_at=${payload.generatedAt})")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Reminders fetch error: ${e.message} - keeping cache")
            false
        }
    }
}
