const { calculateTaskScore, isDueToday, isOverdue, prioritizeTasks } = require('./utils/priority-scorer');
const { parseTask } = require('./utils/task-parser');
const { validateAndClean } = require('./utils/llm-task-parser');
const { formatInTimeZone } = require('date-fns-tz');
const { addDays, subDays } = require('date-fns');

/**
 * COMPREHENSIVE TEST SUITE - COSMIC OCEAN INTELLIGENCE
 * Covers: Unit, Integration, and Scenario-based testing.
 */

const results = {
    passed: 0,
    failed: 0,
    logs: []
};

function assert(condition, message) {
    if (condition) {
        results.passed++;
        results.logs.push(`✅ PASS: ${message}`);
    } else {
        results.failed++;
        results.logs.push(`❌ FAIL: ${message}`);
    }
}

function section(name) {
    results.logs.push(`\n=== ${name.toUpperCase()} ===`);
}

/**
 * 1. UNIT TEST: NLP PARSER (Local Regex)
 */
section('NLP Parser (Local Regex) - Pattern Matching');

const nlpScenarios = [
    { input: "Call Mom Friday", expectedDate: true, label: "Implicit Weekday" },
    { input: "Buy milk at 5pm tomorrow", expectedDate: true, expectedTime: "17:00", label: "Explicit Future Time" },
    { input: "Submit project by end of day", expectedDate: true, label: "End of Day Keyword" },
    { input: "Workout 1h", expectedDate: false, expectedEstimate: 60, label: "Bare Duration (No Date)" },
    { input: "Deep work focus session @office", expectedEnergy: 'high', expectedContext: ['@office'], label: "Context & Energy" }
];

nlpScenarios.forEach(s => {
    const p = parseTask(s.input);
    if (s.expectedDate !== undefined) assert(!!p.dueDate === s.expectedDate, `${s.label} - Date detection`);
    if (s.expectedTime !== undefined) assert(p.dueTime === s.expectedTime, `${s.label} - Time match (${p.dueTime} vs ${s.expectedTime})`);
    if (s.expectedEstimate !== undefined) assert(p.estimateMinutes === s.expectedEstimate, `${s.label} - Estimate match`);
    if (s.expectedEnergy !== undefined) assert(p.energy === s.expectedEnergy, `${s.label} - Energy match`);
    if (s.expectedContext !== undefined) assert(JSON.stringify(p.context) === JSON.stringify(s.expectedContext), `${s.label} - Context match`);
});

/**
 * 2. INTEGRATION TEST: LLM TRUST LOGIC
 */
section('LLM Integration - Trusted Data Preservation');

const llmIntegrations = [
    {
        raw: { task: "Q1 Results", dueDate: "2026-03-31", priority: 1 },
        input: "Submit Q1 Results",
        label: "Implicit Quarter Date"
    },
    {
        raw: { task: "Doctor", dueDate: "2026-02-15", dueTime: "14:30" },
        input: "Dr meeting",
        label: "Ambiguous Phrase with Inferred Date"
    }
];

llmIntegrations.forEach(s => {
    const cleaned = validateAndClean(s.raw, s.input);
    assert(cleaned.dueDate === s.raw.dueDate, `${s.label} - Date preserved via Trust Logic`);
});

/**
 * 3. UNIT TEST: TIMEZONE PRIORITY SCORING
 */
section('Timezone Boundary Logic');

const now = new Date();
const tokyoTZ = 'Asia/Tokyo';
const nyTZ = 'America/New_York';

// Scenario: Task is Due Today in Tokyo (UTC+9)
const tokyoTodayStr = formatInTimeZone(now, tokyoTZ, 'yyyy-MM-dd');
const nyTodayStr = formatInTimeZone(now, nyTZ, 'yyyy-MM-dd');

const taskDueTokyoToday = {
    due_date: tokyoTodayStr,
    priority: 2,
    created_at: subDays(now, 1).toISOString()
};

assert(isDueToday(taskDueTokyoToday, tokyoTZ), "Task due today in Tokyo correctly matches Tokyo context");

// Scenario: Overdue Check
const taskDueYesterdayNY = {
    due_date: formatInTimeZone(subDays(now, 1), nyTZ, 'yyyy-MM-dd'),
    priority: 2
};
assert(isOverdue(taskDueYesterdayNY, nyTZ), "Task due yesterday in NY correctly marked Overdue");

/**
 * 4. SORTING & "ONE THING" E2E SCENARIO
 */
section('E2E Scenario: Ranking & Queueing');

const scenarioTasks = [
    { id: 1, title: 'Old Low Priority', priority: 3, created_at: subDays(now, 10).toISOString(), due_date: null },
    { id: 2, title: 'Urgent Today', priority: 1, created_at: now.toISOString(), due_date: tokyoTodayStr },
    { id: 3, title: 'Overdue Task', priority: 2, created_at: subDays(now, 5).toISOString(), due_date: formatInTimeZone(subDays(now, 2), tokyoTZ, 'yyyy-MM-dd') },
    { id: 4, title: 'Medium Priority No Date', priority: 2, created_at: now.toISOString(), due_date: null }
];

const prioritized = prioritizeTasks(scenarioTasks, { timezone: tokyoTZ });

assert(prioritized[0].id === 3, "Ranking: Overdue task (+1000) is #1");
assert(prioritized[1].id === 2, "Ranking: Urgent Today (+500) is #2");
assert(prioritized.length === 4, "Queue: All 10 tasks maintained in priority queue");

/**
 * FINAL REPORT
 */
console.log(results.logs.join('\n'));
console.log(`\n=== FINAL SUMMARY ===`);
console.log(`PASSED: ${results.passed}`);
console.log(`FAILED: ${results.failed}`);

if (results.failed > 0) {
    process.exit(1);
}
process.exit(0);
