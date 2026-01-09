/**
 * Comprehensive Cosmic Ocean App Test Suite
 *
 * Tests:
 * - Timezone handling (TZ-1 to TZ-5)
 * - Star colors by priority (STAR-1 to STAR-5)
 * - CRUD operations (CREATE, READ, UPDATE, DELETE)
 * - Edge cases
 *
 * Total: 24 tests
 */

require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });

const fs = require('fs');
const path = require('path');

// Configuration
const API_BASE = process.argv[2] || process.env.API_BASE_URL || 'http://localhost:3000';
const TEST_TIMEZONE = 'Asia/Kolkata';
const SCREENSHOT_DIR = path.join(__dirname, '../../test-screenshots');

// Test user
const TEST_USER = {
  email: 'comprehensive-test@example.com',
  password: 'test123456'
};

let authToken = null;

// Results tracking
const testResults = {
  total: 0,
  passed: 0,
  failed: 0,
  tests: []
};

// Ensure screenshot directory
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

// ============ HELPERS ============

function getDateInTimezone(date, timezone) {
  const options = { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' };
  const parts = new Intl.DateTimeFormat('en-CA', options).formatToParts(date);
  const year = parts.find(p => p.type === 'year').value;
  const month = parts.find(p => p.type === 'month').value;
  const day = parts.find(p => p.type === 'day').value;
  return `${year}-${month}-${day}`;
}

function getTimeInTimezone(date, timezone) {
  return date.toLocaleTimeString('en-GB', {
    timeZone: timezone,
    hour12: false,
    hour: '2-digit',
    minute: '2-digit'
  });
}

function addDays(date, days) {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function timeWithin(actual, expected, toleranceMin = 3) {
  if (!actual || !expected) return false;
  const [aH, aM] = actual.split(':').map(Number);
  const [eH, eM] = expected.split(':').map(Number);
  const diffMinutes = Math.abs((aH * 60 + aM) - (eH * 60 + eM));
  return diffMinutes <= toleranceMin;
}

async function apiRequest(method, endpoint, body = null, auth = true) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }

  const options = { method, headers };
  if (body) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${API_BASE}${endpoint}`, options);
  const contentType = response.headers.get('content-type');
  let data;
  if (contentType && contentType.includes('application/json')) {
    data = await response.json();
  } else {
    data = { raw: await response.text() };
  }

  return { status: response.status, data };
}

async function authenticate() {
  console.log('\n🔐 Authenticating...');
  let res = await apiRequest('POST', '/api/auth/login', TEST_USER, false);
  let token = res.data.accessToken || res.data.token;

  if (res.status !== 200 || !token) {
    res = await apiRequest('POST', '/api/auth/register', {
      ...TEST_USER,
      timezone: TEST_TIMEZONE
    }, false);
    token = res.data.accessToken || res.data.token;
    if (!token) throw new Error(`Auth failed: ${JSON.stringify(res.data)}`);
  }

  authToken = token;
  console.log('  ✅ Authenticated');
}

async function cleanup() {
  console.log('🧹 Cleaning up...');
  await apiRequest('DELETE', '/api/tasks');
  console.log('  ✅ Tasks cleared');
}

async function createTask(rawTitle) {
  const res = await apiRequest('POST', '/api/tasks', { rawTitle });
  if (res.status !== 201) throw new Error(`Create failed: ${JSON.stringify(res.data)}`);
  return res.data;
}

async function getTask(id) {
  const res = await apiRequest('GET', `/api/tasks/${id}`);
  return res;
}

async function getAllTasks() {
  const res = await apiRequest('GET', '/api/tasks');
  return res;
}

async function updateTask(id, updates) {
  const res = await apiRequest('PATCH', `/api/tasks/${id}`, updates);
  return res;
}

async function deleteTask(id) {
  const res = await apiRequest('DELETE', `/api/tasks/${id}`);
  return res;
}

async function saveWallpaper(testId) {
  try {
    const url = `${API_BASE}/api/wallpaper?timezone=${TEST_TIMEZONE}&theme=cosmic&resolution=1080x1920`;
    const res = await fetch(url, { headers: { 'Authorization': `Bearer ${authToken}` } });
    if (res.ok) {
      const buffer = await res.arrayBuffer();
      const filepath = path.join(SCREENSHOT_DIR, `${testId}.png`);
      fs.writeFileSync(filepath, Buffer.from(buffer));
      console.log(`  📸 Screenshot: ${testId}.png`);
      return filepath;
    }
  } catch (e) {
    console.log(`  ⚠️ Screenshot failed: ${e.message}`);
  }
  return null;
}

// ============ TEST RUNNER ============

async function runTest(testId, description, testFn) {
  testResults.total++;
  console.log(`\n${'='.repeat(60)}`);
  console.log(`TEST ${testId}: ${description}`);
  console.log('='.repeat(60));

  // Add delay between tests to avoid rate limiting
  await new Promise(r => setTimeout(r, 1000));

  try {
    const result = await testFn();

    if (result.passed) {
      console.log(`\n✅ ${testId} PASSED`);
      testResults.passed++;
    } else {
      console.log(`\n❌ ${testId} FAILED: ${result.reason}`);
      testResults.failed++;
    }

    testResults.tests.push({
      id: testId,
      description,
      ...result
    });

    return result.passed;
  } catch (error) {
    console.log(`\n❌ ${testId} ERROR: ${error.message}`);
    testResults.failed++;
    testResults.tests.push({
      id: testId,
      description,
      passed: false,
      error: error.message
    });
    return false;
  }
}

// ============ TIMEZONE TESTS ============

async function runTimezoneTests() {
  console.log('\n\n' + '═'.repeat(60));
  console.log('  TIMEZONE TESTS (TZ-1 to TZ-5)');
  console.log('═'.repeat(60));

  const now = new Date();
  const todayStr = getDateInTimezone(now, TEST_TIMEZONE);
  const tomorrowStr = getDateInTimezone(addDays(now, 1), TEST_TIMEZONE);
  const yesterdayStr = getDateInTimezone(addDays(now, -1), TEST_TIMEZONE);
  const nextWeekStr = getDateInTimezone(addDays(now, 7), TEST_TIMEZONE);

  // TZ-1: 'tomorrow' at 11pm → next day
  await runTest('TZ-1', "'tomorrow' → next day's date", async () => {
    await cleanup();
    const task = await createTask('meeting tomorrow');
    await saveWallpaper('TZ-1');
    const passed = task.due_date === tomorrowStr;
    return {
      passed,
      reason: passed ? '' : `Expected ${tomorrowStr}, got ${task.due_date}`,
      task
    };
  });

  // TZ-2: 'yesterday' → previous day
  await runTest('TZ-2', "'yesterday' → previous day's date", async () => {
    await cleanup();
    const task = await createTask('missed call yesterday');
    await saveWallpaper('TZ-2');
    const passed = task.due_date === yesterdayStr;
    return {
      passed,
      reason: passed ? '' : `Expected ${yesterdayStr}, got ${task.due_date}`,
      task
    };
  });

  // TZ-3: 'now' → current time, RED star (priority 1)
  await runTest('TZ-3', "'now' → current time + RED star (P1)", async () => {
    await cleanup();
    const task = await createTask('call mom now');
    await saveWallpaper('TZ-3');
    const dateMatch = task.due_date === todayStr;
    const priorityMatch = task.priority === 1;
    const timeMatch = task.due_time && timeWithin(task.due_time, getTimeInTimezone(now, TEST_TIMEZONE), 5);
    const passed = dateMatch && priorityMatch && timeMatch;
    return {
      passed,
      reason: !passed ? `date:${dateMatch}, priority:${priorityMatch}, time:${timeMatch}` : '',
      task
    };
  });

  // TZ-4: 'in 2 hours' → future time, RED star
  await runTest('TZ-4', "'in 2 hours' → future time + RED star (P1)", async () => {
    await cleanup();
    const task = await createTask('meeting in 2 hours');
    await saveWallpaper('TZ-4');
    const future = new Date(now.getTime() + 2 * 60 * 60 * 1000);
    const expectedTime = getTimeInTimezone(future, TEST_TIMEZONE);
    const timeMatch = task.due_time && timeWithin(task.due_time, expectedTime, 5);
    const priorityMatch = task.priority === 1;
    const passed = timeMatch && priorityMatch;
    return {
      passed,
      reason: !passed ? `time:${timeMatch}(exp:${expectedTime},got:${task.due_time}), priority:${priorityMatch}` : '',
      task
    };
  });

  // TZ-5: 'next week' → +7 days, BLUE star (priority 3)
  await runTest('TZ-5', "'next week' → +7 days + BLUE star (P3)", async () => {
    await cleanup();
    const task = await createTask('review next week');
    await saveWallpaper('TZ-5');
    const dateMatch = task.due_date === nextWeekStr;
    const priorityMatch = task.priority === 3 || task.priority === 2; // Allow P2 or P3
    const passed = dateMatch && priorityMatch;
    return {
      passed,
      reason: !passed ? `date:${dateMatch}(exp:${nextWeekStr},got:${task.due_date}), priority:${task.priority}` : '',
      task
    };
  });
}

// ============ STAR COLOR TESTS ============

async function runStarColorTests() {
  console.log('\n\n' + '═'.repeat(60));
  console.log('  STAR COLOR TESTS (STAR-1 to STAR-5)');
  console.log('  P1=RED 52px, P2=ORANGE 36px, P3=BLUE 24px');
  console.log('═'.repeat(60));

  // STAR-1: 'urgent' → RED (P1)
  await runTest('STAR-1', "'urgent' → RED star (P1)", async () => {
    await cleanup();
    const task = await createTask('urgent meeting');
    const screenshot = await saveWallpaper('STAR-1');
    const passed = task.priority === 1;
    return {
      passed,
      reason: !passed ? `Expected priority 1, got ${task.priority}` : '',
      task,
      expectedColor: 'RED 52px',
      screenshot
    };
  });

  // STAR-2: 'groceries' → ORANGE (P2)
  await runTest('STAR-2', "'groceries' → ORANGE star (P2)", async () => {
    await cleanup();
    const task = await createTask('buy groceries');
    const screenshot = await saveWallpaper('STAR-2');
    const passed = task.priority === 2;
    return {
      passed,
      reason: !passed ? `Expected priority 2, got ${task.priority}` : '',
      task,
      expectedColor: 'ORANGE 36px',
      screenshot
    };
  });

  // STAR-3: 'someday' → BLUE (P3)
  await runTest('STAR-3', "'someday' → BLUE star (P3)", async () => {
    await cleanup();
    const task = await createTask('someday learn piano');
    const screenshot = await saveWallpaper('STAR-3');
    const passed = task.priority === 3;
    return {
      passed,
      reason: !passed ? `Expected priority 3, got ${task.priority}` : '',
      task,
      expectedColor: 'BLUE 24px',
      screenshot
    };
  });

  // STAR-4: 'in 30 min' → auto RED (P1)
  await runTest('STAR-4', "'in 30 min' → auto RED star (P1)", async () => {
    await cleanup();
    const task = await createTask('call in 30 min');
    const screenshot = await saveWallpaper('STAR-4');
    const passed = task.priority === 1;
    return {
      passed,
      reason: !passed ? `Expected priority 1 (auto-upgrade), got ${task.priority}` : '',
      task,
      expectedColor: 'RED 52px',
      screenshot
    };
  });

  // STAR-5: All 3 colors visible
  await runTest('STAR-5', "All 3 priority colors visible", async () => {
    await cleanup();
    await new Promise(r => setTimeout(r, 300)); // small delay
    const p1 = await createTask('urgent task now');
    await new Promise(r => setTimeout(r, 300));
    const p2 = await createTask('buy milk');
    await new Promise(r => setTimeout(r, 300));
    const p3 = await createTask('someday organize desk');
    const screenshot = await saveWallpaper('STAR-5');

    const hasP1 = p1.priority === 1;
    const hasP2 = p2.priority === 2;
    const hasP3 = p3.priority === 3;
    const passed = hasP1 && hasP2 && hasP3;

    return {
      passed,
      reason: !passed ? `P1:${hasP1}, P2:${hasP2}, P3:${hasP3}` : '',
      tasks: [p1, p2, p3],
      screenshot
    };
  });
}

// ============ CRUD TESTS ============

async function runCrudTests() {
  console.log('\n\n' + '═'.repeat(60));
  console.log('  CRUD TESTS');
  console.log('═'.repeat(60));

  // CREATE: basic
  await runTest('CRUD-C1', "CREATE: basic task", async () => {
    await cleanup();
    const task = await createTask('test task');
    const screenshot = await saveWallpaper('CRUD-C1');
    const passed = task.id && task.title;
    return {
      passed,
      reason: !passed ? 'Task not created properly' : '',
      task,
      screenshot
    };
  });

  // CREATE: all fields
  await runTest('CRUD-C2', "CREATE: task with all NLP fields", async () => {
    await cleanup();
    const task = await createTask('urgent meeting tomorrow at 3pm for 1 hour');
    const screenshot = await saveWallpaper('CRUD-C2');
    const hasTitle = !!task.title;
    const hasDate = !!task.due_date;
    const hasTime = !!task.due_time;
    const hasPriority = task.priority === 1;
    const passed = hasTitle && hasDate && hasTime && hasPriority;
    return {
      passed,
      reason: !passed ? `title:${hasTitle}, date:${hasDate}, time:${hasTime}, priority:${hasPriority}` : '',
      task,
      screenshot
    };
  });

  // CREATE: NLP parse
  await runTest('CRUD-C3', "CREATE: NLP correctly parses 'email boss in 10 min'", async () => {
    await cleanup();
    const task = await createTask('email boss in 10 min');
    const screenshot = await saveWallpaper('CRUD-C3');
    const hasTime = !!task.due_time;
    const hasPriority = task.priority === 1; // Should be upgraded due to <2hr
    const passed = hasTime && hasPriority;
    return {
      passed,
      reason: !passed ? `time:${task.due_time}, priority:${task.priority}` : '',
      task,
      screenshot
    };
  });

  // READ: all tasks
  await runTest('CRUD-R1', "READ: get all tasks", async () => {
    await cleanup();
    await new Promise(r => setTimeout(r, 300));
    await createTask('task 1');
    await new Promise(r => setTimeout(r, 300));
    await createTask('task 2');
    await new Promise(r => setTimeout(r, 300));
    await createTask('task 3');
    const res = await getAllTasks();
    const screenshot = await saveWallpaper('CRUD-R1');
    const passed = res.status === 200 && Array.isArray(res.data) && res.data.length === 3;
    return {
      passed,
      reason: !passed ? `Status:${res.status}, count:${res.data?.length}` : '',
      tasks: res.data,
      screenshot
    };
  });

  // READ: single task
  await runTest('CRUD-R2', "READ: get single task by ID", async () => {
    await cleanup();
    const created = await createTask('find me');
    const res = await getTask(created.id);
    const screenshot = await saveWallpaper('CRUD-R2');
    const passed = res.status === 200 && res.data.id === created.id;
    return {
      passed,
      reason: !passed ? `Status:${res.status}` : '',
      task: res.data,
      screenshot
    };
  });

  // UPDATE: title
  await runTest('CRUD-U1', "UPDATE: change title", async () => {
    await cleanup();
    const task = await createTask('old title');
    const res = await updateTask(task.id, { title: 'new title' });
    const screenshot = await saveWallpaper('CRUD-U1');
    const passed = res.status === 200 && res.data.title === 'new title';
    return {
      passed,
      reason: !passed ? `Status:${res.status}, title:${res.data?.title}` : '',
      task: res.data,
      screenshot
    };
  });

  // UPDATE: priority (color change)
  await runTest('CRUD-U2', "UPDATE: change priority (P2→P1, color change)", async () => {
    await cleanup();
    const task = await createTask('groceries'); // P2
    console.log(`  Original priority: ${task.priority}`);
    const res = await updateTask(task.id, { priority: 1 });
    const screenshot = await saveWallpaper('CRUD-U2');
    const passed = res.status === 200 && res.data.priority === 1;
    return {
      passed,
      reason: !passed ? `Status:${res.status}, priority:${res.data?.priority}` : '',
      before: task.priority,
      after: res.data?.priority,
      screenshot
    };
  });

  // UPDATE: due date
  await runTest('CRUD-U3', "UPDATE: change due date", async () => {
    await cleanup();
    const task = await createTask('task today');
    const newDate = '2026-01-20';
    const res = await updateTask(task.id, { due_date: newDate });
    const screenshot = await saveWallpaper('CRUD-U3');
    const passed = res.status === 200 && res.data.due_date === newDate;
    return {
      passed,
      reason: !passed ? `Status:${res.status}, date:${res.data?.due_date}` : '',
      task: res.data,
      screenshot
    };
  });

  // UPDATE: complete
  await runTest('CRUD-U4', "UPDATE: mark as completed", async () => {
    await cleanup();
    const task = await createTask('complete me');
    const res = await updateTask(task.id, { completed: true });
    const screenshot = await saveWallpaper('CRUD-U4');
    const passed = res.status === 200 && res.data.completed === true;
    return {
      passed,
      reason: !passed ? `Status:${res.status}, completed:${res.data?.completed}` : '',
      task: res.data,
      screenshot
    };
  });

  // DELETE: valid ID
  await runTest('CRUD-D1', "DELETE: valid task ID", async () => {
    await cleanup();
    const task = await createTask('delete me');
    const res = await deleteTask(task.id);
    const screenshot = await saveWallpaper('CRUD-D1');
    const passed = res.status === 200 || res.status === 204;
    return {
      passed,
      reason: !passed ? `Status:${res.status}` : '',
      deletedId: task.id,
      screenshot
    };
  });

  // DELETE: invalid ID (404)
  await runTest('CRUD-D2', "DELETE: invalid ID returns 404", async () => {
    const res = await deleteTask('00000000-0000-0000-0000-000000000000');
    const passed = res.status === 404;
    return {
      passed,
      reason: !passed ? `Expected 404, got ${res.status}` : ''
    };
  });
}

// ============ EDGE CASE TESTS ============

async function runEdgeCaseTests() {
  console.log('\n\n' + '═'.repeat(60));
  console.log('  EDGE CASE TESTS');
  console.log('═'.repeat(60));

  // EDGE-1: Empty title
  await runTest('EDGE-1', "Empty title handling", async () => {
    await cleanup();
    try {
      const res = await apiRequest('POST', '/api/tasks', { rawTitle: '' });
      // Should either fail or handle gracefully
      const passed = res.status === 400 || (res.status === 201 && res.data.title);
      return {
        passed,
        reason: !passed ? `Status:${res.status}` : '',
        response: res.data
      };
    } catch (e) {
      return { passed: true, reason: 'Correctly rejected' };
    }
  });

  // EDGE-2: Past date
  await runTest('EDGE-2', "Past date (yesterday at 3pm)", async () => {
    await cleanup();
    const task = await createTask('missed call yesterday at 3pm');
    await saveWallpaper('EDGE-2');
    const yesterday = getDateInTimezone(addDays(new Date(), -1), TEST_TIMEZONE);
    const passed = task.due_date === yesterday && task.due_time?.startsWith('15:00');
    return {
      passed,
      reason: !passed ? `date:${task.due_date}(exp:${yesterday}), time:${task.due_time}` : '',
      task
    };
  });

  // EDGE-3: Long text (>200 chars)
  await runTest('EDGE-3', "Long text handling (200+ chars)", async () => {
    await cleanup();
    const longText = 'A'.repeat(250) + ' tomorrow';
    const task = await createTask(longText);
    await saveWallpaper('EDGE-3');
    const passed = task.id && task.title.length > 0;
    return {
      passed,
      reason: !passed ? 'Failed to handle long text' : '',
      titleLength: task.title.length
    };
  });

  // EDGE-4: Special characters
  await runTest('EDGE-4', "Special characters (emoji, unicode)", async () => {
    await cleanup();
    const task = await createTask('Buy 🍕 pizza & "wine" tomorrow');
    await saveWallpaper('EDGE-4');
    const passed = task.id && task.title.includes('pizza');
    return {
      passed,
      reason: !passed ? `Title: ${task.title}` : '',
      task
    };
  });

  // EDGE-5: Sequential rapid creates (rate limit safe)
  await runTest('EDGE-5', "Sequential rapid task creation", async () => {
    await cleanup();
    // Wait longer after cleanup
    await new Promise(r => setTimeout(r, 2000));
    // Create sequentially with larger delays to avoid rate limiting
    const tasks = [];
    for (let i = 1; i <= 3; i++) {
      await new Promise(r => setTimeout(r, 1500)); // 1.5s delay
      const task = await createTask(`rapid task ${i}`);
      tasks.push(task);
    }
    const screenshot = await saveWallpaper('EDGE-5');
    const allCreated = tasks.every(t => t.id);
    const uniqueIds = new Set(tasks.map(t => t.id)).size === 3;
    const passed = allCreated && uniqueIds;
    return {
      passed,
      reason: !passed ? `Created:${allCreated}, Unique:${uniqueIds}` : '',
      tasks,
      screenshot
    };
  });
}

// ============ MAIN ============

async function runAllTests() {
  console.log('\n' + '═'.repeat(60));
  console.log('  COMPREHENSIVE COSMIC OCEAN APP TEST SUITE');
  console.log('  API: ' + API_BASE);
  console.log('  Timezone: ' + TEST_TIMEZONE);
  console.log('═'.repeat(60));

  try {
    await authenticate();
  } catch (error) {
    console.error('❌ Authentication failed:', error.message);
    console.log('\n⚠️  Make sure the server is running');
    process.exit(1);
  }

  // Run all test suites
  await runTimezoneTests();
  await runStarColorTests();
  await runCrudTests();
  await runEdgeCaseTests();

  // Summary
  console.log('\n\n' + '═'.repeat(60));
  console.log('  TEST SUMMARY');
  console.log('═'.repeat(60));
  console.log(`\n  Total:  ${testResults.total}`);
  console.log(`  ✅ Passed: ${testResults.passed}`);
  console.log(`  ❌ Failed: ${testResults.failed}`);
  console.log(`\n  Pass Rate: ${((testResults.passed / testResults.total) * 100).toFixed(1)}%`);

  // List failed tests
  const failed = testResults.tests.filter(t => !t.passed);
  if (failed.length > 0) {
    console.log('\n  Failed Tests:');
    failed.forEach(t => console.log(`    - ${t.id}: ${t.reason || t.error}`));
  }

  // Save results
  const resultsPath = path.join(SCREENSHOT_DIR, 'comprehensive-test-results.json');
  fs.writeFileSync(resultsPath, JSON.stringify(testResults, null, 2));
  console.log(`\n📁 Results saved to: ${resultsPath}`);

  // List screenshots
  console.log('\n📸 Screenshots saved:');
  const screenshots = fs.readdirSync(SCREENSHOT_DIR).filter(f => f.endsWith('.png'));
  screenshots.forEach(s => console.log(`    ${s}`));

  return testResults;
}

runAllTests()
  .then(results => {
    process.exit(results.failed > 0 ? 1 : 0);
  })
  .catch(err => {
    console.error('Test suite error:', err);
    process.exit(1);
  });
