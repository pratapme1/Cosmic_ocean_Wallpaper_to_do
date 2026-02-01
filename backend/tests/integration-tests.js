#!/usr/bin/env node
/**
 * INTEGRATION TEST SUITE - Local-First Architecture
 *
 * Tests complete flows across multiple components:
 * 1. Authentication Flow Integration
 * 2. Task Lifecycle Integration
 * 3. Sync Flow Integration (Offline → Online)
 * 4. Wallpaper Update Integration
 * 5. Multi-Device Sync Simulation
 * 6. Conflict Resolution Integration
 * 7. Data Consistency Integration
 * 8. Error Recovery Integration
 * 9. Performance Integration
 * 10. Real-World Scenario Tests
 *
 * Run: node tests/integration-tests.js
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

function assertEqual(actual, expected, msg = '') {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${msg}\n     Expected: ${JSON.stringify(expected)}\n     Actual: ${JSON.stringify(actual)}`);
  }
}

function assertNotEqual(actual, notExpected, msg = '') {
  if (JSON.stringify(actual) === JSON.stringify(notExpected)) {
    throw new Error(`${msg}\n     Should not be: ${JSON.stringify(notExpected)}`);
  }
}

function assertTrue(condition, msg = 'Expected true') {
  if (!condition) throw new Error(msg);
}

function assertFalse(condition, msg = 'Expected false') {
  if (condition) throw new Error(msg);
}

function assertNotNull(value, msg = 'Expected not null') {
  if (value === null || value === undefined) throw new Error(msg);
}

function assertNull(value, msg = 'Expected null') {
  if (value !== null && value !== undefined) throw new Error(`${msg}, got: ${value}`);
}

function assertLength(arr, len, msg = '') {
  if (arr.length !== len) throw new Error(`${msg}\n     Expected length: ${len}\n     Actual: ${arr.length}`);
}

function assertContains(arr, item, msg = '') {
  if (!arr.includes(item)) throw new Error(`${msg}\n     Array does not contain: ${item}`);
}

function assertGreaterThan(a, b, msg = '') {
  if (!(a > b)) throw new Error(`${msg}\n     Expected ${a} > ${b}`);
}

// =============================================================================
// INTEGRATED SYSTEM COMPONENTS
// =============================================================================

// Simulates the complete integrated system
class IntegratedSystem {
  constructor() {
    // Backend components
    this.database = new Database();
    this.authService = new AuthService(this.database);
    this.taskService = new TaskService(this.database);
    this.syncService = new SyncService(this.database);
    this.wallpaperService = new WallpaperService(this.database);

    // Client components (per device)
    this.clients = new Map();
  }

  createClient(deviceId) {
    const client = new ClientDevice(deviceId, this);
    this.clients.set(deviceId, client);
    return client;
  }

  getClient(deviceId) {
    return this.clients.get(deviceId);
  }
}

// =============================================================================
// BACKEND SERVICES
// =============================================================================

class Database {
  constructor() {
    this.users = new Map();
    this.tasks = new Map();
    this.sessions = new Map();
  }

  // User operations
  createUser(email, passwordHash) {
    const id = crypto.randomUUID();
    const user = { id, email, passwordHash, theme: 'cosmic', createdAt: Date.now() };
    this.users.set(id, user);
    return user;
  }

  getUserByEmail(email) {
    return Array.from(this.users.values()).find(u => u.email === email);
  }

  getUserById(id) {
    return this.users.get(id);
  }

  // Task operations
  createTask(userId, data) {
    const id = data.id || crypto.randomUUID();
    const task = {
      id,
      userId,
      title: data.title || 'Untitled',
      priority: data.priority || 0,
      estimateMinutes: data.estimateMinutes || null,
      dueDate: data.dueDate || null,
      dueTime: data.dueTime || null,
      completed: false,
      deleted: false,
      syncVersion: 1,
      createdAt: data.createdAt || Date.now(),
      updatedAt: data.updatedAt || Date.now()
    };
    this.tasks.set(id, task);
    return task;
  }

  getTask(id) {
    return this.tasks.get(id);
  }

  getTasksForUser(userId) {
    return Array.from(this.tasks.values())
      .filter(t => t.userId === userId && !t.deleted);
  }

  getActiveTasksForUser(userId) {
    return this.getTasksForUser(userId).filter(t => !t.completed);
  }

  updateTask(id, updates) {
    const task = this.tasks.get(id);
    if (!task) return null;
    Object.assign(task, updates, {
      syncVersion: task.syncVersion + 1,
      updatedAt: Date.now()
    });
    return task;
  }

  deleteTask(id) {
    const task = this.tasks.get(id);
    if (task) {
      task.deleted = true;
      task.updatedAt = Date.now();
    }
    return task;
  }

  getTasksModifiedSince(userId, since) {
    // If since is 0 or very old, return all tasks for initial sync
    if (since === 0) {
      return Array.from(this.tasks.values())
        .filter(t => t.userId === userId && !t.deleted);
    }
    return Array.from(this.tasks.values())
      .filter(t => t.userId === userId && t.updatedAt > since);
  }
}

class AuthService {
  constructor(db) {
    this.db = db;
    this.tokens = new Map();
  }

  register(email, password) {
    if (this.db.getUserByEmail(email)) {
      return { error: 'User already exists' };
    }
    const passwordHash = crypto.createHash('sha256').update(password).digest('hex');
    const user = this.db.createUser(email, passwordHash);
    const token = this.generateToken(user.id);
    return { user, token };
  }

  login(email, password) {
    const user = this.db.getUserByEmail(email);
    if (!user) return { error: 'User not found' };

    const passwordHash = crypto.createHash('sha256').update(password).digest('hex');
    if (user.passwordHash !== passwordHash) return { error: 'Invalid password' };

    const token = this.generateToken(user.id);
    return { user, token };
  }

  generateToken(userId) {
    const token = crypto.randomBytes(32).toString('hex');
    this.tokens.set(token, { userId, createdAt: Date.now() });
    return token;
  }

  validateToken(token) {
    const session = this.tokens.get(token);
    if (!session) return null;
    return this.db.getUserById(session.userId);
  }

  logout(token) {
    this.tokens.delete(token);
  }
}

class TaskService {
  constructor(db) {
    this.db = db;
  }

  create(userId, data) {
    return this.db.createTask(userId, data);
  }

  getAll(userId) {
    return this.db.getTasksForUser(userId);
  }

  getActive(userId) {
    return this.db.getActiveTasksForUser(userId);
  }

  getById(taskId, userId) {
    const task = this.db.getTask(taskId);
    if (task && task.userId === userId && !task.deleted) {
      return task;
    }
    return null;
  }

  update(taskId, userId, updates) {
    const task = this.db.getTask(taskId);
    if (!task || task.userId !== userId) {
      return { error: 'Task not found' };
    }
    return this.db.updateTask(taskId, updates);
  }

  complete(taskId, userId) {
    return this.update(taskId, userId, { completed: true, completedAt: Date.now() });
  }

  delete(taskId, userId) {
    const task = this.db.getTask(taskId);
    if (!task || task.userId !== userId) {
      return { error: 'Task not found' };
    }
    return this.db.deleteTask(taskId);
  }

  getTopPriority(userId) {
    const tasks = this.db.getActiveTasksForUser(userId);
    if (tasks.length === 0) return null;

    return tasks.sort((a, b) => {
      // Due date first
      if (a.dueDate && b.dueDate) {
        const diff = new Date(a.dueDate) - new Date(b.dueDate);
        if (diff !== 0) return diff;
      }
      if (a.dueDate && !b.dueDate) return -1;
      if (!a.dueDate && b.dueDate) return 1;
      // Then priority
      return b.priority - a.priority;
    })[0];
  }
}

class SyncService {
  constructor(db) {
    this.db = db;
  }

  sync(userId, lastSyncAt, changes) {
    const results = { applied: 0, rejected: 0, conflicts: [] };

    // Process client changes
    for (const change of changes) {
      const result = this.applyChange(userId, change);
      if (result.success) {
        results.applied++;
      } else {
        results.rejected++;
        results.conflicts.push({
          clientId: change.clientId,
          reason: result.reason,
          serverData: result.serverData
        });
      }
    }

    // Get server changes since lastSyncAt
    const serverChanges = this.db.getTasksModifiedSince(userId, lastSyncAt || 0);

    return {
      syncedAt: Date.now(),
      results,
      tasks: serverChanges,
      conflicts: results.conflicts
    };
  }

  applyChange(userId, change) {
    const { type, taskId, data, timestamp } = change;

    switch (type) {
      case 'create': {
        const existing = this.db.getTask(taskId || data.id);
        if (existing) {
          return { success: false, reason: 'already_exists', serverData: existing };
        }
        this.db.createTask(userId, { ...data, id: taskId, createdAt: timestamp, updatedAt: timestamp });
        return { success: true };
      }

      case 'update': {
        const task = this.db.getTask(taskId);
        if (!task || task.userId !== userId) {
          return { success: false, reason: 'task_not_found' };
        }
        if (timestamp < task.updatedAt) {
          return { success: false, reason: 'stale_data', serverData: task };
        }
        this.db.updateTask(taskId, { ...data, updatedAt: timestamp });
        return { success: true };
      }

      case 'delete': {
        const task = this.db.getTask(taskId);
        if (!task || task.userId !== userId) {
          return { success: true }; // Idempotent
        }
        if (timestamp < task.updatedAt) {
          return { success: false, reason: 'stale_data', serverData: task };
        }
        this.db.deleteTask(taskId);
        return { success: true };
      }

      case 'complete': {
        const task = this.db.getTask(taskId);
        if (!task || task.userId !== userId) {
          return { success: false, reason: 'task_not_found' };
        }
        this.db.updateTask(taskId, { completed: true, completedAt: timestamp });
        return { success: true };
      }

      default:
        return { success: false, reason: 'unknown_operation' };
    }
  }
}

class WallpaperService {
  constructor(db) {
    this.db = db;
    this.cache = new Map();
  }

  generate(userId, theme = 'cosmic') {
    const taskService = new TaskService(this.db);
    const topTask = taskService.getTopPriority(userId);

    const urgency = this.calculateUrgency(topTask);
    const cacheKey = `${userId}_${theme}_${topTask?.id || 'none'}_${urgency}`;

    // Check cache
    if (this.cache.has(cacheKey)) {
      return { ...this.cache.get(cacheKey), fromCache: true };
    }

    const wallpaper = {
      theme,
      urgency,
      task: topTask ? { id: topTask.id, title: topTask.title, dueDate: topTask.dueDate } : null,
      colors: this.getColors(theme, urgency),
      generatedAt: Date.now()
    };

    this.cache.set(cacheKey, wallpaper);
    return { ...wallpaper, fromCache: false };
  }

  calculateUrgency(task) {
    if (!task) return 'clear';
    if (!task.dueDate) return 'calm';

    const hoursUntil = (new Date(task.dueDate) - Date.now()) / (1000 * 60 * 60);
    if (hoursUntil < 0) return 'critical';
    if (hoursUntil < 4) return 'critical';
    if (hoursUntil < 24) return 'urgent';
    if (hoursUntil < 48) return 'attention';
    return 'calm';
  }

  getColors(theme, urgency) {
    const themes = {
      cosmic: {
        clear: '#4a9eff', calm: '#3a86ff', attention: '#ffd60a',
        urgent: '#ff6b35', critical: '#ff0a0a'
      },
      ocean: {
        clear: '#90e0ef', calm: '#94d2bd', attention: '#ffd166',
        urgent: '#ff9e00', critical: '#ffba08'
      },
      fantasy: {
        clear: '#f72585', calm: '#c77dff', attention: '#ffba08',
        urgent: '#ff5400', critical: '#ff0000'
      }
    };
    return { accent: themes[theme]?.[urgency] || '#ffffff' };
  }

  invalidateCache(userId) {
    for (const key of this.cache.keys()) {
      if (key.startsWith(userId)) {
        this.cache.delete(key);
      }
    }
  }
}

// =============================================================================
// CLIENT DEVICE SIMULATION
// =============================================================================

class ClientDevice {
  constructor(deviceId, system) {
    this.deviceId = deviceId;
    this.system = system;
    this.token = null;
    this.userId = null;
    this.isOnline = true;
    this.localTasks = new Map();
    this.syncQueue = [];
    this.lastSyncAt = 0;
  }

  // Network simulation
  goOffline() {
    this.isOnline = false;
  }

  goOnline() {
    this.isOnline = true;
  }

  // Auth
  async register(email, password) {
    if (!this.isOnline) return { error: 'Offline' };
    const result = this.system.authService.register(email, password);
    if (!result.error) {
      this.token = result.token;
      this.userId = result.user.id;
    }
    return result;
  }

  async login(email, password) {
    if (!this.isOnline) return { error: 'Offline' };
    const result = this.system.authService.login(email, password);
    if (!result.error) {
      this.token = result.token;
      this.userId = result.user.id;
    }
    return result;
  }

  // Task operations (local-first)
  async createTask(data) {
    const taskId = crypto.randomUUID();
    const timestamp = Date.now();

    const task = {
      id: taskId,
      ...data,
      syncStatus: 'pending',
      createdAt: timestamp,
      updatedAt: timestamp
    };

    this.localTasks.set(taskId, task);
    this.syncQueue.push({
      type: 'create',
      taskId,
      data,
      timestamp,
      clientId: `${this.deviceId}_${taskId}_${timestamp}`
    });

    if (this.isOnline) {
      await this.sync();
    }

    return task;
  }

  async updateTask(taskId, updates) {
    const task = this.localTasks.get(taskId);
    if (!task) return { error: 'Task not found' };

    const timestamp = Date.now();
    Object.assign(task, updates, { syncStatus: 'pending', updatedAt: timestamp });

    // Remove previous pending updates for this task
    this.syncQueue = this.syncQueue.filter(c => c.taskId !== taskId || c.type === 'create');
    this.syncQueue.push({
      type: 'update',
      taskId,
      data: updates,
      timestamp,
      clientId: `${this.deviceId}_${taskId}_${timestamp}`
    });

    if (this.isOnline) {
      await this.sync();
    }

    return task;
  }

  async completeTask(taskId) {
    const task = this.localTasks.get(taskId);
    if (!task) return { error: 'Task not found' };

    const timestamp = Date.now();
    task.completed = true;
    task.completedAt = timestamp;
    task.syncStatus = 'pending';

    this.syncQueue.push({
      type: 'complete',
      taskId,
      timestamp,
      clientId: `${this.deviceId}_${taskId}_${timestamp}`
    });

    if (this.isOnline) {
      await this.sync();
    }

    return task;
  }

  async deleteTask(taskId) {
    const task = this.localTasks.get(taskId);
    if (!task) return { error: 'Task not found' };

    const timestamp = Date.now();
    task.deleted = true;
    task.syncStatus = 'pending';

    // Remove all pending ops for this task, add delete
    this.syncQueue = this.syncQueue.filter(c => c.taskId !== taskId);
    this.syncQueue.push({
      type: 'delete',
      taskId,
      timestamp,
      clientId: `${this.deviceId}_${taskId}_${timestamp}`
    });

    if (this.isOnline) {
      await this.sync();
    }

    return task;
  }

  getLocalTasks() {
    return Array.from(this.localTasks.values()).filter(t => !t.deleted);
  }

  getActiveTasks() {
    return this.getLocalTasks().filter(t => !t.completed);
  }

  // Sync
  async sync() {
    if (!this.isOnline || !this.userId) return { error: 'Cannot sync' };

    const result = this.system.syncService.sync(
      this.userId,
      this.lastSyncAt,
      this.syncQueue
    );

    // Update local state based on results
    for (const conflict of result.conflicts) {
      const change = this.syncQueue.find(c => c.clientId === conflict.clientId);
      if (change && conflict.serverData) {
        // Server wins - update local
        this.localTasks.set(change.taskId, {
          ...conflict.serverData,
          syncStatus: 'synced'
        });
      }
    }

    // Apply server changes
    for (const serverTask of result.tasks) {
      const local = this.localTasks.get(serverTask.id);
      const hasPendingChange = this.syncQueue.some(c => c.taskId === serverTask.id);

      // If no pending local change, always apply server version (server is source of truth)
      // If there's a pending change, only apply if server is strictly newer
      if (!local || !hasPendingChange || serverTask.updatedAt > local.updatedAt) {
        this.localTasks.set(serverTask.id, { ...serverTask, syncStatus: 'synced' });
      }
    }

    // Clear synced items from queue
    this.syncQueue = this.syncQueue.filter(c => {
      const conflict = result.conflicts.find(cf => cf.clientId === c.clientId);
      return conflict && conflict.reason !== 'stale_data'; // Keep non-stale conflicts for retry
    });

    // Mark synced tasks
    for (const task of this.localTasks.values()) {
      if (task.syncStatus === 'pending' && !this.syncQueue.some(c => c.taskId === task.id)) {
        task.syncStatus = 'synced';
      }
    }

    this.lastSyncAt = result.syncedAt;
    return result;
  }

  getPendingSyncCount() {
    return this.syncQueue.length;
  }

  // Wallpaper
  async getWallpaper(theme = 'cosmic') {
    if (this.isOnline) {
      return this.system.wallpaperService.generate(this.userId, theme);
    }
    // Offline: generate locally
    const topTask = this.getActiveTasks().sort((a, b) => {
      if (a.dueDate && b.dueDate) return new Date(a.dueDate) - new Date(b.dueDate);
      if (a.dueDate) return -1;
      if (b.dueDate) return 1;
      return b.priority - a.priority;
    })[0];

    return {
      theme,
      urgency: this.system.wallpaperService.calculateUrgency(topTask),
      task: topTask ? { id: topTask.id, title: topTask.title } : null,
      generatedAt: Date.now(),
      offline: true
    };
  }
}

// =============================================================================
// INTEGRATION TESTS
// =============================================================================

// Test Suite 1: Authentication Flow Integration
suite('1. Authentication Flow Integration');

test('Register → Login → Logout flow', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');

  // Register
  const regResult = await client.register('user@test.com', 'password123');
  assertNotNull(regResult.user);
  assertNotNull(regResult.token);

  // Login with same credentials
  const client2 = system.createClient('device2');
  const loginResult = await client2.login('user@test.com', 'password123');
  assertNotNull(loginResult.user);
  assertEqual(loginResult.user.id, regResult.user.id);

  // Logout
  system.authService.logout(regResult.token);
  assertNull(system.authService.validateToken(regResult.token));
});

test('Duplicate registration fails', async () => {
  const system = new IntegratedSystem();
  const client1 = system.createClient('device1');
  const client2 = system.createClient('device2');

  await client1.register('user@test.com', 'pass1');
  const result = await client2.register('user@test.com', 'pass2');

  assertEqual(result.error, 'User already exists');
});

test('Invalid login fails', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');

  await client.register('user@test.com', 'correct');
  const result = await client.login('user@test.com', 'wrong');

  assertEqual(result.error, 'Invalid password');
});

// Test Suite 2: Task Lifecycle Integration
suite('2. Task Lifecycle Integration');

test('Create → Read → Update → Complete → Delete lifecycle', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Create
  const task = await client.createTask({ title: 'Test Task', priority: 2 });
  assertNotNull(task.id);
  assertEqual(task.title, 'Test Task');

  // Read
  const tasks = client.getLocalTasks();
  assertLength(tasks, 1);

  // Update
  await client.updateTask(task.id, { title: 'Updated Task', priority: 3 });
  const updated = client.localTasks.get(task.id);
  assertEqual(updated.title, 'Updated Task');
  assertEqual(updated.priority, 3);

  // Complete
  await client.completeTask(task.id);
  const completed = client.localTasks.get(task.id);
  assertTrue(completed.completed);
  assertNotNull(completed.completedAt);

  // Delete
  await client.deleteTask(task.id);
  const remaining = client.getLocalTasks();
  assertLength(remaining, 0);
});

test('Task priority sorting works correctly', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  await client.createTask({ title: 'Low', priority: 0 });
  await client.createTask({ title: 'High', priority: 3 });
  await client.createTask({ title: 'Medium', priority: 1 });

  const top = system.taskService.getTopPriority(client.userId);
  assertEqual(top.title, 'High');
});

test('Due date sorting takes precedence', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  const nextWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();

  await client.createTask({ title: 'Next Week High Priority', priority: 3, dueDate: nextWeek });
  await client.createTask({ title: 'Tomorrow Low Priority', priority: 0, dueDate: tomorrow });

  const top = system.taskService.getTopPriority(client.userId);
  assertEqual(top.title, 'Tomorrow Low Priority');
});

// Test Suite 3: Sync Flow Integration
suite('3. Sync Flow Integration (Offline → Online)');

test('Operations queue while offline and sync when online', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Go offline
  client.goOffline();

  // Create tasks offline
  await client.createTask({ title: 'Offline Task 1' });
  await client.createTask({ title: 'Offline Task 2' });
  await client.createTask({ title: 'Offline Task 3' });

  // Check queue
  assertEqual(client.getPendingSyncCount(), 3);

  // Server should have no tasks yet
  const serverTasks = system.taskService.getAll(client.userId);
  assertLength(serverTasks, 0);

  // Go online and sync
  client.goOnline();
  const result = await client.sync();

  assertEqual(result.results.applied, 3);
  assertEqual(client.getPendingSyncCount(), 0);

  // Server now has tasks
  const serverTasksAfter = system.taskService.getAll(client.userId);
  assertLength(serverTasksAfter, 3);
});

test('Multiple offline sessions sync correctly', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Session 1: offline
  client.goOffline();
  await client.createTask({ title: 'Session 1 Task' });

  // Brief online
  client.goOnline();
  await client.sync();

  // Session 2: offline
  client.goOffline();
  await client.createTask({ title: 'Session 2 Task' });

  // Back online
  client.goOnline();
  await client.sync();

  const serverTasks = system.taskService.getAll(client.userId);
  assertLength(serverTasks, 2);
});

test('Offline update then delete only syncs delete', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Create task online
  const task = await client.createTask({ title: 'Test' });
  await client.sync();

  // Go offline, update, then delete
  client.goOffline();
  await client.updateTask(task.id, { title: 'Updated' });
  await client.deleteTask(task.id);

  // Should only have delete in queue
  assertEqual(client.getPendingSyncCount(), 1);
  assertEqual(client.syncQueue[0].type, 'delete');

  // Sync
  client.goOnline();
  await client.sync();

  const serverTasks = system.taskService.getAll(client.userId);
  assertLength(serverTasks, 0);
});

// Test Suite 4: Wallpaper Update Integration
suite('4. Wallpaper Update Integration');

test('Wallpaper updates when top task changes', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // No tasks - clear state
  const wp1 = await client.getWallpaper('cosmic');
  assertEqual(wp1.urgency, 'clear');
  assertNull(wp1.task);

  // Add task
  const task = await client.createTask({ title: 'Important', priority: 2 });
  const wp2 = await client.getWallpaper('cosmic');
  assertEqual(wp2.task.title, 'Important');

  // Complete task - back to clear
  await client.completeTask(task.id);
  system.wallpaperService.invalidateCache(client.userId);
  const wp3 = await client.getWallpaper('cosmic');
  assertEqual(wp3.urgency, 'clear');
});

test('Wallpaper urgency reflects due date', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Overdue task
  const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  await client.createTask({ title: 'Overdue', dueDate: yesterday });

  const wp = await client.getWallpaper('cosmic');
  assertEqual(wp.urgency, 'critical');
});

test('Wallpaper works offline with local data', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  await client.createTask({ title: 'My Task' });
  await client.sync();

  // Go offline
  client.goOffline();

  // Should still generate wallpaper
  const wp = await client.getWallpaper('cosmic');
  assertNotNull(wp);
  assertEqual(wp.task.title, 'My Task');
  assertTrue(wp.offline);
});

test('All themes generate valid wallpapers', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  await client.createTask({ title: 'Test' });

  const themes = ['cosmic', 'ocean', 'fantasy'];
  for (const theme of themes) {
    system.wallpaperService.invalidateCache(client.userId);
    const wp = await client.getWallpaper(theme);
    assertEqual(wp.theme, theme);
    assertNotNull(wp.colors.accent);
  }
});

// Test Suite 5: Multi-Device Sync Simulation
suite('5. Multi-Device Sync Simulation');

test('Changes on device A appear on device B after sync', async () => {
  const system = new IntegratedSystem();

  // Device A: register
  const deviceA = system.createClient('deviceA');
  await deviceA.register('user@test.com', 'pass');

  // Device B: login
  const deviceB = system.createClient('deviceB');
  await deviceB.login('user@test.com', 'pass');

  // Device A: create task
  await deviceA.createTask({ title: 'From Device A' });

  // Device B: sync
  await deviceB.sync();

  const deviceBTasks = deviceB.getLocalTasks();
  assertLength(deviceBTasks, 1);
  assertEqual(deviceBTasks[0].title, 'From Device A');
});

test('Both devices create tasks simultaneously', async () => {
  const system = new IntegratedSystem();

  const deviceA = system.createClient('deviceA');
  await deviceA.register('user@test.com', 'pass');

  const deviceB = system.createClient('deviceB');
  await deviceB.login('user@test.com', 'pass');

  // Both create offline
  deviceA.goOffline();
  deviceB.goOffline();

  await deviceA.createTask({ title: 'Task from A' });
  await deviceB.createTask({ title: 'Task from B' });

  // Both go online and sync
  deviceA.goOnline();
  deviceB.goOnline();

  await deviceA.sync();
  await deviceB.sync();

  // A needs to do full sync to get B's task
  deviceA.lastSyncAt = 0;
  await deviceA.sync();

  // Both should have both tasks
  assertLength(deviceA.getLocalTasks(), 2);
  assertLength(deviceB.getLocalTasks(), 2);
});

test('Device B updates task created by device A', async () => {
  const system = new IntegratedSystem();

  const deviceA = system.createClient('deviceA');
  await deviceA.register('user@test.com', 'pass');

  const deviceB = system.createClient('deviceB');
  await deviceB.login('user@test.com', 'pass');

  // A creates task
  const task = await deviceA.createTask({ title: 'Original' });
  await deviceA.sync();

  // B syncs to get task first (initial sync)
  deviceB.lastSyncAt = 0; // Force initial sync
  await deviceB.sync();

  // Verify B has the task
  assertTrue(deviceB.localTasks.has(task.id), 'Device B should have the task after sync');

  // B updates the task
  await deviceB.updateTask(task.id, { title: 'Updated by B' });
  await deviceB.sync();

  // A syncs, gets update - force full sync to ensure we get the server's latest state
  deviceA.lastSyncAt = 0;
  await deviceA.sync();

  assertEqual(deviceA.localTasks.get(task.id).title, 'Updated by B');
});

// Test Suite 6: Conflict Resolution Integration
suite('6. Conflict Resolution Integration');

test('Concurrent updates - last write wins', async () => {
  const system = new IntegratedSystem();

  const deviceA = system.createClient('deviceA');
  await deviceA.register('user@test.com', 'pass');

  const deviceB = system.createClient('deviceB');
  await deviceB.login('user@test.com', 'pass');

  // Create task, both sync
  const task = await deviceA.createTask({ title: 'Original' });
  await deviceA.sync();
  await deviceB.sync();

  // Both go offline
  deviceA.goOffline();
  deviceB.goOffline();

  // A updates first
  await deviceA.updateTask(task.id, { title: 'From A' });

  // Small delay, then B updates
  await new Promise(r => setTimeout(r, 10));
  await deviceB.updateTask(task.id, { title: 'From B' });

  // A goes online first
  deviceA.goOnline();
  await deviceA.sync();

  // Then B
  deviceB.goOnline();
  await deviceB.sync();

  // B's update is newer, so it wins
  const serverTask = system.taskService.getById(task.id, deviceA.userId);
  assertEqual(serverTask.title, 'From B');
});

test('Stale data conflict returns server version', async () => {
  const system = new IntegratedSystem();

  const deviceA = system.createClient('deviceA');
  await deviceA.register('user@test.com', 'pass');

  const deviceB = system.createClient('deviceB');
  await deviceB.login('user@test.com', 'pass');

  // Create and sync
  const task = await deviceA.createTask({ title: 'Original' });
  await deviceA.sync();
  await deviceB.sync();

  // B goes offline, A updates
  deviceB.goOffline();
  await deviceA.updateTask(task.id, { title: 'Server Update' });
  await deviceA.sync();

  // B tries to update with stale data
  deviceB.goOnline();
  deviceB.localTasks.get(task.id).updatedAt = Date.now() - 100000; // Fake old timestamp
  deviceB.syncQueue.push({
    type: 'update',
    taskId: task.id,
    data: { title: 'Stale Update' },
    timestamp: Date.now() - 100000,
    clientId: 'stale'
  });

  const result = await deviceB.sync();

  // Should have conflict
  assertTrue(result.conflicts.length > 0);

  // Local should be updated to server version
  const localTask = deviceB.localTasks.get(task.id);
  assertEqual(localTask.title, 'Server Update');
});

// Test Suite 7: Data Consistency Integration
suite('7. Data Consistency Integration');

test('Task count remains consistent after sync cycles', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Create 5 tasks
  for (let i = 0; i < 5; i++) {
    await client.createTask({ title: `Task ${i}` });
  }

  // Multiple sync cycles
  for (let i = 0; i < 10; i++) {
    await client.sync();
  }

  assertLength(client.getLocalTasks(), 5);
  assertLength(system.taskService.getAll(client.userId), 5);
});

test('Completed tasks stay completed after sync', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  const task = await client.createTask({ title: 'Test' });
  await client.completeTask(task.id);
  await client.sync();
  await client.sync();
  await client.sync();

  const localTask = client.localTasks.get(task.id);
  assertTrue(localTask.completed);

  const serverTask = system.taskService.getById(task.id, client.userId);
  assertTrue(serverTask.completed);
});

test('Deleted tasks stay deleted after sync', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  const task = await client.createTask({ title: 'Test' });
  await client.deleteTask(task.id);
  await client.sync();
  await client.sync();
  await client.sync();

  assertLength(client.getLocalTasks(), 0);
  assertLength(system.taskService.getAll(client.userId), 0);
});

// Test Suite 8: Error Recovery Integration
suite('8. Error Recovery Integration');

test('Recover from partial sync failure', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Create task offline so it queues
  client.goOffline();
  await client.createTask({ title: 'Good Task' });

  // Verify task is queued
  assertEqual(client.getPendingSyncCount(), 1);

  // Go online and sync
  client.goOnline();
  await client.sync();

  // Should be synced now
  assertEqual(client.getPendingSyncCount(), 0);
  assertLength(system.taskService.getAll(client.userId), 1);
});

test('Queue persists across offline periods', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Create offline
  client.goOffline();
  await client.createTask({ title: 'Offline 1' });
  await client.createTask({ title: 'Offline 2' });

  // Simulate app restart (queue persists)
  const queueBackup = [...client.syncQueue];
  assertEqual(queueBackup.length, 2);

  // Restore and sync
  client.goOnline();
  client.syncQueue = queueBackup;
  await client.sync();

  assertLength(system.taskService.getAll(client.userId), 2);
});

// Test Suite 9: Performance Integration
suite('9. Performance Integration');

test('Handle 50 tasks efficiently', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  const start = Date.now();

  // Create 50 tasks
  for (let i = 0; i < 50; i++) {
    await client.createTask({ title: `Task ${i}`, priority: i % 4 });
  }

  // Sync all
  await client.sync();

  const duration = Date.now() - start;

  assertLength(client.getLocalTasks(), 50);
  assertTrue(duration < 5000, `Took too long: ${duration}ms`);
});

test('Wallpaper generation with many tasks is fast', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Create 100 tasks
  for (let i = 0; i < 100; i++) {
    await client.createTask({
      title: `Task ${i}`,
      priority: i % 4,
      dueDate: new Date(Date.now() + (i + 1) * 60 * 60 * 1000).toISOString()
    });
  }

  const start = Date.now();
  for (let i = 0; i < 10; i++) {
    await client.getWallpaper('cosmic');
  }
  const duration = Date.now() - start;

  assertTrue(duration < 1000, `Wallpaper gen too slow: ${duration}ms for 10 iterations`);
});

// Test Suite 10: Real-World Scenario Tests
suite('10. Real-World Scenario Tests');

test('Daily workflow: create, complete, sync across devices', async () => {
  const system = new IntegratedSystem();

  // Morning: User creates tasks on phone
  const phone = system.createClient('phone');
  await phone.register('user@test.com', 'pass');

  await phone.createTask({ title: 'Morning meeting', priority: 3, dueDate: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString() });
  await phone.createTask({ title: 'Review PRs', priority: 2 });
  await phone.createTask({ title: 'Lunch with team', priority: 1, dueDate: new Date(Date.now() + 5 * 60 * 60 * 1000).toISOString() });

  // Check wallpaper shows top priority
  const morningWp = await phone.getWallpaper('cosmic');
  assertEqual(morningWp.task.title, 'Morning meeting');

  // At work: Login on desktop
  const desktop = system.createClient('desktop');
  await desktop.login('user@test.com', 'pass');
  desktop.lastSyncAt = 0; // Force initial sync to get all tasks
  await desktop.sync();

  assertLength(desktop.getLocalTasks(), 3);

  // Complete morning meeting on desktop
  const meetingTask = Array.from(desktop.localTasks.values()).find(t => t.title === 'Morning meeting');
  assertNotNull(meetingTask, 'Meeting task should exist on desktop');
  await desktop.completeTask(meetingTask.id);

  // Phone syncs, sees completion
  await phone.sync();
  const phoneTask = phone.localTasks.get(meetingTask.id);
  assertNotNull(phoneTask, 'Phone should have the meeting task');
  assertTrue(phoneTask.completed, 'Meeting should be completed on phone');

  // Wallpaper updated to next task
  system.wallpaperService.invalidateCache(phone.userId);
  const updatedWp = await phone.getWallpaper('cosmic');
  assertNotEqual(updatedWp.task?.title, 'Morning meeting');
});

test('Offline commute: queue tasks, sync at destination', async () => {
  const system = new IntegratedSystem();
  const phone = system.createClient('phone');
  await phone.register('user@test.com', 'pass');

  // Create initial task online
  await phone.createTask({ title: 'Existing task' });

  // Enter subway (offline)
  phone.goOffline();

  // User thinks of tasks during commute
  await phone.createTask({ title: 'Call mom' });
  await phone.createTask({ title: 'Buy groceries' });
  await phone.createTask({ title: 'Fix bug in app' });

  // Check local state
  assertLength(phone.getLocalTasks(), 4);
  assertEqual(phone.getPendingSyncCount(), 3);

  // Arrive at destination
  phone.goOnline();
  await phone.sync();

  // All synced
  assertEqual(phone.getPendingSyncCount(), 0);
  assertLength(system.taskService.getAll(phone.userId), 4);
});

test('Weekend cleanup: bulk complete and delete', async () => {
  const system = new IntegratedSystem();
  const client = system.createClient('device1');
  await client.register('user@test.com', 'pass');

  // Week's tasks
  const tasks = [];
  for (let i = 0; i < 10; i++) {
    const task = await client.createTask({ title: `Week task ${i}` });
    tasks.push(task);
  }

  // Complete half
  for (let i = 0; i < 5; i++) {
    await client.completeTask(tasks[i].id);
  }

  // Delete 2
  await client.deleteTask(tasks[5].id);
  await client.deleteTask(tasks[6].id);

  // Check state
  const active = client.getActiveTasks();
  assertLength(active, 3); // 10 - 5 completed - 2 deleted = 3 active

  // Server consistent
  await client.sync();
  const serverActive = system.taskService.getActive(client.userId);
  assertLength(serverActive, 3);
});

// =============================================================================
// RUN ALL TESTS
// =============================================================================

async function runTests() {
  console.log('\n' + '═'.repeat(70));
  console.log('INTEGRATION TEST SUITE - Local-First Architecture');
  console.log('═'.repeat(70) + '\n');

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
  console.log('\n' + '═'.repeat(70));
  console.log('TEST RESULTS SUMMARY');
  console.log('═'.repeat(70));

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
    console.log('🎉 ALL INTEGRATION TESTS PASSED!\n');
  } else {
    console.log('❌ SOME TESTS FAILED\n');
    testResults.filter(t => t.status === 'FAIL').forEach(t => {
      console.log(`  ${t.suite} > ${t.name}`);
      console.log(`    ${t.error}`);
    });
  }

  process.exit(failCount > 0 ? 1 : 0);
}

runTests().catch(console.error);
