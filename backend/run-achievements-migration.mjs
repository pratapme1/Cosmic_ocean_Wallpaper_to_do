/**
 * Run Achievement Database Migration
 *
 * Executes migration 011 to create the achievements system tables.
 *
 * Usage: node run-achievements-migration.mjs
 *
 * Epic: 10 Phase 2 - Achievement System
 * Created: 2026-01-09
 */

import dotenv from 'dotenv';
// Load from backend .env first, then fall back to parent directory
dotenv.config({ path: './.env' });
dotenv.config({ path: '../.env' });
import pg from 'pg';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const { Pool } = pg;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

async function runMigration() {
  console.log('=== Achievement Migration Runner ===\n');

  // Verify environment
  const dbUrl = process.env.DATABASE_URL;
  if (!dbUrl) {
    console.error('ERROR: DATABASE_URL not set');
    process.exit(1);
  }

  console.log('Connecting to database...');
  const pool = new Pool({
    connectionString: dbUrl,
    ssl: { rejectUnauthorized: false }
  });

  try {
    // Test connection
    const testResult = await pool.query('SELECT NOW() as time');
    console.log('Connected:', testResult.rows[0].time);

    // Read migration SQL
    const migrationPath = path.join(__dirname, 'migrations', '011_achievements.sql');
    const migrationSQL = fs.readFileSync(migrationPath, 'utf8');

    console.log('\nRunning migration 011_achievements.sql...\n');

    // Execute migration
    await pool.query(migrationSQL);

    console.log('Migration completed successfully!\n');

    // Verify tables created
    console.log('Verifying tables...');

    // Check user_achievements table
    const tableCheck = await pool.query(`
      SELECT column_name, data_type, column_default
      FROM information_schema.columns
      WHERE table_name = 'user_achievements'
      ORDER BY ordinal_position
    `);

    console.log('\nuser_achievements table columns:');
    tableCheck.rows.forEach(row => {
      console.log(`  - ${row.column_name}: ${row.data_type}`);
    });

    // Check users table for new columns
    const usersCheck = await pool.query(`
      SELECT column_name, data_type, column_default
      FROM information_schema.columns
      WHERE table_name = 'users'
      AND column_name IN ('show_achievements_on_wallpaper', 'achievement_notification_enabled')
      ORDER BY column_name
    `);

    console.log('\nNew users table columns:');
    usersCheck.rows.forEach(row => {
      console.log(`  - ${row.column_name}: ${row.data_type} (default: ${row.column_default})`);
    });

    console.log('\n=== Migration Verified ===');

  } catch (err) {
    console.error('\nMigration FAILED:', err.message);
    console.error(err);
    process.exit(1);
  } finally {
    await pool.end();
    console.log('\nDatabase connection closed.');
  }
}

runMigration();
