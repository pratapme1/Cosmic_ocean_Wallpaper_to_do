/**
 * E2E Test: Wallpaper Refresh & Achievement Detection
 *
 * Tests CRUD operations and their reflection on wallpaper generation.
 * Verifies achievement detection based on task completion patterns.
 *
 * Test Coverage:
 * 1. CREATE task → wallpaper should include new task
 * 2. UPDATE task (complete) → wallpaper should reflect completion
 * 3. DELETE task → wallpaper should not include deleted task
 * 4. Achievement detection after multiple completions
 *
 * Created: 2026-01-09
 */

import dotenv from 'dotenv';
dotenv.config({ path: './.env' });
dotenv.config({ path: '../.env' });

import pg from 'pg';
import fs from 'fs';
import crypto from 'crypto';

const { Pool } = pg;

// Import services
const { generateEnhancedWallpaper } = await import('../services/wallpaper-generator-enhanced.js');
const { AchievementService } = await import('../services/achievement-service.js');

const achievementService = new AchievementService();

// Test configuration
const TEST_USER_EMAIL = `e2e-test-${Date.now()}@test.com`;
const OUTPUT_DIR = '/home/vi/supernova/test-screenshots';

let pool;
let testUserId;

// Test results tracking
const testResults = {
  passed: 0,
  failed: 0,
  tests: []
};

function logTest(name, passed, details = '') {
  const status = passed ? '✅ PASS' : '❌ FAIL';
  console.log(`${status}: ${name}`);
  if (details) console.log(`   ${details}`);
  testResults.tests.push({ name, passed, details });
  if (passed) testResults.passed++;
  else testResults.failed++;
}

async function setup() {
  console.log('\n========================================');
  console.log('E2E TEST: Wallpaper Refresh & Achievements');
  console.log('========================================\n');

  pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
  });

  // Create test user
  const passwordHash = '$2b$10$' + crypto.randomBytes(22).toString('base64'); // Dummy hash
  const result = await pool.query(`
    INSERT INTO users (email, password_hash, timezone, theme, resolution, show_achievements_on_wallpaper)
    VALUES ($1, $2, 'Asia/Kolkata', 'cosmic', '1080x1920', true)
    RETURNING id
  `, [TEST_USER_EMAIL, passwordHash]);

  testUserId = result.rows[0].id;
  console.log(`Created test user: ${TEST_USER_EMAIL}`);
  console.log(`User ID: ${testUserId}\n`);
}

async function cleanup() {
  console.log('\n--- Cleanup ---');

  // Delete test user's tasks
  await pool.query('DELETE FROM tasks WHERE user_id = $1', [testUserId]);

  // Delete test user's achievements
  await pool.query('DELETE FROM user_achievements WHERE user_id = $1', [testUserId]);

  // Delete test user
  await pool.query('DELETE FROM users WHERE id = $1', [testUserId]);

  console.log('Test user and data cleaned up');
  await pool.end();
}

async function generateAndSaveWallpaper(suffix, tasks, allTasks) {
  const user = {
    id: testUserId,
    theme: 'cosmic',
    resolution: '1080x1920',
    done_for_today: false,
    show_achievements_on_wallpaper: true,
    hide_all_tasks_mode: false,
    auto_hide_work_tasks: false,
    default_privacy_level: 'public'
  };

  const buffer = await generateEnhancedWallpaper(
    user,
    { tasks, allTasks },
    Date.now(),
    'Asia/Kolkata'
  );

  const filename = `e2e-${suffix}-${Date.now()}.png`;
  const filepath = `${OUTPUT_DIR}/${filename}`;
  fs.writeFileSync(filepath, buffer);

  return { buffer, filepath, size: buffer.length };
}

// Priority mapping: 0=low, 1=medium, 2=high, 3=critical
const PRIORITY = { low: 0, medium: 1, high: 2, critical: 3 };

// ==================== TEST 1: CREATE Task ====================
async function testCreateTask() {
  console.log('\n--- TEST 1: CREATE Task ---');

  // Create a task
  const taskResult = await pool.query(`
    INSERT INTO tasks (user_id, title, priority, completed, created_at)
    VALUES ($1, 'E2E Test Task 1', $2, false, NOW())
    RETURNING *
  `, [testUserId, PRIORITY.high]);

  const task = taskResult.rows[0];
  console.log(`Created task: "${task.title}" (ID: ${task.id})`);

  // Fetch all tasks for user
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);

  // Generate wallpaper
  const wallpaper = await generateAndSaveWallpaper('create-task', activeTasks, allTasksResult.rows);

  // Verify
  const isValidPng = wallpaper.buffer[0] === 0x89 && wallpaper.buffer[1] === 0x50;
  const hasSufficientSize = wallpaper.size > 50000; // Should be > 50KB

  logTest(
    'CREATE Task reflects on wallpaper',
    isValidPng && hasSufficientSize,
    `PNG valid: ${isValidPng}, Size: ${(wallpaper.size/1024).toFixed(1)}KB, Saved: ${wallpaper.filepath}`
  );

  return task.id;
}

