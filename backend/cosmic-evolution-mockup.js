/**
 * COSMIC EVOLUTION MOCKUP GENERATOR
 *
 * Generates sample wallpapers showing achievement-based visual progression
 *
 * Levels:
 * 0: Void (0-9 tasks) - Dark empty space
 * 1: First Light (10-24 tasks) - Subtle purple nebula wisps
 * 2: Nebula Birth (25-49 tasks) - Bright nebula clouds + aurora
 * 3: Galaxy Core (50-99 tasks) - Golden galaxy spiral + shooting stars
 * 4: Galactic Empire (100-199 tasks) - Multiple nebulas + planets
 * 5: Cosmic Transcendence (200+ tasks) - Prismatic explosion
 */

const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

// ===================================
// COSMIC EVOLUTION ENGINE
// ===================================

const COSMIC_LEVELS = {
  0: { name: 'Void', minTasks: 0, colors: ['#000814', '#001d3d'], intensity: 0 },
  1: { name: 'First Light', minTasks: 10, colors: ['#001d3d', '#1a0066', '#4a148c'], intensity: 0.3 },
  2: { name: 'Nebula Birth', minTasks: 25, colors: ['#1a0066', '#4a148c', '#d500f9'], intensity: 0.6 },
  3: { name: 'Galaxy Core', minTasks: 50, colors: ['#4a148c', '#ff6f00', '#ffd700'], intensity: 0.8 },
  4: { name: 'Galactic Empire', minTasks: 100, colors: ['#ff6f00', '#1e88e5', '#9c27b0'], intensity: 1.0 },
  5: { name: 'Cosmic Transcendence', minTasks: 200, colors: ['#9c27b0', '#00bcd4', '#ff6f00', '#e91e63'], intensity: 1.5 }
};

function getCosmicLevel(totalCompleted) {
  if (totalCompleted >= 200) return 5;
  if (totalCompleted >= 100) return 4;
  if (totalCompleted >= 50) return 3;
  if (totalCompleted >= 25) return 2;
  if (totalCompleted >= 10) return 1;
  return 0;
}

// ===================================
// SVG GENERATORS FOR COSMIC LAYERS
// ===================================

/**
 * Generate nebula wisps (Level 1+)
 * Subtle purple/blue wisps floating in background
 */
function generateNebulaWisps(width, height, intensity) {
  const opacity = Math.min(0.4, intensity * 0.4);
  const cx1 = width * 0.3;
  const cy1 = height * 0.2;
  const cx2 = width * 0.7;
  const cy2 = height * 0.6;

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="wisp1" cx="30%" cy="20%">
          <stop offset="0%" style="stop-color:#4a148c;stop-opacity:${opacity}" />
          <stop offset="100%" style="stop-color:#4a148c;stop-opacity:0" />
        </radialGradient>
        <radialGradient id="wisp2" cx="70%" cy="60%">
          <stop offset="0%" style="stop-color:#1a0066;stop-opacity:${opacity * 0.8}" />
          <stop offset="100%" style="stop-color:#1a0066;stop-opacity:0" />
        </radialGradient>
        <filter id="blur">
          <feGaussianBlur stdDeviation="80" />
        </filter>
      </defs>
      <ellipse cx="${cx1}" cy="${cy1}" rx="${width * 0.4}" ry="${height * 0.3}"
               fill="url(#wisp1)" filter="url(#blur)" />
      <ellipse cx="${cx2}" cy="${cy2}" rx="${width * 0.35}" ry="${height * 0.25}"
               fill="url(#wisp2)" filter="url(#blur)" />
    </svg>
  `;
}

/**
 * Generate nebula clouds (Level 2+)
 * Bright pink/purple nebula clouds with more intensity
 */
function generateNebulaClouds(width, height, intensity) {
  const opacity = Math.min(0.7, intensity * 0.7);

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="nebula1" cx="40%" cy="30%">
          <stop offset="0%" style="stop-color:#d500f9;stop-opacity:${opacity}" />
          <stop offset="50%" style="stop-color:#6a1b9a;stop-opacity:${opacity * 0.5}" />
          <stop offset="100%" style="stop-color:#4a148c;stop-opacity:0" />
        </radialGradient>
        <radialGradient id="nebula2" cx="60%" cy="70%">
          <stop offset="0%" style="stop-color:#9c27b0;stop-opacity:${opacity * 0.8}" />
          <stop offset="50%" style="stop-color:#4a148c;stop-opacity:${opacity * 0.4}" />
          <stop offset="100%" style="stop-color:#1a0066;stop-opacity:0" />
        </radialGradient>
        <filter id="nebulaBlur">
          <feGaussianBlur stdDeviation="100" />
        </filter>
      </defs>
      <ellipse cx="${width * 0.4}" cy="${height * 0.3}" rx="${width * 0.5}" ry="${height * 0.4}"
               fill="url(#nebula1)" filter="url(#nebulaBlur)" />
      <ellipse cx="${width * 0.6}" cy="${height * 0.7}" rx="${width * 0.45}" ry="${height * 0.35}"
               fill="url(#nebula2)" filter="url(#nebulaBlur)" />
    </svg>
  `;
}

