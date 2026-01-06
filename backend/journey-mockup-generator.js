/**
 * THE JOURNEY - REALISTIC ACHIEVEMENT MOCKUP GENERATOR
 *
 * 4 Realistic Background Concepts:
 * 1. Mountain Ascent - Base camp → Summit above clouds
 * 2. Skyline Evolution - Empty field → Metropolis
 * 3. Cosmic Horizon - Dark dawn → Aurora display
 * 4. Path to Light - Foggy path → Illuminated destination
 *
 * Each with subtle achievement overlays (particles, light rays, stats)
 */

const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const LEVELS = {
  0: { name: 'Beginning', minTasks: 0, progress: 0 },
  2: { name: 'Progressing', minTasks: 25, progress: 0.4 },
  5: { name: 'Mastery', minTasks: 200, progress: 1.0 }
};

// ============================================
// CONCEPT 1: MOUNTAIN ASCENT
// ============================================

function generateMountainAscent(width, height, level, totalCompleted) {
  const progress = LEVELS[level].progress;

  // Sky transforms from foggy valley to clear summit
  const skyColors = [
    { low: ['#4a5568', '#718096'], mid: ['#718096', '#a0aec0'], high: ['#cbd5e0', '#e2e8f0'] }, // Level 0: Foggy
    { low: ['#2d3748', '#4a5568'], mid: ['#5a7fa6', '#7ba3c7'], high: ['#a3c9e8', '#d4e9ff'] }, // Level 2: Clearing
    { low: ['#1a365d', '#2c5282'], mid: ['#f6ad55', '#ed8936'], high: ['#fbd38d', '#fef5e7'] }  // Level 5: Golden hour
  ];

  const colors = skyColors[level === 0 ? 0 : level === 2 ? 1 : 2];

  let svg = `<svg width="${width}" height="${height}">`;

  // Sky gradient (changes with altitude)
  svg += `
    <defs>
      <linearGradient id="sky" x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" style="stop-color:${colors.high[1]};stop-opacity:1" />
        <stop offset="50%" style="stop-color:${colors.mid[1]};stop-opacity:1" />
        <stop offset="100%" style="stop-color:${colors.low[1]};stop-opacity:1" />
      </linearGradient>
      <filter id="mountainBlur">
        <feGaussianBlur stdDeviation="${20 - progress * 15}" />
      </filter>
    </defs>
    <rect width="${width}" height="${height}" fill="url(#sky)" />
  `;

  // Clouds (below you at higher levels)
  const cloudY = height * (0.7 - progress * 0.4); // Clouds drop as you ascend
  const cloudOpacity = level === 5 ? 0.9 : 0.5;

  for (let i = 0; i < 5; i++) {
    const x = (width / 6) * i;
    const y = cloudY + (Math.sin(i) * 30);
    svg += `
      <ellipse cx="${x}" cy="${y}" rx="${120 + i * 20}" ry="${40 + i * 10}"
               fill="#ffffff" opacity="${cloudOpacity * (0.3 + Math.random() * 0.3)}"
               filter="url(#mountainBlur)" />
    `;
  }

  // Mountain peaks (distant at low level, close at high level)
  const mountainScale = 0.6 + progress * 0.4;
  const mountainSharpness = progress * 10;

  // Distant mountains
  svg += `
    <polygon points="0,${height} ${width * 0.3},${height * (0.5 + progress * 0.3)} ${width * 0.5},${height * (0.6 + progress * 0.2)} 0,${height}"
             fill="#4a5568" opacity="${0.4 + progress * 0.3}" />
    <polygon points="${width * 0.4},${height * (0.6 + progress * 0.2)} ${width * 0.7},${height * (0.4 + progress * 0.3)} ${width},${height} ${width * 0.4},${height}"
             fill="#2d3748" opacity="${0.5 + progress * 0.3}" />
  `;

  // Near mountains (sharper at summit)
  svg += `
    <polygon points="0,${height} ${width * 0.25},${height * (0.7 - progress * 0.1)} ${width * 0.45},${height} 0,${height}"
             fill="#1a202c" opacity="${0.7 + progress * 0.2}" />
    <polygon points="${width * 0.55},${height} ${width * 0.75},${height * (0.65 - progress * 0.1)} ${width},${height} ${width * 0.55},${height}"
             fill="#1a202c" opacity="${0.7 + progress * 0.2}" />
  `;

  svg += `</svg>`;
  return svg;
}

