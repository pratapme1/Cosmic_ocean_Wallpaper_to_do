/**
 * E2E Test Suite - Task 3: Privacy API Endpoints
 * Epic 10 - Phase 1 - Task 3
 *
 * Tests the privacy API endpoints for user settings and task privacy management.
 * Uses only API calls (no direct database access).
 *
 * Test Coverage:
 * 1. User privacy settings (GET/PATCH /api/user)
 * 2. Task creation with privacy fields (POST /api/tasks)
 * 3. Task privacy updates (PATCH /api/tasks/:id)
 * 4. Privacy field defaults from user settings
 * 5. GET endpoints return privacy fields
 */

import crypto from 'crypto';

// Test configuration
const API_BASE = process.env.API_BASE || 'http://localhost:3001';

// Test state
let testUserEmail;
let testUserToken;
let testTaskIds = [];

// Helper: Generate unique test email
function generateTestEmail() {
  const uniqueId = crypto.randomBytes(4).toString('hex');
  return `privacy-api-test-${uniqueId}@test.com`;
}

// Helper: Make API request
async function apiRequest(method, endpoint, body = null, token = null) {
  const headers = {
    'Content-Type': 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const options = {
    method,
    headers
  };

  if (body) {
    options.body = JSON.stringify(body);
  }

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, options);
    const data = await response.json().catch(() => ({}));
    return { status: response.status, data };
  } catch (error) {
    return { status: 0, error: error.message };
  }
}

// Test runner
let passedTests = 0;
let failedTests = 0;
const testResults = [];

function test(name, condition, details = '') {
  if (condition) {
    passedTests++;
    testResults.push({ name, passed: true });
    console.log(`  [PASS] ${name}`);
  } else {
    failedTests++;
    testResults.push({ name, passed: false, details });
    console.log(`  [FAIL] ${name}`);
    if (details) console.log(`         Details: ${details}`);
  }
}

// ============= SETUP =============
async function setup() {
  console.log('\n=== SETUP ===');

  // Check server health
  const healthResponse = await apiRequest('GET', '/api/health');
  if (healthResponse.status !== 200) {
    throw new Error(`Server not healthy: ${JSON.stringify(healthResponse)}`);
  }
  console.log('Server is healthy');

  // Create test user via API
  testUserEmail = generateTestEmail();
  const registerResponse = await apiRequest('POST', '/api/auth/register', {
    email: testUserEmail,
    password: 'TestPassword123!'
  });

  if (registerResponse.status === 201) {
    testUserToken = registerResponse.data.accessToken;
    console.log(`Created test user: ${testUserEmail}`);
    console.log(`Token received: ${testUserToken ? 'yes' : 'no'}`);
  } else {
    throw new Error(`Failed to create test user: ${JSON.stringify(registerResponse)}`);
  }
}

// ============= TEST SUITES =============