/**
 * Generate aurora bands (Level 2+)
 * Flowing aurora curtains across the sky
 */
function generateAuroraBands(width, height, intensity, streakDays = 0) {
  const opacity = Math.min(0.5, intensity * 0.5);
  const bands = Math.min(5, Math.floor(streakDays / 3) + 2); // More bands for longer streaks

  let bandsSvg = '';
  for (let i = 0; i < bands; i++) {
    const y = (height * 0.2) + (i * height * 0.15);
    const waveOffset = i * 50;

    bandsSvg += `
      <path d="M 0,${y} Q ${width * 0.25},${y - 30 + waveOffset} ${width * 0.5},${y}
                T ${width},${y}"
            stroke="#00bcd4" stroke-width="3" fill="none"
            opacity="${opacity * (1 - i * 0.15)}" filter="url(#auroraGlow)" />
    `;
  }

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <filter id="auroraGlow">
          <feGaussianBlur stdDeviation="8" />
        </filter>
      </defs>
      ${bandsSvg}
    </svg>
  `;
}

/**
 * Generate galaxy spiral (Level 3+)
 * Golden galaxy core with spiral arms
 */
function generateGalaxySpiral(width, height, intensity) {
  const cx = width * 0.5;
  const cy = height * 0.4;
  const opacity = Math.min(0.8, intensity * 0.8);

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="galaxyCore" cx="50%" cy="50%">
          <stop offset="0%" style="stop-color:#ffd700;stop-opacity:${opacity}" />
          <stop offset="30%" style="stop-color:#ff6f00;stop-opacity:${opacity * 0.7}" />
          <stop offset="70%" style="stop-color:#4a148c;stop-opacity:${opacity * 0.3}" />
          <stop offset="100%" style="stop-color:#1a0066;stop-opacity:0" />
        </radialGradient>
        <filter id="galaxyGlow">
          <feGaussianBlur stdDeviation="30" />
        </filter>
      </defs>
      <!-- Galaxy core -->
      <circle cx="${cx}" cy="${cy}" r="${width * 0.3}"
              fill="url(#galaxyCore)" filter="url(#galaxyGlow)" />
      <!-- Central bright core -->
      <circle cx="${cx}" cy="${cy}" r="${width * 0.05}"
              fill="#ffffff" opacity="${opacity * 0.9}" filter="url(#galaxyGlow)" />
    </svg>
  `;
}

/**
 * Generate shooting stars (Level 3+)
 * Streaking stars across the sky
 */
function generateShootingStars(width, height, count = 5) {
  let stars = '';

  for (let i = 0; i < count; i++) {
    const startX = width * (0.1 + Math.random() * 0.8);
    const startY = height * (0.1 + Math.random() * 0.4);
    const endX = startX + (50 + Math.random() * 100);
    const endY = startY + (30 + Math.random() * 60);

    stars += `
      <line x1="${startX}" y1="${startY}" x2="${endX}" y2="${endY}"
            stroke="#ffffff" stroke-width="2" opacity="0.6"
            stroke-linecap="round" filter="url(#starGlow)" />
    `;
  }

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <filter id="starGlow">
          <feGaussianBlur stdDeviation="3" />
        </filter>
      </defs>
      ${stars}
    </svg>
  `;
}

/**
 * Generate multiple nebulas (Level 4+)
 * Layered nebula clouds in different colors
 */
function generateMultipleNebulas(width, height, intensity) {
  const opacity = Math.min(0.6, intensity * 0.6);

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="nebula3" cx="20%" cy="40%">
          <stop offset="0%" style="stop-color:#1e88e5;stop-opacity:${opacity * 0.7}" />
          <stop offset="100%" style="stop-color:#1e88e5;stop-opacity:0" />
        </radialGradient>
        <radialGradient id="nebula4" cx="80%" cy="60%">
          <stop offset="0%" style="stop-color:#ff6f00;stop-opacity:${opacity * 0.6}" />
          <stop offset="100%" style="stop-color:#ff6f00;stop-opacity:0" />
        </radialGradient>
        <filter id="multiNebulaBlur">
          <feGaussianBlur stdDeviation="120" />
        </filter>
      </defs>
      <ellipse cx="${width * 0.2}" cy="${height * 0.4}" rx="${width * 0.4}" ry="${height * 0.3}"
               fill="url(#nebula3)" filter="url(#multiNebulaBlur)" />
      <ellipse cx="${width * 0.8}" cy="${height * 0.6}" rx="${width * 0.35}" ry="${height * 0.25}"
               fill="url(#nebula4)" filter="url(#multiNebulaBlur)" />
    </svg>
  `;
}