// ============================================
// CONCEPT 2: SKYLINE EVOLUTION
// ============================================

function generateSkylineEvolution(width, height, level, totalCompleted) {
  const progress = LEVELS[level].progress;

  // Sky: Dawn → Afternoon → Golden hour
  const skyColors = [
    ['#1e3a5f', '#4a5f7f', '#7a8fa5'], // Dawn
    ['#4a90c8', '#6ba8d9', '#a8d5f2'], // Afternoon
    ['#ff6b35', '#ffa500', '#ffd700']  // Golden hour
  ];

  const colors = skyColors[level === 0 ? 0 : level === 2 ? 1 : 2];

  let svg = `<svg width="${width}" height="${height}">`;

  // Sky gradient
  let skyGradient = '';
  colors.forEach((color, i) => {
    const offset = (i / (colors.length - 1)) * 100;
    skyGradient += `<stop offset="${offset}%" style="stop-color:${color};stop-opacity:1" />`;
  });

  svg += `
    <defs>
      <linearGradient id="sky" x1="0%" y1="0%" x2="0%" y2="100%">
        ${skyGradient}
      </linearGradient>
      <filter id="glow">
        <feGaussianBlur stdDeviation="4" />
      </filter>
    </defs>
    <rect width="${width}" height="${height}" fill="url(#sky)" />
  `;

  // Ground
  const groundY = height * 0.75;
  svg += `
    <rect x="0" y="${groundY}" width="${width}" height="${height - groundY}"
          fill="#1a1a1a" opacity="0.9" />
  `;

  // Buildings (more and taller with progress)
  const buildingCount = 3 + Math.floor(progress * 15); // 3 → 18 buildings
  const maxHeight = progress * 400; // Max building height

  for (let i = 0; i < buildingCount; i++) {
    const buildingWidth = 40 + Math.random() * 60;
    const buildingHeight = (50 + Math.random() * maxHeight);
    const x = (width / buildingCount) * i + (Math.random() * 30);
    const y = groundY - buildingHeight;

    // Building body
    svg += `
      <rect x="${x}" y="${y}" width="${buildingWidth}" height="${buildingHeight}"
            fill="#2d3748" opacity="${0.8 + Math.random() * 0.2}"
            stroke="#1a202c" stroke-width="2" />
    `;

    // Windows (more lit at higher levels)
    const windowRows = Math.floor(buildingHeight / 25);
    const windowCols = Math.floor(buildingWidth / 15);

    for (let row = 0; row < windowRows; row++) {
      for (let col = 0; col < windowCols; col++) {
        if (Math.random() < 0.3 + progress * 0.5) { // More windows lit with progress
          const wx = x + 8 + col * 15;
          const wy = y + 8 + row * 25;
          svg += `
            <rect x="${wx}" y="${wy}" width="8" height="12"
                  fill="${level === 5 ? '#ffd700' : '#fbbf24'}"
                  opacity="${0.6 + Math.random() * 0.4}"
                  filter="url(#glow)" />
          `;
        }
      }
    }
  }

  svg += `</svg>`;
  return svg;
}

// ============================================
// CONCEPT 3: COSMIC HORIZON
// ============================================

