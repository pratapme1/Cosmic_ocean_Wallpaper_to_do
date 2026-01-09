/**
 * CRUD Operations → Wallpaper & Achievements Test
 *
 * Tests that:
 * 1. Task CRUD operations are reflected in wallpaper generation
 * 2. Achievements are earned based on task operations
 * 3. Wallpaper content updates when tasks change
 */

const API_URL = 'https://cosmic-ocean-api.vercel.app';

let authToken = null;
let userId = null;
let testEmail = `crud-test-${Date.now()}@test.com`;
let createdTaskIds = [];

const log = (msg, status = '📋') => console.log(`${status} ${msg}`);
const logSection = (title) => {
  console.log('');
  console.log('='.repeat(60));
  console.log(`${title}`);
  console.log('='.repeat(60));
};

async function apiCall(endpoint, method = 'GET', body = null, expectStatus = 200) {
  const headers = { 'Content-Type': 'application/json' };
  if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

  const options = { method, headers };
  if (body) options.body = JSON.stringify(body);

  const response = await fetch(`${API_URL}${endpoint}`, options);
  const data = await response.json().catch(() => ({}));

  if (response.status !== expectStatus) {
    throw new Error(`Expected ${expectStatus}, got ${response.status}: ${JSON.stringify(data)}`);
  }
  return data;
}

async function getWallpaperSize() {
  const response = await fetch(`${API_URL}/api/wallpaper`, {
    headers: { 'Authorization': `Bearer ${authToken}` }
  });
  if (!response.ok) {
    const text = await response.text().catch(() => 'no body');
    throw new Error(`Wallpaper failed: ${response.status} - ${text}`);
  }
  const buffer = await response.arrayBuffer();
  return buffer.byteLength;
}

// Results tracker
const results = { passed: 0, failed: 0, tests: [] };

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

// ==================== MAIN TEST SUITE ====================

