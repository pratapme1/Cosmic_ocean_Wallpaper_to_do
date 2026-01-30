/**
 * Epic 8: LLM Message Intelligence
 * Generates dynamic wallpaper messages using Gemini 1.5 Flash
 *
 * Features:
 * - Voice rotation (5 distinct voices)
 * - Intent rotation (6 contextual angles)
 * - Freshness constraints (never repeat)
 * - Anti-pattern validation
 * - Graceful fallback to templates
 */

const Anthropic = require('@anthropic-ai/sdk');
const pool = require('../db/pool');
const { StatsAggregator } = require('./stats-aggregator');

// Initialize Claude
let anthropic = null;

function initializeClaude() {
  if (!process.env.ANTHROPIC_API_KEY) {
    console.warn('[MessageGen] ANTHROPIC_API_KEY not set - LLM messages disabled');
    return false;
  }

  if (!process.env.ENABLE_LLM_MESSAGES || process.env.ENABLE_LLM_MESSAGES !== 'true') {
    console.log('[MessageGen] LLM messages disabled via ENABLE_LLM_MESSAGES flag');
    return false;
  }

  try {
    anthropic = new Anthropic({
      apiKey: process.env.ANTHROPIC_API_KEY,
    });
    console.log('[MessageGen] Claude initialized successfully');
    return true;
  } catch (error) {
    console.error('[MessageGen] Failed to initialize Claude:', error.message);
    return false;
  }
}

// Voice definitions
const VOICES = {
  WARM_FRIEND: {
    description: "Supportive friend who genuinely cares",
    tone: "Casual, can start with 'Hey' occasionally",
    example: "Hey, nice work today. Three left."
  },
  QUIET_OBSERVER: {
    description: "Short, observational statements",
    tone: "State facts without judgment. Minimal words.",
    example: "Three tasks. Calm evening ahead."
  },
  PLAYFUL: {
    description: "Light, slightly cheeky tone",
    tone: "Can personify tasks. Not silly—just lightly fun.",
    example: "Eight down, three standing. Who's next?"
  },
  POETIC: {
    description: "Lyrical, metaphorical, brief",
    tone: "Uses imagery and rhythm",
    example: "The day winds down. One task waits."
  },
  DIRECT: {
    description: "Clear, actionable, no fluff",
    tone: "Business-like efficiency",
    example: "Review vulnerability. Four hours left."
  }
};

// Intent definitions
const INTENTS = {
  CELEBRATE: "Focus on wins and achievements",
  NUDGE: "Gently surface next action",
  TIME_AWARE: "Acknowledge time of day and energy",
  STREAK_FOCUS: "Reference streak without preaching",
  PERMISSION: "Give permission to rest",
  FOCUS_NEXT: "Surface the most important next task"
};

/**
 * Build narrative context from user data
 * Enhanced with StatsAggregator for historical context and patterns
 */
/**
 * Build narrative context from user data
 * Enhanced with StatsAggregator for historical context and patterns
 */