function generateCosmicHorizon(width, height, level, totalCompleted) {
  const progress = LEVELS[level].progress;

  // Realistic aurora photography style
  const horizonY = height * 0.7;

  let svg = `<svg width="${width}" height="${height}">`;

  // Sky: Dark → Breaking dawn → Full aurora
  const skyBase = level === 0 ? '#0a0e1a' : level === 2 ? '#1a1f3a' : '#0f1b2e';
  const skyMid = level === 0 ? '#1a1f3a' : level === 2 ? '#2d3561' : '#1e3a5f';
  const skyTop = level === 0 ? '#0f1419' : level === 2 ? '#1a2744' : '#2c4a6b';

  svg += `
    <defs>
      <linearGradient id="sky" x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" style="stop-color:${skyTop};stop-opacity:1" />
        <stop offset="60%" style="stop-color:${skyMid};stop-opacity:1" />
        <stop offset="100%" style="stop-color:${skyBase};stop-opacity:1" />
      </linearGradient>
      <filter id="auroraGlow">
        <feGaussianBlur stdDeviation="40" />
      </filter>
      <filter id="starGlow">
        <feGaussianBlur stdDeviation="2" />
      </filter>
    </defs>
    <rect width="${width}" height="${height}" fill="url(#sky)" />
  `;

  // Stars (more visible at higher levels)
  const starCount = 30 + Math.floor(progress * 70);
  for (let i = 0; i < starCount; i++) {
    const x = Math.random() * width;
    const y = Math.random() * horizonY;
    const r = 1 + Math.random() * 2;
    const opacity = 0.4 + Math.random() * 0.6;

    svg += `<circle cx="${x}" cy="${y}" r="${r}" fill="#ffffff" opacity="${opacity}" filter="url(#starGlow)" />`;
  }

  // Aurora bands (intensity increases with level)
  if (level >= 2) {
    const auroraOpacity = progress * 0.7;
    const bands = Math.floor(1 + progress * 4); // 1-5 aurora bands

    for (let i = 0; i < bands; i++) {
      const y = horizonY * (0.3 + i * 0.15);
      const color = i % 2 === 0 ? '#10b981' : '#06b6d4'; // Green and cyan

      svg += `
        <ellipse cx="${width / 2}" cy="${y}" rx="${width * 0.8}" ry="${80 + i * 20}"
                 fill="${color}" opacity="${auroraOpacity * (0.5 - i * 0.08)}"
                 filter="url(#auroraGlow)" />
      `;
    }

    // Pink aurora at highest level
    if (level === 5) {
      svg += `
        <ellipse cx="${width / 2}" cy="${horizonY * 0.5}" rx="${width * 0.6}" ry="100"
                 fill="#ec4899" opacity="${auroraOpacity * 0.5}"
                 filter="url(#auroraGlow)" />
      `;
    }
  }

  // Horizon line
  svg += `
    <line x1="0" y1="${horizonY}" x2="${width}" y2="${horizonY}"
          stroke="#374151" stroke-width="2" opacity="0.5" />
  `;

  // Ground (dark silhouette)
  svg += `
    <rect x="0" y="${horizonY}" width="${width}" height="${height - horizonY}"
          fill="#0a0a0a" opacity="0.95" />
  `;

  // Horizon glow (increases with level)
  if (level >= 2) {
    const glowColor = level === 5 ? '#fbbf24' : '#60a5fa';
    svg += `
      <ellipse cx="${width / 2}" cy="${horizonY}" rx="${width * 0.8}" ry="40"
               fill="${glowColor}" opacity="${progress * 0.4}"
               filter="url(#auroraGlow)" />
    `;
  }

  svg += `</svg>`;
  return svg;
}

// ============================================
// CONCEPT 4: PATH TO LIGHT
// ============================================

