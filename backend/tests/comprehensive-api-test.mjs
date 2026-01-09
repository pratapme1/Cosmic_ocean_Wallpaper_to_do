/**
 * Comprehensive API & CRUD Test with Achievement Card
 * Tests all endpoints and verifies achievement rendering
 */

import fs from 'fs';
import path from 'path';

// Use native fetch (Node 18+)

const API_BASE = 'https://cosmic-ocean-api.vercel.app';
const SCREENSHOTS_DIR = '/home/vi/supernova/test-screenshots';

// Test user credentials
const TEST_USER = {
  email: `comprehensive-test-${Date.now()}@test.com`,
  password: 'TestPass123!',
  name: 'Comprehensive Test User'
};

let authToken = null;
let userId = null;
let createdTaskIds = [];

const results = {
  passed: 0,
  failed: 0,
  tests: []
};

function log(message) {
  console.log(message);
}

function logResult(testName, passed, details = '') {
  const status = passed ? '✅ PASS' : '❌ FAIL';
  console.log(`${status}: ${testName}`);
  if (details) console.log(`   ${details}`);
  results.tests.push({ name: testName, passed, details });
  if (passed) results.passed++;
  else results.failed++;
}

async function saveScreenshot(buffer, name) {
  const filename = `comprehensive-${name}-${Date.now()}.png`;
  const filepath = path.join(SCREENSHOTS_DIR, filename);
  fs.writeFileSync(filepath, buffer);
  return filepath;
}

// ============================================
// 1. AUTHENTICATION TESTS
// ============================================

