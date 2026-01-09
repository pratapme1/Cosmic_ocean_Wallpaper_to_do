/**
 * Apply Privacy Fields Migration (010)
 * Epic 10 - Wallpaper Experience Enhancement - Phase 1
 */
import { Client } from 'pg';
import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Load environment
dotenv.config({ path: join(__dirname, '../.env') });

const client = new Client({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
});

async function runPrivacyMigration() {
  try {
    await client.connect();
    console.log('✅ Connected to database');

    // Check if privacy columns already exist in tasks table
    const checkTasksQuery = `
      SELECT column_name
      FROM information_schema.columns
      WHERE table_name = 'tasks'
        AND column_name IN ('is_private', 'privacy_level', 'privacy_display')
      ORDER BY column_name;
    `;

    const tasksResult = await client.query(checkTasksQuery);
    console.log(`\n📊 Tasks table: Found ${tasksResult.rows.length}/3 privacy columns:`);
    tasksResult.rows.forEach(row => console.log(`  - ${row.column_name}`));

    // Check if privacy columns already exist in users table
    const checkUsersQuery = `
      SELECT column_name
      FROM information_schema.columns
      WHERE table_name = 'users'
        AND column_name IN ('default_privacy_level', 'auto_hide_work_tasks', 'work_hours_start', 'work_hours_end', 'biometric_reveal_enabled', 'hide_all_tasks_mode')
      ORDER BY column_name;
    `;

    const usersResult = await client.query(checkUsersQuery);
    console.log(`\n📊 Users table: Found ${usersResult.rows.length}/6 privacy columns:`);
    usersResult.rows.forEach(row => console.log(`  - ${row.column_name}`));

    // Check if migration already applied
    if (tasksResult.rows.length === 3 && usersResult.rows.length === 6) {
      console.log('\n✅ Migration 010 already applied - all privacy columns exist');
      return { success: true, alreadyApplied: true };
    }

    // Apply migration
    console.log('\n🔄 Applying privacy fields migration (010)...');
    const migrationSQL = readFileSync(
      join(__dirname, 'migrations/010_privacy_fields.sql'),
      'utf-8'
    );

    await client.query(migrationSQL);
    console.log('✅ Migration applied successfully!');

    // Verify migration - Tasks
    const verifyTasksResult = await client.query(checkTasksQuery);
    console.log(`\n✅ Tasks table verification: ${verifyTasksResult.rows.length}/3 columns`);
    verifyTasksResult.rows.forEach(row => console.log(`  - ${row.column_name}`));

    // Verify migration - Users
    const verifyUsersResult = await client.query(checkUsersQuery);
    console.log(`\n✅ Users table verification: ${verifyUsersResult.rows.length}/6 columns`);
    verifyUsersResult.rows.forEach(row => console.log(`  - ${row.column_name}`));

    // Test inserting a task with privacy fields
    console.log('\n🧪 Testing privacy fields...');

    // Get a test user (or create one for testing)
    const testUserQuery = await client.query('SELECT id FROM users LIMIT 1');
    if (testUserQuery.rows.length > 0) {
      const userId = testUserQuery.rows[0].id;

      // Insert test task with privacy fields
      const insertResult = await client.query(`
        INSERT INTO tasks (user_id, title, is_private, privacy_level, privacy_display)
        VALUES ($1, 'Privacy Test Task', true, 'custom', 'Personal matter')
        RETURNING id, title, is_private, privacy_level, privacy_display
      `, [userId]);

      console.log('✅ Test task created with privacy fields:');
      console.log(JSON.stringify(insertResult.rows[0], null, 2));

      // Clean up test task
      await client.query('DELETE FROM tasks WHERE id = $1', [insertResult.rows[0].id]);
      console.log('✅ Test task cleaned up');
    }

    // Test user privacy preferences
    if (testUserQuery.rows.length > 0) {
      const userId = testUserQuery.rows[0].id;

      // Update user with privacy preferences
      const updateResult = await client.query(`
        UPDATE users
        SET default_privacy_level = 'category',
            auto_hide_work_tasks = true,
            work_hours_start = '09:00',
            work_hours_end = '17:00',
            biometric_reveal_enabled = true,
            hide_all_tasks_mode = false
        WHERE id = $1
        RETURNING id, default_privacy_level, auto_hide_work_tasks, work_hours_start, work_hours_end
      `, [userId]);

      console.log('\n✅ User privacy preferences updated:');
      console.log(JSON.stringify(updateResult.rows[0], null, 2));

      // Reset to defaults
      await client.query(`
        UPDATE users
        SET default_privacy_level = 'public',
            auto_hide_work_tasks = false
        WHERE id = $1
      `, [userId]);
      console.log('✅ User preferences reset to defaults');
    }

    console.log('\n🎉 Migration 010 complete! Privacy fields are ready to use.');
    return { success: true, alreadyApplied: false };

  } catch (err) {
    console.error('❌ Error:', err.message);
    console.error(err.stack);
    process.exit(1);
  } finally {
    await client.end();
  }
}

runPrivacyMigration();
