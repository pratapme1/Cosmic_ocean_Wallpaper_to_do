/**
 * Rate Limiter Middleware for LLM Endpoints
 *
 * Prevents API abuse and manages LLM API costs by limiting:
 * - 10 requests per minute per user
 * - 100 requests per day per user
 *
 * Tracks usage in-memory (with Redis fallback for production scaling)
 *
 * @module middleware/rate-limiter
 */

const rateLimit = require('express-rate-limit');
const { parseTask } = require('../utils/task-parser');

// In-memory storage for rate limiting (simple Map)
// TODO: Replace with Redis for multi-instance deployments
const userRequestCounts = new Map();

/**
 * Get or initialize user rate limit counters
 *
 * @param {string} userId - User ID
 * @returns {object} - Counter object with minute and day buckets
 */
function getUserCounters(userId) {
  if (!userRequestCounts.has(userId)) {
    const now = Date.now();
    userRequestCounts.set(userId, {
      minute: {
        count: 0,
        resetAt: now + 60000 // 1 minute
      },
      day: {
        count: 0,
        resetAt: now + 86400000 // 24 hours
      }
    });
  }
  return userRequestCounts.get(userId);
}

/**
 * Reset counters if time window expired
 *
 * @param {object} counters - User counters
 */
function resetExpiredCounters(counters) {
  const now = Date.now();

  if (now >= counters.minute.resetAt) {
    counters.minute.count = 0;
    counters.minute.resetAt = now + 60000;
  }

  if (now >= counters.day.resetAt) {
    counters.day.count = 0;
    counters.day.resetAt = now + 86400000;
  }
}

/**
 * Check if user is within rate limits
 *
 * @param {string} userId - User ID
 * @returns {object} - { allowed: boolean, reason: string, resetAt: number }
 */
function checkRateLimit(userId) {
  const counters = getUserCounters(userId);
  resetExpiredCounters(counters);

  // Check minute limit (10 requests/min)
  if (counters.minute.count >= 10) {
    return {
      allowed: false,
      reason: 'minute_limit_exceeded',
      limit: 10,
      window: 'minute',
      resetAt: counters.minute.resetAt,
      resetIn: Math.ceil((counters.minute.resetAt - Date.now()) / 1000)
    };
  }

  // Check day limit (100 requests/day)
  if (counters.day.count >= 100) {
    return {
      allowed: false,
      reason: 'day_limit_exceeded',
      limit: 100,
      window: 'day',
      resetAt: counters.day.resetAt,
      resetIn: Math.ceil((counters.day.resetAt - Date.now()) / 1000)
    };
  }

  // Increment counters
  counters.minute.count++;
  counters.day.count++;

  return {
    allowed: true,
    remaining: {
      minute: 10 - counters.minute.count,
      day: 100 - counters.day.count
    },
    resetAt: {
      minute: counters.minute.resetAt,
      day: counters.day.resetAt
    }
  };
}

/**
 * Express middleware for LLM endpoint rate limiting
 *
 * If rate limited, automatically falls back to local parser
 * instead of returning 429 error.
 */
function llmRateLimiter(req, res, next) {
  // Extract user ID from auth token or request
  const userId = req.user?.id || req.body?.userId || 'anonymous';

  // Check rate limit
  const limitCheck = checkRateLimit(userId);

  // Add rate limit info to response headers
  if (limitCheck.allowed) {
    res.setHeader('X-RateLimit-Remaining-Minute', limitCheck.remaining.minute);
    res.setHeader('X-RateLimit-Remaining-Day', limitCheck.remaining.day);
    res.setHeader('X-RateLimit-Reset-Minute', new Date(limitCheck.resetAt.minute).toISOString());
    res.setHeader('X-RateLimit-Reset-Day', new Date(limitCheck.resetAt.day).toISOString());

    // Allow request to proceed
    next();
  } else {
    // Rate limited - fallback to local parser instead of error
    console.warn(`[Rate Limiter] User ${userId} exceeded ${limitCheck.window} limit (${limitCheck.limit} req/${limitCheck.window})`);

    // Parse using local parser
    const { title } = req.body;
    const fallbackResult = parseTask(title || '');

    // Add rate limit metadata
    res.setHeader('X-RateLimit-Exceeded', 'true');
    res.setHeader('X-RateLimit-Retry-After', limitCheck.resetIn);

    // Return fallback parse result with rate limit info
    return res.json({
      ...fallbackResult,
      source: 'local_fallback',
      reason: 'rate_limited',
      rateLimitInfo: {
        window: limitCheck.window,
        limit: limitCheck.limit,
        resetIn: limitCheck.resetIn,
        message: `Rate limit exceeded. Try again in ${limitCheck.resetIn} seconds.`
      }
    });
  }
}

/**
 * Get current rate limit status for a user
 *
 * @param {string} userId - User ID
 * @returns {object} - Current usage and limits
 */
function getRateLimitStatus(userId) {
  const counters = getUserCounters(userId);
  resetExpiredCounters(counters);

  return {
    minute: {
      used: counters.minute.count,
      limit: 10,
      remaining: 10 - counters.minute.count,
      resetAt: new Date(counters.minute.resetAt).toISOString(),
      resetIn: Math.ceil((counters.minute.resetAt - Date.now()) / 1000)
    },
    day: {
      used: counters.day.count,
      limit: 100,
      remaining: 100 - counters.day.count,
      resetAt: new Date(counters.day.resetAt).toISOString(),
      resetIn: Math.ceil((counters.day.resetAt - Date.now()) / 1000)
    }
  };
}

/**
 * Clear rate limit counters for a user (admin only)
 *
 * @param {string} userId - User ID
 */
function clearRateLimit(userId) {
  userRequestCounts.delete(userId);
  console.log(`[Rate Limiter] Cleared limits for user ${userId}`);
}

/**
 * Cleanup old entries (runs periodically)
 */
function cleanup() {
  const now = Date.now();
  const threshold = now - 86400000; // 24 hours ago

  for (const [userId, counters] of userRequestCounts.entries()) {
    // If both counters expired, remove entry
    if (counters.minute.resetAt < threshold && counters.day.resetAt < threshold) {
      userRequestCounts.delete(userId);
    }
  }

  console.log(`[Rate Limiter] Cleanup complete. Active users: ${userRequestCounts.size}`);
}

// Run cleanup every hour
setInterval(cleanup, 3600000);

// Export middleware and utility functions
module.exports = {
  llmRateLimiter,
  checkRateLimit,
  getRateLimitStatus,
  clearRateLimit,
  cleanup
};
