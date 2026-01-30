const express = require('express');
const router = express.Router();
const pool = require('../db/pool');
const { optionalAuth } = require('../middleware/auth');

/**
 * GET /api/diag
 * Deep diagnostics for database and infrastructure
 */
router.get('/', optionalAuth, async (req, res) => {
    const dbUrl = process.env.DATABASE_URL || '';
    const isSupabase = dbUrl.includes('supabase.co');
    const rawPort = dbUrl.match(/:(\d+)\//)?.[1] || 'unknown';
    const needsPoolerTransform = isSupabase && rawPort === '5432';
    
    const stats = {
        timestamp: new Date().toISOString(),
        environment: {
            isVercel: process.env.VERCEL === 'true' || !!process.env.VERCEL,
            nodeVersion: process.version,
            region: process.env.VERCEL_REGION || 'unknown'
        },
        database: {
            urlPresent: !!dbUrl,
            rawPort: rawPort,
            isSupabase: isSupabase,
            needsPoolerTransform: needsPoolerTransform,
            isPoolerUrl: dbUrl.includes('pooler.supabase.com') || rawPort === '6543',
            poolStatus: {
                totalConnections: pool.getDbPool().totalCount,
                idleConnections: pool.getDbPool().idleCount,
                waitingRequests: pool.getDbPool().waitingCount
            }
        }
    };

    try {
        const start = Date.now();
        // Simple query to test connection
        const result = await pool.query('SELECT version() as version, current_database() as database');
        stats.database.connectionSuccess = true;
        stats.database.latencyMs = Date.now() - start;
        stats.database.info = result.rows[0];
        stats.database.mode = needsPoolerTransform ? 'Using Pooler (port 6543)' : 'Direct Connection';

        res.json(stats);
    } catch (err) {
        stats.database.connectionSuccess = false;
        stats.database.error = err.message;
        stats.database.errorCode = err.code;
        stats.database.mode = needsPoolerTransform ? 'Pooler Transform Failed' : 'Direct Connection Failed';
        res.status(500).json(stats);
    }
});

module.exports = router;
