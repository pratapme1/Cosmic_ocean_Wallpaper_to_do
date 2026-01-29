const express = require('express');
const { body, validationResult } = require('express-validator');
const { verifyToken } = require('../middleware/auth');
const cacheService = require('../services/cache');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

const router = express.Router();

// Configure Multer for file uploads
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadDir = path.join(__dirname, '../uploads');
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    // Save as: user-{id}-{timestamp}.ext
    const ext = path.extname(file.originalname);
    cb(null, `user-${req.user.userId}-${Date.now()}${ext}`);
  }
});

const upload = multer({
  storage: storage,
  limits: { fileSize: 10 * 1024 * 1024 }, // 10MB limit
  fileFilter: (req, file, cb) => {
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Only image files are allowed!'), false);
    }
  }
});

// All user routes require authentication
router.use(verifyToken);

/**
 * GET /api/user
 * Get current user profile
 */
router.get('/', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    const result = await client.query(
      `SELECT id, email, theme, resolution, display_mode, timezone,
              setup_complete, done_for_today, done_for_today_at,
              wallpaper_token, created_at, updated_at,
              default_privacy_level, auto_hide_work_tasks,
              work_hours_start, work_hours_end,
              biometric_reveal_enabled, hide_all_tasks_mode,
              wallpaper_mode, custom_wallpaper_path
       FROM users WHERE id = $1`,
      [userId]
    );


    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }


    res.json(result.rows[0]);
  } catch (err) {
    console.error('Get user error:', err);
    res.status(500).json({ error: 'Failed to fetch user data' });
  }
});

/**
 * POST /api/user/wallpaper
 * Upload custom wallpaper
 */
router.post('/wallpaper', upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No image file uploaded' });
    }

    const userId = req.user.userId;
    const client = req.app.locals.dbClient;
    const filePath = req.file.path; // Absolute path on server

    // Update user record: set mode to custom and save path
    const query = `
      UPDATE users
      SET wallpaper_mode = 'custom',
          custom_wallpaper_path = $1,
          updated_at = NOW()
      WHERE id = $2
      RETURNING wallpaper_mode, custom_wallpaper_path
    `;

    const result = await client.query(query, [filePath, userId]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Invalidate cache so next request generates using new custom image
    await cacheService.invalidateUserWallpapers(userId);

    res.json({
      success: true,
      message: 'Wallpaper uploaded successfully',
      data: result.rows[0]
    });
  } catch (err) {
    console.error('Upload wallpaper error:', err);
    res.status(500).json({ error: 'Failed to upload wallpaper' });
  }
});

/**
 * PATCH /api/user
 * Update user settings
 */