/**
 * Generate planets (Level 4+)
 * Background planets with rings
 */
function generatePlanets(width, height) {
  const planet1X = width * 0.15;
  const planet1Y = height * 0.25;
  const planet1R = width * 0.08;

  const planet2X = width * 0.85;
  const planet2Y = height * 0.7;
  const planet2R = width * 0.06;

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="planet1Grad" cx="40%" cy="40%">
          <stop offset="0%" style="stop-color:#ff6f00;stop-opacity:0.8" />
          <stop offset="100%" style="stop-color:#d84315;stop-opacity:0.6" />
        </radialGradient>
        <radialGradient id="planet2Grad" cx="40%" cy="40%">
          <stop offset="0%" style="stop-color:#1e88e5;stop-opacity:0.7" />
          <stop offset="100%" style="stop-color:#0d47a1;stop-opacity:0.5" />
        </radialGradient>
        <filter id="planetGlow">
          <feGaussianBlur stdDeviation="10" />
        </filter>
      </defs>
      <!-- Planet 1 (with ring) -->
      <circle cx="${planet1X}" cy="${planet1Y}" r="${planet1R}"
              fill="url(#planet1Grad)" opacity="0.7" filter="url(#planetGlow)" />
      <ellipse cx="${planet1X}" cy="${planet1Y}" rx="${planet1R * 1.5}" ry="${planet1R * 0.3}"
               fill="none" stroke="#ff6f00" stroke-width="2" opacity="0.5" />

      <!-- Planet 2 -->
      <circle cx="${planet2X}" cy="${planet2Y}" r="${planet2R}"
              fill="url(#planet2Grad)" opacity="0.6" filter="url(#planetGlow)" />
    </svg>
  `;
}

/**
 * Generate cosmic rift (Level 5+)
 * Prismatic dimensional tear effect
 */
function generateCosmicRift(width, height, intensity) {
  const cx = width * 0.5;
  const cy = height * 0.3;
  const opacity = Math.min(0.9, intensity * 0.6);

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="rift1" cx="50%" cy="50%">
          <stop offset="0%" style="stop-color:#ffffff;stop-opacity:${opacity}" />
          <stop offset="20%" style="stop-color:#00bcd4;stop-opacity:${opacity * 0.8}" />
          <stop offset="40%" style="stop-color:#9c27b0;stop-opacity:${opacity * 0.6}" />
          <stop offset="60%" style="stop-color:#ff6f00;stop-opacity:${opacity * 0.4}" />
          <stop offset="80%" style="stop-color:#e91e63;stop-opacity:${opacity * 0.2}" />
          <stop offset="100%" style="stop-color:#e91e63;stop-opacity:0" />
        </radialGradient>
        <filter id="riftGlow">
          <feGaussianBlur stdDeviation="50" />
        </filter>
      </defs>
      <!-- Prismatic explosion -->
      <circle cx="${cx}" cy="${cy}" r="${width * 0.4}"
              fill="url(#rift1)" filter="url(#riftGlow)" opacity="0.8" />
      <circle cx="${cx}" cy="${cy}" r="${width * 0.1}"
              fill="#ffffff" opacity="${opacity * 0.7}" filter="url(#riftGlow)" />
    </svg>
  `;
}

/**
 * Generate quantum shimmer (Level 5+)
 * Shimmering particle distortions
 */
