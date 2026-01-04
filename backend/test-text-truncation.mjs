/**
 * Test proper pixel-based text truncation
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

const testCases = [
  {
    title: 'Email manager about project status updates and quarterly review meeting',
    expected: 'Should truncate based on actual pixel width, not character count'
  },
  {
    title: 'WWWWWWWWWWWWWWWWWWWWWWWWWWWWWW',
    expected: 'Wide characters should truncate earlier than narrow characters'
  },
  {
    title: 'Buy milk',
    expected: 'Short text should not truncate'
  }
];

const tasks = testCases.map((tc, i) => ({
  id: `${i}`,
  title: tc.title,
  priority: 1,
  category: 'work',
  estimate_minutes: 30
}));

console.log('═'.repeat(70));
console.log('PIXEL-BASED TEXT TRUNCATION TEST');
console.log('═'.repeat(70));
console.log();

testCases.forEach((tc, i) => {
  console.log(`Test ${i + 1}:`);
  console.log(`  Input:    "${tc.title}"`);
  console.log(`  Expected: ${tc.expected}`);
  console.log();
});

console.log('Generating wallpaper...');

try {
  const wallpaper = await generateEnhancedWallpaper(
    { theme: 'cosmic', resolution: '1080x2340', done_for_today: false },
    { tasks, allTasks: tasks },
    Date.now(),
    'Asia/Kolkata'
  );

  const filename = '/tmp/text-truncation-test.png';
  fs.writeFileSync(filename, wallpaper);

  console.log(`✅ SUCCESS: ${filename}`);
  console.log(`   Size: ${(wallpaper.length / 1024).toFixed(1)} KB`);
  console.log();
  console.log('Visual check:');
  console.log('  1. Long title should be truncated with "..." at the end');
  console.log('  2. Wide characters should take up more space');
  console.log('  3. Short text should display fully');
  console.log('  4. NO text should overflow off screen');
  console.log();
  console.log('Open the image to verify!');

} catch (error) {
  console.log(`❌ FAILED: ${error.message}`);
  console.log(error.stack);
}

console.log('═'.repeat(70));
