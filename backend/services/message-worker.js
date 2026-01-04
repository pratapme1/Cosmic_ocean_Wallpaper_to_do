/**
 * Epic 8: Message Generation Background Worker
 * Periodically generates messages for active users to keep cache fresh
 *
 * Features:
 * - Runs every 2 hours
 * - Only processes users active in last 24h
 * - Checks cache before generating
 * - Error handling per user (doesn't block entire job)
 * - Logs statistics
 */

const pool = require('../db/pool');
const { prefillCache } = require('./wallpaper-message-provider');

// Worker configuration
const WORKER_INTERVAL_MS = 2 * 60 * 60 * 1000;  // 2 hours
const ACTIVE_WINDOW_HOURS = 24;

/**
 * Get users active in last N hours
 */
async function getActiveUsers(hours = ACTIVE_WINDOW_HOURS) {
  if (!pool) {
    console.warn('[MessageWorker] Database not available');
    return [];
  }

  const client = await pool.connect();

  try {
    // Users who created or completed tasks recently
    const query = `
      SELECT DISTINCT u.id, u.email
      FROM users u
      INNER JOIN tasks t ON t.user_id = u.id
      WHERE t.created_at >= NOW() - INTERVAL '${hours} hours'
         OR t.updated_at >= NOW() - INTERVAL '${hours} hours'
         OR t.completed_at >= NOW() - INTERVAL '${hours} hours'
      ORDER BY u.id;
    `;

    const result = await client.query(query);
    return result.rows;

  } finally {
    client.release();
  }
}

/**
 * Run worker job - process all active users
 */
async function runWorkerJob() {
  const startTime = Date.now();
  console.log('\n=== [MessageWorker] Starting job ===');
  console.log(`Time: ${new Date().toISOString()}`);

  try {
    // Get active users
    const activeUsers = await getActiveUsers(ACTIVE_WINDOW_HOURS);
    console.log(`[MessageWorker] Found ${activeUsers.length} active users in last ${ACTIVE_WINDOW_HOURS}h`);

    if (activeUsers.length === 0) {
      console.log('[MessageWorker] No active users, skipping job');
      return {
        success: true,
        processed: 0,
        skipped: 0,
        errors: 0
      };
    }

    // Process each user
    const stats = {
      processed: 0,
      skipped: 0,
      errors: 0,
      totalGenerated: 0
    };

    for (const user of activeUsers) {
      try {
        console.log(`[MessageWorker] Processing user ${user.id} (${user.email})...`);

        const result = await prefillCache(user.id);

        if (result.prefilled) {
          console.log(`  ✅ Generated ${result.count} messages (${result.source}), cache now: ${result.remaining}`);
          stats.processed++;
          stats.totalGenerated += result.count || 0;
        } else {
          console.log(`  ⏭️  Skipped: ${result.reason || result.error}`);
          stats.skipped++;
        }

      } catch (error) {
        console.error(`  ❌ Error for user ${user.id}:`, error.message);
        stats.errors++;
        // Continue to next user (don't block entire job)
      }
    }

    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log('\n=== [MessageWorker] Job Complete ===');
    console.log(`Duration: ${duration}s`);
    console.log(`Processed: ${stats.processed} users`);
    console.log(`Skipped: ${stats.skipped} users`);
    console.log(`Errors: ${stats.errors} users`);
    console.log(`Total messages generated: ${stats.totalGenerated}`);
    console.log('=====================================\n');

    return {
      success: true,
      ...stats,
      duration
    };

  } catch (error) {
    console.error('[MessageWorker] Job failed:', error.message);
    console.error(error.stack);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Start worker (runs periodically)
 */
function startWorker() {
  console.log('[MessageWorker] Starting periodic worker...');
  console.log(`[MessageWorker] Interval: ${WORKER_INTERVAL_MS / 1000 / 60} minutes`);
  console.log(`[MessageWorker] Active window: ${ACTIVE_WINDOW_HOURS} hours`);

  // Run immediately on start
  setTimeout(() => {
    runWorkerJob().catch(err => {
      console.error('[MessageWorker] Initial job failed:', err);
    });
  }, 5000);  // 5 second delay to let server fully start

  // Run periodically
  const intervalId = setInterval(() => {
    runWorkerJob().catch(err => {
      console.error('[MessageWorker] Periodic job failed:', err);
    });
  }, WORKER_INTERVAL_MS);

  console.log('[MessageWorker] Worker started successfully');

  return intervalId;  // Return for stopping if needed
}

/**
 * Stop worker (for testing or shutdown)
 */
function stopWorker(intervalId) {
  if (intervalId) {
    clearInterval(intervalId);
    console.log('[MessageWorker] Worker stopped');
  }
}

module.exports = {
  startWorker,
  stopWorker,
  runWorkerJob,  // Export for manual testing
  getActiveUsers
};
