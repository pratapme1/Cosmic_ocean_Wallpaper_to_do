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
    count: 40,
    sizeRange: [2, 6],  // Increased for visibility on high-res
    twinkleSpeed: [3000, 5000], // ms
    driftSpeed: 0.5,
    opacity: [0.5, 0.9]  // Increased minimum opacity
  },
  attention: {
    count: 60,
    sizeRange: [3, 8],  // Increased for visibility
    twinkleSpeed: [2000, 4000],
    driftSpeed: 1.0,
    opacity: [0.6, 1.0]
  },
  urgent: {
    count: 80,
    sizeRange: [4, 10],  // Increased for visibility
    twinkleSpeed: [1000, 3000],
    driftSpeed: 2.0,
    opacity: [0.7, 1.0]
  },
  critical: {
    count: 120,
    sizeRange: [5, 12],  // Increased for visibility
    twinkleSpeed: [500, 2000],
    driftSpeed: 3.0,
    opacity: [0.8, 1.0]
  },
  clear: {
    count: 50,
    sizeRange: [3, 7],  // Increased for visibility
    twinkleSpeed: [2000, 4000],
    driftSpeed: 0.3,
    opacity: [0.6, 1.0]
  }
};

/**
 * Particle parameters by urgency level for Ocean theme (Bubbles)
 */
const BUBBLE_PARAMS = {
  calm: {
    count: 30,
    sizeRange: [6, 16],  // Increased for visibility
    riseSpeed: 20, // pixels per second
    wobble: 5,
    opacity: [0.4, 0.7]  // Increased minimum opacity
  },
  attention: {
    count: 45,
    sizeRange: [8, 20],  // Increased for visibility
    riseSpeed: 35,
    wobble: 8,
    opacity: [0.5, 0.8]
  },
  urgent: {
    count: 60,
    sizeRange: [10, 24],  // Increased for visibility
    riseSpeed: 50,
    wobble: 12,
    opacity: [0.6, 0.9]
  },
  critical: {
    count: 90,
    sizeRange: [12, 28],  // Increased for visibility
    riseSpeed: 70,
    wobble: 16,
    opacity: [0.7, 1.0]
  },
  clear: {
    count: 40,
    sizeRange: [8, 18],  // Increased for visibility
    riseSpeed: 25,
    wobble: 6,
    opacity: [0.5, 0.8]
  }
};

/**
 * Zone-based particle density weights
 * Particles are less dense in task area to maintain readability
 * Increased weights to ensure more particles are visible
 */
