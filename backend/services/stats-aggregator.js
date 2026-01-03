/**
 * Stats Aggregator for Wallpaper Intelligence
 *
 * Aggregates user statistics for:
 * - Pattern analysis (peak productivity times, preferred categories)
 * - Achievement detection (streaks, milestones)
 * - Message engine context
 *
 * Provides daily, weekly, and all-time statistics.
 */

/**
 * StatsAggregator class
 * Computes and caches user statistics
 */
class StatsAggregator {
  constructor(options = {}) {
    this.options = {
      streakGracePeriodHours: 36,  // Allow 36h gap for streak
      cacheEnabled: true,
      cacheTTLMinutes: 15,
      ...options
    };

    // In-memory cache (per user)
    this.cache = new Map();
  }

  /**
   * Get aggregated stats for a user
   * @param {string} userId - User ID
   * @param {Array} tasks - Array of all user tasks
   * @param {Object} options - Optional overrides
   * @returns {Object} Aggregated statistics
   */
  async getStats(userId, tasks, options = {}) {
    // Check cache first
    if (this.options.cacheEnabled && !options.forceRefresh) {
      const cached = this.getFromCache(userId);
      if (cached) return cached;
    }

    // Compute stats
    const stats = this.computeStats(tasks);

    // Cache the result
    if (this.options.cacheEnabled) {
      this.setCache(userId, stats);
    }

    return stats;
  }

  /**
   * Compute all statistics from tasks
   * @param {Array} tasks - Array of task objects
   * @returns {Object} Computed statistics
   */
  computeStats(tasks) {
    const now = new Date();
    const completedTasks = (tasks || []).filter(t => t && t.completed);

    return {
      // Daily stats
      completedToday: this.countCompletedToday(completedTasks),
      createdToday: this.countCreatedToday(tasks),

      // Weekly stats
      completedThisWeek: this.countCompletedThisWeek(completedTasks),
      averagePerDay: this.calculateAveragePerDay(completedTasks),

      // All-time stats
      totalCompleted: completedTasks.length,
      totalTasks: (tasks || []).length,

      // Streak stats
      streakDays: this.calculateStreak(completedTasks),
      longestStreak: this.calculateLongestStreak(completedTasks),

      // Pattern analysis
      patterns: this.analyzePatterns(completedTasks),

      // Timestamps
      computedAt: now.toISOString(),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
    };
  }