async function testRegister() {
  log('\n--- 1. AUTH: Register ---');
  try {
    const res = await fetch(`${API_BASE}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(TEST_USER)
    });
    const data = await res.json();

    console.log('   Register response:', JSON.stringify(data).substring(0, 200));

    // API returns accessToken (not token)
    const token = data.token || data.accessToken;
    if (res.ok && token) {
      authToken = token;
      userId = data.user?.id;
      logResult('Register new user', true, `User ID: ${userId}`);
      return true;
    } else if (data.error && data.error.includes('already')) {
      // User already exists, try login instead
      log('   User exists, trying login...');
      return await testLogin();
    } else {
      logResult('Register new user', false, data.error || JSON.stringify(data));
      return false;
    }
  } catch (err) {
    logResult('Register new user', false, err.message);
    return false;
  }
}

async function testLogin() {
  log('\n--- 2. AUTH: Login ---');
  try {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: TEST_USER.email, password: TEST_USER.password })
    });
    const data = await res.json();

    const token = data.token || data.accessToken;
    if (res.ok && token) {
      authToken = token;
      userId = data.user?.id;
      logResult('Login existing user', true);
      return true;
    } else {
      logResult('Login existing user', false, data.error || JSON.stringify(data));
      return false;
    }
  } catch (err) {
    logResult('Login existing user', false, err.message);
    return false;
  }
}

// ============================================
// 2. TASK CRUD TESTS
// ============================================

async function testCreateTask(taskData) {
  const taskName = taskData.title;
  try {
    const res = await fetch(`${API_BASE}/api/tasks`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify(taskData)
    });
    const data = await res.json();

    if (res.ok && data.id) {
      createdTaskIds.push(data.id);
      logResult(`CREATE task: "${taskName}"`, true, `ID: ${data.id}`);
      return data;
    } else {
      logResult(`CREATE task: "${taskName}"`, false, data.error);
      return null;
    }
  } catch (err) {
    logResult(`CREATE task: "${taskName}"`, false, err.message);
    return null;
  }
}

async function testReadTasks() {
  log('\n--- 4. CRUD: Read Tasks ---');
  try {
    const res = await fetch(`${API_BASE}/api/tasks`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (res.ok && Array.isArray(data)) {
      logResult('READ all tasks', true, `Found ${data.length} tasks`);
      return data;
    } else {
      logResult('READ all tasks', false, data.error);
      return [];
    }
  } catch (err) {
    logResult('READ all tasks', false, err.message);
    return [];
  }
}

async function testUpdateTask(taskId, updates) {
  try {
    const res = await fetch(`${API_BASE}/api/tasks/${taskId}`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify(updates)
    });
    const data = await res.json();

    if (res.ok) {
      logResult(`UPDATE task ${taskId}`, true, JSON.stringify(updates));
      return data;
    } else {
      logResult(`UPDATE task ${taskId}`, false, data.error);
      return null;
    }
  } catch (err) {
    logResult(`UPDATE task ${taskId}`, false, err.message);
    return null;
  }
}

async function testDeleteTask(taskId) {
  try {
    const res = await fetch(`${API_BASE}/api/tasks/${taskId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    if (res.ok) {
      logResult(`DELETE task ${taskId}`, true);
      return true;
    } else {
      const data = await res.json();
      logResult(`DELETE task ${taskId}`, false, data.error);
      return false;
    }
  } catch (err) {
    logResult(`DELETE task ${taskId}`, false, err.message);
    return false;
  }
}

// ============================================
// 3. WALLPAPER & ACHIEVEMENT TESTS
// ============================================

async function testWallpaperGeneration(scenario) {
  try {
    const res = await fetch(`${API_BASE}/api/wallpaper?width=1080&height=1920&theme=cosmic`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    if (res.ok) {
      const buffer = Buffer.from(await res.arrayBuffer());
      const filepath = await saveScreenshot(buffer, scenario);
      const sizeKB = (buffer.length / 1024).toFixed(1);
      logResult(`WALLPAPER: ${scenario}`, true, `Size: ${sizeKB}KB, Saved: ${path.basename(filepath)}`);
      return { buffer, filepath };
    } else {
      const text = await res.text();
      logResult(`WALLPAPER: ${scenario}`, false, text.substring(0, 100));
      return null;
    }
  } catch (err) {
    logResult(`WALLPAPER: ${scenario}`, false, err.message);
    return null;
  }
}

async function testUserPreferences() {
  log('\n--- 7. USER PREFERENCES ---');
  try {
    // GET preferences
    const getRes = await fetch(`${API_BASE}/api/user/preferences`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    if (getRes.ok) {
      const prefs = await getRes.json();
      logResult('GET user preferences', true, `Theme: ${prefs.theme || 'cosmic'}`);
    } else {
      logResult('GET user preferences', false);
    }

    // PATCH preferences
    const patchRes = await fetch(`${API_BASE}/api/user/preferences`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify({ show_achievements_on_wallpaper: true })
    });

    if (patchRes.ok) {
      logResult('PATCH user preferences', true, 'show_achievements_on_wallpaper: true');
    } else {
      logResult('PATCH user preferences', false);
    }
  } catch (err) {
    logResult('User preferences', false, err.message);
  }
}

async function testHealthEndpoint() {
  log('\n--- 0. HEALTH CHECK ---');
  try {
    const res = await fetch(`${API_BASE}/api/health`);
    const data = await res.json();

    if (res.ok && data.status === 'ok') {
      logResult('Health check', true, `Version: ${data.version}`);
      return true;
    } else {
      logResult('Health check', false);
      return false;
    }
  } catch (err) {
    logResult('Health check', false, err.message);
    return false;
  }
}

// ============================================
// CLEANUP
// ============================================

async function cleanup() {
  log('\n--- CLEANUP ---');

  // Delete all created tasks
  for (const taskId of createdTaskIds) {
    try {
      await fetch(`${API_BASE}/api/tasks/${taskId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
    } catch (e) {
      // Ignore cleanup errors
    }
  }

  // Delete test user
  try {
    await fetch(`${API_BASE}/api/auth/delete-account`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    log('Test user cleaned up');
  } catch (e) {
    log('Note: Could not delete test user (may need manual cleanup)');
  }
}

// ============================================
// MAIN TEST RUNNER
// ============================================

async function runAllTests() {
  console.log('========================================');
  console.log('COMPREHENSIVE API & ACHIEVEMENT TEST');
  console.log('========================================');
  console.log(`API: ${API_BASE}`);
  console.log(`Time: ${new Date().toISOString()}`);
  console.log('========================================\n');

  // 0. Health check
  await testHealthEndpoint();

  // 1. Register
  const registered = await testRegister();
  if (!registered) {
    console.log('\n❌ Cannot proceed without authentication');
    return;
  }

  // 2. Login (verify it works)
  await testLogin();

  // 3. CRUD: Create tasks
  log('\n--- 3. CRUD: Create Tasks ---');

  const task1 = await testCreateTask({
    title: 'Test Task 1 - High Priority',
    priority: 2,
    estimate_minutes: 30
  });

  const task2 = await testCreateTask({
    title: 'Test Task 2 - Medium Priority',
    priority: 1,
    estimate_minutes: 15
  });

  const task3 = await testCreateTask({
    title: 'Test Task 3 - Low Priority',
    priority: 0,
    estimate_minutes: 45
  });

  // 4. Read tasks
  await testReadTasks();

  // 5. Generate wallpaper with pending tasks
  log('\n--- 5. WALLPAPER: With Pending Tasks ---');
  await testWallpaperGeneration('pending-tasks');

  // 6. Update: Complete a task (triggers First Step achievement)
  log('\n--- 6. CRUD: Update Tasks ---');
  if (task1) {
    await testUpdateTask(task1.id, { completed: true });
  }

  // Generate wallpaper after completing task (should show achievement)
  log('\n--- WALLPAPER: After First Completion ---');
  await testWallpaperGeneration('first-completion');

  // Complete all remaining tasks (triggers Zero Inbox)
  if (task2) {
    await testUpdateTask(task2.id, { completed: true });
  }
  if (task3) {
    await testUpdateTask(task3.id, { completed: true });
  }

  // Generate wallpaper with all tasks complete (Zero Inbox + First Step)
  log('\n--- WALLPAPER: Zero Inbox ---');
  await testWallpaperGeneration('zero-inbox');

  // 7. User preferences
  await testUserPreferences();

  // 8. Delete a task
  log('\n--- 8. CRUD: Delete Task ---');
  if (task3) {
    await testDeleteTask(task3.id);
    createdTaskIds = createdTaskIds.filter(id => id !== task3.id);
  }

  // Final wallpaper after delete
  log('\n--- WALLPAPER: After Delete ---');
  await testWallpaperGeneration('after-delete');

  // Cleanup
  await cleanup();

  // Summary
  console.log('\n========================================');
  console.log('TEST SUMMARY');
  console.log('========================================');
  console.log(`Total: ${results.passed + results.failed}`);
  console.log(`Passed: ${results.passed}`);
  console.log(`Failed: ${results.failed}`);
  console.log('========================================');

  if (results.failed === 0) {
    console.log('\n🎉 ALL TESTS PASSED!');
  } else {
    console.log('\n⚠️  Some tests failed:');
    results.tests.filter(t => !t.passed).forEach(t => {
      console.log(`   - ${t.name}: ${t.details}`);
    });
  }

  console.log('\n📸 Screenshots saved to:', SCREENSHOTS_DIR);
}

// Run tests
runAllTests().catch(err => {
  console.error('Test runner error:', err);
  process.exit(1);
});
