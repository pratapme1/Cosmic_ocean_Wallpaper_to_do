/**
 * Atmosphere Controller for Wallpaper Intelligence
 *
 * Controls wallpaper visual parameters based on urgency score and task state.
 * Maps urgency levels to visual parameters for dynamic wallpaper appearance.
 *
 * Urgency States:
 * - clear (0-20): No pending tasks, calm visuals
 * - calm (20-40): Tasks under control, relaxed atmosphere
 * - attention (40-60): Some tasks need focus
 * - urgent (60-80): Important tasks due soon
 * - critical (80-100): Overdue or immediate attention needed
 */

const URGENCY_STATES = {
  CLEAR: 'clear',
  CALM: 'calm',
  ATTENTION: 'attention',
  URGENT: 'urgent',
  CRITICAL: 'critical'
};

// Visual parameter mappings for each urgency state
const VISUAL_PARAMS = {
  clear: {
    particleCount: 30,
    particleCountRange: [25, 35],
    animationSpeed: 0.5,
    colorIntensity: 0.6,
    breathingRate: 0.02,
    glowIntensity: 0.4,
    transitionDuration: 2000  // ms
  },
  calm: {
    particleCount: 50,
    particleCountRange: [40, 60],
    animationSpeed: 0.8,
    colorIntensity: 0.7,
    breathingRate: 0.04,
    glowIntensity: 0.5,
    transitionDuration: 1500
  },
  attention: {
    particleCount: 70,
    particleCountRange: [60, 80],
    animationSpeed: 1.0,
    colorIntensity: 0.8,
    breathingRate: 0.06,
    glowIntensity: 0.6,
    transitionDuration: 1000
  },
  urgent: {
    particleCount: 90,
    particleCountRange: [80, 100],
    animationSpeed: 1.3,
    colorIntensity: 0.9,
    breathingRate: 0.07,
    glowIntensity: 0.75,
    transitionDuration: 800
  },
  critical: {
    particleCount: 100,
    particleCountRange: [95, 100],
    animationSpeed: 1.5,
    colorIntensity: 1.0,
    breathingRate: 0.08,
    glowIntensity: 0.9,
    transitionDuration: 500
  }
};

// Score thresholds for each state
const SCORE_THRESHOLDS = {
  clear: { min: 0, max: 20 },
  calm: { min: 20, max: 40 },
  attention: { min: 40, max: 60 },
  urgent: { min: 60, max: 80 },
  critical: { min: 80, max: 100 }
};

/**
 * AtmosphereController class
 * Calculates urgency and maps to visual parameters
 */
class AtmosphereController {
  constructor(options = {}) {
    this.options = {
      overdueWeight: 25,        // Score added per overdue task
      criticalWeight: 20,       // Score added per critical task (<2h)
      urgentWeight: 10,         // Score added per urgent task (<8h)
      attentionWeight: 5,       // Score added per attention task (<24h)
      maxScore: 100,
      ...options
    };
  }

  /**
   * Calculate the complete atmosphere state
   * @param {Array} tasks - Array of task objects
   * @param {Object} stats - Optional user statistics
   * @returns {Object} Atmosphere state with urgency score, state, and visual params
   */
  calculateState(tasks, stats = null) {
    const urgencyScore = this.calculateUrgencyScore(tasks);
    const state = this.scoreToState(urgencyScore);
    const visualParams = this.getVisualParams(state, urgencyScore);

    return {
      urgencyScore,
      state,
      stateName: state,
      visualParams,
      taskCounts: this.getTaskCounts(tasks),
      timestamp: new Date().toISOString()
    };
  }

  /**
   * Calculate urgency score based on tasks
   * @param {Array} tasks - Array of task objects
   * @returns {number} Score from 0-100
   */
  calculateUrgencyScore(tasks) {
    if (!tasks || tasks.length === 0) {
      return 0; // No tasks = clear state
    }

    const now = new Date();
    let score = 0;

    // Filter to active tasks only
    const activeTasks = tasks.filter(t =>
      t && !t.completed && !t.archived
    );

    if (activeTasks.length === 0) {
      return 0; // All done = clear state
    }

    for (const task of activeTasks) {
      const dueDate = task.dueDate || task.due_date;

      if (!dueDate) {
        // No due date - minimal contribution
        score += 1;
        continue;
      }

      const dueDateObj = new Date(dueDate);
      const hoursUntilDue = (dueDateObj - now) / (1000 * 60 * 60);

      if (hoursUntilDue < 0) {
        // Overdue
        score += this.options.overdueWeight;
      } else if (hoursUntilDue < 2) {
        // Critical - due within 2 hours
        score += this.options.criticalWeight;
      } else if (hoursUntilDue < 8) {
        // Urgent - due within 8 hours
        score += this.options.urgentWeight;
      } else if (hoursUntilDue < 24) {
        // Attention - due within 24 hours
        score += this.options.attentionWeight;
      }
      // Tasks due later than 24h don't add to score
    }

    // Apply priority multiplier
    const highPriorityTasks = activeTasks.filter(t => t.priority === 1);
    if (highPriorityTasks.length > 0) {
      score += highPriorityTasks.length * 5;
    }

    // Cap at max score
    return Math.min(this.options.maxScore, score);
  }

