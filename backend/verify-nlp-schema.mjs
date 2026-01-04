/**
 * Verify all 8 NLP columns exist in tasks table
 */
import { Client } from 'pg';
import dotenv from 'dotenv';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: join(__dirname, '../.env') });

const client = new Client({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
});

async function verifySchema() {
  try {
    await client.connect();

    const query = `
      SELECT column_name, data_type, is_nullable
      FROM information_schema.columns
      WHERE table_name = 'tasks'
        AND column_name IN (
          'category', 'context_tags', 'energy_level', 'time_context',
          'recurring_interval', 'recurring_day_of_week', 'recurring_day_of_month', 'raw_title'
        )
      ORDER BY column_name;
    `;

    const result = await client.query(query);

    console.log(`\n📊 NLP Columns Status: ${result.rows.length}/8 columns exist\n`);
    console.log('Column Name                | Data Type        | Nullable');
    console.log('---------------------------|------------------|----------');

    const expectedColumns = [
      'category',
      'context_tags',
      'energy_level',
      'raw_title',
      'recurring_day_of_month',
      'recurring_day_of_week',
      'recurring_interval',
      'time_context'
    ];

    expectedColumns.forEach(colName => {
      const found = result.rows.find(r => r.column_name === colName);
      if (found) {
        console.log(`✅ ${found.column_name.padEnd(25)} | ${found.data_type.padEnd(16)} | ${found.is_nullable}`);
      } else {
        console.log(`❌ ${colName.padEnd(25)} | MISSING`);
      }
    });

    // Check indexes
    const indexQuery = `
      SELECT indexname, indexdef
      FROM pg_indexes
      WHERE tablename = 'tasks'
        AND indexname IN ('idx_tasks_category', 'idx_tasks_energy_level', 'idx_tasks_context_tags');
    `;

    const indexResult = await client.query(indexQuery);
    console.log(`\n📊 Indexes: ${indexResult.rows.length}/3 exist\n`);
    indexResult.rows.forEach(row => {
      console.log(`✅ ${row.indexname}`);
    });

    if (result.rows.length === 8 && indexResult.rows.length === 3) {
      console.log('\n✅ Migration fully applied - all columns and indexes exist!');
    } else {
      console.log('\n⚠️  Migration incomplete');
    }

  } catch (err) {
    console.error('❌ Error:', err.message);
  } finally {
    await client.end();
  }
}

verifySchema();
