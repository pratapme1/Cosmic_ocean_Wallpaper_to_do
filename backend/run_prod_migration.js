const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

console.log('=== PRODUCTION MIGRATION RUNNER ===');
console.log('This script will apply the wallpaper timeout fix (index creation) to your database.');
console.log('Please paste your PRODUCTION database connection string (Postgres URL):');

rl.question('> ', async (dbUrl) => {
    if (!dbUrl || !dbUrl.startsWith('postgres')) {
        console.log('Using local environment DATABASE_URL if available...');
        require('dotenv').config({ path: path.join(__dirname, '../.env') });
        dbUrl = process.env.POSTGRES_URL || process.env.DATABASE_URL;
    }

    if (!dbUrl) {
        console.error('❌ No database URL provided or found in .env');
        process.exit(1);
    }

    console.log(`\nConnecting to: ${dbUrl.split('@')[1]}...`); // Mask auth details

    const pool = new Pool({
        connectionString: dbUrl.trim(),
        ssl: { rejectUnauthorized: false }, // Vercel/Neon usually require SSL
        connectionTimeoutMillis: 5000
    });

    try {
        // 1. Apply Index Fix
        const sqlPath = path.join(__dirname, 'migrations', '012_fix_wallpaper_timeout.sql');
        const sql = fs.readFileSync(sqlPath, 'utf8');

        console.log('Applying 012_fix_wallpaper_timeout.sql...');
        await pool.query(sql);
        console.log('✅ Index created successfully.');

        // 2. Verify Schema
        console.log('\nVerifying index existence...');
        const result = await pool.query(`
      SELECT indexname, indexdef 
      FROM pg_indexes 
      WHERE tablename = 'tasks' AND indexname = 'idx_tasks_summary';
    `);

        if (result.rows.length > 0) {
            console.log('✅ Verification PASS: idx_tasks_summary exists.');
        } else {
            console.error('❌ Verification FAIL: Index not found after creation.');
        }

    } catch (err) {
        console.error('❌ Migration failed:', err.message);
    } finally {
        await pool.end();
        rl.close();
    }
});
