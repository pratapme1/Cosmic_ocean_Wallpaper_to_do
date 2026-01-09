/**
 * Enhanced Wallpaper Generator
 * Complete implementation based on ANDROID_UI_UX_DESIGN_SYSTEM.md
 *
 * Features:
 * - Multi-layer rendering (background, particles, scene, transition, task, interaction)
 * - Safe zone calculations
 * - Responsive layout system
 * - Particle systems (stars, bubbles)
 * - Animation support
 * - WCAG-compliant text rendering
 * - Theme system (cosmic, ocean, fantasy)
 */

const sharp = require('sharp');
const { getLayoutConfig } = require('./layout-system');
const { generateParticles } = require('./particle-system');
const { getColorPalette, lerpColor, verifyWCAG } = require('./color-system');
const { getAnimationState } = require('./animation-system');
const { renderTextToSvg } = require('./text-renderer');
const { MessageEngine } = require('./message-engine');
const { AtmosphereController } = require('./atmosphere-controller');
const { StatsAggregator } = require('./stats-aggregator');
const { getCurrentMessage } = require('./wallpaper-message-provider');
const { filterTasksForWallpaper, getPrivacyStats } = require('./privacy-filter');

// Initialize intelligence layer components
const messageEngine = new MessageEngine();
const atmosphereController = new AtmosphereController();
const statsAggregator = new StatsAggregator();

/**
 * Determine urgency level from tasks
 * @param {array} tasks - Array of tasks
 * @param {boolean} doneForToday - User marked as done
 * @returns {string} Urgency level
 */
function calculateUrgency(tasks, doneForToday = false) {
  if (doneForToday || tasks.length === 0) return 'clear';

  const now = new Date();
  const hasOverdue = tasks.some(t => {
    if (!t.due_date) return false;
    const dueDate = new Date(t.due_date);
    return dueDate < now;
  });

  if (hasOverdue) return 'critical';

  const hasDueToday = tasks.some(t => {
    if (!t.due_date) return false;
    const dueDate = new Date(t.due_date);
    return dueDate.toDateString() === now.toDateString();
  });

  if (hasDueToday) return 'urgent';

  const hasDueWithin48h = tasks.some(t => {
    if (!t.due_date) return false;
    const dueDate = new Date(t.due_date);
    const hoursUntil = (dueDate - now) / (1000 * 60 * 60);
    return hoursUntil > 0 && hoursUntil <= 48;
  });

  if (hasDueWithin48h) return 'attention';

  return 'calm';
}

/**
 * Generate SVG for background layer with gradient
 */
