/**
 * Weather/Mood Overlay System
 * Epic 10 Phase 3 - Task 10
 *
 * Provides productivity-based weather overlays for wallpaper:
 * - Clear: On track, no overdue tasks
 * - Cloudy: Slightly behind, 1-2 overdue
 * - Overcast: Falling behind, 3-5 overdue
 * - Storm: Critical state, 5+ overdue or critical tasks
 * - Rainbow: Celebration! All tasks complete
 */

/**
 * Weather states with visual configurations
 */
const WEATHER_STATES = {
  rainbow: {
    name: 'Rainbow',
    description: 'All clear! Celebration mode',
    priority: 5, // Highest priority
    overlay: {
      type: 'rainbow_arc',
      opacity: 0.4,
      position: { x: 0.5, y: 0.3 },
      colors: ['#ff6b6b', '#feca57', '#48dbfb', '#1dd1a1', '#5f27cd'],
      spread: 0.8,
      glow: true,
    },
    particles: {
      type: 'sparkles',
      count: 40,
      colors: ['#ffd700', '#ff69b4', '#00ff7f', '#87ceeb'],
      speed: 0.6,
      size: { min: 2, max: 5 },
      glow: true,
      twinkle: true,
    },
    ambient: {
      brightness: 1.1,
      saturation: 1.2,
      warmth: 0.7,
      glow: 0.3,
    },
    message: 'All clear!',
  },

  clear: {
    name: 'Clear',
    description: 'On track, smooth sailing',
    priority: 4,
    overlay: {
      type: 'subtle_glow',
      opacity: 0.15,
      color: '#87ceeb',
      position: { x: 0.7, y: 0.2 },
    },
    particles: {
      type: 'light_dust',
      count: 15,
      color: '#ffffff',
      speed: 0.2,
      size: { min: 1, max: 2 },
    },
    ambient: {
      brightness: 1.0,
      saturation: 1.0,
      warmth: 0.5,
      glow: 0.1,
    },
    message: null,
  },

  cloudy: {
    name: 'Cloudy',
    description: 'Slightly behind schedule',
    priority: 3,
    overlay: {
      type: 'clouds',
      opacity: 0.35,
      count: 4,
      color: '#d3d3d3',
      speed: 0.15,
      coverage: 0.3, // 30% of sky covered
    },
    particles: {
      type: 'mist',
      count: 20,
      color: '#e0e0e0',
      speed: 0.1,
      size: { min: 3, max: 8 },
      blur: true,
    },
    ambient: {
      brightness: 0.9,
      saturation: 0.95,
      warmth: 0.4,
      glow: 0,
    },
    message: null,
  },

  overcast: {
    name: 'Overcast',
    description: 'Falling behind, needs attention',
    priority: 2,
    overlay: {
      type: 'heavy_clouds',
      opacity: 0.5,
      count: 6,
      color: '#9e9e9e',
      speed: 0.2,
      coverage: 0.6, // 60% coverage
    },
    particles: {
      type: 'fog',
      count: 30,
      color: '#bdbdbd',
      speed: 0.08,
      size: { min: 5, max: 15 },
      blur: true,
    },
    ambient: {
      brightness: 0.75,
      saturation: 0.85,
      warmth: 0.35,
      glow: 0,
    },
    message: 'Tasks need attention',
  },

  storm: {
    name: 'Storm',
    description: 'Critical state, urgent action needed',
    priority: 1,
    overlay: {
      type: 'storm_clouds',
      opacity: 0.6,
      color: '#424242',
      coverage: 0.8,
      lightning: {
        enabled: true,
        frequency: 5000, // ms between flashes
        color: '#fff9c4',
        intensity: 0.8,
      },
    },
    particles: {
      type: 'rain',
      count: 60,
      color: '#90caf9',
      speed: 1.5,
      angle: -10, // Slight wind angle
      size: { min: 1, max: 2 },
      length: 15,
    },
    ambient: {
      brightness: 0.6,
      saturation: 0.7,
      warmth: 0.2,
      glow: 0,
      flash: true, // Lightning flash effect
    },
    message: 'Critical tasks overdue!',
  },
};

/**
 * Thresholds for determining weather state
 */
const THRESHOLDS = {
  // Task completion rate thresholds
  completionRate: {
    excellent: 0.9,  // 90%+ = clear/rainbow
    good: 0.7,       // 70-90% = clear
    moderate: 0.5,   // 50-70% = cloudy
    poor: 0.3,       // 30-50% = overcast
    critical: 0.0,   // <30% = storm
  },
  // Overdue task thresholds
  overdueTasks: {
    clear: 0,
    cloudy: 2,
    overcast: 5,
    storm: 10,
  },
  // Critical task thresholds (critical priority overdue)
  criticalTasks: {
    clear: 0,
    cloudy: 1,
    overcast: 2,
    storm: 3,
  },
  // Hours overdue escalation
  overdueHours: {
    warning: 2,     // 2+ hours overdue
    serious: 12,    // 12+ hours overdue
    critical: 24,   // 24+ hours overdue
  },
};

/**
 * Calculate productivity score from tasks
 * @param {Array} tasks - Array of task objects
 * @returns {Object} Productivity metrics
 */