  /**
   * Convert urgency score to state name
   * @param {number} score - Urgency score 0-100
   * @returns {string} State name
   */
  scoreToState(score) {
    if (score <= SCORE_THRESHOLDS.clear.max) return URGENCY_STATES.CLEAR;
    if (score <= SCORE_THRESHOLDS.calm.max) return URGENCY_STATES.CALM;
    if (score <= SCORE_THRESHOLDS.attention.max) return URGENCY_STATES.ATTENTION;
    if (score <= SCORE_THRESHOLDS.urgent.max) return URGENCY_STATES.URGENT;
    return URGENCY_STATES.CRITICAL;
  }

  /**
   * Get visual parameters for a state
   * @param {string} state - State name
   * @param {number} score - Urgency score for interpolation
   * @returns {Object} Visual parameters
   */
  getVisualParams(state, score) {
    const params = VISUAL_PARAMS[state];
    if (!params) {
      return VISUAL_PARAMS.calm; // Default fallback
    }

    // Create copy with interpolated values
    const result = { ...params };

    // Interpolate particle count within range
    if (params.particleCountRange) {
      const [min, max] = params.particleCountRange;
      const thresholds = SCORE_THRESHOLDS[state];
      const normalizedScore = (score - thresholds.min) / (thresholds.max - thresholds.min);
      result.particleCount = Math.round(min + (max - min) * normalizedScore);
    }

    return result;
  }

  /**
   * Get task counts by category
   * @param {Array} tasks - Array of task objects
   * @returns {Object} Task counts
   */
  getTaskCounts(tasks) {
    if (!tasks) return { total: 0, overdue: 0, urgent: 0, upcoming: 0, noDate: 0 };

    const now = new Date();
    const activeTasks = tasks.filter(t => t && !t.completed && !t.archived);

    let overdue = 0;
    let urgent = 0;
    let upcoming = 0;
    let noDate = 0;

    for (const task of activeTasks) {
      const dueDate = task.dueDate || task.due_date;

      if (!dueDate) {
        noDate++;
        continue;
      }

      const dueDateObj = new Date(dueDate);
      const hoursUntilDue = (dueDateObj - now) / (1000 * 60 * 60);

      if (hoursUntilDue < 0) {
        overdue++;
      } else if (hoursUntilDue < 8) {
        urgent++;
      } else {
        upcoming++;
      }
    }

    return {
      total: activeTasks.length,
      overdue,
      urgent,
      upcoming,
      noDate
    };
  }

  /**
   * Get the dominant urgency level for wallpaper theming
   * @param {Array} tasks - Array of task objects
   * @returns {string} Urgency state for color theming
   */
  getDominantUrgency(tasks) {
    const state = this.calculateState(tasks);
    return state.state;
  }

  /**
   * Check if atmosphere should use animated effects
   * @param {string} state - Urgency state
   * @returns {boolean} Whether to enable animations
   */
  shouldAnimate(state) {
    // Enable animations for attention+ states
    return ['attention', 'urgent', 'critical'].includes(state);
  }

  /**
   * Get color multiplier for theme colors based on urgency
   * @param {string} state - Urgency state
   * @returns {number} Color intensity multiplier 0-1
   */
  getColorMultiplier(state) {
    return VISUAL_PARAMS[state]?.colorIntensity || 0.7;
  }

  /**
   * Get recommended particle density
   * @param {string} state - Urgency state
   * @returns {number} Particle count
   */
  getParticleDensity(state) {
    return VISUAL_PARAMS[state]?.particleCount || 50;
  }

  /**
   * Get animation speed multiplier
   * @param {string} state - Urgency state
   * @returns {number} Speed multiplier
   */
  getAnimationSpeed(state) {
    return VISUAL_PARAMS[state]?.animationSpeed || 1.0;
  }
}

module.exports = {
  AtmosphereController,
  URGENCY_STATES,
  VISUAL_PARAMS,
  SCORE_THRESHOLDS
};
