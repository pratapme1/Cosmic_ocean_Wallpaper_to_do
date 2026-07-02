package com.cosmicocean

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val UNIQUE_WORK_NAME = "wallpaper_refresh_worker"
        const val WORK_MANAGER_REFRESH_INTERVAL_MINUTES = 15L
    }

    override suspend fun doWork(): Result {
        Log.d("WallpaperWorker", "Starting background reminders refresh...")

        // WorkManager cannot guarantee a five-minute periodic cadence (Android
        // enforces a 15-minute minimum), so this worker is only a coarse
        // background cache refresh. The live wallpaper engine owns rendering
        // and performs the five-minute refresh while it is running.
        try {
            com.cosmicocean.reminders.RemoteRemindersRepository
                .getInstance(applicationContext)
                .refresh()
        } catch (e: Exception) {
            Log.w("WallpaperWorker", "Vi reminders refresh failed: ${e.message}")
        }
        return Result.success()
    }
}
