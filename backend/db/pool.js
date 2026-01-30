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
    // CRITICAL for Vercel: Limited connections per lambda instance.
    // Supabase free tier allows ~60 connections. With Vercel's serverless nature,
    // we need a balance: enough for concurrent requests within one lambda,
    // but not so many that we exhaust Supabase limits across all lambdas.
    max: isVercel ? 3 : 10,
    // CRITICAL for Vercel: Release idle connections quickly to prevent
    // connection accumulation across lambda instances.
    idleTimeoutMillis: isVercel ? 500 : 10000,
    // Connection timeout: How long to wait for a connection from the pool
    connectionTimeoutMillis: isVercel ? 15000 : 5000,
    // Acquire timeout: How long to wait when all connections are in use
    acquireTimeoutMillis: isVercel ? 15000 : 5000,
    // Cancel pending requests if they wait too long (prevents queue buildup)
    allowExitOnIdle: isVercel ? true : false
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