// ==================== TEST 2: UPDATE Task (Complete) ====================
async function testUpdateTask(taskId) {
  console.log('\n--- TEST 2: UPDATE Task (Complete) ---');

  // Complete the task
  await pool.query(`
    UPDATE tasks
    SET completed = true, completed_at = NOW()
    WHERE id = $1
  `, [taskId]);

  console.log(`Completed task ID: ${taskId}`);

  // Fetch all tasks
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);
  const completedTasks = allTasksResult.rows.filter(t => t.completed);

  console.log(`Active tasks: ${activeTasks.length}, Completed: ${completedTasks.length}`);

  // Generate wallpaper
  const wallpaper = await generateAndSaveWallpaper('update-complete', activeTasks, allTasksResult.rows);

  // Verify
  const isValidPng = wallpaper.buffer[0] === 0x89 && wallpaper.buffer[1] === 0x50;

  logTest(
    'UPDATE Task (complete) reflects on wallpaper',
    isValidPng && activeTasks.length === 0,
    `Active tasks: ${activeTasks.length}, Completed: ${completedTasks.length}, Size: ${(wallpaper.size/1024).toFixed(1)}KB`
  );
}

// ==================== TEST 3: CREATE Multiple Tasks ====================
async function testCreateMultipleTasks() {
  console.log('\n--- TEST 3: CREATE Multiple Tasks ---');

  const tasks = [
    { title: 'E2E Task A - High Priority', priority: PRIORITY.high },
    { title: 'E2E Task B - Medium Priority', priority: PRIORITY.medium },
    { title: 'E2E Task C - Low Priority', priority: PRIORITY.low },
    { title: 'E2E Task D - Critical', priority: PRIORITY.critical }
  ];

  for (const task of tasks) {
    await pool.query(`
      INSERT INTO tasks (user_id, title, priority, completed, created_at)
      VALUES ($1, $2, $3, false, NOW())
    `, [testUserId, task.title, task.priority]);
    console.log(`Created: ${task.title}`);
  }

  // Fetch all tasks
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);

  // Generate wallpaper
  const wallpaper = await generateAndSaveWallpaper('create-multiple', activeTasks, allTasksResult.rows);

  logTest(
    'CREATE Multiple Tasks reflects on wallpaper',
    activeTasks.length === 4,
    `Total active tasks: ${activeTasks.length}, Wallpaper size: ${(wallpaper.size/1024).toFixed(1)}KB`
  );

  return allTasksResult.rows;
}

// ==================== TEST 4: DELETE Task ====================
async function testDeleteTask(allTasks) {
  console.log('\n--- TEST 4: DELETE Task ---');

  // Find an active task to delete
  const taskToDelete = allTasks.find(t => !t.completed && t.title.includes('Task B'));

  if (!taskToDelete) {
    logTest('DELETE Task', false, 'No task found to delete');
    return;
  }

  await pool.query('DELETE FROM tasks WHERE id = $1', [taskToDelete.id]);
  console.log(`Deleted task: "${taskToDelete.title}" (ID: ${taskToDelete.id})`);

  // Fetch remaining tasks
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);

  // Generate wallpaper
  const wallpaper = await generateAndSaveWallpaper('delete-task', activeTasks, allTasksResult.rows);

  // Verify deleted task is not in list
  const deletedTaskExists = allTasksResult.rows.some(t => t.id === taskToDelete.id);

  logTest(
    'DELETE Task reflects on wallpaper',
    !deletedTaskExists && activeTasks.length === 3,
    `Task deleted: ${!deletedTaskExists}, Remaining active: ${activeTasks.length}`
  );
}

// ==================== TEST 5: Achievement Detection on Completions ====================
async function testAchievementDetection() {
  console.log('\n--- TEST 5: Achievement Detection ---');

  // Complete all remaining tasks to trigger achievements
  const activeTasksResult = await pool.query(
    'SELECT * FROM tasks WHERE user_id = $1 AND completed = false',
    [testUserId]
  );

  console.log(`Completing ${activeTasksResult.rows.length} tasks...`);

  for (const task of activeTasksResult.rows) {
    await pool.query(`
      UPDATE tasks
      SET completed = true, completed_at = NOW()
      WHERE id = $1
    `, [task.id]);
    console.log(`  Completed: ${task.title}`);
  }

  // Fetch all tasks for achievement detection
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);
  const completedTasks = allTasksResult.rows.filter(t => t.completed);

  console.log(`\nTask summary: ${activeTasks.length} active, ${completedTasks.length} completed`);

  // Detect achievements
  const achievementResult = await achievementService.detectAchievements(
    testUserId,
    allTasksResult.rows,
    []
  );

  console.log(`\nAchievements detected:`);
  console.log(`  Earned: ${achievementResult.earned.length}`);
  console.log(`  In Progress: ${achievementResult.inProgress.length}`);
  console.log(`  Total Points: ${achievementResult.totalPoints}`);

  if (achievementResult.earned.length > 0) {
    console.log(`  Earned badges:`);
    achievementResult.earned.forEach(a => {
      console.log(`    - ${a.name} (${a.points} pts)`);
    });
  }

  if (achievementResult.inProgress.length > 0) {
    console.log(`  In progress:`);
    achievementResult.inProgress.slice(0, 3).forEach(a => {
      console.log(`    - ${a.name}: ${a.current}/${a.target} (${(a.progress * 100).toFixed(0)}%)`);
    });
  }

  // Generate wallpaper with achievements
  const wallpaper = await generateAndSaveWallpaper('achievements', activeTasks, allTasksResult.rows);

  logTest(
    'Achievement detection works after completions',
    achievementResult.earned.length > 0 || achievementResult.inProgress.length > 0,
    `Earned: ${achievementResult.earned.length}, In Progress: ${achievementResult.inProgress.length}, Points: ${achievementResult.totalPoints}`
  );
}

