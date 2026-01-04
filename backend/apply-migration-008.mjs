/**
 * Apply Epic 8 Message Intelligence migration (008)
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

async function checkAndMigrate() {
  try {
    await client.connect();
    console.log('✅ Connected to database');

    // Check if message_cache table exists
    const checkQuery = `
      SELECT table_name
      FROM information_schema.tables
      WHERE table_schema = 'public'
        AND table_name IN ('message_cache', 'message_history', 'parse_analytics')
      ORDER BY table_name;
    `;

    const result = await client.query(checkQuery);
    console.log(`\n📊 Found ${result.rows.length}/3 message intelligence tables:`);
    result.rows.forEach(row => console.log(`  - ${row.table_name}`));

    if (result.rows.length === 3) {
      console.log('\n✅ Migration 008 already applied - all tables exist');
      return;
    }

    if (result.rows.length > 0 && result.rows.length < 3) {
      console.log('\n⚠️  Partial migration detected - some tables missing!');
      console.log('This should not happen. You may need to rollback and reapply.');
      return;
    }

    // Apply migration
    console.log('\n🔄 Applying Message Intelligence migration (008)...');
    const migrationSQL = readFileSync(
      join(__dirname, 'migrations/008_message_intelligence.sql'),
      'utf-8'
    );

    await client.query(migrationSQL);
    console.log('✅ Migration 008 applied successfully!');

    // Verify migration
    const verifyResult = await client.query(checkQuery);
    console.log(`\n✅ Verified: ${verifyResult.rows.length}/3 tables now exist`);
    verifyResult.rows.forEach(row => console.log(`  - ${row.table_name}`));

    // Check indexes
    console.log('\n📊 Checking indexes...');
    const indexQuery = `
      SELECT indexname
      FROM pg_indexes
      WHERE tablename IN ('message_cache', 'message_history', 'parse_analytics')
      ORDER BY indexname;
    `;
    const indexResult = await client.query(indexQuery);
    console.log(`Found ${indexResult.rows.length} indexes:`);
    indexResult.rows.forEach(row => console.log(`  - ${row.indexname}`));

  } catch (err) {
    console.error('❌ Error:', err.message);
    console.error(err.stack);
    process.exit(1);
  } finally {
    await client.end();
  }
}

checkAndMigrate();
