/**
 * PROOF OF TOMORROW BUG
 *
 * Demonstrates the UTC extraction bug in parseDateTimeForDB
 */

console.log('========================================');
console.log('PROOF OF BUG: parseDateTimeForDB');
console.log('========================================\n');

// Simulate what chrono-node returns for "tomorrow" in IST
const now = new Date('2026-01-06T15:30:00+05:30'); // 3:30 PM IST
const tomorrow = new Date('2026-01-07T00:00:00+05:30'); // Midnight tomorrow IST

console.log('User says: "speak to Anand tomorrow"');
console.log('User timezone: Asia/Kolkata (IST = UTC+5:30)');
console.log('Current time: 3:30 PM IST on Jan 6');
console.log('');

console.log('chrono-node returns Date object:');
console.log(`  Local (IST): ${tomorrow.toLocaleString('en-US', { timeZone: 'Asia/Kolkata' })}`);
console.log(`  UTC:         ${tomorrow.toISOString()}`);
console.log('');

// BUGGY parseDateTimeForDB (current code)
function parseDateTimeForDB_BUGGY(value) {
  const dateObj = new Date(value);

  // Extract date using UTC (WRONG!)
  const year = dateObj.getUTCFullYear();
  const month = String(dateObj.getUTCMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getUTCDate()).padStart(2, '0');
  const dateStr = `${year}-${month}-${day}`;

  return { date: dateStr };
}

// FIXED parseDateTimeForDB
function parseDateTimeForDB_FIXED(value) {
  const dateObj = new Date(value);

  // Extract date using local methods (CORRECT!)
  const year = dateObj.getFullYear();
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getDate()).padStart(2, '0');
  const dateStr = `${year}-${month}-${day}`;

  return { date: dateStr };
}

console.log('BUGGY CODE (uses getUTCDate):');
const buggy = parseDateTimeForDB_BUGGY(tomorrow);
console.log(`  Stored in DB: ${buggy.date}`);
console.log(`  Expected:     2026-01-07`);
console.log(`  Status:       ${buggy.date === '2026-01-07' ? '✅' : '❌ WRONG - Got ' + buggy.date}`);
console.log('');

console.log('FIXED CODE (uses getDate):');
const fixed = parseDateTimeForDB_FIXED(tomorrow);
console.log(`  Stored in DB: ${fixed.date}`);
console.log(`  Expected:     2026-01-07`);
console.log(`  Status:       ${fixed.date === '2026-01-07' ? '✅ CORRECT' : '❌'}`);
console.log('');

console.log('========================================');
console.log('EXPLANATION');
console.log('========================================\n');

console.log('chrono-node parses "tomorrow" as:');
console.log('  "2026-01-07 00:00:00" in user\'s timezone (IST)');
console.log('');
console.log('JavaScript Date stores it internally as UTC:');
console.log('  2026-01-07 00:00 IST = 2026-01-06 18:30 UTC');
console.log('                         ^^^^^^^^^^');
console.log('                         Same DATE in UTC!');
console.log('');
console.log('BUGGY: getUTCDate() extracts UTC date:');
console.log('  → 2026-01-06 ❌ (wrong day)');
console.log('');
console.log('FIXED: getDate() extracts local date:');
console.log('  → 2026-01-07 ✅ (correct day)');
