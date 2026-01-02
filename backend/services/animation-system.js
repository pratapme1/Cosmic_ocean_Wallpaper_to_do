/**
 * Animation System
 * Provides easing curves, breathing animations, and timing utilities
 * Based on ANDROID_UI_UX_DESIGN_SYSTEM.md specifications
 */

/**
 * Cubic Bezier easing curves
 */
const EASING_CURVES = {
  standard: { p1: 0.4, p2: 0.0, p3: 0.2, p4: 1.0 },
  accelerate: { p1: 0.4, p2: 0.0, p3: 1.0, p4: 1.0 },
  decelerate: { p1: 0.0, p2: 0.0, p3: 0.2, p4: 1.0 },
  breathe: { p1: 0.45, p2: 0.05, p3: 0.55, p4: 0.95 },
  bounce: { p1: 0.68, p2: -0.55, p3: 0.265, p4: 1.55 }
};

/**
 * Animation duration standards (in milliseconds)
 */
const DURATIONS = {
  microInteraction: 100,
  buttonState: 150,
  checkboxFill: 200,
  contentReveal: 250,
  taskSlide: 400,
  celebrationSmall: 1000,
  celebrationLarge: 2500,
  colorTransition: 30000,
  breatheMin: 1500,
  breatheMax: 10000
};

/**
 * Calculate breathing animation phase using sine wave
 * @param {number} timeMs - Current time in milliseconds
 * @param {number} cycleDurationMs - Duration of one breath cycle
 * @returns {number} Phase value from 0 to 1
 */
function calculateBreathPhase(timeMs, cycleDurationMs) {
  const phase = (timeMs % cycleDurationMs) / cycleDurationMs;
  return Math.sin(phase * 2 * Math.PI) * 0.5 + 0.5;
}

/**
 * Calculate scale factor from breath phase
 * @param {number} phase - Breath phase (0-1)
 * @param {number} intensity - Intensity of breathing (0.02 for calm, 0.08 for critical)
 * @returns {number} Scale factor
 */
function calculateBreathScale(phase, intensity) {
  return 1 + (phase - 0.5) * 2 * intensity;
}

/**
 * Get breathing parameters based on urgency
 * @param {string} urgency - Urgency level
 * @returns {object} Breathing parameters
 */
function getBreathingParams(urgency) {
  const params = {
    clear: {
      cycleDuration: 4000,
      intensity: 0.02,
      color: 'rgba(76, 175, 80, 0.1)'
    },
    calm: {
      cycleDuration: 5000,
      intensity: 0.02,
      color: 'rgba(107, 138, 255, 0.1)'
    },
    attention: {
      cycleDuration: 3500,
      intensity: 0.04,
      color: 'rgba(155, 122, 255, 0.15)'
    },
    urgent: {
      cycleDuration: 2500,
      intensity: 0.06,
      color: 'rgba(255, 183, 77, 0.2)'
    },
    critical: {
      cycleDuration: 1500,
      intensity: 0.08,
      color: 'rgba(255, 82, 82, 0.25)'
    }
  };

  return params[urgency] || params.calm;
}

/**
 * Cubic bezier interpolation
 * Simplified implementation for t in [0, 1]
 */
function cubicBezier(p1, p2, p3, p4, t) {
  const cX = 3 * p1;
  const bX = 3 * (p3 - p1) - cX;
  const aX = 1 - cX - bX;

  const cY = 3 * p2;
  const bY = 3 * (p4 - p2) - cY;
  const aY = 1 - cY - bY;

  function sampleCurveX(t) {
    return ((aX * t + bX) * t + cX) * t;
  }

  function sampleCurveY(t) {
    return ((aY * t + bY) * t + cY) * t;
  }

  function solveCurveX(x) {
    let t0, t1, t2, x2, d2;
    const epsilon = 1e-5;

    // Newton-Raphson iteration
    t2 = x;
    for (let i = 0; i < 8; i++) {
      x2 = sampleCurveX(t2) - x;
      if (Math.abs(x2) < epsilon) return t2;
      d2 = (3 * aX * t2 + 2 * bX) * t2 + cX;
      if (Math.abs(d2) < epsilon) break;
      t2 = t2 - x2 / d2;
    }

    // Fallback to bisection
    t0 = 0;
    t1 = 1;
    t2 = x;

    while (t0 < t1) {
      x2 = sampleCurveX(t2);
      if (Math.abs(x2 - x) < epsilon) return t2;
      if (x > x2) t0 = t2;
      else t1 = t2;
      t2 = (t1 - t0) * 0.5 + t0;
    }

    return t2;
  }

  return sampleCurveY(solveCurveX(t));
}

/**
 * Apply easing curve to progress value
 * @param {string} curveName - Name of easing curve
 * @param {number} progress - Linear progress (0-1)
 * @returns {number} Eased progress (0-1)
 */
function applyEasing(curveName, progress) {
  const curve = EASING_CURVES[curveName] || EASING_CURVES.standard;
  return cubicBezier(curve.p1, curve.p2, curve.p3, curve.p4, progress);
}

/**
 * Calculate color transition progress
 * Transitions take 30 seconds
 * @param {number} startTime - Transition start time (ms)
 * @param {number} currentTime - Current time (ms)
 * @returns {number} Progress from 0 to 1
 */
function getColorTransitionProgress(startTime, currentTime) {
  const elapsed = currentTime - startTime;
  const progress = Math.min(elapsed / DURATIONS.colorTransition, 1);
  return progress;
}

/**
 * Get animation state for a given timestamp
 * Used to generate consistent animations for wallpapers
 * @param {number} timestamp - Unix timestamp in milliseconds
 * @param {string} urgency - Current urgency level
 * @returns {object} Animation state
 */
function getAnimationState(timestamp, urgency) {
  const breathParams = getBreathingParams(urgency);
  const breathPhase = calculateBreathPhase(timestamp, breathParams.cycleDuration);
  const breathScale = calculateBreathScale(breathPhase, breathParams.intensity);

  return {
    timestamp,
    breathPhase,
    breathScale,
    breathParams,
    // For particle animations
    particleTime: timestamp,
    // For color transitions (if transitioning)
    transitionProgress: 0
  };
}

module.exports = {
  EASING_CURVES,
  DURATIONS,
  calculateBreathPhase,
  calculateBreathScale,
  getBreathingParams,
  applyEasing,
  getColorTransitionProgress,
  getAnimationState
};
