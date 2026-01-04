/**
 * Test MessageEngine Integration in Wallpaper Generator
 * Verifies that intelligent messages are generated and included in wallpapers
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Import the wallpaper generator
const { generateEnhancedWallpaper } = await import('./services/wallpaper-generator-enhanced.js');

console.log('🧪 Testing MessageEngine Integration\n');

// Test Case 1: User with active tasks and streak
console.log('=== Test 1: Active user with streak ===');
const user1 = {
  theme: 'cosmic',
  resolution: '1080x1920',
  done_for_today: false
};

const tasks1 = [
  {
    id: '1',
    title: 'URGENT: Finish report',
    priority: 1,
    estimate_minutes: 120,
    due_date: new Date().toISOString().split('T')[0], // Due today
    completed: false,
    category: 'work',
    energy_level: 'high'
  },
  {
    id: '2',
    title: 'Call dentist',
    priority: 2,
    estimate_minutes: 15,
    completed: false,
    category: 'health'
  },
  {
    id: '3',
    title: 'Buy groceries',
    priority: 2,
    estimate_minutes: 45,
    completed: false,
    category: 'errands'
  }
];

// Include completed tasks for stats calculation
const allTasks1 = [
  ...tasks1,
  {
    id: '4',
    title: 'Morning workout',
    priority: 2,
    completed: true,
    completed_at: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2 hours ago
    category: 'health'
  },
  {
    id: '5',
    title: 'Read chapter',
    priority: 3,
    completed: true,
    completed_at: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(), // Yesterday
    category: 'learning'
  }
];

try {
  console.log('Generating wallpaper...');
  const wallpaper1 = await generateEnhancedWallpaper(user1, {
    tasks: tasks1,
    allTasks: allTasks1
  });

  console.log(`✅ Wallpaper generated: ${wallpaper1.length} bytes`);
  writeFileSync(join(__dirname, 'test-wallpaper-message-1.png'), wallpaper1);
  console.log('💾 Saved to: test-wallpaper-message-1.png\n');
} catch (err) {
  console.error('❌ Error:', err.message);
  console.error(err.stack);
}

// Test Case 2: User marked done for today
console.log('=== Test 2: Done for today (celebration) ===');
const user2 = {
  theme: 'ocean',
  resolution: '1080x1920',
  done_for_today: true
};

try {
  const wallpaper2 = await generateEnhancedWallpaper(user2, {
    tasks: [],
    allTasks: allTasks1
  });

  console.log(`✅ Wallpaper generated: ${wallpaper2.length} bytes`);
  writeFileSync(join(__dirname, 'test-wallpaper-message-2.png'), wallpaper2);
  console.log('💾 Saved to: test-wallpaper-message-2.png\n');
} catch (err) {
  console.error('❌ Error:', err.message);
}

// Test Case 3: Overdue tasks (critical urgency)
console.log('=== Test 3: Overdue tasks (critical urgency) ===');
const user3 = {
  theme: 'cosmic',
  resolution: '1080x1920',
  done_for_today: false
};

const yesterday = new Date();
yesterday.setDate(yesterday.getDate() - 1);

const tasks3 = [
  {
    id: '1',
    title: 'Submit tax return',
    priority: 1,
    estimate_minutes: 180,
    due_date: yesterday.toISOString().split('T')[0], // Yesterday (overdue)
    completed: false,
    category: 'finance',
    energy_level: 'high'
  }
];

try {
  const wallpaper3 = await generateEnhancedWallpaper(user3, {
    tasks: tasks3,
    allTasks: tasks3
  });

  console.log(`✅ Wallpaper generated: ${wallpaper3.length} bytes`);
  writeFileSync(join(__dirname, 'test-wallpaper-message-3.png'), wallpaper3);
  console.log('💾 Saved to: test-wallpaper-message-3.png\n');
} catch (err) {
  console.error('❌ Error:', err.message);
}

// Test Case 4: No tasks (all clear)
console.log('=== Test 4: No tasks (all clear) ===');
const user4 = {
  theme: 'fantasy',
  resolution: '1080x1920',
  done_for_today: false
};

try {
  const wallpaper4 = await generateEnhancedWallpaper(user4, {
    tasks: [],
    allTasks: []
  });

  console.log(`✅ Wallpaper generated: ${wallpaper4.length} bytes`);
  writeFileSync(join(__dirname, 'test-wallpaper-message-4.png'), wallpaper4);
  console.log('💾 Saved to: test-wallpaper-message-4.png\n');
} catch (err) {
  console.error('❌ Error:', err.message);
}

console.log('✅ All tests completed!');
console.log('\nCheck the generated PNG files to verify intelligent messages are displayed.');
