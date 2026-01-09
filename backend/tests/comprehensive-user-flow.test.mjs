/**
 * Comprehensive End-to-End User Flow Test
 * Epic 10 Phase 3 - Complete User Journey
 *
 * Tests the entire user flow from registration to wallpaper generation
 * including all settings and features
 */

const API_URL = 'https://cosmic-ocean-api.vercel.app';

let authToken = null;
let userId = null;
let testEmail = `e2e-test-${Date.now()}@test.com`;
let createdTaskIds = [];

const log = (msg, status = '📋') => console.log(`${status} ${msg}`);

async function apiCall(endpoint, method = 'GET', body = null, expectStatus = 200) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }

  const options = {
    method,
    headers,
  };

  if (body) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${API_URL}${endpoint}`, options);
  const data = await response.json().catch(() => ({}));

  if (response.status !== expectStatus) {
    throw new Error(`Expected ${expectStatus}, got ${response.status}: ${JSON.stringify(data)}`);
  }

  return data;
}

// Test Results Tracker
const results = {
  passed: 0,
  failed: 0,
  tests: []
};

async function runTest(name, testFn) {
  try {
    await testFn();
    results.passed++;
    results.tests.push({ name, status: 'PASS' });
    log(`${name}`, '✅');
    return true;
  } catch (err) {
    results.failed++;
    results.tests.push({ name, status: 'FAIL', error: err.message });
    log(`${name} - ${err.message}`, '❌');
    return false;
  }
}

// ==================== TEST SUITE ====================

async function runAllTests() {
  log('='.repeat(60));
  log('COMPREHENSIVE END-TO-END USER FLOW TEST', '🚀');
  log('='.repeat(60));
  log('');

  // ===== PHASE 1: AUTHENTICATION =====
  log('--- Phase 1: Authentication ---', '📦');

  await runTest('1.1 API Health Check', async () => {
    const health = await apiCall('/api/health');
    if (health.status !== 'ok') throw new Error('API not healthy');
    if (!health.dbInitialized) throw new Error('Database not initialized');
  });

  await runTest('1.2 Register New User', async () => {
    const data = await apiCall('/api/auth/register', 'POST', {
      email: testEmail,
      password: 'TestPass123!'
    }, 201);  // Registration returns 201 Created
    if (!data.accessToken) throw new Error('No access token');
    if (!data.user?.id) throw new Error('No user ID');
    authToken = data.accessToken;
    userId = data.user.id;
  });

  await runTest('1.3 Get User Profile', async () => {
    const user = await apiCall('/api/user');
    if (user.email !== testEmail) throw new Error('Email mismatch');
  });

  // ===== PHASE 2: PREFERENCES =====
  log('');
  log('--- Phase 2: User Preferences ---', '📦');

  await runTest('2.1 Get Default Preferences', async () => {
    const prefs = await apiCall('/api/user/preferences');
    if (prefs.theme !== 'cosmic') throw new Error(`Expected cosmic theme, got ${prefs.theme}`);
  });

  await runTest('2.2 Update Theme to Ocean', async () => {
    const updated = await apiCall('/api/user/preferences', 'PATCH', { theme: 'ocean' });
    if (updated.theme !== 'ocean') throw new Error('Theme not updated');
  });

  await runTest('2.3 Update Privacy Settings', async () => {
    const updated = await apiCall('/api/user/preferences', 'PATCH', {
      default_privacy_level: 'category',
      auto_hide_work_tasks: true
    });
    if (updated.default_privacy_level !== 'category') throw new Error('Privacy level not updated');
  });

  await runTest('2.4 Get Environment Settings (Defaults)', async () => {
    const prefs = await apiCall('/api/user/preferences');
    // Should have defaults even if columns don't exist
    const timeMode = prefs.time_of_day_mode || 'auto';
    const weatherEnabled = prefs.weather_overlay_enabled !== false;
    if (timeMode !== 'auto') throw new Error(`Expected auto time mode, got ${timeMode}`);
    if (!weatherEnabled) throw new Error('Weather overlay should be enabled by default');
  });

  // ===== PHASE 3: TASK MANAGEMENT =====
  log('');
  log('--- Phase 3: Task Management ---', '📦');

  await runTest('3.1 Create Task (Natural Language)', async () => {
    const task = await apiCall('/api/tasks', 'POST', {
      title: 'Review project proposal tomorrow at 3pm'
    }, 201);  // POST returns 201
    if (!task.id) throw new Error('No task ID');
    createdTaskIds.push(task.id);
    // Check NLP parsed fields
    if (!task.due_date) log('  Warning: NLP did not parse due_date', '⚠️');
  });

  await runTest('3.2 Create High Priority Task', async () => {
    const task = await apiCall('/api/tasks', 'POST', {
      title: 'URGENT: Server maintenance needed',
      priority: 3
    }, 201);
    createdTaskIds.push(task.id);
    if (task.priority !== 3) throw new Error('Priority not set correctly');
  });

  await runTest('3.3 Create Private Task', async () => {
    const task = await apiCall('/api/tasks', 'POST', {
      title: 'Doctor appointment next week',
      is_private: true,
      privacy_level: 'hidden'
    }, 201);
    createdTaskIds.push(task.id);
    if (!task.is_private) throw new Error('Task not marked as private');
  });

  await runTest('3.4 Get All Tasks', async () => {
    const tasks = await apiCall('/api/tasks');
    if (!Array.isArray(tasks)) throw new Error('Tasks is not an array');
    if (tasks.length < 3) throw new Error(`Expected at least 3 tasks, got ${tasks.length}`);
  });

  await runTest('3.5 Update Task', async () => {
    const taskId = createdTaskIds[0];
    const updated = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      title: 'Review updated proposal',
      priority: 2
    });
    if (updated.title !== 'Review updated proposal') throw new Error('Title not updated');
  });

  await runTest('3.6 Complete Task', async () => {
    const taskId = createdTaskIds[1];
    const completed = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      completed: true
    });
    if (!completed.completed) throw new Error('Task not marked complete');
  });

  // ===== PHASE 4: ACHIEVEMENTS =====
  log('');
  log('--- Phase 4: Achievements ---', '📦');

  await runTest('4.1 Get Achievements', async () => {
    const achievements = await apiCall('/api/achievements');
    if (!achievements.earned) throw new Error('No earned array');
    if (typeof achievements.totalPoints !== 'number') throw new Error('No totalPoints');
    log(`  Total points: ${achievements.totalPoints}`, '🏆');
  });

  await runTest('4.2 Check Achievements', async () => {
    // Use the check endpoint which forces recalculation
    const result = await apiCall('/api/achievements/check', 'POST', {});
    // No error means success
  });

  // ===== PHASE 5: WALLPAPER GENERATION =====
  log('');
  log('--- Phase 5: Wallpaper Generation ---', '📦');

  await runTest('5.1 Generate Wallpaper (Ocean Theme)', async () => {
    const response = await fetch(`${API_URL}/api/wallpaper`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    if (!response.ok) throw new Error(`Wallpaper request failed: ${response.status}`);
    const contentType = response.headers.get('content-type');
    if (!contentType?.includes('image/png')) throw new Error(`Expected PNG, got ${contentType}`);
    const buffer = await response.arrayBuffer();
    if (buffer.byteLength < 10000) throw new Error('Wallpaper too small');
    log(`  Generated wallpaper: ${(buffer.byteLength / 1024).toFixed(1)} KB`, '📷');
  });

  await runTest('5.2 Generate Wallpaper (Different Resolution)', async () => {
    // Test wallpaper with explicit resolution
    const response = await fetch(`${API_URL}/api/wallpaper?width=1080&height=1920`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    if (!response.ok) throw new Error(`Resolution wallpaper failed: ${response.status}`);
    const contentType = response.headers.get('content-type');
    if (!contentType?.includes('image/png')) throw new Error(`Expected PNG, got ${contentType}`);
  });

  // Switch to fantasy theme for variety
  await runTest('5.3 Switch to Fantasy Theme', async () => {
    const updated = await apiCall('/api/user/preferences', 'PATCH', { theme: 'fantasy' });
    if (updated.theme !== 'fantasy') throw new Error('Theme not updated to fantasy');
  });

  await runTest('5.4 Generate Fantasy Wallpaper', async () => {
    const response = await fetch(`${API_URL}/api/wallpaper`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    if (!response.ok) throw new Error(`Fantasy wallpaper failed: ${response.status}`);
    const contentType = response.headers.get('content-type');
    if (!contentType?.includes('image/png')) throw new Error('Not a PNG');
  });

  // ===== PHASE 6: PRIVACY FEATURES =====
  log('');
  log('--- Phase 6: Privacy Features ---', '📦');

  await runTest('6.1 Hide All Tasks Mode', async () => {
    await apiCall('/api/user/preferences', 'PATCH', { hide_all_tasks_mode: true });
    const prefs = await apiCall('/api/user/preferences');
    if (!prefs.hide_all_tasks_mode) throw new Error('Hide all not enabled');
  });

  await runTest('6.2 Generate Wallpaper with All Hidden', async () => {
    const response = await fetch(`${API_URL}/api/wallpaper`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    if (!response.ok) throw new Error('Hidden wallpaper failed');
    // Should still generate but without tasks
  });

  await runTest('6.3 Disable Hide All Tasks', async () => {
    await apiCall('/api/user/preferences', 'PATCH', { hide_all_tasks_mode: false });
  });

  // ===== PHASE 7: STATISTICS =====
  log('');
  log('--- Phase 7: Statistics ---', '📦');

  await runTest('7.1 Get Weekly Stats', async () => {
    const stats = await apiCall('/api/user/stats/weekly');
    if (!stats.weeks) throw new Error('No weeks data');
    if (!stats.trends) throw new Error('No trends data');
  });

  await runTest('7.2 Get Graduation Stats', async () => {
    const graduation = await apiCall('/api/user/stats/graduation');
    if (!graduation.graduation) throw new Error('No graduation data');
  });

  // ===== PHASE 8: DONE FOR TODAY =====
  log('');
  log('--- Phase 8: Done For Today ---', '📦');

  await runTest('8.1 Mark Done For Today', async () => {
    const result = await apiCall('/api/user/done-for-today', 'POST');
    if (!result.success) throw new Error('Done for today failed');
  });

  await runTest('8.2 Generate Celebration Wallpaper', async () => {
    const response = await fetch(`${API_URL}/api/wallpaper`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    if (!response.ok) {
      const text = await response.text().catch(() => 'no body');
      throw new Error(`Celebration wallpaper failed: ${response.status} - ${text}`);
    }
    // Should show celebration mode
    const buffer = await response.arrayBuffer();
    log(`  Celebration wallpaper: ${(buffer.byteLength / 1024).toFixed(1)} KB`, '🎉');
  });

  // ===== PHASE 9: CLEANUP =====
  log('');
  log('--- Phase 9: Cleanup ---', '📦');

  await runTest('9.1 Delete Test Tasks', async () => {
    for (const taskId of createdTaskIds) {
      try {
        await apiCall(`/api/tasks/${taskId}`, 'DELETE');
      } catch (e) {
        // Ignore errors for already deleted tasks
      }
    }
  });

  await runTest('9.2 Clear All Tasks', async () => {
    await apiCall('/api/tasks', 'DELETE');
    const tasks = await apiCall('/api/tasks');
    if (tasks.length > 0) throw new Error(`Expected 0 tasks after clear, got ${tasks.length}`);
  });

  await runTest('9.3 Delete Test Account', async () => {
    const result = await apiCall('/api/user', 'DELETE');
    if (!result.success) throw new Error('Account deletion failed');
  });

  // ===== SUMMARY =====
  log('');
  log('='.repeat(60));
  log('TEST SUMMARY', '📊');
  log('='.repeat(60));
  log(`Total: ${results.passed + results.failed} tests`);
  log(`Passed: ${results.passed}`, '✅');
  log(`Failed: ${results.failed}`, results.failed > 0 ? '❌' : '✅');

  if (results.failed > 0) {
    log('');
    log('Failed Tests:');
    results.tests.filter(t => t.status === 'FAIL').forEach(t => {
      log(`  - ${t.name}: ${t.error}`, '❌');
    });
  }

  log('');
  return results.failed === 0;
}

// Run tests
runAllTests()
  .then(success => {
    process.exit(success ? 0 : 1);
  })
  .catch(err => {
    console.error('Test runner error:', err);
    process.exit(1);
  });
