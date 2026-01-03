/**
 * Message Engine for Wallpaper Intelligence
 *
 * Generates intelligent, contextual messages for wallpaper based on:
 * - Task state (overdue, upcoming, completed)
 * - Time of day (morning, afternoon, evening)
 * - User patterns (streaks, productivity peaks)
 * - Achievement milestones
 *
 * Message Priority Hierarchy:
 * 1. CRITICAL (P0): Overdue tasks, urgent deadlines
 * 2. ACHIEVEMENT (P1): Streaks, milestones, completions
 * 3. PATTERN (P2): Behavioral insights, productivity patterns
 * 4. TIME_CONTEXT (P3): Morning motivation, evening wrap-up
 * 5. ENCOURAGEMENT (P4): General motivation, tips
 */

const MESSAGE_TYPES = {
  CRITICAL: 0,
  ACHIEVEMENT: 1,
  PATTERN: 2,
  TIME_CONTEXT: 3,
  ENCOURAGEMENT: 4
};

// Message templates organized by type
// NOTE: Emojis removed for proper Satori rendering (no emoji font support)
const MESSAGE_TEMPLATES = {
  // Critical messages (highest priority)
  critical: {
    overdue: [
      { text: 'OVERDUE: {task} was due {timeAgo}', icon: '!' },
      { text: 'Overdue: {task}', icon: '!' },
      { text: '{task} needs attention', icon: '!' }
    ],
    urgent: [
      { text: 'URGENT: {task} is due in {timeLeft}', icon: '!' },
      { text: '{timeLeft} until {task}', icon: '!' },
      { text: '{task} - {timeLeft} remaining', icon: '!' }
    ]
  },

  // Achievement messages
  achievement: {
    streak: [
      { text: '{days}-day streak! Keep going!', icon: '*' },
      { text: '{days} days in a row!', icon: '*' },
      { text: 'Amazing! {days}-day streak!', icon: '*' }
    ],
    daily: [
      { text: '{count} tasks done today!', icon: '*' },
      { text: '{count} completed today!', icon: '*' },
      { text: 'Crushed {count} tasks today!', icon: '*' }
    ],
    weekly: [
      { text: '{count} tasks this week!', icon: '+' },
      { text: '{count} completed this week', icon: '+' }
    ],
    milestone: [
      { text: '{count} tasks all-time!', icon: '+' },
      { text: 'Milestone: {count} tasks!', icon: '+' },
      { text: 'You\'ve done {count} tasks!', icon: '+' }
    ],
    allDone: [
      { text: 'All done for today!', icon: '*' },
      { text: 'Task list cleared!', icon: '*' },
      { text: 'Zero inbox achieved!', icon: '*' }
    ]
  },

  // Pattern messages
  pattern: {
    productivity: [
      { text: 'You\'re most productive in the {period}', icon: '+' },
      { text: 'Peak performance: {period}', icon: '+' }
    ],
    category: [
      { text: '{category} tasks are your strength', icon: '+' },
      { text: 'You excel at {category} tasks', icon: '+' }
    ],
    insight: [
      { text: 'Try {category} tasks in the {period}', icon: '+' },
      { text: 'Your {category} game is strong', icon: '+' }
    ]
  },

  // Time context messages
  timeContext: {
    morning: [
      { text: 'Good morning! Focus on: {task}', icon: '>' },
      { text: 'Rise and shine! Top priority: {task}', icon: '>' },
      { text: 'New day, fresh start. Begin with: {task}', icon: '>' }
    ],
    afternoon: [
      { text: 'Keep the momentum! Next: {task}', icon: '>' },
      { text: 'Afternoon push: {task}', icon: '>' },
      { text: 'Stay focused: {task}', icon: '>' }
    ],
    evening: [
      { text: '{remaining} tasks left today', icon: '>' },
      { text: 'Evening wrap-up: {task}', icon: '>' },
      { text: 'Finish strong: {task}', icon: '>' }
    ],
    night: [
      { text: 'Tomorrow\'s priority: {task}', icon: '>' },
      { text: 'Rest up. Tomorrow: {task}', icon: '>' }
    ]
  },

  // Encouragement messages (fallback)
  encouragement: {
    general: [
      { text: 'One step at a time', icon: '~' },
      { text: 'Focus on the ONE thing', icon: '~' },
      { text: 'You\'ve got this!', icon: '~' },
      { text: 'Small wins add up', icon: '~' },
      { text: 'Progress, not perfection', icon: '~' },
      { text: 'Keep moving forward', icon: '~' },
      { text: 'Every task counts', icon: '~' },
      { text: 'Flow with your tasks', icon: '~' }
    ],
    empty: [
      { text: 'No tasks! Enjoy your day', icon: '*' },
      { text: 'Nothing pending', icon: '*' },
      { text: 'Clear skies ahead', icon: '*' }
    ]
  }
};

