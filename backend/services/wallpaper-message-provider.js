/**
 * Epic 8: Wallpaper Message Provider
 * Manages message cache rotation and fallback
 *
 * Features:
 * - Retrieves next unshown message from cache
 * - Marks messages as shown
 * - Logs to history for analytics
 * - Triggers background refresh when cache low
 * - Falls back to templates when cache empty
 */

const pool = require('../db/pool');
const { generateAndCacheMessages, getFallbackMessage } = require('./message-generator-llm');

/**
 * Get next unshown message from cache
 */
/**
 * Get next unshown message from cache
 */
async function getNextCachedMessage(userId, client = null) {
  // Use client if provided (transactional context), otherwise singleton pool
  const db = client || pool;

  const query = `
    SELECT id, message, voice, intent
    FROM message_cache
    WHERE user_id = $1 AND shown = false
    ORDER BY display_order ASC
    LIMIT 1;
  `;

  const result = await db.query(query, [userId]);
  return result.rows[0] || null;
}

/**
 * Mark message as shown
 */
async function markMessageShown(messageId, client = null) {
  const db = client || pool;
  const query = 'UPDATE message_cache SET shown = true, shown_at = NOW() WHERE id = $1';
  await db.query(query, [messageId]);
}

/**
 * Log message to history
 */
async function logToHistory(userId, message, voice, intent, context, client = null) {
  const db = client || pool;
  const query = `INSERT INTO message_history (user_id, message, voice, intent, context, shown_at)
       VALUES ($1, $2, $3, $4, $5, NOW())`;
  const params = [userId, message, voice, intent, context];

  try {
    await db.query(query, params);
  } catch (error) {
    console.error('[MessageProvider] Error logging to history:', error.message);
  }
}

/**
 * Get count of unshown messages in cache
 */
async function getUnshownCount(userId, client = null) {
  const db = client || pool;
  const query = 'SELECT COUNT(*) as count FROM message_cache WHERE user_id = $1 AND shown = false';
  const result = await db.query(query, [userId]);
  return parseInt(result.rows[0].count) || 0;
}

/**
 * Trigger background message generation (async, non-blocking)
 */
function triggerBackgroundRefresh(userId) {
  // Run async without waiting
  setImmediate(async () => {
    try {
      console.log(`[MessageProvider] Triggering background refresh for user ${userId}`);
      await generateAndCacheMessages(userId);
    } catch (error) {
      console.error(`[MessageProvider] Background refresh failed for user ${userId}:`, error.message);
    }
  });
}

/**
 * Main entry point: Get current message for wallpaper
 *
 * Flow:
 * 1. Try to get from cache
 * 2. If cache empty, generate immediately
 * 3. If generation fails, use fallback template
 */
async function getCurrentMessage(userId, context = null, depth = 0) {
  // Prevent infinite recursion
  if (depth > 1) {
    console.warn('[MessageProvider] Max recursion depth reached, using hard fallback');
    return {
      message: 'Tasks await',
      source: 'depth_fallback',
      voice: 'DIRECT',
      intent: 'NUDGE'
    };
  }

  // ZERO-FAILURE ARCHITECTURE:
  // Do NOT acquire a connection here. Use pool.query() for atomic ops
  // or allow generateAndCacheMessages to handle its own short-lived connections.
  let client = null;
  try {
    // 1. Try to get from cache using singleton pool
    const cached = await getNextCachedMessage(userId);

    if (cached) {
      // Mark as shown
      await markMessageShown(cached.id, client);

      // Log to history
      await logToHistory(userId, cached.message, cached.voice, cached.intent, context, client);

      // Check if cache running low (trigger refresh)
      const remaining = await getUnshownCount(userId, client);
      console.log(`[MessageProvider] Cache remaining for user ${userId}: ${remaining}`);

      if (remaining <= 2) {
        console.log(`[MessageProvider] Cache low (${remaining} remaining), triggering refresh`);
        triggerBackgroundRefresh(userId);
      }

      console.log(`[MessageProvider] Returning cached message: "${cached.message}"`);
      return {
        message: cached.message,
        source: 'cache',
        voice: cached.voice,
        intent: cached.intent
      };
    }

    // Release client before potentially long generation step
    if (client) {
      client.release();
      client = null;
    }

    // 2. Cache empty - generate immediately
    // Note: generateAndCacheMessages internally manages its own transaction/connection
    console.log('[MessageProvider] Cache empty, generating immediately...');
    // IMPORTANT: Do NOT pass client here. Let generator manage split transaction.
    const generateResult = await generateAndCacheMessages(userId);

    if (generateResult.success && generateResult.count > 0) {
      // Recursively get first message from newly populated cache
      return await getCurrentMessage(userId, context, depth + 1);
    }

    // 3. Generation failed - use fallback template
    console.warn('[MessageProvider] Generation failed, using fallback template');
    const messageContext = context || { pendingCount: 0, completedToday: 0, timeOfDay: 'MORNING' };
    const fallbackMsg = getFallbackMessage(messageContext);

    // Log fallback to history (best effort)
    await logToHistory(userId, fallbackMsg, 'DIRECT', 'NUDGE', messageContext);

    return {
      message: fallbackMsg,
      source: 'template_fallback',
      voice: 'DIRECT',
      intent: 'NUDGE'
    };

  } catch (error) {
    console.error('[MessageProvider] CRITICAL Error in getCurrentMessage:', error.message);

    // ZERO-FAILURE FAILSAFE:
    // If DB is totally down or times out, return a safe static message without crashing.
    return {
      message: 'Tasks await',
      source: 'failsafe_fallback',
      voice: 'DIRECT',
      intent: 'NUDGE'
    };
  }
}

/**
 * Prefill cache for user (called during background job)
 */
async function prefillCache(userId) {
  try {
    const remaining = await getUnshownCount(userId);

    if (remaining >= 3) {
      console.log(`[MessageProvider] Cache sufficient for user ${userId} (${remaining} messages)`);
      return { prefilled: false, reason: 'cache_sufficient', remaining };
    }

    console.log(`[MessageProvider] Prefilling cache for user ${userId} (${remaining} remaining)`);

    const result = await generateAndCacheMessages(userId);

    return {
      prefilled: true,
      count: result.count,
      source: result.source,
      remaining: await getUnshownCount(userId)
    };

  } catch (error) {
    console.error(`[MessageProvider] Error prefilling cache for user ${userId}:`, error.message);
    return {
      prefilled: false,
      error: error.message
    };
  }
}

module.exports = {
  getCurrentMessage,
  prefillCache,
  getUnshownCount,
  triggerBackgroundRefresh
};