async function testUserPrivacySettings() {
  console.log('\n=== Test Suite 1: User Privacy Settings ===');

  // Test 1.1: GET /api/user returns privacy fields
  const getResponse = await apiRequest('GET', '/api/user', null, testUserToken);
  test(
    '1.1 GET /api/user returns privacy fields',
    getResponse.status === 200 &&
    'default_privacy_level' in getResponse.data &&
    'auto_hide_work_tasks' in getResponse.data &&
    'hide_all_tasks_mode' in getResponse.data,
    `Status: ${getResponse.status}, Has fields: ${Object.keys(getResponse.data).join(', ')}`
  );

  // Test 1.2: Check default values
  test(
    '1.2 Default privacy level is "public"',
    getResponse.data.default_privacy_level === 'public',
    `Got: ${getResponse.data.default_privacy_level}`
  );

  test(
    '1.3 Default hide_all_tasks_mode is false',
    getResponse.data.hide_all_tasks_mode === false,
    `Got: ${getResponse.data.hide_all_tasks_mode}`
  );

  test(
    '1.4 Default auto_hide_work_tasks is false',
    getResponse.data.auto_hide_work_tasks === false,
    `Got: ${getResponse.data.auto_hide_work_tasks}`
  );

  // Test 1.5: PATCH /api/user - Update default_privacy_level
  const patchResponse1 = await apiRequest('PATCH', '/api/user', {
    default_privacy_level: 'category'
  }, testUserToken);
  test(
    '1.5 PATCH /api/user - Update default_privacy_level',
    patchResponse1.status === 200 &&
    patchResponse1.data.default_privacy_level === 'category',
    `Status: ${patchResponse1.status}, Value: ${patchResponse1.data.default_privacy_level}`
  );

  // Test 1.6: PATCH /api/user - Update hide_all_tasks_mode
  const patchResponse2 = await apiRequest('PATCH', '/api/user', {
    hide_all_tasks_mode: true
  }, testUserToken);
  test(
    '1.6 PATCH /api/user - Update hide_all_tasks_mode',
    patchResponse2.status === 200 &&
    patchResponse2.data.hide_all_tasks_mode === true,
    `Status: ${patchResponse2.status}, Value: ${patchResponse2.data.hide_all_tasks_mode}`
  );

  // Test 1.7: PATCH /api/user - Update auto_hide_work_tasks with work hours
  const patchResponse3 = await apiRequest('PATCH', '/api/user', {
    auto_hide_work_tasks: true,
    work_hours_start: '08:00',
    work_hours_end: '18:00'
  }, testUserToken);
  test(
    '1.7 PATCH /api/user - Update work hours settings',
    patchResponse3.status === 200 &&
    patchResponse3.data.auto_hide_work_tasks === true,
    `Status: ${patchResponse3.status}, Value: ${patchResponse3.data.auto_hide_work_tasks}`
  );

  // Test 1.8: PATCH /api/user - Invalid privacy level rejected
  const patchResponse4 = await apiRequest('PATCH', '/api/user', {
    default_privacy_level: 'invalid_level'
  }, testUserToken);
  test(
    '1.8 PATCH /api/user - Invalid privacy level rejected',
    patchResponse4.status === 400,
    `Status: ${patchResponse4.status}`
  );

  // Test 1.9: Verify settings persisted via GET
  const verifyResponse = await apiRequest('GET', '/api/user', null, testUserToken);
  test(
    '1.9 Settings persisted correctly',
    verifyResponse.status === 200 &&
    verifyResponse.data.default_privacy_level === 'category' &&
    verifyResponse.data.hide_all_tasks_mode === true &&
    verifyResponse.data.auto_hide_work_tasks === true,
    `default_privacy_level: ${verifyResponse.data.default_privacy_level}, hide_all_tasks_mode: ${verifyResponse.data.hide_all_tasks_mode}`
  );

  // Reset to defaults for next tests
  await apiRequest('PATCH', '/api/user', {
    default_privacy_level: 'public',
    hide_all_tasks_mode: false,
    auto_hide_work_tasks: false
  }, testUserToken);
}