/**
 * MessageEngine class
 * Generates contextual messages for wallpaper display
 */
class MessageEngine {
  constructor(statsAggregator = null) {
    this.statsAggregator = statsAggregator;
  }

  /**
   * Generate the most relevant message for the current state
   * @param {Object} user - User object with settings
   * @param {Array} tasks - Array of task objects
   * @param {Date} currentTime - Current timestamp
   * @param {Object} stats - Optional pre-computed stats
   * @returns {Object} Message object { text, priority, type, icon }
   */
  generateMessage(user, tasks, currentTime = new Date(), stats = null) {
    // Filter to active tasks only
    const activeTasks = (tasks || []).filter(t =>
      t && !t.completed && !t.archived
    );

    // Check each priority level in order
    const message =
      this.getCriticalMessage(activeTasks, currentTime) ||
      this.getAchievementMessage(tasks, stats, user) ||
      this.getPatternMessage(stats) ||
      this.getTimeContextMessage(currentTime, activeTasks) ||
      this.getEncouragementMessage(activeTasks);

    return message;
  }

  /**
   * Get critical message (overdue or urgent tasks)
   */
  getCriticalMessage(tasks, currentTime) {
    if (!tasks || tasks.length === 0) return null;

    // Check for overdue tasks
    const overdueTasks = tasks.filter(t => {
      if (!t.dueDate && !t.due_date) return false;
      const dueDate = new Date(t.dueDate || t.due_date);
      return dueDate < currentTime;
    });

    if (overdueTasks.length > 0) {
      const task = overdueTasks[0];
      const dueDate = new Date(task.dueDate || task.due_date);
      const timeAgo = this.formatTimeAgo(currentTime - dueDate);

      return this.selectMessage(MESSAGE_TEMPLATES.critical.overdue, {
        task: this.truncateText(task.title, 30),
        timeAgo
      }, MESSAGE_TYPES.CRITICAL);
    }

    // Check for urgent tasks (due within 2 hours)
    const urgentTasks = tasks.filter(t => {
      if (!t.dueDate && !t.due_date) return false;
      const dueDate = new Date(t.dueDate || t.due_date);
      const hoursUntil = (dueDate - currentTime) / (1000 * 60 * 60);
      return hoursUntil > 0 && hoursUntil <= 2;
    });

    if (urgentTasks.length > 0) {
      const task = urgentTasks[0];
      const dueDate = new Date(task.dueDate || task.due_date);
      const timeLeft = this.formatTimeLeft(dueDate - currentTime);

      return this.selectMessage(MESSAGE_TEMPLATES.critical.urgent, {
        task: this.truncateText(task.title, 25),
        timeLeft
      }, MESSAGE_TYPES.CRITICAL);
    }

    return null;
  }

  /**
   * Get achievement message (streaks, completions, milestones)
   */
  getAchievementMessage(tasks, stats, user) {
    // Check if all tasks are done
    const activeTasks = (tasks || []).filter(t => !t.completed && !t.archived);
    if (tasks && tasks.length > 0 && activeTasks.length === 0) {
      return this.selectMessage(MESSAGE_TEMPLATES.achievement.allDone, {}, MESSAGE_TYPES.ACHIEVEMENT);
    }

    // Check stats for achievements
    if (stats) {
      // Check for streak
      if (stats.streakDays && stats.streakDays >= 3) {
        return this.selectMessage(MESSAGE_TEMPLATES.achievement.streak, {
          days: stats.streakDays
        }, MESSAGE_TYPES.ACHIEVEMENT);
      }

      // Check for daily completions
      if (stats.completedToday && stats.completedToday >= 5) {
        return this.selectMessage(MESSAGE_TEMPLATES.achievement.daily, {
          count: stats.completedToday
        }, MESSAGE_TYPES.ACHIEVEMENT);
      }

      // Check for weekly completions
      if (stats.completedThisWeek && stats.completedThisWeek >= 20) {
        return this.selectMessage(MESSAGE_TEMPLATES.achievement.weekly, {
          count: stats.completedThisWeek
        }, MESSAGE_TYPES.ACHIEVEMENT);
      }

      // Check for milestones (100, 500, 1000, etc.)
      const milestones = [100, 250, 500, 1000, 2500, 5000];
      if (stats.totalCompleted) {
        for (const milestone of milestones) {
          if (stats.totalCompleted >= milestone && stats.totalCompleted < milestone + 10) {
            return this.selectMessage(MESSAGE_TEMPLATES.achievement.milestone, {
              count: milestone
            }, MESSAGE_TYPES.ACHIEVEMENT);
          }
        }
      }
    }

    return null;
  }

