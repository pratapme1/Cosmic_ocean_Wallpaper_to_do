const express = require('express');
const { body, validationResult } = require('express-validator');
const crypto = require('crypto');
const { verifyToken } = require('../middleware/auth');

const router = express.Router();

// All sync routes require authentication
router.use(verifyToken);

/**
 * POST /api/sync
 * Synchronize pending changes and get latest data
 * 
 * CRITICAL FIX: Handles clientId → serverId mapping for local-first architecture
 *
 * Request body:
 * {
 *   lastSyncAt: timestamp,
 *   pendingChanges: [
 *     {
 *       type: 'create' | 'update' | 'delete',
 *       entity: 'task',
 *       data: {...},
 *       clientId: 'local-id',  // Android local ID (any format)
 *       timestamp: number
 *     }
 *   ]
 * }
 *
 * Response:
 * {
 *   syncedAt: timestamp,
 *   tasks: [...],
 *   results: { applied, rejected, skipped },
 *   conflicts: [...],
 *   mappings: [  // NEW: ClientId → ServerId mappings for successful creates
 *     { clientId: 'star-xxx', serverId: 'uuid', serverData: {...} }
 *   ]
 * }
 */
router.post(
  '/',
  [
    body('lastSyncAt').optional().isInt(),
    body('pendingChanges').optional().isArray(),
    body('pendingChanges.*.data').optional().isObject(),
    body('pendingChanges.*.clientId').isString(),
    body('pendingChanges.*.timestamp').isInt()
  ],
  async (req, res) => {
    try {
      // Validate input
      const errors = validationResult(req);
      if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
      }

      const userId = req.user.userId;
      const client = req.dbClient;
      let { lastSyncAt, pendingChanges = [] } = req.body;

      // Normalize pending changes
      pendingChanges = pendingChanges.map(change => ({
        type: change.action || change.type,
        entity: change.entity || 'task',
        data: {
          ...(change.data || {}),
          id: change.taskId || change.data?.id
        },
        clientId: change.clientId,
        timestamp: change.timestamp
      }));

      const syncResults = {
        applied: 0,
        rejected: 0,
        skipped: 0,
        conflicts: [],
        mappings: []  // CRITICAL FIX: Track successful mappings
      };

      // Process pending changes in order
      for (const change of pendingChanges) {
        if (!['create', 'update', 'delete'].includes(change.type)) {
          console.warn(`[Sync] Skipping invalid change type: ${change.type}`);
          syncResults.skipped++;
          continue;
        }

        try {
          if (change.entity === 'task') {
            const result = await applyTaskChange(client, userId, change);
            if (result.success) {
              syncResults.applied++;
              // CRITICAL FIX: If create succeeded, add mapping
              if (change.type === 'create' && result.clientId && result.serverId) {
                syncResults.mappings.push({
                  clientId: result.clientId,
                  serverId: result.serverId,
                  serverData: result.data
                });
              }
            } else {
              syncResults.rejected++;
              syncResults.conflicts.push({
                clientId: change.clientId,
                reason: result.reason,
                serverData: result.serverData,
                serverId: result.serverId  // Include serverId for already_exists
              });
            }
          }
        } catch (err) {
          console.error('Error applying change:', err);
          syncResults.rejected++;
          syncResults.conflicts.push({
            clientId: change.clientId,
            reason: 'server_error',
            error: err.message
          });
        }
      }

      // Get all current tasks (updated after sync)
      const tasksResult = await client.query(`
        SELECT * FROM tasks
        WHERE user_id = $1
        AND (updated_at > to_timestamp($2 / 1000.0) OR created_at > to_timestamp($2 / 1000.0))
        ORDER BY updated_at DESC
      `, [userId, lastSyncAt || 0]);

      res.json({
        syncedAt: Date.now(),
        tasks: tasksResult.rows,
        results: syncResults,
        conflicts: syncResults.conflicts,  // Backward compatibility
        mappings: syncResults.mappings  // NEW: ClientId → ServerId mappings
      });
    } catch (err) {
      console.error('Sync error:', err);
      res.status(500).json({ error: 'Synchronization failed' });
    }
  }
);

/**
 * Apply a task change with last-write-wins conflict resolution
 */
async function applyTaskChange(client, userId, change) {
  const { type, data, clientId, timestamp } = change;

  try {
    switch (type) {
      case 'create':
        return await createTask(client, userId, data, clientId, timestamp);

      case 'update':
        return await updateTask(client, userId, data, clientId, timestamp);

      case 'delete':
        return await deleteTask(client, userId, data, clientId, timestamp);

      default:
        return { success: false, reason: 'unknown_operation' };
    }
  } catch (err) {
    console.error('Apply change error:', err);
    return { success: false, reason: 'database_error', error: err.message };
  }
}

/**
 * Create task from sync
 * CRITICAL FIX: Generate proper server UUID, map clientId to serverId
 */