async function buildMessageContext(userId) {
  // Use Singleton Pool directly - no manual connect/release needed
  // This ensures we get data and get out fast.
  const db = pool;

  try {
    // Get tasks summary (basic counts)
    const tasksQuery = `
      SELECT
        COUNT(*) FILTER (WHERE completed = false AND archived = false) as pending_count,
        COUNT(*) FILTER (WHERE completed = true AND DATE(completed_at) = CURRENT_DATE) as completed_today,
        COUNT(*) FILTER (WHERE due_date = CURRENT_DATE AND completed = false) as due_today,
        COUNT(*) FILTER (WHERE due_date < CURRENT_DATE AND completed = false) as overdue,
        MIN(due_date) FILTER (WHERE completed = false AND due_date >= CURRENT_DATE) as next_due_date
      FROM tasks
      WHERE user_id = $1;
    `;
    const tasksResult = await db.query(tasksQuery, [userId]);
    const tasks = tasksResult.rows[0];

    // Get most urgent task
    const urgentQuery = `
      SELECT title, priority, category, due_date, due_time
      FROM tasks
      WHERE user_id = $1 AND completed = false AND archived = false
      ORDER BY priority ASC, due_date ASC NULLS LAST
      LIMIT 1;
    `;
    const urgentResult = await db.query(urgentQuery, [userId]);
    const urgentTask = urgentResult.rows[0] || null;

    // ENHANCEMENT: Get tasks for StatsAggregator
    // OPTIMIZATION: Split the OR query into two simple parts to ensure index usage
    // OPTIMIZATION: Reduce limit to 20 for context window
    const recentCreatedQuery = `
      SELECT id, title, created_at, category, priority
      FROM tasks
      WHERE user_id = $1
      AND (archived = false OR archived IS NULL)
      AND created_at > NOW() - INTERVAL '7 days'
      ORDER BY created_at DESC
      LIMIT 20
    `;

    const recentCompletedQuery = `
      SELECT id, title, completed_at, category, priority
      FROM tasks
      WHERE user_id = $1
      AND (archived = false OR archived IS NULL)
      AND completed_at > NOW() - INTERVAL '7 days'
      ORDER BY completed_at DESC
      LIMIT 20
    `;

    const [recentCreated, recentCompleted] = await Promise.all([
      db.query(recentCreatedQuery, [userId]),
      db.query(recentCompletedQuery, [userId])
    ]);

    // Merge and deduplicate by ID
    const taskMap = new Map();
    [...recentCreated.rows, ...recentCompleted.rows].forEach(task => {
      taskMap.set(task.id, task);
    });
    const allTasks = Array.from(taskMap.values());

    // ENHANCEMENT: Calculate stats using StatsAggregator
    const statsAggregator = new StatsAggregator();
    const stats = statsAggregator.computeStats(allTasks);

    // Calculate time of day
    const hour = new Date().getHours();
    let timeOfDay;
    if (hour < 6) timeOfDay = 'NIGHT';
    else if (hour < 12) timeOfDay = 'MORNING';
    else if (hour < 17) timeOfDay = 'AFTERNOON';
    else if (hour < 21) timeOfDay = 'EVENING';
    else timeOfDay = 'NIGHT';

    // Get day of week
    const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    const dayOfWeek = dayNames[new Date().getDay()];

    return {
      // Basic task counts
      pendingCount: parseInt(tasks.pending_count) || 0,
      completedToday: parseInt(tasks.completed_today) || 0,
      dueToday: parseInt(tasks.due_today) || 0,
      overdue: parseInt(tasks.overdue) || 0,
      nextDueDate: tasks.next_due_date,
      urgentTask,

      // Time context
      timeOfDay,
      dayOfWeek,
      hour,

      // ENHANCEMENT: Historical stats and patterns
      stats: {
        completedThisWeek: stats.completedThisWeek,
        totalCompleted: stats.totalCompleted,
        streakDays: stats.streakDays,
        longestStreak: stats.longestStreak,
        averagePerDay: stats.averagePerDay,
        patterns: stats.patterns  // peakPeriod, topCategory, mostProductiveDay
      }
    };
  } catch (error) {
    console.error('[MessageGen] Error building context:', error);
    throw error;
  }
}

/**
 * Get recent messages to avoid repetition
 */
async function getRecentMessages(userId, limit = 20) {
  const db = pool;

  try {
    const query = `
      SELECT message, voice, intent
      FROM message_history
      WHERE user_id = $1
      ORDER BY shown_at DESC
      LIMIT $2;
    `;
    const result = await db.query(query, [userId, limit]);
    return result.rows;
  } catch (error) {
    console.error('[MessageGen] Error fetching recent:', error.message);
    return [];
  }
}

// Helper functions

function selectNextVoice(recentMessages, context) {
  // Avoid recent voices (last 5)
  const recentVoices = new Set(recentMessages.slice(0, 5).map(m => m.voice));
  const availableVoices = Object.keys(VOICES).filter(v => !recentVoices.has(v));

  // If all used, pick least recently used
  if (availableVoices.length === 0) {
    const counts = {};
    recentMessages.forEach(m => {
      counts[m.voice] = (counts[m.voice] || 0) + 1;
    });
    // Sort by usage (ascending)
    const sorted = Object.keys(VOICES).sort((a, b) => (counts[a] || 0) - (counts[b] || 0));
    return sorted[0];
  }

  // Randomly pick from available
  return availableVoices[Math.floor(Math.random() * availableVoices.length)];
}

function selectNextIntent(context, recentMessages) {
  // Logic based on context
  const { pendingCount, completedToday, timeOfDay, urgentTask } = context;

  // Potential intents
  let candidates = [];

  // Urgent task -> NUDGE or FOCUS_NEXT
  if (urgentTask) {
    candidates.push('NUDGE', 'FOCUS_NEXT');
    if (urgentTask.priority <= 1) candidates.push('DIRECT'); // Very urgent
  }

  // Completed a lot -> CELEBRATE
  if (completedToday > 3) {
    candidates.push('CELEBRATE');
  }

  // Time of day logic
  if (timeOfDay === 'MORNING') candidates.push('NUDGE', 'FOCUS_NEXT');
  if (timeOfDay === 'EVENING' || timeOfDay === 'NIGHT') candidates.push('PERMISSION'); // Rest

  // Default fallback
  if (candidates.length === 0) {
    candidates = Object.keys(INTENTS);
  }

  // Filter out recently used intents (last 3)
  const recentIntents = new Set(recentMessages.slice(0, 3).map(m => m.intent));
  const available = candidates.filter(i => !recentIntents.has(i));

  if (available.length > 0) {
    return available[Math.floor(Math.random() * available.length)];
  }

  // Fallback to random candidate
  return candidates[Math.floor(Math.random() * candidates.length)];
}