function generatePathToLight(width, height, level, totalCompleted) {
  const progress = LEVELS[level].progress;

  let svg = `<svg width="${width}" height="${height}">`;

  // Sky: Dark foggy → Clearing → Bright destination
  const skyColors = [
    ['#1a1a2e', '#2d2d44'], // Dark fog
    ['#2d3561', '#4a5a8a'], // Clearing
    ['#fbbf24', '#f59e0b', '#ea580c'] // Golden destination
  ];

  const colors = skyColors[level === 0 ? 0 : level === 2 ? 1 : 2];

  let skyGradient = '';
  colors.forEach((color, i) => {
    const offset = (i / (colors.length - 1)) * 100;
    skyGradient += `<stop offset="${offset}%" style="stop-color:${color};stop-opacity:1" />`;
  });

  svg += `
    <defs>
      <linearGradient id="sky" x1="0%" y1="0%" x2="0%" y2="100%">
        ${skyGradient}
      </linearGradient>
      <filter id="fog">
        <feGaussianBlur stdDeviation="${30 - progress * 25}" />
      </filter>
      <filter id="destinationGlow">
        <feGaussianBlur stdDeviation="60" />
      </filter>
    </defs>
    <rect width="${width}" height="${height}" fill="url(#sky)" />
  `;

  // Destination light (grows with progress)
  if (level >= 2) {
    const destSize = progress * 300;
    const destY = height * 0.3;
    const destOpacity = progress * 0.8;

    svg += `
      <circle cx="${width / 2}" cy="${destY}" r="${destSize}"
              fill="#fbbf24" opacity="${destOpacity}"
              filter="url(#destinationGlow)" />
    `;

    // Light rays at highest level
    if (level === 5) {
      for (let i = 0; i < 8; i++) {
        const angle = (i / 8) * Math.PI * 2;
        const rayLength = 400;
        const x1 = width / 2;
        const y1 = destY;
        const x2 = x1 + Math.cos(angle) * rayLength;
        const y2 = y1 + Math.sin(angle) * rayLength;

        svg += `
          <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"
                stroke="#fbbf24" stroke-width="${4 + Math.random() * 4}"
                opacity="${0.3 + Math.random() * 0.2}"
                filter="url(#destinationGlow)" />
        `;
      }
    }
  }

  // Ground with perspective
  const vanishingY = height * 0.45;

  // Dark ground
  svg += `
    <polygon points="0,${height} 0,${vanishingY} ${width},${vanishingY} ${width},${height}"
             fill="#1a1a1a" opacity="0.9" />
  `;

  // Path (perspective)
  const pathLeftBottom = width * 0.3;
  const pathRightBottom = width * 0.7;
  const pathLeftTop = width * 0.48;
  const pathRightTop = width * 0.52;

  svg += `
    <polygon points="${pathLeftBottom},${height} ${pathLeftTop},${vanishingY} ${pathRightTop},${vanishingY} ${pathRightBottom},${height}"
             fill="#4a5568" opacity="${0.6 + progress * 0.3}" />
  `;

  // Path center line
  svg += `
    <line x1="${width / 2}" y1="${vanishingY}" x2="${width / 2}" y2="${height}"
          stroke="#718096" stroke-width="4" opacity="${0.5 + progress * 0.3}"
          stroke-dasharray="20,20" />
  `;

  // Fog layers (dissipate with progress)
  const fogOpacity = 0.7 - progress * 0.6;
  if (fogOpacity > 0.1) {
    for (let i = 0; i < 3; i++) {
      const y = height * (0.4 + i * 0.15);
      svg += `
        <ellipse cx="${width / 2}" cy="${y}" rx="${width}" ry="${100 - i * 20}"
                 fill="#e5e7eb" opacity="${fogOpacity * (0.5 - i * 0.15)}"
                 filter="url(#fog)" />
      `;
    }
  }

  svg += `</svg>`;
  return svg;
}

// ============================================
// ACHIEVEMENT OVERLAY
// ============================================

function generateAchievementOverlay(width, height, level, totalCompleted, conceptName) {
  const progress = LEVELS[level].progress;

  let svg = `<svg width="${width}" height="${height}">`;

  svg += `
    <defs>
      <filter id="particleGlow">
        <feGaussianBlur stdDeviation="3" />
      </filter>
    </defs>
  `;

  // Floating achievement particles (increase with level)
  const particleCount = Math.floor(20 + progress * 80);
  for (let i = 0; i < particleCount; i++) {
    const x = Math.random() * width;
    const y = Math.random() * height * 0.8; // Keep particles in upper area
    const r = 1 + Math.random() * 3;
    const opacity = 0.3 + Math.random() * 0.5;

    svg += `<circle cx="${x}" cy="${y}" r="${r}" fill="#fbbf24" opacity="${opacity}" filter="url(#particleGlow)" />`;
  }

  // Subtle stats in corner
  const statsY = height * 0.05;
  const statsOpacity = 0.5 + progress * 0.3;

  svg += `
    <text x="${width * 0.9}" y="${statsY}"
          fill="#ffffff" opacity="${statsOpacity}"
          font-family="Inter, sans-serif" font-size="28" font-weight="600"
          text-anchor="end">
      ${totalCompleted}
    </text>
    <text x="${width * 0.9}" y="${statsY + 35}"
          fill="#ffffff" opacity="${statsOpacity * 0.7}"
          font-family="Inter, sans-serif" font-size="18"
          text-anchor="end">
      completed
    </text>
  `;

  svg += `</svg>`;
  return svg;
}

