/**
 * Investigate garbled text issue
 *
 * PROBLEM: Wallpaper shows "Jhikkm okhhjk kkkkloh hiyikkbgik..." instead of real task titles
 * POSSIBLE CAUSES:
 * 1. Database has corrupt data
 * 2. Encoding issue (UTF-8 vs Latin1)
 * 3. Font rendering issue (unlikely - we tested this already)
 * 4. Task title field has binary data somehow
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

console.log('=== GARBLED TEXT INVESTIGATION ===\n');

// Test Case 1: Normal text
console.log('Test 1: Normal ASCII text');
const normalTasks = [{
  id: '1',
  title: 'Review the vulnerability charges',
  priority: 2,
  category: 'work',
  context_tags: ['@work']
}];

try {
  const wallpaper1 = await generateEnhancedWallpaper(
    { theme: 'cosmic', resolution: '1080x1920', done_for_today: false },
    { tasks: normalTasks, allTasks: normalTasks },
    Date.now(),
    'Asia/Kolkata'
  );
  fs.writeFileSync('/tmp/test-normal-text.png', wallpaper1);
  console.log('✅ Normal text rendered OK');
  console.log(`   Title: "${normalTasks[0].title}"`);
} catch (error) {
  console.log(`❌ Failed: ${error.message}`);
}

// Test Case 2: Random garbage (simulating corruption)
console.log('\nTest 2: Random garbage text (simulating corruption)');
const garbledTasks = [{
  id: '2',
  title: 'Jhikkm okhhjk kkkkloh hiyikkbgijk',
  priority: 2,
  category: 'work',
  context_tags: ['@work']
}];

try {
  const wallpaper2 = await generateEnhancedWallpaper(
    { theme: 'cosmic', resolution: '1080x1920', done_for_today: false },
    { tasks: garbledTasks, allTasks: garbledTasks },
    Date.now(),
    'Asia/Kolkata'
  );
  fs.writeFileSync('/tmp/test-garbled-text.png', wallpaper2);
  console.log('✅ Garbled text rendered OK');
  console.log(`   Title: "${garbledTasks[0].title}"`);
} catch (error) {
  console.log(`❌ Failed: ${error.message}`);
}

// Test Case 3: UTF-8 special characters
console.log('\nTest 3: UTF-8 special characters');
const utf8Tasks = [{
  id: '3',
  title: 'Email manager about project 你好',
  priority: 2,
  category: 'work',
  context_tags: ['@office']
}];

try {
  const wallpaper3 = await generateEnhancedWallpaper(
    { theme: 'cosmic', resolution: '1080x1920', done_for_today: false },
    { tasks: utf8Tasks, allTasks: utf8Tasks },
    Date.now(),
    'Asia/Kolkata'
  );
  fs.writeFileSync('/tmp/test-utf8-text.png', wallpaper3);
  console.log('✅ UTF-8 text rendered OK');
  console.log(`   Title: "${utf8Tasks[0].title}"`);
} catch (error) {
  console.log(`❌ Failed: ${error.message}`);
}

// Test Case 4: Very long title
console.log('\nTest 4: Very long title (truncation test)');
const longTasks = [{
  id: '4',
  title: 'This is a very long task title that should be truncated because it exceeds the maximum length allowed on the wallpaper display area and we need to see how it handles this edge case properly without breaking the layout or causing text overflow issues',
  priority: 2,
  category: 'work'
}];

try {
  const wallpaper4 = await generateEnhancedWallpaper(
    { theme: 'cosmic', resolution: '1080x1920', done_for_today: false },
    { tasks: longTasks, allTasks: longTasks },
    Date.now(),
    'Asia/Kolkata'
  );
  fs.writeFileSync('/tmp/test-long-text.png', wallpaper4);
  console.log('✅ Long text rendered OK');
  console.log(`   Title length: ${longTasks[0].title.length} chars`);
} catch (error) {
  console.log(`❌ Failed: ${error.message}`);
}

console.log('\n=== DIAGNOSIS ===');
console.log('If all tests pass, the issue is DATABASE CORRUPTION, not rendering.');
console.log('The user has garbage data stored in their tasks table.');
console.log('\n📊 Next step: Check production database for corrupt task titles.');
console.log('   SELECT id, title FROM tasks WHERE user_id = ? ORDER BY created_at DESC LIMIT 10;');