async function testTaskCreationWithPrivacy() {
  console.log('\n=== Test Suite 2: Task Creation with Privacy Fields ===');

  // Test 2.1: Create task with default privacy (public)
  const task1Response = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Public task for testing'
  }, testUserToken);
  test(
    '2.1 Create task with default privacy',
    task1Response.status === 201 &&
    task1Response.data.is_private === false &&
    task1Response.data.privacy_level === 'public',
    `Status: ${task1Response.status}, is_private: ${task1Response.data.is_private}, privacy_level: ${task1Response.data.privacy_level}`
  );
  if (task1Response.data.id) testTaskIds.push(task1Response.data.id);

  // Test 2.2: Create task with explicit privacy level
  const task2Response = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Category privacy task',
    is_private: true,
    privacy_level: 'category'
  }, testUserToken);
  test(
    '2.2 Create task with explicit privacy level "category"',
    task2Response.status === 201 &&
    task2Response.data.is_private === true &&
    task2Response.data.privacy_level === 'category',
    `Status: ${task2Response.status}, is_private: ${task2Response.data.is_private}, privacy_level: ${task2Response.data.privacy_level}`
  );
  if (task2Response.data.id) testTaskIds.push(task2Response.data.id);

  // Test 2.3: Create task with initials privacy level
  const task3Response = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Initials privacy task',
    is_private: true,
    privacy_level: 'initials'
  }, testUserToken);
  test(
    '2.3 Create task with privacy level "initials"',
    task3Response.status === 201 &&
    task3Response.data.is_private === true &&
    task3Response.data.privacy_level === 'initials',
    `Status: ${task3Response.status}, is_private: ${task3Response.data.is_private}, privacy_level: ${task3Response.data.privacy_level}`
  );
  if (task3Response.data.id) testTaskIds.push(task3Response.data.id);

  // Test 2.4: Create task with hidden privacy level
  const task4Response = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Hidden task',
    is_private: true,
    privacy_level: 'hidden'
  }, testUserToken);
  test(
    '2.4 Create task with privacy level "hidden"',
    task4Response.status === 201 &&
    task4Response.data.is_private === true &&
    task4Response.data.privacy_level === 'hidden',
    `Status: ${task4Response.status}, is_private: ${task4Response.data.is_private}, privacy_level: ${task4Response.data.privacy_level}`
  );
  if (task4Response.data.id) testTaskIds.push(task4Response.data.id);

  // Test 2.5: Create task with custom privacy display
  const task5Response = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Secret meeting with boss',
    is_private: true,
    privacy_level: 'custom',
    privacy_display: 'Appointment'
  }, testUserToken);
  test(
    '2.5 Create task with custom privacy display',
    task5Response.status === 201 &&
    task5Response.data.is_private === true &&
    task5Response.data.privacy_level === 'custom' &&
    task5Response.data.privacy_display === 'Appointment',
    `Status: ${task5Response.status}, privacy_display: ${task5Response.data.privacy_display}`
  );
  if (task5Response.data.id) testTaskIds.push(task5Response.data.id);

  // Test 2.6: Create task inherits user's default privacy level
  // First, set user default to 'initials'
  await apiRequest('PATCH', '/api/user', {
    default_privacy_level: 'initials'
  }, testUserToken);

  const task6Response = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Task with inherited privacy'
  }, testUserToken);
  test(
    '2.6 Task inherits user default privacy level',
    task6Response.status === 201 &&
    task6Response.data.privacy_level === 'initials' &&
    task6Response.data.is_private === true,
    `Status: ${task6Response.status}, privacy_level: ${task6Response.data.privacy_level}, is_private: ${task6Response.data.is_private}`
  );
  if (task6Response.data.id) testTaskIds.push(task6Response.data.id);

  // Reset user default
  await apiRequest('PATCH', '/api/user', {
    default_privacy_level: 'public'
  }, testUserToken);
}

