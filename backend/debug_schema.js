
const { Pool } = require('pg');
require('dotenv').config({ path: '/home/vi/supernova/.env' });

const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
});

async function checkSchema() {
    try {
        const res = await pool.query(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'users';"
        );
        console.log("Columns in users table:", res.rows.map(r => r.column_name));
    } catch (e) {
        console.error(e);
    } finally {
        await pool.end();
    }
}

checkSchema();
