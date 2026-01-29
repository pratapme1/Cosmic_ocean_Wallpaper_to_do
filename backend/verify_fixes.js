const { calculateTaskScore, isDueToday, isOverdue } = require('./utils/priority-scorer');
const { parseTask } = require('./utils/task-parser');
const { validateAndClean } = require('./utils/llm-task-parser');
const { toZonedTime, formatInTimeZone } = require('date-fns-tz');

console.log('=== VERIFICATION START ===\n');

// 1. TEST LOCAL PARSER (Implicit Dates)
console.log('--- 1. Testing Local Parser (Implicit Dates) ---');
const input1 = "Submit report Friday";
const result1 = parseTask(input1);
console.log(`Input: "${input1}"`);
console.log(`Parsed Due Date: ${result1.dueDate ? result1.dueDate.toString() : 'NULL'} (Expected: Date Object)`);
if (result1.dueDate) console.log('✅ PASS: Implicit "Friday" detected');
else console.error('❌ FAIL: Implicit "Friday" NOT detected');

const input2 = "Run 30 minutes";
const result2 = parseTask(input2);
console.log(`Input: "${input2}"`);
console.log(`Parsed Due Date: ${result2.dueDate} (Expected: NULL)`);
console.log(`Parsed Estimate: ${result2.estimateMinutes} (Expected: 30)`);
if (!result2.dueDate && result2.estimateMinutes === 30) console.log('✅ PASS: Duration "30 minutes" handled as estimate');
else console.error('❌ FAIL: Duration handling incorrect');

// 2. TEST LLM VALIDATION (Trust Logic)
console.log('\n--- 2. Testing LLM Validation (Trust) ---');
const llmRaw = {
    task: "Complete Q3 Review",
    dueDate: "2026-03-31", // Date NOT in keywords
    priority: 2,
    category: "work",
    energyLevel: "high"
};
const cleaned = validateAndClean(llmRaw, "Complete Q3 Review");
console.log(`Input: "Complete Q3 Review", LLM Date: "2026-03-31"`);
console.log(`Cleaned Due Date: ${cleaned.dueDate} (Expected: 2026-03-31)`);
if (cleaned.dueDate === "2026-03-31") console.log('✅ PASS: Valid date preserved (Validation relaxed)');
else console.error('❌ FAIL: Date stripped by over-validation');

// 3. TEST TIMEZONE PRIORITY SCORER
console.log('\n--- 3. Testing Timezone Priority Scorer ---');

const now = new Date();
console.log(`Current Server Time (UTC): ${now.toISOString()}`);

const userTZ = 'Asia/Tokyo';
// Correctly calculate "User Today" string using formatInTimeZone
const userTodayStr = formatInTimeZone(now, userTZ, 'yyyy-MM-dd');
console.log(`User Today in Tokyo: ${userTodayStr}`);

const taskToday = {
    due_date: userTodayStr,
    priority: 2,
    created_at: new Date().toISOString()
};

const isToday = isDueToday(taskToday, userTZ);
console.log(`Task Due Date: ${taskToday.due_date}`);
console.log(`isDueToday('${userTZ}'): ${isToday} (Expected: true)`);
if (isToday) console.log('✅ PASS: Timezone "Today" match');
else console.error('❌ FAIL: Timezone "Today" mismatch');

console.log('\n=== VERIFICATION END ===');