router.patch(
  '/',
  [
    body('theme').optional().isIn(['cosmic', 'ocean', 'fantasy']),
    body('resolution').optional().matches(/^\d+x\d+$/),
    body('display_mode').optional().isIn(['one_thing', 'all_tasks']),
    body('timezone').optional().isString(),
    body('setup_complete').optional().isBoolean(),
    body('done_for_today').optional().isBoolean(),
    // Privacy settings (Epic 10)
    body('default_privacy_level').optional().isIn(['public', 'category', 'initials', 'hidden', 'custom']),
    body('auto_hide_work_tasks').optional().isBoolean(),
    body('work_hours_start').optional().matches(/^\d{2}:\d{2}(:\d{2})?$/),
    body('work_hours_end').optional().matches(/^\d{2}:\d{2}(:\d{2})?$/),
    body('biometric_reveal_enabled').optional().isBoolean(),
    body('hide_all_tasks_mode').optional().isBoolean(),
    body('wallpaper_mode').optional().isIn(['generated', 'custom'])
  ],
  async (req, res) => {
    try {
      // Validate input
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
      }

      const userId = req.user.userId;
      const client = req.app.locals.dbClient;

      // Build dynamic update query
      const updates = req.body;
      const fields = [];
      const values = [userId];
      let i = 2;

      // Allowed fields for update
      const allowedFields = [
        'theme',
        'resolution',
        'display_mode',
        'timezone',
        'setup_complete',
        'done_for_today',
        // Privacy settings (Epic 10)
        'default_privacy_level',
        'auto_hide_work_tasks',
        'work_hours_start',
        'work_hours_end',
        'biometric_reveal_enabled',
        'hide_all_tasks_mode',
        'wallpaper_mode'
      ];

      for (const [key, value] of Object.entries(updates)) {
        if (allowedFields.includes(key)) {
          fields.push(`${key} = $${i++}`);
          values.push(value);
        }
      }

      if (fields.length === 0) {
        return res.status(400).json({ error: 'No valid fields to update' });
      }

      // Handle done_for_today special case
      if (updates.done_for_today === true) {
        fields.push(`done_for_today_at = NOW()`);
      }

      fields.push('updated_at = NOW()');

      const query = `
        UPDATE users
        SET ${fields.join(', ')}
        WHERE id = $1
        RETURNING id, email, theme, resolution, display_mode, timezone,
                  setup_complete, done_for_today, done_for_today_at,
                  wallpaper_token, created_at, updated_at,
                  default_privacy_level, auto_hide_work_tasks,
                  work_hours_start, work_hours_end,
                  biometric_reveal_enabled, hide_all_tasks_mode,
                  wallpaper_mode, custom_wallpaper_path
      `;

      const result = await client.query(query, values);

      if (result.rows.length === 0) {
        return res.status(404).json({ error: 'User not found' });
      }

      // Invalidate wallpaper cache when user settings change
      // (especially theme, resolution, or done_for_today)
      await cacheService.invalidateUserWallpapers(userId);

      res.json(result.rows[0]);
    } catch (err) {
      console.error('Update user error:', err);
      res.status(500).json({ error: 'Failed to update user settings' });
    }
  }
);

/**
 * GET /api/user/preferences
 * Get user privacy and display preferences (Epic 10)
 */
router.get('/preferences', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Use COALESCE to provide defaults for environment columns that may not exist yet
    const result = await client.query(
      `SELECT
         theme, resolution, display_mode, timezone,
         default_privacy_level, auto_hide_work_tasks,
         work_hours_start, work_hours_end,
         biometric_reveal_enabled, hide_all_tasks_mode,
         COALESCE(time_of_day_mode, 'auto') as time_of_day_mode,
         COALESCE(manual_time_period, 'morning') as manual_time_period,
         COALESCE(weather_overlay_enabled, true) as weather_overlay_enabled,
         COALESCE(particle_intensity, 'medium') as particle_intensity
       FROM users WHERE id = $1`,
      [userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    res.json(result.rows[0]);
  } catch (err) {
    // If columns don't exist yet, return defaults for environment settings
    if (err.message && err.message.includes('column') && err.message.includes('does not exist')) {
      console.log('[Preferences] Environment columns not yet migrated, returning defaults');
      try {
        const fallbackResult = await req.app.locals.dbClient.query(
          `SELECT
             theme, resolution, display_mode, timezone,
             default_privacy_level, auto_hide_work_tasks,
             work_hours_start, work_hours_end,
             biometric_reveal_enabled, hide_all_tasks_mode
           FROM users WHERE id = $1`,
          [req.user.userId]
        );

        if (fallbackResult.rows.length === 0) {
          return res.status(404).json({ error: 'User not found' });
        }

        // Add default environment settings
        const prefs = fallbackResult.rows[0];
        prefs.time_of_day_mode = 'auto';
        prefs.manual_time_period = 'morning';
        prefs.weather_overlay_enabled = true;
        prefs.particle_intensity = 'medium';
        return res.json(prefs);
      } catch (fallbackErr) {
        console.error('Fallback preferences error:', fallbackErr);
        return res.status(500).json({ error: 'Failed to fetch preferences' });
      }
    }

    console.error('Get preferences error:', err);
    res.status(500).json({ error: 'Failed to fetch preferences' });
  }
});

