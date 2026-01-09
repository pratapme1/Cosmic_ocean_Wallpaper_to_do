/**
 * Environment System - Time-of-Day Dynamic Environments
 * Epic 10 Phase 3 - Task 9
 *
 * Provides time-based visual environments for wallpaper generation:
 * - Dawn (5-7 AM): Warm sunrise rays
 * - Morning (7-12): Bright sky with clouds
 * - Afternoon (12-5 PM): Deep blue with sun rays
 * - Evening (5-8 PM): Sunset orange-purple glow
 * - Night (8 PM - 5 AM): Starry dark sky
 */

/**
 * Time of day periods with hour ranges
 */
const TIME_PERIODS = {
  DAWN: { start: 5, end: 7, name: 'dawn' },
  MORNING: { start: 7, end: 12, name: 'morning' },
  AFTERNOON: { start: 12, end: 17, name: 'afternoon' },
  EVENING: { start: 17, end: 20, name: 'evening' },
  NIGHT: { start: 20, end: 5, name: 'night' }, // Wraps around midnight
};

/**
 * Environment configurations for each time period
 */
const ENVIRONMENTS = {
  dawn: {
    name: 'Dawn',
    description: 'Sunrise warmth',
    gradient: {
      type: 'linear',
      angle: 180, // Top to bottom
      stops: [
        { offset: 0, color: '#1a1a2e' },     // Dark top
        { offset: 0.3, color: '#4a1942' },   // Purple
        { offset: 0.5, color: '#c94b4b' },   // Coral
        { offset: 0.7, color: '#f2994a' },   // Orange
        { offset: 1, color: '#ffb347' },     // Golden bottom
      ],
    },
    overlay: {
      type: 'sunrise_rays',
      opacity: 0.3,
      color: '#ffb347',
      rays: 8,
    },
    particles: {
      type: 'dust_motes',
      count: 15,
      color: '#ffe0b2',
      speed: 0.3,
      size: { min: 1, max: 3 },
    },
    ambient: {
      brightness: 0.7,
      warmth: 0.8, // 0 = cool, 1 = warm
      saturation: 1.1,
    },
  },

  morning: {
    name: 'Morning',
    description: 'Bright and clear',
    gradient: {
      type: 'linear',
      angle: 180,
      stops: [
        { offset: 0, color: '#87ceeb' },     // Sky blue top
        { offset: 0.4, color: '#98d8e8' },   // Light cyan
        { offset: 0.7, color: '#b0e0f0' },   // Pale blue
        { offset: 1, color: '#e0f4ff' },     // Almost white bottom
      ],
    },
    overlay: {
      type: 'clouds',
      opacity: 0.4,
      count: 5,
      speed: 0.2,
    },
    particles: {
      type: 'floating_specks',
      count: 20,
      color: '#ffffff',
      speed: 0.4,
      size: { min: 1, max: 2 },
    },
    ambient: {
      brightness: 1.0,
      warmth: 0.5,
      saturation: 1.0,
    },
  },

  afternoon: {
    name: 'Afternoon',
    description: 'Deep blue sky',
    gradient: {
      type: 'linear',
      angle: 180,
      stops: [
        { offset: 0, color: '#1e3c72' },     // Deep blue top
        { offset: 0.3, color: '#2a5298' },   // Rich blue
        { offset: 0.6, color: '#4a90c2' },   // Medium blue
        { offset: 1, color: '#87ceeb' },     // Sky blue bottom
      ],
    },
    overlay: {
      type: 'sun_rays',
      opacity: 0.2,
      color: '#ffd700',
      rays: 12,
      position: { x: 0.7, y: 0.2 }, // Upper right
    },
    particles: {
      type: 'light_beams',
      count: 8,
      color: '#fff8dc',
      speed: 0.1,
      size: { min: 2, max: 5 },
    },
    ambient: {
      brightness: 0.95,
      warmth: 0.6,
      saturation: 1.05,
    },
  },

  evening: {
    name: 'Evening',
    description: 'Sunset glow',
    gradient: {
      type: 'linear',
      angle: 180,
      stops: [
        { offset: 0, color: '#2c3e50' },     // Dark blue top
        { offset: 0.25, color: '#614385' },  // Purple
        { offset: 0.5, color: '#c94b4b' },   // Red-orange
        { offset: 0.75, color: '#f2994a' },  // Orange
        { offset: 1, color: '#f8b500' },     // Golden bottom
      ],
    },
    overlay: {
      type: 'sunset_glow',
      opacity: 0.35,
      color: '#ff6b6b',
      spread: 0.6,
    },
    particles: {
      type: 'warm_drift',
      count: 12,
      color: '#ffd6a5',
      speed: 0.25,
      size: { min: 2, max: 4 },
    },
    ambient: {
      brightness: 0.8,
      warmth: 0.9,
      saturation: 1.15,
    },
  },

  night: {
    name: 'Night',
    description: 'Starry sky',
    gradient: {
      type: 'linear',
      angle: 180,
      stops: [
        { offset: 0, color: '#0d1b2a' },     // Almost black top
        { offset: 0.3, color: '#1b263b' },   // Dark navy
        { offset: 0.6, color: '#2d3f5f' },   // Deep blue
        { offset: 1, color: '#3a506b' },     // Muted blue bottom
      ],
    },
    overlay: {
      type: 'starfield',
      opacity: 0.6,
      density: 150,
      twinkle: true,
    },
    particles: {
      type: 'stars',
      count: 50,
      color: '#ffffff',
      speed: 0.05,
      size: { min: 1, max: 3 },
      twinkle: true,
    },
    ambient: {
      brightness: 0.6,
      warmth: 0.2,
      saturation: 0.9,
    },
  },
};

