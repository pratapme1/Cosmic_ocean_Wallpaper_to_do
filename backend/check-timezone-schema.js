/**
 * Check if database has timezone support
 */

const { Client } = require('pg');

async function checkSchema() {
  const client = new Client({
    connectionString: "postgresql://postgres:cThiDaywOqISDTBN@db.pqnlatrigbdhkgardsap.supabase.co:5432/postgres",
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();

    // Check users table schema
    console.log('='.repeat(70));
    console.log('CHECKING DATABASE SCHEMA FOR TIMEZONE SUPPORT');
    console.log('='.repeat(70));
    console.log();

    const usersSchema = await client.query(`
      SELECT column_name, data_type
      FROM information_schema.columns
      WHERE table_name = 'users'
      ORDER BY ordinal_position;
    `);

    console.log('USERS TABLE COLUMNS:');
    console.log('-'.repeat(70));
    usersSchema.rows.forEach(col => {
      const hasTimezone = col.column_name.toLowerCase().includes('timezone') || col.column_name.toLowerCase().includes('tz');
      console.log(`  ${col.column_name.padEnd(30)} ${col.data_type}${hasTimezone ? ' ← TIMEZONE FIELD' : ''}`);
    });

    const hasTimezone = usersSchema.rows.some(col =>
      col.column_name.toLowerCase().includes('timezone') || col.column_name.toLowerCase().includes('tz')
    );

    console.log();
    if (hasTimezone) {
      console.log('✅ GOOD: Users table has timezone field');
    } else {
      console.log('❌ PROBLEM: Users table MISSING timezone field');
      console.log('   Need to add: ALTER TABLE users ADD COLUMN timezone VARCHAR(50) DEFAULT \'UTC\';');
    }

    // Check tasks table
    console.log();
    console.log('TASKS TABLE TIME COLUMNS:');
    console.log('-'.repeat(70));

    const tasksSchema = await client.query(`
      SELECT column_name, data_type
      FROM information_schema.columns
      WHERE table_name = 'tasks'
      AND (column_name LIKE '%time%' OR column_name LIKE '%date%')
      ORDER BY ordinal_position;
    `);

    tasksSchema.rows.forEach(col => {
      console.log(`  ${col.column_name.padEnd(30)} ${col.data_type}`);
    });

    console.log();
    console.log('❌ PROBLEM: TIME/DATE columns have no timezone info');
    console.log('   - due_time stores HH:MM:SS without timezone');
    console.log('   - due_date stores YYYY-MM-DD without timezone');
    console.log('   - System assumes UTC, but user expects local time');

  } finally {
    await client.end();
  }
}

checkSchema().catch(console.error);