  /**
   * Get pattern-based message (productivity insights)
   */
  getPatternMessage(stats) {
    if (!stats || !stats.patterns) return null;

    // Check for peak productivity period
    if (stats.patterns.peakPeriod) {
      // Only show occasionally (20% chance)
      if (Math.random() < 0.2) {
        return this.selectMessage(MESSAGE_TEMPLATES.pattern.productivity, {
          period: stats.patterns.peakPeriod
        }, MESSAGE_TYPES.PATTERN);
      }
    }

    // Check for top category
    if (stats.patterns.topCategory && Math.random() < 0.15) {
      return this.selectMessage(MESSAGE_TEMPLATES.pattern.category, {
        category: stats.patterns.topCategory
      }, MESSAGE_TYPES.PATTERN);
    }

    return null;
  }

  /**
   * Get time context message (morning, afternoon, evening)
   */
  getTimeContextMessage(currentTime, tasks) {
    const hour = currentTime.getHours();
    const topTask = tasks && tasks.length > 0 ? tasks[0] : null;
    const remaining = tasks ? tasks.length : 0;

    let period;
    let templates;

    if (hour >= 5 && hour < 12) {
      period = 'morning';
      templates = MESSAGE_TEMPLATES.timeContext.morning;
    } else if (hour >= 12 && hour < 17) {
      period = 'afternoon';
      templates = MESSAGE_TEMPLATES.timeContext.afternoon;
    } else if (hour >= 17 && hour < 21) {
      period = 'evening';
      templates = MESSAGE_TEMPLATES.timeContext.evening;
    } else {
      period = 'night';
      templates = MESSAGE_TEMPLATES.timeContext.night;
    }

    if (topTask) {
      return this.selectMessage(templates, {
        task: this.truncateText(topTask.title, 25),
        remaining: remaining
      }, MESSAGE_TYPES.TIME_CONTEXT);
    }

    return null;
  }

  /**
   * Get encouragement message (fallback)
   */
  getEncouragementMessage(tasks) {
    if (!tasks || tasks.length === 0) {
      return this.selectMessage(MESSAGE_TEMPLATES.encouragement.empty, {}, MESSAGE_TYPES.ENCOURAGEMENT);
    }

    return this.selectMessage(MESSAGE_TEMPLATES.encouragement.general, {}, MESSAGE_TYPES.ENCOURAGEMENT);
  }

  /**
   * Select a random message from templates and fill in variables
   */
  selectMessage(templates, vars, priority) {
    if (!templates || templates.length === 0) {
      return this.getDefaultMessage();
    }

    const template = templates[Math.floor(Math.random() * templates.length)];
    let text = template.text;

    // Replace all variables
    for (const [key, value] of Object.entries(vars)) {
      text = text.replace(new RegExp(`{${key}}`, 'g'), value);
    }

    return {
      text,
      priority,
      type: this.getPriorityName(priority),
      icon: template.icon
    };
  }

  /**
   * Get priority name from enum
   */
  getPriorityName(priority) {
    const names = ['CRITICAL', 'ACHIEVEMENT', 'PATTERN', 'TIME_CONTEXT', 'ENCOURAGEMENT'];
    return names[priority] || 'ENCOURAGEMENT';
  }

  /**
   * Get default fallback message
   */
  getDefaultMessage() {
    return {
      text: '🎯 Focus on the ONE thing',
      priority: MESSAGE_TYPES.ENCOURAGEMENT,
      type: 'ENCOURAGEMENT',
      icon: '🎯'
    };
  }

  /**
   * Format time ago (e.g., "2 hours ago", "3 days ago")
   */
  formatTimeAgo(milliseconds) {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days} day${days > 1 ? 's' : ''} ago`;
    if (hours > 0) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    if (minutes > 0) return `${minutes} min ago`;
    return 'just now';
  }

  /**
   * Format time left (e.g., "30 min", "2 hours")
   */
  formatTimeLeft(milliseconds) {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (hours >= 1) {
      const remainingMins = minutes % 60;
      if (remainingMins > 0) {
        return `${hours}h ${remainingMins}m`;
      }
      return `${hours} hour${hours > 1 ? 's' : ''}`;
    }
    return `${minutes} min`;
  }

  /**
   * Truncate text to max length with ellipsis
   */
  truncateText(text, maxLength) {
    if (!text) return 'Task';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength - 3) + '...';
  }
}

module.exports = {
  MessageEngine,
  MESSAGE_TYPES,
  MESSAGE_TEMPLATES
};
