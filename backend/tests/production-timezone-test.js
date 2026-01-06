/**
 * PRODUCTION TIMEZONE TEST
 *
 * NO-GO RULE: Test in production BEFORE claiming deployment is complete
 *
 * This test verifies:
 * 1. Is LLM enabled in production?
 * 2. Does timezone fix work in production?
 * 3. Does "call mom now" return Priority 1?
 * 4. Does "in 10 minutes" return correct due_time?
 */

const https = require('https');

const PRODUCTION_URL = 'https://cosmic-ocean-api.vercel.app';

// Test user credentials (from previous tests)
const TEST_EMAIL = 'test-epic9@test.com';
const TEST_PASSWORD = 'testpass123';

console.log('='.repeat(80));
console.log('PRODUCTION TIMEZONE TEST - Verifying v1.4.2 Deployment');
console.log('='.repeat(80));
console.log();

/**
 * Make HTTP request
 */
function makeRequest(method, path, data = null, token = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, PRODUCTION_URL);

    const options = {
      method,
      headers: {
        'Content-Type': 'application/json'
      }
    };

    if (token) {
      options.headers['Authorization'] = `Bearer ${token}`;
    }

    const req = https.request(url, options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          resolve({ status: res.statusCode, data: json });
        } catch (e) {
          resolve({ status: res.statusCode, data: body });
        }
      });
    });

    req.on('error', reject);

    if (data) {
      req.write(JSON.stringify(data));
    }

    req.end();
  });
}

/**
 * Test 1: Check production environment
 */
async function testProductionEnvironment() {
  console.log('Test 1: Production Environment Check');
  console.log('-'.repeat(80));

  try {
    const { data } = await makeRequest('GET', '/api/health');

    console.log('Version:', data.version);
    console.log('DB Mode:', data.mode);
    console.log('Environment Keys:', data.env.dbKeys || []);
    console.log();

    if (data.version !== '1.4.2') {
      console.log('❌ FAIL: Production is not v1.4.2!');
      console.log(`   Expected: 1.4.2, Got: ${data.version}`);
      return false;
    }

    console.log('✅ Version is 1.4.2');

    // Check if LLM env vars are mentioned
    const envKeys = data.env.dbKeys || [];
    const hasLLMKey = envKeys.includes('ANTHROPIC_API_KEY');
    const hasLLMEnabled = envKeys.includes('ENABLE_LLM_PARSING');

    if (!hasLLMKey && !hasLLMEnabled) {
      console.log('⚠️ WARNING: No LLM environment variables detected');
      console.log('   This means LLM parsing is likely DISABLED');
      console.log('   Tasks will use fallback parser');
      console.log('   Timezone fix code exists but LLM won\'t use it');
    } else {
      console.log('✅ LLM environment variables detected');
    }

    return true;

  } catch (error) {
    console.error('❌ ERROR:', error.message);
    return false;
  }
}

/**
 * Test 2: Login and get token
 */
async function testLogin() {
  console.log();
  console.log('Test 2: Login to get token');
  console.log('-'.repeat(80));

  try {
    const { status, data } = await makeRequest('POST', '/api/auth/login', {
      email: TEST_EMAIL,
      password: TEST_PASSWORD
    });

    if (status === 200 && data.token) {
      console.log('✅ Login successful');
      console.log('   User ID:', data.user?.id);
      console.log('   Timezone:', data.user?.timezone || 'NOT SET');
      return data.token;
    } else {
      console.log('❌ Login failed:', data.error || 'Unknown error');
      console.log('   This test requires existing user. Try registering first.');
      return null;
    }

  } catch (error) {
    console.error('❌ ERROR:', error.message);
    return null;
  }
}

/**
 * Test 3: Parse "call mom now" - should be Priority 1
 */
async function testCallMomNow(token) {
  console.log();
  console.log('Test 3: Parse "call mom now" (should be Priority 1)');
  console.log('-'.repeat(80));

  try {
    const { status, data } = await makeRequest('POST', '/api/tasks/parse-llm', {
      title: 'call mom now'
    }, token);

    if (status === 200) {
      console.log('Response:', JSON.stringify(data.parsed, null, 2));
      console.log();

      const priority = data.parsed.priority;
      const source = data.parsed.source;

      console.log('Source:', source);
      console.log('Priority:', priority);

      if (source.includes('llm')) {
        console.log('✅ LLM parsing is ENABLED');
      } else {
        console.log('⚠️ Using fallback parser (LLM disabled)');
      }

      if (priority === 1) {
        console.log('✅ PASS: Priority is 1 (HIGH)');
        return true;
      } else {
        console.log('❌ FAIL: Priority is', priority, '(expected 1)');
        console.log('   "now" should trigger HIGH priority');
        return false;
      }

    } else {
      console.log('❌ Parse failed:', status, data);
      return false;
    }

  } catch (error) {
    console.error('❌ ERROR:', error.message);
    return false;
  }
}

/**
 * Test 4: Parse "in 10 minutes" - should have due_time
 */
