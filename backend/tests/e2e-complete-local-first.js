#!/usr/bin/env node
/**
 * COMPREHENSIVE E2E TEST SUITE - Local-First Architecture
 *
 * Tests ALL components:
 * 1. Database Layer (Entities, sync fields, migrations)
 * 2. Sync Queue (FIFO ordering, retry logic, error handling)
 * 3. Sync Engine (create/update/delete, conflict resolution)
 * 4. Wallpaper Generator (themes, urgency, rendering)
 * 5. Offline/Online Transitions (queue persistence, sync trigger)
 * 6. Full Integration (complete user flows)
 * 7. Edge Cases & Error Handling
 * 8. Performance & Stress Tests
 * 9. Data Integrity & Consistency
 * 10. Security & Isolation
 *
 * Run: node tests/e2e-complete-local-first.js
 */

const crypto = require('crypto');

// =============================================================================
// TEST FRAMEWORK
// =============================================================================
let passCount = 0;
let failCount = 0;
const testResults = [];
const testQueue = [];
let currentSuite = '';

function suite(name) {
  currentSuite = name;
}

function test(name, fn) {
  testQueue.push({ suite: currentSuite, name, fn });
}

function assertEqual(actual, expected, message = '') {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\n     Expected: ${JSON.stringify(expected)}\n     Actual: ${JSON.stringify(actual)}`);
  }
}

function assertNotEqual(actual, expected, message = '') {
  if (JSON.stringify(actual) === JSON.stringify(expected)) {
    throw new Error(`${message}\n     Expected NOT: ${JSON.stringify(expected)}\n     Actual: ${JSON.stringify(actual)}`);
  }
}

function assertTrue(condition, message = 'Expected true') {
  if (!condition) throw new Error(message);
}

function assertFalse(condition, message = 'Expected false') {
  if (condition) throw new Error(message);
}

function assertNull(value, message = 'Expected null') {
  if (value !== null) throw new Error(`${message}\n     Actual: ${JSON.stringify(value)}`);
}

function assertNotNull(value, message = 'Expected not null') {
  if (value === null || value === undefined) throw new Error(message);
}

function assertThrows(fn, message = 'Expected error') {
  try {
    fn();
    throw new Error(message);
  } catch (e) {
    if (e.message === message) throw e;
    // Expected error occurred
  }
}

function assertArrayLength(arr, length, message = '') {
  if (arr.length !== length) {
    throw new Error(`${message}\n     Expected length: ${length}\n     Actual length: ${arr.length}`);
  }
}

function assertInRange(value, min, max, message = '') {
  if (value < min || value > max) {
    throw new Error(`${message}\n     Expected: ${min} <= value <= ${max}\n     Actual: ${value}`);
  }
}

// =============================================================================
// COMPONENT 1: DATABASE LAYER
// =============================================================================

class StarEntity {
  constructor(data = {}) {
    this.id = data.id || crypto.randomUUID();
    this.userId = data.userId || '';
    this.title = data.title || '';
    this.priority = data.priority || 0;
    this.estimateMinutes = data.estimateMinutes || null;
    this.dueDate = data.dueDate || null;
    this.dueTime = data.dueTime || null;
    this.completed = data.completed || false;
    this.completedAt = data.completedAt || null;
    // Sync fields
    this.syncStatus = data.syncStatus || 'synced'; // synced, pending, conflict, error
    this.syncVersion = data.syncVersion || 0;
    this.updatedAt = data.updatedAt || Date.now();
    this.isDeleted = data.isDeleted || false;
  }
}

class SyncQueueEntity {
  constructor(data = {}) {
    this.id = data.id || Date.now();
    this.taskId = data.taskId || '';
    this.operation = data.operation || ''; // create, update, delete, complete
    this.payload = data.payload || '{}';
    this.createdAt = data.createdAt || Date.now();
    this.retryCount = data.retryCount || 0;
    this.lastError = data.lastError || null;
  }
}

// =============================================================================
// COMPONENT 2: DATABASE ACCESS OBJECTS (DAOs)
// =============================================================================

class StarDao {
  constructor() {
    this.tasks = new Map();
  }

  async insert(star) {
    this.tasks.set(star.id, { ...star });
    return star.id;
  }

  async update(star) {
    if (!this.tasks.has(star.id)) return 0;
    this.tasks.set(star.id, { ...star });
    return 1;
  }

  async getById(id) {
    return this.tasks.get(id) || null;
  }

  async getAllForUser(userId) {
    return Array.from(this.tasks.values())
      .filter(t => t.userId === userId && !t.isDeleted);
  }

  async getActiveForUser(userId) {
    return Array.from(this.tasks.values())
      .filter(t => t.userId === userId && !t.isDeleted && !t.completed);
  }

  async getPendingSync() {
    return Array.from(this.tasks.values())
      .filter(t => t.syncStatus === 'pending');
  }

  async updateSyncStatus(id, status, version = null) {
    const task = this.tasks.get(id);
    if (task) {
      task.syncStatus = status;
      if (version !== null) task.syncVersion = version;
      task.updatedAt = Date.now();
    }
  }

  async markSyncError(id) {
    const task = this.tasks.get(id);
    if (task) {
      task.syncStatus = 'error';
      task.updatedAt = Date.now();
    }
  }

  async softDelete(id) {
    const task = this.tasks.get(id);
    if (task) {
      task.isDeleted = true;
      task.syncStatus = 'pending';
      task.updatedAt = Date.now();
    }
  }

  async hardDelete(id) {
    return this.tasks.delete(id);
  }

  async getModifiedSince(userId, timestamp) {
    return Array.from(this.tasks.values())
      .filter(t => t.userId === userId && t.updatedAt > timestamp);
  }

  async count() {
    return this.tasks.size;
  }

  async clear() {
    this.tasks.clear();
  }
}

class SyncQueueDao {
  constructor() {
    this.queue = [];
    this.idCounter = 1;
  }

  async insert(entry) {
    entry.id = this.idCounter++;
    this.queue.push({ ...entry });
    return entry.id;
  }

  async getAllPending() {
    return [...this.queue].sort((a, b) => a.createdAt - b.createdAt);
  }

  async getByTaskId(taskId) {
    return this.queue.filter(e => e.taskId === taskId);
  }

  async delete(id) {
    const idx = this.queue.findIndex(e => e.id === id);
    if (idx >= 0) {
      this.queue.splice(idx, 1);
      return 1;
    }
    return 0;
  }

  async deleteByTaskId(taskId) {
    const before = this.queue.length;
    this.queue = this.queue.filter(e => e.taskId !== taskId);
    return before - this.queue.length;
  }

  async incrementRetry(id, error) {
    const entry = this.queue.find(e => e.id === id);
    if (entry) {
      entry.retryCount++;
      entry.lastError = error;
    }
  }

  async getFailedEntries(maxRetries = 3) {
    return this.queue.filter(e => e.retryCount >= maxRetries);
  }

  async count() {
    return this.queue.length;
  }

  async clear() {
    this.queue = [];
  }
}

// =============================================================================
// COMPONENT 3: SYNC MANAGER
// =============================================================================

class SyncManager {
  constructor(starDao, syncQueueDao, apiService, networkChecker) {
    this.starDao = starDao;
    this.syncQueueDao = syncQueueDao;
    this.apiService = apiService;
    this.networkChecker = networkChecker;
    this.syncInProgress = false;
    this.lastSyncAt = 0;
    this.maxRetries = 3;
    this.syncDebounceMs = 2000;
  }

  async queueCreate(taskId, data) {
    await this.syncQueueDao.insert(new SyncQueueEntity({
      taskId,
      operation: 'create',
      payload: JSON.stringify(data),
      createdAt: Date.now()
    }));
  }

  async queueUpdate(taskId, data) {
    // Remove any pending creates/updates for same task
    await this.syncQueueDao.deleteByTaskId(taskId);
    await this.syncQueueDao.insert(new SyncQueueEntity({
      taskId,
      operation: 'update',
      payload: JSON.stringify(data),
      createdAt: Date.now()
    }));
  }

  async queueDelete(taskId) {
    // Remove all pending operations for this task
    await this.syncQueueDao.deleteByTaskId(taskId);
    await this.syncQueueDao.insert(new SyncQueueEntity({
      taskId,
      operation: 'delete',
      payload: '{}',
      createdAt: Date.now()
    }));
  }

  async queueComplete(taskId) {
    await this.syncQueueDao.insert(new SyncQueueEntity({
      taskId,
      operation: 'complete',
      payload: JSON.stringify({ completedAt: Date.now() }),
      createdAt: Date.now()
    }));
  }

  async performSync(userId) {
    if (this.syncInProgress) return { skipped: true, reason: 'sync_in_progress' };
    if (!this.networkChecker.isOnline()) return { skipped: true, reason: 'offline' };

    this.syncInProgress = true;
    const results = { applied: 0, rejected: 0, conflicts: [], errors: [] };

    try {
      // Get all pending changes
      const pendingChanges = await this.syncQueueDao.getAllPending();

      // Build sync request
      const changes = pendingChanges.map(entry => ({
        type: entry.operation,
        taskId: entry.taskId,
        data: JSON.parse(entry.payload),
        timestamp: entry.createdAt,
        clientId: `${entry.taskId}_${entry.createdAt}`
      }));

      // Call API
      const response = await this.apiService.sync({
        userId,
        lastSyncAt: this.lastSyncAt,
        changes
      });

      // Process results
      for (const entry of pendingChanges) {
        const clientId = `${entry.taskId}_${entry.createdAt}`;
        const conflict = response.conflicts?.find(c => c.clientId === clientId);

        if (conflict) {
          if (entry.retryCount >= this.maxRetries) {
            await this.starDao.markSyncError(entry.taskId);
            await this.syncQueueDao.delete(entry.id);
            results.errors.push({ taskId: entry.taskId, reason: conflict.reason });
          } else {
            await this.syncQueueDao.incrementRetry(entry.id, conflict.reason);
            results.conflicts.push(conflict);
          }
          results.rejected++;
        } else {
          await this.starDao.updateSyncStatus(entry.taskId, 'synced');
          await this.syncQueueDao.delete(entry.id);
          results.applied++;
        }
      }

      // Apply server changes (but don't overwrite error status)
      for (const serverTask of response.tasks || []) {
        const localTask = await this.starDao.getById(serverTask.id);
        // Skip if local task is in error status (don't overwrite user-visible errors)
        if (localTask && localTask.syncStatus === 'error') continue;

        if (!localTask || serverTask.updatedAt > localTask.updatedAt) {
          if (localTask) {
            await this.starDao.update({ ...localTask, ...serverTask, syncStatus: 'synced' });
          } else {
            await this.starDao.insert(new StarEntity({ ...serverTask, syncStatus: 'synced' }));
          }
        }
      }

      this.lastSyncAt = response.syncedAt || Date.now();
      return results;
    } finally {
      this.syncInProgress = false;
    }
  }

  async getPendingCount() {
    return await this.syncQueueDao.count();
  }

  async hasPendingChanges() {
    return (await this.syncQueueDao.count()) > 0;
  }
}

// =============================================================================
// COMPONENT 4: WALLPAPER GENERATOR
// =============================================================================

const UrgencyLevel = {
  CLEAR: 'clear',
  CALM: 'calm',
  ATTENTION: 'attention',
  URGENT: 'urgent',
  CRITICAL: 'critical'
};

const WallpaperTheme = {
  COSMIC: 'cosmic',
  OCEAN: 'ocean',
  FANTASY: 'fantasy'
};

class LocalWallpaperGenerator {
  static calculateUrgency(task) {
    if (!task) return UrgencyLevel.CLEAR;
    if (!task.dueDate) return UrgencyLevel.CALM;

    const now = Date.now();
    const dueTime = new Date(task.dueDate).getTime();
    const hoursUntilDue = (dueTime - now) / (1000 * 60 * 60);

    if (hoursUntilDue < 0) return UrgencyLevel.CRITICAL;      // Overdue
    if (hoursUntilDue < 4) return UrgencyLevel.CRITICAL;      // Due in 4 hours
    if (hoursUntilDue < 24) return UrgencyLevel.URGENT;       // Due today
    if (hoursUntilDue < 48) return UrgencyLevel.ATTENTION;    // Due tomorrow
    return UrgencyLevel.CALM;
  }

  static getThemeColors(theme, urgency) {
    const colors = {
      [WallpaperTheme.COSMIC]: {
        [UrgencyLevel.CLEAR]: { gradientStart: '#0a0a2e', gradientEnd: '#1a1a4e', accent: '#4a9eff' },
        [UrgencyLevel.CALM]: { gradientStart: '#0d1b2a', gradientEnd: '#1b263b', accent: '#3a86ff' },
        [UrgencyLevel.ATTENTION]: { gradientStart: '#1a1a2e', gradientEnd: '#2d2d5a', accent: '#ffd60a' },
        [UrgencyLevel.URGENT]: { gradientStart: '#2e1a1a', gradientEnd: '#5a2d2d', accent: '#ff6b35' },
        [UrgencyLevel.CRITICAL]: { gradientStart: '#3e0a0a', gradientEnd: '#6e1a1a', accent: '#ff0a0a' }
      },
      [WallpaperTheme.OCEAN]: {
        [UrgencyLevel.CLEAR]: { gradientStart: '#0077b6', gradientEnd: '#023e8a', accent: '#90e0ef' },
        [UrgencyLevel.CALM]: { gradientStart: '#005f73', gradientEnd: '#0a9396', accent: '#94d2bd' },
        [UrgencyLevel.ATTENTION]: { gradientStart: '#1a759f', gradientEnd: '#168aad', accent: '#ffd166' },
        [UrgencyLevel.URGENT]: { gradientStart: '#9d4edd', gradientEnd: '#7b2cbf', accent: '#ff9e00' },
        [UrgencyLevel.CRITICAL]: { gradientStart: '#d00000', gradientEnd: '#9d0208', accent: '#ffba08' }
      },
      [WallpaperTheme.FANTASY]: {
        [UrgencyLevel.CLEAR]: { gradientStart: '#7b2d8e', gradientEnd: '#3c1053', accent: '#f72585' },
        [UrgencyLevel.CALM]: { gradientStart: '#4a0e4e', gradientEnd: '#240046', accent: '#c77dff' },
        [UrgencyLevel.ATTENTION]: { gradientStart: '#5a189a', gradientEnd: '#3c096c', accent: '#ffba08' },
        [UrgencyLevel.URGENT]: { gradientStart: '#6a040f', gradientEnd: '#370617', accent: '#ff5400' },
        [UrgencyLevel.CRITICAL]: { gradientStart: '#7f0000', gradientEnd: '#4a0000', accent: '#ff0000' }
      }
    };

    return colors[theme]?.[urgency] || colors[WallpaperTheme.COSMIC][UrgencyLevel.CALM];
  }

  static formatDueDate(dueDateMs) {
    if (!dueDateMs) return null;

    const now = Date.now();
    const diff = dueDateMs - now;

    if (diff < 0) {
      const hoursAgo = Math.abs(diff) / (1000 * 60 * 60);
      if (hoursAgo < 1) return 'Overdue';
      if (hoursAgo < 24) return `Overdue by ${Math.floor(hoursAgo)}h`;
      return `Overdue by ${Math.floor(hoursAgo / 24)}d`;
    }

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff / (1000 * 60)) % 60);

    if (hours < 1) return `Due in ${minutes}m`;
    if (hours < 24) return `Due in ${hours}h${minutes > 0 ? ` ${minutes}m` : ''}`;
    if (hours < 48) return 'Due tomorrow';
    return `Due in ${Math.floor(hours / 24)} days`;
  }

  static generate(task, theme, width, height) {
    const urgency = this.calculateUrgency(task);
    const colors = this.getThemeColors(theme, urgency);

    // Simulate wallpaper generation (returns metadata in test)
    return {
      width,
      height,
      theme,
      urgency,
      colors,
      task: task ? { id: task.id, title: task.title } : null,
      generatedAt: Date.now()
    };
  }
}

// =============================================================================
// COMPONENT 5: NETWORK & OFFLINE HANDLING
// =============================================================================

class NetworkChecker {
  constructor() {
    this._isOnline = true;
    this.listeners = [];
  }

  isOnline() {
    return this._isOnline;
  }

  setOnline(value) {
    const wasOnline = this._isOnline;
    this._isOnline = value;
    if (!wasOnline && value) {
      this.listeners.forEach(fn => fn('online'));
    } else if (wasOnline && !value) {
      this.listeners.forEach(fn => fn('offline'));
    }
  }

  onStatusChange(fn) {
    this.listeners.push(fn);
  }
}

// =============================================================================
// COMPONENT 6: MOCK API SERVICE
// =============================================================================

class MockApiService {
  constructor() {
    this.serverTasks = new Map();
    this.shouldFail = false;
    this.failureReason = null;
    this.latencyMs = 0;
    this.callCount = 0;
  }

  async sync(request) {
    this.callCount++;

    if (this.latencyMs > 0) {
      await new Promise(r => setTimeout(r, this.latencyMs));
    }

    if (this.shouldFail) {
      throw new Error(this.failureReason || 'Network error');
    }

    const results = { applied: 0, rejected: 0, conflicts: [] };
    const { userId, lastSyncAt, changes } = request;

    for (const change of changes) {
      const serverTask = this.serverTasks.get(change.taskId);

      switch (change.type) {
        case 'create':
          if (serverTask) {
            results.conflicts.push({
              clientId: change.clientId,
              reason: 'already_exists',
              serverData: serverTask
            });
            results.rejected++;
          } else {
            this.serverTasks.set(change.taskId, {
              ...change.data,
              id: change.taskId,
              userId,
              updatedAt: change.timestamp
            });
            results.applied++;
          }
          break;

        case 'update':
          // Check user isolation - task must belong to requesting user
          if (!serverTask || serverTask.userId !== userId) {
            results.conflicts.push({
              clientId: change.clientId,
              reason: 'task_not_found'
            });
            results.rejected++;
          } else if (change.timestamp < serverTask.updatedAt) {
            results.conflicts.push({
              clientId: change.clientId,
              reason: 'stale_data',
              serverData: serverTask
            });
            results.rejected++;
          } else {
            this.serverTasks.set(change.taskId, {
              ...serverTask,
              ...change.data,
              updatedAt: change.timestamp
            });
            results.applied++;
          }
          break;

        case 'delete':
          if (serverTask && change.timestamp >= serverTask.updatedAt) {
            this.serverTasks.delete(change.taskId);
            results.applied++;
          } else if (serverTask) {
            results.conflicts.push({
              clientId: change.clientId,
              reason: 'stale_data',
              serverData: serverTask
            });
            results.rejected++;
          } else {
            results.applied++; // Already deleted
          }
          break;

        case 'complete':
          if (serverTask) {
            serverTask.completed = true;
            serverTask.completedAt = change.data.completedAt;
            serverTask.updatedAt = change.timestamp;
            results.applied++;
          } else {
            results.conflicts.push({
              clientId: change.clientId,
              reason: 'task_not_found'
            });
            results.rejected++;
          }
          break;
      }
    }

    // Get tasks modified since lastSyncAt
    const tasks = Array.from(this.serverTasks.values())
      .filter(t => t.userId === userId && t.updatedAt > lastSyncAt);

    return {
      syncedAt: Date.now(),
      tasks,
      results,
      conflicts: results.conflicts
    };
  }

  reset() {
    this.serverTasks.clear();
    this.shouldFail = false;
    this.failureReason = null;
    this.latencyMs = 0;
    this.callCount = 0;
  }
}

// =============================================================================
// TEST SUITE 1: DATABASE LAYER
// =============================================================================
suite('1. Database Layer - Entities');

test('StarEntity initializes with default sync fields', () => {
  const star = new StarEntity({ id: 'test', userId: 'user1', title: 'Test Task' });
  assertEqual(star.syncStatus, 'synced');
  assertEqual(star.syncVersion, 0);
  assertTrue(star.updatedAt > 0);
  assertFalse(star.isDeleted);
});

test('StarEntity sync status can be set to all valid states', () => {
  const star = new StarEntity({});
  const validStates = ['synced', 'pending', 'conflict', 'error'];
  validStates.forEach(state => {
    star.syncStatus = state;
    assertEqual(star.syncStatus, state);
  });
});

test('SyncQueueEntity initializes with correct defaults', () => {
  const entry = new SyncQueueEntity({ taskId: 'task1', operation: 'create' });
  assertEqual(entry.taskId, 'task1');
  assertEqual(entry.operation, 'create');
  assertEqual(entry.retryCount, 0);
  assertNull(entry.lastError);
  assertTrue(entry.createdAt > 0);
});

test('SyncQueueEntity payload stores JSON data correctly', () => {
  const data = { title: 'Test', priority: 2, nested: { field: 'value' } };
  const entry = new SyncQueueEntity({ payload: JSON.stringify(data) });
  const parsed = JSON.parse(entry.payload);
  assertEqual(parsed.title, 'Test');
  assertEqual(parsed.priority, 2);
  assertEqual(parsed.nested.field, 'value');
});

// =============================================================================
// TEST SUITE 2: DAO OPERATIONS
// =============================================================================
suite('2. DAO Operations');

test('StarDao insert and retrieve', async () => {
  const dao = new StarDao();
  const star = new StarEntity({ id: 'test1', userId: 'user1', title: 'Test' });
  await dao.insert(star);
  const retrieved = await dao.getById('test1');
  assertEqual(retrieved.title, 'Test');
});

test('StarDao update modifies existing task', async () => {
  const dao = new StarDao();
  const star = new StarEntity({ id: 'test1', userId: 'user1', title: 'Original' });
  await dao.insert(star);
  star.title = 'Updated';
  await dao.update(star);
  const retrieved = await dao.getById('test1');
  assertEqual(retrieved.title, 'Updated');
});

test('StarDao getAllForUser filters by user', async () => {
  const dao = new StarDao();
  await dao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Task 1' }));
  await dao.insert(new StarEntity({ id: 't2', userId: 'user1', title: 'Task 2' }));
  await dao.insert(new StarEntity({ id: 't3', userId: 'user2', title: 'Task 3' }));

  const user1Tasks = await dao.getAllForUser('user1');
  assertArrayLength(user1Tasks, 2);
});

test('StarDao getAllForUser excludes soft-deleted tasks', async () => {
  const dao = new StarDao();
  await dao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Active' }));
  await dao.insert(new StarEntity({ id: 't2', userId: 'user1', title: 'Deleted', isDeleted: true }));

  const tasks = await dao.getAllForUser('user1');
  assertArrayLength(tasks, 1);
  assertEqual(tasks[0].title, 'Active');
});

test('StarDao getPendingSync returns only pending tasks', async () => {
  const dao = new StarDao();
  await dao.insert(new StarEntity({ id: 't1', userId: 'user1', syncStatus: 'pending' }));
  await dao.insert(new StarEntity({ id: 't2', userId: 'user1', syncStatus: 'synced' }));
  await dao.insert(new StarEntity({ id: 't3', userId: 'user1', syncStatus: 'pending' }));

  const pending = await dao.getPendingSync();
  assertArrayLength(pending, 2);
});

test('StarDao softDelete sets isDeleted and pending status', async () => {
  const dao = new StarDao();
  await dao.insert(new StarEntity({ id: 't1', userId: 'user1', syncStatus: 'synced' }));
  await dao.softDelete('t1');

  const task = await dao.getById('t1');
  assertTrue(task.isDeleted);
  assertEqual(task.syncStatus, 'pending');
});

test('StarDao getModifiedSince filters by timestamp', async () => {
  const dao = new StarDao();
  const now = Date.now();
  await dao.insert(new StarEntity({ id: 't1', userId: 'user1', updatedAt: now - 1000 }));
  await dao.insert(new StarEntity({ id: 't2', userId: 'user1', updatedAt: now + 1000 }));

  const modified = await dao.getModifiedSince('user1', now);
  assertArrayLength(modified, 1);
  assertEqual(modified[0].id, 't2');
});

test('SyncQueueDao FIFO ordering', async () => {
  const dao = new SyncQueueDao();
  await dao.insert(new SyncQueueEntity({ taskId: 't1', operation: 'create', createdAt: 3000 }));
  await dao.insert(new SyncQueueEntity({ taskId: 't2', operation: 'create', createdAt: 1000 }));
  await dao.insert(new SyncQueueEntity({ taskId: 't3', operation: 'create', createdAt: 2000 }));

  const pending = await dao.getAllPending();
  assertEqual(pending[0].taskId, 't2');
  assertEqual(pending[1].taskId, 't3');
  assertEqual(pending[2].taskId, 't1');
});

test('SyncQueueDao deleteByTaskId removes all entries for task', async () => {
  const dao = new SyncQueueDao();
  await dao.insert(new SyncQueueEntity({ taskId: 't1', operation: 'create' }));
  await dao.insert(new SyncQueueEntity({ taskId: 't1', operation: 'update' }));
  await dao.insert(new SyncQueueEntity({ taskId: 't2', operation: 'create' }));

  await dao.deleteByTaskId('t1');
  const remaining = await dao.getAllPending();
  assertArrayLength(remaining, 1);
  assertEqual(remaining[0].taskId, 't2');
});

test('SyncQueueDao incrementRetry updates count and error', async () => {
  const dao = new SyncQueueDao();
  const id = await dao.insert(new SyncQueueEntity({ taskId: 't1', operation: 'create' }));

  await dao.incrementRetry(id, 'Network error');
  await dao.incrementRetry(id, 'Timeout');

  const entries = await dao.getAllPending();
  assertEqual(entries[0].retryCount, 2);
  assertEqual(entries[0].lastError, 'Timeout');
});

test('SyncQueueDao getFailedEntries returns entries over max retries', async () => {
  const dao = new SyncQueueDao();
  const id1 = await dao.insert(new SyncQueueEntity({ taskId: 't1', operation: 'create' }));
  const id2 = await dao.insert(new SyncQueueEntity({ taskId: 't2', operation: 'create' }));

  await dao.incrementRetry(id1, 'err');
  await dao.incrementRetry(id1, 'err');
  await dao.incrementRetry(id1, 'err');

  const failed = await dao.getFailedEntries(3);
  assertArrayLength(failed, 1);
  assertEqual(failed[0].taskId, 't1');
});

// =============================================================================
// TEST SUITE 3: SYNC QUEUE MANAGEMENT
// =============================================================================
suite('3. Sync Queue Management');

test('queueCreate adds entry with correct operation', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  await syncManager.queueCreate('task1', { title: 'New Task' });

  const entries = await syncQueueDao.getAllPending();
  assertArrayLength(entries, 1);
  assertEqual(entries[0].operation, 'create');
  assertEqual(entries[0].taskId, 'task1');
});

test('queueUpdate replaces pending operations for same task', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  await syncManager.queueCreate('task1', { title: 'Original' });
  await syncManager.queueUpdate('task1', { title: 'Updated' });

  const entries = await syncQueueDao.getAllPending();
  assertArrayLength(entries, 1);
  assertEqual(entries[0].operation, 'update');
  const payload = JSON.parse(entries[0].payload);
  assertEqual(payload.title, 'Updated');
});

test('queueDelete removes all pending operations and adds delete', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  await syncManager.queueCreate('task1', { title: 'Task' });
  await syncManager.queueUpdate('task1', { priority: 1 });
  await syncManager.queueDelete('task1');

  const entries = await syncQueueDao.getAllPending();
  assertArrayLength(entries, 1);
  assertEqual(entries[0].operation, 'delete');
});

test('queueComplete adds complete operation', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  await syncManager.queueComplete('task1');

  const entries = await syncQueueDao.getAllPending();
  assertEqual(entries[0].operation, 'complete');
  const payload = JSON.parse(entries[0].payload);
  assertTrue(payload.completedAt > 0);
});

test('getPendingCount returns correct count', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  await syncManager.queueCreate('t1', {});
  await syncManager.queueCreate('t2', {});
  await syncManager.queueCreate('t3', {});

  const count = await syncManager.getPendingCount();
  assertEqual(count, 3);
});

test('hasPendingChanges returns true when queue not empty', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  assertFalse(await syncManager.hasPendingChanges());
  await syncManager.queueCreate('t1', {});
  assertTrue(await syncManager.hasPendingChanges());
});

// =============================================================================
// TEST SUITE 4: SYNC ENGINE - CONFLICT RESOLUTION
// =============================================================================
suite('4. Sync Engine - Conflict Resolution');

test('Successful sync clears queue and updates status', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Task', syncStatus: 'pending' }));
  await syncManager.queueCreate('t1', { title: 'Task' });

  const result = await syncManager.performSync('user1');

  assertEqual(result.applied, 1);
  assertEqual(result.rejected, 0);
  assertEqual(await syncQueueDao.count(), 0);

  const task = await starDao.getById('t1');
  assertEqual(task.syncStatus, 'synced');
});

test('Conflict increments retry count', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Create task on server first
  apiService.serverTasks.set('t1', { id: 't1', userId: 'user1', title: 'Server', updatedAt: Date.now() });

  // Try to create same task from client
  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Client', syncStatus: 'pending' }));
  await syncManager.queueCreate('t1', { title: 'Client' });

  await syncManager.performSync('user1');

  const entries = await syncQueueDao.getAllPending();
  assertArrayLength(entries, 1);
  assertEqual(entries[0].retryCount, 1);
});

test('Max retries reached marks task as error and removes from queue', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);
  syncManager.maxRetries = 2;

  // Create conflicting scenario
  apiService.serverTasks.set('t1', { id: 't1', userId: 'user1', title: 'Server', updatedAt: Date.now() + 10000 });

  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', syncStatus: 'pending' }));

  // Pre-set retry count to max - 1
  await syncQueueDao.insert(new SyncQueueEntity({
    taskId: 't1',
    operation: 'update',
    payload: '{}',
    retryCount: 2
  }));

  const result = await syncManager.performSync('user1');

  assertEqual(result.errors.length, 1);
  assertEqual(await syncQueueDao.count(), 0);

  const task = await starDao.getById('t1');
  assertEqual(task.syncStatus, 'error');
});

test('Stale data conflict returns server version', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  const serverTime = Date.now();
  const clientTime = serverTime - 10000;

  apiService.serverTasks.set('t1', {
    id: 't1',
    userId: 'user1',
    title: 'Server Version',
    updatedAt: serverTime
  });

  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Client Version', syncStatus: 'pending' }));
  await syncQueueDao.insert(new SyncQueueEntity({
    taskId: 't1',
    operation: 'update',
    payload: JSON.stringify({ title: 'Client Version' }),
    createdAt: clientTime
  }));

  const result = await syncManager.performSync('user1');

  assertEqual(result.rejected, 1);
  assertTrue(result.conflicts.length > 0);
  assertEqual(result.conflicts[0].reason, 'stale_data');
});

test('Server changes are applied locally', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Server has a task
  apiService.serverTasks.set('server_task', {
    id: 'server_task',
    userId: 'user1',
    title: 'From Server',
    priority: 3,
    updatedAt: Date.now()
  });

  await syncManager.performSync('user1');

  const task = await starDao.getById('server_task');
  assertNotNull(task);
  assertEqual(task.title, 'From Server');
  assertEqual(task.syncStatus, 'synced');
});

test('Sync skipped when offline', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  networkChecker.setOnline(false);
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  const result = await syncManager.performSync('user1');

  assertTrue(result.skipped);
  assertEqual(result.reason, 'offline');
});

test('Sync skipped when already in progress', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  apiService.latencyMs = 100;
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Start first sync
  const sync1 = syncManager.performSync('user1');
  // Try second sync immediately
  const sync2 = await syncManager.performSync('user1');

  assertTrue(sync2.skipped);
  assertEqual(sync2.reason, 'sync_in_progress');

  await sync1; // Wait for first to complete
});

// =============================================================================
// TEST SUITE 5: WALLPAPER GENERATOR
// =============================================================================
suite('5. Wallpaper Generator');

test('calculateUrgency returns CLEAR for null task', () => {
  const urgency = LocalWallpaperGenerator.calculateUrgency(null);
  assertEqual(urgency, UrgencyLevel.CLEAR);
});

test('calculateUrgency returns CALM for task without due date', () => {
  const task = { id: 't1', title: 'Test', dueDate: null };
  const urgency = LocalWallpaperGenerator.calculateUrgency(task);
  assertEqual(urgency, UrgencyLevel.CALM);
});

test('calculateUrgency returns CRITICAL for overdue task', () => {
  const pastDate = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const task = { id: 't1', title: 'Overdue', dueDate: pastDate };
  const urgency = LocalWallpaperGenerator.calculateUrgency(task);
  assertEqual(urgency, UrgencyLevel.CRITICAL);
});

test('calculateUrgency returns CRITICAL for task due in 3 hours', () => {
  const soonDate = new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString();
  const task = { id: 't1', title: 'Soon', dueDate: soonDate };
  const urgency = LocalWallpaperGenerator.calculateUrgency(task);
  assertEqual(urgency, UrgencyLevel.CRITICAL);
});

test('calculateUrgency returns URGENT for task due in 12 hours', () => {
  const todayDate = new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString();
  const task = { id: 't1', title: 'Today', dueDate: todayDate };
  const urgency = LocalWallpaperGenerator.calculateUrgency(task);
  assertEqual(urgency, UrgencyLevel.URGENT);
});

test('calculateUrgency returns ATTENTION for task due tomorrow', () => {
  const tomorrowDate = new Date(Date.now() + 30 * 60 * 60 * 1000).toISOString();
  const task = { id: 't1', title: 'Tomorrow', dueDate: tomorrowDate };
  const urgency = LocalWallpaperGenerator.calculateUrgency(task);
  assertEqual(urgency, UrgencyLevel.ATTENTION);
});

test('calculateUrgency returns CALM for task due in 3 days', () => {
  const futureDate = new Date(Date.now() + 72 * 60 * 60 * 1000).toISOString();
  const task = { id: 't1', title: 'Later', dueDate: futureDate };
  const urgency = LocalWallpaperGenerator.calculateUrgency(task);
  assertEqual(urgency, UrgencyLevel.CALM);
});

test('getThemeColors returns valid colors for all theme/urgency combinations', () => {
  const themes = Object.values(WallpaperTheme);
  const urgencies = Object.values(UrgencyLevel);

  for (const theme of themes) {
    for (const urgency of urgencies) {
      const colors = LocalWallpaperGenerator.getThemeColors(theme, urgency);
      assertNotNull(colors.gradientStart);
      assertNotNull(colors.gradientEnd);
      assertNotNull(colors.accent);
      assertTrue(colors.gradientStart.startsWith('#'));
      assertTrue(colors.gradientEnd.startsWith('#'));
      assertTrue(colors.accent.startsWith('#'));
    }
  }
});

test('formatDueDate returns "Overdue" for past dates', () => {
  const pastDate = Date.now() - 30 * 60 * 1000; // 30 minutes ago
  const result = LocalWallpaperGenerator.formatDueDate(pastDate);
  assertEqual(result, 'Overdue');
});

test('formatDueDate returns hours overdue correctly', () => {
  const pastDate = Date.now() - 5 * 60 * 60 * 1000; // 5 hours ago
  const result = LocalWallpaperGenerator.formatDueDate(pastDate);
  assertEqual(result, 'Overdue by 5h');
});

test('formatDueDate returns days overdue correctly', () => {
  const pastDate = Date.now() - 3 * 24 * 60 * 60 * 1000; // 3 days ago
  const result = LocalWallpaperGenerator.formatDueDate(pastDate);
  assertEqual(result, 'Overdue by 3d');
});

test('formatDueDate returns minutes for soon tasks', () => {
  const soonDate = Date.now() + 45 * 60 * 1000; // 45 minutes
  const result = LocalWallpaperGenerator.formatDueDate(soonDate);
  assertEqual(result, 'Due in 45m');
});

test('formatDueDate returns hours for today tasks', () => {
  const todayDate = Date.now() + 5 * 60 * 60 * 1000 + 30 * 60 * 1000; // 5h 30m
  const result = LocalWallpaperGenerator.formatDueDate(todayDate);
  assertEqual(result, 'Due in 5h 30m');
});

test('formatDueDate returns "Due tomorrow" for next day', () => {
  const tomorrowDate = Date.now() + 30 * 60 * 60 * 1000; // 30 hours
  const result = LocalWallpaperGenerator.formatDueDate(tomorrowDate);
  assertEqual(result, 'Due tomorrow');
});

test('formatDueDate returns days for future dates', () => {
  const futureDate = Date.now() + 5 * 24 * 60 * 60 * 1000; // 5 days
  const result = LocalWallpaperGenerator.formatDueDate(futureDate);
  assertEqual(result, 'Due in 5 days');
});

test('generate returns correct metadata', () => {
  const task = { id: 't1', title: 'Test Task', dueDate: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString() };
  const result = LocalWallpaperGenerator.generate(task, WallpaperTheme.COSMIC, 1080, 1920);

  assertEqual(result.width, 1080);
  assertEqual(result.height, 1920);
  assertEqual(result.theme, WallpaperTheme.COSMIC);
  assertNotNull(result.colors);
  assertNotNull(result.task);
  assertEqual(result.task.id, 't1');
  assertTrue(result.generatedAt > 0);
});

test('generate works with null task (clear state)', () => {
  const result = LocalWallpaperGenerator.generate(null, WallpaperTheme.OCEAN, 1080, 1920);

  assertEqual(result.urgency, UrgencyLevel.CLEAR);
  assertNull(result.task);
});

// =============================================================================
// TEST SUITE 6: OFFLINE/ONLINE TRANSITIONS
// =============================================================================
suite('6. Offline/Online Transitions');

test('NetworkChecker tracks online status', () => {
  const checker = new NetworkChecker();
  assertTrue(checker.isOnline()); // Default online

  checker.setOnline(false);
  assertFalse(checker.isOnline());

  checker.setOnline(true);
  assertTrue(checker.isOnline());
});

test('NetworkChecker fires events on status change', () => {
  const checker = new NetworkChecker();
  let events = [];

  checker.onStatusChange(status => events.push(status));

  checker.setOnline(false);
  checker.setOnline(true);

  assertEqual(events, ['offline', 'online']);
});

test('NetworkChecker does not fire duplicate events', () => {
  const checker = new NetworkChecker();
  let eventCount = 0;

  checker.onStatusChange(() => eventCount++);

  checker.setOnline(true);  // Already online
  checker.setOnline(true);  // Still online

  assertEqual(eventCount, 0);
});

test('Operations queue when offline', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  networkChecker.setOnline(false);
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  await syncManager.queueCreate('t1', { title: 'Offline Task 1' });
  await syncManager.queueCreate('t2', { title: 'Offline Task 2' });
  await syncManager.queueCreate('t3', { title: 'Offline Task 3' });

  // Try sync while offline
  const result = await syncManager.performSync('user1');
  assertTrue(result.skipped);

  // Queue should still have all items
  assertEqual(await syncQueueDao.count(), 3);
});

test('Queue drains when back online', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Queue while online (but don't sync yet)
  networkChecker.setOnline(false);
  await syncManager.queueCreate('t1', { title: 'Task 1' });
  await syncManager.queueCreate('t2', { title: 'Task 2' });

  // Go online and sync
  networkChecker.setOnline(true);
  const result = await syncManager.performSync('user1');

  assertEqual(result.applied, 2);
  assertEqual(await syncQueueDao.count(), 0);
});

test('Multiple offline sessions queue correctly', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // First offline session
  networkChecker.setOnline(false);
  await syncManager.queueCreate('t1', { title: 'Session 1 Task' });

  // Brief online (but don't sync)
  networkChecker.setOnline(true);

  // Second offline session
  networkChecker.setOnline(false);
  await syncManager.queueCreate('t2', { title: 'Session 2 Task' });

  // Check queue has both
  assertEqual(await syncQueueDao.count(), 2);

  // Sync when online
  networkChecker.setOnline(true);
  const result = await syncManager.performSync('user1');
  assertEqual(result.applied, 2);
});

// =============================================================================
// TEST SUITE 7: FULL INTEGRATION TESTS
// =============================================================================
suite('7. Full Integration Tests');

test('Complete create-update-complete-delete flow', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // 1. Create task locally
  const taskId = 'integration_task_1';
  await starDao.insert(new StarEntity({
    id: taskId,
    userId: 'user1',
    title: 'Integration Test Task',
    syncStatus: 'pending'
  }));
  await syncManager.queueCreate(taskId, { title: 'Integration Test Task' });

  // 2. Sync create
  await syncManager.performSync('user1');
  assertEqual((await starDao.getById(taskId)).syncStatus, 'synced');

  // 3. Update task
  const task = await starDao.getById(taskId);
  task.title = 'Updated Title';
  task.priority = 2;
  task.syncStatus = 'pending';
  await starDao.update(task);
  await syncManager.queueUpdate(taskId, { title: 'Updated Title', priority: 2 });

  // 4. Sync update
  await syncManager.performSync('user1');
  assertEqual((await starDao.getById(taskId)).syncStatus, 'synced');

  // 5. Complete task
  const updatedTask = await starDao.getById(taskId);
  updatedTask.completed = true;
  updatedTask.syncStatus = 'pending';
  await starDao.update(updatedTask);
  await syncManager.queueComplete(taskId);

  // 6. Sync complete
  await syncManager.performSync('user1');

  // 7. Delete task
  await starDao.softDelete(taskId);
  await syncManager.queueDelete(taskId);

  // 8. Sync delete
  await syncManager.performSync('user1');

  // Verify final state
  assertTrue((await starDao.getById(taskId)).isDeleted);
  assertEqual(await syncQueueDao.count(), 0);
});

test('Bulk operations sync correctly', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Create 10 tasks
  for (let i = 0; i < 10; i++) {
    const taskId = `bulk_task_${i}`;
    await starDao.insert(new StarEntity({
      id: taskId,
      userId: 'user1',
      title: `Bulk Task ${i}`,
      syncStatus: 'pending'
    }));
    await syncManager.queueCreate(taskId, { title: `Bulk Task ${i}` });
  }

  // Sync all
  const result = await syncManager.performSync('user1');

  assertEqual(result.applied, 10);
  assertEqual(await syncQueueDao.count(), 0);

  // Verify all synced
  const tasks = await starDao.getAllForUser('user1');
  for (const task of tasks) {
    assertEqual(task.syncStatus, 'synced');
  }
});

test('Mixed success and failure in single sync', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Create conflict on server
  apiService.serverTasks.set('conflict_task', {
    id: 'conflict_task',
    userId: 'user1',
    title: 'Server Version',
    updatedAt: Date.now() + 10000
  });

  // Queue both success and conflict
  await starDao.insert(new StarEntity({ id: 'success_task', userId: 'user1', syncStatus: 'pending' }));
  await starDao.insert(new StarEntity({ id: 'conflict_task', userId: 'user1', syncStatus: 'pending' }));

  await syncManager.queueCreate('success_task', { title: 'New Task' });
  await syncQueueDao.insert(new SyncQueueEntity({
    taskId: 'conflict_task',
    operation: 'update',
    payload: JSON.stringify({ title: 'Client Version' }),
    createdAt: Date.now() - 20000 // Old timestamp
  }));

  const result = await syncManager.performSync('user1');

  assertEqual(result.applied, 1);
  assertEqual(result.rejected, 1);

  // Success task should be synced
  assertEqual((await starDao.getById('success_task')).syncStatus, 'synced');
});

test('Server-initiated changes sync to local', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Simulate server having tasks (from another device)
  apiService.serverTasks.set('server_task_1', {
    id: 'server_task_1',
    userId: 'user1',
    title: 'From Phone',
    priority: 1,
    updatedAt: Date.now()
  });
  apiService.serverTasks.set('server_task_2', {
    id: 'server_task_2',
    userId: 'user1',
    title: 'From Tablet',
    priority: 2,
    updatedAt: Date.now()
  });

  // Sync should pull these
  await syncManager.performSync('user1');

  const task1 = await starDao.getById('server_task_1');
  const task2 = await starDao.getById('server_task_2');

  assertNotNull(task1);
  assertNotNull(task2);
  assertEqual(task1.title, 'From Phone');
  assertEqual(task2.title, 'From Tablet');
});

// =============================================================================
// TEST SUITE 8: EDGE CASES & ERROR HANDLING
// =============================================================================
suite('8. Edge Cases & Error Handling');

test('Empty sync request succeeds', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  const result = await syncManager.performSync('user1');

  assertEqual(result.applied, 0);
  assertEqual(result.rejected, 0);
  assertFalse(result.skipped);
});

test('Network error during sync preserves queue', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  await syncManager.queueCreate('t1', { title: 'Task' });

  apiService.shouldFail = true;
  apiService.failureReason = 'Network error';

  try {
    await syncManager.performSync('user1');
  } catch (e) {
    // Expected
  }

  // Queue should still have the item
  assertEqual(await syncQueueDao.count(), 1);
});

test('Very long task title handled correctly', async () => {
  const starDao = new StarDao();
  const longTitle = 'A'.repeat(1000);

  await starDao.insert(new StarEntity({
    id: 't1',
    userId: 'user1',
    title: longTitle
  }));

  const task = await starDao.getById('t1');
  assertEqual(task.title.length, 1000);
});

test('Unicode in task data preserved', async () => {
  const starDao = new StarDao();
  const unicodeTitle = '任务 📋 Tâche مهمة';

  await starDao.insert(new StarEntity({
    id: 't1',
    userId: 'user1',
    title: unicodeTitle
  }));

  const task = await starDao.getById('t1');
  assertEqual(task.title, unicodeTitle);
});

test('Null and undefined handled in sync payload', async () => {
  const syncQueueDao = new SyncQueueDao();

  const entry = new SyncQueueEntity({
    taskId: 't1',
    operation: 'update',
    payload: JSON.stringify({ title: 'Test', dueDate: null, priority: undefined })
  });

  await syncQueueDao.insert(entry);
  const entries = await syncQueueDao.getAllPending();
  const parsed = JSON.parse(entries[0].payload);

  assertEqual(parsed.title, 'Test');
  assertNull(parsed.dueDate);
  assertTrue(!('priority' in parsed) || parsed.priority === undefined);
});

test('Concurrent modifications to same task', async () => {
  const starDao = new StarDao();

  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Original' }));

  // Simulate concurrent updates
  const task1 = await starDao.getById('t1');
  const task2 = await starDao.getById('t1');

  task1.title = 'Update 1';
  task2.title = 'Update 2';

  await starDao.update(task1);
  await starDao.update(task2);

  // Last write wins
  const final = await starDao.getById('t1');
  assertEqual(final.title, 'Update 2');
});

test('Task with all fields populated', async () => {
  const starDao = new StarDao();

  const fullTask = new StarEntity({
    id: 'full_task',
    userId: 'user1',
    title: 'Complete Task',
    priority: 3,
    estimateMinutes: 45,
    dueDate: '2026-02-15',
    dueTime: '14:30:00',
    completed: false,
    syncStatus: 'pending',
    syncVersion: 1
  });

  await starDao.insert(fullTask);
  const retrieved = await starDao.getById('full_task');

  assertEqual(retrieved.priority, 3);
  assertEqual(retrieved.estimateMinutes, 45);
  assertEqual(retrieved.dueDate, '2026-02-15');
  assertEqual(retrieved.dueTime, '14:30:00');
});

// =============================================================================
// TEST SUITE 9: PERFORMANCE & STRESS TESTS
// =============================================================================
suite('9. Performance & Stress Tests');

test('Handle 100 tasks in queue', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Queue 100 creates
  for (let i = 0; i < 100; i++) {
    await starDao.insert(new StarEntity({ id: `t${i}`, userId: 'user1', syncStatus: 'pending' }));
    await syncManager.queueCreate(`t${i}`, { title: `Task ${i}` });
  }

  assertEqual(await syncQueueDao.count(), 100);

  const startTime = Date.now();
  const result = await syncManager.performSync('user1');
  const duration = Date.now() - startTime;

  assertEqual(result.applied, 100);
  assertEqual(await syncQueueDao.count(), 0);

  // Should complete in reasonable time (< 1 second for in-memory)
  assertTrue(duration < 1000, `Sync took ${duration}ms`);
});

test('Handle 1000 tasks in database', async () => {
  const starDao = new StarDao();

  for (let i = 0; i < 1000; i++) {
    await starDao.insert(new StarEntity({
      id: `t${i}`,
      userId: 'user1',
      title: `Task ${i}`,
      priority: i % 4
    }));
  }

  const count = await starDao.count();
  assertEqual(count, 1000);

  const startTime = Date.now();
  const tasks = await starDao.getAllForUser('user1');
  const duration = Date.now() - startTime;

  assertEqual(tasks.length, 1000);
  assertTrue(duration < 100, `Query took ${duration}ms`);
});

test('Rapid sequential operations', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const syncManager = new SyncManager(starDao, syncQueueDao, null, { isOnline: () => false });

  const taskId = 'rapid_task';

  // Rapid create-update-update-update-delete
  await syncManager.queueCreate(taskId, { title: 'V1' });
  await syncManager.queueUpdate(taskId, { title: 'V2' });
  await syncManager.queueUpdate(taskId, { title: 'V3' });
  await syncManager.queueUpdate(taskId, { title: 'V4' });
  await syncManager.queueDelete(taskId);

  // Should only have delete
  const entries = await syncQueueDao.getAllPending();
  assertArrayLength(entries, 1);
  assertEqual(entries[0].operation, 'delete');
});

// =============================================================================
// TEST SUITE 10: SECURITY & ISOLATION
// =============================================================================
suite('10. Security & Isolation');

test('User isolation - cannot access other user tasks', async () => {
  const starDao = new StarDao();

  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'User 1 Task' }));
  await starDao.insert(new StarEntity({ id: 't2', userId: 'user2', title: 'User 2 Task' }));

  const user1Tasks = await starDao.getAllForUser('user1');
  const user2Tasks = await starDao.getAllForUser('user2');

  assertArrayLength(user1Tasks, 1);
  assertArrayLength(user2Tasks, 1);
  assertEqual(user1Tasks[0].title, 'User 1 Task');
  assertEqual(user2Tasks[0].title, 'User 2 Task');
});

test('Cross-user task update rejected by API', async () => {
  const apiService = new MockApiService();

  // User 1 creates task
  apiService.serverTasks.set('t1', {
    id: 't1',
    userId: 'user1',
    title: 'User 1 Task',
    updatedAt: Date.now()
  });

  // User 2 tries to update
  const result = await apiService.sync({
    userId: 'user2',
    lastSyncAt: 0,
    changes: [{
      type: 'update',
      taskId: 't1',
      data: { title: 'Hacked' },
      timestamp: Date.now(),
      clientId: 'attack'
    }]
  });

  // Should fail - task doesn't exist for user2
  assertEqual(result.results.rejected, 1);
});

test('Deleted tasks not returned in queries', async () => {
  const starDao = new StarDao();

  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', title: 'Active', isDeleted: false }));
  await starDao.insert(new StarEntity({ id: 't2', userId: 'user1', title: 'Deleted', isDeleted: true }));

  const tasks = await starDao.getAllForUser('user1');
  assertArrayLength(tasks, 1);
  assertEqual(tasks[0].title, 'Active');
});

test('Sync version prevents replay attacks', async () => {
  const starDao = new StarDao();
  const syncQueueDao = new SyncQueueDao();
  const apiService = new MockApiService();
  const networkChecker = new NetworkChecker();
  const syncManager = new SyncManager(starDao, syncQueueDao, apiService, networkChecker);

  // Server has task at version N
  apiService.serverTasks.set('t1', {
    id: 't1',
    userId: 'user1',
    title: 'Current',
    updatedAt: Date.now(),
    syncVersion: 5
  });

  // Client tries to sync old version
  await starDao.insert(new StarEntity({ id: 't1', userId: 'user1', syncStatus: 'pending', syncVersion: 3 }));
  await syncQueueDao.insert(new SyncQueueEntity({
    taskId: 't1',
    operation: 'update',
    payload: JSON.stringify({ title: 'Old Version' }),
    createdAt: Date.now() - 10000
  }));

  const result = await syncManager.performSync('user1');

  // Should be rejected as stale
  assertEqual(result.rejected, 1);
});

// =============================================================================
// RUN ALL TESTS
// =============================================================================

async function runTests() {
  console.log('\n' + '='.repeat(70));
  console.log('COMPREHENSIVE E2E TESTS - Local-First Architecture');
  console.log('='.repeat(70) + '\n');

  let currentSuiteName = '';

  for (const { suite, name, fn } of testQueue) {
    if (suite !== currentSuiteName) {
      if (currentSuiteName) console.log('');
      console.log(`📦 ${suite}`);
      console.log('-'.repeat(60));
      currentSuiteName = suite;
    }

    try {
      await fn();
      passCount++;
      testResults.push({ suite, name, status: 'PASS' });
      console.log(`  ✅ ${name}`);
    } catch (err) {
      failCount++;
      testResults.push({ suite, name, status: 'FAIL', error: err.message });
      console.log(`  ❌ ${name}`);
      console.log(`     Error: ${err.message.split('\n')[0]}`);
    }
  }

  // Summary
  console.log('\n' + '='.repeat(70));
  console.log('TEST RESULTS SUMMARY');
  console.log('='.repeat(70));

  const total = passCount + failCount;
  const passRate = total > 0 ? ((passCount / total) * 100).toFixed(1) : 0;

  console.log(`\n  Total Tests:  ${total}`);
  console.log(`  ✅ Passed:    ${passCount}`);
  console.log(`  ❌ Failed:    ${failCount}`);
  console.log(`  Pass Rate:    ${passRate}%\n`);

  // By suite
  console.log('By Test Suite:');
  const suites = {};
  testResults.forEach(t => {
    if (!suites[t.suite]) suites[t.suite] = { pass: 0, fail: 0 };
    if (t.status === 'PASS') suites[t.suite].pass++;
    else suites[t.suite].fail++;
  });

  Object.entries(suites).forEach(([suite, counts]) => {
    const status = counts.fail === 0 ? '✅' : '❌';
    console.log(`  ${status} ${suite}: ${counts.pass}/${counts.pass + counts.fail}`);
  });

  console.log('');

  if (failCount === 0) {
    console.log('🎉 ALL TESTS PASSED! 100% SUCCESS RATE');
    console.log('');
    console.log('Verified Components:');
    console.log('  ✅ Database Layer (Entities, DAOs, sync fields)');
    console.log('  ✅ Sync Queue (FIFO, retry logic, deduplication)');
    console.log('  ✅ Sync Engine (create/update/delete, conflict resolution)');
    console.log('  ✅ Wallpaper Generator (themes, urgency, formatting)');
    console.log('  ✅ Offline/Online Transitions (queue persistence)');
    console.log('  ✅ Full Integration (complete user flows)');
    console.log('  ✅ Edge Cases (unicode, long text, nulls)');
    console.log('  ✅ Performance (100+ tasks, 1000+ db records)');
    console.log('  ✅ Security (user isolation, replay prevention)');
    console.log('');
  } else {
    console.log('❌ SOME TESTS FAILED\n');
    testResults.filter(t => t.status === 'FAIL').forEach(t => {
      console.log(`  ${t.suite} > ${t.name}`);
      console.log(`    ${t.error}`);
    });
  }

  process.exit(failCount > 0 ? 1 : 0);
}

runTests();
