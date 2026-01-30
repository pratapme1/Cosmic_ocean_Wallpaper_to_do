const path = require('path');
// Load .env from parent dir locally, or use Vercel env vars in production
if (process.env.NODE_ENV !== 'production') {
  require('dotenv').config({ path: path.join(__dirname, '../.env') });
}

// Configure fontconfig for serverless environments (Vercel has no system fonts)
const fontsDir = path.join(__dirname, 'fonts');
process.env.FONTCONFIG_PATH = fontsDir;
process.env.FONTCONFIG_FILE = path.join(fontsDir, 'fonts.conf');

const express = require('express');
// Remove local Pool import, we use db/pool.js
const { types } = require('pg');
const cors = require('cors');
const dbPool = require('./db/pool');

// CRITICAL: Override PostgreSQL DATE type parser to avoid timezone issues
// By default, pg creates Date objects at midnight in local timezone, causing date shifts
// Type 1082 = DATE, we return it as-is (YYYY-MM-DD string)
types.setTypeParser(1082, (val) => val);  // Return DATE as string, not Date object
const bodyParser = require('body-parser');

const { parseTask } = require('./utils/task-parser');
const { prioritizeTasks, daysSince } = require('./utils/priority-scorer');
const { generateWallpaper } = require('./services/wallpaper-generator');
const { generateEnhancedWallpaper, generateFallbackWallpaper } = require('./services/wallpaper-generator-enhanced');
const { MockClient } = require('./utils/mock-client');
const cacheService = require('./services/cache');
const { queryWithRetry } = require('./utils/db-retry');

// LLM Intelligence (Epic 8)
const { parseLLM } = require('./utils/llm-task-parser');
const { llmRateLimiter } = require('./middleware/rate-limiter');
const { startWorker, stopWorker } = require('./services/message-worker');

// Authentication & Rate Limiting
const { verifyToken, optionalAuth, getUserId } = require('./middleware/auth');
const { globalLimiter, authLimiter, wallpaperLimiter, taskCreationLimiter } = require('./middleware/rate-limit');
const { errorHandler, notFoundHandler, asyncHandler, APIError, ErrorCodes } = require('./middleware/error-handler');
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const syncRoutes = require('./routes/sync');
const achievementRoutes = require('./routes/achievements');

/**
 * Parse date/time value for PostgreSQL.
 * Handles: ISO strings, millisecond timestamps (string or number), YYYY-MM-DD.
 * Returns: { date: 'YYYY-MM-DD', time: 'HH:MM:SS' or null }
 */
