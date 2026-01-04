/**
 * FINAL resolution fix test - MUST VERIFY IMAGES
 * Changes made:
 * 1. Reverted container to height: '100%' (not taskZone.height)
 * 2. Removed flex: 1 spacer that pushed time too far
 * 3. Added calculated marginTop to position time in safe zone
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

const devices = [
  { name: 'Your Device', resolution: '393x876', expectedTasks: 2 },
  { name: 'Samsung S24', resolution: '1080x2340', expectedTasks: 3 },
  { name: 'Nothing Phone 1', resolution: '1080x2400', expectedTasks: 3 },
];

const tasks = [
  {
    id: '1',
    title: 'Review the vulnerability charges',
    priority: 2,
    category: 'work',
    context_tags: ['@work'],
    estimate_minutes: 30,
    due_date: '2026-01-05',
    due_time: '14:00:00'
  },
  {
    id: '2',
    title: 'Email manager about project status',
    priority: 1,
    category: 'work',
    context_tags: ['@office'],
    estimate_minutes: 15,
    due_date: '2026-01-04',
    due_time: '23:30:00'
  },
  {
    id: '3',
    title: 'Buy groceries for dinner tonight',
    priority: 3,
    category: 'errands',
    estimate_minutes: 45,
    due_date: '2026-01-06'
  },
  {
    id: '4',
    title: 'Call dentist to schedule appointment',
    priority: 2,
    category: 'health',
    estimate_minutes: 10,
    due_date: '2026-01-07'
  }
];

console.log('═'.repeat(70));
console.log('FINAL RESOLUTION FIX TEST');
console.log('Changes: Removed flex spacer, calculated time margin');
console.log('═'.repeat(70));
console.log();

for (const device of devices) {
  console.log(`📱 ${device.name} (${device.resolution})`);

  try {
    const wallpaper = await generateEnhancedWallpaper(
      { theme: 'cosmic', resolution: device.resolution, done_for_today: false },
      { tasks, allTasks: tasks },
      Date.now(),
      'Asia/Kolkata'
    );

    const filename = `/tmp/FINAL-${device.name.toLowerCase().replace(/\s+/g, '-')}.png`;
    fs.writeFileSync(filename, wallpaper);

    console.log(`   ✅ Generated: ${filename}`);
    console.log(`   Size: ${(wallpaper.length / 1024).toFixed(1)} KB`);
    console.log(`   Expected tasks shown: ${device.expectedTasks}`);
    console.log();
  } catch (error) {
    console.log(`   ❌ FAILED: ${error.message}`);
    console.log();
  }
}

console.log('═'.repeat(70));
console.log('VERIFICATION CHECKLIST:');
console.log('');
console.log('I MUST manually check these images for:');
console.log('  1. ✅ Time visible at bottom of task zone?');
console.log('  2. ✅ Time NOT cut off by edge of image?');
console.log('  3. ✅ All tasks readable?');
console.log('  4. ✅ "+ X more" indicator visible (if applicable)?');
console.log('  5. ✅ No content overflow?');
console.log('');
console.log('Files to inspect:');
console.log('  - /tmp/FINAL-your-device.png');
console.log('  - /tmp/FINAL-samsung-s24.png');
console.log('  - /tmp/FINAL-nothing-phone-1.png');
console.log('═'.repeat(70));
