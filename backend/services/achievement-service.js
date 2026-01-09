/**
 * Achievement Detection Service for Cosmic Ocean
 *
 * Detects and tracks user achievements based on task completion patterns.
 *
 * Achievement Types:
 * - STREAK: Consecutive days with completions (3, 7, 14, 30, 100 days)
 * - SPEED_DEMON: 5+ tasks completed in 1 hour
 * - EARLY_BIRD: Most tasks completed in morning (5-9 AM)
 * - NIGHT_OWL: Most tasks completed at night (9 PM - 2 AM)
 * - ZERO_INBOX: All tasks completed (none pending)
 * - MILESTONE: Total tasks completed (50, 100, 500, 1000, 5000)
 * - CATEGORY_MASTER: 50+ tasks in a single category
 * - FIRST_TASK: Complete your first task
 *
 * Epic: 10 Phase 2 - Achievement System
 * Created: 2026-01-09
 */

const { StatsAggregator } = require('./stats-aggregator');

// Achievement definitions
const ACHIEVEMENT_DEFINITIONS = {
  // Streak achievements
  STREAK_3: {
    id: 'streak_3',
    type: 'streak',
    name: '3-Day Streak',
    description: 'Complete tasks 3 days in a row',
    icon: 'flame',
    color: '#FF6B35',
    threshold: 3,
    points: 10
  },
  STREAK_7: {
    id: 'streak_7',
    type: 'streak',
    name: 'Week Warrior',
    description: 'Complete tasks 7 days in a row',
    icon: 'flame',
    color: '#FF8C42',
    threshold: 7,
    points: 25
  },
  STREAK_14: {
    id: 'streak_14',
    type: 'streak',
    name: 'Fortnight Force',
    description: 'Complete tasks 14 days in a row',
    icon: 'fire',
    color: '#FF9F1C',
    threshold: 14,
    points: 50
  },
  STREAK_30: {
    id: 'streak_30',
    type: 'streak',
    name: 'Monthly Master',
    description: 'Complete tasks 30 days in a row',
    icon: 'fire',
    color: '#FFB627',
    threshold: 30,
    points: 100
  },
  STREAK_100: {
    id: 'streak_100',
    type: 'streak',
    name: 'Centurion',
    description: 'Complete tasks 100 days in a row',
    icon: 'crown',
    color: '#FFD700',
    threshold: 100,
    points: 500
  },

  // Speed achievement
  SPEED_DEMON: {
    id: 'speed_demon',
    type: 'speed',
    name: 'Speed Demon',
    description: 'Complete 5 tasks in 1 hour',
    icon: 'lightning',
    color: '#00D4FF',
    threshold: 5,
    points: 30
  },

  // Time of day achievements
  EARLY_BIRD: {
    id: 'early_bird',
    type: 'time_preference',
    name: 'Early Bird',
    description: 'Complete 20+ tasks in the morning (5-9 AM)',
    icon: 'sun',
    color: '#FFBE0B',
    threshold: 20,
    points: 35
  },
  NIGHT_OWL: {
    id: 'night_owl',
    type: 'time_preference',
    name: 'Night Owl',
    description: 'Complete 20+ tasks at night (9 PM - 2 AM)',
    icon: 'moon',
    color: '#8338EC',
    threshold: 20,
    points: 35
  },

  // Zero inbox
  ZERO_INBOX: {
    id: 'zero_inbox',
    type: 'zero_inbox',
    name: 'Zero Inbox',
    description: 'Clear all your tasks',
    icon: 'check-circle',
    color: '#06D6A0',
    threshold: 1,
    points: 15
  },

  // Milestone achievements
  MILESTONE_50: {
    id: 'milestone_50',
    type: 'milestone',
    name: 'Half Century',
    description: 'Complete 50 tasks total',
    icon: 'trophy',
    color: '#A8DADC',
    threshold: 50,
    points: 25
  },
  MILESTONE_100: {
    id: 'milestone_100',
    type: 'milestone',
    name: 'Century Club',
    description: 'Complete 100 tasks total',
    icon: 'trophy',
    color: '#457B9D',
    threshold: 100,
    points: 50
  },
  MILESTONE_500: {
    id: 'milestone_500',
    type: 'milestone',
    name: 'Task Master',
    description: 'Complete 500 tasks total',
    icon: 'medal',
    color: '#1D3557',
    threshold: 500,
    points: 150
  },
  MILESTONE_1000: {
    id: 'milestone_1000',
    type: 'milestone',
    name: 'Legendary',
    description: 'Complete 1000 tasks total',
    icon: 'star',
    color: '#E63946',
    threshold: 1000,
    points: 300
  },
  MILESTONE_5000: {
    id: 'milestone_5000',
    type: 'milestone',
    name: 'Cosmic Legend',
    description: 'Complete 5000 tasks total',
    icon: 'crown',
    color: '#FFD700',
    threshold: 5000,
    points: 1000
  },

  // Category mastery
  CATEGORY_MASTER: {
    id: 'category_master',
    type: 'category',
    name: 'Category Master',
    description: 'Complete 50+ tasks in one category',
    icon: 'star',
    color: '#118AB2',
    threshold: 50,
    points: 40
  },

  // First task
  FIRST_TASK: {
    id: 'first_task',
    type: 'milestone',
    name: 'First Step',
    description: 'Complete your first task',
    icon: 'rocket',
    color: '#06D6A0',
    threshold: 1,
    points: 5
  }
};

