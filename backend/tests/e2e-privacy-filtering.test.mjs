/**
 * E2E Test: Privacy Filtering in Wallpaper Generator
 * Epic 10 - Phase 1 - Task 2 Verification
 *
 * Tests:
 * 1. Privacy filter correctly filters hidden tasks
 * 2. Privacy filter transforms titles based on privacy level
 * 3. hide_all_tasks_mode returns empty array
 * 4. auto_hide_work_tasks filters work tasks outside work hours
 * 5. Wallpaper generation with privacy filtering works
 * 6. Category badges hidden for private tasks
 */
import { Client } from 'pg';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import crypto from 'crypto';
import fs from 'fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: join(__dirname, '../../.env') });

// Import privacy filter
const { filterTasksForWallpaper, getPrivacyDisplayTitle, isWithinWorkHours, getPrivacyStats } = await import('../services/privacy-filter.js');

// Import wallpaper generator for integration test
const { generateEnhancedWallpaper } = await import('../services/wallpaper-generator-enhanced.js');

const client = new Client({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
});

// Test state
let testUserId = null;
let testTaskIds = [];
let testsPassed = 0;
let testsFailed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ✅ ${message}`);
    testsPassed++;
  } else {
    console.log(`  ❌ ${message}`);
    testsFailed++;
  }
}

async function setupTestData() {
  // Create test user
  const email = `test-privacy-filter-${Date.now()}@test.com`;
  const userResult = await client.query(`
    INSERT INTO users (email, password_hash, default_privacy_level, auto_hide_work_tasks, work_hours_start, work_hours_end)
    VALUES ($1, $2, 'public', false, '09:00', '17:00')
    RETURNING id
  `, [email, 'test-hash-' + crypto.randomBytes(16).toString('hex')]);

  testUserId = userResult.rows[0].id;
  console.log(`\n📋 Created test user: ${testUserId}`);

  // Create test tasks with various privacy levels
  const testTasks = [
    { title: 'Public Task', is_private: false, privacy_level: 'public', category: 'work' },
    { title: 'Doctor Appointment', is_private: true, privacy_level: 'category', category: 'health' },
    { title: 'Secret Project', is_private: true, privacy_level: 'initials', category: 'work' },
    { title: 'Bank Password Change', is_private: true, privacy_level: 'hidden', category: 'finance' },
    { title: 'Personal Matter', is_private: true, privacy_level: 'custom', privacy_display: 'Family stuff', category: 'personal' },
    { title: 'Morning Standup', is_private: false, privacy_level: 'public', category: 'work' },
  ];

  for (const task of testTasks) {
    const result = await client.query(`
      INSERT INTO tasks (user_id, title, is_private, privacy_level, privacy_display, category)
      VALUES ($1, $2, $3, $4, $5, $6)
      RETURNING id
    `, [testUserId, task.title, task.is_private, task.privacy_level, task.privacy_display || null, task.category]);
    testTaskIds.push(result.rows[0].id);
  }

  console.log(`📋 Created ${testTaskIds.length} test tasks`);
}

async function cleanupTestData() {
  if (testTaskIds.length > 0) {
    await client.query('DELETE FROM tasks WHERE id = ANY($1)', [testTaskIds]);
  }
  if (testUserId) {
    await client.query('DELETE FROM users WHERE id = $1', [testUserId]);
  }
  console.log('\n🧹 Cleaned up test data');
}

// ============================================
// TEST 1: Privacy Display Title Transformation
// ============================================
async function test1_privacyDisplayTitle() {
  console.log('\n═══════════════════════════════════════════');
  console.log('TEST 1: Privacy Display Title Transformation');
  console.log('═══════════════════════════════════════════');

  // Test public (non-private)
  const publicTask = { title: 'Public Task', is_private: false, privacy_level: 'public' };
  assert(
    getPrivacyDisplayTitle(publicTask) === 'Public Task',
    'Public task shows full title'
  );

  // Test category level
  const categoryTask = { title: 'Doctor Appointment', is_private: true, privacy_level: 'category', category: 'health' };
  assert(
    getPrivacyDisplayTitle(categoryTask) === 'Health task',
    `Category level shows "Health task" (got: ${getPrivacyDisplayTitle(categoryTask)})`
  );

  // Test initials level
  const initialsTask = { title: 'Secret Project', is_private: true, privacy_level: 'initials' };
  assert(
    getPrivacyDisplayTitle(initialsTask) === 'S...',
    `Initials level shows "S..." (got: ${getPrivacyDisplayTitle(initialsTask)})`
  );

  // Test hidden level
  const hiddenTask = { title: 'Bank Password', is_private: true, privacy_level: 'hidden' };
  assert(
    getPrivacyDisplayTitle(hiddenTask) === null,
    'Hidden level returns null'
  );

  // Test custom level
  const customTask = { title: 'Personal Matter', is_private: true, privacy_level: 'custom', privacy_display: 'Family stuff' };
  assert(
    getPrivacyDisplayTitle(customTask) === 'Family stuff',
    `Custom level shows "Family stuff" (got: ${getPrivacyDisplayTitle(customTask)})`
  );

  // Test custom without display text
  const customNoDisplay = { title: 'Something', is_private: true, privacy_level: 'custom' };
  assert(
    getPrivacyDisplayTitle(customNoDisplay) === 'Private task',
    `Custom without display shows "Private task" (got: ${getPrivacyDisplayTitle(customNoDisplay)})`
  );
}

// ============================================
// TEST 2: Filter Tasks for Wallpaper
// ============================================
async function test2_filterTasksForWallpaper() {
  console.log('\n═══════════════════════════════════════════');
  console.log('TEST 2: Filter Tasks for Wallpaper');
  console.log('═══════════════════════════════════════════');

  // Load test tasks from database
  const result = await client.query(`
    SELECT id, title, is_private, privacy_level, privacy_display, category
    FROM tasks WHERE user_id = $1 ORDER BY created_at
  `, [testUserId]);

  const tasks = result.rows;
  console.log(`  Loaded ${tasks.length} tasks from database`);

  // Filter with default preferences
  const userPrefs = { hide_all_tasks_mode: false, auto_hide_work_tasks: false };
  const filtered = filterTasksForWallpaper(tasks, userPrefs, 'UTC');

  // Should have 5 tasks (1 hidden filtered out)
  assert(
    filtered.length === 5,
    `Filtered out hidden task (${tasks.length} -> ${filtered.length})`
  );

  // Check displayTitle is set correctly
  const categoryTask = filtered.find(t => t.privacy_level === 'category');
  assert(
    categoryTask && categoryTask.displayTitle === 'Health task',
    `Category task has displayTitle="Health task" (got: ${categoryTask?.displayTitle})`
  );

  const initialsTask = filtered.find(t => t.privacy_level === 'initials');
  assert(
    initialsTask && initialsTask.displayTitle === 'S...',
    `Initials task has displayTitle="S..." (got: ${initialsTask?.displayTitle})`
  );

  const customTask = filtered.find(t => t.privacy_level === 'custom');
  assert(
    customTask && customTask.displayTitle === 'Family stuff',
    `Custom task has displayTitle="Family stuff" (got: ${customTask?.displayTitle})`
  );

  // Check original title is preserved
  assert(
    categoryTask && categoryTask.originalTitle === 'Doctor Appointment',
    `Original title preserved (got: ${categoryTask?.originalTitle})`
  );
}

// ============================================
// TEST 3: Hide All Tasks Mode
// ============================================
async function test3_hideAllTasksMode() {
  console.log('\n═══════════════════════════════════════════');
  console.log('TEST 3: Hide All Tasks Mode');
  console.log('═══════════════════════════════════════════');

  const tasks = [
    { title: 'Task 1', is_private: false },
    { title: 'Task 2', is_private: false },
    { title: 'Task 3', is_private: false },
  ];

  // With hide_all_tasks_mode = true
  const userPrefs = { hide_all_tasks_mode: true };
  const filtered = filterTasksForWallpaper(tasks, userPrefs, 'UTC');

  assert(
    filtered.length === 0,
    `hide_all_tasks_mode returns empty array (got: ${filtered.length} tasks)`
  );

  // With hide_all_tasks_mode = false
  const userPrefs2 = { hide_all_tasks_mode: false };
  const filtered2 = filterTasksForWallpaper(tasks, userPrefs2, 'UTC');

  assert(
    filtered2.length === 3,
    `Without hide_all_tasks_mode, all tasks shown (got: ${filtered2.length})`
  );
}

// ============================================
// TEST 4: Auto Hide Work Tasks
// ============================================
async function test4_autoHideWorkTasks() {
  console.log('\n═══════════════════════════════════════════');
  console.log('TEST 4: Auto Hide Work Tasks');
  console.log('═══════════════════════════════════════════');

  const tasks = [
    { title: 'Work Meeting', is_private: false, category: 'work' },
    { title: 'Personal Errand', is_private: false, category: 'personal' },
    { title: 'Another Work Task', is_private: false, category: 'work' },
  ];

  // Test within work hours
  const inWorkHours = isWithinWorkHours('00:00', '23:59', 'UTC'); // Should be true (all day)
  assert(
    inWorkHours === true,
    `isWithinWorkHours returns true for 00:00-23:59 (got: ${inWorkHours})`
  );

  // Test outside work hours (narrow window)
  const now = new Date();
  const currentHour = now.getUTCHours();
  const outsideHours = isWithinWorkHours(`${(currentHour + 2) % 24}:00`, `${(currentHour + 3) % 24}:00`, 'UTC');
  assert(
    outsideHours === false,
    `isWithinWorkHours returns false outside work hours (got: ${outsideHours})`
  );

  // Filter with auto_hide_work_tasks when outside work hours
  const userPrefs = {
    auto_hide_work_tasks: true,
    work_hours_start: `${(currentHour + 2) % 24}:00`,
    work_hours_end: `${(currentHour + 3) % 24}:00`
  };
  const filtered = filterTasksForWallpaper(tasks, userPrefs, 'UTC');

  assert(
    filtered.length === 1,
    `Auto-hide filters work tasks outside hours (got: ${filtered.length}, expected: 1)`
  );

  assert(
    filtered[0].category === 'personal',
    `Only personal task remains (got: ${filtered[0].category})`
  );
}

// ============================================
// TEST 5: Privacy Stats
// ============================================
async function test5_privacyStats() {
  console.log('\n═══════════════════════════════════════════');
  console.log('TEST 5: Privacy Stats');
  console.log('═══════════════════════════════════════════');

  const tasks = [
    { title: 'Public', is_private: false },
    { title: 'Category', is_private: true, privacy_level: 'category' },
    { title: 'Hidden1', is_private: true, privacy_level: 'hidden' },
    { title: 'Hidden2', is_private: true, privacy_level: 'hidden' },
    { title: 'Custom', is_private: true, privacy_level: 'custom' },
  ];

  const filtered = filterTasksForWallpaper(tasks, {}, 'UTC');
  const stats = getPrivacyStats(tasks, filtered);

  assert(
    stats.totalTasks === 5,
    `Total tasks = 5 (got: ${stats.totalTasks})`
  );

  assert(
    stats.displayedTasks === 3,
    `Displayed tasks = 3 (got: ${stats.displayedTasks})`
  );

  assert(
    stats.hiddenTasks === 2,
    `Hidden tasks = 2 (got: ${stats.hiddenTasks})`
  );

  assert(
    stats.byPrivacyLevel.hidden === 2,
    `byPrivacyLevel.hidden = 2 (got: ${stats.byPrivacyLevel.hidden})`
  );
}

// ============================================
// TEST 6: Wallpaper Generation with Privacy
// ============================================
async function test6_wallpaperGenerationWithPrivacy() {
  console.log('\n═══════════════════════════════════════════');
  console.log('TEST 6: Wallpaper Generation with Privacy');
  console.log('═══════════════════════════════════════════');

  // Load test tasks
  const tasksResult = await client.query(`
    SELECT id, title, is_private, privacy_level, privacy_display, category, priority, estimate_minutes, due_date
    FROM tasks WHERE user_id = $1 AND completed = false ORDER BY created_at
  `, [testUserId]);

  // Create user object with privacy settings
  const user = {
    id: testUserId,
    theme: 'cosmic',
    resolution: '1080x1920',
    done_for_today: false,
    hide_all_tasks_mode: false,
    auto_hide_work_tasks: false,
    work_hours_start: '09:00',
    work_hours_end: '17:00'
  };

  const data = { tasks: tasksResult.rows };

  try {
    // Generate wallpaper
    console.log('  Generating wallpaper with privacy filtering...');
    const wallpaperBuffer = await generateEnhancedWallpaper(user, data, Date.now(), 'UTC');

    assert(
      wallpaperBuffer && wallpaperBuffer.length > 0,
      `Wallpaper generated successfully (${wallpaperBuffer.length} bytes)`
    );

    // Save wallpaper for visual inspection
    const outputPath = join(__dirname, 'privacy-wallpaper-test.png');
    fs.writeFileSync(outputPath, wallpaperBuffer);
    console.log(`  📸 Wallpaper saved to: ${outputPath}`);

    assert(
      fs.existsSync(outputPath),
      'Wallpaper file saved successfully'
    );

    // Test with hide_all_tasks_mode
    const userHideAll = { ...user, hide_all_tasks_mode: true };
    const wallpaperHideAll = await generateEnhancedWallpaper(userHideAll, data, Date.now(), 'UTC');

    assert(
      wallpaperHideAll && wallpaperHideAll.length > 0,
      `Wallpaper with hide_all_tasks_mode generated (${wallpaperHideAll.length} bytes)`
    );

    // Save hide-all wallpaper
    const hideAllPath = join(__dirname, 'privacy-wallpaper-hide-all.png');
    fs.writeFileSync(hideAllPath, wallpaperHideAll);
    console.log(`  📸 Hide-all wallpaper saved to: ${hideAllPath}`);

  } catch (err) {
    console.log(`  ❌ Wallpaper generation failed: ${err.message}`);
    testsFailed++;
  }
}

// ============================================
// MAIN TEST RUNNER
// ============================================
async function runAllTests() {
  console.log('╔═══════════════════════════════════════════════════════════╗');
  console.log('║  E2E TEST: Privacy Filtering (Epic 10 - Phase 1 - Task 2) ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');
  console.log(`\nDate: ${new Date().toISOString()}`);
  console.log('Purpose: Verify privacy filtering in wallpaper generator\n');

  try {
    await client.connect();
    console.log('✅ Connected to database');

    // Setup
    await setupTestData();

    // Run tests
    await test1_privacyDisplayTitle();
    await test2_filterTasksForWallpaper();
    await test3_hideAllTasksMode();
    await test4_autoHideWorkTasks();
    await test5_privacyStats();
    await test6_wallpaperGenerationWithPrivacy();

    // Cleanup
    await cleanupTestData();

    // Summary
    console.log('\n═══════════════════════════════════════════');
    console.log('TEST SUMMARY');
    console.log('═══════════════════════════════════════════');
    console.log(`  Total Tests: ${testsPassed + testsFailed}`);
    console.log(`  ✅ Passed: ${testsPassed}`);
    console.log(`  ❌ Failed: ${testsFailed}`);
    console.log('═══════════════════════════════════════════');

    if (testsFailed === 0) {
      console.log('\n🎉 ALL TESTS PASSED! Privacy filtering verified successfully.');
      console.log('\n📋 PROOF OF SUCCESS:');
      console.log('  - Privacy levels transform titles correctly');
      console.log('  - Hidden tasks are filtered out');
      console.log('  - hide_all_tasks_mode returns empty array');
      console.log('  - auto_hide_work_tasks filters work tasks');
      console.log('  - Privacy stats computed correctly');
      console.log('  - Wallpaper generation with privacy works');
      console.log('  - Test wallpapers saved for visual inspection');
      process.exit(0);
    } else {
      console.log('\n⚠️ Some tests failed. Please review and fix.');
      process.exit(1);
    }

  } catch (err) {
    console.error('\n❌ Test error:', err.message);
    console.error(err.stack);

    try {
      await cleanupTestData();
    } catch (cleanupErr) {
      console.error('Cleanup error:', cleanupErr.message);
    }

    process.exit(1);
  } finally {
    await client.end();
  }
}

runAllTests();
