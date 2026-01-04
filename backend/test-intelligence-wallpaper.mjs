/**
 * E2E Test: Wallpaper Intelligence Layer
 * Tests the complete integration of NLP parser, message engine,
 * atmosphere controller, and wallpaper generation.
 */

import { writeFileSync } from 'fs';

// Import the modules
const { generateEnhancedWallpaper } = await import('./services/wallpaper-generator-enhanced.js');
const { parseTask } = await import('./utils/task-parser.js');
const { MessageEngine } = await import('./services/message-engine.js');
const { AtmosphereController } = await import('./services/atmosphere-controller.js');
const { StatsAggregator } = await import('./services/stats-aggregator.js');

console.log('='.repeat(60));
console.log('E2E TEST: Wallpaper Intelligence Layer');
console.log('='.repeat(60));

// Test 1: NLP Parser
console.log('\n📝 TEST 1: NLP Parser\n');

const testInputs = [
  'URGENT: @work finish report tomorrow 2h',
  '@home clean kitchen 30m',
  'Weekly team meeting every Monday at 10am',
  'Quick email check',
  'Workout at gym tomorrow morning'
];

for (const input of testInputs) {
  const result = parseTask(input);
  console.log(`Input: "${input}"`);
  console.log(`  → Title: "${result.title}"`);
  console.log(`  → Priority: P${result.priority} (${result.priorityReason})`);
  console.log(`  → Context: ${result.context.length > 0 ? result.context.join(', ') : 'none'}`);
  console.log(`  → Category: ${result.category}`);
  console.log(`  → Energy: ${result.energy}`);
  console.log(`  → Duration: ${result.estimateMinutes ? result.estimateMinutes + 'min' : 'none'}`);
  console.log(`  → Recurring: ${result.recurring ? result.recurring.interval : 'none'}`);
  console.log(`  → Confidence: ${(result.confidence * 100).toFixed(0)}%`);
  console.log('');
}

// Test 2: Atmosphere Controller
console.log('\n🌡️ TEST 2: Atmosphere Controller\n');

const atmosphereController = new AtmosphereController();

const testScenarios = [
  { name: 'No tasks', tasks: [] },
  { name: 'Calm (future tasks)', tasks: [
    { title: 'Task 1', due_date: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) }
  ]},
  { name: 'Attention (due tomorrow)', tasks: [
    { title: 'Task 1', due_date: new Date(Date.now() + 20 * 60 * 60 * 1000) }
  ]},
  { name: 'Urgent (due in 6 hours)', tasks: [
    { title: 'Task 1', due_date: new Date(Date.now() + 6 * 60 * 60 * 1000) }
  ]},
  { name: 'Critical (overdue)', tasks: [
    { title: 'Task 1', due_date: new Date(Date.now() - 2 * 60 * 60 * 1000) }
  ]}
];

for (const scenario of testScenarios) {
  const state = atmosphereController.calculateState(scenario.tasks);
  console.log(`${scenario.name}:`);
  console.log(`  → Urgency Score: ${state.urgencyScore}`);
  console.log(`  → State: ${state.state}`);
  console.log(`  → Particles: ${state.visualParams.particleCount}`);
  console.log(`  → Animation Speed: ${state.visualParams.animationSpeed}x`);
  console.log('');
}

// Test 3: Message Engine
console.log('\n💬 TEST 3: Message Engine\n');

const messageEngine = new MessageEngine();

const messageScenarios = [
  {
    name: 'No tasks',
    tasks: [],
    stats: { completedToday: 0, streakDays: 0 }
  },
  {
    name: 'Morning with tasks',
    tasks: [{ title: 'Important meeting', priority: 1 }],
    stats: { completedToday: 2, streakDays: 5 }
  },
  {
    name: 'Overdue task',
    tasks: [{ title: 'Submit report', due_date: new Date(Date.now() - 3600000) }],
    stats: { completedToday: 0, streakDays: 0 }
  },
  {
    name: 'All done',
    tasks: [{ title: 'Done task', completed: true }],
    stats: { completedToday: 5, streakDays: 7 }
  }
];

for (const scenario of messageScenarios) {
  const message = messageEngine.generateMessage({}, scenario.tasks, new Date(), scenario.stats);
  console.log(`${scenario.name}:`);
  console.log(`  → Message: "${message.text}"`);
  console.log(`  → Type: ${message.type}`);
  console.log(`  → Priority: ${message.priority}`);
  console.log('');
}

// Test 4: Stats Aggregator
console.log('\n📊 TEST 4: Stats Aggregator\n');

const statsAggregator = new StatsAggregator();

const mockTasks = [
  { title: 'Task 1', completed: true, completedAt: new Date().toISOString(), category: 'work' },
  { title: 'Task 2', completed: true, completedAt: new Date().toISOString(), category: 'work' },
  { title: 'Task 3', completed: true, completedAt: new Date(Date.now() - 86400000).toISOString(), category: 'personal' },
  { title: 'Task 4', completed: false, category: 'health' },
];

const stats = statsAggregator.computeStats(mockTasks);
console.log('Stats for mock tasks:');
console.log(`  → Completed Today: ${stats.completedToday}`);
console.log(`  → Completed This Week: ${stats.completedThisWeek}`);
console.log(`  → Total Completed: ${stats.totalCompleted}`);
console.log(`  → Streak Days: ${stats.streakDays}`);
console.log(`  → Peak Period: ${stats.patterns.peakPeriod || 'not enough data'}`);
console.log('');

// Test 5: Full Wallpaper Generation
console.log('\n🖼️ TEST 5: Full Wallpaper Generation\n');

const wallpaperScenarios = [
  {
    name: 'cosmic-urgent',
    user: { theme: 'cosmic', resolution: '1080x1920' },
    tasks: [
      { title: 'Submit quarterly report', priority: 1, due_date: new Date(Date.now() + 4 * 3600000), estimate_minutes: 120 },
      { title: 'Review PR', priority: 2, estimate_minutes: 30 }
    ]
  },
  {
    name: 'ocean-clear',
    user: { theme: 'ocean', resolution: '1080x1920', done_for_today: true },
    tasks: []
  },
  {
    name: 'fantasy-critical',
    user: { theme: 'fantasy', resolution: '1080x1920' },
    tasks: [
      { title: 'Overdue: Call client', priority: 1, due_date: new Date(Date.now() - 2 * 3600000) }
    ]
  }
];

for (const scenario of wallpaperScenarios) {
  console.log(`Generating ${scenario.name}...`);

  try {
    const startTime = Date.now();
    const buffer = await generateEnhancedWallpaper(
      scenario.user,
      { tasks: scenario.tasks, allTasks: scenario.tasks }
    );
    const duration = Date.now() - startTime;

    const filename = `wallpaper-${scenario.name}-test.png`;
    writeFileSync(filename, buffer);

    console.log(`  ✅ Generated ${filename} (${buffer.length} bytes) in ${duration}ms`);
  } catch (error) {
    console.log(`  ❌ Failed: ${error.message}`);
  }
  console.log('');
}

console.log('='.repeat(60));
console.log('E2E TEST COMPLETE');
console.log('='.repeat(60));
