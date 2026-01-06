/**
 * Apply database migration 009 - Query optimization indexes
 */

const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

async function applyMigration() {
  const client = new Client({
    connectionString: process.env.DATABASE_URL || "postgresql://postgres:cThiDaywOqISDTBN@db.pqnlatrigbdhkgardsap.supabase.co:5432/postgres",
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();
    console.log('✅ Connected to database');

    // Read migration file
    const migrationPath = path.join(__dirname, 'migrations', '009_optimize_tasks_query.sql');
    const migration = fs.readFileSync(migrationPath, 'utf8');

    console.log('\n📋 Applying migration 009_optimize_tasks_query.sql...\n');
    console.log(migration);
    console.log('\n' + '='.repeat(70));

    // Execute migration
    await client.query(migration);

    console.log('✅ Migration applied successfully!\n');

    // Verify indexes created
    console.log('📊 Verifying indexes...\n');
    const indexQuery = `
      SELECT indexname, indexdef
      FROM pg_indexes
      WHERE tablename = 'tasks'
      AND indexname LIKE 'idx_tasks_%'
      ORDER BY indexname;
    `;

    const result = await client.query(indexQuery);

    if (result.rows.length > 0) {
      console.log('✅ Indexes created:');
      result.rows.forEach(row => {
        console.log(`   - ${row.indexname}`);
      });
    } else {
      console.log('⚠️  No indexes found (may have been created already)');
    }

    console.log('\n✅ Database migration complete!');

  } catch (error) {
    console.error('❌ Migration failed:', error.message);
    throw error;
  } finally {
    await client.end();
  }
}

applyMigration().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});
