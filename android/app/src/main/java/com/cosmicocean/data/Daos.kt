package com.cosmicocean.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StarDao {
    @Query("SELECT * FROM stars WHERE isArchived = 0")
    fun getAllActiveStars(): Flow<List<StarEntity>>

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
