/**
 * Test with long task titles to ensure proper fitting
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

const tasks = [
  {
    id: '1',
    title: 'Email manager about project status updates and quarterly review meeting schedule',
    priority: 1,
    category: 'work',
    context_tags: ['@office'],
    estimate_minutes: 30,
    due_date: '2026-01-04',
    due_time: '23:30:00'
  },
  {
    id: '2',
    title: 'Complete the comprehensive vulnerability assessment report for the security team',
    priority: 2,
    category: 'work',
    context_tags: ['@work'],
    estimate_minutes: 45,
    due_date: '2026-01-05',
    due_time: '14:00:00'
  },
  {
    id: '3',
    title: 'Review and finalize the quarterly budget presentation slides for stakeholders',
    priority: 2,
    category: 'work',
    estimate_minutes: 60,
    due_date: '2026-01-06'
  },
  {
    id: '4',
    title: 'Schedule dentist appointment and pick up prescription medications from pharmacy',
    priority: 3,
    category: 'health',
    estimate_minutes: 20,
    due_date: '2026-01-07'
  }
];

console.log('═'.repeat(70));
console.log('LONG TEXT TEST');
console.log('Testing with realistic long task titles');
console.log('═'.repeat(70));
console.log();

const devices = [
  { name: 'Your Device', resolution: '393x876' },
  { name: 'Samsung S24', resolution: '1080x2340' },
];

for (const device of devices) {
  console.log(`📱 ${device.name} (${device.resolution})`);

  try {
    const wallpaper = await generateEnhancedWallpaper(
      { theme: 'cosmic', resolution: device.resolution, done_for_today: false },
      { tasks, allTasks: tasks },
      Date.now(),
      'Asia/Kolkata'
    );

    const filename = `/tmp/LONGTEXT-${device.name.toLowerCase().replace(/\s+/g, '-')}.png`;
    fs.writeFileSync(filename, wallpaper);

    console.log(`   ✅ Generated: ${filename}`);
    console.log(`   Size: ${(wallpaper.length / 1024).toFixed(1)} KB`);
    console.log();
  } catch (error) {
    console.log(`   ❌ FAILED: ${error.message}`);
    console.log();
  }
}

console.log('═'.repeat(70));
console.log('CHECK:');
console.log('  1. Are all 3 tasks visible?');
console.log('  2. Is text properly truncated (not cut off)?');
console.log('  3. Does "+ X more" indicator fit?');
console.log('  4. Is intelligent message readable?');
console.log('═'.repeat(70));
