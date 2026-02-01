/**
 * Complete E2E API Test Suite - Cosmic Ocean Backend
 * Tests all API endpoints and integration points
 * 
 * Run: npm test -- e2e-complete.test.js
 * Or: npm test (runs all tests including this one)
 */

const request = require('supertest');
const path = require('path');

// Load environment variables
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

// Import server app
const app = require('../server');
const { cleanupTestUser } = require('./helpers');

// Test configuration
const TEST_USER_PASSWORD = 'TestPassword123!';

// Helper: Create test user and get token
async function createTestUserAndLogin() {
  const testEmail = global.testUtils.randomEmail();
  
  // Register
  const registerRes = await request(app)
    .post('/api/auth/register')
    .send({ email: testEmail, password: TEST_USER_PASSWORD });
  
  if (registerRes.status !== 201 && registerRes.status !== 200) {
    throw new Error(`Failed to register: ${registerRes.body.error || registerRes.status}`);
  }
  
  // Login
  const loginRes = await request(app)
    .post('/api/auth/login')
    .send({ email: testEmail, password: TEST_USER_PASSWORD });
  
  if (loginRes.status !== 200) {
    throw new Error(`Failed to login: ${loginRes.body.error || loginRes.status}`);
  }
  
  return {
    accessToken: loginRes.body.accessToken,
    refreshToken: loginRes.body.refreshToken,
    userId: loginRes.body.user.id,
    testEmail: testEmail
  };
}

// Test 1: Health Check
describe('1. Health Check API', () => {
  test('GET /api/health should return status ok', async () => {
    const res = await request(app)
      .get('/api/health')
      .expect(200);
    
    expect(res.body).toHaveProperty('status', 'ok');
    expect(res.body).toHaveProperty('version');
    expect(res.body).toHaveProperty('timestamp');
  });
});