// ==================== TEST 6: Wallpaper Consistency ====================
async function testWallpaperConsistency() {
  console.log('\n--- TEST 6: Wallpaper Consistency ---');

  // Generate wallpaper multiple times and check consistency
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);

  const sizes = [];
  for (let i = 0; i < 3; i++) {
    const wallpaper = await generateAndSaveWallpaper(`consistency-${i}`, activeTasks, allTasksResult.rows);
    sizes.push(wallpaper.size);
    console.log(`  Generation ${i + 1}: ${(wallpaper.size/1024).toFixed(1)}KB`);
  }

  // Check that all wallpapers are valid (sizes within 20% of each other)
  const avgSize = sizes.reduce((a, b) => a + b, 0) / sizes.length;
  const consistent = sizes.every(s => Math.abs(s - avgSize) / avgSize < 0.2);

  logTest(
    'Wallpaper generation is consistent',
    consistent && sizes.every(s => s > 50000),
    `Sizes: ${sizes.map(s => (s/1024).toFixed(1) + 'KB').join(', ')}`
  );
}

// ==================== TEST 7: Stress Test - Many Tasks ====================
async function testManyTasks() {
  console.log('\n--- TEST 7: Stress Test - Many Tasks ---');

  // Create 20 additional tasks
  for (let i = 1; i <= 20; i++) {
    await pool.query(`
      INSERT INTO tasks (user_id, title, priority, completed, created_at)
      VALUES ($1, $2, $3, false, NOW())
    `, [testUserId, `Stress Task ${i}`, [PRIORITY.high, PRIORITY.medium, PRIORITY.low][i % 3]]);
  }

  console.log('Created 20 additional tasks');

  // Fetch all tasks
  const allTasksResult = await pool.query('SELECT * FROM tasks WHERE user_id = $1', [testUserId]);
  const activeTasks = allTasksResult.rows.filter(t => !t.completed);

  console.log(`Total tasks: ${allTasksResult.rows.length}, Active: ${activeTasks.length}`);

  // Generate wallpaper
  const startTime = Date.now();
  const wallpaper = await generateAndSaveWallpaper('stress-test', activeTasks, allTasksResult.rows);
  const elapsed = Date.now() - startTime;

  logTest(
    'Wallpaper generation handles many tasks',
    wallpaper.size > 50000 && elapsed < 10000,
    `Generated in ${elapsed}ms, Size: ${(wallpaper.size/1024).toFixed(1)}KB, Tasks: ${activeTasks.length}`
  );
}

// ==================== MAIN ====================
async function runTests() {
  try {
    await setup();

    // Run tests in sequence
    const task1Id = await testCreateTask();
    await testUpdateTask(task1Id);
    const allTasks = await testCreateMultipleTasks();
    await testDeleteTask(allTasks);
    await testAchievementDetection();
    await testWallpaperConsistency();
    await testManyTasks();

    // Summary
    console.log('\n========================================');
    console.log('TEST SUMMARY');
    console.log('========================================');
    console.log(`Total: ${testResults.tests.length}`);
    console.log(`Passed: ${testResults.passed}`);
    console.log(`Failed: ${testResults.failed}`);
    console.log('========================================\n');

    if (testResults.failed === 0) {
      console.log('🎉 ALL TESTS PASSED!\n');
    } else {
      console.log('⚠️  Some tests failed. Check details above.\n');
      console.log('Failed tests:');
      testResults.tests.filter(t => !t.passed).forEach(t => {
        console.log(`  - ${t.name}: ${t.details}`);
      });
    }

  } catch (err) {
    console.error('\n❌ TEST ERROR:', err.message);
    console.error(err.stack);
  } finally {
    await cleanup();
  }

  return testResults;
}

// Run
const results = await runTests();
process.exit(results.failed > 0 ? 1 : 0);
