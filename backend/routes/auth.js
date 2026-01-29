const express = require('express');
const bcrypt = require('bcrypt');
const { body, validationResult } = require('express-validator');
const {
  generateAccessToken,
  generateRefreshToken,
  generateWallpaperToken,
  verifyRefreshToken
} = require('../middleware/auth');

const router = express.Router();

/**
 * POST /api/auth/register
 * Register new user account
 */
router.post(
  '/register',
  [
    body('email').isEmail().normalizeEmail(),
    body('password').isLength({ min: 8 }).withMessage('Password must be at least 8 characters'),
    body('theme').optional().isIn(['cosmic', 'ocean', 'fantasy']),
    body('timezone').optional().isString()
  ],
  async (req, res) => {
    try {
      // Validate input
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
      }

      const { email, password, theme, timezone, resolution } = req.body;
      const client = req.app.locals.dbClient;

      // Check if user already exists
      const existingUser = await client.query(
        'SELECT id FROM users WHERE email = $1',
        [email]
      );

      if (existingUser.rows.length > 0) {
        return res.status(409).json({ error: 'Email already registered' });
      }

      // Hash password
      const saltRounds = 10;
      const passwordHash = await bcrypt.hash(password, saltRounds);

      // Generate wallpaper token for widget access
      const crypto = require('crypto');
      const userId = crypto.randomUUID();
      const wallpaperToken = generateWallpaperToken(userId);

      // Create user
      const query = `
        INSERT INTO users (
          id, email, password_hash, theme, timezone, resolution, wallpaper_token
        ) VALUES ($1, $2, $3, $4, $5, $6, $7)
        RETURNING id, email, theme, timezone, resolution, wallpaper_token, created_at
      `;

      const values = [
        userId,
        email,
        passwordHash,
        theme || 'cosmic',
        timezone || 'UTC',
        resolution || '1170x2532',
        wallpaperToken
      ];

      const result = await client.query(query, values);
      const user = result.rows[0];

      // Generate tokens
      const accessToken = generateAccessToken(user.id, user.email);
      const refreshToken = generateRefreshToken(user.id, user.email);

      res.status(201).json({
        accessToken,
        refreshToken,
        wallpaperToken: user.wallpaper_token,
        user: {
          id: user.id,
          email: user.email,
          theme: user.theme,
          timezone: user.timezone,
          resolution: user.resolution
        }
      });
    } catch (err) {
      console.error('Registration error:', err);
      res.status(500).json({ error: 'Failed to register user' });
    }
  }
);

/**
 * POST /api/auth/login
 * Authenticate user and return tokens
 */
router.post(
  '/login',
  [
    body('email').isEmail().normalizeEmail(),
    body('password').exists()
  ],
  async (req, res) => {
    try {
      // Validate input
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
      }

      const { email, password } = req.body;
      const client = req.app.locals.dbClient;

      // Find user
      const result = await client.query(
        'SELECT * FROM users WHERE email = $1',
        [email]
      );

      if (result.rows.length === 0) {
        return res.status(401).json({ error: 'Invalid email or password' });
      }

      const user = result.rows[0];

      // Verify password
      const isValidPassword = await bcrypt.compare(password, user.password_hash);

      if (!isValidPassword) {
        return res.status(401).json({ error: 'Invalid email or password' });
      }

      // Generate wallpaper token if doesn't exist
      let wallpaperToken = user.wallpaper_token;
      if (!wallpaperToken) {
        wallpaperToken = generateWallpaperToken(user.id);
        await client.query(
          'UPDATE users SET wallpaper_token = $1 WHERE id = $2',
          [wallpaperToken, user.id]
        );
      }

      // Generate tokens
      const accessToken = generateAccessToken(user.id, user.email);
      const refreshToken = generateRefreshToken(user.id, user.email);

      res.json({
        accessToken,
        refreshToken,
        wallpaperToken,
        user: {
          id: user.id,
          email: user.email,
          theme: user.theme,
          timezone: user.timezone,
          resolution: user.resolution,
          done_for_today: user.done_for_today
        }
      });
    } catch (err) {
      console.error('Login error:', err);
      res.status(500).json({ error: 'Failed to authenticate' });
    }
  }
);

/**
 * POST /api/auth/refresh
 * Refresh access token using refresh token
 */
router.post(
  '/refresh',
  [body('refreshToken').exists()],
  async (req, res) => {
    try {
      // Validate input
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
      }

      const { refreshToken } = req.body;
      const client = req.app.locals.dbClient;

      // Verify refresh token
      let decoded;
      try {
        decoded = verifyRefreshToken(refreshToken);
      } catch (err) {
        return res.status(401).json({ error: 'Invalid or expired refresh token' });
      }

      // Verify user still exists
      const result = await client.query(
        'SELECT id, email FROM users WHERE id = $1',
        [decoded.userId]
      );

      if (result.rows.length === 0) {
        return res.status(401).json({ error: 'User not found' });
      }

      const user = result.rows[0];

      // Generate new access token
      const accessToken = generateAccessToken(user.id, user.email);

      res.json({ accessToken });
    } catch (err) {
      console.error('Token refresh error:', err);
      res.status(500).json({ error: 'Failed to refresh token' });
    }
  }
);

/**
 * POST /api/auth/wallpaper-token
 * Regenerate wallpaper token (requires valid access token)
 */
router.post('/wallpaper-token', async (req, res) => {
  try {
    // This endpoint requires authentication via middleware
    // Will be added when integrated into server.js
    const userId = req.user?.userId;

    if (!userId) {
      return res.status(401).json({ error: 'Authentication required' });
    }

    const client = req.app.locals.dbClient;

    // Generate new wallpaper token
    const wallpaperToken = generateWallpaperToken(userId);

    // Update in database
    await client.query(
      'UPDATE users SET wallpaper_token = $1, updated_at = NOW() WHERE id = $2',
      [wallpaperToken, userId]
    );

    res.json({ wallpaperToken });
  } catch (err) {
    console.error('Wallpaper token generation error:', err);
    res.status(500).json({ error: 'Failed to generate wallpaper token' });
  }
});

// POST /api/auth/forgot-password
// Stub for future implementation
router.post('/forgot-password', [
  body('email').isEmail().normalizeEmail()
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({ errors: errors.array() });
  }

  // In production, this would trigger an email
  res.json({ message: 'Password reset instructions have been sent to your email.' });
});

module.exports = router;
