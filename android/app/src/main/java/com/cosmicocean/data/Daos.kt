package com.cosmicocean.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StarDao {
    // === Query Methods ===
    
    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0 ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, urgency ASC")
    fun getAllActiveStars(): Flow<List<StarEntity>>
    
    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0")
    suspend fun getAllActiveStarsSync(): List<StarEntity>
    
    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0 AND isCompleted = 0 ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, urgency ASC LIMIT 1")
    suspend fun getTopTask(): StarEntity?
    
    @Query("SELECT * FROM stars WHERE isArchived = 0 AND isDeleted = 0 AND isCompleted = 0 ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, urgency ASC LIMIT 3")
    suspend fun getTop3Tasks(): List<StarEntity>
    
    @Query("SELECT COUNT(*) FROM stars WHERE isArchived = 0 AND isDeleted = 0 AND isCompleted = 0")
    suspend fun getActiveTaskCount(): Int
    
    @Query("SELECT * FROM stars WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): StarEntity?
    
    @Query("SELECT * FROM stars WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): StarEntity?
    
    @Query("SELECT * FROM stars WHERE localId = :localId OR serverId = :localId")
    suspend fun getById(localId: String): StarEntity?
    
    // === Transaction Methods (CRITICAL FIX: Issue #3) ===
    
    @Transaction
    suspend fun insertStarWithTransaction(star: StarEntity) {
        insertStar(star)
    }
    
    /**
     * CRITICAL FIX: Atomic replace operation for sync
     * Prevents data loss if app crashes mid-replace
     */
    @Transaction
    suspend fun replaceStar(oldLocalId: String, newStar: StarEntity) {
        deleteStarByLocalId(oldLocalId)
        insertStar(newStar)
    }
    
    /**
     * CRITICAL FIX: Update server ID after successful sync
     */
    @Transaction
    suspend fun updateServerIdAfterSync(localId: String, serverId: String, serverTimestamp: Long) {
        updateServerId(localId, serverId)
        updateSyncStatus(localId, "synced", serverTimestamp)
    }
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStar(star: StarEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStars(stars: List<StarEntity>)
    
    @Delete
    suspend fun deleteStar(star: StarEntity)
    
    @Query("DELETE FROM stars WHERE localId = :localId")
    suspend fun deleteStarByLocalId(localId: String)
    
    @Query("DELETE FROM stars WHERE serverId = :serverId")
    suspend fun deleteStarByServerId(serverId: String)
    
    @Query("DELETE FROM stars")
    suspend fun deleteAllStars()
    
    // === Local-First Sync Methods ===
    
    @Query("SELECT * FROM stars WHERE syncStatus != 'synced'")
    suspend fun getPendingSyncTasks(): List<StarEntity>
    
    @Query("SELECT * FROM stars WHERE updatedAt > :since")
    suspend fun getTasksSince(since: Long): List<StarEntity>
    
    @Query("UPDATE stars SET syncStatus = :status, serverUpdatedAt = :serverTimestamp, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: String, status: String, serverTimestamp: Long? = null, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE stars SET serverId = :serverId, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateServerId(localId: String, serverId: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE stars SET syncStatus = 'error', updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun markSyncError(localId: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE stars SET isDeleted = 1, syncStatus = 'pending', updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun softDelete(localId: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE stars SET isCompleted = 1, completedAt = :completedAt, syncStatus = 'pending', updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun markCompleted(localId: String, completedAt: Long, updatedAt: Long = System.currentTimeMillis())
    
    @Query("SELECT MAX(serverUpdatedAt) FROM stars WHERE syncStatus = 'synced'")
    suspend fun getLastServerSyncTimestamp(): Long?
    
    @Query("SELECT * FROM stars WHERE syncStatus = 'error' AND updatedAt < :cutoffTimestamp")
    suspend fun getOldErrorTasks(cutoffTimestamp: Long): List<StarEntity>
    
    @Query("SELECT COUNT(*) FROM stars WHERE syncStatus = 'error'")
    suspend fun getErrorTaskCount(): Int
}

@Dao
interface ConstellationDao {
    @Query("SELECT * FROM constellation_links")
    fun getAllLinks(): Flow<List<ConstellationLinkEntity>>
    
    @Query("SELECT * FROM constellation_links WHERE starA = :localId OR starB = :localId")
    suspend fun getLinksForStar(localId: String): List<ConstellationLinkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ConstellationLinkEntity)
    
    @Query("DELETE FROM constellation_links WHERE starA = :localId OR starB = :localId")
    suspend fun deleteLinksForStar(localId: String)
    
    @Query("DELETE FROM constellation_links")
    suspend fun deleteAllLinks()
}

@Dao
interface OrbitDao {
    @Query("SELECT * FROM orbital_relationships")
    fun getAllOrbits(): Flow<List<OrbitEntity>>
    
    @Query("SELECT * FROM orbital_relationships WHERE parentId = :parentId")
    suspend fun getOrbitsForParent(parentId: String): List<OrbitEntity>
    
    @Query("SELECT * FROM orbital_relationships WHERE childId = :childId")
    suspend fun getOrbitForChild(childId: String): OrbitEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrbit(orbit: OrbitEntity)
    
    @Query("DELETE FROM orbital_relationships WHERE childId = :childId")
    suspend fun deleteOrbitForChild(childId: String)
    
    @Query("DELETE FROM orbital_relationships WHERE parentId = :parentId OR childId = :parentId")
    suspend fun deleteOrbitsForStar(parentId: String)
    
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
 * CRITICAL FIX: Uses localTaskId instead of taskId for stable references
 */
@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>
    
    @Query("SELECT * FROM sync_queue WHERE localTaskId = :localTaskId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForTask(localTaskId: String): SyncQueueEntity?
    
    @Query("SELECT * FROM sync_queue WHERE localTaskId = :localTaskId ORDER BY createdAt ASC")
    suspend fun getAllForTask(localTaskId: String): List<SyncQueueEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncQueueEntity): Long
    
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM sync_queue WHERE localTaskId = :localTaskId")
    suspend fun deleteByLocalTaskId(localTaskId: String)
    
    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    
    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String)
    
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingCount(): Int
    
    @Query("DELETE FROM sync_queue WHERE createdAt < :cutoffTimestamp")
    suspend fun deleteOldEntries(cutoffTimestamp: Long)
    
    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}
