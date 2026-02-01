package com.cosmicocean.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StarDao {
    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0")
    fun getAllActiveStars(): Flow<List<StarEntity>>

    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0")
    suspend fun getAllActiveStarsSync(): List<StarEntity>

    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0 AND isCompleted = 0 ORDER BY urgency ASC, dueDate ASC LIMIT 1")
    suspend fun getTopTask(): StarEntity?

    @Query("SELECT * FROM stars WHERE id = :id")
    suspend fun getById(id: String): StarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStar(star: StarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStars(stars: List<StarEntity>)

    @Delete
    suspend fun deleteStar(star: StarEntity)

    @Query("DELETE FROM stars WHERE id = :id")
    suspend fun deleteStarById(id: String)

    @Query("DELETE FROM stars")
    suspend fun deleteAllStars()

    // === Local-First Sync Methods ===

    @Query("SELECT * FROM stars WHERE syncStatus != 'synced'")
    suspend fun getPendingSyncTasks(): List<StarEntity>

    @Query("SELECT * FROM stars WHERE updatedAt > :since")
    suspend fun getTasksSince(since: Long): List<StarEntity>

    @Query("UPDATE stars SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE stars SET syncStatus = 'error', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSyncError(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE stars SET isDeleted = 1, syncStatus = 'pending', updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE stars SET isCompleted = 1, completedAt = :completedAt, syncStatus = 'pending', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT MAX(updatedAt) FROM stars WHERE syncStatus = 'synced'")
    suspend fun getLastSyncTimestamp(): Long?
}

@Dao
interface ConstellationDao {
    @Query("SELECT * FROM constellation_links")
    fun getAllLinks(): Flow<List<ConstellationLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ConstellationLinkEntity)

    @Query("DELETE FROM constellation_links")
    suspend fun deleteAllLinks()
}

@Dao
interface OrbitDao {
    @Query("SELECT * FROM orbital_relationships")
    fun getAllOrbits(): Flow<List<OrbitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrbit(orbit: OrbitEntity)

    @Query("DELETE FROM orbital_relationships")
    suspend fun deleteAllOrbits()
}

@Dao
interface TrophyDao {
    @Query("SELECT * FROM trophies WHERE userId = :userId ORDER BY milestone ASC")
    fun getAllTrophies(userId: String): Flow<List<TrophyEntity>>

    @Query("SELECT * FROM trophies WHERE userId = :userId ORDER BY milestone ASC")
    suspend fun getAllTrophiesSync(userId: String): List<TrophyEntity>

    @Query("SELECT * FROM trophies WHERE userId = :userId AND milestone = :milestone LIMIT 1")
    suspend fun getTrophyByMilestone(userId: String, milestone: Int): TrophyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrophy(trophy: TrophyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrophies(trophies: List<TrophyEntity>)

    @Update
    suspend fun updateTrophy(trophy: TrophyEntity)

    @Query("DELETE FROM trophies WHERE userId = :userId")
    suspend fun deleteAllTrophies(userId: String)

    @Query("SELECT COUNT(*) FROM trophies WHERE userId = :userId")
    suspend fun getTrophyCount(userId: String): Int
}

@Dao
interface AchievementStatsDao {
    @Query("SELECT * FROM achievement_stats WHERE userId = :userId LIMIT 1")
    fun getStats(userId: String): Flow<AchievementStatsEntity?>

    @Query("SELECT * FROM achievement_stats WHERE userId = :userId LIMIT 1")
    suspend fun getStatsSync(userId: String): AchievementStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: AchievementStatsEntity)

    @Update
    suspend fun updateStats(stats: AchievementStatsEntity)

    @Query("DELETE FROM achievement_stats WHERE userId = :userId")
    suspend fun deleteStats(userId: String)
}

/**
 * Sync Queue DAO
 * Manages pending sync operations for local-first architecture
 */
@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE taskId = :taskId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForTask(taskId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}
