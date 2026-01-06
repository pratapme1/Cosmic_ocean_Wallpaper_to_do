/**
 * Test to reproduce the timeout bug
 *
 * Test Scenario: User with 200+ tasks in last 30 days
 * Expected: Message generation completes < 5s
 * Actual (BEFORE fix): Times out at 15s
 *
 * NO-GO RULE: Test BEFORE fixing
 */

// Load environment variables FIRST (before requiring modules that use them)
require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });

const { buildMessageContext } = require('../services/message-generator-llm');
const pool = require('../db/pool');

async function createTestUser() {
  const client = await pool.connect();

  try {
    // Create test user
    const userResult = await client.query(
      `INSERT INTO users (email, password_hash)
       VALUES ($1, $2)
       ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email
       RETURNING id`,
      ['timeout-test@example.com', 'test123']
    );
    const userId = userResult.rows[0].id;

    // Create 200 tasks over last 30 days (simulating active user)
    console.log('Creating 200 test tasks over 30 days...');

    for (let i = 0; i < 200; i++) {
      const daysAgo = Math.floor(i / 7); // Spread over 30 days
      const completed = i < 150; // 150 completed, 50 pending

      await client.query(
        `INSERT INTO tasks (
          user_id, title, priority, category, completed,
          created_at, completed_at
        ) VALUES ($1, $2, $3, $4, $5, NOW() - INTERVAL '${daysAgo} days', $6)`,
        [
          userId,
          `Test task ${i}`,
          Math.floor(Math.random() * 3) + 1,
          ['work', 'personal', 'health'][i % 3],
          completed,
          completed ? `NOW() - INTERVAL '${daysAgo} days'` : null
        ]
      );
    }

    console.log(`✅ Created user with 200 tasks (ID: ${userId})`);
    return userId;

  } finally {
    client.release();
  }
}

async function testMessageGenerationPerformance(userId) {
  console.log('\n=== Testing Message Generation Performance ===\n');

  // Test 1: Measure buildMessageContext (the slow part)
  console.log('Test 1: buildMessageContext() with 200 tasks');

  const startTime = Date.now();

  try {
    const context = await buildMessageContext(userId);
    const duration = Date.now() - startTime;

    console.log(`✅ Completed in ${duration}ms`);
    console.log(`   - Pending: ${context.pendingCount}`);
    console.log(`   - Completed today: ${context.completedToday}`);
    console.log(`   - Streak: ${context.stats.streakDays} days`);
    console.log(`   - Total completed: ${context.stats.totalCompleted}`);

    if (duration > 15000) {
      console.log(`❌ TIMEOUT: Exceeded 15s Vercel limit (${duration}ms)`);
      return false;
    } else if (duration > 5000) {
      console.log(`⚠️  SLOW: Exceeded 5s target (${duration}ms)`);
      return false;
    } else {
      console.log(`✅ PASS: Within 5s target`);
      return true;
    }

  } catch (error) {
    const duration = Date.now() - startTime;
    console.log(`❌ ERROR after ${duration}ms: ${error.message}`);
    return false;
  }
}

async function verifyQueryData(userId) {
  console.log('\n=== Verifying Query Returns Expected Data ===\n');

  const client = await pool.connect();

  try {
    // Test the problematic query
    const query = `
      SELECT *
      FROM tasks
      WHERE user_id = $1
      AND (archived = false OR archived IS NULL)
      AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
    `;

    const startTime = Date.now();
    const result = await client.query(query, [userId]);
    const duration = Date.now() - startTime;

    console.log(`Query returned ${result.rows.length} rows in ${duration}ms`);
    console.log(`Average: ${(duration / result.rows.length).toFixed(2)}ms per row`);

    // Check data quality
    const completed = result.rows.filter(t => t.completed).length;
    const pending = result.rows.filter(t => !t.completed).length;

    console.log(`  - Completed: ${completed}`);
    console.log(`  - Pending: ${pending}`);

    // Verify expected vs actual
    console.log('\n✅ Expected: ~200 tasks (150 completed, 50 pending)');
    console.log(`✅ Actual: ${result.rows.length} tasks (${completed} completed, ${pending} pending)`);

    if (Math.abs(result.rows.length - 200) < 10 && Math.abs(completed - 150) < 10) {
      console.log('✅ PASS: Data matches expected values');
      return true;
    } else {
      console.log('❌ FAIL: Data does not match expected values');
      return false;
    }

  } finally {
    client.release();
  }
}

async function cleanup(userId) {
  console.log('\n=== Cleanup ===\n');

  const client = await pool.connect();

  try {
    await client.query('DELETE FROM tasks WHERE user_id = $1', [userId]);
    await client.query('DELETE FROM users WHERE id = $1', [userId]);
    console.log('✅ Cleaned up test data');
  } finally {
    client.release();
  }
}

async function runTests() {
  console.log('='.repeat(60));
  console.log('TIMEOUT BUG TEST - BEFORE FIX');
  console.log('='.repeat(60));

  let userId;

  try {
    // Step 1: Create test user with realistic data
    userId = await createTestUser();

    // Step 2: Verify query returns expected data
    const dataValid = await verifyQueryData(userId);

    // Step 3: Test performance (EXPECT to fail before fix)
    const perfGood = await testMessageGenerationPerformance(userId);

    // Summary
    console.log('\n' + '='.repeat(60));
    console.log('TEST SUMMARY');
    console.log('='.repeat(60));
    console.log(`Data Quality: ${dataValid ? '✅ PASS' : '❌ FAIL'}`);
    console.log(`Performance: ${perfGood ? '✅ PASS' : '❌ FAIL (EXPECTED BEFORE FIX)'}`);
    console.log('='.repeat(60));

  } catch (error) {
    console.error('Test failed:', error);
  } finally {
    if (userId) {
      await cleanup(userId);
    }
    await pool.end();
  }
}

// Run tests
runTests().catch(console.error);
