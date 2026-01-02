const path = require('path');
// Load .env from parent dir locally, or use Vercel env vars in production
if (process.env.NODE_ENV !== 'production') {
  require('dotenv').config({ path: path.join(__dirname, '../.env') });
}
const express = require('express');
const { Pool, types } = require('pg');
const cors = require('cors');

// CRITICAL: Override PostgreSQL DATE type parser to avoid timezone issues
// By default, pg creates Date objects at midnight in local timezone, causing date shifts
// Type 1082 = DATE, we return it as-is (YYYY-MM-DD string)
types.setTypeParser(1082, (val) => val);  // Return DATE as string, not Date object
const bodyParser = require('body-parser');

const { parseTimeEstimate } = require('./utils/time-parser');
const { prioritizeTasks, daysSince } = require('./utils/priority-scorer');
const { generateWallpaper } = require('./services/wallpaper-generator');
const { generateEnhancedWallpaper, generateFallbackWallpaper } = require('./services/wallpaper-generator-enhanced');
const { MockClient } = require('./utils/mock-client');
const cacheService = require('./services/cache');
const { queryWithRetry } = require('./utils/db-retry');

// Authentication & Rate Limiting
const { verifyToken, optionalAuth, getUserId } = require('./middleware/auth');
const { globalLimiter, authLimiter, wallpaperLimiter, taskCreationLimiter } = require('./middleware/rate-limit');
const { errorHandler, notFoundHandler, asyncHandler, APIError, ErrorCodes } = require('./middleware/error-handler');
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const syncRoutes = require('./routes/sync');

/**
 * Parse date/time value for PostgreSQL.
 * Handles: ISO strings, millisecond timestamps (string or number), YYYY-MM-DD.
 * Returns: { date: 'YYYY-MM-DD', time: 'HH:MM:SS' or null }
 */
function parseDateTimeForDB(value) {
  if (!value || value === '' || value === 'null') return { date: null, time: null };

  let dateObj;

  // Check if it's a millisecond timestamp (all digits)
  if (typeof value === 'number' || (typeof value === 'string' && /^\d{10,13}$/.test(value))) {
    const ms = typeof value === 'number' ? value : parseInt(value, 10);
    dateObj = new Date(ms);
  } else if (typeof value === 'string') {
    dateObj = new Date(value);
  }

  // Validate the date
  if (!dateObj || isNaN(dateObj.getTime())) {
    console.warn(`parseDateTimeForDB: Invalid date value: ${value}`);
    return { date: null, time: null };
  }

  // Extract date part (YYYY-MM-DD) using UTC
  const year = dateObj.getUTCFullYear();
  const month = String(dateObj.getUTCMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getUTCDate()).padStart(2, '0');
  const dateStr = `${year}-${month}-${day}`;

  // Extract time part (HH:MM:SS) - only if not midnight
  const hours = dateObj.getUTCHours();
  const minutes = dateObj.getUTCMinutes();
  const seconds = dateObj.getUTCSeconds();

  let timeStr = null;
  if (hours !== 0 || minutes !== 0 || seconds !== 0) {
    timeStr = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  return { date: dateStr, time: timeStr };
}

// Simple date-only parser for backward compatibility
function parseDateForDB(value) {
  return parseDateTimeForDB(value).date;
}

const app = express();
const port = process.env.PORT || 3000;

// Trust proxy for Vercel/cloud deployments (needed for rate limiting)
app.set('trust proxy', 1);

app.use(cors());
app.use(bodyParser.json());

// Apply global rate limiting (100 req/min)
app.use(globalLimiter);

// --- REQUEST LOGGER ---
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    console.log(`Auth: ${req.headers['authorization'] || 'none'}`);
    next();
});

// Database Connection Pool - Lazy initialization for serverless
let client = null;
let dbInitialized = false;

/**
 * Get database client (lazy initialization for serverless environments)
 * This ensures env vars are available before creating the connection
 */
