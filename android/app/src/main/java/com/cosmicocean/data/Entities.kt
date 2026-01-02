package com.cosmicocean.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stars")
data class StarEntity(
    @PrimaryKey val id: String,
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
    val archivedAt: Long?
)

@Entity(tableName = "constellation_links")
data class ConstellationLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val starA: String,
    val starB: String,
    val createdAt: Long,
    val strength: Float,
    val fadeDirection: String
)

@Entity(tableName = "orbital_relationships")
data class OrbitEntity(
    @PrimaryKey val childId: String,
    val parentId: String,
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
    val fromStar: String
)

/**
 * Trophy/Achievement Entity
 * Tracks milestone unlocks (1, 10, 25, 50, 100, 250, 500, 1000 completions)
 */
@Entity(tableName = "trophies")
data class TrophyEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val milestone: Int,              // 1, 10, 25, 50, 100, 250, 500, 1000
    val tier: String,                // bronze, silver, gold, platinum, cosmic
    val unlockedAt: Long,
    val shareCount: Int = 0
)

/**
 * Achievement Statistics Entity
 * Tracks user's achievement progress and stats
 */
@Entity(tableName = "achievement_stats")
data class AchievementStatsEntity(
    @PrimaryKey val userId: String,
    val totalCompletions: Int = 0,
    val dailyMaxCompletions: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val fastestCompletion: Long = 0,    // milliseconds
    val lastCompletionDate: Long = 0
)