/**
 * PATCH /api/user/preferences
 * Update user privacy and display preferences (Epic 10)
 */
router.patch(
  '/preferences',
  [
    body('theme').optional().isIn(['cosmic', 'ocean', 'fantasy']),
    body('resolution').optional().matches(/^\d+x\d+$/),
    body('display_mode').optional().isIn(['one_thing', 'all_tasks']),
    body('timezone').optional().isString(),
    body('default_privacy_level').optional().isIn(['public', 'category', 'initials', 'hidden', 'custom']),
    body('auto_hide_work_tasks').optional().isBoolean(),
    body('work_hours_start').optional().matches(/^\d{2}:\d{2}(:\d{2})?$/),
    body('work_hours_end').optional().matches(/^\d{2}:\d{2}(:\d{2})?$/),
    body('biometric_reveal_enabled').optional().isBoolean(),
    body('hide_all_tasks_mode').optional().isBoolean(),
    // Epic 10 Phase 3: Environment settings
    body('time_of_day_mode').optional().isIn(['auto', 'manual']),
    body('manual_time_period').optional().isIn(['dawn', 'morning', 'afternoon', 'evening', 'night']),
    body('weather_overlay_enabled').optional().isBoolean(),
    body('particle_intensity').optional().isIn(['low', 'medium', 'high'])
  ],
  async (req, res) => {
    try {
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
      }

      const userId = req.user.userId;
      const client = req.app.locals.dbClient;

      const updates = req.body;
      const fields = [];
      const values = [userId];
      let i = 2;

      // Core fields that exist in all deployments
      const coreFields = [
        'theme', 'resolution', 'display_mode', 'timezone',
        'default_privacy_level', 'auto_hide_work_tasks',
        'work_hours_start', 'work_hours_end',
        'biometric_reveal_enabled', 'hide_all_tasks_mode'
      ];

      // Environment fields (Epic 10 Phase 3) - may not exist yet
      const environmentFields = [
        'time_of_day_mode', 'manual_time_period',
        'weather_overlay_enabled', 'particle_intensity'
      ];

      const allowedFields = [...coreFields, ...environmentFields];

      // Separate updates into core and environment
      const coreUpdates = {};
      const envUpdates = {};

      for (const [key, value] of Object.entries(updates)) {
        if (coreFields.includes(key)) {
          coreUpdates[key] = value;
        } else if (environmentFields.includes(key)) {
          envUpdates[key] = value;
        }
      }

      // Build query for core fields first
      for (const [key, value] of Object.entries(coreUpdates)) {
        fields.push(`${key} = $${i++}`);
        values.push(value);
      }

      // Try to add environment fields (may fail if columns don't exist)
      for (const [key, value] of Object.entries(envUpdates)) {
        fields.push(`${key} = $${i++}`);
        values.push(value);
      }

      if (fields.length === 0) {
        return res.status(400).json({ error: 'No valid fields to update' });
      }

      fields.push('updated_at = NOW()');

      // Try full query first with all fields
      let query = `
        UPDATE users
        SET ${fields.join(', ')}
        WHERE id = $1
        RETURNING theme, resolution, display_mode, timezone,
                  default_privacy_level, auto_hide_work_tasks,
                  work_hours_start, work_hours_end,
                  biometric_reveal_enabled, hide_all_tasks_mode,
                  COALESCE(time_of_day_mode, 'auto') as time_of_day_mode,
                  COALESCE(manual_time_period, 'morning') as manual_time_period,
                  COALESCE(weather_overlay_enabled, true) as weather_overlay_enabled,
                  COALESCE(particle_intensity, 'medium') as particle_intensity
      `;

      let result;
      try {
        result = await client.query(query, values);
      } catch (err) {
        // If environment columns don't exist, retry with core fields only
        if (err.message && err.message.includes('column') && err.message.includes('does not exist')) {
          console.log('[Preferences] Environment columns not migrated, updating core fields only');

          if (Object.keys(coreUpdates).length === 0) {
            return res.status(400).json({
              error: 'Environment settings not available yet. Please run database migration.',
              pending_migration: true
            });
          }

          // Rebuild query with only core fields
          const coreOnlyFields = [];
          const coreOnlyValues = [userId];
          let j = 2;

          for (const [key, value] of Object.entries(coreUpdates)) {
            coreOnlyFields.push(`${key} = $${j++}`);
            coreOnlyValues.push(value);
          }

          coreOnlyFields.push('updated_at = NOW()');

          query = `
            UPDATE users
            SET ${coreOnlyFields.join(', ')}
            WHERE id = $1
            RETURNING theme, resolution, display_mode, timezone,
                      default_privacy_level, auto_hide_work_tasks,
                      work_hours_start, work_hours_end,
                      biometric_reveal_enabled, hide_all_tasks_mode
          `;

          result = await client.query(query, coreOnlyValues);

          // Add default environment settings to response
          if (result.rows.length > 0) {
            result.rows[0].time_of_day_mode = 'auto';
            result.rows[0].manual_time_period = 'morning';
            result.rows[0].weather_overlay_enabled = true;
            result.rows[0].particle_intensity = 'medium';
          }
        } else {
          throw err;
        }
      }

      if (result.rows.length === 0) {
        return res.status(404).json({ error: 'User not found' });
      }

      // Invalidate wallpaper cache when preferences change
      await cacheService.invalidateUserWallpapers(userId);

      res.json(result.rows[0]);
    } catch (err) {
      console.error('Update preferences error:', err);
      res.status(500).json({ error: 'Failed to update preferences' });
    }
  }
);

