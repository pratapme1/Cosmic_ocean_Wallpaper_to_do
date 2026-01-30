const fs = require('fs');
const path = require('path');
const { getDbPool } = require('./db/pool');

async function runMigrations() {
    console.log('[Migrator] Starting database migrations...');
    const pool = getDbPool();
    let client;

    try {
        client = await pool.connect();

        // Create migrations table if not exists
        await client.query(`
      CREATE TABLE IF NOT EXISTS migrations (
        id SERIAL PRIMARY KEY,
        name VARCHAR(255) UNIQUE NOT NULL,
        executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

        // Get list of migration files
        const migrationsDir = path.join(__dirname, 'migrations');
        const files = fs.readdirSync(migrationsDir)
            .filter(f => f.endsWith('.sql') && !f.includes('rollback'))
            .sort(); // Run in order

        for (const file of files) {
            // Check if already executed
            const check = await client.query('SELECT id FROM migrations WHERE name = $1', [file]);

            if (check.rows.length === 0) {
                console.log(`[Migrator] Applying migration: ${file}`);
                const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf8');

                await client.query('BEGIN');
                try {
                    await client.query(sql);
                    await client.query('INSERT INTO migrations (name) VALUES ($1)', [file]);
                    await client.query('COMMIT');
                    console.log(`[Migrator] ✅ Success: ${file}`);
                } catch (err) {
                    await client.query('ROLLBACK');
                    console.error(`[Migrator] ❌ Failed: ${file}`, err);
                    throw err;
                }
            } else {
                // console.log(`[Migrator] Skipping (already executed): ${file}`);
            }
        }
        console.log('[Migrator] All migrations checked/applied.');
    } catch (err) {
        console.error('[Migrator] Critical error:', err);
        throw err;
    } finally {
        if (client) client.release();
    }
}

module.exports = { runMigrations };