async function testTaskPrivacyUpdates() {
  console.log('\n=== Test Suite 3: Task Privacy Updates ===');

  // Create a task to update
  const createResponse = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Task to update privacy'
  }, testUserToken);
  const taskId = createResponse.data.id;
  testTaskIds.push(taskId);

  // Test 3.1: Update task to private with category level
  const patch1Response = await apiRequest('PATCH', `/api/tasks/${taskId}`, {
    is_private: true,
    privacy_level: 'category'
  }, testUserToken);
  test(
    '3.1 PATCH task - Make private with category level',
    patch1Response.status === 200 &&
    patch1Response.data.is_private === true &&
    patch1Response.data.privacy_level === 'category',
    `Status: ${patch1Response.status}, is_private: ${patch1Response.data.is_private}, privacy_level: ${patch1Response.data.privacy_level}`
  );

  // Test 3.2: Update privacy level to hidden
  const patch2Response = await apiRequest('PATCH', `/api/tasks/${taskId}`, {
    privacy_level: 'hidden'
  }, testUserToken);
  test(
    '3.2 PATCH task - Change to hidden',
    patch2Response.status === 200 &&
    patch2Response.data.privacy_level === 'hidden',
    `Status: ${patch2Response.status}, privacy_level: ${patch2Response.data.privacy_level}`
  );

  // Test 3.3: Update with custom privacy display
  const patch3Response = await apiRequest('PATCH', `/api/tasks/${taskId}`, {
    privacy_level: 'custom',
    privacy_display: 'Custom Display Text'
  }, testUserToken);
  test(
    '3.3 PATCH task - Set custom privacy display',
    patch3Response.status === 200 &&
    patch3Response.data.privacy_level === 'custom' &&
    patch3Response.data.privacy_display === 'Custom Display Text',
    `Status: ${patch3Response.status}, privacy_display: ${patch3Response.data.privacy_display}`
  );

  // Test 3.4: Make task public again
  const patch4Response = await apiRequest('PATCH', `/api/tasks/${taskId}`, {
    is_private: false,
    privacy_level: 'public'
  }, testUserToken);
  test(
    '3.4 PATCH task - Make public again',
    patch4Response.status === 200 &&
    patch4Response.data.is_private === false &&
    patch4Response.data.privacy_level === 'public',
    `Status: ${patch4Response.status}, is_private: ${patch4Response.data.is_private}`
  );

  // Test 3.5: Verify persistence via GET
  const getResponse = await apiRequest('GET', `/api/tasks/${taskId}`, null, testUserToken);
  test(
    '3.5 Privacy changes persisted',
    getResponse.status === 200 &&
    getResponse.data.is_private === false &&
    getResponse.data.privacy_level === 'public',
    `is_private: ${getResponse.data.is_private}, privacy_level: ${getResponse.data.privacy_level}`
  );
}

async function testGetEndpointsReturnPrivacy() {
  console.log('\n=== Test Suite 4: GET Endpoints Return Privacy Fields ===');

  // First create a private task
  const createResponse = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Private task for GET test',
    is_private: true,
    privacy_level: 'initials'
  }, testUserToken);
  const taskId = createResponse.data.id;
  testTaskIds.push(taskId);

  // Test 4.1: GET /api/tasks returns privacy fields
  const tasksResponse = await apiRequest('GET', '/api/tasks', null, testUserToken);
  const privateTask = tasksResponse.data.find(t => t.id === taskId);
  test(
    '4.1 GET /api/tasks returns privacy fields',
    tasksResponse.status === 200 &&
    privateTask &&
    'is_private' in privateTask &&
    'privacy_level' in privateTask,
    `Found task: ${!!privateTask}, has fields: is_private=${privateTask?.is_private}, privacy_level=${privateTask?.privacy_level}`
  );

  // Test 4.2: GET /api/tasks/all returns privacy fields
  const allTasksResponse = await apiRequest('GET', '/api/tasks/all', null, testUserToken);
  const privateTaskAll = allTasksResponse.data.tasks?.find(t => t.id === taskId);
  test(
    '4.2 GET /api/tasks/all returns privacy fields',
    allTasksResponse.status === 200 &&
    privateTaskAll &&
    'is_private' in privateTaskAll &&
    'privacy_level' in privateTaskAll,
    `Found task: ${!!privateTaskAll}, has fields: is_private=${privateTaskAll?.is_private}, privacy_level=${privateTaskAll?.privacy_level}`
  );

  // Test 4.3: GET /api/tasks/:id returns privacy fields
  const singleTaskResponse = await apiRequest('GET', `/api/tasks/${taskId}`, null, testUserToken);
  test(
    '4.3 GET /api/tasks/:id returns privacy fields',
    singleTaskResponse.status === 200 &&
    'is_private' in singleTaskResponse.data &&
    'privacy_level' in singleTaskResponse.data &&
    singleTaskResponse.data.is_private === true &&
    singleTaskResponse.data.privacy_level === 'initials',
    `is_private: ${singleTaskResponse.data.is_private}, privacy_level: ${singleTaskResponse.data.privacy_level}`
  );
}