/**
 * AchievementService class
 * Detects and manages user achievements
 */
class AchievementService {
  constructor(options = {}) {
    this.options = {
      cacheEnabled: true,
      cacheTTLMinutes: 5,
      ...options
    };

    this.statsAggregator = new StatsAggregator();
    this.cache = new Map();
  }

  /**
   * Get all achievement definitions
   * @returns {Object} Achievement definitions
   */
  getDefinitions() {
    return ACHIEVEMENT_DEFINITIONS;
  }

  /**
   * Detect all earned achievements for a user
   * @param {string} userId - User ID
   * @param {Array} tasks - All user tasks
   * @param {Array} existingAchievements - Already earned achievements
   * @returns {Object} Detection results
   */
  async detectAchievements(userId, tasks, existingAchievements = []) {
    // Check cache first
    if (this.options.cacheEnabled) {
      const cached = this.getFromCache(userId);
      if (cached) return cached;
    }

    const completedTasks = (tasks || []).filter(t => t && t.completed);
    const pendingTasks = (tasks || []).filter(t => t && !t.completed);
    const stats = this.statsAggregator.computeStats(tasks);

    // Track which achievements are already earned
    const earnedIds = new Set((existingAchievements || []).map(a => a.achievement_id));

    const detectedAchievements = [];
    const newAchievements = [];

    // Detect streak achievements
    const streakAchievements = this.detectStreakAchievements(stats.streakDays, earnedIds);
    detectedAchievements.push(...streakAchievements.all);
    newAchievements.push(...streakAchievements.new);

    // Detect speed demon
    const speedAchievements = this.detectSpeedDemon(completedTasks, earnedIds);
    detectedAchievements.push(...speedAchievements.all);
    newAchievements.push(...speedAchievements.new);

    // Detect time preference achievements
    const timeAchievements = this.detectTimePreferences(completedTasks, earnedIds);
    detectedAchievements.push(...timeAchievements.all);
    newAchievements.push(...timeAchievements.new);

    // Detect zero inbox
    const zeroInboxAchievements = this.detectZeroInbox(pendingTasks, completedTasks, earnedIds);
    detectedAchievements.push(...zeroInboxAchievements.all);
    newAchievements.push(...zeroInboxAchievements.new);

    // Detect milestones
    const milestoneAchievements = this.detectMilestones(stats.totalCompleted, earnedIds);
    detectedAchievements.push(...milestoneAchievements.all);
    newAchievements.push(...milestoneAchievements.new);

    // Detect category master
    const categoryAchievements = this.detectCategoryMaster(completedTasks, earnedIds);
    detectedAchievements.push(...categoryAchievements.all);
    newAchievements.push(...categoryAchievements.new);

    // Calculate total points
    const totalPoints = detectedAchievements.reduce((sum, a) => sum + (a.points || 0), 0);

    // Find next achievements in progress
    const inProgress = this.getInProgressAchievements(stats, completedTasks, pendingTasks, earnedIds);

    const result = {
      earned: detectedAchievements,
      new: newAchievements,
      inProgress,
      totalPoints,
      stats: {
        streak: stats.streakDays,
        longestStreak: stats.longestStreak,
        totalCompleted: stats.totalCompleted,
        completedToday: stats.completedToday
      }
    };

    // Cache the result
    if (this.options.cacheEnabled) {
      this.setCache(userId, result);
    }

    return result;
  }

  /**
   * Detect streak achievements
   */
  detectStreakAchievements(streakDays, earnedIds) {
    const all = [];
    const newAchievements = [];

    const streakThresholds = [
      { id: 'streak_3', threshold: 3 },
      { id: 'streak_7', threshold: 7 },
      { id: 'streak_14', threshold: 14 },
      { id: 'streak_30', threshold: 30 },
      { id: 'streak_100', threshold: 100 }
    ];

    for (const { id, threshold } of streakThresholds) {
      if (streakDays >= threshold) {
        const def = ACHIEVEMENT_DEFINITIONS[id.toUpperCase()];
        const achievement = { ...def, earnedAt: new Date().toISOString() };
        all.push(achievement);

        if (!earnedIds.has(id)) {
          newAchievements.push(achievement);
        }
      }
    }

    return { all, new: newAchievements };
  }

