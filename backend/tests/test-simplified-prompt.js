/**
 * Test the simplified LLM prompt
 */
require('dotenv').config({ path: require('path').join(__dirname, '../../.env') });

const { parseLLM } = require('../utils/llm-task-parser');

async function runTests() {
  console.log('=== Testing Simplified LLM Prompt ===');
  console.log('Today:', new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' }));
  console.log('Time:', new Date().toLocaleTimeString('en-GB', { timeZone: 'Asia/Kolkata', hour: '2-digit', minute: '2-digit' }));
  console.log('');

  const tests = [
    // The user's specific case
    { input: 'jumping into conversation for a project tomorrow', expectDate: 'tomorrow', expectPriority: 2 },

    // Core timezone tests
    { input: 'meeting tomorrow', expectDate: 'tomorrow', expectPriority: 2 },
    { input: 'missed call yesterday at 3pm', expectDate: 'yesterday', expectTime: '15:00' },
    { input: 'call mom now', expectPriority: 1 },
    { input: 'remind me in 30 min', expectPriority: 1 },
    { input: 'buy groceries', expectDate: null, expectPriority: 2 },
    { input: 'urgent meeting', expectPriority: 1 },
    { input: 'someday learn piano', expectPriority: 3 },
  ];

  let passed = 0;
  let failed = 0;

  for (const test of tests) {
    process.stdout.write(`Testing: "${test.input}"... `);
    const result = await parseLLM(test.input, 'Asia/Kolkata');

    let ok = true;
    let issues = [];

    // Check priority
    if (test.expectPriority && result.priority !== test.expectPriority) {
      ok = false;
      issues.push(`priority=${result.priority} (expected ${test.expectPriority})`);
    }

    // Check date exists when expected
    if (test.expectDate === 'tomorrow' && !result.due_date) {
      ok = false;
      issues.push('no due_date (expected tomorrow)');
    }
    if (test.expectDate === 'yesterday' && !result.due_date) {
      ok = false;
      issues.push('no due_date (expected yesterday)');
    }

    // Check time exists when expected
    if (test.expectTime && result.due_time !== test.expectTime) {
      ok = false;
      issues.push(`due_time=${result.due_time} (expected ${test.expectTime})`);
    }

    if (ok) {
      console.log('✅ PASS');
      console.log(`   → date: ${result.due_date} | time: ${result.due_time} | priority: ${result.priority}`);
      passed++;
    } else {
      console.log('❌ FAIL:', issues.join(', '));
      console.log(`   → date: ${result.due_date} | time: ${result.due_time} | priority: ${result.priority}`);
      failed++;
    }
  }

  console.log('');
  console.log('=== Summary ===');
  console.log(`Passed: ${passed}`);
  console.log(`Failed: ${failed}`);

  return failed === 0;
}

runTests()
  .then(success => process.exit(success ? 0 : 1))
  .catch(e => { console.error(e); process.exit(1); });
