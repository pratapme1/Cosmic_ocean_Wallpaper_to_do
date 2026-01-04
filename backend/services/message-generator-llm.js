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

const { GoogleGenerativeAI } = require('@google/generative-ai');
const pool = require('../db/pool');

// Initialize Gemini
let genAI = null;

function initializeGemini() {
  if (!process.env.GEMINI_API_KEY) {
    console.warn('[MessageGen] GEMINI_API_KEY not set - LLM messages disabled');
    return false;
  }

  if (!process.env.ENABLE_LLM_MESSAGES || process.env.ENABLE_LLM_MESSAGES !== 'true') {
    console.log('[MessageGen] LLM messages disabled via ENABLE_LLM_MESSAGES flag');
    return false;
  }

  try {
    genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
    console.log('[MessageGen] Gemini initialized successfully');
    return true;
  } catch (error) {
    console.error('[MessageGen] Failed to initialize Gemini:', error.message);
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
 */
async function buildMessageContext(userId) {
  const client = await pool.connect();

  try {
    // Get tasks summary
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
    const tasksResult = await client.query(tasksQuery, [userId]);
    const tasks = tasksResult.rows[0];

    // Get most urgent task
    const urgentQuery = `
      SELECT title, priority, category, due_date, due_time
      FROM tasks
      WHERE user_id = $1 AND completed = false AND archived = false
      ORDER BY priority ASC, due_date ASC NULLS LAST
      LIMIT 1;
    `;
    const urgentResult = await client.query(urgentQuery, [userId]);
    const urgentTask = urgentResult.rows[0] || null;

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
      pendingCount: parseInt(tasks.pending_count) || 0,
      completedToday: parseInt(tasks.completed_today) || 0,
      dueToday: parseInt(tasks.due_today) || 0,
      overdue: parseInt(tasks.overdue) || 0,
      nextDueDate: tasks.next_due_date,
      urgentTask,
      timeOfDay,
      dayOfWeek,
      hour
    };
  } finally {
    client.release();
  }
}

/**
 * Get recent messages to avoid repetition
 */
async function getRecentMessages(userId, limit = 20) {
  const client = await pool.connect();

  try {
    const query = `
      SELECT message, voice, intent
      FROM message_history
      WHERE user_id = $1
      ORDER BY shown_at DESC
      LIMIT $2;
    `;
    const result = await client.query(query, [userId, limit]);
    return result.rows;
  } finally {
    client.release();
  }
}

/**
 * Select next voice (least recently used)
 */
function selectNextVoice(recentMessages, context) {
  const voiceOrder = Object.keys(VOICES);
  const recentVoices = recentMessages.map(m => m.voice);

  // Find least recently used voice
  let nextVoice = voiceOrder.find(v => !recentVoices.includes(v));

  // If all voices used recently, pick random
  if (!nextVoice) {
    nextVoice = voiceOrder[Math.floor(Math.random() * voiceOrder.length)];
  }

  // Context-aware overrides
  if (context.timeOfDay === 'NIGHT' && nextVoice === 'DIRECT') {
    nextVoice = 'QUIET_OBSERVER';  // Don't be harsh at night
  }

  if (context.overdue > 3) {
    nextVoice = 'DIRECT';  // Critical urgency needs direct voice
  }

  return nextVoice;
}

/**
 * Select next intent based on context
 */
function selectNextIntent(context, recentMessages) {
  const recentIntents = recentMessages.map(m => m.intent);

  // Context-driven intent selection
  if (context.pendingCount === 0 && context.completedToday > 0) {
    return 'CELEBRATE';  // All done!
  }

  if (context.completedToday >= 5) {
    return Math.random() > 0.5 ? 'CELEBRATE' : 'PERMISSION';
  }

  if (context.overdue > 0) {
    return 'FOCUS_NEXT';  // Need to tackle overdue tasks
  }

  if (context.dueToday > 0) {
    return 'NUDGE';  // Gentle reminder of today's tasks
  }

  if (context.timeOfDay === 'MORNING') {
    return 'TIME_AWARE';  // Fresh start messaging
  }

  if (context.timeOfDay === 'NIGHT') {
    return 'PERMISSION';  // Okay to rest
  }

  // Find least recently used intent
  const intentOrder = Object.keys(INTENTS);
  const nextIntent = intentOrder.find(i => !recentIntents.includes(i));

  return nextIntent || 'NUDGE';
}

/**
 * Build freshness constraints from recent messages
 */
function buildFreshnessConstraints(recentMessages) {
  if (recentMessages.length === 0) {
    return '';
  }

  let constraints = '\nRECENT MESSAGES (DO NOT repeat or closely paraphrase):\n';

  recentMessages.slice(0, 10).forEach(msg => {
    constraints += `- "${msg.message}"\n`;
  });

  // Extract overused words (appeared 3+ times)
  const words = recentMessages
    .map(m => m.message)
    .join(' ')
    .toLowerCase()
    .split(/\s+/);

  const wordCounts = {};
  words.forEach(w => {
    if (w.length > 3) {  // Skip short words
      wordCounts[w] = (wordCounts[w] || 0) + 1;
    }
  });

  const overused = Object.entries(wordCounts)
    .filter(([_, count]) => count >= 3)
    .map(([word, _]) => word);

  if (overused.length > 0) {
    constraints += `\nOVERUSED WORDS to avoid: ${overused.join(', ')}\n`;
  }

  return constraints;
}

/**
 * Build dynamic prompt for message generation
 */
function buildMessagePrompt(context, voice, intent, recentMessages) {
  const voiceConfig = VOICES[voice];
  const intentDesc = INTENTS[intent];

  // Build narrative context
  let narrative = '';

  if (context.pendingCount === 0) {
    narrative = `User has completed all tasks. `;
    if (context.completedToday > 0) {
      narrative += `They finished ${context.completedToday} tasks today.`;
    } else {
      narrative += `Clear day ahead.`;
    }
  } else if (context.pendingCount === 1) {
    narrative = `User has one task pending.`;
    if (context.urgentTask) {
      narrative += ` It's "${context.urgentTask.title}"`;
      if (context.urgentTask.due_date) {
        const dueDate = new Date(context.urgentTask.due_date);
        const today = new Date();
        const diffDays = Math.ceil((dueDate - today) / (1000 * 60 * 60 * 24));
        if (diffDays === 0) narrative += ` (due today)`;
        else if (diffDays === 1) narrative += ` (due tomorrow)`;
        else if (diffDays < 0) narrative += ` (overdue)`;
      }
    }
  } else {
    narrative = `User has ${context.pendingCount} tasks pending.`;
    if (context.completedToday > 0) {
      narrative += ` Completed ${context.completedToday} today.`;
    }
    if (context.dueToday > 0) {
      narrative += ` ${context.dueToday} due today.`;
    }
    if (context.overdue > 0) {
      narrative += ` ${context.overdue} overdue.`;
    }
  }

  narrative += `\n\nTime context: ${context.dayOfWeek} ${context.timeOfDay.toLowerCase()}.`;

  const freshnessConstraints = buildFreshnessConstraints(recentMessages);

  return `Generate 5 short wallpaper messages.

CONTEXT:
${narrative}

VOICE: ${voiceConfig.description}
TONE: ${voiceConfig.tone}
EXAMPLE: "${voiceConfig.example}"

INTENT: ${intentDesc}
${freshnessConstraints}

ANTI-PATTERNS (NEVER use):
- Starting with "Great job" / "Nice work" / "Well done"
- "You got this" / "Keep it up" / "Keep going"
- "Crushing it" / "Killing it"
- Corporate motivation speak
- Exclamation marks on every message
- Starting with "Solid" (common LLM crutch)
- Questions in every message
- Emojis (not supported in wallpaper font)

RULES:
- Maximum 8 words per message
- Each message must be meaningfully different
- Natural, human language
- No emojis
- Focus on variety - different sentence structures, different angles

Return JSON:
{
  "messages": [
    "First message here",
    "Second message here",
    "Third message here",
    "Fourth message here",
    "Fifth message here"
  ]
}`;
}

/**
 * Validate generated messages
 */
function validateMessages(messages, recentMessages) {
  const antiPatterns = [
    'great job', 'nice work', 'well done', 'you got this',
    'keep it up', 'keep going', 'crushing it', 'killing it',
    'solid', 'awesome', 'amazing', 'fantastic'
  ];

  return messages.filter(msg => {
    // Word count check (max 8 words)
    const wordCount = msg.split(' ').length;
    if (wordCount > 8) {
      console.warn(`[MessageGen] Message too long (${wordCount} words): "${msg}"`);
      return false;
    }

    // Anti-pattern check
    const lower = msg.toLowerCase();
    for (const pattern of antiPatterns) {
      if (lower.includes(pattern)) {
        console.warn(`[MessageGen] Anti-pattern "${pattern}" detected in: "${msg}"`);
        return false;
      }
    }

    // Exact duplicate check
    const recentTexts = recentMessages.map(m => m.message.toLowerCase());
    if (recentTexts.includes(lower)) {
      console.warn(`[MessageGen] Duplicate message: "${msg}"`);
      return false;
    }

    // Emoji check (simple heuristic - most emojis are outside ASCII range)
    if (/[^\x00-\x7F]/.test(msg)) {
      console.warn(`[MessageGen] Non-ASCII characters (emoji?) detected: "${msg}"`);
      return false;
    }

    return true;
  });
}

/**
 * Generate messages using LLM
 */
async function generateMessagesLLM(userId, context) {
  if (!initializeGemini()) {
    console.log('[MessageGen] Gemini not available, falling back to templates');
    return null;  // Fallback to templates
  }

  try {
    // Get recent messages for freshness
    const recentMessages = await getRecentMessages(userId, 20);

    // Select voice and intent
    const voice = selectNextVoice(recentMessages, context);
    const intent = selectNextIntent(context, recentMessages);

    console.log(`[MessageGen] Generating messages - Voice: ${voice}, Intent: ${intent}`);

    // Build prompt
    const prompt = buildMessagePrompt(context, voice, intent, recentMessages);

    // Call Gemini
    // Use configurable model name, default to gemini-2.0-flash-lite
    const modelName = process.env.GEMINI_MODEL || 'gemini-2.0-flash-lite';
    const model = genAI.getGenerativeModel({
      model: modelName,
      generationConfig: {
        temperature: 0.9,  // Higher creativity for variety
        maxOutputTokens: 150,
        responseMimeType: 'application/json'
      }
    });

    const timeoutPromise = new Promise((_, reject) => {
      setTimeout(() => reject(new Error('Gemini timeout (10s)')), 10000);
    });

    const generatePromise = model.generateContent(prompt);

    const result = await Promise.race([generatePromise, timeoutPromise]);
    const response = result.response;
    const text = response.text();

    // Parse JSON response
    const parsed = JSON.parse(text);

    if (!parsed.messages || !Array.isArray(parsed.messages)) {
      throw new Error('Invalid response format from Gemini');
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
  const client = await pool.connect();

  try {
    await client.query('BEGIN');

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

/**
 * Main entry point: Generate and cache messages
 */
async function generateAndCacheMessages(userId) {
  try {
    console.log(`[MessageGen] Generating messages for user ${userId}...`);

    // Build context
    const context = await buildMessageContext(userId);

    // Generate messages with LLM
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
  }
}

module.exports = {
  generateAndCacheMessages,
  buildMessageContext,
  getFallbackMessage,
  VOICES,
  INTENTS
};
