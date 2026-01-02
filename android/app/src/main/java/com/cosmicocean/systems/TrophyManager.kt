package com.cosmicocean.systems

import com.cosmicocean.data.AchievementStatsEntity
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.TrophyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

/**
 * Trophy/Achievement Manager
 * PWA-accurate milestone tracking: 1, 10, 25, 50, 100, 250, 500, 1000 completions
 * Tier progression: Bronze → Silver → Gold → Platinum → Cosmic
 */
class TrophyManager(
    private val database: CosmicDatabase,
    private val userId: String
) {

    companion object {
        // Milestone thresholds (matches PWA)
        val MILESTONES = listOf(1, 10, 25, 50, 100, 250, 500, 1000)

        // Tier mapping
        fun getTier(milestone: Int): String {
            return when (milestone) {
                1 -> "bronze"
                10 -> "bronze"
                25 -> "silver"
                50 -> "silver"
                100 -> "gold"
                250 -> "gold"
                500 -> "platinum"
                1000 -> "cosmic"
                else -> "bronze"
            }
        }

        // Trophy display names
        fun getTrophyName(milestone: Int): String {
            return when (milestone) {
                1 -> "First Step"
                10 -> "Getting Started"
                25 -> "Quarter Century"
                50 -> "Half Century"
                100 -> "Centurion"
                250 -> "Champion"
                500 -> "Legend"
                1000 -> "Cosmic Master"
                else -> "Achievement"
            }
        }

        // Trophy descriptions
        fun getTrophyDescription(milestone: Int): String {
            return when (milestone) {
                1 -> "Completed your first task!"
                10 -> "Completed 10 tasks"
                25 -> "Completed 25 tasks"
                50 -> "Completed 50 tasks"
                100 -> "Completed 100 tasks"
                250 -> "Completed 250 tasks"
                500 -> "Completed 500 tasks"
                1000 -> "Completed 1000 tasks - You are a true master!"
                else -> "Milestone completed"
            }
        }
    }

    /**
     * Get all unlocked trophies for the user
     */
    fun getTrophies(): Flow<List<TrophyEntity>> {
        return database.trophyDao().getAllTrophies(userId)
    }

    /**
     * Get achievement stats for the user
     */
    fun getStats(): Flow<AchievementStatsEntity?> {
        return database.achievementStatsDao().getStats(userId)
    }

    /**
     * Record a task completion and check for milestone unlocks
     * Returns newly unlocked trophies (if any)
     */
    suspend fun recordCompletion(): List<TrophyEntity> {
        val newlyUnlocked = mutableListOf<TrophyEntity>()

        // Get or create stats
        var stats = database.achievementStatsDao().getStatsSync(userId)
        if (stats == null) {
            stats = AchievementStatsEntity(userId = userId)
            database.achievementStatsDao().insertStats(stats)
        }

        // Increment total completions
        val newTotal = stats.totalCompletions + 1
        val updatedStats = stats.copy(
            totalCompletions = newTotal,
            lastCompletionDate = System.currentTimeMillis()
        )
        database.achievementStatsDao().updateStats(updatedStats)

        // Check for milestone unlocks
        for (milestone in MILESTONES) {
            if (newTotal == milestone) {
                // Unlock this milestone!
                val trophy = TrophyEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    milestone = milestone,
                    tier = getTier(milestone),
                    unlockedAt = System.currentTimeMillis(),
                    shareCount = 0
                )
                database.trophyDao().insertTrophy(trophy)
                newlyUnlocked.add(trophy)
            }
        }

        return newlyUnlocked
    }

    /**
     * Update daily stats (max completions per day)
     */
    suspend fun updateDailyStats(completionsToday: Int) {
        val stats = database.achievementStatsDao().getStatsSync(userId) ?: return

        if (completionsToday > stats.dailyMaxCompletions) {
            val updatedStats = stats.copy(dailyMaxCompletions = completionsToday)
            database.achievementStatsDao().updateStats(updatedStats)
        }
    }

    /**
     * Update streak information
     */
    suspend fun updateStreak(currentStreak: Int) {
        val stats = database.achievementStatsDao().getStatsSync(userId) ?: return

        val longestStreak = maxOf(stats.longestStreak, currentStreak)
        val updatedStats = stats.copy(
            currentStreak = currentStreak,
            longestStreak = longestStreak
        )
        database.achievementStatsDao().updateStats(updatedStats)
    }

    /**
     * Record fastest completion time
     */
    suspend fun recordCompletionTime(timeMs: Long) {
        val stats = database.achievementStatsDao().getStatsSync(userId) ?: return

        if (stats.fastestCompletion == 0L || timeMs < stats.fastestCompletion) {
            val updatedStats = stats.copy(fastestCompletion = timeMs)
            database.achievementStatsDao().updateStats(updatedStats)
        }
    }

    /**
     * Get total trophy count
     */
    suspend fun getTrophyCount(): Int {
        return database.trophyDao().getTrophyCount(userId)
    }

    /**
     * Get next milestone to unlock
     */
    suspend fun getNextMilestone(): Int? {
        val stats = database.achievementStatsDao().getStatsSync(userId) ?: return MILESTONES.first()
        val total = stats.totalCompletions

        return MILESTONES.firstOrNull { it > total }
    }

    /**
     * Get progress to next milestone (0.0 to 1.0)
     */
    suspend fun getProgressToNextMilestone(): Float {
        val stats = database.achievementStatsDao().getStatsSync(userId) ?: return 0f
        val total = stats.totalCompletions

        val nextMilestone = MILESTONES.firstOrNull { it > total } ?: return 1f
        val previousMilestone = MILESTONES.lastOrNull { it <= total } ?: 0

        if (nextMilestone == previousMilestone) return 1f

        val progress = (total - previousMilestone).toFloat() /
                (nextMilestone - previousMilestone).toFloat()

        return progress.coerceIn(0f, 1f)
    }

    /**
     * Increment share count for a trophy
     */
    suspend fun incrementShareCount(trophyId: String) {
        val trophy = database.trophyDao().getAllTrophiesSync(userId)
            .firstOrNull { it.id == trophyId } ?: return

        val updatedTrophy = trophy.copy(shareCount = trophy.shareCount + 1)
        database.trophyDao().updateTrophy(updatedTrophy)
    }

    /**
     * Get trophy tier color
     */
    fun getTierColor(tier: String): Int {
        return when (tier.lowercase()) {
            "bronze" -> 0xFFCD7F32.toInt()
            "silver" -> 0xFFC0C0C0.toInt()
            "gold" -> 0xFFFFD700.toInt()
            "platinum" -> 0xFFE5E4E2.toInt()
            "cosmic" -> 0xFF9B59B6.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    /**
     * Get trophy tier emoji
     */
    fun getTierEmoji(tier: String): String {
        return when (tier.lowercase()) {
            "bronze" -> "🥉"
            "silver" -> "🥈"
            "gold" -> "🥇"
            "platinum" -> "💎"
            "cosmic" -> "🌌"
            else -> "🏆"
        }
    }

    /**
     * Check if user has unlocked specific trophy
     */
    suspend fun hasTrophy(milestone: Int): Boolean {
        return database.trophyDao().getTrophyByMilestone(userId, milestone) != null
    }

    /**
     * Get all trophy data for a user (for sharing/export)
     */
    suspend fun getTrophySummary(): TrophySummary {
        val trophies = database.trophyDao().getAllTrophiesSync(userId)
        val stats = database.achievementStatsDao().getStatsSync(userId)
            ?: AchievementStatsEntity(userId = userId)

        return TrophySummary(
            totalTrophies = trophies.size,
            totalCompletions = stats.totalCompletions,
            bronzeTrophies = trophies.count { it.tier == "bronze" },
            silverTrophies = trophies.count { it.tier == "silver" },
            goldTrophies = trophies.count { it.tier == "gold" },
            platinumTrophies = trophies.count { it.tier == "platinum" },
            cosmicTrophies = trophies.count { it.tier == "cosmic" },
            currentStreak = stats.currentStreak,
            longestStreak = stats.longestStreak,
            dailyMax = stats.dailyMaxCompletions
        )
    }
}

/**
 * Trophy summary data class
 */
data class TrophySummary(
    val totalTrophies: Int,
    val totalCompletions: Int,
    val bronzeTrophies: Int,
    val silverTrophies: Int,
    val goldTrophies: Int,
    val platinumTrophies: Int,
    val cosmicTrophies: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val dailyMax: Int
)