const ZONE_WEIGHTS = {
  clock: 0.6,      // More particles in clock area
  scene: 1.0,      // Full density in scene area
  transition: 0.8, // Good density in transition
  task: 0.4,       // Some particles in task area (keep readable)
  interaction: 0.3 // Minimal in bottom navigation area
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
 * @param {object} overrides - Optional overrides from intelligence layer
 * @returns {array} Array of particle objects
 */
function generateParticles(theme, layout, urgency, timestamp = 0, overrides = {}) {
  // Apply intelligence layer overrides
  const particleCountOverride = overrides.particleCount || null;

  let particles;
  switch (theme) {
    case 'cosmic':
      particles = generateStarsWithOverrides(layout, urgency, timestamp, particleCountOverride);
      break;
    case 'ocean':
      particles = generateBubblesWithOverrides(layout, urgency, timestamp, particleCountOverride);
      break;
    case 'fantasy':
      // Fantasy theme uses stars with purple color
      particles = generateStarsWithOverrides(layout, urgency, timestamp, particleCountOverride).map(star => ({
        ...star,
        color: '#B8A0FF' // Purple sparkles
      }));
      break;
    default:
      particles = generateStarsWithOverrides(layout, urgency, timestamp, particleCountOverride);
  }

  return particles;
}

/**
 * Generate stars with optional count override from intelligence layer
 */
function generateStarsWithOverrides(layout, urgency, timestamp, countOverride) {
  const params = { ...STAR_PARAMS[urgency] || STAR_PARAMS.calm };
  if (countOverride) {
    params.count = countOverride;
  }
  return generateStarsWithParams(layout, params, timestamp);
}

/**
 * Generate bubbles with optional count override from intelligence layer
 */
function generateBubblesWithOverrides(layout, urgency, timestamp, countOverride) {
  const params = { ...BUBBLE_PARAMS[urgency] || BUBBLE_PARAMS.calm };
  if (countOverride) {
    params.count = countOverride;
  }
  return generateBubblesWithParams(layout, params, timestamp);
}

/**
 * Generate stars with explicit params (for override support)
 */
function generateStarsWithParams(layout, params, timestamp) {
  const { width, height, layoutZones } = layout;
  const stars = [];
  const zones = ['clock', 'scene', 'transition', 'task', 'interaction'];

  for (let i = 0; i < params.count; i++) {
    const seed = i * 7919;
    const zoneIndex = Math.floor((i / params.count) * zones.length);
    const zoneName = zones[zoneIndex];
    const zone = layoutZones[zoneName];
    const weight = ZONE_WEIGHTS[zoneName];

    if (seededRandom(seed) > weight) continue;

    const x = randomRange(layout.margins.horizontal, width - layout.margins.horizontal, seededRandom(seed + 1));
    const y = randomRange(zone.y, zone.y + zone.height, seededRandom(seed + 2));
    const size = randomRange(params.sizeRange[0], params.sizeRange[1], seededRandom(seed + 3));
    const twinkleDuration = randomRange(params.twinkleSpeed[0], params.twinkleSpeed[1], seededRandom(seed + 4));
    const twinklePhase = ((timestamp + i * 100) % twinkleDuration) / twinkleDuration;
    const twinkleOpacity = Math.sin(twinklePhase * Math.PI * 2) * 0.5 + 0.5;
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
 * Generate bubbles with explicit params (for override support)
 */
function generateBubblesWithParams(layout, params, timestamp) {
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

    const baseX = randomRange(layout.margins.horizontal, width - layout.margins.horizontal, seededRandom(seed + 1));
    const wobbleOffset = Math.sin((timestamp / 1000 + i) * 2) * params.wobble;
    const x = baseX + wobbleOffset;

    const riseOffset = (timestamp / 1000 * params.riseSpeed + i * 50) % height;
    const baseY = zone.y + zone.height - riseOffset;
    const y = ((baseY - zone.y) % zone.height) + zone.y;

    const size = randomRange(params.sizeRange[0], params.sizeRange[1], seededRandom(seed + 3));
    const baseOpacity = randomRange(params.opacity[0], params.opacity[1], seededRandom(seed + 4));
    const fadeIn = Math.min(1, (y - zone.y) / 50);
    const fadeOut = Math.min(1, (zone.y + zone.height - y) / 50);
    const opacity = baseOpacity * fadeIn * fadeOut;

    bubbles.push({
      x: Math.floor(x),
      y: Math.floor(y),
      size: Math.floor(size),
      opacity,
      color: 'rgba(100, 200, 255, 0.6)'
    });
  }

  return bubbles;
}

// ============================================
// ENHANCED PARTICLE TYPES (Epic 10 Phase 3)
// ============================================

/**
 * Generate sunrise/sunset ray particles
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Ray configuration from environment
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of ray objects
 */
function generateSunRays(layout, config = {}, timestamp = 0) {
  const {
    count = 8,
    color = '#ffd700',
    opacity = 0.3,
    position = { x: 0.5, y: 0.2 },
  } = config;

  const { width, height } = layout;
  const rays = [];

  const centerX = width * position.x;
  const centerY = height * position.y;

  for (let i = 0; i < count; i++) {
    const angle = (i / count) * Math.PI * 2;
    const rotationOffset = (timestamp / 50000) * Math.PI * 2; // Slow rotation
    const finalAngle = angle + rotationOffset;

    // Ray length varies with twinkle
    const twinkle = Math.sin((timestamp / 2000 + i) * Math.PI) * 0.3 + 0.7;
    const rayLength = Math.min(width, height) * 0.6 * twinkle;

    rays.push({
      type: 'ray',
      startX: centerX,
      startY: centerY,
      endX: centerX + Math.cos(finalAngle) * rayLength,
      endY: centerY + Math.sin(finalAngle) * rayLength,
      width: 15 + i % 3 * 5, // Varying widths
      color,
      opacity: opacity * twinkle,
      blur: 20,
    });
  }

  return rays;
}

/**
 * Generate cloud particles
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Cloud configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of cloud objects
 */
function generateClouds(layout, config = {}, timestamp = 0) {
  const {
    count = 5,
    color = '#ffffff',
    opacity = 0.4,
    speed = 0.2,
    coverage = 0.3,
  } = config;

  const { width, height } = layout;
  const clouds = [];

  for (let i = 0; i < count; i++) {
    const seed = i * 1337;

    // Horizontal drift
    const baseX = (seededRandom(seed) * width * 1.5) - width * 0.25;
    const drift = ((timestamp / 1000) * speed * 20 + i * 100) % (width * 1.5);
    const x = (baseX + drift) % (width * 1.5) - width * 0.25;

    // Vertical position (upper portion of screen)
    const y = seededRandom(seed + 1) * height * coverage;

    // Cloud size
    const cloudWidth = 100 + seededRandom(seed + 2) * 150;
    const cloudHeight = 40 + seededRandom(seed + 3) * 60;

    clouds.push({
      type: 'cloud',
      x,
      y,
      width: cloudWidth,
      height: cloudHeight,
      color,
      opacity: opacity * (0.7 + seededRandom(seed + 4) * 0.3),
      blur: 30,
      // Cloud made of multiple ellipses
      puffs: [
        { offsetX: 0, offsetY: 0, radiusX: cloudWidth * 0.4, radiusY: cloudHeight * 0.5 },
        { offsetX: cloudWidth * 0.25, offsetY: -10, radiusX: cloudWidth * 0.3, radiusY: cloudHeight * 0.4 },
        { offsetX: -cloudWidth * 0.2, offsetY: 5, radiusX: cloudWidth * 0.35, radiusY: cloudHeight * 0.45 },
      ],
    });
  }

  return clouds;
}

/**
 * Generate rain particles
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Rain configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of raindrop objects
 */
function generateRain(layout, config = {}, timestamp = 0) {
  const {
    count = 60,
    color = '#90caf9',
    speed = 1.5,
    angle = -10, // Degrees
    length = 15,
  } = config;

  const { width, height } = layout;
  const raindrops = [];
  const angleRad = (angle * Math.PI) / 180;

  for (let i = 0; i < count; i++) {
    const seed = i * 2749;

    // Random X position
    const x = seededRandom(seed) * width;

    // Y position with falling animation
    const fallSpeed = speed * 500;
    const baseY = seededRandom(seed + 1) * height;
    const y = (baseY + (timestamp / 1000) * fallSpeed) % (height + length);

    // Length variation
    const dropLength = length * (0.7 + seededRandom(seed + 2) * 0.6);

    raindrops.push({
      type: 'raindrop',
      x,
      y,
      length: dropLength,
      angle: angleRad,
      color,
      opacity: 0.5 + seededRandom(seed + 3) * 0.5,
      width: 1 + seededRandom(seed + 4),
    });
  }

  return raindrops;
}

/**
 * Generate lightning flash effect
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Lightning configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Object|null} Lightning bolt or null if not flashing
 */
function generateLightning(layout, config = {}, timestamp = 0) {
  const {
    frequency = 5000, // ms between flashes
    color = '#fff9c4',
    intensity = 0.8,
  } = config;

  const { width, height } = layout;

  // Determine if lightning should flash
  const flashCycle = timestamp % frequency;
  const isFlashing = flashCycle < 200; // 200ms flash duration

  if (!isFlashing) return null;

  // Flash brightness oscillation
  const flashProgress = flashCycle / 200;
  const brightness = Math.sin(flashProgress * Math.PI) * intensity;

  // Generate bolt path
  const startX = width * 0.3 + Math.random() * width * 0.4;
  const segments = [];
  let currentX = startX;
  let currentY = 0;

  while (currentY < height * 0.7) {
    const nextX = currentX + (Math.random() - 0.5) * 100;
    const nextY = currentY + 30 + Math.random() * 50;

    segments.push({
      startX: currentX,
      startY: currentY,
      endX: nextX,
      endY: nextY,
    });

    currentX = nextX;
    currentY = nextY;

    // Occasionally branch
    if (Math.random() > 0.7) {
      const branchEndX = nextX + (Math.random() - 0.5) * 80;
      const branchEndY = nextY + 20 + Math.random() * 40;
      segments.push({
        startX: nextX,
        startY: nextY,
        endX: branchEndX,
        endY: branchEndY,
        isBranch: true,
      });
    }
  }

  return {
    type: 'lightning',
    segments,
    color,
    brightness,
    glowRadius: 50,
    screenFlash: brightness * 0.3, // Ambient flash
  };
}

/**
 * Generate sparkle/rainbow particles
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Sparkle configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of sparkle objects
 */
function generateSparkles(layout, config = {}, timestamp = 0) {
  const {
    count = 40,
    colors = ['#ffd700', '#ff69b4', '#00ff7f', '#87ceeb', '#ff6b6b'],
    speed = 0.6,
    glow = true,
    twinkle = true,
  } = config;

  const { width, height } = layout;
  const sparkles = [];

  for (let i = 0; i < count; i++) {
    const seed = i * 3571;

    // Position
    const x = seededRandom(seed) * width;
    const baseY = seededRandom(seed + 1) * height;

    // Floating animation
    const floatOffset = Math.sin((timestamp / 1000 + i) * speed) * 20;
    const y = baseY + floatOffset;

    // Twinkle effect
    let opacity = 0.7 + seededRandom(seed + 2) * 0.3;
    if (twinkle) {
      const twinklePhase = Math.sin((timestamp / 500 + i * 0.5) * Math.PI);
      opacity *= 0.5 + twinklePhase * 0.5;
    }

    // Color cycling
    const colorIndex = Math.floor(seededRandom(seed + 3) * colors.length);

    // Size with pulse
    const baseSize = 2 + seededRandom(seed + 4) * 4;
    const pulseSize = twinkle ? baseSize * (0.8 + Math.sin((timestamp / 300 + i) * Math.PI) * 0.4) : baseSize;

    sparkles.push({
      type: 'sparkle',
      x,
      y,
      size: pulseSize,
      color: colors[colorIndex],
      opacity,
      glow: glow ? pulseSize * 3 : 0,
      rotation: (timestamp / 1000 + i) * 0.5, // Slow rotation
    });
  }

  return sparkles;
}

/**
 * Generate mist/fog particles
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Mist configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of mist objects
 */
function generateMist(layout, config = {}, timestamp = 0) {
  const {
    count = 20,
    color = '#e0e0e0',
    speed = 0.1,
  } = config;

  const { width, height } = layout;
  const mist = [];

  for (let i = 0; i < count; i++) {
    const seed = i * 4231;

    // Slow horizontal drift
    const baseX = seededRandom(seed) * width * 1.5 - width * 0.25;
    const drift = ((timestamp / 1000) * speed * 10) % (width * 1.5);
    const x = (baseX + drift) % (width * 1.5) - width * 0.25;

    // Vertical position
    const y = seededRandom(seed + 1) * height;

    // Large, soft blobs
    const size = 100 + seededRandom(seed + 2) * 200;

    // Opacity varies with "breathing"
    const breathe = Math.sin((timestamp / 3000 + i) * Math.PI) * 0.1 + 0.9;
    const opacity = (0.1 + seededRandom(seed + 3) * 0.15) * breathe;

    mist.push({
      type: 'mist',
      x,
      y,
      size,
      color,
      opacity,
      blur: size * 0.5,
    });
  }

  return mist;
}

/**
 * Generate dust motes (warm floating particles)
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Dust configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of dust mote objects
 */
function generateDustMotes(layout, config = {}, timestamp = 0) {
  const {
    count = 15,
    color = '#ffe0b2',
    speed = 0.3,
  } = config;

  const { width, height } = layout;
  const motes = [];

  for (let i = 0; i < count; i++) {
    const seed = i * 5023;

    // Gentle floating motion
    const baseX = seededRandom(seed) * width;
    const baseY = seededRandom(seed + 1) * height;

    const floatX = Math.sin((timestamp / 2000 + i * 0.7) * Math.PI) * 30;
    const floatY = Math.cos((timestamp / 2500 + i * 0.5) * Math.PI) * 20;

    const x = baseX + floatX;
    const y = baseY + floatY;

    // Size
    const size = 1 + seededRandom(seed + 2) * 3;

    // Subtle visibility variation
    const visibility = Math.sin((timestamp / 1500 + i) * Math.PI) * 0.3 + 0.7;
    const opacity = (0.4 + seededRandom(seed + 3) * 0.4) * visibility;

    motes.push({
      type: 'dustMote',
      x,
      y,
      size,
      color,
      opacity,
      glow: size * 2,
    });
  }

  return motes;
}

/**
 * Generate environment-specific particles
 * @param {string} particleType - Type of particle effect
 * @param {Object} layout - Layout configuration
 * @param {Object} config - Particle configuration
 * @param {number} timestamp - Animation timestamp
 * @returns {Array} Array of particle objects
 */
function generateEnvironmentParticles(particleType, layout, config = {}, timestamp = 0) {
  switch (particleType) {
    case 'sunrise_rays':
    case 'sun_rays':
      return generateSunRays(layout, config, timestamp);

    case 'clouds':
    case 'heavy_clouds':
    case 'storm_clouds':
      return generateClouds(layout, config, timestamp);

    case 'rain':
      return generateRain(layout, config, timestamp);

    case 'lightning':
      const lightning = generateLightning(layout, config, timestamp);
      return lightning ? [lightning] : [];

    case 'sparkles':
    case 'rainbow_sparkles':
      return generateSparkles(layout, config, timestamp);

    case 'mist':
    case 'fog':
      return generateMist(layout, config, timestamp);

    case 'dust_motes':
    case 'warm_drift':
    case 'light_dust':
      return generateDustMotes(layout, config, timestamp);

    case 'stars':
      return generateStars(layout, 'clear', timestamp);

    case 'floating_specks':
    case 'light_beams':
      return generateDustMotes(layout, { ...config, count: config.count || 8 }, timestamp);

    default:
      console.warn(`[Particles] Unknown type: ${particleType}`);
      return [];
  }
}

module.exports = {
  // Original exports
  generateStars,
  generateBubbles,
  generateParticles,
  STAR_PARAMS,
  BUBBLE_PARAMS,
  ZONE_WEIGHTS,

  // Enhanced particle exports (Epic 10 Phase 3)
  generateSunRays,
  generateClouds,
  generateRain,
  generateLightning,
  generateSparkles,
  generateMist,
  generateDustMotes,
  generateEnvironmentParticles,
};
