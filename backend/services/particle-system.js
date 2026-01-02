/**
 * Particle System Generator
 * Creates star fields, bubbles, and other particle effects
 * Based on ANDROID_UI_UX_DESIGN_SYSTEM.md specifications
 */

/**
 * Particle parameters by urgency level for Cosmic theme (Stars)
 */
const STAR_PARAMS = {
  calm: {
    count: 30,
    sizeRange: [1, 3],
    twinkleSpeed: [3000, 5000], // ms
    driftSpeed: 0.5,
    opacity: [0.3, 0.8]
  },
  attention: {
    count: 50,
    sizeRange: [2, 4],
    twinkleSpeed: [2000, 4000],
    driftSpeed: 1.0,
    opacity: [0.4, 0.9]
  },
  urgent: {
    count: 70,
    sizeRange: [2, 5],
    twinkleSpeed: [1000, 3000],
    driftSpeed: 2.0,
    opacity: [0.5, 1.0]
  },
  critical: {
    count: 100,
    sizeRange: [3, 6],
    twinkleSpeed: [500, 2000],
    driftSpeed: 3.0,
    opacity: [0.6, 1.0]
  },
  clear: {
    count: 50,
    sizeRange: [2, 4],
    twinkleSpeed: [2000, 4000],
    driftSpeed: 0.3,
    opacity: [0.5, 1.0]
  }
};

/**
 * Particle parameters by urgency level for Ocean theme (Bubbles)
 */
const BUBBLE_PARAMS = {
  calm: {
    count: 20,
    sizeRange: [4, 12],
    riseSpeed: 20, // pixels per second
    wobble: 5,
    opacity: [0.2, 0.6]
  },
  attention: {
    count: 35,
    sizeRange: [6, 16],
    riseSpeed: 35,
    wobble: 8,
    opacity: [0.3, 0.7]
  },
  urgent: {
    count: 50,
    sizeRange: [8, 20],
    riseSpeed: 50,
    wobble: 12,
    opacity: [0.4, 0.8]
  },
  critical: {
    count: 80,
    sizeRange: [10, 24],
    riseSpeed: 70,
    wobble: 16,
    opacity: [0.5, 0.9]
  },
  clear: {
    count: 30,
    sizeRange: [6, 14],
    riseSpeed: 25,
    wobble: 6,
    opacity: [0.3, 0.7]
  }
};

/**
 * Zone-based particle density weights
 * Particles are less dense in task area to maintain readability
 */
const ZONE_WEIGHTS = {
  clock: 0.3,
  scene: 1.0,
  transition: 0.5,
  task: 0.2,
  interaction: 0.1
};

/**
 * Random number generator with seed support
 */
function seededRandom(seed) {
  const x = Math.sin(seed++) * 10000;
  return x - Math.floor(x);
}

/**
 * Random value between min and max
 */
function randomRange(min, max, seed = Math.random()) {
  return min + seed * (max - min);
}

/**
 * Generate star particles for cosmic theme
 * @param {object} layout - Layout configuration
 * @param {string} urgency - Urgency level
 * @param {number} timestamp - Animation timestamp (for breathing/twinkling)
 * @returns {array} Array of star particle objects
 */