/**
 * GET /api/user/export
 * Export all user data (GDPR compliance)
 */
router.get('/export', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Get user data
    const userResult = await client.query('SELECT * FROM users WHERE id = $1', [userId]);
    const user = userResult.rows[0];

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Get all tasks
    const tasksResult = await client.query('SELECT * FROM tasks WHERE user_id = $1', [userId]);

    // Get all stats
    const statsResult = await client.query('SELECT * FROM user_stats WHERE user_id = $1', [userId]);

    // Remove password hash from export
    delete user.password_hash;

    // Build export data
    const exportData = {
      exportedAt: new Date().toISOString(),
      version: '1.0',
      user,
      tasks: tasksResult.rows,
      stats: statsResult.rows
    };

    res.json(exportData);
  } catch (err) {
    console.error('Export user data error:', err);
    res.status(500).json({ error: 'Failed to export user data' });
  }
});

/**
 * DELETE /api/user
 * Delete user account and all associated data (GDPR compliance)
 */
router.delete('/', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Verify user exists
    const userResult = await client.query('SELECT id FROM users WHERE id = $1', [userId]);

    if (userResult.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Delete user (cascades to tasks and stats via foreign key constraints)
    await client.query('DELETE FROM users WHERE id = $1', [userId]);

    res.json({
      success: true,
      message: 'Account deleted successfully. All associated data has been removed.'
    });
  } catch (err) {
    console.error('Delete user error:', err);
    res.status(500).json({ error: 'Failed to delete account' });
  }
});

/**
 * GET /api/user/stats/weekly
 * Get weekly statistics for current user
 */
