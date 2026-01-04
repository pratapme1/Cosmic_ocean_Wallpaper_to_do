/**
 * Database connection pool
 * Shared instance for all services
 */

const { Pool } = require('pg');

// Extract Supabase connection URL from environment
const dbUrl = process.env.DATABASE_URL;

if (!dbUrl) {
  console.warn('[DB] DATABASE_URL not set - database features disabled');
}

// Create pool instance
const pool = dbUrl ? new Pool({
  connectionString: dbUrl,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false,
  max: 10,  // Max connections
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000
}) : null;

if (pool) {
  pool.on('error', (err) => {
    console.error('[DB] Unexpected error on idle client', err);
  });

  console.log('[DB] Connection pool initialized');
}

module.exports = pool;
