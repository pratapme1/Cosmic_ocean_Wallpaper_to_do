const { Pool } = require('pg');
const { MockClient } = require('../utils/mock-client');

let poolInstance = null;

function getDbPool() {
  if (poolInstance) return poolInstance;

  const dbUrl = process.env.DATABASE_URL || process.env.POSTGRES_URL;

  if (!dbUrl) {
    console.warn('[DB] DATABASE_URL not set - using Mock Client');
    poolInstance = new MockClient();
    return poolInstance;
  }

  console.log('[DB] Initializing Singleton Connection Pool...');
  const isVercel = process.env.VERCEL === 'true' || !!process.env.VERCEL;

  poolInstance = new Pool({
    connectionString: dbUrl,
    ssl: { rejectUnauthorized: false },
    // CRITICAL for Vercel: Single connection per lambda instance.
    // This prevents "retry storms" from multiplying connections and hitting Supabase limits.
    max: isVercel ? 1 : 10,
    idleTimeoutMillis: 10000,
    connectionTimeoutMillis: isVercel ? 10000 : 5000 // Give pooler time to queue
  });

  poolInstance.on('connect', () => {
    console.log('[DB] New client connected to database');
  });

  poolInstance.on('acquire', () => {
    console.log('[DB] Client acquired from pool');
  });

  poolInstance.on('error', (err) => {
    console.error('[DB] Unexpected error on idle client', err);
    // Don't exit process in serverless, just log. 
    // The pool might recover or next lambda invocation will create new one.
  });

  return poolInstance;
}

// Export singleton accessors
module.exports = {
  getDbPool,
  // Helper for direct queries (auto-connect/release) - Safest way to query
  query: (text, params) => getDbPool().query(text, params),
  // Helper for transactions - acquire a client manually
  connect: () => getDbPool().connect(),
};