  /**
   * Detect speed demon achievement (5 tasks in 1 hour)
   */
  detectSpeedDemon(completedTasks, earnedIds) {
    const all = [];
    const newAchievements = [];

    // Group completed tasks by hour
    const tasksByHour = {};
    for (const task of completedTasks) {
      const completedAt = task.completedAt || task.completed_at || task.updatedAt || task.updated_at;
      if (!completedAt) continue;

      const date = new Date(completedAt);
      const hourKey = `${date.toISOString().split('T')[0]}-${date.getHours()}`;

      if (!tasksByHour[hourKey]) {
        tasksByHour[hourKey] = [];
      }
      tasksByHour[hourKey].push(task);
    }

    // Check if any hour has 5+ completions
    const hasSpeedDemon = Object.values(tasksByHour).some(tasks => tasks.length >= 5);

    if (hasSpeedDemon) {
      const def = ACHIEVEMENT_DEFINITIONS.SPEED_DEMON;
      const achievement = { ...def, earnedAt: new Date().toISOString() };
      all.push(achievement);

      if (!earnedIds.has('speed_demon')) {
        newAchievements.push(achievement);
      }
    }

    return { all, new: newAchievements };
  }

  /**
   * Detect time preference achievements (early bird / night owl)
   */
  detectTimePreferences(completedTasks, earnedIds) {
    const all = [];
    const newAchievements = [];

    let morningCount = 0;
    let nightCount = 0;

    for (const task of completedTasks) {
      const completedAt = task.completedAt || task.completed_at || task.updatedAt || task.updated_at;
      if (!completedAt) continue;

      const date = new Date(completedAt);
      const hour = date.getHours();

      // Morning: 5-9 AM
      if (hour >= 5 && hour < 9) {
        morningCount++;
      }
      // Night: 9 PM - 2 AM (21-23, 0-2)
      if (hour >= 21 || hour < 2) {
        nightCount++;
      }
    }

    // Early bird (20+ morning tasks)
    if (morningCount >= 20) {
      const def = ACHIEVEMENT_DEFINITIONS.EARLY_BIRD;
      const achievement = { ...def, earnedAt: new Date().toISOString() };
      all.push(achievement);

      if (!earnedIds.has('early_bird')) {
        newAchievements.push(achievement);
      }
    }

    // Night owl (20+ night tasks)
    if (nightCount >= 20) {
      const def = ACHIEVEMENT_DEFINITIONS.NIGHT_OWL;
      const achievement = { ...def, earnedAt: new Date().toISOString() };
      all.push(achievement);

      if (!earnedIds.has('night_owl')) {
        newAchievements.push(achievement);
      }
    }

    return { all, new: newAchievements };
  }

  /**
   * Detect zero inbox achievement
   */
  detectZeroInbox(pendingTasks, completedTasks, earnedIds) {
    const all = [];
    const newAchievements = [];

    // Zero inbox: no pending tasks AND at least 1 completed task
    if (pendingTasks.length === 0 && completedTasks.length > 0) {
      const def = ACHIEVEMENT_DEFINITIONS.ZERO_INBOX;
      const achievement = { ...def, earnedAt: new Date().toISOString() };
      all.push(achievement);

      if (!earnedIds.has('zero_inbox')) {
        newAchievements.push(achievement);
      }
    }

    return { all, new: newAchievements };
  }

  /**
   * Detect milestone achievements
   */
  detectMilestones(totalCompleted, earnedIds) {
    const all = [];
    const newAchievements = [];

    const milestones = [
      { id: 'first_task', threshold: 1, key: 'FIRST_TASK' },
      { id: 'milestone_50', threshold: 50, key: 'MILESTONE_50' },
      { id: 'milestone_100', threshold: 100, key: 'MILESTONE_100' },
      { id: 'milestone_500', threshold: 500, key: 'MILESTONE_500' },
      { id: 'milestone_1000', threshold: 1000, key: 'MILESTONE_1000' },
      { id: 'milestone_5000', threshold: 5000, key: 'MILESTONE_5000' }
    ];

    for (const { id, threshold, key } of milestones) {
      if (totalCompleted >= threshold) {
        const def = ACHIEVEMENT_DEFINITIONS[key];
        const achievement = { ...def, earnedAt: new Date().toISOString() };
        all.push(achievement);

        if (!earnedIds.has(id)) {
          newAchievements.push(achievement);
        }
      }
    }

    return { all, new: newAchievements };
  }

