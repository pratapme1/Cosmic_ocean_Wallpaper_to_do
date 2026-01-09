/**
 * Achievement Routes
 *
 * Endpoints for the achievement/gamification system.
 *
 * Endpoints:
 * - GET /api/achievements - Get user's achievements and progress
 * - GET /api/achievements/definitions - Get all achievement definitions
 * - POST /api/achievements/:id/dismiss - Dismiss an achievement from display
 * - POST /api/achievements/check - Force check for new achievements
 *
 * Epic: 10 Phase 2 - Achievement System
 * Created: 2026-01-09
 */

const express = require('express');
const { verifyToken } = require('../middleware/auth');
const { AchievementService, ACHIEVEMENT_DEFINITIONS } = require('../services/achievement-service');
const cacheService = require('../services/cache');

const router = express.Router();
const achievementService = new AchievementService();

// All achievement routes require authentication
router.use(verifyToken);

/**
 * GET /api/achievements
 * Get user's earned achievements and progress
 */
router.get('/', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Get all user tasks
    const tasksResult = await client.query(
      `SELECT id, title, category, completed, created_at, updated_at
       FROM tasks
       WHERE user_id = $1
       ORDER BY created_at DESC`,
      [userId]
    );
    const tasks = tasksResult.rows;

    // Get existing achievements from database
    const achievementsResult = await client.query(
      `SELECT achievement_id, earned_at, dismissed, metadata
       FROM user_achievements
       WHERE user_id = $1
       ORDER BY earned_at DESC`,
      [userId]
    );
    const existingAchievements = achievementsResult.rows;

    // Detect achievements
    const detectionResult = await achievementService.detectAchievements(
      userId,
      tasks,
      existingAchievements
    );

    // Save any new achievements to database
    for (const newAchievement of detectionResult.new) {
      try {
        await client.query(
          `INSERT INTO user_achievements (user_id, achievement_id, earned_at, metadata)
           VALUES ($1, $2, $3, $4)
           ON CONFLICT (user_id, achievement_id) DO NOTHING`,
          [
            userId,
            newAchievement.id,
            newAchievement.earnedAt,
            JSON.stringify(newAchievement.metadata || {})
          ]
        );
      } catch (err) {
        console.error(`Failed to save achievement ${newAchievement.id}:`, err);
      }
    }

    // Get user preferences for achievement display
    const userResult = await client.query(
      `SELECT show_achievements_on_wallpaper, achievement_notification_enabled
       FROM users WHERE id = $1`,
      [userId]
    );
    const userPrefs = userResult.rows[0] || {
      show_achievements_on_wallpaper: true,
      achievement_notification_enabled: true
    };

    // Get non-dismissed achievements for wallpaper
    const wallpaperAchievements = detectionResult.earned.filter(a => {
      const existing = existingAchievements.find(e => e.achievement_id === a.id);
      return !existing?.dismissed;
    });

    res.json({
      earned: detectionResult.earned,
      new: detectionResult.new,
      inProgress: detectionResult.inProgress,
      totalPoints: detectionResult.totalPoints,
      stats: detectionResult.stats,
      wallpaperAchievements: achievementService.getWallpaperAchievements(wallpaperAchievements, 3),
      preferences: userPrefs
    });
  } catch (err) {
    console.error('Get achievements error:', err);
    res.status(500).json({ error: 'Failed to fetch achievements' });
  }
});

/**
 * GET /api/achievements/definitions
 * Get all achievement definitions (for UI display)
 */
router.get('/definitions', async (req, res) => {
  try {
    const definitions = achievementService.getDefinitions();

    // Group by type for easier UI rendering
    const grouped = {
      streak: [],
      milestone: [],
      speed: [],
      time_preference: [],
      zero_inbox: [],
      category: []
    };

    for (const [key, def] of Object.entries(definitions)) {
      if (grouped[def.type]) {
        grouped[def.type].push(def);
      }
    }

    res.json({
      all: definitions,
      grouped
    });
  } catch (err) {
    console.error('Get achievement definitions error:', err);
    res.status(500).json({ error: 'Failed to fetch achievement definitions' });
  }
});

/**
 * POST /api/achievements/:id/dismiss
 * Dismiss an achievement from wallpaper display
 */
