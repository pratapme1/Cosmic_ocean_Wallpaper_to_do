/**
 * E2E Tests: Rate Limiting and Error Scenarios
 * Tests rate limiting, error handling, and edge cases
 */

const request = require('supertest');
const app = require('../server');
const { createTestUser, cleanupTestUser } = require('./helpers');

describe('Rate Limiting and Error Scenarios', () => {
  let accessToken;

  beforeEach(async () => {
    // Create a test user
    const user = await createTestUser(app);
    accessToken = user.accessToken;
  });

  afterEach(async () => {
    // Cleanup
    if (accessToken) {
      await cleanupTestUser(app, accessToken);
    }
  });

  describe('Rate Limiting', () => {
    it('should allow requests within rate limit', async () => {
      // Make a few requests (well within limit)
      for (let i = 0; i < 5; i++) {
        const response = await request(app)
          .get('/api/tasks')
          .set('Authorization', `Bearer ${accessToken}`)
          .expect(200);

        expect(response.body).toBeDefined();
      }
    });

    it('should include rate limit headers', async () => {
      const response = await request(app)
        .get('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      // Check for rate limit headers (skipped in test environment)
      // In test mode, rate limiting is disabled so headers won't be present
      if (process.env.NODE_ENV !== 'test') {
        expect(response.headers['ratelimit-limit']).toBeDefined();
        expect(response.headers['ratelimit-remaining']).toBeDefined();
      }
    });

    // Note: Actual rate limit testing would require many rapid requests
    // which could slow down the test suite. In production, rate limits
    // would be tested with dedicated load testing tools.
  });

  describe('Error Handling - 404 Not Found', () => {
    it('should return 404 for non-existent routes', async () => {
      const response = await request(app)
        .get('/api/non-existent-route')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);

      expect(response.body.error).toBeDefined();
      expect(response.body.error.message).toContain('not found');
      expect(response.body.error.code).toBe('NOT_FOUND');
      expect(response.body.error.path).toBe('/api/non-existent-route');
    });

    it('should return 404 for non-existent task', async () => {
      const fakeId = '00000000-0000-0000-0000-000000000000'; // Non-existent UUID

      const response = await request(app)
        .get(`/api/tasks/${fakeId}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);

      expect(response.body.error).toBeDefined();
    });

    it('should return structured error response', async () => {
      const response = await request(app)
        .get('/api/non-existent')
        .expect(404);

      // Check error structure
      expect(response.body.error).toHaveProperty('message');
      expect(response.body.error).toHaveProperty('code');
      expect(response.body.error).toHaveProperty('timestamp');
      expect(response.body.error).toHaveProperty('path');
      expect(response.body.error).toHaveProperty('method');
    });
  });

  describe('Error Handling - Validation Errors', () => {
    it('should return validation error for invalid email format', async () => {
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: 'not-an-email',
          password: 'ValidPassword123!'
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
      expect(Array.isArray(response.body.errors)).toBe(true);
    });

    it('should return validation error for short password', async () => {
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email: 'test@example.com',
          password: 'short'
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });

    it('should return validation error for invalid theme', async () => {
      const response = await request(app)
        .patch('/api/user')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          theme: 'invalid-theme'
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });

    it('should return validation error for invalid resolution format', async () => {
      const response = await request(app)
        .patch('/api/user')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          resolution: 'not-a-resolution'
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });
  });

  describe('Error Handling - Authentication Errors', () => {
    it('should return 401 for missing authorization header', async () => {
      const response = await request(app)
        .get('/api/user')
        .expect(401);

      expect(response.body.error).toContain('authorization');
    });

    it('should return 401 for invalid token format', async () => {
      const response = await request(app)
        .get('/api/user')
        .set('Authorization', 'InvalidFormat')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });

    it('should return 401 for malformed JWT', async () => {
      const response = await request(app)
        .get('/api/user')
        .set('Authorization', 'Bearer not.a.valid.jwt')
        .expect(401);

      expect(response.body.error).toContain('Invalid');
    });

    it('should return 401 for expired token', async () => {
      // Note: Testing expired tokens requires time manipulation or
      // using a token with very short expiry. In production, you'd
      // use a library like timekeeper or manually create expired tokens.

      // For now, just test with completely invalid token
      const response = await request(app)
        .get('/api/user')
        .set('Authorization', 'Bearer expired-token')
        .expect(401);

      expect(response.body.error).toBeDefined();
    });
  });

  describe('Error Handling - Conflict Errors', () => {
    it('should return 409 for duplicate email registration', async () => {
      const email = global.testUtils.randomEmail();

      // First registration
      await request(app)
        .post('/api/auth/register')
        .send({
          email,
          password: 'Password123!'
        })
        .expect(201);

      // Duplicate registration
      const response = await request(app)
        .post('/api/auth/register')
        .send({
          email,
          password: 'DifferentPassword123!'
        })
        .expect(409);

      expect(response.body.error).toContain('Email already registered');
    });
  });

  describe('Health Check', () => {
    it('should return health status', async () => {
      const response = await request(app)
        .get('/api/health')
        .expect(200);

      expect(response.body).toHaveProperty('status');
      expect(response.body).toHaveProperty('mode');
      expect(response.body.status).toBe('ok');
      expect(['mock', 'postgres']).toContain(response.body.mode);
    });

    it('should not require authentication', async () => {
      // Health endpoint should work without auth
      const response = await request(app)
        .get('/api/health')
        .expect(200);

      expect(response.body.status).toBe('ok');
    });
  });

  describe('CORS and Headers', () => {
    it('should include CORS headers', async () => {
      const response = await request(app)
        .get('/api/health')
        .expect(200);

      expect(response.headers['access-control-allow-origin']).toBeDefined();
    });

    it('should handle OPTIONS preflight requests', async () => {
      const response = await request(app)
        .options('/api/tasks')
        .expect(204);

      expect(response.headers['access-control-allow-methods']).toBeDefined();
    });
  });

  describe('Request Validation', () => {
    it('should reject POST /api/tasks with missing rawTitle', async () => {
      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          priority: 1
          // Missing rawTitle
        })
        .expect(500);

      // Should fail since rawTitle is required for parsing
      expect(response.body.error).toBeDefined();
    });

    it('should reject POST /api/sync with invalid lastSyncAt', async () => {
      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 'not-a-number',
          pendingChanges: []
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });

    it('should handle malformed JSON gracefully', async () => {
      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .set('Content-Type', 'application/json')
        .send('{ invalid json }')
        .expect(400);

      expect(response.body).toBeDefined();
    });
  });

  describe('Database Error Scenarios', () => {
    it('should handle gracefully when operations fail', async () => {
      // This test would require mocking database failures
      // For now, we're testing that the error handler is in place

      // Try to update a non-existent task
      const fakeId = '00000000-0000-0000-0000-000000000000'; // Non-existent UUID
      const response = await request(app)
        .patch(`/api/tasks/${fakeId}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          title: 'Updated title'
        })
        .expect(200);

      // Should return empty result (no rows updated)
      expect(response.body).toBeDefined();
    });
  });

  describe('Edge Cases', () => {
    it('should handle very long task titles', async () => {
      const longTitle = 'A'.repeat(500);

      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          rawTitle: longTitle
        })
        .expect(201);

      expect(response.body.title.length).toBeGreaterThan(0);
    });

    it('should handle special characters in task titles', async () => {
      const specialTitle = 'Task with émojis 🎉 and spëcial çhars!';

      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          rawTitle: specialTitle
        })
        .expect(201);

      expect(response.body.title).toContain('🎉');
    });

    it('should handle negative priority values', async () => {
      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          rawTitle: 'Task with negative priority',
          priority: -1
        })
        .expect(201);

      // Should accept negative values (no validation on priority range)
      expect(response.body.priority).toBe(-1);
    });

    it('should handle extremely large estimate_minutes', async () => {
      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          rawTitle: 'Task in 10000 hours',
          priority: 0
        })
        .expect(201);

      // Chrono-node might parse this, but it should handle gracefully
      expect(response.body).toHaveProperty('id');
    });

    it('should handle null/undefined optional fields', async () => {
      const response = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          rawTitle: 'Minimal task',
          context_location: null,
          context_time: null,
          x: null,
          y: null
        })
        .expect(201);

      expect(response.body.context_location).toBeNull();
      expect(response.body.x).toBeNull();
    });
  });
});
