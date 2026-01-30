/**
 * E2E Tests: Offline Sync with Conflict Resolution
 * Tests sync endpoint, last-write-wins conflicts, and data consistency
 */

const request = require('supertest');
const app = require('../server');
const { createTestUser, createTestTask, updateTask, cleanupTestUser } = require('./helpers');

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

describe('Offline Sync with Conflict Resolution', () => {
  let accessToken;
  let userId;

  beforeEach(async () => {
    // Create a test user
    const user = await createTestUser(app);
    accessToken = user.accessToken;
    userId = user.user.id;
  });

  afterEach(async () => {
    // Cleanup
    await cleanupTestUser(app, accessToken);
  });

  describe('POST /api/sync - Basic Sync', () => {
    it('should sync successfully with no pending changes', async () => {
      const lastSyncAt = Date.now();

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt,
          pendingChanges: []
        })
        .expect(200);

      expect(response.body).toHaveProperty('tasks');
      expect(response.body).toHaveProperty('syncedAt');
      expect(response.body).toHaveProperty('conflicts');
      expect(Array.isArray(response.body.tasks)).toBe(true);
      expect(Array.isArray(response.body.conflicts)).toBe(true);
    });

    it('should return tasks created after lastSyncAt', async () => {
      // Initial sync
      const firstSyncResponse = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: []
        })
        .expect(200);

      const lastSyncAt = firstSyncResponse.body.syncedAt;

      // Wait a bit
      await new Promise(resolve => setTimeout(resolve, 100));

      // Create a new task after first sync
      const task = await createTestTask(app, accessToken, { rawTitle: 'New task after sync' });

      // Second sync - should return the new task
      const secondSyncResponse = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt,
          pendingChanges: []
        })
        .expect(200);

      expect(secondSyncResponse.body.tasks.length).toBeGreaterThan(0);
      const foundTask = secondSyncResponse.body.tasks.find(t => t.id === task.id);
      expect(foundTask).toBeTruthy();
      expect(foundTask.title).toBe('New task after sync');
    });

    it('should not return tasks created before lastSyncAt', async () => {
      // Create task
      await createTestTask(app, accessToken, { rawTitle: 'Old task' });

      // Wait a bit
      await new Promise(resolve => setTimeout(resolve, 100));

      // Sync
      const syncResponse = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: Date.now() + 1000, // Future timestamp
          pendingChanges: []
        })
        .expect(200);

      // Should return no tasks (all are before lastSyncAt)
      expect(syncResponse.body.tasks.length).toBe(0);
    });
  });

  describe('POST /api/sync - Pushing Changes', () => {
    it('should create new task from pending changes', async () => {
      const clientId = generateUUID();
      const timestamp = Date.now();

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'create',
              clientId,
              timestamp,
              data: {
                title: 'Offline created task',
                priority: 1,
                estimate_minutes: 30
              }
            }
          ]
        })
        .expect(200);

      expect(response.body.results).toBeTruthy();
      expect(response.body.results.applied).toBe(1);
      expect(response.body.results.conflicts.length).toBe(0);

      // Verify task was created
      const tasks = await request(app)
        .get('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      const createdTask = tasks.body.find(t => t.title === 'Offline created task');
      expect(createdTask).toBeTruthy();
      expect(createdTask.priority).toBe(1);
    });

    it('should update existing task from pending changes', async () => {
      // Create a task
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task to update' });

      // Wait a bit
      await new Promise(resolve => setTimeout(resolve, 100));

      // Sync with update
      const clientId = generateUUID();
      const timestamp = Date.now();

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'update',
              clientId,
              timestamp,
              taskId: task.id,
              data: {
                title: 'Updated offline',
                priority: 2
              }
            }
          ]
        })
        .expect(200);

      expect(response.body.results.applied).toBe(1);

      // Verify update
      const updatedTask = await request(app)
        .get(`/api/tasks/${task.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(updatedTask.body.title).toBe('Updated offline');
      expect(updatedTask.body.priority).toBe(2);
    });

    it('should delete task from pending changes', async () => {
      // Create a task
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task to delete' });

      // Wait
      await new Promise(resolve => setTimeout(resolve, 100));

      // Sync with delete
      const clientId = generateUUID();
      const timestamp = Date.now();

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'delete',
              clientId,
              timestamp,
              taskId: task.id
            }
          ]
        })
        .expect(200);

      expect(response.body.results.applied).toBe(1);

      // Verify deletion
      await request(app)
        .get(`/api/tasks/${task.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);
    });

    it('should handle multiple pending changes in order', async () => {
      const baseTimestamp = Date.now();

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'create',
              clientId: generateUUID(),
              timestamp: baseTimestamp,
              data: { title: 'Task 1', priority: 0 }
            },
            {
              action: 'create',
              clientId: generateUUID(),
              timestamp: baseTimestamp + 1,
              data: { title: 'Task 2', priority: 1 }
            },
            {
              action: 'create',
              clientId: generateUUID(),
              timestamp: baseTimestamp + 2,
              data: { title: 'Task 3', priority: 2 }
            }
          ]
        })
        .expect(200);

      expect(response.body.results.applied).toBe(3);

      // Verify all tasks were created
      const tasks = await request(app)
        .get('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(tasks.body.length).toBe(3);
    });
  });

  describe('POST /api/sync - Conflict Resolution (Last-Write-Wins)', () => {
    it('should detect conflict when client change is older than server', async () => {
      // Create task
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task' });

      // Wait
      await new Promise(resolve => setTimeout(resolve, 100));

      // Update on server
      await updateTask(app, accessToken, task.id, { title: 'Server update', priority: 2 });

      // Try to sync with older client change
      const oldTimestamp = Date.now() - 10000; // 10 seconds ago

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'update',
              clientId: generateUUID(),
              timestamp: oldTimestamp,
              taskId: task.id,
              data: { title: 'Client update', priority: 1 }
            }
          ]
        })
        .expect(200);

      // Should have a conflict
      expect(response.body.results.conflicts.length).toBe(1);
      expect(response.body.results.conflicts[0].reason).toBe('stale_data');
      expect(response.body.results.conflicts[0].serverData).toBeTruthy();

      // Server version should win
      const currentTask = await request(app)
        .get(`/api/tasks/${task.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(currentTask.body.title).toBe('Server update');
      expect(currentTask.body.priority).toBe(2);
    });

    it('should apply client change when it is newer than server', async () => {
      // Create task with old timestamp
      const task = await createTestTask(app, accessToken, { rawTitle: 'Old task' });

      // Wait
      await new Promise(resolve => setTimeout(resolve, 100));

      // Sync with newer client change
      const newTimestamp = Date.now() + 1000; // 1 second in future

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'update',
              clientId: generateUUID(),
              timestamp: newTimestamp,
              taskId: task.id,
              data: { title: 'Newer client update', priority: 2 }
            }
          ]
        })
        .expect(200);

      // Should be applied (no conflict)
      expect(response.body.results.applied).toBe(1);
      expect(response.body.results.conflicts.length).toBe(0);

      // Client version should win
      const currentTask = await request(app)
        .get(`/api/tasks/${task.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(currentTask.body.title).toBe('Newer client update');
    });

    it('should handle conflict on non-existent task', async () => {
      const fakeTaskId = 99999;

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'update',
              clientId: generateUUID(),
              timestamp: Date.now(),
              taskId: fakeTaskId,
              data: { title: 'Update to non-existent task' }
            }
          ]
        })
        .expect(200);

      // Should have a conflict
      expect(response.body.results.conflicts.length).toBe(1);
      expect(response.body.results.conflicts[0].reason).toBe('task_not_found');
    });

    it('should return conflicts and applied changes separately', async () => {
      // Create task
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task' });

      // Wait
      await new Promise(resolve => setTimeout(resolve, 100));

      // Update on server
      await updateTask(app, accessToken, task.id, { title: 'Server update' });

      // Sync with one good change and one conflicting change
      const oldTimestamp = Date.now() - 10000;
      const newTimestamp = Date.now();

      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'update',
              clientId: generateUUID(),
              timestamp: oldTimestamp,
              taskId: task.id,
              data: { title: 'Conflicting update' }
            },
            {
              action: 'create',
              clientId: generateUUID(),
              timestamp: newTimestamp,
              data: { title: 'Good new task', priority: 1 }
            }
          ]
        })
        .expect(200);

      expect(response.body.results.applied).toBe(1); // One applied
      expect(response.body.results.conflicts.length).toBe(1); // One conflict
    });
  });

  describe('POST /api/sync - Error Handling', () => {
    it('should require authentication', async () => {
      await request(app)
        .post('/api/sync')
        .send({
          lastSyncAt: 0,
          pendingChanges: []
        })
        .expect(401);
    });

    it('should validate lastSyncAt parameter', async () => {
      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          // Missing lastSyncAt
          pendingChanges: []
        })
        .expect(400);

      expect(response.body).toHaveProperty('errors');
    });

    it('should handle invalid pending changes gracefully', async () => {
      const response = await request(app)
        .post('/api/sync')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({
          lastSyncAt: 0,
          pendingChanges: [
            {
              action: 'invalid-action',
              clientId: generateUUID(),
              timestamp: Date.now()
            }
          ]
        })
        .expect(200);

      // Should skip invalid changes
      expect(response.body.results.skipped).toBeGreaterThan(0);
    });
  });
});
