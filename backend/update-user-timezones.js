/**
 * Update Existing Users' Timezone
 *
 * This script updates users who have timezone='UTC' to their actual timezone.
 *
 * Usage:
 *   node update-user-timezones.js [timezone]
 *
 * Examples:
 *   node update-user-timezones.js Asia/Kolkata
 *   node update-user-timezones.js America/New_York
 */

require('dotenv').config();
const { Pool } = require('pg');

const DEFAULT_TIMEZONE = 'Asia/Kolkata';

async function updateUserTimezones() {
  const targetTimezone = process.argv[2] || DEFAULT_TIMEZONE;

  console.log('=========================================');
  console.log('UPDATE USER TIMEZONES');
  console.log('=========================================');
  console.log(`Target timezone: ${targetTimezone}`);
  console.log('');

  // Connect to database
  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
  });

  try {
    // Step 1: Show current users with UTC timezone
    console.log('Step 1: Finding users with timezone=UTC...');
    const beforeResult = await pool.query(`
      SELECT id, email, timezone, created_at
      FROM users
      WHERE timezone = 'UTC'
      ORDER BY created_at DESC
    `);

    if (beforeResult.rows.length === 0) {
      console.log('✅ No users with UTC timezone found. All users already have correct timezone.');
      process.exit(0);
    }

    console.log(`Found ${beforeResult.rows.length} user(s) with UTC timezone:\n`);

    beforeResult.rows.forEach((user, index) => {
      const date = new Date(user.created_at).toISOString().split('T')[0];
      console.log(`${index + 1}. ${user.email}`);
      console.log(`   ID: ${user.id}`);
      console.log(`   Current timezone: ${user.timezone}`);
      console.log(`   Created: ${date}`);
      console.log('');
    });

    // Step 2: Update timezones
    console.log('Step 2: Updating timezones...');
    console.log(`Updating all UTC users to: ${targetTimezone}`);
    console.log('');

    const updateResult = await pool.query(`
      UPDATE users
      SET timezone = $1, updated_at = NOW()
      WHERE timezone = 'UTC'
      RETURNING id, email, timezone
    `, [targetTimezone]);

    console.log(`✅ Updated ${updateResult.rowCount} user(s)\n`);

    // Step 3: Verify updates
    console.log('Step 3: Verifying updates...');
    const afterResult = await pool.query(`
      SELECT id, email, timezone
      FROM users
      WHERE id = ANY($1)
      ORDER BY email
    `, [updateResult.rows.map(r => r.id)]);

    afterResult.rows.forEach((user, index) => {
      console.log(`${index + 1}. ${user.email}`);
      console.log(`   Timezone: ${user.timezone} ✅`);
      console.log('');
    });

    // Step 4: Check if any UTC users remain
    const remainingUtc = await pool.query(`
      SELECT COUNT(*) as count
      FROM users
      WHERE timezone = 'UTC'
    `);

    if (remainingUtc.rows[0].count === '0') {
      console.log('✅ SUCCESS: All users have been updated from UTC');
    } else {
      console.log(`⚠️ WARNING: ${remainingUtc.rows[0].count} user(s) still have UTC timezone`);
    }

    console.log('');
    console.log('=========================================');
    console.log('UPDATE COMPLETE');
    console.log('=========================================');

  } catch (error) {
    console.error('❌ Error updating timezones:', error.message);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

// Run the update
updateUserTimezones();