async function getDbClient() {
  if (dbInitialized) return client;

  const dbUrl = process.env.DATABASE_URL || process.env.POSTGRES_URL;
  console.log('🔧 DB Init: DATABASE_URL set:', !!process.env.DATABASE_URL);
  console.log('🔧 DB Init: DATABASE_URL value:', process.env.DATABASE_URL ? process.env.DATABASE_URL.substring(0, 30) + '...' : 'undefined');
  console.log('🔧 DB Init: NODE_ENV:', process.env.NODE_ENV);
  console.log('🔧 DB Init: All env keys:', Object.keys(process.env).filter(k => k.includes('DATABASE') || k.includes('POSTGRES') || k.includes('DB')));

  if (!dbUrl) {
    console.log('⚠️  No DATABASE_URL, using In-Memory Mock Database');
    client = new MockClient();
    client.connect();
    dbInitialized = true;
    return client;
  }

  try {
    client = new Pool({
      connectionString: dbUrl,
      ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false,
      max: 10, // Reduced for serverless
      idleTimeoutMillis: 10000,
      connectionTimeoutMillis: 5000,
    });

    // Test the connection
    await client.query('SELECT NOW()');
    console.log('✅ PostgreSQL pool connected');

    client.on('error', (err) => {
      console.error('Database pool error:', err);
    });

    dbInitialized = true;
    return client;
  } catch (err) {
    console.error('❌ Failed to connect to PostgreSQL:', err.message);
    console.log('⚠️  Falling back to In-Memory Mock Database');
    client = new MockClient();
    client.connect();
    dbInitialized = true;
    return client;
  }
}

// Middleware to ensure DB is initialized before handling requests
app.use(async (req, res, next) => {
  try {
    req.dbClient = await getDbClient();
    next();
  } catch (err) {
    console.error('DB middleware error:', err);
    next(err);
  }
});

// Initialize cache service
cacheService.connect().catch(err => {
  console.error('Cache service initialization failed:', err);
});

// --- MIDNIGHT RESET FOR DONE_FOR_TODAY ---
// Schedule daily reset of done_for_today flag at midnight
function scheduleMidnightReset() {
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(0, 0, 0, 0);

  const msUntilMidnight = tomorrow.getTime() - now.getTime();

  console.log(`⏰ Scheduled done_for_today reset in ${Math.floor(msUntilMidnight / 1000 / 60)} minutes (at midnight)`);

  setTimeout(async () => {
    try {
      console.log('🔄 Running midnight reset for done_for_today...');
      const db = await getDbClient();
      const result = await db.query(
        'UPDATE users SET done_for_today = FALSE, done_for_today_at = NULL WHERE done_for_today = TRUE'
      );
      console.log(`✅ Reset done_for_today for ${result.rowCount} users`);

      // Invalidate all wallpaper caches (since done_for_today affects wallpaper)
      await cacheService.clearPattern('wallpaper:*');
      console.log('✅ Cleared wallpaper cache');
    } catch (err) {
      console.error('❌ Midnight reset failed:', err);
    }

    // Schedule next midnight reset
    scheduleMidnightReset();
  }, msUntilMidnight);
}

// Start midnight reset scheduler
if (process.env.NODE_ENV !== 'test') {
  scheduleMidnightReset();
}

// --- AUTHENTICATION ROUTES ---
// Apply strict rate limiting to auth endpoints
app.use('/api/auth', authLimiter, authRoutes);

// --- USER ROUTES ---
// User profile, settings, GDPR, and statistics endpoints
app.use('/api/user', userRoutes);

// --- SYNC ROUTES ---
// Offline sync with conflict resolution
app.use('/api/sync', syncRoutes);

