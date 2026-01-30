const express = require('express');
const router = express.Router();
const pool = require('../db/pool');
const { optionalAuth } = require('../middleware/auth');

/**
 * GET /api/diag
 * Deep diagnostics for database and infrastructure
 */
router.get('/', optionalAuth, async (req, res) => {
    const stats = {
        timestamp: new Date().toISOString(),
        environment: {
            isVercel: process.env.VERCEL === 'true' || !!process.env.VERCEL,
            nodeVersion: process.version,
            region: process.env.VERCEL_REGION || 'unknown'
        },
        database: {
            urlPresent: !!process.env.DATABASE_URL,
            isPoolerUrl: process.env.DATABASE_URL?.includes('pooler.supabase.com') || process.env.DATABASE_URL?.includes(':6543'),
            poolStatus: {
                totalConnections: pool.getDbPool().totalCount,
                idleConnections: pool.getDbPool().idleCount,
                waitingRequests: pool.getDbPool().waitingCount
            }
        }
    };

    try {
        const start = Date.now();
        const result = await pool.query('SELECT version(), current_setting($1) as pgbouncer', ['session_replication_role']);
        stats.database.connectionSuccess = true;
        stats.database.latencyMs = Date.now() - start;
        stats.database.info = result.rows[0];

        // Check if on port 6543
        const urlParts = process.env.DATABASE_URL.match(/:(\d+)\//);
        stats.database.port = urlParts ? urlParts[1] : 'unknown';

        // Check pooler mode (Session vs Transaction)
        // Transaction pooler usually doesn't support SHOW POOLS, so we check the port
        if (stats.database.port === '6543') {
            stats.database.mode = 'Transaction Pooler (Correct)';
        } else if (stats.database.port === '5432') {
            stats.database.mode = 'Direct Connection (Too brittle for serverless)';
        }

        res.json(stats);
    } catch (err) {
        stats.database.connectionSuccess = false;
        stats.database.error = err.message;
        stats.database.errorCode = err.code;
        res.status(500).json(stats);
    }
});

module.exports = router;
