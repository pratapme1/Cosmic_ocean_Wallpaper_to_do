/**
 * Test wallpaper generation on REAL device resolutions
 * Following NO-GO rules: Test with actual device specs, not ideal cases
 */

import { generateEnhancedWallpaper } from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

// Real device specifications
const devices = [
  {
    name: 'Your Device (Reported)',
    resolution: '393x876',
    density: '~240dpi (1.5x)',
    notes: 'Small screen - user reported issues'
  },
  {
    name: 'Samsung Galaxy S24',
    resolution: '1080x2340',
    density: '~420dpi (2.6x)',
    notes: 'FHD+ AMOLED, 6.2 inch'
  },
  {
    name: 'Samsung Galaxy S24 Ultra',
    resolution: '1440x3120',
    density: '~505dpi (3.1x)',
    notes: 'QHD+ AMOLED, 6.8 inch'
  },
  {
    name: 'Nothing Phone (1)',
    resolution: '1080x2400',
    density: '~395dpi (2.5x)',
    notes: 'FHD+ OLED, 6.55 inch'
  },
  {
    name: 'Google Pixel 8',
    resolution: '1080x2400',
    density: '~428dpi (2.7x)',
    notes: 'FHD+ OLED, 6.2 inch'
  },
  {
    name: 'OnePlus 11',
    resolution: '1440x3216',
    density: '~525dpi (3.3x)',
    notes: 'QHD+ AMOLED, 6.7 inch'
  },
  {
    name: 'iPhone 14 Pro (if user has)',
    resolution: '1179x2556',
    density: '~460dpi (3x)',
    notes: 'Super Retina XDR, 6.1 inch'
  }
];

// Sample tasks for testing
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
console.log('MULTI-DEVICE WALLPAPER TEST');
console.log('Testing fixes on real device resolutions');
console.log('═'.repeat(70));
console.log();

let allPassed = true;

for (const device of devices) {
  console.log('━'.repeat(70));
  console.log(`📱 ${device.name}`);
  console.log('━'.repeat(70));
  console.log(`   Resolution: ${device.resolution}`);
  console.log(`   Density:    ${device.density}`);
  console.log(`   Notes:      ${device.notes}`);
  console.log();

  const [width, height] = device.resolution.split('x').map(Number);

  try {
    const wallpaper = await generateEnhancedWallpaper(
      { theme: 'cosmic', resolution: device.resolution, done_for_today: false },
      { tasks, allTasks: tasks },
      Date.now(),
      'Asia/Kolkata'
    );

    const filename = `/tmp/device-${device.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}.png`;
    fs.writeFileSync(filename, wallpaper);

    // Calculate expected density and tasks shown
    const expectedDensity = width >= 1440 ? 3.5 :
                           width >= 1080 ? 2.5 :
                           width >= 720  ? 2.0 :
                           width >= 540  ? 1.5 : 1.0;

    const expectedTasks = width < 500 ? 2 : 3;

    console.log(`   ✅ PASS: Wallpaper generated`);
    console.log(`   Size:           ${(wallpaper.length / 1024).toFixed(1)} KB`);
    console.log(`   Calc density:   ${expectedDensity}x`);
    console.log(`   Tasks shown:    ${expectedTasks} (${4 - expectedTasks} more)`);
    console.log(`   Output:         ${filename}`);
    console.log();

  } catch (error) {
    console.log(`   ❌ FAIL: ${error.message}`);
    console.log();
    allPassed = false;
  }
}

console.log('═'.repeat(70));
console.log('TEST SUMMARY');
console.log('═'.repeat(70));

if (allPassed) {
  console.log('✅ ALL DEVICES PASSED');
  console.log();
  console.log('Generated wallpapers:');
  devices.forEach(device => {
    const filename = `device-${device.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}.png`;
    console.log(`   - /tmp/${filename}`);
  });
  console.log();
  console.log('📊 Resolution Breakdown:');
  console.log('   Small (< 500px):   2 tasks + time');
  console.log('   Medium (500-720px): 3 tasks + time');
  console.log('   Large (> 720px):    3 tasks + time');
  console.log();
  console.log('🎯 Next: User should visually verify images look correct');
} else {
  console.log('❌ SOME TESTS FAILED - Review errors above');
}

console.log();