// GET /api/wallpaper
// Supports both JWT auth and wallpaper tokens with Redis caching
// Enhanced version with animation support
app.get('/api/wallpaper', wallpaperLimiter, verifyToken, async (req, res) => {
  try {
    const userId = getUserId(req);
    const resolution = req.query.resolution || '1080x1920';
    const theme = req.query.theme || 'cosmic';
    const mode = 'one_thing';
    const useEnhanced = req.query.enhanced !== 'false'; // Use enhanced by default
    const timestamp = req.query.timestamp ? parseInt(req.query.timestamp) : Date.now();

    console.log(`🎨 WALLPAPER REQUEST: userId=${userId}, theme=${theme}, resolution=${resolution}`);

    // Check cache first (only for non-animated wallpapers)
    const cacheKey = cacheService.wallpaperKey(userId, theme, resolution, mode);

    // Skip cache if timestamp is provided (animated wallpaper)
    if (!req.query.timestamp) {
      const cachedWallpaper = await cacheService.getBuffer(cacheKey);

      if (cachedWallpaper) {
        console.log(`✅ Cache HIT: ${cacheKey}`);
        res.set('Content-Type', 'image/png');
        res.set('X-Cache', 'HIT');
        return res.send(cachedWallpaper);
      }
    }

    console.log(`❌ Cache MISS: ${cacheKey}`);
    res.set('X-Cache', 'MISS');

    const userRes = await queryWithRetry(client, "SELECT * FROM users WHERE id = $1", [userId]);
    const userObj = userRes.rows[0];

    // CRITICAL: Use query params directly to match cache key
    // Using database values causes cache inconsistency when settings change
    const user = {
      theme: theme,  // Use query param (matches cache key)
      resolution: resolution,  // Use query param (matches cache key)
      done_for_today: userObj?.done_for_today || false
    };

    console.log(`🖼️  GENERATING: theme=${user.theme}, resolution=${user.resolution}, done=${user.done_for_today}`);

    let imageBuffer;

    if (userObj && userObj.done_for_today) {
      // Done for today - celebration wallpaper
      if (useEnhanced) {
        imageBuffer = await generateEnhancedWallpaper(user, { tasks: [] }, timestamp);
      } else {
        imageBuffer = await generateWallpaper(user, { topTask: null, count: 0 });
      }
    } else {
      // Get tasks
      const result = await queryWithRetry(client, `
        SELECT * FROM tasks
        WHERE user_id = $1
        AND completed = false
        AND (archived = false OR archived IS NULL)
        AND (snoozed_until IS NULL OR snoozed_until < NOW())
      `, [userId]);

      const prioritized = prioritizeTasks(result.rows);
      const topTasks = prioritized.slice(0, 3);
      const count = Math.max(0, prioritized.length - 3);

      if (useEnhanced) {
        imageBuffer = await generateEnhancedWallpaper(user, { tasks: prioritized }, timestamp);
      } else {
        imageBuffer = await generateWallpaper(user, { tasks: topTasks, count });
      }
    }

    // Cache the generated wallpaper (1 hour TTL) - only if not animated
    if (!req.query.timestamp) {
      await cacheService.setBuffer(cacheKey, imageBuffer, 3600);
    }

    res.set('Content-Type', 'image/png');
    res.send(imageBuffer);
  } catch (err) {
    console.error('❌ Wallpaper generation error:', err);

    // Return fallback wallpaper instead of 500 error
    try {
      const [width, height] = (req.query.resolution || '1080x1920').split('x').map(Number);
      const fallbackBuffer = await generateFallbackWallpaper(
        width,
        height,
        'Unable to generate wallpaper. Please try again later.'
      );

      res.set('Content-Type', 'image/png');
      res.set('X-Fallback', 'true');
      res.send(fallbackBuffer);
    } catch (fallbackErr) {
      console.error('❌ Fallback wallpaper also failed:', fallbackErr);
      res.status(500).json({ error: 'Wallpaper generation failed' });
    }
  }
});