function buildFreshnessConstraints(recentMessages) {
  if (!recentMessages || recentMessages.length === 0) return '';

  const recentTexts = recentMessages.slice(0, 10).map(m => `"${m.message}"`).join(', ');
  return `\nCRITICAL CONSTRAINT: Do NOT repeat or paraphrase these recent messages: ${recentTexts}. Generate something new.`;
}

function buildMessagePrompt(context, voice, intent, recentMessages) {
  const voiceDef = VOICES[voice];
  const intentDef = INTENTS[intent];

  let taskContext = `You have ${context.pendingCount} pending tasks.`;
  if (context.urgentTask) {
    taskContext += ` Most urgent: "${context.urgentTask.title}" (Priority ${context.urgentTask.priority}).`;
  }
  if (context.completedToday > 0) {
    taskContext += ` You completed ${context.completedToday} tasks today.`;
  }

  const constraints = buildFreshnessConstraints(recentMessages);

  return `
    Context: ${taskContext}
    Time of Day: ${context.timeOfDay}
    
    Role: You are a "Cosmic Companion" with the voice: "${voice}" (${voiceDef.description}).
    Tone: ${voiceDef.tone}
    Example: "${voiceDef.example}"

    Goal: Generate 3 distinct, short wallpaper messages (max 10 words each).
    Intent: "${intent}" (${intentDef}).
    
    ${constraints}

    Output Format: return JSON only in this format:
    {
      "messages": ["message 1", "message 2", "message 3"]
    }
  `;
}

function validateMessages(messages, recentMessages) {
  // Filter out empty, too long, or duplicates
  const recentSet = new Set(recentMessages.map(m => m.message.toLowerCase()));

  return messages.filter(msg => {
    if (!msg || typeof msg !== 'string') return false;
    if (msg.length > 60) return false; // Max length constraint
    if (recentSet.has(msg.toLowerCase())) return false; // Exact duplicate
    return true;
  });
}

/**
 * Generate messages using LLM
 */
async function generateMessagesLLM(userId, context) {
  if (!initializeClaude()) {
    console.log('[MessageGen] Claude not available, falling back to templates');
    return null;  // Fallback to templates
  }

  try {
    // Get recent messages for freshness (Auto-released pool query)
    const recentMessages = await getRecentMessages(userId, 20);

    // Select voice and intent
    const voice = selectNextVoice(recentMessages, context);
    const intent = selectNextIntent(context, recentMessages);

    console.log(`[MessageGen] Generating messages - Voice: ${voice}, Intent: ${intent}`);

    // Build prompt
    const prompt = buildMessagePrompt(context, voice, intent, recentMessages);

    // Call Claude
    // Use configurable model name, default to claude-3-haiku
    const modelName = process.env.CLAUDE_MODEL || 'claude-3-haiku-20240307';

    const timeoutPromise = new Promise((_, reject) => {
      setTimeout(() => reject(new Error('Claude timeout (25s)')), 25000);
    });

    const generatePromise = anthropic.messages.create({
      model: modelName,
      max_tokens: 1024,
      temperature: 0.9,  // Higher creativity for variety
      messages: [{
        role: 'user',
        content: prompt
      }]
    });

    const result = await Promise.race([generatePromise, timeoutPromise]);
    const text = result.content[0].text;

    // Parse JSON response (clean markdown code blocks if present)
    let cleanJson = text.replace(/```json\n?|\n?```/g, '').trim();

    // If response starts with text (not JSON), try to extract JSON object
    if (!cleanJson.startsWith('{')) {
      const jsonMatch = cleanJson.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        cleanJson = jsonMatch[0];
        console.log('[MessageGen] Extracted JSON from text response');
      } else {
        throw new Error('No JSON object found in response');
      }
    }

    const parsed = JSON.parse(cleanJson);

    if (!parsed.messages || !Array.isArray(parsed.messages)) {
      throw new Error('Invalid response format from Claude');
    }

    console.log(`[MessageGen] Generated ${parsed.messages.length} raw messages`);

    // Validate messages
    const validMessages = validateMessages(parsed.messages, recentMessages);

    console.log(`[MessageGen] ${validMessages.length}/${parsed.messages.length} messages passed validation`);

    if (validMessages.length === 0) {
      console.warn('[MessageGen] No valid messages generated, falling back');
      return null;
    }

    // Return messages with metadata
    return validMessages.map((msg, index) => ({
      message: msg,
      voice,
      intent,
      displayOrder: index + 1
    }));

  } catch (error) {
    console.error('[MessageGen] Error generating messages:', error.message);
    return null;  // Fallback to templates
  }
}