// ============================================
// TEXT OVERLAY
// ============================================

function generateTextOverlay(width, height, level, totalCompleted, conceptName) {
  const centerY = height * 0.88;
  const config = LEVELS[level];

  // Subtle motivational text
  const messages = {
    0: 'Your journey begins',
    2: 'Making progress',
    5: 'You are unstoppable'
  };

  const message = messages[level];

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <filter id="textGlow">
          <feGaussianBlur stdDeviation="6" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>
      <text x="50%" y="${centerY}"
            fill="#ffffff" opacity="0.9"
            font-family="Inter, sans-serif" font-size="${Math.floor(width * 0.04)}px" font-weight="300"
            text-anchor="middle"
            filter="url(#textGlow)">
        ${message}
      </text>
    </svg>
  `;
}

// ============================================
// MAIN GENERATOR
// ============================================

async function generateJourneyMockup(concept, level, totalCompleted, outputPath) {
  const width = 1080;
  const height = 1920;
  const startTime = Date.now();

  try {
    let backgroundSvg;

    switch(concept) {
      case 'mountain':
        backgroundSvg = generateMountainAscent(width, height, level, totalCompleted);
        break;
      case 'skyline':
        backgroundSvg = generateSkylineEvolution(width, height, level, totalCompleted);
        break;
      case 'cosmic':
        backgroundSvg = generateCosmicHorizon(width, height, level, totalCompleted);
        break;
      case 'path':
        backgroundSvg = generatePathToLight(width, height, level, totalCompleted);
        break;
      default:
        throw new Error(`Unknown concept: ${concept}`);
    }

    const overlaySvg = generateAchievementOverlay(width, height, level, totalCompleted, concept);
    const textSvg = generateTextOverlay(width, height, level, totalCompleted, concept);

    const layers = [
      { input: Buffer.from(backgroundSvg), blend: 'over' },
      { input: Buffer.from(overlaySvg), blend: 'over' },
      { input: Buffer.from(textSvg), blend: 'over' }
    ];

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

// ============================================
// MAIN EXECUTION
// ============================================

async function generateAllJourneys() {
  console.log('\n🏔️  THE JOURNEY - REALISTIC ACHIEVEMENT MOCKUPS\n');
  console.log('Generating 4 realistic transformation concepts...\n');

  const outputDir = path.join(__dirname, 'journey-mockups');

  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir);
  }

  const concepts = [
    { name: 'mountain', label: 'Mountain Ascent' },
    { name: 'skyline', label: 'Skyline Evolution' },
    { name: 'cosmic', label: 'Cosmic Horizon' },
    { name: 'path', label: 'Path to Light' }
  ];

  for (const concept of concepts) {
    console.log(`\n🎯 CONCEPT: ${concept.label.toUpperCase()}`);
    console.log('─'.repeat(60));

    const levels = [
      { level: 0, totalCompleted: 5 },
      { level: 2, totalCompleted: 35 },
      { level: 5, totalCompleted: 250 }
    ];

    for (const mockup of levels) {
      const filename = `${concept.name}-level${mockup.level}.png`;
      const outputPath = path.join(outputDir, filename);
      const result = await generateJourneyMockup(concept.name, mockup.level, mockup.totalCompleted, outputPath);

      if (result.success) {
        console.log(`  Level ${mockup.level} (${mockup.totalCompleted} tasks) → ${result.generationTime}ms | ${result.fileSizeKB}KB`);
      } else {
        console.log(`  Level ${mockup.level} → ERROR: ${result.error}`);
      }
    }
  }

  console.log(`\n✨ Mockups saved to: ${outputDir}\n`);
  console.log('📝 Realistic backgrounds that transform as you achieve!\n');
}

if (require.main === module) {
  generateAllJourneys()
    .then(() => process.exit(0))
    .catch(err => {
      console.error('❌ Error:', err);
      process.exit(1);
    });
}

module.exports = {
  generateJourneyMockup,
  generateMountainAscent,
  generateSkylineEvolution,
  generateCosmicHorizon,
  generatePathToLight
};
