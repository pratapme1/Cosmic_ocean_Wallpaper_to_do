/**
 * Test Tomorrow Bug - NO-GO Compliance
 *
 * Test what the parsers return for "tomorrow" tasks
 */

const { parseTask } = require('../utils/task-parser');

console.log('========================================');
console.log('TESTING "TOMORROW" BUG - Current Behavior');
console.log('========================================\n');

// Test 1: Local parser with "speak to Anand tomorrow"
console.log('Test 1: Local NLP Parser');
console.log('Input: "speak to Anand tomorrow"\n');

const result1 = parseTask('speak to Anand tomorrow');
console.log('Result:');
console.log(JSON.stringify(result1, null, 2));

console.log('\n---\n');

// Test 2: Check what date it returns
if (result1.dueDate) {
  const dueDate = new Date(result1.dueDate);
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);

  console.log('Date Analysis:');
  console.log(`Now (IST):      ${now.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' })}`);
  console.log(`Parsed Date:    ${dueDate.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' })}`);
  console.log(`Expected (Tomorrow): ${tomorrow.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' })}`);
  console.log('');

  const parsedDateStr = dueDate.toISOString().split('T')[0];
  const tomorrowStr = tomorrow.toISOString().split('T')[0];

  console.log(`Parsed Date String:   ${parsedDateStr}`);
  console.log(`Expected Date String: ${tomorrowStr}`);
  console.log(`Match: ${parsedDateStr === tomorrowStr ? '✅' : '❌'}`);
}

console.log('\n========================================');
console.log('Test 2: Database Storage Format');
console.log('========================================\n');

// Test parseDateTimeForDB function
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

  // Extract date part (YYYY-MM-DD) using UTC
  const year = dateObj.getUTCFullYear();
  const month = String(dateObj.getUTCMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getUTCDate()).padStart(2, '0');
  const dateStr = `${year}-${month}-${day}`;

  const hours = dateObj.getUTCHours();
  const minutes = dateObj.getUTCMinutes();
  const seconds = dateObj.getUTCSeconds();

  let timeStr = null;
  if (hours !== 0 || minutes !== 0 || seconds !== 0) {
    timeStr = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  return { date: dateStr, time: timeStr };
}

if (result1.dueDate) {
  console.log('Input to parseDateTimeForDB:', result1.dueDate);
  const dbFormat = parseDateTimeForDB(result1.dueDate);
  console.log('Database format:', dbFormat);
  console.log('');

  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  const expectedDateStr = tomorrow.toISOString().split('T')[0];

  console.log(`Expected in DB: ${expectedDateStr}`);
  console.log(`Actual in DB:   ${dbFormat.date}`);
  console.log(`Match: ${dbFormat.date === expectedDateStr ? '✅' : '❌'}`);
}

console.log('\n========================================');
console.log('CONCLUSION');
console.log('========================================\n');

if (result1.dueDate) {
  const dueDate = new Date(result1.dueDate);
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);

  const parsedDateStr = dueDate.toISOString().split('T')[0];
  const tomorrowStr = tomorrow.toISOString().split('T')[0];
  const dbFormat = parseDateTimeForDB(result1.dueDate);

  if (parsedDateStr === tomorrowStr && dbFormat.date === tomorrowStr) {
    console.log('✅ Parser is CORRECT - Returns tomorrow\'s date');
  } else {
    console.log('❌ BUG FOUND:');
    console.log(`   Parser returns: ${parsedDateStr}`);
    console.log(`   Expected:       ${tomorrowStr}`);
    console.log(`   DB stores:      ${dbFormat.date}`);
  }
}
