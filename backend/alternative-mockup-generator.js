/**
 * ALTERNATIVE ACHIEVEMENT MOCKUP GENERATOR
 *
 * 4 Completely Different Visual Concepts:
 *
 * 1. ENERGY FIELD - Abstract particle energy that intensifies
 * 2. SACRED GEOMETRY - Mandala patterns that grow complex
 * 3. ZEN GARDEN - Minimalist Japanese aesthetic
 * 4. TREE OF LIFE - Organic growth from seed to massive tree
 */

const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const LEVELS = {
  0: { name: 'Silent', minTasks: 0 },
  1: { name: 'Awakening', minTasks: 10 },
  2: { name: 'Growing', minTasks: 25 },
  3: { name: 'Flourishing', minTasks: 50 },
  4: { name: 'Magnificent', minTasks: 100 },
  5: { name: 'Transcendent', minTasks: 200 }
};

// ============================================
// CONCEPT 1: ENERGY FIELD
// Living particle field that intensifies
// ============================================

function generateEnergyField(width, height, level, totalCompleted) {
  // Energy parameters scale with level
  const particleCount = 50 + (level * 100); // 50 → 550 particles
  const energyIntensity = 0.3 + (level * 0.14); // 0.3 → 1.0
  const fieldSpeed = level * 0.2; // Movement speed

  // Gradient intensity based on level
  const gradientStops = [
    { level: 0, colors: ['#0a0a0a', '#1a1a2e'] },
    { level: 1, colors: ['#1a1a2e', '#16213e', '#0f3460'] },
    { level: 2, colors: ['#0f3460', '#533483', '#7b2cbf'] },
    { level: 3, colors: ['#7b2cbf', '#c77dff', '#e0aaff'] },
    { level: 4, colors: ['#e0aaff', '#ff006e', '#ffbe0b'] },
    { level: 5, colors: ['#ffbe0b', '#fb5607', '#ff006e', '#8338ec'] }
  ];

  const colors = gradientStops[level].colors;

  // Generate gradient
  let gradientStopsStr = '';
  colors.forEach((color, i) => {
    const offset = (i / (colors.length - 1)) * 100;
    gradientStopsStr += `<stop offset="${offset}%" style="stop-color:${color};stop-opacity:1" />`;
  });

  // Generate energy particles
  let particles = '';
  const timestamp = Date.now();

  for (let i = 0; i < particleCount; i++) {
    const seed = i * 7919;
    const x = (Math.sin(seed) * 0.5 + 0.5) * width;
    const y = (Math.cos(seed * 1.3) * 0.5 + 0.5) * height;

    // Particle movement based on time
    const offsetX = Math.sin((timestamp / 1000 + i) * fieldSpeed) * 20;
    const offsetY = Math.cos((timestamp / 1000 + i * 1.5) * fieldSpeed) * 20;

    const finalX = x + offsetX;
    const finalY = y + offsetY;

    // Size scales with level
    const size = 1 + (level * 0.5) + (Math.random() * 2);
    const opacity = 0.3 + (energyIntensity * 0.6) * Math.random();

    // Color varies
    const colorIndex = Math.floor(Math.random() * colors.length);
    const color = colors[colorIndex];

    particles += `<circle cx="${finalX}" cy="${finalY}" r="${size}" fill="${color}" opacity="${opacity}" />`;
  }

  // Energy waves (more at higher levels)
  let waves = '';
  const waveCount = Math.min(5, level + 1);

  for (let i = 0; i < waveCount; i++) {
    const y = (height / (waveCount + 1)) * (i + 1);
    const amplitude = 30 + (level * 10);
    const waveOpacity = 0.1 + (level * 0.1);

    waves += `
      <path d="M 0,${y} Q ${width * 0.25},${y - amplitude} ${width * 0.5},${y} T ${width},${y}"
            stroke="${colors[Math.min(i, colors.length - 1)]}"
            stroke-width="${2 + level}"
            fill="none"
            opacity="${waveOpacity}"
            filter="url(#energyGlow)" />
    `;
  }

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="energyBg" cx="50%" cy="50%">
          ${gradientStopsStr}
        </radialGradient>
        <filter id="energyGlow">
          <feGaussianBlur stdDeviation="5" />
        </filter>
      </defs>
      <rect width="${width}" height="${height}" fill="url(#energyBg)" />
      ${waves}
      ${particles}
    </svg>
  `;
}

// ============================================
// CONCEPT 2: SACRED GEOMETRY
// Mandala patterns growing in complexity
// ============================================

function generateSacredGeometry(width, height, level, totalCompleted) {
  const cx = width / 2;
  const cy = height * 0.4;

  // Color palette based on level
  const palettes = [
    ['#1a1a2e', '#16213e'], // Dark
    ['#0f3460', '#533483'], // Purple
    ['#7b2cbf', '#c77dff'], // Bright purple
    ['#e0aaff', '#ff006e'], // Pink
    ['#ffbe0b', '#fb5607'], // Orange/yellow
    ['#ff006e', '#8338ec', '#3a86ff'] // Rainbow
  ];

  const colors = palettes[level];
  const primaryColor = colors[0];
  const secondaryColor = colors[colors.length - 1];

  // Background
  let svg = `
    <svg width="${width}" height="${height}">
      <defs>
        <linearGradient id="geoBg" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" style="stop-color:#0a0a0a;stop-opacity:1" />
          <stop offset="100%" style="stop-color:#1a1a2e;stop-opacity:1" />
        </linearGradient>
        <filter id="geoGlow">
          <feGaussianBlur stdDeviation="3" />
        </filter>
      </defs>
      <rect width="${width}" height="${height}" fill="url(#geoBg)" />
  `;

  // Geometric layers (more complex at higher levels)
  const layers = level + 1;
  const maxRadius = Math.min(width, height) * 0.35;

  for (let layer = 0; layer < layers; layer++) {
    const radius = maxRadius * ((layer + 1) / layers);
    const sides = 3 + (layer * 2); // 3, 5, 7, 9, 11, 13 sides
    const rotation = (layer * 15) % 360;
    const opacity = 0.3 + (layer / layers) * 0.5;

    // Generate polygon points
    let points = '';
    for (let i = 0; i < sides; i++) {
      const angle = (i / sides) * Math.PI * 2 + (rotation * Math.PI / 180);
      const x = cx + Math.cos(angle) * radius;
      const y = cy + Math.sin(angle) * radius;
      points += `${x},${y} `;
    }

    const color = layer % 2 === 0 ? primaryColor : secondaryColor;

    svg += `
      <polygon points="${points}"
               fill="none"
               stroke="${color}"
               stroke-width="${2 + layer * 0.5}"
               opacity="${opacity}"
               filter="url(#geoGlow)" />
    `;

    // Add circles at vertices
    for (let i = 0; i < sides; i++) {
      const angle = (i / sides) * Math.PI * 2 + (rotation * Math.PI / 180);
      const x = cx + Math.cos(angle) * radius;
      const y = cy + Math.sin(angle) * radius;

      svg += `<circle cx="${x}" cy="${y}" r="${3 + layer * 0.5}" fill="${secondaryColor}" opacity="${opacity * 1.2}" />`;
    }
  }

  // Central core
  svg += `
    <circle cx="${cx}" cy="${cy}" r="${15 + level * 5}"
            fill="${secondaryColor}"
            opacity="0.9"
            filter="url(#geoGlow)" />
  `;

  svg += `</svg>`;
  return svg;
}

// ============================================
// CONCEPT 3: ZEN GARDEN
// Minimalist elements accumulating
// ============================================

function generateZenGarden(width, height, level, totalCompleted) {
  const groundY = height * 0.7;

  let svg = `
    <svg width="${width}" height="${height}">
      <defs>
        <linearGradient id="zenBg" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" style="stop-color:#f5f5f0;stop-opacity:1" />
          <stop offset="100%" style="stop-color:#e8e8d8;stop-opacity:1" />
        </linearGradient>
        <filter id="zenShadow">
          <feDropShadow dx="2" dy="2" stdDeviation="4" flood-opacity="0.2"/>
        </filter>
      </defs>
      <rect width="${width}" height="${height}" fill="url(#zenBg)" />
  `;

  // Ground (sand)
  svg += `
    <rect x="0" y="${groundY}" width="${width}" height="${height - groundY}"
          fill="#d4c5a9" opacity="0.8" />
  `;

  // Zen circles in sand (more as level increases)
  const circleCount = level * 3;
  for (let i = 0; i < circleCount; i++) {
    const x = (width / (circleCount + 1)) * (i + 1);
    const y = groundY + 50 + (Math.sin(i) * 20);
    const r = 30 + (Math.cos(i * 2) * 10);

    svg += `
      <circle cx="${x}" cy="${y}" r="${r}"
              fill="none"
              stroke="#a89968"
              stroke-width="1"
              opacity="0.3" />
    `;
  }

  // Elements appear based on level
  if (level >= 1) {
    // Small rock
    svg += `
      <ellipse cx="${width * 0.3}" cy="${groundY - 20}" rx="40" ry="30"
               fill="#5a5a52"
               opacity="0.9"
               filter="url(#zenShadow)" />
    `;
  }

  if (level >= 2) {
    // Bamboo stalk
    const bambooX = width * 0.7;
    svg += `
      <rect x="${bambooX - 5}" y="${groundY - 150}" width="10" height="150"
            fill="#4a7c59"
            opacity="0.9"
            filter="url(#zenShadow)" />
      <line x1="${bambooX - 20}" y1="${groundY - 120}" x2="${bambooX + 20}" y2="${groundY - 120}"
            stroke="#3d6647" stroke-width="2" opacity="0.7" />
      <line x1="${bambooX - 20}" y1="${groundY - 80}" x2="${bambooX + 20}" y2="${groundY - 80}"
            stroke="#3d6647" stroke-width="2" opacity="0.7" />
    `;
  }

  if (level >= 3) {
    // Larger rocks
    svg += `
      <ellipse cx="${width * 0.5}" cy="${groundY - 30}" rx="60" ry="45"
               fill="#6a6a5a"
               opacity="0.9"
               filter="url(#zenShadow)" />
      <ellipse cx="${width * 0.55}" cy="${groundY - 15}" rx="45" ry="30"
               fill="#5a5a52"
               opacity="0.9"
               filter="url(#zenShadow)" />
    `;
  }

  if (level >= 4) {
    // Tree
    const treeX = width * 0.15;
    svg += `
      <!-- Trunk -->
      <rect x="${treeX - 15}" y="${groundY - 200}" width="30" height="200"
            fill="#3d2817"
            opacity="0.9"
            filter="url(#zenShadow)" />
      <!-- Foliage -->
      <circle cx="${treeX}" cy="${groundY - 220}" r="60"
              fill="#4a7c59"
              opacity="0.7"
              filter="url(#zenShadow)" />
      <circle cx="${treeX - 30}" cy="${groundY - 200}" r="45"
              fill="#3d6647"
              opacity="0.7"
              filter="url(#zenShadow)" />
      <circle cx="${treeX + 30}" cy="${groundY - 200}" r="45"
              fill="#3d6647"
              opacity="0.7"
              filter="url(#zenShadow)" />
    `;
  }

  if (level >= 5) {
    // Pagoda
    const pagodaX = width * 0.85;
    svg += `
      <!-- Base -->
      <rect x="${pagodaX - 40}" y="${groundY - 180}" width="80" height="180"
            fill="#8b4513"
            opacity="0.9"
            filter="url(#zenShadow)" />
      <!-- Roof layers -->
      <polygon points="${pagodaX - 60},${groundY - 180} ${pagodaX + 60},${groundY - 180} ${pagodaX + 50},${groundY - 200} ${pagodaX - 50},${groundY - 200}"
               fill="#d2691e"
               opacity="0.9"
               filter="url(#zenShadow)" />
      <polygon points="${pagodaX - 50},${groundY - 240} ${pagodaX + 50},${groundY - 240} ${pagodaX + 40},${groundY - 260} ${pagodaX - 40},${groundY - 260}"
               fill="#cd853f"
               opacity="0.9"
               filter="url(#zenShadow)" />
    `;
  }

  svg += `</svg>`;
  return svg;
}

// ============================================
// CONCEPT 4: TREE OF LIFE
// Organic growth from seed to massive tree
// ============================================

function generateTreeOfLife(width, height, level, totalCompleted) {
  const cx = width / 2;
  const groundY = height * 0.75;

  // Background gradient
  const skyColors = [
    ['#0a0a1a', '#1a1a2e'], // Night
    ['#1a1a2e', '#2d3561'], // Dawn
    ['#2d3561', '#4a5a8a'], // Morning
    ['#5a7db8', '#87ceeb'], // Day
    ['#87ceeb', '#ffd700'], // Golden hour
    ['#ff6b6b', '#ffd700', '#87ceeb'] // Magical
  ];

  const colors = skyColors[level];
  let gradientStops = '';
  colors.forEach((color, i) => {
    const offset = (i / (colors.length - 1)) * 100;
    gradientStops += `<stop offset="${offset}%" style="stop-color:${color};stop-opacity:1" />`;
  });

  let svg = `
    <svg width="${width}" height="${height}">
      <defs>
        <linearGradient id="sky" x1="0%" y1="0%" x2="0%" y2="100%">
          ${gradientStops}
        </linearGradient>
        <filter id="treeGlow">
          <feGaussianBlur stdDeviation="8" />
        </filter>
        <filter id="leafGlow">
          <feGaussianBlur stdDeviation="4" />
        </filter>
      </defs>
      <rect width="${width}" height="${height}" fill="url(#sky)" />
  `;

  // Ground
  svg += `
    <rect x="0" y="${groundY}" width="${width}" height="${height - groundY}"
          fill="#2d5016" opacity="0.9" />
  `;

  if (level === 0) {
    // Just a seed
    svg += `
      <ellipse cx="${cx}" cy="${groundY}" rx="15" ry="20"
               fill="#8b4513" opacity="0.8" />
    `;
  } else if (level === 1) {
    // Sprout
    svg += `
      <ellipse cx="${cx}" cy="${groundY}" rx="15" ry="20"
               fill="#8b4513" opacity="0.8" />
      <rect x="${cx - 3}" y="${groundY - 60}" width="6" height="60"
            fill="#4a7c59" opacity="0.9" />
      <ellipse cx="${cx - 15}" cy="${groundY - 60}" rx="12" ry="8"
               fill="#6b9b37" opacity="0.8" />
      <ellipse cx="${cx + 15}" cy="${groundY - 55}" rx="12" ry="8"
               fill="#6b9b37" opacity="0.8" />
    `;
  } else if (level === 2) {
    // Young tree
    const trunkWidth = 20;
    const trunkHeight = 150;
    svg += `
      <rect x="${cx - trunkWidth / 2}" y="${groundY - trunkHeight}"
            width="${trunkWidth}" height="${trunkHeight}"
            fill="#654321" opacity="0.9" />
      <circle cx="${cx}" cy="${groundY - trunkHeight - 30}" r="50"
              fill="#6b9b37" opacity="0.8" filter="url(#leafGlow)" />
      <circle cx="${cx - 30}" cy="${groundY - trunkHeight}" r="35"
              fill="#5a8a2f" opacity="0.8" filter="url(#leafGlow)" />
      <circle cx="${cx + 30}" cy="${groundY - trunkHeight}" r="35"
              fill="#5a8a2f" opacity="0.8" filter="url(#leafGlow)" />
    `;
  } else if (level === 3) {
    // Mature tree
    const trunkWidth = 40;
    const trunkHeight = 250;
    svg += `
      <rect x="${cx - trunkWidth / 2}" y="${groundY - trunkHeight}"
            width="${trunkWidth}" height="${trunkHeight}"
            fill="#654321" opacity="0.9" />
      <circle cx="${cx}" cy="${groundY - trunkHeight - 50}" r="80"
              fill="#7cb342" opacity="0.9" filter="url(#leafGlow)" />
      <circle cx="${cx - 60}" cy="${groundY - trunkHeight + 20}" r="60"
              fill="#6b9b37" opacity="0.9" filter="url(#leafGlow)" />
      <circle cx="${cx + 60}" cy="${groundY - trunkHeight + 20}" r="60"
              fill="#6b9b37" opacity="0.9" filter="url(#leafGlow)" />
      <circle cx="${cx - 50}" cy="${groundY - trunkHeight - 30}" r="55"
              fill="#558b2f" opacity="0.9" filter="url(#leafGlow)" />
      <circle cx="${cx + 50}" cy="${groundY - trunkHeight - 30}" r="55"
              fill="#558b2f" opacity="0.9" filter="url(#leafGlow)" />
    `;
  } else if (level === 4) {
    // Grand tree
    const trunkWidth = 60;
    const trunkHeight = 350;
    svg += `
      <rect x="${cx - trunkWidth / 2}" y="${groundY - trunkHeight}"
            width="${trunkWidth}" height="${trunkHeight}"
            fill="#654321" opacity="0.9" />
      <!-- Large canopy -->
      ${generateCanopy(cx, groundY - trunkHeight, 100, '#7cb342', 0.9)}
      ${generateCanopy(cx - 80, groundY - trunkHeight + 50, 75, '#6b9b37', 0.9)}
      ${generateCanopy(cx + 80, groundY - trunkHeight + 50, 75, '#6b9b37', 0.9)}
      ${generateCanopy(cx - 90, groundY - trunkHeight - 30, 65, '#558b2f', 0.9)}
      ${generateCanopy(cx + 90, groundY - trunkHeight - 30, 65, '#558b2f', 0.9)}
    `;
  } else if (level >= 5) {
    // Ancient glowing tree
    const trunkWidth = 80;
    const trunkHeight = 450;
    svg += `
      <rect x="${cx - trunkWidth / 2}" y="${groundY - trunkHeight}"
            width="${trunkWidth}" height="${trunkHeight}"
            fill="#8b6914" opacity="0.95" filter="url(#treeGlow)" />
      <!-- Massive golden canopy -->
      ${generateCanopy(cx, groundY - trunkHeight - 80, 130, '#ffd700', 0.95)}
      ${generateCanopy(cx - 100, groundY - trunkHeight + 30, 90, '#ffed4e', 0.9)}
      ${generateCanopy(cx + 100, groundY - trunkHeight + 30, 90, '#ffed4e', 0.9)}
      ${generateCanopy(cx - 120, groundY - trunkHeight - 50, 80, '#fff176', 0.9)}
      ${generateCanopy(cx + 120, groundY - trunkHeight - 50, 80, '#fff176', 0.9)}
      ${generateCanopy(cx, groundY - trunkHeight - 160, 100, '#ffeb3b', 0.95)}
      <!-- Glowing particles -->
      ${generateGlowingParticles(cx, groundY - trunkHeight - 100, 150, 30)}
    `;
  }

  svg += `</svg>`;
  return svg;
}

function generateCanopy(cx, cy, r, color, opacity) {
  return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${color}" opacity="${opacity}" filter="url(#leafGlow)" />`;
}

