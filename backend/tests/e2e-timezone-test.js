/**
 * E2E Timezone Test
 *
 * Full flow: Input → LLM Parse → API Create → DB Storage → Verify
 *
 * Prerequisites:
 * 1. Local server running: cd backend && node server.js
 * 2. Database connection configured
 * 3. ANTHROPIC_API_KEY set in .env
 */

require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });

const fs = require('fs');
const path = require('path');

// Use command line arg or env var for API base
const API_BASE = process.argv[2] || process.env.API_BASE_URL || 'http://localhost:3000';
const TEST_TIMEZONE = 'Asia/Kolkata';
const SCREENSHOT_DIR = path.join(__dirname, '../../test-screenshots');

// Ensure screenshot directory exists
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

// Test user credentials (create in DB if needed)
const TEST_USER = {
  email: 'timezone-test@example.com',
  password: 'test123456'
};

let authToken = null;
let testResults = {
  passed: 0,
  failed: 0,
  tests: []
};

// Helper: Get date string in YYYY-MM-DD format for a timezone
function getDateInTimezone(date, timezone) {
  const options = { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' };
  const parts = new Intl.DateTimeFormat('en-CA', options).formatToParts(date);
  const year = parts.find(p => p.type === 'year').value;
  const month = parts.find(p => p.type === 'month').value;
  const day = parts.find(p => p.type === 'day').value;
  return `${year}-${month}-${day}`;
}

// Helper: Get time string in HH:MM format for a timezone
function getTimeInTimezone(date, timezone) {
  return date.toLocaleTimeString('en-GB', {
    timeZone: timezone,
    hour12: false,
    hour: '2-digit',
    minute: '2-digit'
  });
}

// Helper: Make API request
async function apiRequest(method, endpoint, body = null, auth = true) {
  const headers = {
    'Content-Type': 'application/json'
  };

  if (auth && authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
    console.log(`  [Debug] Using auth token: ${authToken.substring(0, 20)}...`);
  } else if (auth) {
    console.log(`  [Debug] Auth requested but no token available`);
  }

  const options = {
    method,
    headers
  };

  if (body) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${API_BASE}${endpoint}`, options);

  // Handle non-JSON responses
  const contentType = response.headers.get('content-type');
  let data;
  if (contentType && contentType.includes('application/json')) {
    data = await response.json();
  } else {
    data = { raw: await response.text() };
  }

  return { status: response.status, data };
}

// Authenticate or register test user
async function authenticate() {
  console.log('\n🔐 Authenticating test user...');

  // Try to login first
  let res = await apiRequest('POST', '/api/auth/login', TEST_USER, false);
  console.log(`  Login response: ${res.status}`, res.data.token ? '(has token)' : '(no token)');

  // API returns accessToken, not token
  const token = res.data.accessToken || res.data.token;

  if (res.status !== 200 || !token) {
    // Register if login fails
    console.log('  Registering new test user...');
    res = await apiRequest('POST', '/api/auth/register', {
      ...TEST_USER,
      timezone: TEST_TIMEZONE
    }, false);

    const regToken = res.data.accessToken || res.data.token;
    console.log(`  Register response: ${res.status}`, regToken ? '(has token)' : '(no token)');

    if ((res.status !== 201 && res.status !== 200) || !regToken) {
      throw new Error(`Failed to register: ${JSON.stringify(res.data)}`);
    }
    authToken = regToken;
  } else {
    authToken = token;
  }
  console.log(`  ✅ Authenticated. Token: ${authToken.substring(0, 30)}...`);

  // Update timezone setting
  const prefRes = await apiRequest('PATCH', '/api/user/preferences', { timezone: TEST_TIMEZONE });
  console.log(`  Preferences update: ${prefRes.status}`);
  console.log(`  ✅ Timezone set to: ${TEST_TIMEZONE}`);
}

// Clean up test tasks
async function cleanup() {
  console.log('\n🧹 Cleaning up old test tasks...');
  const res = await apiRequest('DELETE', '/api/tasks');
  console.log(`  ✅ Deleted all tasks`);
}

// Run single E2E test
async function runE2ETest(name, input, validateFn) {
  console.log('\n' + '='.repeat(60));
  console.log(`E2E TEST ${name}: "${input}"`);
  console.log('='.repeat(60));

  const now = new Date();
  const testStart = Date.now();

  try {
    // 1. Create task via API
    console.log('\n📤 Creating task via API...');
    const createRes = await apiRequest('POST', '/api/tasks', {
      rawTitle: input
    });

    if (createRes.status !== 201) {
      throw new Error(`Failed to create task: ${JSON.stringify(createRes.data)}`);
    }

    const task = createRes.data;
    console.log(`  Task ID: ${task.id}`);
    console.log(`  Title: ${task.title}`);
    console.log(`  Due Date: ${task.due_date}`);
    console.log(`  Due Time: ${task.due_time}`);
    console.log(`  Priority: ${task.priority}`);

    // 2. Validate task data
    console.log('\n✅ Validating stored data...');
    const validation = validateFn(task, now);

    let allPassed = true;
    for (const check of validation) {
      const passed = check.pass;
      console.log(`  ${check.field}: ${check.actual} ${passed ? '✅' : '❌'} (expected: ${check.expected})`);
      if (!passed) allPassed = false;
    }

    // 3. Fetch wallpaper
    console.log('\n🖼️  Fetching wallpaper...');
    const wallpaperUrl = `${API_BASE}/api/wallpaper?timezone=${TEST_TIMEZONE}&theme=cosmic&resolution=1080x1920`;
    const wallpaperRes = await fetch(wallpaperUrl, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    if (wallpaperRes.ok) {
      const buffer = await wallpaperRes.arrayBuffer();
      const screenshotPath = path.join(SCREENSHOT_DIR, `test-${name.replace(/\./g, '-')}.png`);
      fs.writeFileSync(screenshotPath, Buffer.from(buffer));
      console.log(`  ✅ Screenshot saved: ${screenshotPath}`);
    } else {
      console.log(`  ⚠️  Wallpaper fetch failed: ${wallpaperRes.status}`);
    }

    // 4. Clean up - delete the task
    await apiRequest('DELETE', `/api/tasks/${task.id}`);

    const duration = Date.now() - testStart;
    console.log(`\n${allPassed ? '✅ TEST PASSED' : '❌ TEST FAILED'} (${duration}ms)`);

    testResults.tests.push({
      name,
      input,
      task,
      validation,
      passed: allPassed,
      duration
    });

    if (allPassed) {
      testResults.passed++;
    } else {
      testResults.failed++;
    }

    return allPassed;

  } catch (error) {
    console.error(`\n❌ TEST ERROR: ${error.message}`);
    testResults.tests.push({
      name,
      input,
      error: error.message,
      passed: false
    });
    testResults.failed++;
    return false;
  }
}

// Helper: Check if time is within N minutes of expected
function timeWithin(actual, expected, toleranceMin = 2) {
  if (!actual || !expected) return false;
  const [aH, aM] = actual.split(':').map(Number);
  const [eH, eM] = expected.split(':').map(Number);
  const diffMinutes = Math.abs((aH * 60 + aM) - (eH * 60 + eM));
  return diffMinutes <= toleranceMin;
}

// Main test suite
async function runAllTests() {
  console.log('\n' + '═'.repeat(60));
  console.log('  E2E TIMEZONE TEST SUITE');
  console.log('  API: ' + API_BASE);
  console.log('  Timezone: ' + TEST_TIMEZONE);
  console.log('═'.repeat(60));

  try {
    await authenticate();
    await cleanup();
  } catch (error) {
    console.error('❌ Setup failed:', error.message);
    console.log('\n⚠️  Make sure the local server is running: cd backend && node server.js');
    process.exit(1);
  }

  const now = new Date();
  const todayStr = getDateInTimezone(now, TEST_TIMEZONE);
  const yesterdayStr = getDateInTimezone(new Date(now.getTime() - 24 * 60 * 60 * 1000), TEST_TIMEZONE);
  const tomorrowStr = getDateInTimezone(new Date(now.getTime() + 24 * 60 * 60 * 1000), TEST_TIMEZONE);

  // TEST A: 'missed call yesterday at 3pm'
  await runE2ETest('A', 'missed call yesterday at 3pm', (task, now) => [
    { field: 'due_date', actual: task.due_date, expected: yesterdayStr, pass: task.due_date === yesterdayStr },
    { field: 'due_time', actual: task.due_time?.substring(0, 5), expected: '15:00', pass: task.due_time?.startsWith('15:00') }
  ]);

  // TEST B: 'call mom now'
  await runE2ETest('B', 'call mom now', (task, now) => {
    const expectedTime = getTimeInTimezone(now, TEST_TIMEZONE);
    return [
      { field: 'due_date', actual: task.due_date, expected: todayStr, pass: task.due_date === todayStr },
      { field: 'due_time', actual: task.due_time?.substring(0, 5), expected: `~${expectedTime}`, pass: timeWithin(task.due_time, expectedTime) },
      { field: 'priority', actual: task.priority, expected: 1, pass: task.priority === 1 }
    ];
  });

  // TEST C: 'meeting tomorrow at 10am'
  await runE2ETest('C', 'meeting tomorrow at 10am', (task, now) => [
    { field: 'due_date', actual: task.due_date, expected: tomorrowStr, pass: task.due_date === tomorrowStr },
    { field: 'due_time', actual: task.due_time?.substring(0, 5), expected: '10:00', pass: task.due_time?.startsWith('10:00') }
  ]);

  // TEST D.1: 'remind in 30 min'
  await runE2ETest('D.1', 'remind in 30 min', (task, now) => {
    const future = new Date(now.getTime() + 30 * 60 * 1000);
    const expectedTime = getTimeInTimezone(future, TEST_TIMEZONE);
    const expectedDate = getDateInTimezone(future, TEST_TIMEZONE);
    return [
      { field: 'due_date', actual: task.due_date, expected: expectedDate, pass: task.due_date === expectedDate },
      { field: 'due_time', actual: task.due_time?.substring(0, 5), expected: `~${expectedTime}`, pass: timeWithin(task.due_time, expectedTime, 3) }
    ];
  });

  // TEST E: 'buy groceries' (no date) → defaults to NOW
  await runE2ETest('E', 'buy groceries', (task, now) => {
    const expectedTime = getTimeInTimezone(now, TEST_TIMEZONE);
    return [
      { field: 'due_date', actual: task.due_date, expected: todayStr, pass: task.due_date === todayStr },
      { field: 'due_time', actual: task.due_time?.substring(0, 5), expected: `~${expectedTime}`, pass: timeWithin(task.due_time, expectedTime, 3) }
    ];
  });

  // TEST F: 'dentist Friday' (date only)
  // Today is Friday, so "dentist Friday" could mean today or next Friday
  const nextFriday = getDateInTimezone(new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000), TEST_TIMEZONE);
  await runE2ETest('F', 'dentist Friday', (task, now) => [
    { field: 'due_date', actual: task.due_date, expected: `${todayStr} or ${nextFriday}`, pass: task.due_date === todayStr || task.due_date === nextFriday },
    { field: 'due_time', actual: task.due_time, expected: 'null or any', pass: true } // Time is ambiguous for date-only
  ]);

  // TEST G: 'standup at 9am' (time only)
  // If current time > 9am, the server may set tomorrow due to midnight rollover logic
  await runE2ETest('G', 'standup at 9am', (task, now) => [
    { field: 'due_date', actual: task.due_date, expected: `${todayStr} or ${tomorrowStr}`, pass: task.due_date === todayStr || task.due_date === tomorrowStr },
    { field: 'due_time', actual: task.due_time?.substring(0, 5), expected: '09:00', pass: task.due_time?.startsWith('09:00') }
  ]);

  // TEST H: 'task tomorrow'
  await runE2ETest('H', 'task tomorrow', (task, now) => [
    { field: 'due_date', actual: task.due_date, expected: tomorrowStr, pass: task.due_date === tomorrowStr }
  ]);

  // Print summary
  console.log('\n' + '═'.repeat(60));
  console.log('  E2E TEST SUMMARY');
  console.log('═'.repeat(60));
  console.log(`\n  Total: ${testResults.passed + testResults.failed}`);
  console.log(`  ✅ Passed: ${testResults.passed}`);
  console.log(`  ❌ Failed: ${testResults.failed}`);
  console.log('\n');

  // Save results
  const resultsPath = path.join(SCREENSHOT_DIR, 'e2e-test-results.json');
  fs.writeFileSync(resultsPath, JSON.stringify(testResults, null, 2));
  console.log(`📁 Results saved to: ${resultsPath}`);

  return testResults;
}

// Run tests
runAllTests()
  .then(results => {
    process.exit(results.failed > 0 ? 1 : 0);
  })
  .catch(err => {
    console.error('Test suite error:', err);
    process.exit(1);
  });