async function testIn10Minutes(token) {
  console.log();
  console.log('Test 4: Parse "email manager in 10 minutes" (should have due_time)');
  console.log('-'.repeat(80));

  try {
    const { status, data } = await makeRequest('POST', '/api/tasks/parse-llm', {
      title: 'email manager in 10 minutes'
    }, token);

    if (status === 200) {
      console.log('Response:', JSON.stringify(data.parsed, null, 2));
      console.log();

      const dueTime = data.parsed.due_time || data.parsed.dueTime;
      const source = data.parsed.source;

      console.log('Source:', source);
      console.log('Due Time:', dueTime || 'null');

      if (dueTime) {
        console.log('✅ PASS: due_time is set:', dueTime);
        console.log('   Timezone fix would apply if LLM is enabled');
        return true;
      } else {
        console.log('⚠️ SKIP: due_time is null');
        if (source.includes('fallback')) {
          console.log('   Fallback parser doesn\'t parse "in 10 minutes" correctly');
          console.log('   This is expected - need LLM enabled for this feature');
        }
        return false;
      }

    } else {
      console.log('❌ Parse failed:', status, data);
      return false;
    }

  } catch (error) {
    console.error('❌ ERROR:', error.message);
    return false;
  }
}

/**
 * Test 5: Create actual task
 */
async function testCreateTask(token) {
  console.log();
  console.log('Test 5: Create task "call mom now"');
  console.log('-'.repeat(80));

  try {
    const { status, data } = await makeRequest('POST', '/api/tasks', {
      rawTitle: 'call mom now'
    }, token);

    if (status === 201) {
      console.log('✅ Task created successfully');
      console.log('   Task ID:', data.task.id);
      console.log('   Title:', data.task.title);
      console.log('   Priority:', data.task.priority);
      console.log('   Category:', data.task.category);

      if (data.task.priority === 1) {
        console.log('✅ PASS: Priority is 1 (correct)');
        return true;
      } else {
        console.log('❌ FAIL: Priority is', data.task.priority, '(expected 1)');
        return false;
      }

    } else {
      console.log('❌ Task creation failed:', status, data);
      return false;
    }

  } catch (error) {
    console.error('❌ ERROR:', error.message);
    return false;
  }
}

/**
 * Main test execution
 */
async function runTests() {
  console.log('Starting production tests...');
  console.log();

  const results = {
    environment: false,
    login: false,
    callMomNow: false,
    in10Minutes: false,
    createTask: false
  };

  // Test 1: Environment
  results.environment = await testProductionEnvironment();

  // Test 2: Login
  const token = await testLogin();
  if (!token) {
    console.log();
    console.log('❌ STOPPED: Cannot continue without token');
    console.log('   To fix: Register test user or use existing credentials');
    return;
  }
  results.login = true;

  // Test 3: Parse "call mom now"
  results.callMomNow = await testCallMomNow(token);

  // Test 4: Parse "in 10 minutes"
  results.in10Minutes = await testIn10Minutes(token);

  // Test 5: Create task
  results.createTask = await testCreateTask(token);

  // Summary
  console.log();
  console.log('='.repeat(80));
  console.log('TEST SUMMARY');
  console.log('='.repeat(80));
  console.log();

  const passed = Object.values(results).filter(r => r === true).length;
  const total = Object.values(results).length;

  console.log(`Environment Check: ${results.environment ? '✅' : '❌'}`);
  console.log(`Login:             ${results.login ? '✅' : '❌'}`);
  console.log(`"call mom now":    ${results.callMomNow ? '✅' : '❌'}`);
  console.log(`"in 10 minutes":   ${results.in10Minutes ? '✅' : '⚠️ (LLM needed)'}`);
  console.log(`Create Task:       ${results.createTask ? '✅' : '❌'}`);
  console.log();
  console.log(`Passed: ${passed}/${total}`);
  console.log();

  if (!results.in10Minutes) {
    console.log('⚠️ CRITICAL ISSUE IDENTIFIED:');
    console.log('   LLM parsing is likely DISABLED in production');
    console.log('   ANTHROPIC_API_KEY or ENABLE_LLM_PARSING not set in Vercel');
    console.log();
    console.log('   This means:');
    console.log('   - Timezone fix code exists but is NOT being used');
    console.log('   - "in 10 minutes" does NOT parse correctly');
    console.log('   - Fallback parser is used (limited functionality)');
    console.log();
    console.log('   TO FIX:');
    console.log('   1. Go to Vercel dashboard');
    console.log('   2. Add environment variables:');
    console.log('      ANTHROPIC_API_KEY=<your-key>');
    console.log('      ENABLE_LLM_PARSING=true');
    console.log('   3. Redeploy');
  }

  if (!results.callMomNow || !results.createTask) {
    console.log('❌ PRIORITY ISSUE:');
    console.log('   "call mom now" is NOT returning Priority 1');
    console.log('   This is unexpected - fallback parser should handle "now"');
    console.log('   Need to investigate why priority is not being set correctly');
  }

  console.log('='.repeat(80));
}

// Run tests
runTests().catch(console.error);