  /**
   * Count tasks completed today
   */
  countCompletedToday(completedTasks) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    return completedTasks.filter(t => {
      const completedAt = t.completedAt || t.completed_at || t.updatedAt || t.updated_at;
      if (!completedAt) return false;
      const date = new Date(completedAt);
      return date >= today;
    }).length;
  }

  /**
   * Count tasks created today
   */
  countCreatedToday(tasks) {
    if (!tasks) return 0;
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    return tasks.filter(t => t).filter(t => { // Filter out null/undefined elements
      const createdAt = t.createdAt || t.created_at;
      if (!createdAt) return false;
      const date = new Date(createdAt);
      return date >= today;
    }).length;
  }

  /**
   * Count tasks completed this week
   */
  countCompletedThisWeek(completedTasks) {
    const now = new Date();
    const startOfWeek = new Date(now);
    startOfWeek.setDate(now.getDate() - now.getDay()); // Sunday
    startOfWeek.setHours(0, 0, 0, 0);

    return completedTasks.filter(t => {
      const completedAt = t.completedAt || t.completed_at || t.updatedAt || t.updated_at;
      if (!completedAt) return false;
      const date = new Date(completedAt);
      return date >= startOfWeek;
    }).length;
  }

  /**
   * Calculate average tasks completed per day (last 30 days)
   */
  calculateAveragePerDay(completedTasks) {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const recentTasks = completedTasks.filter(t => {
      const completedAt = t.completedAt || t.completed_at || t.updatedAt || t.updated_at;
      if (!completedAt) return false;
      const date = new Date(completedAt);
      return date >= thirtyDaysAgo;
    });

    if (recentTasks.length === 0) return 0;

    // Count unique days with completions
    const uniqueDays = new Set();
    for (const task of recentTasks) {
      const completedAt = task.completedAt || task.completed_at || task.updatedAt || task.updated_at;
      const date = new Date(completedAt);
      uniqueDays.add(date.toISOString().split('T')[0]);
    }

    return Math.round((recentTasks.length / Math.max(uniqueDays.size, 1)) * 10) / 10;
  }

  /**
   * Calculate current completion streak (consecutive days)
   */
  calculateStreak(completedTasks) {
    if (completedTasks.length === 0) return 0;

    // Get completion dates (with error handling for invalid dates)
    const completionDates = completedTasks
      .map(t => {
        const completedAt = t.completedAt || t.completed_at || t.updatedAt || t.updated_at;
        if (!completedAt) return null;
        try {
          const date = new Date(completedAt);
          if (isNaN(date.getTime())) return null; // Invalid date
          return date.toISOString().split('T')[0];
        } catch (e) {
          return null; // Invalid date format
        }
      })
      .filter(d => d !== null);

    if (completionDates.length === 0) return 0;

    // Get unique dates sorted descending
    const uniqueDates = [...new Set(completionDates)].sort().reverse();

    // Check if today or yesterday has a completion (streak must be recent)
    const today = new Date().toISOString().split('T')[0];
    const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];

    if (uniqueDates[0] !== today && uniqueDates[0] !== yesterday) {
      return 0; // Streak broken
    }

    // Count consecutive days
    let streak = 1;
    for (let i = 1; i < uniqueDates.length; i++) {
      const current = new Date(uniqueDates[i - 1]);
      const previous = new Date(uniqueDates[i]);
      const dayDiff = (current - previous) / 86400000;

      if (dayDiff === 1) {
        streak++;
      } else if (dayDiff <= this.options.streakGracePeriodHours / 24) {
        // Grace period - still count as streak
        streak++;
      } else {
        break; // Streak ended
      }
    }

    return streak;
  }

  /**
   * Calculate longest ever streak
   */
  calculateLongestStreak(completedTasks) {
    if (completedTasks.length === 0) return 0;

    // Get completion dates (with error handling for invalid dates)
    const completionDates = completedTasks
      .map(t => {
        const completedAt = t.completedAt || t.completed_at || t.updatedAt || t.updated_at;
        if (!completedAt) return null;
        try {
          const date = new Date(completedAt);
          if (isNaN(date.getTime())) return null; // Invalid date
          return date.toISOString().split('T')[0];
        } catch (e) {
          return null; // Invalid date format
        }
      })
      .filter(d => d !== null);

    if (completionDates.length === 0) return 0;

    // Get unique dates sorted ascending
    const uniqueDates = [...new Set(completionDates)].sort();

    let longestStreak = 1;
    let currentStreak = 1;

    for (let i = 1; i < uniqueDates.length; i++) {
      const current = new Date(uniqueDates[i]);
      const previous = new Date(uniqueDates[i - 1]);
      const dayDiff = (current - previous) / 86400000;

      if (dayDiff === 1) {
        currentStreak++;
        longestStreak = Math.max(longestStreak, currentStreak);
      } else {
        currentStreak = 1;
      }
    }

    return longestStreak;
  }

  /**
   * Analyze patterns in task completion
   */
  analyzePatterns(completedTasks) {
    if (completedTasks.length < 10) {
      // Not enough data for patterns
      return {
        peakPeriod: null,
        peakHour: null,
        topCategory: null,
        mostProductiveDay: null
      };
    }

    // Analyze completion times
    const hourCounts = {};
    const dayCounts = {};
    const categoryCounts = {};

    for (const task of completedTasks) {
      const completedAt = task.completedAt || task.completed_at || task.updatedAt || task.updated_at;
      if (!completedAt) continue;

      const date = new Date(completedAt);
      const hour = date.getHours();
      const day = date.getDay();
      const category = task.category || 'general';

      hourCounts[hour] = (hourCounts[hour] || 0) + 1;
      dayCounts[day] = (dayCounts[day] || 0) + 1;
      categoryCounts[category] = (categoryCounts[category] || 0) + 1;
    }

    // Find peak hour
    let peakHour = 9; // Default
    let maxHourCount = 0;
    for (const [hour, count] of Object.entries(hourCounts)) {
      if (count > maxHourCount) {
        maxHourCount = count;
        peakHour = parseInt(hour);
      }
    }

    // Determine peak period
    let peakPeriod;
    if (peakHour >= 5 && peakHour < 12) {
      peakPeriod = 'morning';
    } else if (peakHour >= 12 && peakHour < 17) {
      peakPeriod = 'afternoon';
    } else if (peakHour >= 17 && peakHour < 21) {
      peakPeriod = 'evening';
    } else {
      peakPeriod = 'night';
    }

    // Find most productive day
    const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    let mostProductiveDay = 'Monday'; // Default
    let maxDayCount = 0;
    for (const [day, count] of Object.entries(dayCounts)) {
      if (count > maxDayCount) {
        maxDayCount = count;
        mostProductiveDay = dayNames[parseInt(day)];
      }
    }

    // Find top category
    let topCategory = 'general';
    let maxCategoryCount = 0;
    for (const [category, count] of Object.entries(categoryCounts)) {
      if (count > maxCategoryCount) {
        maxCategoryCount = count;
        topCategory = category;
      }
    }

    return {
      peakPeriod,
      peakHour,
      topCategory,
      mostProductiveDay,
      hourDistribution: hourCounts,
      dayDistribution: dayCounts,
      categoryDistribution: categoryCounts
    };
  }

  /**
   * Get stats from cache
   */
  getFromCache(userId) {
    const cached = this.cache.get(userId);
    if (!cached) return null;

    const now = Date.now();
    const age = now - cached.timestamp;
    const maxAge = this.options.cacheTTLMinutes * 60 * 1000;

    if (age > maxAge) {
      this.cache.delete(userId);
      return null;
    }

    return cached.stats;
  }

  /**
   * Set stats in cache
   */
  setCache(userId, stats) {
    this.cache.set(userId, {
      stats,
      timestamp: Date.now()
    });
  }

  /**
   * Clear cache for a user (call after task changes)
   */
  clearCache(userId) {
    if (userId) {
      this.cache.delete(userId);
    } else {
      this.cache.clear();
    }
  }

  /**
   * Get quick stats summary
   */
  getQuickSummary(tasks) {
    const stats = this.computeStats(tasks);
    return {
      completedToday: stats.completedToday,
      streak: stats.streakDays,
      totalCompleted: stats.totalCompleted
    };
  }
}

module.exports = {
  StatsAggregator
};