/**
 * Theme-specific environment adjustments
 * Modifies base environment to match theme personality
 */
const THEME_MODIFIERS = {
  cosmic: {
    gradient: {
      saturation: 1.2,
      shift: 10, // Shift hues toward purple
    },
    particles: {
      multiplier: 1.5, // More particles
      glow: true,
    },
    overlay: {
      nebula: true, // Add nebula effect
    },
  },
  ocean: {
    gradient: {
      saturation: 0.9,
      shift: -20, // Shift hues toward blue-green
    },
    particles: {
      multiplier: 0.8,
      type_override: 'bubbles',
    },
    overlay: {
      waves: true,
    },
  },
  fantasy: {
    gradient: {
      saturation: 1.1,
      shift: 30, // Shift hues toward magical pink-purple
    },
    particles: {
      multiplier: 1.2,
      type_override: 'sparkles',
      glow: true,
    },
    overlay: {
      aurora: true,
    },
  },
};

/**
 * Get current hour in user's timezone
 * @param {string} timezone - IANA timezone string (e.g., 'Asia/Kolkata')
 * @returns {number} Hour (0-23)
 */
function getCurrentHour(timezone = 'UTC') {
  try {
    const now = new Date();
    const formatter = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      hour: 'numeric',
      hour12: false,
    });
    const hour = parseInt(formatter.format(now), 10);
    return isNaN(hour) ? new Date().getUTCHours() : hour;
  } catch (err) {
    console.warn(`[Environment] Invalid timezone ${timezone}, using UTC`);
    return new Date().getUTCHours();
  }
}

/**
 * Determine time period from hour
 * @param {number} hour - Hour (0-23)
 * @returns {string} Time period name
 */
function getTimePeriod(hour) {
  if (hour >= TIME_PERIODS.DAWN.start && hour < TIME_PERIODS.DAWN.end) {
    return 'dawn';
  }
  if (hour >= TIME_PERIODS.MORNING.start && hour < TIME_PERIODS.MORNING.end) {
    return 'morning';
  }
  if (hour >= TIME_PERIODS.AFTERNOON.start && hour < TIME_PERIODS.AFTERNOON.end) {
    return 'afternoon';
  }
  if (hour >= TIME_PERIODS.EVENING.start && hour < TIME_PERIODS.EVENING.end) {
    return 'evening';
  }
  // Night: 8 PM to 5 AM (wraps around midnight)
  return 'night';
}

/**
 * Calculate transition progress between two time periods
 * Returns 0-1 for smooth blending
 * @param {number} hour - Current hour
 * @param {number} minute - Current minute (optional, for smoother transitions)
 * @returns {Object} { current, next, progress }
 */
function getTransitionState(hour, minute = 0) {
  const hourDecimal = hour + minute / 60;
  const current = getTimePeriod(hour);

  // Define transition windows (30 min before/after period boundary)
  const TRANSITION_WINDOW = 0.5; // hours

  // Find the next period and calculate transition progress
  let next = current;
  let progress = 0;

  // Check if we're near a transition boundary
  const periods = Object.values(TIME_PERIODS);
  for (const period of periods) {
    const boundaryStart = period.start;
    const distanceToStart = Math.abs(hourDecimal - boundaryStart);

    if (distanceToStart <= TRANSITION_WINDOW) {
      // We're in a transition zone
      const prevPeriod = getTimePeriod(boundaryStart - 1);
      if (hourDecimal < boundaryStart) {
        // Approaching the boundary
        next = period.name;
        progress = 0.5 + (TRANSITION_WINDOW - distanceToStart) / TRANSITION_WINDOW * 0.5;
      } else {
        // Just passed the boundary
        next = current;
        progress = (TRANSITION_WINDOW - distanceToStart) / TRANSITION_WINDOW * 0.5;
      }
      break;
    }
  }

  return { current, next, progress };
}

/**
 * Apply theme modifiers to environment
 * @param {Object} environment - Base environment config
 * @param {string} theme - Theme name (cosmic, ocean, fantasy)
 * @returns {Object} Modified environment
 */