function generateQuantumShimmer(width, height) {
  let particles = '';

  for (let i = 0; i < 30; i++) {
    const x = width * Math.random();
    const y = height * Math.random();
    const r = 2 + Math.random() * 4;
    const colors = ['#00bcd4', '#9c27b0', '#ff6f00', '#e91e63'];
    const color = colors[Math.floor(Math.random() * colors.length)];

    particles += `
      <circle cx="${x}" cy="${y}" r="${r}" fill="${color}" opacity="0.6"
              filter="url(#shimmerGlow)" />
    `;
  }

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <filter id="shimmerGlow">
          <feGaussianBlur stdDeviation="5" />
        </filter>
      </defs>
      ${particles}
    </svg>
  `;
}

// ===================================
// MAIN MOCKUP GENERATOR
// ===================================

/**
 * Generate cosmic layers for a given level
 */
function generateCosmicLayers(level, width, height, stats) {
  const layers = [];
  const config = COSMIC_LEVELS[level];
  const intensity = config.intensity;

  if (level >= 1) {
    layers.push(generateNebulaWisps(width, height, intensity));
  }

  if (level >= 2) {
    layers.push(generateNebulaClouds(width, height, intensity));
    layers.push(generateAuroraBands(width, height, intensity, stats.streakDays));
  }

  if (level >= 3) {
    layers.push(generateGalaxySpiral(width, height, intensity));
    layers.push(generateShootingStars(width, height, stats.completedToday || 5));
  }

  if (level >= 4) {
    layers.push(generateMultipleNebulas(width, height, intensity));
    layers.push(generatePlanets(width, height));
  }

  if (level >= 5) {
    layers.push(generateCosmicRift(width, height, intensity));
    layers.push(generateQuantumShimmer(width, height));
  }

  return layers;
}

/**
 * Generate enhanced background with cosmic evolution
 */
function generateEnhancedBackground(width, height, level) {
  const config = COSMIC_LEVELS[level];
  const colors = config.colors;

  // Multi-color gradient based on achievement level
  let gradientStops = '';
  colors.forEach((color, index) => {
    const offset = (index / (colors.length - 1)) * 100;
    gradientStops += `<stop offset="${offset}%" style="stop-color:${color};stop-opacity:1" />`;
  });

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="bgGradient" cx="50%" cy="40%">
          ${gradientStops}
        </radialGradient>
      </defs>
      <rect width="${width}" height="${height}" fill="url(#bgGradient)" />
    </svg>
  `;
}

/**
 * Generate simple starfield
 */
function generateStarfield(width, height, count = 50) {
  let stars = '';

  for (let i = 0; i < count; i++) {
    const x = Math.random() * width;
    const y = Math.random() * height;
    const r = 1 + Math.random() * 2;
    const opacity = 0.3 + Math.random() * 0.7;

    stars += `<circle cx="${x}" cy="${y}" r="${r}" fill="#ffffff" opacity="${opacity}" />`;
  }

  return `
    <svg width="${width}" height="${height}">
      ${stars}
    </svg>
  `;
}

/**
 * Generate text overlay with level info
 */
function generateTextOverlay(width, height, level, totalCompleted) {
  const config = COSMIC_LEVELS[level];
  const centerY = height * 0.5;

  return `
    <svg width="${width}" height="${height}">
      <style>
        .level-title {
          fill: #ffffff;
          font-family: 'Inter', sans-serif;
          font-size: ${Math.floor(width * 0.08)}px;
          font-weight: 600;
          text-anchor: middle;
        }
        .level-subtitle {
          fill: rgba(255,255,255,0.7);
          font-family: 'Inter', sans-serif;
          font-size: ${Math.floor(width * 0.04)}px;
          text-anchor: middle;
        }
        .glow {
          filter: url(#textGlow);
        }
      </style>
      <defs>
        <filter id="textGlow">
          <feGaussianBlur stdDeviation="8" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>
      <text x="50%" y="${centerY - 30}" class="level-title glow">LEVEL ${level}: ${config.name.toUpperCase()}</text>
      <text x="50%" y="${centerY + 30}" class="level-subtitle glow">${totalCompleted} tasks completed</text>
      ${level < 5 ? `<text x="50%" y="${centerY + 80}" class="level-subtitle">${COSMIC_LEVELS[level + 1].minTasks - totalCompleted} more to unlock "${COSMIC_LEVELS[level + 1].name}"</text>` : ''}
    </svg>
  `;
}

/**
 * Generate complete mockup wallpaper
 */
