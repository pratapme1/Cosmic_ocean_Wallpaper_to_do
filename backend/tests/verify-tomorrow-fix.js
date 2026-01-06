/**
 * VERIFY TOMORROW FIX - NO-GO Compliance
 *
 * Test that the fix correctly stores tomorrow's date
 */

const { parseTask } = require('../utils/task-parser');

// Copy the FIXED parseDateTimeForDB from server.js
function parseDateTimeForDB(value) {
  if (!value || value === '' || value === 'null') return { date: null, time: null };

  let dateObj;

  if (value instanceof Date) {
    dateObj = value;
  } else if (typeof value === 'number' || (typeof value === 'string' && /^\d{10,13}$/.test(value))) {
    const ms = typeof value === 'number' ? value : parseInt(value, 10);
    dateObj = new Date(ms);
  } else if (typeof value === 'string') {
    dateObj = new Date(value);
  }

  if (!dateObj || isNaN(dateObj.getTime())) {
    console.warn(`parseDateTimeForDB: Invalid date value: ${value}`);
    return { date: null, time: null };
  }

  // FIXED: Extract date part using LOCAL time, not UTC
  const year = dateObj.getFullYear();
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getDate()).padStart(2, '0');
  const dateStr = `${year}-${month}-${day}`;

  // Extract time part using LOCAL time
  const hours = dateObj.getHours();
  const minutes = dateObj.getMinutes();
  const seconds = dateObj.getSeconds();

  let timeStr = null;
  if (hours !== 0 || minutes !== 0 || seconds !== 0) {
    timeStr = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  return { date: dateStr, time: timeStr };
}

console.log('========================================');
console.log('VERIFY FIX: Tomorrow Date Storage');
console.log('========================================\n');

// Test with EXACT user inputs
const testCases = [
  'speak to Anand tomorrow',
  'complete record tomorrow',
  'update resume tomorrow'
];

const now = new Date();
const tomorrow = new Date(now);
tomorrow.setDate(tomorrow.getDate() + 1);
const expectedDateStr = tomorrow.toISOString().split('T')[0];

console.log(`Current date: ${now.toISOString().split('T')[0]}`);
console.log(`Expected tomorrow: ${expectedDateStr}\n`);

let allPassed = true;

testCases.forEach((input, i) => {
  console.log(`Test ${i + 1}: "${input}"`);

  // Parse with NLP
  const parsed = parseTask(input);
  console.log(`  Parser returns: ${parsed.dueDate ? new Date(parsed.dueDate).toISOString() : 'null'}`);

  // Convert to DB format
  const dbFormat = parseDateTimeForDB(parsed.dueDate);
  console.log(`  DB will store: ${dbFormat.date}`);
  console.log(`  Expected:      ${expectedDateStr}`);

  const passed = dbFormat.date === expectedDateStr;
  console.log(`  Status: ${passed ? '✅ PASS' : '❌ FAIL'}`);
  console.log('');

  if (!passed) allPassed = false;
});

console.log('========================================');
console.log('RESULT');
console.log('========================================\n');

if (allPassed) {
  console.log('✅ ALL TESTS PASSED - Fix is correct!');
  console.log('');
  console.log('Tomorrow tasks will now be stored with correct date.');
  console.log('Ready for deployment.');
} else {
  console.log('❌ SOME TESTS FAILED - Fix needs more work');
  process.exit(1);
}
