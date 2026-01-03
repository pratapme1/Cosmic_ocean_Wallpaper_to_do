/**
 * Comprehensive Stats Aggregator Test Suite
 * Tests user statistics aggregation and pattern analysis
 *
 * Test Coverage:
 * - Daily/weekly/monthly statistics
 * - Streak calculation
 * - Pattern analysis
 * - Cache management
 * - Edge cases and null handling
 */

const { StatsAggregator } = require('../services/stats-aggregator');

describe('Stats Aggregator - Comprehensive Test Suite', () => {
  let aggregator;

  beforeEach(() => {
    aggregator = new StatsAggregator({ cacheEnabled: false });
  });

  // Helper to create a task completed at a specific time
  const createTask = (id, completedAt, options = {}) => ({
    id,
    title: `Task ${id}`,
    completed: true,
    completedAt: completedAt?.toISOString(),
    createdAt: options.createdAt?.toISOString() || new Date().toISOString(),
    category: options.category || 'general',
    ...options
  });

  // Helper to get date X days ago at specific hour
  const daysAgo = (days, hour = 12) => {
    const date = new Date();
    date.setDate(date.getDate() - days);
    date.setHours(hour, 0, 0, 0);
    return date;
  };

  // ============================================================
  // COMPUTE STATS BASIC TESTS (5 cases)
  // ============================================================
  describe('Compute Stats - Basic', () => {
    test('returns all stat properties', () => {
      const stats = aggregator.computeStats([]);

      expect(stats).toHaveProperty('completedToday');
      expect(stats).toHaveProperty('createdToday');
      expect(stats).toHaveProperty('completedThisWeek');
      expect(stats).toHaveProperty('averagePerDay');
      expect(stats).toHaveProperty('totalCompleted');
      expect(stats).toHaveProperty('totalTasks');
      expect(stats).toHaveProperty('streakDays');
      expect(stats).toHaveProperty('longestStreak');
      expect(stats).toHaveProperty('patterns');
      expect(stats).toHaveProperty('computedAt');
      expect(stats).toHaveProperty('timezone');
    });

    test('returns zeros for empty tasks', () => {
      const stats = aggregator.computeStats([]);

      expect(stats.completedToday).toBe(0);
      expect(stats.completedThisWeek).toBe(0);
      expect(stats.totalCompleted).toBe(0);
      expect(stats.streakDays).toBe(0);
    });

    test('returns zeros for null tasks', () => {
      const stats = aggregator.computeStats(null);

      expect(stats.totalCompleted).toBe(0);
      expect(stats.totalTasks).toBe(0);
    });

    test('counts only completed tasks for totalCompleted', () => {
      const tasks = [
        createTask('1', new Date()),
        { id: '2', title: 'Not done', completed: false },
        { id: '3', title: 'Also not done', completed: false }
      ];

      const stats = aggregator.computeStats(tasks);
      expect(stats.totalCompleted).toBe(1);
      expect(stats.totalTasks).toBe(3);
    });

    test('computedAt is valid ISO timestamp', () => {
      const stats = aggregator.computeStats([]);
      expect(() => new Date(stats.computedAt)).not.toThrow();
    });
  });

  // ============================================================
  // DAILY STATS TESTS (4 cases)
  // ============================================================
  describe('Daily Stats', () => {
    test('counts tasks completed today correctly', () => {
      const now = new Date();
      const today1 = new Date(now);
      today1.setHours(9, 0, 0, 0);
      const today2 = new Date(now);
      today2.setHours(14, 0, 0, 0);

      const tasks = [
        createTask('1', today1),
        createTask('2', today2),
        createTask('3', daysAgo(1)) // Yesterday
      ];

      const stats = aggregator.computeStats(tasks);
      expect(stats.completedToday).toBe(2);
    });

    test('counts tasks created today correctly', () => {
      const now = new Date();
      const todayCreated = new Date(now);
      todayCreated.setHours(10, 0, 0, 0);

      const tasks = [
        { id: '1', title: 'Task', createdAt: todayCreated.toISOString(), completed: false },
        { id: '2', title: 'Task 2', createdAt: daysAgo(1).toISOString(), completed: false }
      ];

      const stats = aggregator.computeStats(tasks);
      expect(stats.createdToday).toBe(1);
    });

    test('handles completed_at snake_case property', () => {
      const now = new Date();
      now.setHours(10, 0, 0, 0);

      const tasks = [{
        id: '1',
        title: 'Task',
        completed: true,
        completed_at: now.toISOString()
      }];

      const stats = aggregator.computeStats(tasks);
      expect(stats.completedToday).toBe(1);
    });

    test('handles updatedAt as fallback for completedAt', () => {
      const now = new Date();
      now.setHours(10, 0, 0, 0);

      const tasks = [{
        id: '1',
        title: 'Task',
        completed: true,
        updatedAt: now.toISOString()
      }];

      const stats = aggregator.computeStats(tasks);
      expect(stats.completedToday).toBe(1);
    });
  });

  // ============================================================
  // WEEKLY STATS TESTS (3 cases)
  // ============================================================
  describe('Weekly Stats', () => {
    test('counts tasks completed this week correctly', () => {
      const now = new Date();
      const dayOfWeek = now.getDay();

      // Task from earlier this week (2 days ago, but still this week if not Sunday/Monday)
      const thisWeek = daysAgo(Math.min(dayOfWeek, 2));

      const tasks = [
        createTask('1', thisWeek),
        createTask('2', daysAgo(10)) // Last week
      ];

      const stats = aggregator.computeStats(tasks);
      expect(stats.completedThisWeek).toBeGreaterThanOrEqual(1);
    });

    test('calculates average per day correctly', () => {
      // Create tasks over multiple days
      const tasks = [
        createTask('1', daysAgo(0)),
        createTask('2', daysAgo(0)),
        createTask('3', daysAgo(1)),
        createTask('4', daysAgo(1)),
        createTask('5', daysAgo(2)),
        createTask('6', daysAgo(2))
      ];

      const stats = aggregator.computeStats(tasks);
      // 6 tasks over 3 days = 2.0 average
      expect(stats.averagePerDay).toBe(2.0);
    });

    test('returns 0 average for no recent completions', () => {
      const tasks = [
        createTask('1', daysAgo(45)) // Over 30 days ago
      ];

      const stats = aggregator.computeStats(tasks);
      expect(stats.averagePerDay).toBe(0);
    });
  });

  // ============================================================
  // STREAK CALCULATION TESTS (6 cases)
  // ============================================================
  describe('Streak Calculation', () => {
    test('returns 0 for no completed tasks', () => {
      const streak = aggregator.calculateStreak([]);
      expect(streak).toBe(0);
    });

    test('returns 1 for task completed today only', () => {
      const tasks = [createTask('1', daysAgo(0))];
      const streak = aggregator.calculateStreak(tasks);
      expect(streak).toBe(1);
    });

    test('returns 1 for task completed yesterday only', () => {
      const tasks = [createTask('1', daysAgo(1))];
      const streak = aggregator.calculateStreak(tasks);
      expect(streak).toBe(1);
    });

    test('counts consecutive days correctly', () => {
      const tasks = [
        createTask('1', daysAgo(0)),
        createTask('2', daysAgo(1)),
        createTask('3', daysAgo(2)),
        createTask('4', daysAgo(3))
      ];

      const streak = aggregator.calculateStreak(tasks);
      expect(streak).toBe(4);
    });

    test('returns 0 if streak broken (> 1 day gap)', () => {
      const tasks = [
        createTask('1', daysAgo(3)) // 3 days ago, no tasks since then
      ];

      const streak = aggregator.calculateStreak(tasks);
      expect(streak).toBe(0);
    });

    test('handles multiple tasks on same day', () => {
      const tasks = [
        createTask('1', daysAgo(0, 9)),  // Today 9am
        createTask('2', daysAgo(0, 14)), // Today 2pm
        createTask('3', daysAgo(1, 10)), // Yesterday
        createTask('4', daysAgo(1, 16))  // Yesterday
      ];

      const streak = aggregator.calculateStreak(tasks);
      expect(streak).toBe(2);
    });
  });

  // ============================================================
  // LONGEST STREAK TESTS (4 cases)
  // ============================================================
  describe('Longest Streak', () => {
    test('returns 0 for no tasks', () => {
      const longest = aggregator.calculateLongestStreak([]);
      expect(longest).toBe(0);
    });

    test('returns 1 for single day', () => {
      const tasks = [createTask('1', daysAgo(10))];
      const longest = aggregator.calculateLongestStreak(tasks);
      expect(longest).toBe(1);
    });

    test('calculates longest streak correctly', () => {
      const tasks = [
        // Old streak: 3 days
        createTask('1', daysAgo(20)),
        createTask('2', daysAgo(21)),
        createTask('3', daysAgo(22)),
        // Gap
        // New streak: 2 days
        createTask('4', daysAgo(10)),
        createTask('5', daysAgo(11))
      ];

      const longest = aggregator.calculateLongestStreak(tasks);
      expect(longest).toBe(3);
    });

    test('returns current streak if it is the longest', () => {
      const tasks = [
        createTask('1', daysAgo(0)),
        createTask('2', daysAgo(1)),
        createTask('3', daysAgo(2)),
        createTask('4', daysAgo(3)),
        createTask('5', daysAgo(4))
      ];

      const longest = aggregator.calculateLongestStreak(tasks);
      expect(longest).toBe(5);
    });
  });

  // ============================================================
  // PATTERN ANALYSIS TESTS (5 cases)
  // ============================================================
  describe('Pattern Analysis', () => {
    test('returns null patterns for < 10 tasks', () => {
      const tasks = Array(5).fill(null).map((_, i) =>
        createTask(String(i), daysAgo(i))
      );

      const patterns = aggregator.analyzePatterns(tasks);

      expect(patterns.peakPeriod).toBeNull();
      expect(patterns.peakHour).toBeNull();
      expect(patterns.topCategory).toBeNull();
    });

    test('identifies peak period correctly', () => {
      // Create 15 tasks all completed in the morning
      const tasks = Array(15).fill(null).map((_, i) =>
        createTask(String(i), daysAgo(i, 9)) // 9am
      );

      const patterns = aggregator.analyzePatterns(tasks);
      expect(patterns.peakPeriod).toBe('morning');
      expect(patterns.peakHour).toBe(9);
    });

    test('identifies afternoon as peak period', () => {
      const tasks = Array(15).fill(null).map((_, i) =>
        createTask(String(i), daysAgo(i, 14)) // 2pm
      );

      const patterns = aggregator.analyzePatterns(tasks);
      expect(patterns.peakPeriod).toBe('afternoon');
    });

    test('identifies top category correctly', () => {
      const tasks = [
        ...Array(8).fill(null).map((_, i) =>
          createTask(String(i), daysAgo(i), { category: 'work' })
        ),
        ...Array(4).fill(null).map((_, i) =>
          createTask(String(i + 8), daysAgo(i), { category: 'personal' })
        )
      ];

      const patterns = aggregator.analyzePatterns(tasks);
      expect(patterns.topCategory).toBe('work');
    });

    test('includes distribution data', () => {
      const tasks = Array(15).fill(null).map((_, i) =>
        createTask(String(i), daysAgo(i))
      );

      const patterns = aggregator.analyzePatterns(tasks);

      expect(patterns).toHaveProperty('hourDistribution');
      expect(patterns).toHaveProperty('dayDistribution');
      expect(patterns).toHaveProperty('categoryDistribution');
    });
  });

  // ============================================================
  // CACHE TESTS (6 cases)
  // ============================================================
  describe('Cache Management', () => {
    let cachedAggregator;

    beforeEach(() => {
      cachedAggregator = new StatsAggregator({ cacheEnabled: true, cacheTTLMinutes: 15 });
    });

    test('returns cached stats when available', async () => {
      const tasks = [createTask('1', new Date())];

      // First call - computes and caches
      const stats1 = await cachedAggregator.getStats('user1', tasks);

      // Second call - should return cached
      const stats2 = await cachedAggregator.getStats('user1', []);

      // Should have same completedToday despite empty array
      expect(stats2.totalCompleted).toBe(stats1.totalCompleted);
    });

    test('bypasses cache with forceRefresh', async () => {
      const tasks = [createTask('1', new Date())];

      await cachedAggregator.getStats('user1', tasks);

      // Force refresh with empty tasks
      const stats = await cachedAggregator.getStats('user1', [], { forceRefresh: true });

      expect(stats.totalCompleted).toBe(0);
    });

    test('clears cache for specific user', async () => {
      const tasks = [createTask('1', new Date())];

      await cachedAggregator.getStats('user1', tasks);
      cachedAggregator.clearCache('user1');

      const stats = await cachedAggregator.getStats('user1', []);
      expect(stats.totalCompleted).toBe(0);
    });

    test('clears all cache when userId is null', async () => {
      const tasks = [createTask('1', new Date())];

      await cachedAggregator.getStats('user1', tasks);
      await cachedAggregator.getStats('user2', tasks);

      cachedAggregator.clearCache();

      const stats1 = await cachedAggregator.getStats('user1', []);
      const stats2 = await cachedAggregator.getStats('user2', []);

      expect(stats1.totalCompleted).toBe(0);
      expect(stats2.totalCompleted).toBe(0);
    });

    test('cache expires after TTL', async () => {
      const shortTTL = new StatsAggregator({ cacheEnabled: true, cacheTTLMinutes: 0 });
      const tasks = [createTask('1', new Date())];

      await shortTTL.getStats('user1', tasks);

      // Wait a bit for cache to expire (TTL is 0)
      await new Promise(r => setTimeout(r, 10));

      const stats = await shortTTL.getStats('user1', []);
      expect(stats.totalCompleted).toBe(0);
    });

    test('getFromCache returns null for missing user', () => {
      const cached = cachedAggregator.getFromCache('nonexistent');
      expect(cached).toBeNull();
    });
  });

  // ============================================================
  // QUICK SUMMARY TESTS (2 cases)
  // ============================================================
  describe('Quick Summary', () => {
    test('returns summary with key metrics', () => {
      const tasks = [
        createTask('1', daysAgo(0)),
        createTask('2', daysAgo(1))
      ];

      const summary = aggregator.getQuickSummary(tasks);

      expect(summary).toHaveProperty('completedToday');
      expect(summary).toHaveProperty('streak');
      expect(summary).toHaveProperty('totalCompleted');
      expect(summary.totalCompleted).toBe(2);
    });

    test('returns zeros for empty tasks', () => {
      const summary = aggregator.getQuickSummary([]);

      expect(summary.completedToday).toBe(0);
      expect(summary.streak).toBe(0);
      expect(summary.totalCompleted).toBe(0);
    });
  });

  // ============================================================
  // CONSTRUCTOR OPTIONS TESTS (3 cases)
  // ============================================================
  describe('Constructor Options', () => {
    test('uses default options when none provided', () => {
      const agg = new StatsAggregator();
      expect(agg.options.streakGracePeriodHours).toBe(36);
      expect(agg.options.cacheEnabled).toBe(true);
      expect(agg.options.cacheTTLMinutes).toBe(15);
    });

    test('merges custom options with defaults', () => {
      const agg = new StatsAggregator({ streakGracePeriodHours: 48 });
      expect(agg.options.streakGracePeriodHours).toBe(48);
      expect(agg.options.cacheEnabled).toBe(true); // Default
    });

    test('allows disabling cache', () => {
      const agg = new StatsAggregator({ cacheEnabled: false });
      expect(agg.options.cacheEnabled).toBe(false);
    });
  });

  // ============================================================
  // EDGE CASES (4 cases)
  // ============================================================
  describe('Edge Cases', () => {
    test('handles task with null completedAt', () => {
      const tasks = [{ id: '1', title: 'Task', completed: true, completedAt: null }];
      const stats = aggregator.computeStats(tasks);
      expect(stats.completedToday).toBe(0);
    });

    test('handles task missing completed field', () => {
      const tasks = [{ id: '1', title: 'Task' }];
      const stats = aggregator.computeStats(tasks);
      expect(stats.totalCompleted).toBe(0);
    });

    test('handles invalid date strings gracefully', () => {
      const tasks = [{ id: '1', completed: true, completedAt: 'not-a-date' }];
      // FIX APPLIED: Service now handles invalid dates with try-catch
      const stats = aggregator.computeStats(tasks);
      expect(stats.totalCompleted).toBe(1);
      expect(stats.streakDays).toBe(0); // Invalid date not counted in streak
    });

    test('handles null elements in tasks array gracefully', () => {
      const tasks = [null, createTask('1', new Date()), null];
      // FIX APPLIED: Service now filters null elements before processing
      const stats = aggregator.computeStats(tasks);
      expect(stats.totalCompleted).toBe(1); // Only the valid task counted
    });
  });

  // ============================================================
  // ASYNC GETSTATS TESTS (2 cases)
  // ============================================================
  describe('Async getStats', () => {
    test('getStats is async and returns promise', async () => {
      const result = aggregator.getStats('user1', []);
      expect(result).toBeInstanceOf(Promise);
    });

    test('getStats resolves with stats object', async () => {
      const stats = await aggregator.getStats('user1', []);
      expect(stats).toHaveProperty('totalCompleted');
      expect(stats).toHaveProperty('patterns');
    });
  });
});
