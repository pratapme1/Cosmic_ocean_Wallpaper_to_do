/**
 * Check if NLP migration is needed and apply if necessary
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

    // Check if NLP columns exist
    const checkQuery = `
      SELECT column_name
      FROM information_schema.columns
      WHERE table_name = 'tasks'
        AND column_name IN ('category', 'context_tags', 'energy_level', 'raw_title')
      ORDER BY column_name;
    `;

    const result = await client.query(checkQuery);
    console.log(`\n📊 Found ${result.rows.length}/4 NLP columns in tasks table:`);
    result.rows.forEach(row => console.log(`  - ${row.column_name}`));

    if (result.rows.length === 4) {
      console.log('\n✅ Migration already applied - all NLP columns exist');
      return;
    }

    if (result.rows.length > 0 && result.rows.length < 4) {
      console.log('\n⚠️  Partial migration detected - some columns missing!');
      console.log('This should not happen. You may need to rollback and reapply.');
      return;
    }

    // Apply migration
    console.log('\n🔄 Applying NLP fields migration...');
    const migrationSQL = readFileSync(
      join(__dirname, 'migrations/007_nlp_fields.sql'),
      'utf-8'
    );

    await client.query(migrationSQL);
    console.log('✅ Migration applied successfully!');

    // Verify migration
    const verifyResult = await client.query(checkQuery);
    console.log(`\n✅ Verified: ${verifyResult.rows.length}/4 columns now exist`);
    verifyResult.rows.forEach(row => console.log(`  - ${row.column_name}`));

  } catch (err) {
    console.error('❌ Error:', err.message);
    process.exit(1);
  } finally {
    await client.end();
  }
}

checkAndMigrate();
