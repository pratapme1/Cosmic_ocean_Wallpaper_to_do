const express = require('express');
const { body, validationResult } = require('express-validator');
const { verifyToken } = require('../middleware/auth');

const router = express.Router();

// All sync routes require authentication
router.use(verifyToken);

/**
 * POST /api/sync
 * Synchronize pending changes and get latest data
 *
 * Request body:
 * {
 *   lastSyncAt: timestamp,
 *   pendingChanges: [
 *     {
 *       type: 'create' | 'update' | 'delete',
 *       entity: 'task',
 *       data: {...},
 *       clientId: 'uuid',
 *       timestamp: number
 *     }
 *   ]
 * }
 *
 * Response:
 * {
 *   syncedAt: timestamp,
 *   tasks: [...],
 *   conflicts: [...],
 *   applied: number,
 *   rejected: number
 * }
 */
router.post(
  '/',
  [
    body('lastSyncAt').optional().isInt(),
    body('pendingChanges').optional().isArray(),
    // Accept both 'action' and 'type' for backward compatibility
    body('pendingChanges.*').custom((change) => {
      const actionType = change.action || change.type;
      if (!actionType || !['create', 'update', 'delete'].includes(actionType)) {
        throw new Error('Invalid action/type. Must be create, update, or delete');
      }
      return true;
    }),
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
      const client = req.app.locals.dbClient;
      let { lastSyncAt, pendingChanges = [] } = req.body;

      // Normalize pending changes (map action->type, taskId->data.id, add entity)
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
        conflicts: []
      };

      // Process pending changes in order
      for (const change of pendingChanges) {
        try {
          if (change.entity === 'task') {
            const result = await applyTaskChange(client, userId, change);
            if (result.success) {
              syncResults.applied++;
            } else {
              syncResults.rejected++;
              syncResults.conflicts.push({
                clientId: change.clientId,
                reason: result.reason,
                serverData: result.serverData
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
        conflicts: syncResults.conflicts // For backward compatibility
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
 */
async function createTask(client, userId, data, clientId, timestamp) {
  // Check if task already exists (by client ID or server ID)
  const existingCheck = await client.query(
    'SELECT id, updated_at FROM tasks WHERE id = $1 OR (user_id = $2 AND title = $3)',
    [data.id || clientId, userId, data.title]
  );

  if (existingCheck.rows.length > 0) {
    // Task already exists - this is a conflict
    return {
      success: false,
      reason: 'already_exists',
      serverData: existingCheck.rows[0]
    };
  }

  // Create the task
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
    data.id || clientId,
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

  return { success: true, data: result.rows[0] };
}

/**
 * Update task from sync with last-write-wins
 */
async function updateTask(client, userId, data, clientId, timestamp) {
  // Get current task
  const currentTask = await client.query(
    'SELECT * FROM tasks WHERE id = $1 AND user_id = $2',
    [data.id || clientId, userId]
  );

  if (currentTask.rows.length === 0) {
    return {
      success: false,
      reason: 'not_found'
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
  const values = [data.id || clientId, userId];
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
  // Check if task exists
  const existingTask = await client.query(
    'SELECT updated_at FROM tasks WHERE id = $1 AND user_id = $2',
    [data.id || clientId, userId]
  );

  if (existingTask.rows.length === 0) {
    // Already deleted or never existed
    return { success: true };
  }

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
    [data.id || clientId, userId]
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