router.get('/stats/weekly', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Get stats for last 8 weeks
    const query = `
      SELECT * FROM user_stats
      WHERE user_id = $1
      ORDER BY week_start DESC
      LIMIT 8
    `;

    const result = await client.query(query, [userId]);

    // Calculate trends
    const stats = result.rows;
    let appOpenTrend = null;
    let widgetCompletionRate = null;

    if (stats.length >= 2) {
      const thisWeek = stats[0];
      const lastWeek = stats[1];

      // Calculate app open trend (percentage change)
      if (lastWeek.app_opens > 0) {
        appOpenTrend = ((thisWeek.app_opens - lastWeek.app_opens) / lastWeek.app_opens) * 100;
      }

      // Calculate widget completion rate
      if (thisWeek.tasks_completed > 0) {
        widgetCompletionRate = (thisWeek.tasks_completed_via_widget / thisWeek.tasks_completed) * 100;
      }
    }

    res.json({
      weeks: stats,
      trends: {
        appOpenTrend: appOpenTrend ? Math.round(appOpenTrend) : null,
        widgetCompletionRate: widgetCompletionRate ? Math.round(widgetCompletionRate) : null
      }
    });
  } catch (err) {
    console.error('Get weekly stats error:', err);
    res.status(500).json({ error: 'Failed to fetch weekly statistics' });
  }
});

/**
 * POST /api/user/done-for-today
 * Mark user as done for the day (shows celebration wallpaper)
 */
router.post('/done-for-today', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Mark done_for_today
    const query = `
      UPDATE users
      SET done_for_today = TRUE, done_for_today_at = NOW(), updated_at = NOW()
      WHERE id = $1
      RETURNING done_for_today, done_for_today_at
    `;

    const result = await client.query(query, [userId]);

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    res.json({
      success: true,
      doneForToday: true,
      doneAt: result.rows[0].done_for_today_at
    });
  } catch (err) {
    console.error('Done for today error:', err);
    res.status(500).json({ error: 'Failed to update status' });
  }
});

/**
 * GET /api/user/stats/graduation
 * Calculate "graduation" metrics (app usage reduction over time)
 */
router.get('/stats/graduation', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Get monthly aggregated stats
    const query = `
      SELECT
        DATE_TRUNC('month', week_start) as month,
        SUM(app_opens) as total_app_opens,
        SUM(widget_interactions) as total_widget_interactions,
        SUM(tasks_completed) as total_tasks_completed,
        SUM(tasks_completed_via_widget) as total_tasks_completed_via_widget
      FROM user_stats
      WHERE user_id = $1
      GROUP BY DATE_TRUNC('month', week_start)
      ORDER BY month DESC
      LIMIT 6
    `;

    const result = await client.query(query, [userId]);

    // Calculate graduation progress
    let graduationScore = null;
    let message = null;

    if (result.rows.length >= 2) {
      const thisMonth = result.rows[0];
      const firstMonth = result.rows[result.rows.length - 1];

      // Calculate reduction in app opens
      if (firstMonth.total_app_opens > 0) {
        const reduction = ((firstMonth.total_app_opens - thisMonth.total_app_opens) / firstMonth.total_app_opens) * 100;
        graduationScore = Math.max(0, Math.min(100, reduction));

        if (graduationScore > 50) {
          message = "Amazing! You're checking the app less and less. You're graduating!";
        } else if (graduationScore > 25) {
          message = "You're making progress. The app is becoming invisible infrastructure.";
        } else if (graduationScore > 0) {
          message = "Good start! Keep using the widget to reduce app dependency.";
        } else {
          message = "Use the widget more to complete tasks without opening the app.";
        }
      }

      // Calculate widget preference
      const widgetCompletionRate = thisMonth.total_tasks_completed > 0
        ? (thisMonth.total_tasks_completed_via_widget / thisMonth.total_tasks_completed) * 100
        : 0;

      res.json({
        months: result.rows,
        graduation: {
          score: graduationScore ? Math.round(graduationScore) : 0,
          message,
          widgetCompletionRate: Math.round(widgetCompletionRate)
        }
      });
    } else {
      res.json({
        months: result.rows,
        graduation: {
          score: 0,
          message: 'Keep using Cosmic Ocean to track your progress!',
          widgetCompletionRate: 0
        }
      });
    }
  } catch (err) {
    console.error('Get graduation stats error:', err);
    res.status(500).json({ error: 'Failed to fetch graduation statistics' });
  }
});

module.exports = router;
