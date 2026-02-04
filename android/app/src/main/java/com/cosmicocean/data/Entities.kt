package com.cosmicocean.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Star Entity with Local-First Architecture
 * 
 * CRITICAL FIX: Separate localId (never changes) from serverId (assigned by backend)
 * This prevents broken relationships after sync.
 */
@Entity(tableName = "stars")
data class StarEntity(
    /**
     * LOCAL ID: Generated locally, never changes
     * Used for: local relationships (constellations, orbits), sync queue references
     */
    @PrimaryKey val localId: String,
    
    /**
     * SERVER ID: Assigned by backend after first sync
     * Null until synced, then contains backend UUID
     */
    val serverId: String? = null,
    
    val title: String,
    val urgency: Int,
    val dueDate: Long?,
    val x: Float,
    val y: Float,
    val createdAt: Long,
    val isSubtask: Boolean,
    val isRecurring: Boolean,
    val echoInterval: String?, // Enum as String
    val isCompleted: Boolean,
    val completedAt: Long?,
    val isArchived: Boolean,
    val archivedAt: Long?,
    val contextTag: String? = null,
    
    // Snooze fields (LOCAL-FIRST FIX: Store snooze state locally)
    val isSnoozed: Boolean = false,
    val snoozeUntil: Long? = null,

    // Local-first sync fields
    val syncStatus: String = "pending",  // pending, synced, conflict, error
    val syncVersion: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,  // Soft delete for sync

    // Server timestamp for conflict resolution (CRITICAL FIX: Use server time)
    val serverUpdatedAt: Long? = null
)

/**
 * Constellation Link Entity
 * Uses localId for stable relationships
 */
@Entity(tableName = "constellation_links")
data class ConstellationLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val starA: String,  // localId of star A
    val starB: String,  // localId of star B
    val createdAt: Long,
    val strength: Float,
    val fadeDirection: String
)

/**
 * Orbit Entity
 * Uses localId for stable parent-child relationships
 */
@Entity(tableName = "orbital_relationships")
data class OrbitEntity(
    @PrimaryKey val childId: String,  // localId of child star
    val parentId: String,             // localId of parent star
    val orbitRadius: Float,
    val orbitAngle: Float,
    val angularVelocity: Float,
    val eccentricity: Float,
    val phaseOffset: Float
)

@Entity(tableName = "patinas")
data class PatinaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val opacity: Float,
    val color: Int,
    val vx: Float,
    val vy: Float,
    val fromStar: String  // localId
)

/**
 * Trophy/Achievement Entity
 */
@Entity(tableName = "trophies")
data class TrophyEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val milestone: Int,
    val tier: String,
    val unlockedAt: Long,
    val shareCount: Int = 0
)

/**
 * Achievement Statistics Entity
 */
@Entity(tableName = "achievement_stats")
data class AchievementStatsEntity(
    @PrimaryKey val userId: String,
    val totalCompletions: Int = 0,
    val dailyMaxCompletions: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val fastestCompletion: Long = 0,
    val lastCompletionDate: Long = 0
)

/**
 * Sync Queue Entity
 * Uses localId for task reference (never changes)
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localTaskId: String,  // CRITICAL FIX: Use localId, not serverId
    val operation: String,  // create, update, delete, complete
    val payload: String,    // JSON serialized data
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    // CRITICAL FIX: Track server timestamp for conflict resolution
    val clientTimestamp: Long = System.currentTimeMillis()
)
