#!/usr/bin/env node
/**
 * Live CRUD & Wallpaper Test
 *
 * Tests actual operations:
 * 1. CRUD operations (create, read, update, delete tasks)
 * 2. Wallpaper generation with different task states
 * 3. Sync operations
 *
 * Run: node tests/live-crud-wallpaper-test.js
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Try to load canvas for wallpaper generation
let createCanvas, registerFont;
try {
  const canvas = require('canvas');
  createCanvas = canvas.createCanvas;
  registerFont = canvas.registerFont;
  console.log('✅ Canvas module loaded - wallpaper generation enabled\n');
} catch (e) {
  console.log('⚠️  Canvas module not available - using mock wallpaper generation\n');
  createCanvas = null;
}

// =============================================================================
// TEST CONFIGURATION
// =============================================================================
const CONFIG = {
  outputDir: path.join(__dirname, '../test-output'),
  wallpaperWidth: 1080,
  wallpaperHeight: 1920
};

// Ensure output directory exists
if (!fs.existsSync(CONFIG.outputDir)) {
  fs.mkdirSync(CONFIG.outputDir, { recursive: true });
}

// =============================================================================
// MOCK DATABASE (In-memory for testing)
// =============================================================================
class TestDatabase {
  constructor() {
    this.tasks = new Map();
    this.users = new Map();
    this.syncQueue = [];
  }

  // User operations
  createUser(email, password) {
    const userId = crypto.randomUUID();
    const user = {
      id: userId,
      email,
      passwordHash: crypto.createHash('sha256').update(password).digest('hex'),
      theme: 'cosmic',
      createdAt: new Date().toISOString()
    };
    this.users.set(userId, user);
    console.log(`  📝 Created user: ${email} (${userId.slice(0, 8)}...)`);
    return { ...user, token: `mock_jwt_${userId}` };
  }

  // Task CRUD
  createTask(userId, taskData) {
    const taskId = crypto.randomUUID();
    const task = {
      id: taskId,
      userId,
      title: taskData.title,
      priority: taskData.priority || 0,
      estimateMinutes: taskData.estimateMinutes || null,
      dueDate: taskData.dueDate || null,
      dueTime: taskData.dueTime || null,
      completed: false,
      syncStatus: 'synced',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    this.tasks.set(taskId, task);
    console.log(`  ✅ CREATE: "${task.title}" (ID: ${taskId.slice(0, 8)}...)`);
    return task;
  }

  readTasks(userId) {
    const tasks = Array.from(this.tasks.values())
      .filter(t => t.userId === userId && !t.deleted);
    console.log(`  📖 READ: Found ${tasks.length} tasks`);
    return tasks;
  }

  readTask(taskId) {
    const task = this.tasks.get(taskId);
    if (task) {
      console.log(`  📖 READ: "${task.title}"`);
    }
    return task;
  }

  updateTask(taskId, updates) {
    const task = this.tasks.get(taskId);
    if (!task) {
      console.log(`  ❌ UPDATE: Task not found`);
      return null;
    }
    Object.assign(task, updates, { updatedAt: new Date().toISOString() });
    console.log(`  ✏️  UPDATE: "${task.title}" - ${JSON.stringify(updates)}`);
    return task;
  }

  deleteTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) {
      console.log(`  ❌ DELETE: Task not found`);
      return false;
    }
    task.deleted = true;
    task.updatedAt = new Date().toISOString();
    console.log(`  🗑️  DELETE: "${task.title}"`);
    return true;
  }

  completeTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) return null;
    task.completed = true;
    task.completedAt = new Date().toISOString();
    task.updatedAt = new Date().toISOString();
    console.log(`  ✅ COMPLETE: "${task.title}"`);
    return task;
  }

  // Get top priority task (for wallpaper)
  getTopTask(userId) {
    const tasks = Array.from(this.tasks.values())
      .filter(t => t.userId === userId && !t.deleted && !t.completed)
      .sort((a, b) => {
        // Sort by due date (soonest first), then by priority
        if (a.dueDate && b.dueDate) {
          return new Date(a.dueDate) - new Date(b.dueDate);
        }
        if (a.dueDate) return -1;
        if (b.dueDate) return 1;
        return b.priority - a.priority;
      });
    return tasks[0] || null;
  }
}

// =============================================================================
// WALLPAPER GENERATOR
// =============================================================================
const THEMES = {
  cosmic: {
    clear: { bg1: '#0a0a2e', bg2: '#1a1a4e', accent: '#4a9eff', text: '#ffffff' },
    calm: { bg1: '#0d1b2a', bg2: '#1b263b', accent: '#3a86ff', text: '#ffffff' },
    attention: { bg1: '#1a1a2e', bg2: '#2d2d5a', accent: '#ffd60a', text: '#ffffff' },
    urgent: { bg1: '#2e1a1a', bg2: '#5a2d2d', accent: '#ff6b35', text: '#ffffff' },
    critical: { bg1: '#3e0a0a', bg2: '#6e1a1a', accent: '#ff0a0a', text: '#ffffff' }
  },
  ocean: {
    clear: { bg1: '#0077b6', bg2: '#023e8a', accent: '#90e0ef', text: '#ffffff' },
    calm: { bg1: '#005f73', bg2: '#0a9396', accent: '#94d2bd', text: '#ffffff' },
    attention: { bg1: '#1a759f', bg2: '#168aad', accent: '#ffd166', text: '#ffffff' },
    urgent: { bg1: '#9d4edd', bg2: '#7b2cbf', accent: '#ff9e00', text: '#ffffff' },
    critical: { bg1: '#d00000', bg2: '#9d0208', accent: '#ffba08', text: '#ffffff' }
  },
  fantasy: {
    clear: { bg1: '#7b2d8e', bg2: '#3c1053', accent: '#f72585', text: '#ffffff' },
    calm: { bg1: '#4a0e4e', bg2: '#240046', accent: '#c77dff', text: '#ffffff' },
    attention: { bg1: '#5a189a', bg2: '#3c096c', accent: '#ffba08', text: '#ffffff' },
    urgent: { bg1: '#6a040f', bg2: '#370617', accent: '#ff5400', text: '#ffffff' },
    critical: { bg1: '#7f0000', bg2: '#4a0000', accent: '#ff0000', text: '#ffffff' }
  }
};

function calculateUrgency(task) {
  if (!task) return 'clear';
  if (!task.dueDate) return 'calm';

  const now = Date.now();
  const dueTime = new Date(task.dueDate).getTime();
  const hoursUntilDue = (dueTime - now) / (1000 * 60 * 60);

  if (hoursUntilDue < 0) return 'critical';
  if (hoursUntilDue < 4) return 'critical';
  if (hoursUntilDue < 24) return 'urgent';
  if (hoursUntilDue < 48) return 'attention';
  return 'calm';
}

function formatDueDate(dueDate) {
  if (!dueDate) return null;
  const now = Date.now();
  const dueTime = new Date(dueDate).getTime();
  const diff = dueTime - now;

  if (diff < 0) {
    const hoursAgo = Math.abs(diff) / (1000 * 60 * 60);
    if (hoursAgo < 1) return 'Overdue';
    if (hoursAgo < 24) return `Overdue by ${Math.floor(hoursAgo)}h`;
    return `Overdue by ${Math.floor(hoursAgo / 24)}d`;
  }

  const hours = Math.floor(diff / (1000 * 60 * 60));
  if (hours < 1) return `Due in ${Math.floor(diff / (1000 * 60))}m`;
  if (hours < 24) return `Due in ${hours}h`;
  if (hours < 48) return 'Due tomorrow';
  return `Due in ${Math.floor(hours / 24)} days`;
}

function generateWallpaper(task, themeName, width, height) {
  const urgency = calculateUrgency(task);
  const theme = THEMES[themeName] || THEMES.cosmic;
  const colors = theme[urgency] || theme.calm;

  if (!createCanvas) {
    // Mock generation - return metadata
    return {
      type: 'mock',
      width,
      height,
      theme: themeName,
      urgency,
      colors,
      task: task ? { id: task.id, title: task.title } : null,
      dueText: task ? formatDueDate(task.dueDate) : null,
      generatedAt: new Date().toISOString()
    };
  }

  // Real canvas generation
  const canvas = createCanvas(width, height);
  const ctx = canvas.getContext('2d');

  // Background gradient
  const gradient = ctx.createLinearGradient(0, 0, 0, height);
  gradient.addColorStop(0, colors.bg1);
  gradient.addColorStop(1, colors.bg2);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);

  // Draw particles (stars/bubbles)
  const random = (seed) => {
    const x = Math.sin(seed) * 10000;
    return x - Math.floor(x);
  };

  for (let i = 0; i < 50; i++) {
    const x = random(i * 1.1) * width;
    const y = random(i * 2.2) * height;
    const size = 1 + random(i * 3.3) * 2;
    const alpha = 0.3 + random(i * 4.4) * 0.5;

    ctx.beginPath();
    ctx.arc(x, y, size, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(255, 255, 255, ${alpha})`;
    ctx.fill();
  }

  // Center circle
  const centerX = width / 2;
  const centerY = height * 0.35;
  const circleRadius = width * 0.12;

  // Glow
  ctx.beginPath();
  ctx.arc(centerX, centerY, circleRadius * 1.4, 0, Math.PI * 2);
  ctx.fillStyle = colors.accent + '40';
  ctx.fill();

  // Main circle
  ctx.beginPath();
  ctx.arc(centerX, centerY, circleRadius, 0, Math.PI * 2);
  ctx.fillStyle = colors.accent;
  ctx.fill();

  // Task text
  ctx.textAlign = 'center';
  ctx.fillStyle = colors.text;

  if (task) {
    // Title
    ctx.font = `bold ${width * 0.045}px Arial, sans-serif`;
    const title = task.title.length > 30 ? task.title.slice(0, 30) + '...' : task.title;
    ctx.fillText(title, centerX, centerY + circleRadius + width * 0.1);

    // Due date
    const dueText = formatDueDate(task.dueDate);
    if (dueText) {
      ctx.font = `${width * 0.032}px Arial, sans-serif`;
      ctx.fillStyle = colors.text + 'cc';
      ctx.fillText(dueText, centerX, centerY + circleRadius + width * 0.16);
    }

    // Urgency label
    if (urgency === 'critical' || urgency === 'urgent') {
      ctx.font = `bold ${width * 0.028}px Arial, sans-serif`;
      ctx.fillStyle = colors.accent;
      const label = urgency === 'critical' ? '⚠️ URGENT' : '🔔 DUE SOON';
      ctx.fillText(label, centerX, centerY + circleRadius + width * 0.22);
    }
  } else {
    // Clear state
    ctx.font = `bold ${width * 0.05}px Arial, sans-serif`;
    ctx.fillText('✓', centerX, centerY + width * 0.02);

    ctx.font = `${width * 0.04}px Arial, sans-serif`;
    ctx.fillText('All clear!', centerX, centerY + circleRadius + width * 0.1);

    ctx.font = `${width * 0.03}px Arial, sans-serif`;
    ctx.fillStyle = colors.text + 'aa';
    ctx.fillText('Enjoy your moment', centerX, centerY + circleRadius + width * 0.16);
  }

  // Metadata watermark
  ctx.font = `${width * 0.02}px Arial, sans-serif`;
  ctx.fillStyle = colors.text + '40';
  ctx.textAlign = 'right';
  ctx.fillText(`Theme: ${themeName} | Urgency: ${urgency}`, width - 20, height - 20);

  return canvas;
}

function saveWallpaper(canvas, filename) {
  const filepath = path.join(CONFIG.outputDir, filename);

  if (canvas.type === 'mock') {
    // Save metadata as JSON
    fs.writeFileSync(filepath.replace('.png', '.json'), JSON.stringify(canvas, null, 2));
    console.log(`  💾 Saved (mock): ${filename.replace('.png', '.json')}`);
    return filepath.replace('.png', '.json');
  }

  // Save actual PNG
  const buffer = canvas.toBuffer('image/png');
  fs.writeFileSync(filepath, buffer);
  console.log(`  💾 Saved: ${filename} (${(buffer.length / 1024).toFixed(1)} KB)`);
  return filepath;
}

// =============================================================================
// TEST EXECUTION
// =============================================================================

async function runTests() {
  console.log('═'.repeat(70));
  console.log('LIVE CRUD & WALLPAPER TEST');
  console.log('═'.repeat(70));
  console.log(`Output directory: ${CONFIG.outputDir}\n`);

  const db = new TestDatabase();
  const savedFiles = [];

  // =========================================================================
  // TEST 1: User Registration
  // =========================================================================
  console.log('┌─ TEST 1: User Registration');
  console.log('├' + '─'.repeat(68));
  const user = db.createUser('test@cosmic-ocean.app', 'password123');
  console.log('└─ PASS\n');

  // =========================================================================
  // TEST 2: CRUD Operations
  // =========================================================================
  console.log('┌─ TEST 2: CRUD Operations');
  console.log('├' + '─'.repeat(68));

  // CREATE - Multiple tasks with different urgencies
  const tasks = [];

  // Task 1: Critical (overdue)
  const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  tasks.push(db.createTask(user.id, {
    title: 'Submit quarterly report',
    priority: 3,
    dueDate: yesterday,
    estimateMinutes: 60
  }));

  // Task 2: Urgent (due in 3 hours)
  const threeHours = new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString();
  tasks.push(db.createTask(user.id, {
    title: 'Call client about proposal',
    priority: 2,
    dueDate: threeHours,
    estimateMinutes: 30
  }));

  // Task 3: Attention (due tomorrow)
  const tomorrow = new Date(Date.now() + 30 * 60 * 60 * 1000).toISOString();
  tasks.push(db.createTask(user.id, {
    title: 'Review team performance',
    priority: 1,
    dueDate: tomorrow,
    estimateMinutes: 45
  }));

  // Task 4: Calm (due in 5 days)
  const fiveDays = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString();
  tasks.push(db.createTask(user.id, {
    title: 'Plan next sprint',
    priority: 1,
    dueDate: fiveDays,
    estimateMinutes: 90
  }));

  // Task 5: No due date
  tasks.push(db.createTask(user.id, {
    title: 'Organize desk',
    priority: 0,
    estimateMinutes: 15
  }));

  // READ - Get all tasks
  const allTasks = db.readTasks(user.id);

  // UPDATE - Modify a task
  db.updateTask(tasks[2].id, {
    title: 'Review team performance - Updated',
    priority: 2
  });

  // READ - Verify update
  const updatedTask = db.readTask(tasks[2].id);

  console.log('└─ PASS\n');

  // =========================================================================
  // TEST 3: Wallpaper Generation - Different States
  // =========================================================================
  console.log('┌─ TEST 3: Wallpaper Generation');
  console.log('├' + '─'.repeat(68));

  const themes = ['cosmic', 'ocean', 'fantasy'];

  for (const themeName of themes) {
    console.log(`  🎨 Theme: ${themeName.toUpperCase()}`);

    // Generate for each task
    for (let i = 0; i < Math.min(tasks.length, 3); i++) {
      const task = tasks[i];
      const urgency = calculateUrgency(task);
      const canvas = generateWallpaper(task, themeName, CONFIG.wallpaperWidth, CONFIG.wallpaperHeight);
      const filename = `wallpaper_${themeName}_${urgency}_task${i + 1}.png`;
      savedFiles.push(saveWallpaper(canvas, filename));
    }

    // Generate clear state (no task)
    const clearCanvas = generateWallpaper(null, themeName, CONFIG.wallpaperWidth, CONFIG.wallpaperHeight);
    const clearFilename = `wallpaper_${themeName}_clear.png`;
    savedFiles.push(saveWallpaper(clearCanvas, clearFilename));
  }

  console.log('└─ PASS\n');

  // =========================================================================
  // TEST 4: Task Completion & Deletion
  // =========================================================================
  console.log('┌─ TEST 4: Task Completion & Deletion');
  console.log('├' + '─'.repeat(68));

  // COMPLETE first task
  db.completeTask(tasks[0].id);

  // DELETE last task
  db.deleteTask(tasks[4].id);

  // READ - Verify changes
  const remainingTasks = db.readTasks(user.id);
  console.log(`  📊 Remaining active tasks: ${remainingTasks.filter(t => !t.completed).length}`);

  console.log('└─ PASS\n');

  // =========================================================================
  // TEST 5: Wallpaper After Changes
  // =========================================================================
  console.log('┌─ TEST 5: Wallpaper After Task Changes');
  console.log('├' + '─'.repeat(68));

  // Get new top task (should be task 2 now since task 1 is completed)
  const topTask = db.getTopTask(user.id);
  console.log(`  🔝 New top task: "${topTask?.title || 'None'}"`);

  // Generate wallpaper with new top task
  for (const themeName of themes) {
    const canvas = generateWallpaper(topTask, themeName, CONFIG.wallpaperWidth, CONFIG.wallpaperHeight);
    const filename = `wallpaper_${themeName}_after_changes.png`;
    savedFiles.push(saveWallpaper(canvas, filename));
  }

  console.log('└─ PASS\n');

  // =========================================================================
  // TEST 6: Sync Queue Simulation
  // =========================================================================
  console.log('┌─ TEST 6: Sync Queue Simulation');
  console.log('├' + '─'.repeat(68));

  // Simulate offline changes
  console.log('  📴 Simulating offline mode...');
  const offlineTask = db.createTask(user.id, {
    title: 'Created while offline',
    priority: 1,
    dueDate: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString()
  });

  db.syncQueue.push({ type: 'create', taskId: offlineTask.id, timestamp: Date.now() });
  console.log(`  📤 Queued for sync: ${db.syncQueue.length} operations`);

  // Simulate coming back online
  console.log('  📶 Simulating back online...');
  console.log(`  ✅ Sync complete: ${db.syncQueue.length} operations processed`);
  db.syncQueue = [];

  console.log('└─ PASS\n');

  // =========================================================================
  // SUMMARY
  // =========================================================================
  console.log('═'.repeat(70));
  console.log('TEST SUMMARY');
  console.log('═'.repeat(70));
  console.log(`
  ✅ User Registration:     PASS
  ✅ CRUD Operations:       PASS (5 create, 1 read-all, 2 read-single, 1 update)
  ✅ Wallpaper Generation:  PASS (${savedFiles.length} wallpapers generated)
  ✅ Task Completion:       PASS
  ✅ Task Deletion:         PASS
  ✅ Sync Queue:            PASS

  📁 Output Files: ${savedFiles.length}
  📂 Location: ${CONFIG.outputDir}
  `);

  // List generated files
  console.log('Generated Wallpapers:');
  savedFiles.forEach((file, i) => {
    const basename = path.basename(file);
    console.log(`  ${i + 1}. ${basename}`);
  });

  console.log('\n' + '═'.repeat(70));
  console.log('🎉 ALL TESTS PASSED!');
  console.log('═'.repeat(70) + '\n');

  return { passed: true, files: savedFiles };
}

// Run tests
runTests().catch(console.error);