async function testWallpaperPrivacyIntegration() {
  console.log('\n=== Test Suite 5: Wallpaper Privacy Integration ===');

  // Create tasks with different privacy levels
  const publicTask = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Public visible task',
    is_private: false,
    privacy_level: 'public'
  }, testUserToken);
  testTaskIds.push(publicTask.data.id);

  const hiddenTask = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Should not appear on wallpaper',
    is_private: true,
    privacy_level: 'hidden'
  }, testUserToken);
  testTaskIds.push(hiddenTask.data.id);

  const categoryTask = await apiRequest('POST', '/api/tasks', {
    rawTitle: 'Secret project planning',
    is_private: true,
    privacy_level: 'category',
    category: 'work'
  }, testUserToken);
  testTaskIds.push(categoryTask.data.id);

  // Test 5.1: Wallpaper endpoint responds
  const wallpaperResponse = await fetch(`${API_BASE}/api/wallpaper`, {
    headers: { 'Authorization': `Bearer ${testUserToken}` }
  });
  test(
    '5.1 Wallpaper endpoint responds',
    wallpaperResponse.status === 200,
    `Status: ${wallpaperResponse.status}`
  );

  // Test 5.2: Wallpaper is a valid PNG
  const contentType = wallpaperResponse.headers.get('content-type');
  test(
    '5.2 Wallpaper returns PNG image',
    contentType && contentType.includes('image/png'),
    `Content-Type: ${contentType}`
  );

  // Test 5.3: Enable hide_all_tasks_mode and verify wallpaper still works
  await apiRequest('PATCH', '/api/user', { hide_all_tasks_mode: true }, testUserToken);
  const wallpaperHidden = await fetch(`${API_BASE}/api/wallpaper`, {
    headers: { 'Authorization': `Bearer ${testUserToken}` }
  });
  test(
    '5.3 Wallpaper works with hide_all_tasks_mode',
    wallpaperHidden.status === 200,
    `Status: ${wallpaperHidden.status}`
  );

  // Reset
  await apiRequest('PATCH', '/api/user', { hide_all_tasks_mode: false }, testUserToken);
}

// ============= CLEANUP =============
async function cleanup() {
  console.log('\n=== CLEANUP ===');

  try {
    // Delete test tasks via API
    for (const taskId of testTaskIds) {
      await apiRequest('DELETE', `/api/tasks/${taskId}`, null, testUserToken);
    }
    console.log(`Deleted ${testTaskIds.length} test tasks`);

    // Delete test user via API
    await apiRequest('DELETE', '/api/user', null, testUserToken);
    console.log(`Deleted test user: ${testUserEmail}`);
  } catch (err) {
    console.error('Cleanup error:', err.message);
  }
}

// ============= MAIN =============
async function runTests() {
  console.log('='.repeat(60));
  console.log('E2E Test: Privacy API Endpoints (Epic 10 - Task 3)');
  console.log('='.repeat(60));

  try {
    await setup();

    await testUserPrivacySettings();
    await testTaskCreationWithPrivacy();
    await testTaskPrivacyUpdates();
    await testGetEndpointsReturnPrivacy();
    await testWallpaperPrivacyIntegration();

  } catch (err) {
    console.error('\nTest execution error:', err);
    failedTests++;
  } finally {
    await cleanup();
  }

  // Print summary
  console.log('\n' + '='.repeat(60));
  console.log('TEST SUMMARY');
  console.log('='.repeat(60));
  console.log(`Total Tests: ${passedTests + failedTests}`);
  console.log(`Passed: ${passedTests}`);
  console.log(`Failed: ${failedTests}`);
  console.log('='.repeat(60));

  if (failedTests > 0) {
    console.log('\nFailed Tests:');
    testResults.filter(t => !t.passed).forEach(t => {
      console.log(`  - ${t.name}`);
      if (t.details) console.log(`    Details: ${t.details}`);
    });
  }

  process.exit(failedTests > 0 ? 1 : 0);
}

runTests();