function calculateProductivityMetrics(tasks = []) {
  if (!tasks || tasks.length === 0) {
    return {
      totalTasks: 0,
      completedTasks: 0,
      pendingTasks: 0,
      overdueTasks: 0,
      criticalOverdue: 0,
      completionRate: 1.0, // No tasks = perfect
      maxOverdueHours: 0,
      isAllClear: true,
    };
  }

  const now = new Date();
  let completedTasks = 0;
  let overdueTasks = 0;
  let criticalOverdue = 0;
  let maxOverdueHours = 0;

  for (const task of tasks) {
    if (task.completed === true) {
      completedTasks++;
      continue;
    }

    // Check if overdue
    if (task.due_date) {
      const dueDate = new Date(task.due_date);
      if (task.due_time) {
        const [hours, minutes] = task.due_time.split(':').map(Number);
        dueDate.setHours(hours || 0, minutes || 0, 0, 0);
      }

      if (dueDate < now) {
        overdueTasks++;
        const hoursOverdue = (now - dueDate) / (1000 * 60 * 60);
        maxOverdueHours = Math.max(maxOverdueHours, hoursOverdue);

        // Check if critical priority and overdue
        if (task.priority === 3 || task.priority === 'critical') {
          criticalOverdue++;
        }
      }
    }
  }

  const pendingTasks = tasks.length - completedTasks;
  const completionRate = tasks.length > 0 ? completedTasks / tasks.length : 1.0;
  const isAllClear = pendingTasks === 0;

  return {
    totalTasks: tasks.length,
    completedTasks,
    pendingTasks,
    overdueTasks,
    criticalOverdue,
    completionRate,
    maxOverdueHours,
    isAllClear,
  };
}

/**
 * Determine weather state from productivity metrics
 * @param {Object} metrics - Productivity metrics
 * @returns {string} Weather state name
 */
function determineWeatherState(metrics) {
  const {
    isAllClear,
    overdueTasks,
    criticalOverdue,
    completionRate,
    maxOverdueHours,
  } = metrics;

  // Rainbow: All tasks completed (celebration!)
  if (isAllClear && metrics.totalTasks > 0) {
    return 'rainbow';
  }

  // Storm: Critical conditions
  if (
    criticalOverdue >= THRESHOLDS.criticalTasks.storm ||
    overdueTasks >= THRESHOLDS.overdueTasks.storm ||
    maxOverdueHours >= THRESHOLDS.overdueHours.critical
  ) {
    return 'storm';
  }

  // Overcast: Falling behind
  if (
    criticalOverdue >= THRESHOLDS.criticalTasks.overcast ||
    overdueTasks >= THRESHOLDS.overdueTasks.overcast ||
    maxOverdueHours >= THRESHOLDS.overdueHours.serious ||
    completionRate < THRESHOLDS.completionRate.moderate
  ) {
    return 'overcast';
  }

  // Cloudy: Slightly behind
  if (
    criticalOverdue >= THRESHOLDS.criticalTasks.cloudy ||
    overdueTasks >= THRESHOLDS.overdueTasks.cloudy ||
    maxOverdueHours >= THRESHOLDS.overdueHours.warning ||
    completionRate < THRESHOLDS.completionRate.good
  ) {
    return 'cloudy';
  }

  // Clear: On track
  return 'clear';
}

/**
 * Get weather overlay configuration based on tasks
 * @param {Array} tasks - Array of task objects
 * @param {Object} options - Additional options
 * @returns {Object} Weather configuration
 */
function getWeatherOverlay(tasks = [], options = {}) {
  const { forceState = null } = options;

  // Calculate productivity metrics
  const metrics = calculateProductivityMetrics(tasks);

  // Determine weather state (or use forced state for testing)
  const stateName = forceState || determineWeatherState(metrics);
  const weather = WEATHER_STATES[stateName];

  if (!weather) {
    console.warn(`[Weather] Unknown state: ${stateName}, defaulting to clear`);
    return {
      ...WEATHER_STATES.clear,
      state: 'clear',
      metrics,
    };
  }

  return {
    ...weather,
    state: stateName,
    metrics,
    timestamp: new Date().toISOString(),
  };
}

/**
 * Blend weather overlay with time-of-day environment
 * @param {Object} weather - Weather configuration
 * @param {Object} environment - Time-of-day environment
 * @returns {Object} Combined visual configuration
 */
function blendWithEnvironment(weather, environment) {
  // Weather takes precedence for overlay, but respects time-of-day colors
  const blended = {
    // Base gradient from time-of-day
    gradient: environment.gradient,

    // Weather overlay on top
    weatherOverlay: weather.overlay,

    // Combine particles (both time-of-day and weather)
    particles: [
      { ...environment.particles, layer: 'background' },
      { ...weather.particles, layer: 'foreground' },
    ],

    // Blend ambient settings
    ambient: {
      brightness: environment.ambient.brightness * weather.ambient.brightness,
      saturation: environment.ambient.saturation * weather.ambient.saturation,
      warmth: (environment.ambient.warmth + weather.ambient.warmth) / 2,
      glow: Math.max(environment.ambient.glow || 0, weather.ambient.glow || 0),
      flash: weather.ambient.flash || false,
    },

    // Metadata
    metadata: {
      weatherState: weather.state,
      weatherName: weather.name,
      environmentPeriod: environment.metadata?.period,
      message: weather.message,
      metrics: weather.metrics,
    },
  };

  return blended;
}

/**
 * Get all weather states (for settings/preview)
 * @returns {Array} All weather state configurations
 */
function getAllWeatherStates() {
  return Object.keys(WEATHER_STATES).map(key => ({
    id: key,
    name: WEATHER_STATES[key].name,
    description: WEATHER_STATES[key].description,
    priority: WEATHER_STATES[key].priority,
  }));
}

/**
 * Preview weather state (for testing/settings)
 * @param {string} state - Weather state name
 * @returns {Object} Weather configuration
 */
function previewWeatherState(state) {
  return getWeatherOverlay([], { forceState: state });
}

module.exports = {
  getWeatherOverlay,
  blendWithEnvironment,
  calculateProductivityMetrics,
  determineWeatherState,
  getAllWeatherStates,
  previewWeatherState,
  WEATHER_STATES,
  THRESHOLDS,
};