  /**
   * Detect category master achievement
   */
  detectCategoryMaster(completedTasks, earnedIds) {
    const all = [];
    const newAchievements = [];

    // Count tasks by category
    const categoryCounts = {};
    for (const task of completedTasks) {
      const category = task.category || 'general';
      categoryCounts[category] = (categoryCounts[category] || 0) + 1;
    }

    // Check if any category has 50+ completions
    const masterCategories = Object.entries(categoryCounts)
      .filter(([_, count]) => count >= 50)
      .map(([category, count]) => ({ category, count }));

    for (const { category, count } of masterCategories) {
      const def = ACHIEVEMENT_DEFINITIONS.CATEGORY_MASTER;
      const achievement = {
        ...def,
        id: `category_master_${category}`,
        name: `${category.charAt(0).toUpperCase() + category.slice(1)} Master`,
        description: `Complete 50+ ${category} tasks`,
        earnedAt: new Date().toISOString(),
        metadata: { category, count }
      };
      all.push(achievement);

      if (!earnedIds.has(`category_master_${category}`)) {
        newAchievements.push(achievement);
      }
    }

    return { all, new: newAchievements };
  }

  /**
   * Get achievements currently in progress
   */
  getInProgressAchievements(stats, completedTasks, pendingTasks, earnedIds) {
    const inProgress = [];

    // Next streak milestone
    const nextStreakThresholds = [3, 7, 14, 30, 100];
    for (const threshold of nextStreakThresholds) {
      const id = `streak_${threshold}`;
      if (!earnedIds.has(id) && stats.streakDays < threshold) {
        inProgress.push({
          id,
          name: ACHIEVEMENT_DEFINITIONS[id.toUpperCase()]?.name || `${threshold}-Day Streak`,
          current: stats.streakDays,
          target: threshold,
          progress: Math.min((stats.streakDays / threshold) * 100, 99),
          type: 'streak'
        });
        break; // Only show next streak milestone
      }
    }

    // Next milestone
    const nextMilestones = [
      { threshold: 1, id: 'first_task' },
      { threshold: 50, id: 'milestone_50' },
      { threshold: 100, id: 'milestone_100' },
      { threshold: 500, id: 'milestone_500' },
      { threshold: 1000, id: 'milestone_1000' },
      { threshold: 5000, id: 'milestone_5000' }
    ];
    for (const { threshold, id } of nextMilestones) {
      if (!earnedIds.has(id) && stats.totalCompleted < threshold) {
        const key = id.toUpperCase();
        inProgress.push({
          id,
          name: ACHIEVEMENT_DEFINITIONS[key]?.name || `${threshold} Tasks`,
          current: stats.totalCompleted,
          target: threshold,
          progress: Math.min((stats.totalCompleted / threshold) * 100, 99),
          type: 'milestone'
        });
        break; // Only show next milestone
      }
    }

    // Speed demon progress (tasks in last hour)
    if (!earnedIds.has('speed_demon')) {
      const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
      const tasksInLastHour = completedTasks.filter(t => {
        const completedAt = t.completedAt || t.completed_at || t.updatedAt || t.updated_at;
        if (!completedAt) return false;
        return new Date(completedAt) >= oneHourAgo;
      }).length;

      if (tasksInLastHour > 0) {
        inProgress.push({
          id: 'speed_demon',
          name: 'Speed Demon',
          current: tasksInLastHour,
          target: 5,
          progress: Math.min((tasksInLastHour / 5) * 100, 99),
          type: 'speed'
        });
      }
    }

    // Zero inbox progress
    if (!earnedIds.has('zero_inbox') && completedTasks.length > 0) {
      const total = pendingTasks.length + completedTasks.length;
      const completed = completedTasks.length;
      if (pendingTasks.length <= 5) { // Only show if close
        inProgress.push({
          id: 'zero_inbox',
          name: 'Zero Inbox',
          current: completed,
          target: total,
          progress: Math.min((completed / total) * 100, 99),
          type: 'zero_inbox',
          remaining: pendingTasks.length
        });
      }
    }

    return inProgress;
  }

  /**
   * Get the top N achievements to display on wallpaper
   * @param {Array} achievements - Earned achievements
   * @param {number} limit - Maximum achievements to return (default 3)
   * @returns {Array} Top achievements for wallpaper display
   */
  getWallpaperAchievements(achievements, limit = 3) {
    if (!achievements || achievements.length === 0) return [];

    // Sort by points (highest first), then by most recent
    const sorted = [...achievements].sort((a, b) => {
      if (b.points !== a.points) return b.points - a.points;
      return new Date(b.earnedAt) - new Date(a.earnedAt);
    });

    return sorted.slice(0, limit);
  }

  /**
   * Get cache
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

    return cached.data;
  }

  /**
   * Set cache
   */
  setCache(userId, data) {
    this.cache.set(userId, {
      data,
      timestamp: Date.now()
    });
  }

  /**
   * Clear cache for a user
   */
  clearCache(userId) {
    if (userId) {
      this.cache.delete(userId);
    } else {
      this.cache.clear();
    }
  }
}

module.exports = {
  AchievementService,
  ACHIEVEMENT_DEFINITIONS
};