async function generateMockup(level, totalCompleted, outputPath) {
  const width = 1080;
  const height = 1920;
  const stats = {
    streakDays: Math.min(30, Math.floor(totalCompleted / 5)),
    completedToday: Math.min(10, Math.floor(totalCompleted / 10))
  };

  const startTime = Date.now();

  try {
    const layers = [];

    // Layer 1: Enhanced background
    const bgSvg = generateEnhancedBackground(width, height, level);
    layers.push({ input: Buffer.from(bgSvg), blend: 'over' });

    // Layer 2: Cosmic evolution layers
    const cosmicLayers = generateCosmicLayers(level, width, height, stats);
    cosmicLayers.forEach(layerSvg => {
      layers.push({ input: Buffer.from(layerSvg), blend: 'screen' }); // 'screen' blend for luminous effect
    });

    // Layer 3: Starfield
    const starfieldSvg = generateStarfield(width, height, 80);
    layers.push({ input: Buffer.from(starfieldSvg), blend: 'over' });

    // Layer 4: Text overlay
    const textSvg = generateTextOverlay(width, height, level, totalCompleted);
    layers.push({ input: Buffer.from(textSvg), blend: 'over' });

    // Composite all layers
    const baseImage = sharp({
      create: {
        width,
        height,
        channels: 4,
        background: { r: 0, g: 0, b: 0, alpha: 1 }
      }
    });

    const buffer = await baseImage
      .composite(layers)
      .png()
      .toBuffer();

    // Save to file
    fs.writeFileSync(outputPath, buffer);

    const endTime = Date.now();
    const fileSize = fs.statSync(outputPath).size;

    return {
      success: true,
      generationTime: endTime - startTime,
      fileSize: fileSize,
      fileSizeKB: Math.round(fileSize / 1024)
    };

  } catch (error) {
    return {
      success: false,
      error: error.message
    };
  }
}

// ===================================
// MAIN EXECUTION
// ===================================

async function generateAllMockups() {
  console.log('\n🌌 COSMIC EVOLUTION MOCKUP GENERATOR\n');
  console.log('Generating 6 wallpapers showing achievement progression...\n');

  const outputDir = path.join(__dirname, 'mockups');

  // Create output directory
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir);
  }

  const mockups = [
    { level: 0, totalCompleted: 5, filename: '0-void.png' },
    { level: 1, totalCompleted: 15, filename: '1-first-light.png' },
    { level: 2, totalCompleted: 35, filename: '2-nebula-birth.png' },
    { level: 3, totalCompleted: 65, filename: '3-galaxy-core.png' },
    { level: 4, totalCompleted: 125, filename: '4-galactic-empire.png' },
    { level: 5, totalCompleted: 250, filename: '5-cosmic-transcendence.png' }
  ];

  console.log('┌─────────────────────────────────────────────────────────────┐');
  console.log('│ Level │ Name                  │ Tasks │ Time   │ Size     │');
  console.log('├─────────────────────────────────────────────────────────────┤');

  for (const mockup of mockups) {
    const outputPath = path.join(outputDir, mockup.filename);
    const result = await generateMockup(mockup.level, mockup.totalCompleted, outputPath);

    if (result.success) {
      const config = COSMIC_LEVELS[mockup.level];
      console.log(`│   ${mockup.level}   │ ${config.name.padEnd(21)} │ ${String(mockup.totalCompleted).padStart(5)} │ ${String(result.generationTime).padStart(5)}ms │ ${String(result.fileSizeKB).padStart(6)}KB │`);
    } else {
      console.log(`│   ${mockup.level}   │ ERROR: ${result.error.padEnd(45)} │`);
    }
  }

  console.log('└─────────────────────────────────────────────────────────────┘');
  console.log(`\n✨ Mockups saved to: ${outputDir}\n`);
  console.log('📝 Next steps:');
  console.log('   1. Open the mockups folder');
  console.log('   2. Compare the visual progression');
  console.log('   3. Share feedback on which levels look best');
  console.log('   4. Discuss any visual tweaks needed\n');
}

// Run if called directly
if (require.main === module) {
  generateAllMockups()
    .then(() => process.exit(0))
    .catch(err => {
      console.error('❌ Error:', err);
      process.exit(1);
    });
}

module.exports = {
  generateMockup,
  generateCosmicLayers,
  getCosmicLevel,
  COSMIC_LEVELS
};
