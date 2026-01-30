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

  // Cleanse URL (remove whitespace/newlines which can happen in Vercel UI)
  let cleansedUrl = dbUrl.trim().replace(/[\r\n]/g, '');

  // CRITICAL: Use Supabase Connection Pooler instead of direct connection
  // Port 5432 = direct PostgreSQL (limited to ~60 connections)
  // Port 6543 = connection pooler (handles thousands via pooling)
  // This prevents connection exhaustion on Vercel serverless
  if (cleansedUrl.includes('supabase.co:5432')) {
    console.log('[DB] Detected Supabase direct connection, switching to pooler...');
    
    // Change port from 5432 to 6543 (pooler port)
    // The pooler uses same username/password as direct connection
    cleansedUrl = cleansedUrl.replace(':5432/', ':6543/');
    
    console.log('[DB] Connection pooler configured at port 6543');
  }

  console.log('[DB] Initializing Singleton Connection Pool...');
  const isVercel = process.env.VERCEL === 'true' || !!process.env.VERCEL;

  poolInstance = new Pool({
    connectionString: cleansedUrl,
    ssl: { rejectUnauthorized: false },
    // Using Supabase Connection Pooler (port 6543) which handles thousands of connections
    // So we can safely have more connections per Lambda instance
    max: isVercel ? 10 : 20,
    // Keep connections alive longer to avoid reconnection overhead
    idleTimeoutMillis: isVercel ? 30000 : 60000,
    // Connection timeout: How long to wait for a new connection
    connectionTimeoutMillis: isVercel ? 10000 : 5000,
    // Acquire timeout: How long to wait when all connections are in use
    acquireTimeoutMillis: isVercel ? 10000 : 5000,
    // Statement timeout: Max time for any query
    statement_timeout: 30000
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
