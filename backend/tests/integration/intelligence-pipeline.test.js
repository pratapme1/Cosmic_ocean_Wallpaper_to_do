/**
 * Integration Test Suite - Intelligence Pipeline
 * Tests the complete flow: Tasks → Stats → Atmosphere → Messages → Wallpaper
 *
 * These tests verify that all intelligence layer components work together
 * correctly when processing real task data.
 */

const { MessageEngine } = require('../../services/message-engine');
const { AtmosphereController } = require('../../services/atmosphere-controller');
const { StatsAggregator } = require('../../services/stats-aggregator');

describe('Intelligence Pipeline Integration Tests', () => {
  let messageEngine;
  let atmosphereController;
  let statsAggregator;

  beforeEach(() => {
    statsAggregator = new StatsAggregator({ cacheEnabled: false });
    messageEngine = new MessageEngine(statsAggregator);
    atmosphereController = new AtmosphereController();
  });

  // Helper to create realistic task data
  const createRealisticTasks = () => {
    const now = new Date();
    return [
      {
        id: '1',
        title: 'Review quarterly report',
        dueDate: new Date(now.getTime() + 2 * 60 * 60 * 1000).toISOString(), // Due in 2 hours
        priority: 1,
        completed: false,
        archived: false,
        category: 'work',
        createdAt: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString()
      },
      {
        id: '2',
        title: 'Call dentist for appointment',
        dueDate: new Date(now.getTime() + 8 * 60 * 60 * 1000).toISOString(), // Due in 8 hours
        priority: 2,
        completed: false,
        archived: false,
        category: 'personal',
        createdAt: new Date(now.getTime() - 48 * 60 * 60 * 1000).toISOString()
      },
      {
        id: '3',
        title: 'Complete project documentation',
        dueDate: new Date(now.getTime() + 48 * 60 * 60 * 1000).toISOString(), // Due in 2 days
        priority: 2,
        completed: false,
        archived: false,
        category: 'work',
        createdAt: new Date(now.getTime() - 72 * 60 * 60 * 1000).toISOString()
      },
      {
        id: '4',
        title: 'Buy groceries',
        dueDate: null,
        priority: 3,
        completed: false,
        archived: false,
        category: 'errands',
        createdAt: new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString()
      },
      {
        id: '5',
        title: 'Morning standup completed',
        dueDate: new Date(now.getTime() - 4 * 60 * 60 * 1000).toISOString(),
        priority: 2,
        completed: true,
        completedAt: new Date(now.getTime() - 3 * 60 * 60 * 1000).toISOString(),
        archived: false,
        category: 'work'
      }
    ];
  };

  // ============================================================
  // FULL PIPELINE TESTS (5 cases)
  // ============================================================
  describe('Full Pipeline Flow', () => {
    test('pipeline processes tasks through all stages', async () => {
      const tasks = createRealisticTasks();
      const userId = 'test-user-1';

      // Stage 1: Stats Aggregation
      const stats = await statsAggregator.getStats(userId, tasks);
      expect(stats).toHaveProperty('completedToday');
      expect(stats).toHaveProperty('patterns');

      // Stage 2: Atmosphere Calculation
      const atmosphere = atmosphereController.calculateState(tasks, stats);
      expect(atmosphere).toHaveProperty('urgencyScore');
      expect(atmosphere).toHaveProperty('visualParams');

      // Stage 3: Message Generation
      const message = messageEngine.generateMessage({}, tasks, new Date(), stats);
      expect(message).toHaveProperty('text');
      expect(message).toHaveProperty('priority');
      expect(message.text.length).toBeGreaterThan(0);
    });

    test('overdue task triggers critical message and high urgency', async () => {
      const now = new Date();
      const overdueTask = {
        id: '1',
        title: 'Missed deadline',
        dueDate: new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString(),
        priority: 1,
        completed: false,
        archived: false
      };

      const tasks = [overdueTask];
      const stats = await statsAggregator.getStats('user1', tasks);
      const atmosphere = atmosphereController.calculateState(tasks);
      const message = messageEngine.generateMessage({}, tasks, now, stats);

      // Atmosphere should be at least CALM (25 points for overdue)
      expect(atmosphere.urgencyScore).toBeGreaterThanOrEqual(25);

      // Message should be CRITICAL
      expect(message.priority).toBe(0); // CRITICAL = 0
    });

    test('all tasks completed triggers achievement message and clear state', async () => {
      const now = new Date();
      const completedTasks = [
        {
          id: '1',
          title: 'Task 1',
          completed: true,
          completedAt: now.toISOString(),
          archived: false
        },
        {
          id: '2',
          title: 'Task 2',
          completed: true,
          completedAt: now.toISOString(),
          archived: false
        }
      ];

      const atmosphere = atmosphereController.calculateState(completedTasks);
      const message = messageEngine.generateMessage({}, completedTasks, now);

      // Should be CLEAR state (no active tasks)
      expect(atmosphere.state).toBe('clear');
      expect(atmosphere.urgencyScore).toBe(0);

      // Message should be ACHIEVEMENT (all done)
      expect(message.priority).toBe(1); // ACHIEVEMENT = 1
    });

    test('streak data flows from stats to message', async () => {
      const now = new Date();
      // Create tasks with completions over 5 days
      const tasks = [];
      for (let i = 0; i < 5; i++) {
        const completedAt = new Date(now);
        completedAt.setDate(completedAt.getDate() - i);
        completedAt.setHours(12, 0, 0, 0);
        tasks.push({
          id: String(i),
          title: `Task ${i}`,
          completed: true,
          completedAt: completedAt.toISOString()
        });
      }

      // Add one active task so message isn't "all done"
      tasks.push({
        id: '99',
        title: 'Active task',
        completed: false,
        archived: false,
        dueDate: new Date(now.getTime() + 48 * 60 * 60 * 1000).toISOString()
      });

      const stats = await statsAggregator.getStats('user1', tasks);

      // Stats should show streak
      expect(stats.streakDays).toBeGreaterThanOrEqual(3);

      // Message should reflect streak (if streak >= 3)
      const message = messageEngine.generateMessage({}, tasks, now, stats);
      expect(message.priority).toBe(1); // ACHIEVEMENT
      expect(message.text).toContain(String(stats.streakDays));
    });

    test('visual params scale with urgency score', () => {
      const now = new Date();

      // Low urgency: task due in 2 days
      const lowUrgencyTasks = [{
        id: '1',
        title: 'Low urgency',
        dueDate: new Date(now.getTime() + 48 * 60 * 60 * 1000).toISOString(),
        completed: false
      }];

      // High urgency: overdue task
      const highUrgencyTasks = [{
        id: '1',
        title: 'High urgency',
        dueDate: new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString(),
        completed: false
      }];

      const lowAtmosphere = atmosphereController.calculateState(lowUrgencyTasks);
      const highAtmosphere = atmosphereController.calculateState(highUrgencyTasks);

      // High urgency should have faster animation and more particles
      expect(highAtmosphere.visualParams.animationSpeed)
        .toBeGreaterThan(lowAtmosphere.visualParams.animationSpeed);
      expect(highAtmosphere.visualParams.particleCount)
        .toBeGreaterThan(lowAtmosphere.visualParams.particleCount);
    });
  });

  // ============================================================
  // COMPONENT INTERACTION TESTS (4 cases)
  // ============================================================
  describe('Component Interactions', () => {
    test('stats aggregator output is compatible with message engine', async () => {
      const tasks = createRealisticTasks();
      const stats = await statsAggregator.getStats('user1', tasks);

      // Message engine should accept stats without error
      expect(() => {
        messageEngine.generateMessage({}, tasks, new Date(), stats);
      }).not.toThrow();
    });

    test('atmosphere controller handles same tasks as message engine', () => {
      const tasks = createRealisticTasks();

      // Both should process without error
      expect(() => {
        atmosphereController.calculateState(tasks);
        messageEngine.generateMessage({}, tasks, new Date());
      }).not.toThrow();
    });

    test('pattern data flows correctly through pipeline', async () => {
      const now = new Date();
      // Create 15 tasks completed in the morning for pattern detection
      const tasks = [];
      for (let i = 0; i < 15; i++) {
        const completedAt = new Date(now);
        completedAt.setDate(completedAt.getDate() - i);
        completedAt.setHours(9, 0, 0, 0); // Morning
        tasks.push({
          id: String(i),
          title: `Morning task ${i}`,
          completed: true,
          completedAt: completedAt.toISOString(),
          category: 'work'
        });
      }

      const stats = await statsAggregator.getStats('user1', tasks);

      // Stats should detect morning pattern
      expect(stats.patterns.peakPeriod).toBe('morning');
      expect(stats.patterns.topCategory).toBe('work');
    });

    test('task count consistency across components', () => {
      const tasks = createRealisticTasks();

      const atmosphere = atmosphereController.calculateState(tasks);
      const activeTasks = tasks.filter(t => !t.completed && !t.archived);

      // Atmosphere task count should match active tasks
      expect(atmosphere.taskCounts.total).toBe(activeTasks.length);
    });
  });

  // ============================================================
  // EDGE CASE INTEGRATION TESTS (4 cases)
  // ============================================================
  describe('Edge Case Integration', () => {
    test('empty tasks handled gracefully by all components', async () => {
      const tasks = [];
      const stats = await statsAggregator.getStats('user1', tasks);
      const atmosphere = atmosphereController.calculateState(tasks);
      const message = messageEngine.generateMessage({}, tasks, new Date(), stats);

      expect(stats.totalCompleted).toBe(0);
      expect(atmosphere.state).toBe('clear');
      expect(message.text.length).toBeGreaterThan(0);
    });

    test('null user handled by stats aggregator', async () => {
      const tasks = createRealisticTasks();

      // Should not throw with null userId
      await expect(statsAggregator.getStats(null, tasks)).resolves.toHaveProperty('totalCompleted');
    });

    test('mixed date formats handled correctly', async () => {
      const now = new Date();
      const tasks = [
        {
          id: '1',
          title: 'Task with dueDate',
          dueDate: new Date(now.getTime() + 1 * 60 * 60 * 1000).toISOString(),
          completed: false
        },
        {
          id: '2',
          title: 'Task with due_date',
          due_date: new Date(now.getTime() + 2 * 60 * 60 * 1000).toISOString(),
          completed: false
        }
      ];

      // All components should handle both formats
      const atmosphere = atmosphereController.calculateState(tasks);
      const message = messageEngine.generateMessage({}, tasks, now);

      expect(atmosphere.urgencyScore).toBeGreaterThan(0);
      expect(message).not.toBeNull();
    });

    test('high task count handled efficiently', async () => {
      const now = new Date();
      // Create 100 tasks
      const tasks = Array(100).fill(null).map((_, i) => ({
        id: String(i),
        title: `Task ${i}`,
        dueDate: new Date(now.getTime() + (i + 1) * 60 * 60 * 1000).toISOString(),
        completed: i < 50, // 50 completed, 50 active
        completedAt: i < 50 ? new Date(now.getTime() - i * 60 * 60 * 1000).toISOString() : null
      }));

      const startTime = Date.now();

      const stats = await statsAggregator.getStats('user1', tasks);
      const atmosphere = atmosphereController.calculateState(tasks);
      const message = messageEngine.generateMessage({}, tasks, now, stats);

      const duration = Date.now() - startTime;

      // Should complete within 500ms
      expect(duration).toBeLessThan(500);
      expect(stats.totalCompleted).toBe(50);
      expect(atmosphere.taskCounts.total).toBe(50);
    });
  });

  // ============================================================
  // TIME-BASED INTEGRATION TESTS (3 cases)
  // ============================================================
  describe('Time-Based Integration', () => {
    test('morning context affects message selection', () => {
      const morningTime = new Date();
      morningTime.setHours(9, 0, 0, 0);

      const tasks = [{
        id: '1',
        title: 'Morning task',
        dueDate: new Date(morningTime.getTime() + 8 * 60 * 60 * 1000).toISOString(),
        completed: false
      }];

      const message = messageEngine.generateMessage({}, tasks, morningTime);

      // Should get time context message for morning
      expect(message.priority).toBe(3); // TIME_CONTEXT
    });

    test('evening context with remaining tasks returns time context message', () => {
      const eveningTime = new Date();
      eveningTime.setHours(19, 0, 0, 0);

      const tasks = [
        { id: '1', title: 'Task 1', completed: false, archived: false },
        { id: '2', title: 'Task 2', completed: false, archived: false },
        { id: '3', title: 'Task 3', completed: false, archived: false }
      ];

      const message = messageEngine.getTimeContextMessage(eveningTime, tasks);

      // Evening message should be TIME_CONTEXT priority
      expect(message.priority).toBe(3); // TIME_CONTEXT = 3
      // Message should contain task reference or count (templates vary)
      expect(message.text.length).toBeGreaterThan(0);
    });

    test('urgency escalates as deadline approaches', () => {
      const now = new Date();

      // Task due in 3 hours
      const taskIn3Hours = [{
        id: '1',
        dueDate: new Date(now.getTime() + 3 * 60 * 60 * 1000).toISOString(),
        completed: false
      }];

      // Task due in 1 hour
      const taskIn1Hour = [{
        id: '1',
        dueDate: new Date(now.getTime() + 1 * 60 * 60 * 1000).toISOString(),
        completed: false
      }];

      const atmosphere3h = atmosphereController.calculateState(taskIn3Hours);
      const atmosphere1h = atmosphereController.calculateState(taskIn1Hour);

      // 1 hour task should have higher urgency
      expect(atmosphere1h.urgencyScore).toBeGreaterThan(atmosphere3h.urgencyScore);
    });
  });
});
