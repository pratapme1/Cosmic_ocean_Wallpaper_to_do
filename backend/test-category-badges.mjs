/**
 * Test Category Badges, Context Tags, and Energy Indicators (Fix #5)
 *
 * Tests the new UI elements in wallpaper text rendering:
 * - Category badges with symbols and colors
 * - Context tags (@home, @work, etc.)
 * - Energy level indicators (high/low)
 */

import wallpaperGenerator from './services/wallpaper-generator-enhanced.js';
import fs from 'fs';

const { generateEnhancedWallpaper } = wallpaperGenerator;

// Mock user
const user = {
  id: 'test-user-123',
  theme: 'cosmic' // Will test all 3 themes
};

// Test tasks with categories, context tags, and energy levels
const testTasks = [
  {
    id: '1',
    title: 'Email manager about project update',
    category: 'work',
    context_tags: ['@office', '@computer'],
    energy_level: 'medium',
    priority: 1,
    estimate_minutes: 15,
    due_date: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(), // 2 hours from now
  },
  {
    id: '2',
    title: 'Morning workout at gym',
    category: 'health',
    context_tags: ['@gym', '@morning'],
    energy_level: 'high',
    priority: 2,
    estimate_minutes: 45,
    due_date: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(), // Tomorrow
  },
  {
    id: '3',
    title: 'Review budget and pay bills',
    category: 'finance',
    context_tags: ['@home'],
    energy_level: 'low',
    priority: 3,
    estimate_minutes: 30,
    due_date: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(), // 2 days
  },
  {
    id: '4',
    title: 'Call mom about weekend plans',
    category: 'personal',
    context_tags: ['@phone'],
    energy_level: 'medium',
    priority: 3,
    estimate_minutes: 20,
    due_date: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(), // 3 days
  },
  {
    id: '5',
    title: 'Finish online JavaScript course',
    category: 'learning',
    context_tags: ['@computer', '@focus'],
    energy_level: 'high',
    priority: 2,
    estimate_minutes: 120,
    due_date: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString(), // 5 days
  },
  {
    id: '6',
    title: 'Meet friends for coffee',
    category: 'social',
    context_tags: ['@cafe'],
    energy_level: 'medium',
    priority: 3,
    estimate_minutes: 60,
    due_date: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(), // 1 week
  },
  {
    id: '7',
    title: 'Buy groceries and household items',
    category: 'errands',
    context_tags: ['@store'],
    energy_level: 'low',
    priority: 2,
    estimate_minutes: 40,
    due_date: new Date(Date.now() + 6 * 60 * 60 * 1000).toISOString(), // 6 hours
  },
];

const themes = ['cosmic', 'ocean', 'fantasy'];
const resolution = '1080x1920';

console.log('🧪 Testing Category Badges, Context Tags, and Energy Indicators (Fix #5)\n');
console.log('=' .repeat(80));

for (const theme of themes) {
  console.log(`\n📱 Theme: ${theme.toUpperCase()}`);
  console.log('-'.repeat(80));

  try {
    const startTime = Date.now();

    // Generate wallpaper with category badges
    const result = await generateEnhancedWallpaper(
      {
        ...user,
        theme,
        resolution,
        done_for_today: false
      },
      {
        tasks: testTasks,
        allTasks: testTasks
      }
    );

    const duration = Date.now() - startTime;

    // Save to file (convert ArrayBuffer to Buffer)
    const filename = `wallpaper-fix5-${theme}.png`;
    const buffer = Buffer.from(result.buffer);
    fs.writeFileSync(filename, buffer);

    console.log(`✅ Generated: ${filename}`);
    console.log(`   Size: ${(result.buffer.length / 1024).toFixed(2)} KB`);
    console.log(`   Duration: ${duration}ms`);
    console.log(`   Resolution: ${result.width}x${result.height}`);

    // Display what should be visible
    console.log('\n   Expected UI elements:');
    console.log('   ✓ Category badges: [■ WORK], [▲ HEALTH], [$ FINANCE]');
    console.log('   ✓ Context tags: @ OFFICE, @ GYM, @ HOME');
    console.log('   ✓ Energy indicators: ▲▲ (high), ▼ (low)');
    console.log('   ✓ Colored category backgrounds (subtle pastels)');

  } catch (error) {
    console.error(`❌ Error generating ${theme} wallpaper:`, error.message);
    console.error(error.stack);
  }
}

console.log('\n' + '='.repeat(80));
console.log('🎯 Test Summary:');
console.log('   • 3 themes tested (cosmic, ocean, fantasy)');
console.log('   • 7 categories tested (work, health, finance, personal, learning, social, errands)');
console.log('   • 3 energy levels tested (high, medium, low)');
console.log('   • Context tags displayed for each task');
console.log('\n📸 Visual Verification:');
console.log('   1. Open wallpaper-fix5-cosmic.png');
console.log('   2. Verify category badges visible with symbols');
console.log('   3. Verify context tags shown below estimates');
console.log('   4. Verify energy indicators (▲▲ for high energy tasks)');
console.log('   5. Check text is readable on all themes');
console.log('   6. Verify no visual clutter (clean layout)');
console.log('\n✨ Fix #5 Test Complete!\n');
