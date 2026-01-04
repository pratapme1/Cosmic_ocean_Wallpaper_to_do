/**
 * Test timezone fix - verify that time displays correctly for different timezones
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

// Test case: User in India (UTC+5:30)
const user = {
  theme: 'cosmic',
  resolution: '1080x1920',
  done_for_today: false
};

const tasks = [{
  id: '1',
  title: 'Test timezone task',
  priority: 2,
  estimate_minutes: 30,
  due_date: '2026-01-04',
  due_time: '23:00:00',
  category: 'work',
  context_tags: ['@office'],
  energy_level: 'medium'
}];

console.log('=== TESTING TIMEZONE FIX ===\n');

// Test 1: UTC (default)
console.log('Test 1: UTC timezone (default)');
try {
  const wallpaperUTC = await generateEnhancedWallpaper(user, { tasks, allTasks: tasks }, Date.now(), 'UTC');
  console.log(`✅ UTC wallpaper generated: ${wallpaperUTC.length} bytes`);
  fs.writeFileSync('/tmp/wallpaper-utc-test.png', wallpaperUTC);
  console.log('   Saved to: /tmp/wallpaper-utc-test.png');
} catch (error) {
  console.log(`❌ UTC wallpaper failed: ${error.message}`);
}

// Test 2: India timezone (UTC+5:30)
console.log('\nTest 2: Asia/Kolkata timezone (UTC+5:30)');
try {
  const wallpaperIST = await generateEnhancedWallpaper(user, { tasks, allTasks: tasks }, Date.now(), 'Asia/Kolkata');
  console.log(`✅ IST wallpaper generated: ${wallpaperIST.length} bytes`);
  fs.writeFileSync('/tmp/wallpaper-ist-test.png', wallpaperIST);
  console.log('   Saved to: /tmp/wallpaper-ist-test.png');
} catch (error) {
  console.log(`❌ IST wallpaper failed: ${error.message}`);
}

// Test 3: US Eastern timezone (UTC-5)
console.log('\nTest 3: America/New_York timezone (UTC-5)');
try {
  const wallpaperEST = await generateEnhancedWallpaper(user, { tasks, allTasks: tasks }, Date.now(), 'America/New_York');
  console.log(`✅ EST wallpaper generated: ${wallpaperEST.length} bytes`);
  fs.writeFileSync('/tmp/wallpaper-est-test.png', wallpaperEST);
  console.log('   Saved to: /tmp/wallpaper-est-test.png');
} catch (error) {
  console.log(`❌ EST wallpaper failed: ${error.message}`);
}

// Show what time is displayed in each timezone
console.log('\n=== TIME DISPLAY VERIFICATION ===');
const now = new Date();

const timeUTC = now.toLocaleTimeString('en-US', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: 'UTC'
});

const timeIST = now.toLocaleTimeString('en-US', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: 'Asia/Kolkata'
});

const timeEST = now.toLocaleTimeString('en-US', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: 'America/New_York'
});

console.log(`UTC wallpaper should show:        ${timeUTC}`);
console.log(`IST wallpaper should show:        ${timeIST}`);
console.log(`EST wallpaper should show:        ${timeEST}`);
console.log(`\n✅ All 3 wallpapers should display DIFFERENT times!`);
console.log(`\n📸 Check images visually to verify times are correct.`);