/**
 * Get fallback message (template-based)
 */
function getFallbackMessage(context) {
  // ... existing implementation remains unchanged, no DB access ...
  const { timeOfDay, pendingCount, completedToday } = context;

  // No tasks - zen messages
  if (pendingCount === 0) {
    const zenMessages = ['Clear skies', 'All done', 'Nothing pending', 'Day complete'];
    return zenMessages[Math.floor(Math.random() * zenMessages.length)];
  }

  // One task - focused messages
  if (pendingCount === 1) {
    return 'One task waiting';
  }

  // Multiple tasks - time-based
  const timeBasedFallbacks = {
    MORNING: ['Morning momentum', "Day's unfolding", 'Fresh start'],
    AFTERNOON: ['Midday push', 'Keep the flow', 'Afternoon focus'],
    EVENING: ['Evening wind-down', 'Day settling', 'Wrapping up'],
    NIGHT: ['Rest well', 'Night falls', 'Tomorrow awaits']
  };

  const messages = timeBasedFallbacks[timeOfDay] || ['Tasks await'];
  return messages[Math.floor(Math.random() * messages.length)];
}

/**
 * Cache messages in database
 */
async function cacheMessages(userId, messages) {
  let client = null;

  if (!pool) return;
  // We need a transaction here for atomicity (Delete + Insert)
  // So we MUST explicitly connect and release
  client = await pool.connect();

  try {
    await client.query('BEGIN');

    // OPTIMIZATION: Removed redundant SELECT 1 check (userId is trusted from context)
    // const userCheck = await client.query('SELECT 1 FROM users WHERE id = $1', [userId]);
    // if (userCheck.rows.length === 0) { ... }

    // Clear old cached messages (keep only last 10 shown for history)
    await client.query(
      'DELETE FROM message_cache WHERE user_id = $1',
      [userId]
    );

    // Insert new messages
    for (const msg of messages) {
      await client.query(
        `INSERT INTO message_cache (user_id, message, voice, intent, display_order, shown)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [userId, msg.message, msg.voice, msg.intent, msg.displayOrder, false]
      );
    }

    await client.query('COMMIT');
    console.log(`[MessageGen] Cached ${messages.length} messages for user ${userId}`);
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('[MessageGen] Error caching messages:', error.message);
    throw error;
  } finally {
    client.release();
  }
}

// Import cache service for distributed locking
const cacheService = require('./cache');

// In-memory lock to prevent thundering herd (local process only)
const generatingUsers = new Set();

/**
 * Main entry point: Generate and cache messages
 */
async function generateAndCacheMessages(userId) {
  // Check local in-memory lock first (fastest)
  if (generatingUsers.has(userId)) {
    console.log(`[MessageGen] Generation locally in progress for ${userId}, skipping`);
    return { success: false, reason: 'in_progress_local' };
  }

  // Check distributed Redis lock (cross-instance protection)
  const lockKey = `gen_lock:${userId}`;
  const isLocked = await cacheService.exists(lockKey);

  if (isLocked) {
    console.log(`[MessageGen] Generation locked (distributed) for ${userId}, skipping`);
    return { success: false, reason: 'in_progress_distributed' };
  }

  // Acquire distributed lock (60s TTL)
  await cacheService.set(lockKey, '1', 60);

  try {
    generatingUsers.add(userId);
    console.log(`[MessageGen] Generating messages for user ${userId}...`);

    // Build context (Auto-released pool query)
    // STEP 1: DB ACCESS -> RELEASE
    const context = await buildMessageContext(userId);

    // Generate messages with LLM
    // STEP 2: NET ACCESS (SLOW) -> NO DB LOCK
    let messages = await generateMessagesLLM(userId, context);

    // Fallback to template if LLM failed
    if (!messages || messages.length === 0) {
      console.log('[MessageGen] Using fallback templates');
      const fallbackMsg = getFallbackMessage(context);
      messages = [{
        message: fallbackMsg,
        voice: 'DIRECT',
        intent: 'NUDGE',
        displayOrder: 1
      }];
    }

    // Cache messages
    // STEP 3: DB ACCESS (TRANSACTION) -> CONNECT -> RELEASE
    await cacheMessages(userId, messages);

    return {
      success: true,
      count: messages.length,
      source: messages.length > 1 ? 'llm' : 'template'
    };

  } catch (error) {
    console.error(`[MessageGen] Failed to generate messages for user ${userId}:`, error.message);
    return {
      success: false,
      error: error.message
    };
  } finally {
    generatingUsers.delete(userId);
    // Release distributed lock
    await cacheService.del(lockKey);
  }
}

module.exports = {
  generateAndCacheMessages,
  buildMessageContext,
  getFallbackMessage,
  VOICES,
  INTENTS,
  generatingUsers
};