function parseDateTimeForDB(value) {
  if (!value || value === '' || value === 'null') return { date: null, time: null };

  let dateObj;

  // FIX: Handle Date objects directly (from chrono-node NLP parser)
  if (value instanceof Date) {
    dateObj = value;
  }
  // Check if it's a millisecond timestamp (all digits)
  else if (typeof value === 'number' || (typeof value === 'string' && /^\d{10,13}$/.test(value))) {
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

  // FIX 2026-01-06: Extract date part using LOCAL time, not UTC
  // Bug: "tomorrow" in IST (2026-01-07 00:00 IST) was stored as 2026-01-06 (UTC date)
  // Cause: getUTCDate() returns 6, but getDate() returns 7 (correct local date)
  const year = dateObj.getFullYear();
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getDate()).padStart(2, '0');
  const dateStr = `${year}-${month}-${day}`;

  // Extract time part (HH:MM:SS) using LOCAL time - only if not midnight
  const hours = dateObj.getHours();
  const minutes = dateObj.getMinutes();
  const seconds = dateObj.getSeconds();

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

/**
 * Get database client (lazy initialization for serverless environments)
 * This ensures env vars are available before creating the connection
 */
async function getDbClient() {
  // Use the robust singleton pool
  // In server.js, we return the POOL itself as the client, 
  // because pg.Pool supports .query() just like a Client.
  return dbPool.getDbPool();
}

// Middleware to ensure DB is initialized before handling requests
app.use(async (req, res, next) => {
  try {
    const dbClient = await getDbClient();
    req.dbClient = dbClient;
    req.app.locals.dbClient = dbClient; // For routes that use app.locals
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

// --- ACHIEVEMENT ROUTES ---
// Epic 10 Phase 2: Achievement/gamification system
app.use('/api/achievements', achievementRoutes);

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
    const timezone = req.query.timezone || 'UTC'; // User's timezone (e.g., 'Asia/Kolkata')

    console.log(`🎨 WALLPAPER REQUEST: userId=${userId}, theme=${theme}, resolution=${resolution}, timezone=${timezone}`);

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

    const userRes = await queryWithRetry(req.dbClient, "SELECT * FROM users WHERE id = $1", [userId]);
    const userObj = userRes.rows[0];

    // CRITICAL: Use query params directly to match cache key
    // Using database values causes cache inconsistency when settings change
    const user = {
      id: userId,  // CRITICAL: Required for LLM message generation
      theme: theme,  // Use query param (matches cache key)
      resolution: resolution,  // Use query param (matches cache key)
      done_for_today: userObj?.done_for_today || false,
      // Epic 10: Privacy preferences for wallpaper filtering
      hide_all_tasks_mode: userObj?.hide_all_tasks_mode || false,
      auto_hide_work_tasks: userObj?.auto_hide_work_tasks || false,
      work_hours_start: userObj?.work_hours_start || '09:00',
      work_hours_end: userObj?.work_hours_end || '17:00',
      default_privacy_level: userObj?.default_privacy_level || 'public',
      // Epic 10 Phase 2: Achievement preferences
      show_achievements_on_wallpaper: userObj?.show_achievements_on_wallpaper !== false, // Default to true
      // Epic 11: Custom wallpaper support
      wallpaper_mode: userObj?.wallpaper_mode || 'generated',
      custom_wallpaper_path: userObj?.custom_wallpaper_path || null
    };

    console.log(`🖼️  GENERATING: theme=${user.theme}, resolution=${user.resolution}, done=${user.done_for_today}, wallpaper_mode=${user.wallpaper_mode}`);
    console.log(`🔒 PRIVACY: hide_all=${user.hide_all_tasks_mode}, auto_hide_work=${user.auto_hide_work_tasks}, default_level=${user.default_privacy_level}`);

    let imageBuffer;

    // FIX: Always fetch allTasks for achievement calculation (even when done_for_today)
    // This ensures achievements are shown correctly after task completion
    let allTasksForAchievements = [];
    if (useEnhanced) {
      const allTasksResult = await queryWithRetry(req.dbClient, `
        SELECT * FROM tasks
        WHERE user_id = $1
        AND (archived = false OR archived IS NULL)
        AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
      `, [userId]);
      allTasksForAchievements = allTasksResult.rows;
      console.log(`[Wallpaper] Fetched ${allTasksForAchievements.length} tasks for achievement calculation`);
    }

    if (userObj && userObj.done_for_today) {
      // Done for today - celebration wallpaper (but still show achievements!)
      if (useEnhanced) {
        imageBuffer = await generateEnhancedWallpaper(user, {
          tasks: [],
          allTasks: allTasksForAchievements  // FIX: Pass allTasks for achievements
        }, timestamp, timezone);
      } else {
        imageBuffer = await generateWallpaper(user, { topTask: null, count: 0 });
      }
    } else {
      // Get active tasks
      const result = await queryWithRetry(req.dbClient, `
        SELECT * FROM tasks
        WHERE user_id = $1
        AND completed = false
        AND (archived = false OR archived IS NULL)
        AND (snoozed_until IS NULL OR snoozed_until < NOW())
      `, [userId]);

      const prioritized = prioritizeTasks(result.rows, { timezone });
      const topTasks = prioritized.slice(0, 3);
      const count = Math.max(0, prioritized.length - 3);

      console.log(`[Wallpaper] userId=${userId}, activeTasks=${prioritized.length}, topTask="${topTasks[0]?.title || 'none'}"`);

      if (useEnhanced) {
        imageBuffer = await generateEnhancedWallpaper(user, {
          tasks: prioritized,
          allTasks: allTasksForAchievements  // Use pre-fetched allTasks
        }, timestamp, timezone);
      } else {
        imageBuffer = await generateWallpaper(user, { tasks: topTasks, count });
      }
    }

    // Cache the generated wallpaper (1 minute TTL for live countdown) - only if not animated
    if (!req.query.timestamp) {
      await cacheService.setBuffer(cacheKey, imageBuffer, 60);
    }

    console.log(`✅ Wallpaper generated: ${imageBuffer.length} bytes, sending response`);
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
    // FIX: Fetch user timezone for accurate prioritization
    let userTimezone = 'UTC';
    try {
      const userRes = await req.dbClient.query('SELECT timezone FROM users WHERE id = $1', [userId]);
      if (userRes.rows.length > 0 && userRes.rows[0].timezone) {
        userTimezone = userRes.rows[0].timezone;
      }
    } catch (tzErr) {
      console.warn('Failed to fetch timezone for tasks list:', tzErr);
    }

    const prioritized = prioritizeTasks(result.rows, { location, timeOfDay: time, timezone: userTimezone });
    res.json(prioritized);
  } catch (err) {
    res.status(500).json({ error: 'Database error' });
  }
});

// GET /api/tasks/all - Returns ALL tasks with their current status
app.get('/api/tasks/all', optionalAuth, async (req, res) => {
  try {
    const userId = getUserId(req);

    // Get all tasks regardless of status
    const result = await req.dbClient.query(`
      SELECT
        id,
        title,
        completed,
        archived,
        priority,
        category,
        context_tags,
        estimate_minutes,
        due_date,
        due_time,
        snoozed_until,
        created_at,
        updated_at,
        is_private,
        privacy_level,
        privacy_display,
        CASE
          WHEN completed THEN 'completed'
          WHEN archived THEN 'archived'
          WHEN snoozed_until IS NOT NULL AND snoozed_until > NOW() THEN 'snoozed'
          ELSE 'active'
        END as status
      FROM tasks
      WHERE user_id = $1
      ORDER BY
        CASE
          WHEN completed THEN 3
          WHEN archived THEN 4
          WHEN snoozed_until IS NOT NULL AND snoozed_until > NOW() THEN 2
          ELSE 1
        END,
        priority DESC,
        due_date ASC NULLS LAST,
        created_at DESC
    `, [userId]);

    res.json({
      total: result.rows.length,
      active: result.rows.filter(t => t.status === 'active').length,
      completed: result.rows.filter(t => t.status === 'completed').length,
      archived: result.rows.filter(t => t.status === 'archived').length,
      snoozed: result.rows.filter(t => t.status === 'snoozed').length,
      tasks: result.rows
    });
  } catch (err) {
    console.error('Get all tasks error:', err);
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

// POST /api/tasks/parse-llm (Epic 8: LLM Intelligence)
// Parses user input using Gemini LLM with graceful fallback
app.post('/api/tasks/parse-llm', llmRateLimiter, verifyToken, async (req, res) => {
  try {
    const userId = getUserId(req);
    const { title } = req.body;

    if (!title || typeof title !== 'string') {
      return res.status(400).json({
        error: 'Invalid input',
        message: 'title is required and must be a string'
      });
    }

    // FIX 2026-01-06: Query user timezone for accurate time calculations
    let userTimezone = 'UTC'; // default
    try {
      const tzResult = await req.dbClient.query(
        'SELECT timezone FROM users WHERE id = $1',
        [userId]
      );
      if (tzResult.rows.length > 0 && tzResult.rows[0].timezone) {
        userTimezone = tzResult.rows[0].timezone;
      }
    } catch (tzErr) {
      console.warn('[Parse LLM] Failed to fetch timezone, using UTC:', tzErr.message);
    }

    // Parse using LLM (automatically falls back to local parser on error)
    const parsedData = await parseLLM(title, userTimezone);

    // Return parsed result
    res.json({
      success: true,
      parsed: parsedData,
      originalInput: title,
      timestamp: new Date().toISOString()
    });

  } catch (error) {
    console.error('[Parse LLM Endpoint] Error:', error);

    // Fallback to local parser on any error
    const fallbackResult = parseTask(req.body.title || '');

    res.json({
      success: true,
      parsed: {
        ...fallbackResult,
        source: 'local_fallback',
        reason: 'endpoint_error'
      },
      originalInput: req.body.title,
      timestamp: new Date().toISOString()
    });
  }
});

// POST /api/tasks
app.post('/api/tasks', taskCreationLimiter, verifyToken, async (req, res) => {
  try {
    const userId = getUserId(req);
    const { rawTitle, title: directTitle, priority, context_location, context_time, x, y, is_subtask, is_recurring, echo_interval,
      is_private, privacy_level, privacy_display } = req.body;

    // FIX 2026-01-06: Query user timezone and privacy defaults
    let userTimezone = 'UTC'; // default
    let defaultPrivacyLevel = 'public';
    try {
      const userResult = await req.dbClient.query(
        'SELECT timezone, default_privacy_level FROM users WHERE id = $1',
        [userId]
      );
      if (userResult.rows.length > 0) {
        if (userResult.rows[0].timezone) {
          userTimezone = userResult.rows[0].timezone;
          console.log(`[Task Creation] User timezone: ${userTimezone}`);
        }
        if (userResult.rows[0].default_privacy_level) {
          defaultPrivacyLevel = userResult.rows[0].default_privacy_level;
          console.log(`[Task Creation] Default privacy level: ${defaultPrivacyLevel}`);
        }
      }
    } catch (tzErr) {
      console.warn('[Task Creation] Failed to fetch user settings, using defaults:', tzErr.message);
    }

    // Apply privacy defaults (Epic 10)
    const taskPrivacyLevel = privacy_level || defaultPrivacyLevel;
    const taskIsPrivate = is_private !== undefined ? is_private : (taskPrivacyLevel !== 'public');
    const taskPrivacyDisplay = privacy_display || null;

    console.log(`[Task Creation] START - Input: "${rawTitle || directTitle}"`);


    // Parse with comprehensive NLP (Epic 7: NLP Integration) + LLM (Epic 8)
    let title, dueDate, estimateMinutes, category, contextTags, energy, recurring, timeContext, calculatedPriority, llmDueTime;
    const inputText = rawTitle || directTitle;

    try {
      // Use LLM parsing if enabled, otherwise use local NLP
      const shouldUseLLM = process.env.ENABLE_LLM_PARSING === 'true' && !!process.env.ANTHROPIC_API_KEY;
      console.log(`[Task Creation] LLM enabled: ${shouldUseLLM}, Parsing: "${inputText}"`);

      const parsed = shouldUseLLM ? await parseLLM(inputText, userTimezone) : parseTask(inputText);

      title = parsed.title;
      // LLM parser returns snake_case, local parser returns camelCase - handle both
      dueDate = parsed.dueDate || parsed.due_date;
      estimateMinutes = parsed.estimateMinutes || parsed.estimate_minutes;
      // LLM parser returns due_time as separate string (e.g., "17:00")
      llmDueTime = parsed.dueTime || parsed.due_time;

      // FIX v1.5.5: Handle "now" keyword - set due_date/time in USER'S timezone
      // CRITICAL: DO NOT set dueDate to a Date object! parseDateTimeForDB would extract UTC time.
      // Instead, only set llmDueTime to the user's local time. The v1.5.0 fix will add today's date.
      if (/\bnow\b/i.test(inputText)) {
        console.log('[Task Creation] Detected "now" keyword - setting time in user timezone');
        const now = new Date();
        // DON'T set dueDate here! Let the v1.5.0 fix handle it.
        // But we need to signal that we have a time, so clear any dueDate that was set
        dueDate = null;
        // Set llmDueTime to current time in user's timezone (HH:MM:SS format)
        llmDueTime = now.toLocaleTimeString('en-GB', {
          timeZone: userTimezone,
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        });
        console.log(`[Task Creation] "now" in ${userTimezone}: ${llmDueTime}`);
      }

      // NEW: Extract NLP metadata (handle both naming conventions)
      category = parsed.category || 'general';
      contextTags = parsed.context || parsed.context_tags || [];
      energy = parsed.energy || parsed.energy_level || 'medium';
      recurring = parsed.recurring;
      timeContext = parsed.timeContext || parsed.time_context;

      // Use explicit priority if provided, otherwise use NLP-inferred priority
      calculatedPriority = priority !== undefined ? priority : (parsed.priority || 2);

      console.log(`[${shouldUseLLM ? 'LLM' : 'NLP'}] Parsed task:`, {
        input: inputText,
        title,
        category,
        contextTags,
        priority: calculatedPriority,
        energy,
        recurring: recurring ? recurring.interval : null,
        confidence: parsed.confidence,
        source: parsed.source || 'local'
      });
    } catch (parseErr) {
      console.error('Failed to parse task:', parseErr);
      // Fallback to basic parsing
      title = inputText || 'New Task';
      dueDate = null;
      estimateMinutes = null;
      llmDueTime = null;
      category = 'general';
      contextTags = [];
      energy = 'medium';
      recurring = null;
      timeContext = null;
      calculatedPriority = 2;
    }

    // CRITICAL: Extract date and time separately from dueDate (FIX: was losing time!)
    let dueDateForDB = null;
    let dueTimeForDB = null;
    if (dueDate) {
      const parsed = parseDateTimeForDB(dueDate);
      dueDateForDB = parsed.date;
      dueTimeForDB = parsed.time;
      const dueDateStr = dueDate instanceof Date ? dueDate.toISOString() : dueDate;
      console.log(`[NLP] Parsed due: ${dueDateStr} -> date=${dueDateForDB}, time=${dueTimeForDB}`);
    }
    // LLM parser returns due_time as separate string - use it if not already extracted
    if (!dueTimeForDB && llmDueTime) {
      // Format: "HH:MM" -> "HH:MM:00" for database
      dueTimeForDB = llmDueTime.includes(':') && llmDueTime.length === 5
        ? `${llmDueTime}:00`
        : llmDueTime;
      console.log(`[LLM] Using separate due_time: ${dueTimeForDB}`);
    }

    // FIX v1.5.0 + v1.5.9: If we have due_time but no due_date, set due_date to TODAY or TOMORROW
    // Handle midnight rollover: if due_time < current_time, it's for tomorrow
    if (dueTimeForDB && !dueDateForDB) {
      const now = new Date();
      // Get current time in user's timezone
      const currentTimeStr = now.toLocaleTimeString('en-GB', {
        timeZone: userTimezone,
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });

      // Compare times to detect midnight rollover
      // If due_time is earlier than current time, it's for tomorrow
      const dueTimeParts = dueTimeForDB.split(':').map(Number);
      const currentParts = currentTimeStr.split(':').map(Number);
      const dueMinutes = dueTimeParts[0] * 60 + dueTimeParts[1];
      const currentMinutes = currentParts[0] * 60 + currentParts[1];

      if (dueMinutes < currentMinutes) {
        // Midnight rollover - set to tomorrow
        const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000);
        dueDateForDB = parseDateForDB(tomorrow);
        console.log(`[FIX] due_time ${dueTimeForDB} < current ${currentTimeStr} - setting to tomorrow: ${dueDateForDB}`);
      } else {
        dueDateForDB = parseDateForDB(now);
        console.log(`[FIX] due_time without due_date - setting to today: ${dueDateForDB}`);
      }
    }

    // FIX v1.6.0: If NO date AND NO time provided, default to NOW in user's timezone
    // This implements the requirement: "If no date/time provided → default to NOW"
    if (!dueDateForDB && !dueTimeForDB) {
      const now = new Date();
      dueDateForDB = parseDateForDB(now);
      dueTimeForDB = now.toLocaleTimeString('en-GB', {
        timeZone: userTimezone,
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
      console.log(`[FIX v1.6.0] No date/time provided - defaulting to NOW: ${dueDateForDB} ${dueTimeForDB}`);
    }

    const query = `
      INSERT INTO tasks (
        user_id, title, raw_title, estimate_minutes, priority, due_date, due_time,
        context_location, context_time, x, y, is_subtask, is_recurring, echo_interval,
        category, context_tags, energy_level, time_context,
        recurring_interval, recurring_day_of_week, recurring_day_of_month,
        is_private, privacy_level, privacy_display
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23, $24)
      RETURNING *
    `;
    const values = [
      userId,
      title,
      inputText,  // Store raw input
      estimateMinutes,
      calculatedPriority,  // Use NLP priority
      dueDateForDB,
      dueTimeForDB,
      context_location || null,
      context_time || null,
      x || null,
      y || null,
      is_subtask || false,
      is_recurring || false,
      echo_interval || null,
      // NEW NLP fields:
      category,
      contextTags,  // Array of @tags
      energy,
      timeContext,
      recurring ? recurring.interval : null,
      recurring ? recurring.dayOfWeek : null,
      recurring ? recurring.dayOfMonth : null,
      // Privacy fields (Epic 10)
      taskIsPrivate,
      taskPrivacyLevel,
      taskPrivacyDisplay
    ];

    const result = await req.dbClient.query(query, values);
    await req.dbClient.query("UPDATE users SET done_for_today = FALSE WHERE id = $1", [userId]);

    // Invalidate wallpaper cache
    const cacheInvalidated = await cacheService.invalidateUserWallpapers(userId);
    console.log(`[Task Created] userId=${userId}, title="${title}", priority=${calculatedPriority}, category=${category}, cacheInvalidated=${cacheInvalidated}`);

    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to create task' });
  }
});

// PATCH /api/tasks/:id
app.patch('/api/tasks/:id', verifyToken, async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.userId;
    const updates = req.body;

    // Validate UUID format to prevent DB errors with temporary IDs (e.g. star-123)
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(id)) {
      console.warn(`[PATCH] Invalid UUID format: ${id}. Skipping DB update.`);
      return res.status(400).json({ error: 'Invalid Task ID format' });
    }

    console.log(`[PATCH] Updating task ${id} for user ${userId}`);
    console.log(`[PATCH] Body:`, JSON.stringify(updates));

    // FIX v1.4.8: Support rawTitle for NLP re-parsing on edit
    if (updates.rawTitle) {
      console.log(`[PATCH] Re-parsing with NLP: "${updates.rawTitle}"`);

      // Get user timezone for accurate parsing
      let userTimezone = 'UTC';
      try {
        const tzResult = await req.dbClient.query(
          'SELECT timezone FROM users WHERE id = $1',
          [userId]
        );
        if (tzResult.rows.length > 0 && tzResult.rows[0].timezone) {
          userTimezone = tzResult.rows[0].timezone;
        }
      } catch (tzErr) {
        console.warn('[PATCH] Failed to fetch timezone, using UTC:', tzErr.message);
      }

      try {
        // Parse with LLM or local NLP
        const shouldUseLLM = process.env.ENABLE_LLM_PARSING === 'true' && !!process.env.ANTHROPIC_API_KEY;
        const parsed = shouldUseLLM ? await parseLLM(updates.rawTitle, userTimezone) : parseTask(updates.rawTitle);

        // FIX v1.5.2: Handle "now" keyword - set due_date/time in USER'S timezone (not UTC!)
        let parsedDueDate = parsed.dueDate || parsed.due_date;
        let parsedDueTime = parsed.dueTime || parsed.due_time;
        if (!parsedDueDate && /\bnow\b/i.test(updates.rawTitle)) {
          console.log('[PATCH] Detected "now" keyword - setting due_date to current time in user timezone');
          const now = new Date();
          parsedDueDate = now;
          // Set time in user's timezone
          parsedDueTime = now.toLocaleTimeString('en-GB', {
            timeZone: userTimezone,
            hour12: false,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
          });
          console.log(`[PATCH] "now" in ${userTimezone}: ${parsedDueTime}`);
        }

        // Replace updates with parsed values
        updates.title = parsed.title;
        if (parsedDueDate) {
          const { date, time } = parseDateTimeForDB(parsedDueDate);
          updates.due_date = date;
          // Only use extracted time if we don't have parsedDueTime from "now" fix
          if (time && !parsedDueTime) updates.due_time = time;
        }

        // Handle due_time: prefer parsedDueTime (from "now"), then LLM separate due_time
        const llmDueTime = parsedDueTime || parsed.dueTime || parsed.due_time;
        if (!updates.due_time && llmDueTime) {
          updates.due_time = llmDueTime.includes(':') && llmDueTime.length === 5
            ? `${llmDueTime}:00`
            : llmDueTime;
          console.log(`[PATCH] Using due_time: ${updates.due_time}`);
        }

        // FIX v1.5.0: If we have due_time but no due_date, set due_date to TODAY
        if (updates.due_time && !updates.due_date) {
          const now = new Date();
          updates.due_date = parseDateForDB(now);
          console.log(`[PATCH FIX] due_time without due_date - setting to today: ${updates.due_date}`);
        }

        if (parsed.estimateMinutes || parsed.estimate_minutes) {
          updates.estimate_minutes = parsed.estimateMinutes || parsed.estimate_minutes;
        }
        if (parsed.priority !== undefined) {
          updates.priority = parsed.priority;
        }
        if (parsed.category) {
          updates.category = parsed.category;
        }

        // Remove rawTitle from updates so it doesn't get processed as a field
        delete updates.rawTitle;

        console.log(`[PATCH] Parsed result: title="${updates.title}", due_date=${updates.due_date}, due_time=${updates.due_time}, priority=${updates.priority}`);
      } catch (parseErr) {
        console.error('[PATCH] Failed to parse rawTitle:', parseErr.message);
        // Continue with original updates if parsing fails
        delete updates.rawTitle;
      }
    }

    const fields = [];
    const values = [userId, id];
    let i = 3;
    // Whitelist of valid task fields (prevents SQL injection via column names)
    const validFields = [
      'title', 'priority', 'estimate_minutes', 'due_date', 'due_time',
      'completed', 'completed_at', 'snoozed_until', 'context_location',
      'context_time', 'energy_required', 'x', 'y', 'is_subtask',
      'is_recurring', 'echo_interval', 'archived', 'archived_at',
      'category',  // v1.4.8: Allow category from rawTitle parsing
      // Privacy fields (Epic 10)
      'is_private', 'privacy_level', 'privacy_display'
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
    const { duration_minutes } = req.body || {};

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
    console.error('[SNOOZE ERROR]:', err.message);
    console.error('[SNOOZE ERROR] Code:', err.code);
    console.error('[SNOOZE ERROR] Detail:', err.detail);
    res.status(500).json({ error: 'Failed to snooze task', details: err.message });
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

// Vercel Cron Job: Generate messages for active users
// Runs every 2 hours via Vercel Cron (see vercel.json)
app.get('/api/cron/messages', async (req, res) => {
  // Verify request is from Vercel Cron
  const authHeader = req.headers.authorization;
  if (authHeader !== `Bearer ${process.env.CRON_SECRET}`) {
    // Allow without secret in development or if CRON_SECRET not set
    if (process.env.NODE_ENV === 'production' && process.env.CRON_SECRET) {
      return res.status(401).json({ error: 'Unauthorized' });
    }
  }

  try {
    console.log('[Cron] Message generation job triggered');
    const { runWorkerJob } = require('./services/message-worker');
    const result = await runWorkerJob();
    res.json({ success: true, ...result });
  } catch (error) {
    console.error('[Cron] Message generation failed:', error);
    res.status(500).json({ error: error.message });
  }
});

// Debug endpoint to test timezone handling
app.get('/api/debug/timezone', async (req, res) => {
  const timezone = req.query.tz || 'Asia/Kolkata';
  const now = new Date();

  const istTime = now.toLocaleTimeString('en-GB', {
    timeZone: timezone,
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });

  res.json({
    inputTimezone: timezone,
    serverUtcTime: now.toISOString(),
    serverLocalTime: now.toString(),
    toLocaleTimeStringResult: istTime,
    typeofResult: typeof istTime,
    resultLength: istTime.length
  });
});

app.get('/api/health', async (req, res) => {
  const dbClient = req.dbClient || await getDbClient();
  const now = new Date();

  // Debug: Test timezone conversion (for v1.5.3 debugging)
  const istTime = now.toLocaleTimeString('en-GB', {
    timeZone: 'Asia/Kolkata',
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });

  res.json({
    status: 'ok',
    version: '1.6.0', // Fix: Timezone - default to NOW when no date/time, fix 'in X min/hours'
    mode: dbClient instanceof MockClient ? 'mock' : 'postgres',
    dbInitialized,
    debug: {
      serverUtcTime: now.toISOString(),
      serverLocalTime: now.toString(),
      istTimeViaLocale: istTime,
      llmEnabled: process.env.ENABLE_LLM_PARSING === 'true'
    },
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
let messageWorkerInterval;

// Traditional server startup (for local dev)
if (!process.env.VERCEL && process.env.NODE_ENV !== 'test') {
  server = app.listen(port, () => {
    console.log(`Backend running on http://localhost:${port}`);
    console.log(`Environment: ${process.env.NODE_ENV}`);

    // Start message generation worker
    if (process.env.ENABLE_LLM_MESSAGES === 'true') {
      messageWorkerInterval = startWorker();
      console.log('[Epic8] Message generation worker started');
    }
  });
}

// Graceful shutdown
process.on('SIGTERM', async () => {
  console.log('SIGTERM signal received: closing HTTP server');
  if (server) {
    server.close(async () => {
      console.log('HTTP server closed');

      // Stop message worker (Epic 8)
      if (messageWorkerInterval) {
        stopWorker(messageWorkerInterval);
        console.log('Message worker stopped');
      }

      // Close database pool
      const pool = dbPool.getDbPool();
      if (pool && !(pool instanceof MockClient)) {
        await pool.end();
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
      const pool = dbPool.getDbPool();
      if (pool && !(pool instanceof MockClient)) {
        await pool.end();
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