// GET /api/tasks
app.get('/api/tasks', optionalAuth, async (req, res) => {
  try {
    const userId = getUserId(req);
    const { location, time } = req.query;
    const result = await req.dbClient.query(`
      SELECT * FROM tasks
      WHERE user_id = $1 AND completed = false
      AND (archived = false OR archived IS NULL)
      AND (snoozed_until IS NULL OR snoozed_until < NOW())
    `, [userId]);
    const prioritized = prioritizeTasks(result.rows, { location, timeOfDay: time });
    res.json(prioritized);
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

// GET /api/tasks/:id
app.get('/api/tasks/:id', optionalAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const userId = getUserId(req);
    const result = await req.dbClient.query(
      'SELECT * FROM tasks WHERE user_id = $1 AND id = $2',
      [userId, id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Task not found' });
    }

    res.json(result.rows[0]);
  } catch (err) {
    console.error('Get task error:', err);
    res.status(500).json({ error: 'Database error' });
  }
});

// POST /api/tasks
app.post('/api/tasks', taskCreationLimiter, verifyToken, async (req, res) => {
  try {
    const userId = getUserId(req);
    const { rawTitle, title: directTitle, priority, context_location, context_time, x, y, is_subtask, is_recurring, echo_interval } = req.body;

    // Parse time estimate with error handling for very long or malformed input
    let title, dueDate, estimateMinutes;
    try {
      const parsed = parseTimeEstimate(rawTitle || directTitle);
      title = parsed.title;
      dueDate = parsed.dueDate;
      estimateMinutes = parsed.estimateMinutes;
    } catch (parseErr) {
      console.warn('Failed to parse time estimate:', parseErr);
      // Fallback to using rawTitle or directTitle as-is
      title = rawTitle || directTitle || 'New Task';
      dueDate = null;
      estimateMinutes = null;
    }

    // CRITICAL: Calculate priority from due_date (PWA-accurate logic)
    // Priority is auto-calculated, NOT sent by client
    let calculatedPriority = 2; // Default: P2 (normal)
    if (dueDate) {
      const now = Date.now();
      const dueInMs = dueDate.getTime() - now;
      const dueInMinutes = Math.round(dueInMs / (1000 * 60));

      if (dueInMinutes < 0) {
        calculatedPriority = 1; // Overdue = P1 (urgent)
      } else if (dueInMinutes < 120) {
        calculatedPriority = 2; // Due within 2h = P2 (normal)
      } else {
        calculatedPriority = 3; // Future = P3 (low priority)
      }
    } else {
      // No due date = future task = P3
      calculatedPriority = 3;
    }

    const query = `
      INSERT INTO tasks (user_id, title, estimate_minutes, priority, due_date, context_location, context_time, x, y, is_subtask, is_recurring, echo_interval)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
      RETURNING *
    `;
    const values = [
      userId, title, estimateMinutes, calculatedPriority, dueDate || null,
      context_location || null, context_time || null,
      x || null, y || null, is_subtask || false, is_recurring || false, echo_interval || null
    ];

    const result = await req.dbClient.query(query, values);
    await req.dbClient.query("UPDATE users SET done_for_today = FALSE WHERE id = $1", [userId]);

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to create task' });
  }
});

// PATCH /api/tasks/:id
app.patch('/api/tasks/:id', optionalAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const userId = getUserId(req);
    const updates = req.body;

    // DEBUG: Log all PATCH requests
    console.log(`[PATCH DEBUG] Task: ${id}`);
    console.log(`[PATCH DEBUG] Body:`, JSON.stringify(updates));

    const fields = [];
    const values = [userId, id];
    let i = 3;
    // Whitelist of valid task fields (prevents SQL injection via column names)
    const validFields = [
      'title', 'priority', 'estimate_minutes', 'due_date', 'due_time',
      'completed', 'completed_at', 'snoozed_until', 'context_location',
      'context_time', 'energy_required', 'x', 'y', 'is_subtask',
      'is_recurring', 'echo_interval', 'archived', 'archived_at'
    ];

    for (const [key, value] of Object.entries(updates)) {
      if (key === 'source') continue;
      // Skip completed_at from body - we'll set it with NOW() if completed=true
      if (key === 'completed_at' && updates.completed === true) continue;
      // Skip archived_at from body - we'll set it with NOW() if archived=true
      if (key === 'archived_at' && updates.archived === true) continue;
      // Only allow whitelisted fields
      if (!validFields.includes(key)) {
        console.warn(`PATCH /api/tasks/:id - ignoring unknown field: ${key}`);
        continue;
      }

      // Parse date fields to avoid timezone issues with DATE columns
      let parsedValue = value;
      if (key === 'due_date') {
        // Parse both date and time from the value
        const { date, time } = parseDateTimeForDB(value);
        parsedValue = date;

        // Also set due_time if time was included and not already being set
        if (time && !updates.due_time) {
          fields.push(`due_time = $${i++}`);
          values.push(time);
        }
      } else if (key === 'original_due_date') {
        parsedValue = parseDateForDB(value);
      }

      fields.push(`${key} = $${i++}`);
      values.push(parsedValue);
    }
    if (updates.completed === true) fields.push(`completed_at = NOW()`);
    if (updates.archived === true) fields.push(`archived_at = NOW()`);
    const query = `UPDATE tasks SET ${fields.join(', ')}, updated_at = NOW() WHERE user_id = $1 AND id = $2 RETURNING *`;
    const result = await req.dbClient.query(query, values);
    
    if (result.rows.length > 0 && updates.completed === true) {
      const viaWidget = updates.source === 'widget' ? 1 : 0;
      const metricQuery = `
        INSERT INTO user_stats (user_id, week_start, tasks_completed, tasks_completed_via_widget)
        VALUES ($1, DATE_TRUNC('week', CURRENT_DATE), 1, $2)
        ON CONFLICT (user_id, week_start)
        DO UPDATE SET tasks_completed = user_stats.tasks_completed + 1, tasks_completed_via_widget = user_stats.tasks_completed_via_widget + $2, updated_at = NOW()
      `;
      await req.dbClient.query(metricQuery, [userId, viaWidget]);
    }

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json(result.rows[0]);
  } catch (err) {
    console.error('PATCH /api/tasks/:id error:', err);
    res.status(500).json({ error: 'Database error', details: err.message });
  }
});