function generateGlowingParticles(cx, cy, radius, count) {
  let particles = '';
  for (let i = 0; i < count; i++) {
    const angle = (i / count) * Math.PI * 2;
    const distance = radius * (0.5 + Math.random() * 0.5);
    const x = cx + Math.cos(angle) * distance;
    const y = cy + Math.sin(angle) * distance;
    const r = 2 + Math.random() * 3;

    particles += `<circle cx="${x}" cy="${y}" r="${r}" fill="#fff9c4" opacity="0.8" filter="url(#leafGlow)" />`;
  }
  return particles;
}

// ============================================
// TEXT OVERLAY
// ============================================

function generateTextOverlay(width, height, level, totalCompleted, conceptName) {
  const centerY = height * 0.85;
  const config = LEVELS[level];

  // Text color varies by concept
  const textColors = {
    'energy': '#ffffff',
    'geometry': '#c77dff',
    'zen': '#3d2817',
    'tree': level >= 4 ? '#ffd700' : '#ffffff'
  };

  const textColor = textColors[conceptName] || '#ffffff';

  return `
    <svg width="${width}" height="${height}">
      <style>
        .level-title {
          fill: ${textColor};
          font-family: 'Inter', sans-serif;
          font-size: ${Math.floor(width * 0.06)}px;
          font-weight: 600;
          text-anchor: middle;
        }
        .level-subtitle {
          fill: ${textColor};
          font-family: 'Inter', sans-serif;
          font-size: ${Math.floor(width * 0.035)}px;
          text-anchor: middle;
          opacity: 0.8;
        }
        .glow {
          filter: url(#textGlow);
        }
      </style>
      <defs>
        <filter id="textGlow">
          <feGaussianBlur stdDeviation="4" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>
      <text x="50%" y="${centerY}" class="level-title glow">LEVEL ${level}: ${config.name.toUpperCase()}</text>
      <text x="50%" y="${centerY + 40}" class="level-subtitle">${totalCompleted} tasks completed</text>
    </svg>
  `;
}

