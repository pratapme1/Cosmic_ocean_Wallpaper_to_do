/**
 * Epic 10 Phase 3: Environment Settings Migration
 * Adds environment settings columns to users table
 *
 * Run: node run-environment-migration.mjs
 */

import pg from 'pg';
import dotenv from 'dotenv';

dotenv.config();

const { Client } = pg;

async function runMigration() {
  // Use DATABASE_URL from environment or fallback for dev
  const connectionString = process.env.DATABASE_URL ||
    "postgresql://postgres:cThiDaywOqISDTBN@db.pqnlatrigbdhkgardsap.supabase.co:5432/postgres";

  const client = new Client({
    connectionString,
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();
    console.log('Connected to database');

    // Add environment settings columns if they don't exist
    const migrations = [
      {
        name: 'time_of_day_mode',
        sql: `ALTER TABLE users ADD COLUMN IF NOT EXISTS time_of_day_mode VARCHAR(20) DEFAULT 'auto'`
      },
      {
        name: 'manual_time_period',
        sql: `ALTER TABLE users ADD COLUMN IF NOT EXISTS manual_time_period VARCHAR(20) DEFAULT 'morning'`
      },
      {
        name: 'weather_overlay_enabled',
        sql: `ALTER TABLE users ADD COLUMN IF NOT EXISTS weather_overlay_enabled BOOLEAN DEFAULT TRUE`
      },
      {
        name: 'particle_intensity',
        sql: `ALTER TABLE users ADD COLUMN IF NOT EXISTS particle_intensity VARCHAR(20) DEFAULT 'medium'`
      }
    ];

    for (const migration of migrations) {
      console.log(`Running migration: ${migration.name}...`);
      try {
        await client.query(migration.sql);
        console.log(`  ✅ ${migration.name} - done`);
      } catch (err) {
        if (err.code === '42701') {
          // Column already exists
          console.log(`  ⚠️ ${migration.name} - already exists`);
        } else {
          throw err;
        }
      }
    }

    // Verify columns exist
    const verifyQuery = `
      SELECT column_name
      FROM information_schema.columns
      WHERE table_name = 'users'
      AND column_name IN ('time_of_day_mode', 'manual_time_period', 'weather_overlay_enabled', 'particle_intensity')
    `;
    const result = await client.query(verifyQuery);
    console.log(`\n✅ Verified ${result.rows.length} environment columns exist:`);
    result.rows.forEach(row => console.log(`   - ${row.column_name}`));

    console.log('\n🎉 Migration complete!');
  } catch (err) {
    console.error('Migration failed:', err);
    process.exit(1);
  } finally {
    await client.end();
  }
}

runMigration();