function generateBackgroundLayer(layout, colors, animState) {
  const { width, height } = layout;
  const { breathScale } = animState;

  // Create gradient background
  const scaledWidth = Math.floor(width * breathScale);
  const scaledHeight = Math.floor(height * breathScale);
  const offsetX = (width - scaledWidth) / 2;
  const offsetY = (height - scaledHeight) / 2;

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <radialGradient id="bgGradient" cx="50%" cy="40%">
          <stop offset="0%" style="stop-color:${colors.bgSecondary};stop-opacity:1" />
          <stop offset="100%" style="stop-color:${colors.bgPrimary};stop-opacity:1" />
        </radialGradient>
        <filter id="glow">
          <feGaussianBlur stdDeviation="20" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>
      <rect x="${offsetX}" y="${offsetY}" width="${scaledWidth}" height="${scaledHeight}" fill="url(#bgGradient)" />
    </svg>
  `;
}

/**
 * Generate SVG for particle layer
 */
function generateParticleLayer(layout, theme, urgency, animState) {
  const { width, height } = layout;
  const particles = generateParticles(theme, layout, urgency, animState.timestamp);

  const particleElements = particles.map(p => {
    return `<circle cx="${p.x}" cy="${p.y}" r="${p.size}" fill="${p.color}" opacity="${p.opacity}" />`;
  }).join('\n');

  return `
    <svg width="${width}" height="${height}">
      ${particleElements}
    </svg>
  `;
}

/**
 * Generate SVG for transition gradient layer
 * Smooth gradient from scene to task zone
 */
function generateTransitionLayer(layout, colors) {
  const { width, height, layoutZones } = layout;
  const zone = layoutZones.transition;

  return `
    <svg width="${width}" height="${height}">
      <defs>
        <linearGradient id="transitionGradient" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" style="stop-color:${colors.bgPrimary};stop-opacity:0" />
          <stop offset="50%" style="stop-color:${colors.bgPrimary};stop-opacity:0.3" />
          <stop offset="100%" style="stop-color:${colors.bgPrimary};stop-opacity:0.7" />
        </linearGradient>
      </defs>
      <rect x="0" y="${zone.y}" width="${width}" height="${zone.height}" fill="url(#transitionGradient)" />
    </svg>
  `;
}

// Font configuration for serverless - relies on bundled fonts in /fonts directory
// Fontconfig is configured via FONTCONFIG_PATH in server.js
const FONT_FAMILY = "Inter, sans-serif";

/**
 * Generate SVG for text with multiple layers (glow, shadow, main)
 * WCAG-compliant rendering
 */
function generateTextLayer(layout, tasks, colors, doneForToday) {
  const { width, height, layoutZones, typography, margins } = layout;
  const taskZone = layoutZones.task;

  // Verify WCAG contrast
  const contrastCheck = verifyWCAG(colors.textPrimary, colors.bgPrimary, 'AA', true);
  if (!contrastCheck.passes) {
    console.warn(`WCAG contrast check failed: ${contrastCheck.ratio.toFixed(2)}:1 (required: ${contrastCheck.required}:1)`);
  }

  if (doneForToday) {
    // Celebration mode
    const centerY = taskZone.y + taskZone.height / 2;
    return `
      <svg width="${width}" height="${height}">
        <style>
          
          .celebration { fill: ${colors.textPrimary}; font-family: ${FONT_FAMILY}; font-weight: 400; font-size: ${typography.displayMedium}px; text-anchor: middle; }
          .celebration-sub { fill: ${colors.textSecondary}; font-family: ${FONT_FAMILY}; font-size: ${typography.bodyLarge}px; text-anchor: middle; }
          .glow { filter: url(#textGlow); }
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
        <text x="50%" y="${centerY - 40}" class="celebration glow">✨ DONE FOR TODAY ✨</text>
        <text x="50%" y="${centerY + 20}" class="celebration-sub">Rest. You earned it.</text>
      </svg>
    `;
  }

  if (tasks.length === 0) {
    // No tasks - all clear
    const centerY = taskZone.y + taskZone.height / 2;
    return `
      <svg width="${width}" height="${height}">
        <style>
          
          .clear-text { fill: ${colors.textPrimary}; font-family: ${FONT_FAMILY}; font-weight: 400; font-size: ${typography.headlineLarge}px; text-anchor: middle; }
          .glow { filter: url(#textGlow); }
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
        <text x="50%" y="${centerY}" class="clear-text glow">All clear ✨</text>
      </svg>
    `;
  }

  // Display top 3 tasks (One Thing Mode)
  const topTasks = tasks.slice(0, 3);
  const remainingCount = Math.max(0, tasks.length - 3);

  let currentY = taskZone.y + margins.vertical + typography.labelMedium + 20;

  // "RIGHT NOW" header
  let svgContent = `
    <text x="${margins.horizontal}" y="${currentY}" class="header">RIGHT NOW</text>
  `;

  currentY += margins.vertical + 10;

  // Tasks
  topTasks.forEach((task, index) => {
    const priorityColor = task.priority === 2 ? colors.accentSecondary :
                         task.priority === 1 ? colors.accentPrimary :
                         colors.textSecondary;

    // Priority indicator circle
    svgContent += `
      <circle cx="${margins.horizontal}" cy="${currentY - typography.titleLarge / 3}" r="${typography.labelSmall / 2}" fill="${priorityColor}" />
    `;

    // Task title (with text wrapping for long titles)
    const maxTitleLength = Math.floor((width - margins.horizontal * 3) / (typography.titleLarge * 0.6));
    let title = task.title;
    if (title.length > maxTitleLength) {
      title = title.substring(0, maxTitleLength - 3) + '...';
    }

    svgContent += `
      <text x="${margins.horizontal + typography.labelSmall + 10}" y="${currentY}" class="task-title">${escapeXml(title)}</text>
    `;

    currentY += typography.titleLarge + 5;

    // Time estimate
    if (task.estimate_minutes) {
      const estimate = formatEstimate(task.estimate_minutes);
      svgContent += `
        <text x="${margins.horizontal + typography.labelSmall + 10}" y="${currentY}" class="meta-text">${estimate}</text>
      `;
      currentY += typography.bodyMedium + 10;
    }

    currentY += margins.vertical;
  });

  // Remaining count
  if (remainingCount > 0) {
    svgContent += `
      <text x="${margins.horizontal}" y="${currentY}" class="meta-text">+ ${remainingCount} more today</text>
    `;
  }

  // Current time at bottom
  const currentTime = new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  svgContent += `
    <text x="50%" y="${height - margins.vertical - typography.labelLarge}" class="time">${currentTime}</text>
  `;

  return `
    <svg width="${width}" height="${height}">
      <style>
        
        .header { fill: ${colors.textSecondary}; font-family: ${FONT_FAMILY}; font-size: ${typography.labelLarge}px; letter-spacing: 2px; text-transform: uppercase; filter: url(#textShadow); }
        .task-title { fill: ${colors.textPrimary}; font-family: ${FONT_FAMILY}; font-weight: 500; font-size: ${typography.headlineLarge}px; filter: url(#textShadow); }
        .meta-text { fill: ${colors.textSecondary}; font-family: ${FONT_FAMILY}; font-size: ${typography.bodyMedium}px; filter: url(#textShadow); }
        .time { fill: ${colors.textSecondary}; font-family: ${FONT_FAMILY}; font-weight: 300; font-size: ${typography.displayLarge}px; text-anchor: middle; opacity: 0.5; filter: url(#textShadow); }
        .glow { filter: url(#textGlow); }
      </style>
      <defs>
        <filter id="textGlow">
          <feGaussianBlur stdDeviation="8" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
        <filter id="textShadow">
          <feDropShadow dx="2" dy="2" stdDeviation="4" flood-opacity="0.4"/>
        </filter>
      </defs>
      ${svgContent}
    </svg>
  `;
}

/**
 * Helper function to format time estimate
 */
function formatEstimate(minutes) {
  if (minutes < 60) return `~${minutes}min`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins === 0 ? `~${hours}h` : `~${hours}h ${mins}m`;
}

/**
 * Helper function to escape XML special characters
 */
function escapeXml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/**
 * Main wallpaper generation function
 * @param {object} user - User object with theme, resolution preferences
 * @param {object} data - Data object with tasks array
 * @param {number} timestamp - Optional timestamp for animation state
 * @param {string} timezone - User's timezone (e.g., 'Asia/Kolkata')
 * @returns {Promise<Buffer>} PNG image buffer
 */
async function generateEnhancedWallpaper(user, data, timestamp = Date.now(), timezone = 'UTC') {
  try {
    const { theme = 'cosmic', resolution = '1080x1920', done_for_today = false } = user;
    const [width, height] = resolution.split('x').map(Number);
    const rawTasks = data.tasks || [];
    const allTasks = data.allTasks || rawTasks; // Include completed tasks for stats

    // =====================================
    // PRIVACY FILTERING (Epic 10)
    // =====================================

    // Build user privacy preferences object
    const privacyPrefs = {
      hide_all_tasks_mode: user.hide_all_tasks_mode || false,
      auto_hide_work_tasks: user.auto_hide_work_tasks || false,
      work_hours_start: user.work_hours_start || '09:00',
      work_hours_end: user.work_hours_end || '17:00',
      default_privacy_level: user.default_privacy_level || 'public'
    };

    // Apply privacy filtering to tasks
    const tasks = filterTasksForWallpaper(rawTasks, privacyPrefs, timezone);

    // Log privacy stats
    const privacyStats = getPrivacyStats(rawTasks, tasks);
    console.log(`[Privacy] Filtered: ${privacyStats.totalTasks} -> ${privacyStats.displayedTasks} tasks (${privacyStats.hiddenTasks} hidden)`);

    // =====================================
    // END PRIVACY FILTERING
    // =====================================

    // Calculate layout
    const layout = getLayoutConfig(width, height);

    // =====================================
    // INTELLIGENCE LAYER INTEGRATION
    // =====================================

    // Get atmosphere state with urgency calculation and visual params
    const atmosphere = atmosphereController.calculateState(tasks);
    const urgency = atmosphere.state;
    const visualParams = atmosphere.visualParams;

    console.log(`[Intelligence] Atmosphere: ${urgency} (score: ${atmosphere.urgencyScore})`);
    console.log(`[Intelligence] Visual params: particles=${visualParams.particleCount}, speed=${visualParams.animationSpeed}`);

    // Get user stats for intelligent messaging
    const stats = statsAggregator.computeStats(allTasks);
    console.log(`[Intelligence] Stats: streak=${stats.streakDays}, today=${stats.completedToday}`);

    // Generate intelligent message (Epic 8: LLM-powered with fallback)
    let intelligentMessage;

    try {
      // Try to get LLM-generated message from cache
      const messageContext = {
        pendingCount: tasks.length,
        completedToday: stats.completedToday,
        timeOfDay: new Date().getHours() < 12 ? 'MORNING' : new Date().getHours() < 17 ? 'AFTERNOON' : 'EVENING',
        urgencyScore: atmosphere.urgencyScore
      };

      // Add timeout to prevent blocking wallpaper generation (5 second max)
      const messagePromise = getCurrentMessage(user.id, messageContext);
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(() => reject(new Error('Message fetch timeout (5s)')), 5000)
      );
      const llmMessage = await Promise.race([messagePromise, timeoutPromise]);

      if (llmMessage && llmMessage.message) {
        intelligentMessage = {
          text: llmMessage.message,
          type: llmMessage.intent || 'llm_generated',
          source: llmMessage.source,
          voice: llmMessage.voice
        };
        console.log(`[Intelligence] LLM Message: "${intelligentMessage.text}" (source: ${llmMessage.source})`);
      } else {
        throw new Error('No LLM message available');
      }
    } catch (error) {
      // Fallback to template-based message engine
      console.log(`[Intelligence] LLM message unavailable (${error.message}), using templates`);
      intelligentMessage = messageEngine.generateMessage(user, tasks, new Date(), stats);
      intelligentMessage.source = 'template_engine';
      console.log(`[Intelligence] Template Message: "${intelligentMessage.text}" (type: ${intelligentMessage.type})`);
    }

    // =====================================
    // END INTELLIGENCE LAYER
    // =====================================

    // Get color palette (use urgency from atmosphere)
    const colors = getColorPalette(theme, urgency);

    // Get animation state (enhanced with visual params)
    const animState = getAnimationState(timestamp, urgency);
    // Apply intelligence-driven animation speed
    animState.animationSpeed = visualParams.animationSpeed;
    animState.breathingRate = visualParams.breathingRate;

    console.log(`[Wallpaper] Generating ${theme} theme, ${urgency} urgency, ${width}x${height}`);
    console.log(`[Wallpaper] Animation state: breath=${animState.breathPhase.toFixed(2)}, scale=${animState.breathScale.toFixed(3)}`);

    // Generate layers
    const layers = [];

    // Layer 1: Background with gradient
    const backgroundSvg = generateBackgroundLayer(layout, colors, animState);
    layers.push({ input: Buffer.from(backgroundSvg), blend: 'over' });

    // Layer 2: Particles (stars, bubbles) - use visual params for particle count
    const particleSvg = generateParticleLayerWithParams(layout, theme, urgency, animState, visualParams);
    layers.push({ input: Buffer.from(particleSvg), blend: 'over' });

    // Layer 3: Transition gradient
    const transitionSvg = generateTransitionLayer(layout, colors);
    layers.push({ input: Buffer.from(transitionSvg), blend: 'over' });

    // Layer 4: Task overlay with text (using satori for serverless font rendering)
    // Now includes intelligent message!
    console.log(`[Wallpaper] Rendering text with SATORI (${tasks.length} tasks, timezone=${timezone})`);
    const textSvg = await renderTextToSvg(layout, tasks, colors, done_for_today, intelligentMessage, timezone);
    console.log(`[Wallpaper] Text SVG generated: ${textSvg.length} chars, has paths: ${textSvg.includes('<path')}`);
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

    return await baseImage
      .composite(layers)
      .png()
      .toBuffer();

  } catch (err) {
    console.error('Enhanced wallpaper generation failed:', err);
    throw err;
  }
}

/**
 * Generate particle layer with intelligence-driven params
 */
function generateParticleLayerWithParams(layout, theme, urgency, animState, visualParams) {
  const { width, height } = layout;

  // Override particle count from visual params
  const overrideOptions = {
    particleCount: visualParams.particleCount,
    animationSpeed: visualParams.animationSpeed
  };

  const particles = generateParticles(theme, layout, urgency, animState.timestamp, overrideOptions);

  const particleElements = particles.map(p => {
    return `<circle cx="${p.x}" cy="${p.y}" r="${p.size}" fill="${p.color}" opacity="${p.opacity}" />`;
  }).join('\n');

  return `
    <svg width="${width}" height="${height}">
      ${particleElements}
    </svg>
  `;
}

/**
 * Generate fallback wallpaper for errors
 */
async function generateFallbackWallpaper(width, height, errorMessage = 'Unable to generate wallpaper') {
  const svgFallback = `
    <svg width="${width}" height="${height}">
      <style>
        
        .error-text { fill: white; font-family: ${FONT_FAMILY}; font-size: ${Math.floor(width * 0.04)}px; text-anchor: middle; }
        .icon { fill: rgba(255,255,255,0.3); font-size: ${Math.floor(width * 0.08)}px; }
      </style>
      <rect width="${width}" height="${height}" fill="rgb(20,20,40)" />
      <text x="50%" y="45%" class="icon" text-anchor="middle">⚠️</text>
      <text x="50%" y="55%" class="error-text">${escapeXml(errorMessage)}</text>
    </svg>
  `;

  return sharp(Buffer.from(svgFallback)).png().toBuffer();
}

module.exports = {
  generateEnhancedWallpaper,
  generateFallbackWallpaper,
  calculateUrgency
};