// ============================================
// MAIN GENERATOR
// ============================================

async function generateConceptMockup(concept, level, totalCompleted, outputPath) {
  const width = 1080;
  const height = 1920;
  const startTime = Date.now();

  try {
    let baseSvg;

    switch(concept) {
      case 'energy':
        baseSvg = generateEnergyField(width, height, level, totalCompleted);
        break;
      case 'geometry':
        baseSvg = generateSacredGeometry(width, height, level, totalCompleted);
        break;
      case 'zen':
        baseSvg = generateZenGarden(width, height, level, totalCompleted);
        break;
      case 'tree':
        baseSvg = generateTreeOfLife(width, height, level, totalCompleted);
        break;
      default:
        throw new Error(`Unknown concept: ${concept}`);
    }

    const textSvg = generateTextOverlay(width, height, level, totalCompleted, concept);

    const layers = [
      { input: Buffer.from(baseSvg), blend: 'over' },
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

async function generateAllConcepts() {
  console.log('\n🎨 ALTERNATIVE ACHIEVEMENT MOCKUP GENERATOR\n');
  console.log('Generating 4 DIFFERENT visual concepts...\n');

  const outputDir = path.join(__dirname, 'alternative-mockups');

  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir);
  }

  const concepts = [
    { name: 'energy', label: 'Energy Field' },
    { name: 'geometry', label: 'Sacred Geometry' },
    { name: 'zen', label: 'Zen Garden' },
    { name: 'tree', label: 'Tree of Life' }
  ];

  for (const concept of concepts) {
    console.log(`\n📐 CONCEPT: ${concept.label.toUpperCase()}`);
    console.log('─'.repeat(60));

    const levels = [
      { level: 0, totalCompleted: 5 },
      { level: 2, totalCompleted: 35 },
      { level: 5, totalCompleted: 250 }
    ];

    for (const mockup of levels) {
      const filename = `${concept.name}-level${mockup.level}.png`;
      const outputPath = path.join(outputDir, filename);
      const result = await generateConceptMockup(concept.name, mockup.level, mockup.totalCompleted, outputPath);

      if (result.success) {
        console.log(`  Level ${mockup.level} → ${result.generationTime}ms | ${result.fileSizeKB}KB`);
      } else {
        console.log(`  Level ${mockup.level} → ERROR: ${result.error}`);
      }
    }
  }

  console.log(`\n✨ Mockups saved to: ${outputDir}\n`);
  console.log('📝 Review each concept and choose your favorite!\n');
}

if (require.main === module) {
  generateAllConcepts()
    .then(() => process.exit(0))
    .catch(err => {
      console.error('❌ Error:', err);
      process.exit(1);
    });
}

module.exports = {
  generateConceptMockup,
  generateEnergyField,
  generateSacredGeometry,
  generateZenGarden,
  generateTreeOfLife
};