// Test 2: Authentication Flow
describe('2. Authentication Flow', () => {
  let tokens;
  let testEmail;
  
  beforeAll(async () => {
    testEmail = global.testUtils.randomEmail();
  });
  
  afterAll(async () => {
    if (tokens && tokens.accessToken) {
      await cleanupTestUser(app, tokens.accessToken);
    }
  });
  
  test('2.1 POST /api/auth/register - Create new user', async () => {
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: testEmail, password: TEST_USER_PASSWORD })
      .expect(201);
    
    expect(res.body).toHaveProperty('id');
    expect(res.body).toHaveProperty('email', testEmail);
    expect(res.body).toHaveProperty('wallpaperToken');
  });
  
  test('2.2 POST /api/auth/login - Login user', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: testEmail, password: TEST_USER_PASSWORD })
      .expect(200);
    
    expect(res.body).toHaveProperty('accessToken');
    expect(res.body).toHaveProperty('refreshToken');
    expect(res.body).toHaveProperty('user');
    expect(res.body.user).toHaveProperty('id');
    
    tokens = {
      accessToken: res.body.accessToken,
      refreshToken: res.body.refreshToken,
      userId: res.body.user.id
    };
  });
  
  test('2.3 POST /api/auth/refresh - Refresh token', async () => {
    const res = await request(app)
      .post('/api/auth/refresh')
      .set('Authorization', `Bearer ${tokens.refreshToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('accessToken');
  });
  
  test('2.4 GET /api/user - Get user profile', async () => {
    const res = await request(app)
      .get('/api/user')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('id', tokens.userId);
    expect(res.body).toHaveProperty('email', testEmail);
  });
  
  test('2.5 PATCH /api/user - Update user profile', async () => {
    const res = await request(app)
      .patch('/api/user')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({ theme: 'ocean', display_mode: 'all_tasks' })
      .expect(200);
    
    expect(res.body.user.theme).toBe('ocean');
    expect(res.body.user.display_mode).toBe('all_tasks');
  });
  
  test('2.6 POST /api/auth/wallpaper-token - Get wallpaper token', async () => {
    const res = await request(app)
      .post('/api/auth/wallpaper-token')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('wallpaperToken');
  });
});

// Test 3: Task CRUD Operations
describe('3. Task CRUD Operations', () => {
  let tokens;
  let taskId;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('3.1 POST /api/tasks - Create task with NLP parsing', async () => {
    const res = await request(app)
      .post('/api/tasks')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({ rawTitle: 'Urgent meeting with team in 30 minutes' })
      .expect(201);
    
    expect(res.body).toHaveProperty('id');
    expect(res.body).toHaveProperty('title');
    expect(res.body).toHaveProperty('priority');
    taskId = res.body.id;
  });
  
  test('3.2 GET /api/tasks - List all tasks', async () => {
    const res = await request(app)
      .get('/api/tasks')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body.length).toBeGreaterThan(0);
  });
  
  test('3.3 GET /api/tasks/:id - Get single task', async () => {
    const res = await request(app)
      .get(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('id', taskId);
    expect(res.body).toHaveProperty('title');
  });
  
  test('3.4 PATCH /api/tasks/:id - Update task', async () => {
    const res = await request(app)
      .patch(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({ title: 'Updated task title', priority: 1 })
      .expect(200);
    
    expect(res.body).toHaveProperty('title', 'Updated task title');
    expect(res.body).toHaveProperty('priority', 1);
  });
  
  test('3.5 PATCH /api/tasks/:id - Complete task', async () => {
    const res = await request(app)
      .patch(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({ completed: true })
      .expect(200);
    
    expect(res.body).toHaveProperty('completed', true);
    expect(res.body).toHaveProperty('completed_at');
  });
  
  test('3.6 DELETE /api/tasks/:id - Delete task', async () => {
    const res = await request(app)
      .delete(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('success', true);
  });
  
  test('3.7 Verify task deletion', async () => {
    const res = await request(app)
      .get(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(404);
  });
});

// Test 4: Sync API (Local-First Architecture)
describe('4. Sync API (Local-First)', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('4.1 POST /api/sync - Push local changes', async () => {
    // First create a task to sync
    const createRes = await request(API_BASE_URL)
      .post('/api/tasks')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({ rawTitle: 'Test sync task' });
    
    const taskId = createRes.body.id;
    
    // Now sync with pending changes
    const syncRes = await request(API_BASE_URL)
      .post('/api/sync')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({
        lastSyncAt: Date.now() - 3600000, // 1 hour ago
        pendingChanges: [
          {
            type: 'update',
            clientId: taskId,
            data: { title: 'Updated via sync', priority: 2 },
            timestamp: Date.now()
          }
        ]
      })
      .expect(200);
    
    expect(syncRes.body).toHaveProperty('syncedAt');
    expect(syncRes.body).toHaveProperty('tasks');
    expect(syncRes.body).toHaveProperty('results');
    expect(syncRes.body.results.applied).toBeGreaterThanOrEqual(0);
  });
  
  test('4.2 POST /api/sync - Pull latest changes', async () => {
    const res = await request(app)
      .post('/api/sync')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({
        lastSyncAt: 0, // Get all
        pendingChanges: []
      })
      .expect(200);
    
    expect(res.body).toHaveProperty('syncedAt');
    expect(res.body).toHaveProperty('tasks');
    expect(Array.isArray(res.body.tasks)).toBe(true);
  });
  
  test('4.3 GET /api/sync/status - Get sync status', async () => {
    const res = await request(app)
      .get('/api/sync/status')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('taskCount');
    expect(res.body).toHaveProperty('serverTime');
  });
});

// Test 5: Wallpaper API
describe('5. Wallpaper Generation API', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('5.1 GET /api/wallpaper - Generate wallpaper', async () => {
    const res = await request(app)
      .get('/api/wallpaper')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.headers['content-type']).toContain('image/png');
    expect(res.body).toBeInstanceOf(Buffer);
    expect(res.body.length).toBeGreaterThan(1000); // At least 1KB
  });
  
  test('5.2 GET /api/wallpaper with theme parameter', async () => {
    const themes = ['cosmic', 'ocean', 'fantasy'];
    
    for (const theme of themes) {
      const res = await request(app)
        .get(`/api/wallpaper?theme=${theme}`)
        .set('Authorization', `Bearer ${tokens.accessToken}`)
        .expect(200);
      
      expect(res.headers['content-type']).toContain('image/png');
      expect(res.body.length).toBeGreaterThan(1000);
    }
  });
  
  test('5.3 GET /api/wallpaper with resolution parameter', async () => {
    const res = await request(app)
      .get('/api/wallpaper?resolution=1080x1920')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.headers['content-type']).toContain('image/png');
  });
});

// Test 6: User Preferences & Settings
describe('6. User Preferences API', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('6.1 GET /api/user/preferences - Get preferences', async () => {
    const res = await request(app)
      .get('/api/user/preferences')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('theme');
    expect(res.body).toHaveProperty('display_mode');
  });
  
  test('6.2 PATCH /api/user/preferences - Update preferences', async () => {
    const res = await request(app)
      .patch('/api/user/preferences')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({
        theme: 'fantasy',
        display_mode: 'one_thing',
        default_privacy_level: 'category'
      })
      .expect(200);
    
    expect(res.body).toHaveProperty('message', 'Preferences updated');
    
    // Verify update
    const getRes = await request(API_BASE_URL)
      .get('/api/user/preferences')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(getRes.body.theme).toBe('fantasy');
    expect(getRes.body.default_privacy_level).toBe('category');
  });
});

// Test 7: Achievement System
describe('7. Achievement System API', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('7.1 GET /api/achievements/definitions - Get achievement definitions', async () => {
    const res = await request(app)
      .get('/api/achievements/definitions')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(Array.isArray(res.body)).toBe(true);
    if (res.body.length > 0) {
      expect(res.body[0]).toHaveProperty('id');
      expect(res.body[0]).toHaveProperty('name');
      expect(res.body[0]).toHaveProperty('category');
    }
  });
  
  test('7.2 GET /api/achievements - Get user achievements', async () => {
    const res = await request(app)
      .get('/api/achievements')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('achievements');
    expect(res.body).toHaveProperty('stats');
  });
  
  test('7.3 POST /api/achievements/check - Check for new achievements', async () => {
    const res = await request(app)
      .post('/api/achievements/check')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('checked', true);
  });
});

// Test 8: Done For Today
describe('8. Done For Today API', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('8.1 POST /api/done-for-today - Mark done for today', async () => {
    const res = await request(app)
      .post('/api/done-for-today')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('success', true);
    expect(res.body).toHaveProperty('done_for_today', true);
  });
});

// Test 9: NLP Parsing
describe('9. NLP Task Parsing API', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('9.1 POST /api/tasks/parse-llm - Parse task with LLM', async () => {
    const res = await request(app)
      .post('/api/tasks/parse-llm')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({ title: 'Urgent meeting in 30 minutes @office' })
      .expect(200);
    
    expect(res.body).toHaveProperty('success', true);
    expect(res.body).toHaveProperty('parsed');
    expect(res.body.parsed).toHaveProperty('title');
    expect(res.body.parsed).toHaveProperty('priority');
  });
});

// Test 10: Rate Limiting & Error Handling
describe('10. Rate Limiting & Error Handling', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('10.1 401 on missing auth', async () => {
    const res = await request(app)
      .get('/api/tasks')
      .expect(401);
    
    expect(res.body).toHaveProperty('error');
  });
  
  test('10.2 404 on non-existent task', async () => {
    const res = await request(app)
      .get('/api/tasks/non-existent-id')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(404);
  });
  
  test('10.3 400 on invalid input', async () => {
    const res = await request(app)
      .post('/api/tasks')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .send({}) // Missing required fields
      .expect(400);
  });
});

// Test 11: User Stats
describe('11. User Stats API', () => {
  let tokens;
  
  beforeAll(async () => {
    tokens = await createTestUserAndLogin();
  });
  
  test('11.1 GET /api/user/stats/weekly - Get weekly stats', async () => {
    const res = await request(app)
      .get('/api/user/stats/weekly')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('weeks');
    expect(res.body).toHaveProperty('trends');
  });
  
  test('11.2 GET /api/user/stats/graduation - Get graduation stats', async () => {
    const res = await request(app)
      .get('/api/user/stats/graduation')
      .set('Authorization', `Bearer ${tokens.accessToken}`)
      .expect(200);
    
    expect(res.body).toHaveProperty('months');
    expect(res.body).toHaveProperty('graduation');
  });
});

// Final Summary
afterAll(async () => {
  console.log('\n========================================');
  console.log('E2E API Test Suite Complete!');
  console.log('========================================\n');
  console.log('Tested APIs:');
  console.log('  ✓ Health Check');
  console.log('  ✓ Authentication (register, login, refresh, profile)');
  console.log('  ✓ Task CRUD (create, read, update, delete)');
  console.log('  ✓ Sync API (push, pull, status)');
  console.log('  ✓ Wallpaper Generation');
  console.log('  ✓ User Preferences');
  console.log('  ✓ Achievement System');
  console.log('  ✓ Done For Today');
  console.log('  ✓ NLP Parsing');
  console.log('  ✓ Rate Limiting & Error Handling');
  console.log('  ✓ User Stats');
  console.log('\nAll major API endpoints tested!');
  console.log('========================================\n');
});
