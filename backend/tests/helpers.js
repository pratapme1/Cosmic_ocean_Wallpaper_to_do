/**
 * Test Helper Functions
 * Common utilities for E2E tests
 */

const request = require('supertest');

/**
 * Create a test user and return auth tokens
 */
async function createTestUser(app, userData = {}) {
  const email = userData.email || global.testUtils.randomEmail();
  const password = userData.password || 'TestPassword123!';

  const response = await request(app)
    .post('/api/auth/register')
    .send({
      email,
      password,
      theme: userData.theme || 'cosmic',
      timezone: userData.timezone || 'UTC',
      resolution: userData.resolution || '1080x1920'
    })
    .expect(201);

  return {
    user: response.body.user,
    accessToken: response.body.accessToken,
    refreshToken: response.body.refreshToken,
    wallpaperToken: response.body.wallpaperToken,
    email,
    password
  };
}

/**
 * Login with credentials and return tokens
 */
async function loginUser(app, email, password) {
  const response = await request(app)
    .post('/api/auth/login')
    .send({ email, password })
    .expect(200);

  return {
    user: response.body.user,
    accessToken: response.body.accessToken,
    refreshToken: response.body.refreshToken
  };
}

/**
 * Create a test task
 */
async function createTestTask(app, accessToken, taskData = {}) {
  const response = await request(app)
    .post('/api/tasks')
    .set('Authorization', `Bearer ${accessToken}`)
    .send({
      rawTitle: taskData.rawTitle || taskData.title || 'Test Task',
      priority: taskData.priority || 0,
      context_location: taskData.context_location || null,
      context_time: taskData.context_time || null,
      x: taskData.x || null,
      y: taskData.y || null,
      is_subtask: taskData.is_subtask || false,
      is_recurring: taskData.is_recurring || false,
      echo_interval: taskData.echo_interval || null
    })
    .expect(201);

  return response.body;
}

/**
 * Get all tasks
 */
async function getTasks(app, accessToken, query = {}) {
  const response = await request(app)
    .get('/api/tasks')
    .set('Authorization', `Bearer ${accessToken}`)
    .query(query)
    .expect(200);

  return response.body;
}

/**
 * Update a task
 */
async function updateTask(app, accessToken, taskId, updates) {
  const response = await request(app)
    .patch(`/api/tasks/${taskId}`)
    .set('Authorization', `Bearer ${accessToken}`)
    .send(updates)
    .expect(200);

  return response.body;
}

/**
 * Delete a task
 */
async function deleteTask(app, accessToken, taskId) {
  const response = await request(app)
    .delete(`/api/tasks/${taskId}`)
    .set('Authorization', `Bearer ${accessToken}`)
    .expect(200);

  return response.body;
}

/**
 * Clean up test data (delete all tasks and user)
 */
async function cleanupTestUser(app, accessToken) {
  try {
    // Delete all tasks
    await request(app)
      .delete('/api/tasks')
      .set('Authorization', `Bearer ${accessToken}`);

    // Delete user account
    await request(app)
      .delete('/api/user')
      .set('Authorization', `Bearer ${accessToken}`);
  } catch (err) {
    console.error('Cleanup error:', err.message);
  }
}

module.exports = {
  createTestUser,
  loginUser,
  createTestTask,
  getTasks,
  updateTask,
  deleteTask,
  cleanupTestUser
};