async function createTask(client, userId, data, clientId, timestamp) {
  // Generate a proper server UUID (PostgreSQL compatible)
  const serverId = crypto.randomUUID();
  
  // Check if task already exists by title (same user, same title = duplicate)
  const existingCheck = await client.query(
    'SELECT id, updated_at FROM tasks WHERE user_id = $1 AND title = $2 AND created_at > to_timestamp($3 / 1000.0) - INTERVAL \'1 minute\'',
    [userId, data.title, timestamp]
  );

  if (existingCheck.rows.length > 0) {
    return {
      success: false,
      reason: 'already_exists',
      serverData: existingCheck.rows[0],
      clientId: clientId,
      serverId: existingCheck.rows[0].id  // Return existing server ID
    };
  }

  // Create the task with server-generated UUID
  const query = `
    INSERT INTO tasks (
      id, user_id, title, estimate_minutes, priority, due_date, due_time,
      context_location, context_time, energy_required,
      x, y, is_subtask, is_recurring, echo_interval,
      created_at, updated_at
    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15,
              to_timestamp($16 / 1000.0), to_timestamp($16 / 1000.0))
    RETURNING *
  `;

  const values = [
    serverId,  // CRITICAL FIX: Use server-generated UUID
    userId,
    data.title || 'New Task',
    data.estimate_minutes || null,
    data.priority || 0,
    data.due_date || null,
    data.due_time || null,
    data.context_location || null,
    data.context_time || null,
    data.energy_required || null,
    data.x || null,
    data.y || null,
    data.is_subtask || false,
    data.is_recurring || false,
    data.echo_interval || null,
    timestamp
  ];

  const result = await client.query(query, values);

  return { 
    success: true, 
    data: result.rows[0],
    clientId: clientId,
    serverId: serverId
  };
}

/**
 * Update task from sync with last-write-wins
 */
async function updateTask(client, userId, data, clientId, timestamp) {
  // First try to find by server ID (if we have it)
  let currentTask = await client.query(
    'SELECT * FROM tasks WHERE id = $1 AND user_id = $2',
    [data.id || clientId, userId]
  );
  
  // If not found and data.id looks like a clientId, try to find by other means
  if (currentTask.rows.length === 0 && data.id && data.id.startsWith('star-')) {
    // This is a clientId, we need to find the corresponding server task
    // For now, try matching by title and recent creation
    currentTask = await client.query(
      'SELECT * FROM tasks WHERE user_id = $1 AND title = $2 AND created_at > to_timestamp($3 / 1000.0) - INTERVAL \'5 minutes\'',
      [userId, data.title, timestamp]
    );
  }

  if (currentTask.rows.length === 0) {
    return {
      success: false,
      reason: 'task_not_found'
    };
  }

  const serverTask = currentTask.rows[0];
  const serverTimestamp = new Date(serverTask.updated_at).getTime();

  // Last-write-wins: only apply if client change is newer
  if (timestamp < serverTimestamp) {
    return {
      success: false,
      reason: 'stale_data',
      serverData: serverTask
    };
  }

  // Build dynamic update query
  const updates = [];
  const values = [serverTask.id, userId];
  let paramIndex = 3;

  const allowedFields = [
    'title', 'estimate_minutes', 'priority', 'due_date', 'due_time',
    'completed', 'snoozed_until', 'context_location', 'context_time',
    'energy_required', 'x', 'y', 'is_subtask', 'is_recurring', 'echo_interval'
  ];

  for (const field of allowedFields) {
    if (data.hasOwnProperty(field)) {
      updates.push(`${field} = $${paramIndex++}`);
      values.push(data[field]);
    }
  }

  if (updates.length === 0) {
    return { success: true, data: serverTask };
  }

  // Add timestamp
  updates.push(`updated_at = to_timestamp($${paramIndex} / 1000.0)`);
  values.push(timestamp);

  // Add completed_at if marking as completed
  if (data.completed === true && !serverTask.completed) {
    updates.push(`completed_at = to_timestamp($${paramIndex} / 1000.0)`);
  }

  const query = `
    UPDATE tasks
    SET ${updates.join(', ')}
    WHERE id = $1 AND user_id = $2
    RETURNING *
  `;

  const result = await client.query(query, values);

  return { success: true, data: result.rows[0] };
}

/**
 * Delete task from sync
 */
async function deleteTask(client, userId, data, clientId, timestamp) {
  // Try to find the task - first by server ID, then try to match by title if it's a clientId
  let existingTask = await client.query(
    'SELECT id, updated_at FROM tasks WHERE id = $1 AND user_id = $2',
    [data.id || clientId, userId]
  );
  
  if (existingTask.rows.length === 0 && data.id && data.id.startsWith('star-')) {
    // Try to find by title if we have it
    if (data.title) {
      existingTask = await client.query(
        'SELECT id, updated_at FROM tasks WHERE user_id = $1 AND title = $2 ORDER BY created_at DESC LIMIT 1',
        [userId, data.title]
      );
    }
  }

  if (existingTask.rows.length === 0) {
    return { success: true };
  }

  const serverTaskId = existingTask.rows[0].id;
  const serverTimestamp = new Date(existingTask.rows[0].updated_at).getTime();

  // Last-write-wins: only delete if client change is newer
  if (timestamp < serverTimestamp) {
    return {
      success: false,
      reason: 'stale_data',
      serverData: existingTask.rows[0]
    };
  }

  // Delete the task
  await client.query(
    'DELETE FROM tasks WHERE id = $1 AND user_id = $2',
    [serverTaskId, userId]
  );

  return { success: true };
}

/**
 * GET /api/sync/status
 * Get sync status and last sync timestamp
 */
router.get('/status', async (req, res) => {
  try {
    const userId = req.user.userId;
    const client = req.app.locals.dbClient;

    // Get count of tasks
    const tasksResult = await client.query(
      'SELECT COUNT(*) as count FROM tasks WHERE user_id = $1',
      [userId]
    );

    // Get last modified timestamp
    const lastModifiedResult = await client.query(
      'SELECT MAX(updated_at) as last_modified FROM tasks WHERE user_id = $1',
      [userId]
    );

    res.json({
      taskCount: parseInt(tasksResult.rows[0].count),
      lastModified: lastModifiedResult.rows[0].last_modified
        ? new Date(lastModifiedResult.rows[0].last_modified).getTime()
        : null,
      serverTime: Date.now()
    });
  } catch (err) {
    console.error('Sync status error:', err);
    res.status(500).json({ error: 'Failed to get sync status' });
  }
});

module.exports = router;