function applyThemeModifiers(environment, theme) {
  const modifiers = THEME_MODIFIERS[theme];
  if (!modifiers) return environment;

  const modified = JSON.parse(JSON.stringify(environment)); // Deep clone

  // Apply gradient modifications
  if (modifiers.gradient) {
    // Saturation and hue shift would be applied during rendering
    modified.gradient.saturation = modifiers.gradient.saturation;
    modified.gradient.hueShift = modifiers.gradient.shift;
  }

  // Apply particle modifications
  if (modifiers.particles) {
    if (modifiers.particles.multiplier) {
      modified.particles.count = Math.round(
        modified.particles.count * modifiers.particles.multiplier
      );
    }
    if (modifiers.particles.type_override) {
      modified.particles.type = modifiers.particles.type_override;
    }
    if (modifiers.particles.glow) {
      modified.particles.glow = true;
    }
  }

  // Apply overlay modifications
  if (modifiers.overlay) {
    Object.assign(modified.overlay, modifiers.overlay);
  }

  return modified;
}

/**
 * Blend two environments for smooth transitions
 * @param {Object} envA - First environment
 * @param {Object} envB - Second environment
 * @param {number} progress - Blend progress (0 = envA, 1 = envB)
 * @returns {Object} Blended environment
 */
function blendEnvironments(envA, envB, progress) {
  if (progress === 0) return envA;
  if (progress === 1) return envB;

  // Linear interpolation helper
  const lerp = (a, b, t) => a + (b - a) * t;

  // Blend ambient values
  const ambient = {
    brightness: lerp(envA.ambient.brightness, envB.ambient.brightness, progress),
    warmth: lerp(envA.ambient.warmth, envB.ambient.warmth, progress),
    saturation: lerp(envA.ambient.saturation, envB.ambient.saturation, progress),
  };

  // Blend particle counts
  const particles = {
    ...envA.particles,
    count: Math.round(lerp(envA.particles.count, envB.particles.count, progress)),
    speed: lerp(envA.particles.speed, envB.particles.speed, progress),
  };

  // Blend overlay opacity
  const overlay = {
    ...envA.overlay,
    opacity: lerp(envA.overlay.opacity, envB.overlay.opacity, progress),
  };

  // For gradient, use the dominant environment's gradient
  // (color blending is complex and better handled in rendering)
  const gradient = progress < 0.5 ? envA.gradient : envB.gradient;

  return {
    name: progress < 0.5 ? envA.name : envB.name,
    description: progress < 0.5 ? envA.description : envB.description,
    gradient,
    overlay,
    particles,
    ambient,
    blendProgress: progress,
    blendFrom: envA.name,
    blendTo: envB.name,
  };
}

/**
 * Get environment configuration for current time
 * @param {Object} options - Configuration options
 * @param {string} options.timezone - User's timezone (default: 'UTC')
 * @param {string} options.theme - Theme name (cosmic, ocean, fantasy)
 * @param {boolean} options.enableTransitions - Enable smooth transitions (default: true)
 * @param {number} options.overrideHour - Override hour for testing (optional)
 * @returns {Object} Environment configuration
 */
function getEnvironment(options = {}) {
  const {
    timezone = 'UTC',
    theme = 'cosmic',
    enableTransitions = true,
    overrideHour = null,
  } = options;

  // Get current time
  const hour = overrideHour !== null ? overrideHour : getCurrentHour(timezone);
  const minute = new Date().getMinutes();

  // Determine time period
  const period = getTimePeriod(hour);
  let environment = ENVIRONMENTS[period];

  // Handle transitions if enabled
  if (enableTransitions) {
    const transition = getTransitionState(hour, minute);
    if (transition.progress > 0 && transition.next !== transition.current) {
      const nextEnv = ENVIRONMENTS[transition.next];
      environment = blendEnvironments(environment, nextEnv, transition.progress);
    }
  }

  // Apply theme-specific modifications
  environment = applyThemeModifiers(environment, theme);

  // Add metadata
  return {
    ...environment,
    metadata: {
      period,
      hour,
      timezone,
      theme,
      timestamp: new Date().toISOString(),
    },
  };
}

/**
 * Get all available environments (for settings/preview)
 * @returns {Object} All environment configurations
 */
function getAllEnvironments() {
  return Object.keys(ENVIRONMENTS).map(key => ({
    id: key,
    ...ENVIRONMENTS[key],
  }));
}

/**
 * Preview environment for a specific hour (for testing/settings)
 * @param {number} hour - Hour to preview (0-23)
 * @param {string} theme - Theme name
 * @returns {Object} Environment configuration
 */
function previewEnvironment(hour, theme = 'cosmic') {
  return getEnvironment({
    overrideHour: hour,
    theme,
    enableTransitions: false,
  });
}

module.exports = {
  getEnvironment,
  getAllEnvironments,
  previewEnvironment,
  getTimePeriod,
  getCurrentHour,
  TIME_PERIODS,
  ENVIRONMENTS,
  THEME_MODIFIERS,
};
