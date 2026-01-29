/**
 * E2E Tests: Authentication Flow
 * Tests user registration, login, token refresh, and auth errors
 */

const request = require('supertest');
const path = require('path');

// Load environment variables
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

// Import server app (we'll need to export it from server.js)
const app = require('../server');
const { cleanupTestUser } = require('./helpers');

describe('Authentication Flow', () => {
  let testEmail;
  let testPassword;
  let accessToken;
  let refreshToken;

  beforeEach(() => {
    testEmail = global.testUtils.randomEmail();
    testPassword = 'TestPassword123!';
  });

  afterEach(async () => {
    // Cleanup: delete test user if created
    if (accessToken) {
      await cleanupTestUser(app, accessToken);
      accessToken = null;
      refreshToken = null;
    }
  });

  describe('POST /api/auth/register', () => {
    it('should register a new user successfully', async () => {
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: testPassword,
          theme: 'cosmic',
          timezone: 'UTC',
          resolution: '1080x1920'
        })
        .expect(201);

      expect(response.body).toHaveProperty('accessToken');
      expect(response.body).toHaveProperty('refreshToken');
      expect(response.body).toHaveProperty('wallpaperToken');
      expect(response.body).toHaveProperty('user');
      expect(response.body.user.email).toBe(testEmail);
      expect(response.body.user.theme).toBe('cosmic');
      expect(response.body.user).not.toHaveProperty('password_hash');

      // Save tokens for cleanup
      accessToken = response.body.accessToken;
      refreshToken = response.body.refreshToken;
    });

    it('should reject registration with invalid email', async () => {
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: 'invalid-email',
          password: testPassword,
          theme: 'cosmic'
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });

    it('should reject registration with short password', async () => {
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: 'short',
          theme: 'cosmic'
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });

    it('should reject registration with duplicate email', async () => {
      // First registration
      const firstResponse = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: testPassword,
          theme: 'cosmic'
        })
        .expect(201);

      accessToken = firstResponse.body.accessToken;

      // Second registration with same email
      const secondResponse = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: 'DifferentPassword123!',
          theme: 'ocean'
        })
        .expect(409);

      expect(secondResponse.body.error).toContain('Email already registered');
    });

    it('should apply default theme and resolution if not provided', async () => {
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: testPassword
        })
        .expect(201);

      expect(response.body.user.theme).toBe('cosmic'); // default
      expect(response.body.user.resolution).toBe('1170x2532'); // default (iPhone 15 Pro)

      accessToken = response.body.accessToken;
    });
  });

  describe('POST /api/auth/login', () => {
    beforeEach(async () => {
      // Create a user for login tests
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: testPassword,
          theme: 'cosmic'
        })
        .expect(201);

      accessToken = response.body.accessToken;
    });

    it('should login successfully with correct credentials', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: testEmail,
          password: testPassword
        })
        .expect(200);

      expect(response.body).toHaveProperty('accessToken');
      expect(response.body).toHaveProperty('refreshToken');
      expect(response.body).toHaveProperty('user');
      expect(response.body.user.email).toBe(testEmail);
    });

    it('should reject login with incorrect password', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: testEmail,
          password: 'WrongPassword123!'
        })
        .expect(401);

      expect(response.body.error).toContain('Invalid email or password');
    });

    it('should reject login with non-existent email', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: 'nonexistent@example.com',
          password: testPassword
        })
        .expect(401);

      expect(response.body.error).toContain('Invalid email or password');
    });

    it('should reject login with missing credentials', async () => {
      const response = await request(app)
        .post('/api/auth/login')
        .send({
          email: testEmail
          // Missing password
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });
  });

  describe('POST /api/auth/refresh', () => {
    beforeEach(async () => {
      // Create a user and get tokens
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: testPassword,
          theme: 'cosmic'
        })
        .expect(201);

      accessToken = response.body.accessToken;
      refreshToken = response.body.refreshToken;
    });

    it('should refresh access token with valid refresh token', async () => {
      const response = await request(app)
        .post('/api/auth/refresh')
        .send({
          refreshToken: refreshToken
        })
        .expect(200);

      expect(response.body).toHaveProperty('accessToken');
      expect(typeof response.body.accessToken).toBe('string');
      expect(response.body.accessToken.split('.').length).toBe(3); // Valid JWT format
      // Note: refresh endpoint returns only new accessToken (not refreshToken) for security
      // Note: Token may be same as original if generated in same second (JWT iat is in seconds)
    });

    it('should reject refresh with invalid token', async () => {
      const response = await request(app)
        .post('/api/auth/refresh')
        .send({
          refreshToken: 'invalid-token'
        })
        .expect(401);

      expect(response.body.error).toContain('Invalid');
    });

    it('should reject refresh with missing token', async () => {
      const response = await request(app)
        .post('/api/auth/refresh')
        .send({})
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });
  });

  describe('Protected Endpoints', () => {
    it('should reject requests without authorization header', async () => {
      const response = await request(app)
        .get('/api/user')
        .expect(401);

      expect(response.body.error).toContain('authorization');
    });

    it('should reject requests with invalid token', async () => {
      const response = await request(app)
        .get('/api/user')
        .set('Authorization', 'Bearer invalid-token')
        .expect(401);

      expect(response.body.error).toContain('Invalid');
    });

    it('should allow requests with valid access token', async () => {
      // Create user
      const registerResponse = await request(app)
        .post('/api/auth/register')
        .send({
          email: testEmail,
          password: testPassword,
          theme: 'cosmic'
        })
        .expect(201);

      accessToken = registerResponse.body.accessToken;

      // Access protected endpoint
      const response = await request(app)
        .get('/api/user')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(response.body.email).toBe(testEmail);
    });
  });

  describe('POST /api/auth/forgot-password', () => {
    it('should return success message for forgot password request', async () => {
      const response = await request(app)
        .post('/api/auth/forgot-password')
        .send({ email: testEmail })
        .expect(200);

      expect(response.body).toHaveProperty('message');
      expect(response.body.message).toContain('sent to your email');
    });

    it('should reject forgot password with invalid email', async () => {
      const response = await request(app)
        .post('/api/auth/forgot-password')
        .send({ email: 'not-an-email' })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });
  });
});
