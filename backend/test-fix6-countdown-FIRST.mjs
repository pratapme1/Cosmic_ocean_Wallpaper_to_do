/**
 * Fix #6: Live Countdown - TEST FIRST (NO-GO Practice)
 *
 * Test BEFORE implementing:
 * 1. Create task due in specific time
 * 2. Generate wallpaper
 * 3. Verify countdown shows "DUE IN Xh Ym"
 * 4. Wait, regenerate, verify countdown decreased
 */

import wallpaperGenerator from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

const { generateEnhancedWallpaper } = wallpaperGenerator;

console.log('🧪 Fix #6: Live Countdown - TEST FIRST\n');
console.log('='.repeat(80));
console.log('\n⚠️  This test will FAIL until Fix #6 is implemented\n');

// Test user inputs (real scenarios)
const testScenarios = [
  {
    name: 'Task due in 2 hours 15 minutes',
    task: {
      id: '1',
      title: 'Email manager about project',
      category: 'work',
      context_tags: ['@office'],
      priority: 1,
      estimate_minutes: 15,
      due_date: new Date(Date.now() + 2 * 60 * 60 * 1000 + 15 * 60 * 1000).toISOString(), // 2h 15m from now
    },
    expectedCountdown: 'DUE IN 2H 15M'
  },
  {
    name: 'Task due in 45 minutes',
    task: {
      id: '2',
      title: 'Call with client',
      category: 'work',
      priority: 1,
      estimate_minutes: 30,
      due_date: new Date(Date.now() + 45 * 60 * 1000).toISOString(), // 45min from now
    },
    expectedCountdown: 'DUE IN 45M'
  },
  {
    name: 'Task due in 5 minutes (urgent)',
    task: {
      id: '3',
      title: 'Urgent standup meeting',
      category: 'work',
      priority: 1,
      estimate_minutes: 15,
      due_date: new Date(Date.now() + 5 * 60 * 1000).toISOString(), // 5min from now
    },
    expectedCountdown: 'DUE IN 5M'
  },
  {
    name: 'Task overdue by 30 minutes',
    task: {
      id: '4',
      title: 'Missed deadline task',
      category: 'work',
      priority: 1,
      estimate_minutes: 20,
      due_date: new Date(Date.now() - 30 * 60 * 1000).toISOString(), // 30min ago
    },
    expectedCountdown: '30M OVERDUE'
  },
  {
    name: 'Task due tomorrow',
    task: {
      id: '5',
      title: 'Review document',
      category: 'work',
      priority: 2,
      estimate_minutes: 60,
      due_date: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(), // Tomorrow
    },
    expectedCountdown: 'DUE TOMORROW'
  }
];

async function runTest() {
  const user = {
    id: 'test-user',
    theme: 'cosmic',
    resolution: '1080x1920'
  };

  console.log('Testing countdown for different scenarios:\n');

  for (const scenario of testScenarios) {
    console.log(`📋 Scenario: ${scenario.name}`);
    console.log(`   Task: "${scenario.task.title}"`);
    console.log(`   Due: ${new Date(scenario.task.due_date).toLocaleString()}`);
    console.log(`   Expected: "${scenario.expectedCountdown}"`);

    try {
      const result = await generateEnhancedWallpaper(
        user,
        { tasks: [scenario.task], allTasks: [scenario.task] }
      );

      const filename = `wallpaper-fix6-test-${scenario.name.replace(/\s+/g, '-')}.png`;
      fs.writeFileSync(filename, Buffer.from(result.buffer));

      console.log(`   ✅ Generated: ${filename}`);
      console.log(`   📸 VERIFY: Open PNG and check countdown text\n`);

    } catch (error) {
      console.error(`   ❌ Error: ${error.message}\n`);
    }
  }

  console.log('='.repeat(80));
  console.log('\n🎯 Expected Results (After Fix #6 Implementation):\n');
  console.log('1. ✓ "DUE IN 2H 15M" for task due in 2h 15m');
  console.log('2. ✓ "DUE IN 45M" for task due in 45min');
  console.log('3. ✓ "DUE IN 5M" for task due in 5min (red/urgent color)');
  console.log('4. ✓ "30M OVERDUE" for overdue task (red color)');
  console.log('5. ✓ "DUE TOMORROW" for task due next day');
  console.log('\n📝 Current Status: Likely shows static dates instead of countdown');
  console.log('🔧 Next: Implement countdown logic in text-renderer.js\n');
}

runTest();
