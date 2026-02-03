package com.cosmicocean.utils

import android.content.Context
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.network.LocalOnlyUserStore

data class AchievementSnapshot(
    val achievementCount: Int,
    val streakDays: Int
)

object AchievementUtils {
    suspend fun getSnapshot(context: Context): AchievementSnapshot {
        val db = CosmicDatabase.getDatabase(context)
        val userId = resolveUserId(context)
        val stats = db.achievementStatsDao().getStatsSync(userId)
        val totalCompletions = stats?.totalCompletions ?: 0
        val trophyCount = db.trophyDao().getTrophyCount(userId)
        val streakDays = stats?.currentStreak ?: 0

        // Use total completions for the wallpaper count so achievements reflect task completion volume.
        val displayCount = if (totalCompletions > 0) totalCompletions else trophyCount
        return AchievementSnapshot(displayCount, streakDays)
    }

    private fun resolveUserId(context: Context): String {
        return if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
            LocalOnlyUserStore(context).getOrCreateUser().id
        } else {
            TokenManager(context).getUserId()
                ?: LocalOnlyUserStore(context).getOrCreateUser().id
        }
    }
}
