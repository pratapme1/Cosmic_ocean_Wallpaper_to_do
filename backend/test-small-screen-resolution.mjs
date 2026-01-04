/**
 * Test wallpaper generation for small screen (393x876)
 * User reported content doesn't fit properly on their device
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

const user = {
  theme: 'cosmic',
  resolution: '393x876',  // User's actual screen size
  done_for_today: false
};

const tasks = [
  {
    id: '1',
    title: 'Review the vulnerability charges',
    priority: 2,
    category: 'work',
    context_tags: ['@work'],
    estimate_minutes: 30,
    due_date: '2026-01-05',
    due_time: '14:00:00',
    energy_level: 'medium'
  },
  {
    id: '2',
    title: 'Email manager about project',
    priority: 1,
    category: 'work',
    context_tags: ['@office'],
    estimate_minutes: 15,
    due_date: '2026-01-04',
    due_time: '23:30:00',
    energy_level: 'high'
  },
  {
    id: '3',
    title: 'Buy groceries for dinner',
    priority: 3,
    category: 'errands',
    estimate_minutes: 45,
    due_date: '2026-01-06',
    energy_level: 'low'
  }
];

console.log('=== TESTING SMALL SCREEN RESOLUTION (393x876) ===\n');

try {
  const wallpaper = await generateEnhancedWallpaper(
    user,
    { tasks, allTasks: tasks },
    Date.now(),
    'Asia/Kolkata'
  );

  fs.writeFileSync('/tmp/wallpaper-393x876-test.png', wallpaper);

  console.log(`✅ Wallpaper generated successfully`);
  console.log(`   Resolution: ${user.resolution}`);
  console.log(`   File size: ${wallpaper.length} bytes`);
  console.log(`   Tasks: ${tasks.length}`);
  console.log(`   Saved to: /tmp/wallpaper-393x876-test.png`);
  console.log();
  console.log('📊 Expected layout for 393x876:');
  console.log('   - All 3 tasks visible');
  console.log('   - Current time at bottom');
  console.log('   - No cropping or overflow');
  console.log('   - Text scaled appropriately');
  console.log();
  console.log('✅ Please visually verify the generated wallpaper fits the screen.');
} catch (error) {
  console.log(`❌ Wallpaper generation failed: ${error.message}`);
  console.log(error.stack);
}
