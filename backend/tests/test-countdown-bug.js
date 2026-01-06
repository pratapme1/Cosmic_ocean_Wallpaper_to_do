/**
 * TEST: Countdown Bug - Invalid Date Creation
 *
 * Demonstrates two bugs in text-renderer.js getLiveCountdown():
 * 1. Line 371: Creating fullDueDate with template literal converts Date to invalid string
 * 2. Line 575: Parsing timezone-converted string as UTC creates wrong "now" value
 */

console.log('========================================');
console.log('TEST: Countdown Bug - text-renderer.js');
console.log('========================================\n');

// Simulate what PostgreSQL returns for DATE column
// User created "Call mom tomorrow" on Jan 6, should be due Jan 7
const dbDueDate = new Date('2026-01-06T18:30:00.000Z'); // Jan 7 12:00 AM IST

console.log('DATABASE VALUE:');
console.log(`  due_date (raw): ${dbDueDate.toISOString()}`);
console.log(`  due_date (IST): Jan 7, 2026 12:00 AM`);
console.log('');

// BUG 1: Line 371 - Invalid Date creation
console.log('BUG 1: Creating fullDueDate with template literal');
console.log('-----------------------------------------------');
const buggyDateString = `${dbDueDate}T23:59:59`;
console.log(`  Template literal: \`\${dbDueDate}T23:59:59\``);
console.log(`  Result: "${buggyDateString}"`);
const buggyFullDueDate = new Date(buggyDateString);
console.log(`  new Date(result): ${buggyFullDueDate}`);
console.log(`  Is valid? ${!isNaN(buggyFullDueDate.getTime())} ❌`);
console.log('');

// BUG 2: Line 575 - Wrong "now" calculation
console.log('BUG 2: Timezone-aware "now" calculation');
console.log('---------------------------------------');
const actualNow = new Date('2026-01-06T10:04:22Z'); // 3:34 PM IST
const timezone = 'Asia/Calcutta';
const localString = actualNow.toLocaleString('en-US', { timeZone: timezone });
const buggyNow = new Date(localString);

console.log(`  Actual now (UTC): ${actualNow.toISOString()}`);
console.log(`  Actual now (IST): 3:34 PM on Jan 6`);
console.log(`  toLocaleString: "${localString}"`);
console.log(`  new Date(localeString): ${buggyNow.toISOString()}`);
console.log(`  Offset: ${(buggyNow - actualNow) / (1000 * 60 * 60)} hours ❌`);
console.log('');

// CORRECT APPROACH
console.log('CORRECT APPROACH:');
console.log('----------------');

// Fix 1: Use toISOString() to get ISO format date string
const correctDateString = dbDueDate.toISOString().split('T')[0]; // "2026-01-06"
const correctFullDueDate = new Date(`${correctDateString}T23:59:59Z`);
console.log(`  Date string: "${correctDateString}"`);
console.log(`  fullDueDate: ${correctFullDueDate.toISOString()}`);
console.log(`  Is valid? ${!isNaN(correctFullDueDate.getTime())} ✅`);
console.log('');

// Fix 2: Just use new Date() - it's always UTC
const correctNow = new Date('2026-01-06T10:04:22Z');
console.log(`  now: ${correctNow.toISOString()}`);
console.log('');

// CORRECT COUNTDOWN CALCULATION
console.log('CORRECT COUNTDOWN:');
console.log('-----------------');
const diffMs = correctFullDueDate - correctNow;
const diffHours = diffMs / (1000 * 60 * 60);
const diffMinutes = Math.floor(diffMs / (1000 * 60));

console.log(`  Due: Jan 6, 2026 11:59 PM UTC`);
console.log(`  Now: Jan 6, 2026 10:04 AM UTC (3:34 PM IST)`);
console.log(`  Diff: ${diffHours.toFixed(2)} hours`);
console.log(`  Should show: "DUE IN 13H 55M" ✅`);
console.log('');

// BUGGY COUNTDOWN CALCULATION (what current code does with Invalid Date)
console.log('BUGGY COUNTDOWN (with Invalid Date):');
console.log('------------------------------------');
const buggyDiffMs = buggyFullDueDate - buggyNow;
const buggyDiffMinutes = Math.floor(buggyDiffMs / (1000 * 60));
console.log(`  diffMs: ${buggyDiffMs} (NaN)`);
console.log(`  diffMinutes: ${buggyDiffMinutes} (NaN)`);
console.log(`  Math.abs(NaN) > 1440? ${Math.abs(buggyDiffMinutes) > 1440} (false)`);
console.log(`  Goes to "Due soon" branch`);
console.log(`  hours = Math.floor(NaN / 60) = ${Math.floor(buggyDiffMinutes / 60)}`);
console.log(`  hours < 24? ${Math.floor(buggyDiffMinutes / 60) < 24} (false)`);
console.log(`  Shows: "DUE TOMORROW" ❌`);
console.log('');

console.log('========================================');
console.log('CONCLUSION');
console.log('========================================');
console.log('');
console.log('Current code has TWO critical bugs:');
console.log('1. Invalid Date creation from template literal');
console.log('2. Wrong "now" calculation from timezone conversion');
console.log('');
console.log('Result: Unpredictable countdown display');
console.log('Expected: "DUE IN 13H 55M"');
console.log('Actual: "DUE IN 2D" or other wrong values');
console.log('');
console.log('✅ Test demonstrates the bug clearly');