router.post('/:id/dismiss', async (req, res) => {
  try {
    const userId = req.user.userId;
    const achievementId = req.params.id;
    const client = req.app.locals.dbClient;

    const result = await client.query(
      `UPDATE user_achievements
       SET dismissed = TRUE, dismissed_at = NOW()
       WHERE user_id = $1 AND achievement_id = $2
       RETURNING *`,
      [userId, achievementId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Achievement not found' });
    }

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json({
      success: true,
      achievement: result.rows[0]
    });
  } catch (err) {
    console.error('Dismiss achievement error:', err);
    res.status(500).json({ error: 'Failed to dismiss achievement' });
  }
});

/**
 * POST /api/achievements/:id/undismiss
 * Re-show a dismissed achievement on wallpaper
 */
router.post('/:id/undismiss', async (req, res) => {
  try {
    const userId = req.user.userId;
    const achievementId = req.params.id;
    const client = req.app.locals.dbClient;

    const result = await client.query(
      `UPDATE user_achievements
       SET dismissed = FALSE, dismissed_at = NULL
       WHERE user_id = $1 AND achievement_id = $2
       RETURNING *`,
      [userId, achievementId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Achievement not found' });
    }

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json({
      success: true,
      achievement: result.rows[0]
    });
  } catch (err) {
    console.error('Undismiss achievement error:', err);
    res.status(500).json({ error: 'Failed to undismiss achievement' });
  }
});

/**
 * POST /api/achievements/check
 * Force check for new achievements (useful after bulk operations)
 */
router.post('/check', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Clear cache to force re-detection
    achievementService.clearCache(userId);

    // Get all user tasks
    const tasksResult = await client.query(
      `SELECT id, title, category, completed, created_at, updated_at
       FROM tasks
       WHERE user_id = $1
       ORDER BY created_at DESC`,
      [userId]
    );
    const tasks = tasksResult.rows;

    // Get existing achievements
    const achievementsResult = await client.query(
      `SELECT achievement_id, earned_at, dismissed, metadata
       FROM user_achievements
       WHERE user_id = $1`,
      [userId]
    );
    const existingAchievements = achievementsResult.rows;

    // Detect achievements
    const detectionResult = await achievementService.detectAchievements(
      userId,
      tasks,
      existingAchievements
    );

    // Save any new achievements
    const savedNew = [];
    for (const newAchievement of detectionResult.new) {
      try {
        const insertResult = await client.query(
          `INSERT INTO user_achievements (user_id, achievement_id, earned_at, metadata)
           VALUES ($1, $2, $3, $4)
           ON CONFLICT (user_id, achievement_id) DO NOTHING
           RETURNING *`,
          [
            userId,
            newAchievement.id,
            newAchievement.earnedAt,
            JSON.stringify(newAchievement.metadata || {})
          ]
        );
        if (insertResult.rows.length > 0) {
          savedNew.push(newAchievement);
        }
      } catch (err) {
        console.error(`Failed to save achievement ${newAchievement.id}:`, err);
      }
    }

    // Invalidate wallpaper cache if new achievements were earned
    if (savedNew.length > 0) {
      await cacheService.invalidateUserWallpapers(userId);
    }

    res.json({
      checked: true,
      newAchievements: savedNew,
      totalEarned: detectionResult.earned.length,
      totalPoints: detectionResult.totalPoints
    });
  } catch (err) {
    console.error('Check achievements error:', err);
    res.status(500).json({ error: 'Failed to check achievements' });
  }
});

/**
 * PATCH /api/achievements/preferences
 * Update achievement display preferences
 */
router.patch('/preferences', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;
    const { show_achievements_on_wallpaper, achievement_notification_enabled } = req.body;

    const updates = [];
    const values = [userId];
    let paramIndex = 2;

    if (typeof show_achievements_on_wallpaper === 'boolean') {
      updates.push(`show_achievements_on_wallpaper = $${paramIndex++}`);
      values.push(show_achievements_on_wallpaper);
    }

    if (typeof achievement_notification_enabled === 'boolean') {
      updates.push(`achievement_notification_enabled = $${paramIndex++}`);
      values.push(achievement_notification_enabled);
    }

    if (updates.length === 0) {
      return res.status(400).json({ error: 'No valid fields to update' });
    }

    updates.push('updated_at = NOW()');

    const result = await client.query(
      `UPDATE users
       SET ${updates.join(', ')}
       WHERE id = $1
       RETURNING show_achievements_on_wallpaper, achievement_notification_enabled`,
      values
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json(result.rows[0]);
  } catch (err) {
    console.error('Update achievement preferences error:', err);
    res.status(500).json({ error: 'Failed to update preferences' });
  }
});

/**
 * GET /api/achievements/wallpaper
 * Get achievements formatted for wallpaper rendering
 * (Used internally by wallpaper generator)
 */
router.get('/wallpaper', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Check if user wants achievements on wallpaper
    const userResult = await client.query(
      `SELECT show_achievements_on_wallpaper FROM users WHERE id = $1`,
      [userId]
    );

    if (!userResult.rows[0]?.show_achievements_on_wallpaper) {
      return res.json({ achievements: [], enabled: false });
    }

    // Get non-dismissed achievements
    const achievementsResult = await client.query(
      `SELECT achievement_id, earned_at, metadata
       FROM user_achievements
       WHERE user_id = $1 AND NOT dismissed
       ORDER BY earned_at DESC`,
      [userId]
    );

    // Map to full achievement definitions
    const achievements = achievementsResult.rows.map(row => {
      const key = row.achievement_id.toUpperCase();
      const def = ACHIEVEMENT_DEFINITIONS[key] || {
        id: row.achievement_id,
        name: row.achievement_id,
        icon: 'star',
        color: '#FFD700'
      };
      return {
        ...def,
        earnedAt: row.earned_at,
        metadata: row.metadata
      };
    });

    // Return top 3 achievements for wallpaper
    const wallpaperAchievements = achievementService.getWallpaperAchievements(achievements, 3);

    res.json({
      achievements: wallpaperAchievements,
      enabled: true,
      total: achievements.length
    });
  } catch (err) {
    console.error('Get wallpaper achievements error:', err);
    res.status(500).json({ error: 'Failed to fetch wallpaper achievements' });
  }
});

module.exports = router;