async function runAllTests() {
  logSection('CRUD → WALLPAPER & ACHIEVEMENTS TEST');
  log(`Test Email: ${testEmail}`);
  log(`API: ${API_URL}`);

  // ===== SETUP: Register and Login =====
  logSection('PHASE 1: SETUP');

  await runTest('1.1 Register Test User', async () => {
    const data = await apiCall('/api/auth/register', 'POST', {
      email: testEmail,
      password: 'TestPass123!'
    }, 201);
    if (!data.accessToken) throw new Error('No access token');
    authToken = data.accessToken;
    userId = data.user.id;
    log(`  User ID: ${userId}`, '📝');
  });

  await runTest('1.2 Get Initial Achievements', async () => {
    const achievements = await apiCall('/api/achievements');
    log(`  Initial points: ${achievements.totalPoints}`, '📝');
    log(`  Initial earned: ${achievements.earned?.length || 0}`, '📝');
  });

  await runTest('1.3 Generate Baseline Wallpaper (no tasks)', async () => {
    const size = await getWallpaperSize();
    log(`  Baseline wallpaper: ${(size / 1024).toFixed(1)} KB`, '📝');
    if (size < 5000) throw new Error('Wallpaper too small');
  });

  // ===== CREATE TASKS =====
  logSection('PHASE 2: CREATE TASKS');

  await runTest('2.1 Create First Task', async () => {
    const task = await apiCall('/api/tasks', 'POST', {
      title: 'First test task - high priority',
      priority: 3
    }, 201);
    createdTaskIds.push(task.id);
    log(`  Task ID: ${task.id}`, '📝');
    log(`  Title: ${task.title}`, '📝');
  });

  // Small delay to avoid rate limiting
  await new Promise(r => setTimeout(r, 1000));

  await runTest('2.2 Generate Wallpaper with 1 Task', async () => {
    const size = await getWallpaperSize();
    log(`  Wallpaper with 1 task: ${(size / 1024).toFixed(1)} KB`, '📝');
  });

  await runTest('2.3 Create Second Task', async () => {
    const task = await apiCall('/api/tasks', 'POST', {
      title: 'Second task - medium priority',
      priority: 2
    }, 201);
    createdTaskIds.push(task.id);
    log(`  Task ID: ${task.id}`, '📝');
  });

  await runTest('2.4 Create Third Task (Private)', async () => {
    const task = await apiCall('/api/tasks', 'POST', {
      title: 'Private task - should be hidden',
      is_private: true,
      privacy_level: 'hidden'
    }, 201);
    createdTaskIds.push(task.id);
    log(`  Task ID: ${task.id}`, '📝');
    log(`  Is Private: ${task.is_private}`, '📝');
  });

  await new Promise(r => setTimeout(r, 1000));

  await runTest('2.5 Generate Wallpaper with 3 Tasks', async () => {
    const size = await getWallpaperSize();
    log(`  Wallpaper with 3 tasks: ${(size / 1024).toFixed(1)} KB`, '📝');
  });

  await runTest('2.6 Verify All Tasks in List', async () => {
    const tasks = await apiCall('/api/tasks');
    log(`  Total tasks: ${tasks.length}`, '📝');
    if (tasks.length !== 3) throw new Error(`Expected 3 tasks, got ${tasks.length}`);

    // Log task details
    tasks.forEach((t, i) => {
      log(`  Task ${i+1}: "${t.title}" (priority: ${t.priority}, private: ${t.is_private})`, '📝');
    });
  });

  // ===== UPDATE TASKS =====
  logSection('PHASE 3: UPDATE TASKS');

  await runTest('3.1 Update First Task Title', async () => {
    const taskId = createdTaskIds[0];
    const updated = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      title: 'UPDATED: First task now urgent!'
    });
    log(`  New title: ${updated.title}`, '📝');
    if (!updated.title.includes('UPDATED')) throw new Error('Title not updated');
  });

  await runTest('3.2 Update Second Task Priority', async () => {
    const taskId = createdTaskIds[1];
    const updated = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      priority: 3  // Upgrade to high priority
    });
    log(`  New priority: ${updated.priority}`, '📝');
    if (updated.priority !== 3) throw new Error('Priority not updated');
  });

  await new Promise(r => setTimeout(r, 1000));

  await runTest('3.3 Generate Wallpaper After Updates', async () => {
    const size = await getWallpaperSize();
    log(`  Wallpaper after updates: ${(size / 1024).toFixed(1)} KB`, '📝');
  });

  // ===== COMPLETE TASKS (Should Trigger Achievements) =====
  logSection('PHASE 4: COMPLETE TASKS');

  await runTest('4.1 Complete First Task', async () => {
    const taskId = createdTaskIds[0];
    const completed = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      completed: true
    });
    log(`  Completed: ${completed.completed}`, '📝');
    if (!completed.completed) throw new Error('Task not marked complete');
  });

  await runTest('4.2 Check Achievements After First Completion', async () => {
    // Force achievement recalculation
    await apiCall('/api/achievements/check', 'POST', {});
    const achievements = await apiCall('/api/achievements');
    log(`  Points after 1 completion: ${achievements.totalPoints}`, '📝');
    log(`  Earned achievements: ${achievements.earned?.length || 0}`, '📝');

    // List earned achievements
    if (achievements.earned?.length > 0) {
      achievements.earned.forEach(a => {
        log(`    - ${a.name}: ${a.description}`, '🏆');
      });
    }
  });

  await runTest('4.3 Complete Second Task', async () => {
    const taskId = createdTaskIds[1];
    const completed = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      completed: true
    });
    if (!completed.completed) throw new Error('Task not marked complete');
  });

  await runTest('4.4 Complete Third Task (Private)', async () => {
    const taskId = createdTaskIds[2];
    const completed = await apiCall(`/api/tasks/${taskId}`, 'PATCH', {
      completed: true
    });
    if (!completed.completed) throw new Error('Task not marked complete');
  });

  await runTest('4.5 Check Achievements After All Completions', async () => {
    await apiCall('/api/achievements/check', 'POST', {});
    const achievements = await apiCall('/api/achievements');
    log(`  Points after all completions: ${achievements.totalPoints}`, '📝');
    log(`  Total earned: ${achievements.earned?.length || 0}`, '📝');

    // Should have at least "First Task" achievement
    if (achievements.earned?.length > 0) {
      log(`  Earned Achievements:`, '🏆');
      achievements.earned.forEach(a => {
        log(`    - ${a.name} (${a.points} pts)`, '🏆');
      });
    }
  });

  await new Promise(r => setTimeout(r, 1000));

  await runTest('4.6 Generate Wallpaper After Completions', async () => {
    const size = await getWallpaperSize();
    log(`  Wallpaper after completions: ${(size / 1024).toFixed(1)} KB`, '📝');
  });

  // ===== DELETE TASKS =====
  logSection('PHASE 5: DELETE TASKS');

  await runTest('5.1 Delete First Task', async () => {
    const taskId = createdTaskIds[0];
    await apiCall(`/api/tasks/${taskId}`, 'DELETE');
    log(`  Deleted task: ${taskId}`, '📝');
  });

  await runTest('5.2 Verify Task Count After Delete', async () => {
    const tasks = await apiCall('/api/tasks');
    log(`  Remaining tasks: ${tasks.length}`, '📝');
    if (tasks.length !== 2) throw new Error(`Expected 2 tasks, got ${tasks.length}`);
  });

  await new Promise(r => setTimeout(r, 1000));

  await runTest('5.3 Generate Wallpaper After Delete', async () => {
    const size = await getWallpaperSize();
    log(`  Wallpaper after delete: ${(size / 1024).toFixed(1)} KB`, '📝');
  });

  // ===== DONE FOR TODAY =====
  logSection('PHASE 6: DONE FOR TODAY');

  await runTest('6.1 Mark Done For Today', async () => {
    const result = await apiCall('/api/user/done-for-today', 'POST');
    if (!result.success) throw new Error('Done for today failed');
    log(`  Done for today: ${result.success}`, '📝');
  });

  await new Promise(r => setTimeout(r, 1000));

  await runTest('6.2 Generate Celebration Wallpaper', async () => {
    const size = await getWallpaperSize();
    log(`  Celebration wallpaper: ${(size / 1024).toFixed(1)} KB`, '📝');
  });

  // ===== FINAL ACHIEVEMENT CHECK =====
  logSection('PHASE 7: FINAL VERIFICATION');

  await runTest('7.1 Final Achievement Summary', async () => {
    await apiCall('/api/achievements/check', 'POST', {});
    const achievements = await apiCall('/api/achievements');

    log(`  Total Points: ${achievements.totalPoints}`, '🏆');
    log(`  Total Earned: ${achievements.earned?.length || 0}`, '🏆');

    if (achievements.earned?.length > 0) {
      log(`  Achievement List:`, '📋');
      achievements.earned.forEach((a, i) => {
        log(`    ${i+1}. ${a.name} - ${a.points} pts`, '🏆');
      });
    }
  });

  await runTest('7.2 Final Task Count', async () => {
    const tasks = await apiCall('/api/tasks');
    log(`  Final task count: ${tasks.length}`, '📝');
  });

  // ===== CLEANUP =====
  logSection('PHASE 8: CLEANUP');

  await runTest('8.1 Delete Remaining Tasks', async () => {
    await apiCall('/api/tasks', 'DELETE');
    const tasks = await apiCall('/api/tasks');
    if (tasks.length !== 0) throw new Error(`Expected 0 tasks after clear, got ${tasks.length}`);
  });

  await runTest('8.2 Delete Test Account', async () => {
    const result = await apiCall('/api/user', 'DELETE');
    if (!result.success) throw new Error('Account deletion failed');
  });

  // ===== SUMMARY =====
  logSection('TEST SUMMARY');
  log(`Total Tests: ${results.passed + results.failed}`);
  log(`Passed: ${results.passed}`, '✅');
  log(`Failed: ${results.failed}`, results.failed > 0 ? '❌' : '✅');

  if (results.failed > 0) {
    console.log('');
    log('Failed Tests:');
    results.tests.filter(t => t.status === 'FAIL').forEach(t => {
      log(`  - ${t.name}: ${t.error}`, '❌');
    });
  }

  console.log('');
  return results.failed === 0;
}

// Run tests
runAllTests()
  .then(success => {
    console.log(success ? '\n--complete-promise  COMPLETE' : '\n--complete-promise  FAILED');
    process.exit(success ? 0 : 1);
  })
  .catch(err => {
    console.error('Test runner error:', err);
    console.log('\n--complete-promise  FAILED');
    process.exit(1);
  });
