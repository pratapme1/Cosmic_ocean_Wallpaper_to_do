const jwt = require('jsonwebtoken');

// JWT Secret - in production this should be in environment variables
const JWT_SECRET = process.env.JWT_SECRET || 'cosmic-ocean-secret-change-in-production';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'cosmic-ocean-refresh-secret-change-in-production';

/**
 * Verify JWT token from Authorization header
 * Adds user object to req.user
 */
const verifyToken = (req, res, next) => {
  try {
    const authHeader = req.headers['authorization'];

    if (!authHeader) {
      return res.status(401).json({ error: 'No authorization token provided' });
    }

    // Support both "Bearer <token>" and just "<token>" formats
    const token = authHeader.startsWith('Bearer ')
      ? authHeader.slice(7)
      : authHeader;

    // Try to decode as JWT first
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      req.user = decoded; // { userId, email, iat, exp }
      return next();
    } catch (jwtError) {
      // If JWT verification fails, check if it's a UUID (legacy/wallpaper token)
      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      if (uuidRegex.test(token)) {
        // Legacy mode: treat UUID as userId directly (backward compatibility)
        req.user = { userId: token, legacy: true };
        return next();
      }

      return res.status(401).json({ error: 'Invalid or expired token' });
    }
  } catch (err) {
    console.error('Auth middleware error:', err);
    return res.status(500).json({ error: 'Authentication error' });
  }
};

/**
 * Optional authentication - doesn't fail if no token
 * Used for endpoints that work both authenticated and unauthenticated
 */
const optionalAuth = (req, res, next) => {
  const authHeader = req.headers['authorization'];

  if (!authHeader) {
    // No auth header, continue with default user
    req.user = { userId: '00000000-0000-0000-0000-000000000000', guest: true };
    return next();
  }

  // Has auth header, verify it
  verifyToken(req, res, next);
};

/**
 * Verify wallpaper token (limited scope, long-lived)
 * Used by widgets and automations
 */
const verifyWallpaperToken = async (req, res, next) => {
  try {
    const token = req.query.token || req.headers['x-wallpaper-token'];

    if (!token) {
      return res.status(401).json({ error: 'Wallpaper token required' });
    }

    // Check if token exists in database and get associated user
    // This will be implemented when we add the database query
    // For now, treat it as a JWT with special scope
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      if (decoded.scope !== 'wallpaper') {
        return res.status(403).json({ error: 'Token not authorized for wallpaper access' });
      }
      req.user = decoded;
      return next();
    } catch (jwtError) {
      return res.status(401).json({ error: 'Invalid wallpaper token' });
    }
  } catch (err) {
    console.error('Wallpaper token verification error:', err);
    return res.status(500).json({ error: 'Token verification error' });
  }
};

/**
 * Generate access token (7-day expiry)
 */
const generateAccessToken = (userId, email) => {
  return jwt.sign(
    { userId, email },
    JWT_SECRET,
    { expiresIn: '7d' }
  );
};

/**
 * Generate refresh token (30-day expiry)
 */
const generateRefreshToken = (userId, email) => {
  return jwt.sign(
    { userId, email, refresh: true },
    JWT_REFRESH_SECRET,
    { expiresIn: '30d' }
  );
};

/**
 * Generate wallpaper token (1-year expiry, limited scope)
 * Used for widget-only access to wallpaper endpoint
 */
const generateWallpaperToken = (userId) => {
  return jwt.sign(
    { userId, scope: 'wallpaper' },
    JWT_SECRET,
    { expiresIn: '365d' }
  );
};

/**
 * Verify refresh token
 */
const verifyRefreshToken = (token) => {
  try {
    const decoded = jwt.verify(token, JWT_REFRESH_SECRET);
    if (!decoded.refresh) {
      throw new Error('Not a refresh token');
    }
    return decoded;
  } catch (err) {
    throw new Error('Invalid refresh token');
  }
};

/**
 * Extract user ID from request (works with both JWT and legacy UUID)
 */
const getUserId = (req) => {
  if (req.user && req.user.userId) {
    return req.user.userId;
  }

  // Fallback for endpoints not using auth middleware
  const auth = req.headers['authorization'];
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (auth && uuidRegex.test(auth)) {
    return auth;
  }

  return '00000000-0000-0000-0000-000000000000'; // default test user
};

module.exports = {
  verifyToken,
  optionalAuth,
  verifyWallpaperToken,
  generateAccessToken,
  generateRefreshToken,
  generateWallpaperToken,
  verifyRefreshToken,
  getUserId,
  JWT_SECRET,
  JWT_REFRESH_SECRET
};
