const rateLimit = require('express-rate-limit');

// Skip rate limiting in test environment for E2E tests
const isTestEnv = process.env.NODE_ENV === 'test';

/**
 * Global rate limiter
 * 100 requests per minute for all endpoints
 * Disabled in test environment
 */
const globalLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 100, // 100 requests per window
  message: { error: 'Too many requests, please try again later' },
  standardHeaders: true, // Return rate limit info in headers
  legacyHeaders: false, // Disable X-RateLimit-* headers
  skip: (req) => {
    // Skip rate limiting in test environment or for health check
    return isTestEnv || req.path === '/api/health';
  }
});

/**
 * Strict rate limiter for authentication endpoints
 * 10 requests per minute to prevent brute force
 * Disabled in test environment
 */
const authLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 10, // 10 requests per window
  message: { error: 'Too many authentication attempts, please try again later' },
  standardHeaders: true,
  legacyHeaders: false,
  skipSuccessfulRequests: true, // Don't count successful auth attempts
  skip: () => isTestEnv
});

/**
 * Wallpaper endpoint rate limiter
 * 20 requests per hour to prevent abuse of image generation
 * Disabled in test environment
 */
const wallpaperLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  max: 1000, // 1000 requests per window (supports 60s polling w/ extra buffer)
  message: { error: 'Wallpaper generation rate limit exceeded' },
  standardHeaders: true,
  legacyHeaders: false,
  skip: () => isTestEnv
});

/**
 * Task creation rate limiter
 * Prevent spam task creation
 * 30 tasks per minute
 * Disabled in test environment
 */
const taskCreationLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 30,
  message: { error: 'Too many tasks created, slow down' },
  standardHeaders: true,
  legacyHeaders: false,
  skip: () => isTestEnv
});

module.exports = {
  globalLimiter,
  authLimiter,
  wallpaperLimiter,
  taskCreationLimiter
};