function generateStars(layout, urgency, timestamp = 0) {
  const params = STAR_PARAMS[urgency] || STAR_PARAMS.calm;
  const { width, height, layoutZones } = layout;
  const stars = [];

  // Distribute particles across zones with density weights
  const zones = ['clock', 'scene', 'transition', 'task', 'interaction'];

  for (let i = 0; i < params.count; i++) {
    // Deterministic positioning with some variation
    const seed = i * 7919; // Prime number for good distribution

    // Choose zone based on index and weights
    const zoneIndex = Math.floor((i / params.count) * zones.length);
    const zoneName = zones[zoneIndex];
    const zone = layoutZones[zoneName];
    const weight = ZONE_WEIGHTS[zoneName];

    // Skip some particles in low-density zones
    if (seededRandom(seed) > weight) continue;

    // Position within zone
    const x = randomRange(layout.margins.horizontal, width - layout.margins.horizontal, seededRandom(seed + 1));
    const y = randomRange(zone.y, zone.y + zone.height, seededRandom(seed + 2));

    // Size
    const size = randomRange(params.sizeRange[0], params.sizeRange[1], seededRandom(seed + 3));

    // Twinkle phase (for animation)
    const twinkleDuration = randomRange(params.twinkleSpeed[0], params.twinkleSpeed[1], seededRandom(seed + 4));
    const twinklePhase = ((timestamp + i * 100) % twinkleDuration) / twinkleDuration;
    const twinkleOpacity = Math.sin(twinklePhase * Math.PI * 2) * 0.5 + 0.5;

    // Final opacity
    const baseOpacity = randomRange(params.opacity[0], params.opacity[1], seededRandom(seed + 5));
    const opacity = baseOpacity * twinkleOpacity;

    stars.push({
      x: Math.floor(x),
      y: Math.floor(y),
      size: Math.floor(size),
      opacity,
      twinklePhase,
      color: '#FFFFFF'
    });
  }

  return stars;
}

/**
 * Generate bubble particles for ocean theme
 * @param {object} layout - Layout configuration
 * @param {string} urgency - Urgency level
 * @param {number} timestamp - Animation timestamp
 * @returns {array} Array of bubble particle objects
 */
function generateBubbles(layout, urgency, timestamp = 0) {
  const params = BUBBLE_PARAMS[urgency] || BUBBLE_PARAMS.calm;
  const { width, height, layoutZones } = layout;
  const bubbles = [];

  const zones = ['clock', 'scene', 'transition', 'task', 'interaction'];

  for (let i = 0; i < params.count; i++) {
    const seed = i * 7919;

    const zoneIndex = Math.floor((i / params.count) * zones.length);
    const zoneName = zones[zoneIndex];
    const zone = layoutZones[zoneName];
    const weight = ZONE_WEIGHTS[zoneName];

    if (seededRandom(seed) > weight) continue;

    // X position with wobble
    const baseX = randomRange(layout.margins.horizontal, width - layout.margins.horizontal, seededRandom(seed + 1));
    const wobbleOffset = Math.sin((timestamp / 1000 + i) * 2) * params.wobble;
    const x = baseX + wobbleOffset;

    // Y position (rises over time)
    const riseOffset = ((timestamp / 1000) * params.riseSpeed + i * 50) % height;
    const baseY = randomRange(zone.y, zone.y + zone.height, seededRandom(seed + 2));
    const y = (baseY + riseOffset) % height;

    // Size
    const size = randomRange(params.sizeRange[0], params.sizeRange[1], seededRandom(seed + 3));

    // Opacity
    const opacity = randomRange(params.opacity[0], params.opacity[1], seededRandom(seed + 4));

    bubbles.push({
      x: Math.floor(x),
      y: Math.floor(y),
      size: Math.floor(size),
      opacity,
      color: 'rgba(224, 247, 250, 1)' // Light cyan for bubbles
    });
  }

  return bubbles;
}

/**
 * Generate particles based on theme
 * @param {string} theme - Theme name (cosmic, ocean, fantasy)
 * @param {object} layout - Layout configuration
 * @param {string} urgency - Urgency level
 * @param {number} timestamp - Animation timestamp
 * @returns {array} Array of particle objects
 */
function generateParticles(theme, layout, urgency, timestamp = 0) {
  switch (theme) {
    case 'cosmic':
      return generateStars(layout, urgency, timestamp);
    case 'ocean':
      return generateBubbles(layout, urgency, timestamp);
    case 'fantasy':
      // Fantasy theme could use sparkles or magical particles
      // For now, use stars with different colors
      return generateStars(layout, urgency, timestamp).map(star => ({
        ...star,
        color: '#B8A0FF' // Purple sparkles
      }));
    default:
      return generateStars(layout, urgency, timestamp);
  }
}

module.exports = {
  generateStars,
  generateBubbles,
  generateParticles,
  STAR_PARAMS,
  BUBBLE_PARAMS,
  ZONE_WEIGHTS
};
