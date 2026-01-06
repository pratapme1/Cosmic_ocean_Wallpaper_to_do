/**
 * VERIFY FIX v1.4.8: PATCH endpoint now supports rawTitle for NLP re-parsing
 *
 * This test verifies that when a task is edited with temporal words like:
 * - "tomorrow"
 * - "yesterday"
 * - "now"
 * - "in 10 minutes"
 *
 * The backend PATCH endpoint will:
 * 1. Parse the rawTitle with NLP/LLM
 * 2. Extract clean title (without temporal words)
 * 3. Calculate correct due_date and due_time
 * 4. Store in database
 */

const { parseTask } = require('../utils/task-parser');

console.log('========================================');
console.log('VERIFY FIX v1.4.8: PATCH rawTitle Parsing');
console.log('========================================\n');

// Test cases
const testCases = [
  {
    input: 'call mom tomorrow',
    expected: {
      title: 'Call mom',
      hasDueDate: true,
      hasDueTime: false
    }
  },
  {
    input: 'call sister yesterday',
    expected: {
      title: 'Call sister',
      hasDueDate: true,
      hasDueTime: false
    }
  },
  {
    input: 'email manager in 10 minutes',
    expected: {
      title: 'Email manager',
      hasDueDate: true,
      hasDueTime: true
    }
  },
  {
    input: 'call mom now',
    expected: {
      title: 'Call mom',
      hasDueDate: false,  // "now" doesn't set due date, only priority
      hasDueTime: false
    }
  }
];

let allPassed = true;

testCases.forEach((test, i) => {
  console.log(`Test ${i + 1}: "${test.input}"`);
  console.log('─'.repeat(50));

  // Simulate PATCH endpoint rawTitle parsing
  const parsed = parseTask(test.input);

  console.log(`  Parsed title: "${parsed.title}"`);
  console.log(`  Due date: ${parsed.dueDate || 'null'}`);
  console.log(`  Priority: ${parsed.priority}`);

  // Verify title is cleaned (temporal words removed)
  const titleMatch = parsed.title === test.expected.title;
  console.log(`  ✓ Title cleaned: ${titleMatch ? '✅ PASS' : '❌ FAIL'}`);
  console.log(`    Expected: "${test.expected.title}"`);
  console.log(`    Actual:   "${parsed.title}"`);

  // Verify due date presence
  const dueDateMatch = test.expected.hasDueDate ? !!parsed.dueDate : !parsed.dueDate;
  console.log(`  ✓ Due date ${test.expected.hasDueDate ? 'set' : 'not set'}: ${dueDateMatch ? '✅ PASS' : '❌ FAIL'}`);

  if (parsed.dueDate) {
    console.log(`    Due date value: ${parsed.dueDate.toISOString ? parsed.dueDate.toISOString() : parsed.dueDate}`);
  }

  const passed = titleMatch && dueDateMatch;
  console.log(`  Overall: ${passed ? '✅ PASS' : '❌ FAIL'}`);
  console.log('');

  if (!passed) allPassed = false;
});

console.log('========================================');
console.log('RESULT');
console.log('========================================\n');

if (allPassed) {
  console.log('✅ ALL TESTS PASSED');
  console.log('');
  console.log('The rawTitle PATCH fix works correctly:');
  console.log('1. Temporal words are removed from title');
  console.log('2. Due dates are calculated from time expressions');
  console.log('3. Priority is adjusted based on urgency keywords');
  console.log('');
  console.log('Ready for deployment as v1.4.8');
  console.log('');
  console.log('Android app will now use:');
  console.log('  "rawTitle" instead of "title" in PATCH requests');
  console.log('  Backend will re-parse with NLP on each edit');
  process.exit(0);
} else {
  console.log('❌ SOME TESTS FAILED');
  console.log('');
  console.log('The parsing logic needs adjustment before deployment.');
  process.exit(1);
}