// POST /api/tasks/:id/snooze
app.post('/api/tasks/:id/snooze', optionalAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const userId = getUserId(req);
    const { duration_minutes } = req.body;

    // Use provided duration or default to 1 day (1440 minutes)
    const minutes = duration_minutes || 1440;

    console.log(`[SNOOZE] Task ${id}: duration=${minutes} minutes`);

    const query = `
      UPDATE tasks
      SET original_due_date = COALESCE(original_due_date, due_date),
          due_date = (NOW() + INTERVAL '1 minute' * $3)::date,
          due_time = (NOW() + INTERVAL '1 minute' * $3)::time,
          snoozed_until = NOW() + INTERVAL '1 minute' * $3,
          times_rescheduled = COALESCE(times_rescheduled, 0) + 1,
          updated_at = NOW()
      WHERE user_id = $1 AND id = $2 RETURNING *
    `;
    const result = await req.dbClient.query(query, [userId, id, minutes]);

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: 'Failed to snooze task' });
  }
});

// POST /api/done-for-today
app.post('/api/done-for-today', optionalAuth, async (req, res) => {
  try {
    const userId = getUserId(req);
    const query = `UPDATE users SET done_for_today = TRUE, done_for_today_at = NOW() WHERE id = $1`;
    await req.dbClient.query(query, [userId]);

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed to update status' });
  }
});

// POST /api/metrics/app-open
app.post('/api/metrics/app-open', optionalAuth, async (req, res) => {
  try {
    const userId = getUserId(req);
    const query = `
      INSERT INTO user_stats (user_id, week_start, app_opens)
      VALUES ($1, DATE_TRUNC('week', CURRENT_DATE), 1)
      ON CONFLICT (user_id, week_start)
      DO UPDATE SET app_opens = user_stats.app_opens + 1, updated_at = NOW()
    `;
    await req.dbClient.query(query, [userId]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Failed to track metric' });
  }
});

// DELETE /api/tasks
app.delete('/api/tasks', optionalAuth, async (req, res) => {
  try {
    const userId = getUserId(req);
    await req.dbClient.query('DELETE FROM tasks WHERE user_id = $1', [userId]);

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json({ success: true, message: 'All tasks deleted' });
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

// DELETE /api/tasks/:id
app.delete('/api/tasks/:id', optionalAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const userId = getUserId(req);
    const result = await req.dbClient.query('DELETE FROM tasks WHERE user_id = $1 AND id = $2 RETURNING *', [userId, id]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Task not found' });

    // Invalidate wallpaper cache
    await cacheService.invalidateUserWallpapers(userId);

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

app.get('/api/health', async (req, res) => {
  const dbClient = req.dbClient || await getDbClient();
  res.json({
    status: 'ok',
    mode: dbClient instanceof MockClient ? 'mock' : 'postgres',
    dbInitialized,
    env: {
      hasDbUrl: !!process.env.DATABASE_URL,
      hasPostgresUrl: !!process.env.POSTGRES_URL,
      nodeEnv: process.env.NODE_ENV,
      dbKeys: Object.keys(process.env).filter(k => k.includes('DATABASE') || k.includes('POSTGRES') || k.includes('DB_'))
    }
  });
});

// --- ERROR HANDLERS (Must be last) ---
// 404 handler for undefined routes
app.use(notFoundHandler);

// Global error handler
app.use(errorHandler);

// Only start server if not in test mode
let server;
if (process.env.NODE_ENV !== 'test') {
  server = app.listen(port, () => {
    console.log(`Backend running on http://localhost:${port}`);
  });
}

// Graceful shutdown
process.on('SIGTERM', async () => {
  console.log('SIGTERM signal received: closing HTTP server');
  if (server) {
    server.close(async () => {
      console.log('HTTP server closed');

      // Close database pool
      if (client && !(client instanceof MockClient)) {
        await client.end();
        console.log('Database pool closed');
      }

      // Close Redis connection
      await cacheService.disconnect();
      console.log('Redis connection closed');

      process.exit(0);
    });
  }
});

process.on('SIGINT', async () => {
  console.log('SIGINT signal received: closing HTTP server');
  if (server) {
    server.close(async () => {
      console.log('HTTP server closed');

      // Close database pool
      if (client && !(client instanceof MockClient)) {
        await client.end();
        console.log('Database pool closed');
      }

      // Close Redis connection
      await cacheService.disconnect();
      console.log('Redis connection closed');

      process.exit(0);
    });
  }
});

// Export app for testing
module.exports = app;