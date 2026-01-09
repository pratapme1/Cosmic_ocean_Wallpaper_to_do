/**
 * Test wallpaper generation with real production user
 */

import dotenv from 'dotenv';
dotenv.config({ path: './.env' });
dotenv.config({ path: '../.env' });

import pg from 'pg';
import fs from 'fs';

const { Pool } = pg;

// Import the wallpaper generator
const { generateEnhancedWallpaper } = await import('./services/wallpaper-generator-enhanced.js');

async function testWithRealUser() {
  console.log('=== TESTING WITH PRODUCTION DATABASE ===\n');

  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
  });

  try {
    // Get a real user
    const userResult = await pool.query('SELECT * FROM users LIMIT 1');
    if (userResult.rows.length === 0) {
      console.log('No users found in database');
      return;
    }

    const dbUser = userResult.rows[0];
    console.log('User:', dbUser.email);
    console.log('User ID:', dbUser.id);

    // Get user's tasks
    const tasksResult = await pool.query(
      'SELECT * FROM tasks WHERE user_id = $1 ORDER BY created_at DESC',
      [dbUser.id]
    );

    console.log('Total tasks:', tasksResult.rows.length);

    const completedTasks = tasksResult.rows.filter(t => t.completed);
    const activeTasks = tasksResult.rows.filter(t => !t.completed);

    console.log('Completed:', completedTasks.length);
    console.log('Active:', activeTasks.length);
    console.log('');

    // Build user object for wallpaper
    const user = {
      id: dbUser.id,
      theme: dbUser.theme || 'cosmic',
      resolution: dbUser.resolution || '1080x1920',
      done_for_today: dbUser.done_for_today || false,
      show_achievements_on_wallpaper: dbUser.show_achievements_on_wallpaper !== false,
      hide_all_tasks_mode: dbUser.hide_all_tasks_mode || false,
      auto_hide_work_tasks: dbUser.auto_hide_work_tasks || false,
      work_hours_start: dbUser.work_hours_start || '09:00',
      work_hours_end: dbUser.work_hours_end || '17:00',
      default_privacy_level: dbUser.default_privacy_level || 'public'
    };

    console.log('User preferences:');
    console.log('  - show_achievements_on_wallpaper:', user.show_achievements_on_wallpaper);
    console.log('  - theme:', user.theme);
    console.log('');

    console.log('Generating wallpaper for real user...');
    const buffer = await generateEnhancedWallpaper(
      user,
      { tasks: activeTasks.slice(0, 10), allTasks: tasksResult.rows },
      Date.now(),
      dbUser.timezone || 'Asia/Kolkata'
    );

    const outputPath = '/home/vi/supernova/test-screenshots/real-user-achievement-wallpaper.png';
    fs.writeFileSync(outputPath, buffer);

    console.log('');
    console.log('=== WALLPAPER SAVED ===');
    console.log('Path:', outputPath);
    console.log('Size:', (buffer.length / 1024).toFixed(1), 'KB');

  } catch (err) {
    console.error('ERROR:', err.message);
    console.error(err.stack);
  } finally {
    await pool.end();
  }
}

testWithRealUser();
