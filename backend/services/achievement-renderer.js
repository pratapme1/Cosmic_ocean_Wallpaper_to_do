/**
 * Achievement Badge Renderer for Wallpaper
 *
 * Renders achievement badges and progress indicators on wallpaper.
 *
 * Features:
 * - SVG icon generation for each achievement type
 * - Achievement bar component for top of wallpaper
 * - Progress ring for next milestone
 * - Satori-compatible element generation
 *
 * Epic: 10 Phase 2 - Achievement System
 * Created: 2026-01-09
 */

const satori = require('satori').default;
const fs = require('fs');
const path = require('path');

// Load fonts (same as text-renderer)
const fontsDir = path.join(__dirname, '../fonts');
const interRegular = fs.readFileSync(path.join(fontsDir, 'Inter-Regular.woff'));
const interMedium = fs.readFileSync(path.join(fontsDir, 'Inter-Medium.woff'));

const fonts = [
  {
    name: 'Inter',
    data: interRegular,
    weight: 400,
    style: 'normal',
  },
  {
    name: 'Inter',
    data: interMedium,
    weight: 500,
    style: 'normal',
  },
];

/**
 * Achievement icon SVG paths
 * Simple geometric shapes that render well at small sizes
 */
const ACHIEVEMENT_ICONS = {
  // Flame (for streak achievements)
  flame: `
    <path d="M12 2C12 2 12 5.5 12 8C12 10 13.5 11 14 11C15 11 16 10 16 8.5
             C16 8.5 18 11 18 14C18 17.5 15 20 12 20C9 20 6 17.5 6 14
             C6 10 8 7 9 5C9 5 10 7 11 7C12 7 12 2 12 2Z"
          fill="currentColor"/>
  `,

  // Fire (larger flame for bigger streaks)
  fire: `
    <path d="M12 1C12 1 12 6 12 8C12 11 14 12 15 12C16 12 18 11 18 8
             C18 8 22 12 22 16C22 20 18 23 12 23C6 23 2 20 2 16
             C2 11 6 7 8 4C8 4 10 8 11 8C12 8 12 1 12 1Z
             M12 17C10.5 17 9 15.5 9 14C9 13 10 12 12 12
             C14 12 15 13 15 14C15 15.5 13.5 17 12 17Z"
          fill="currentColor"/>
  `,

  // Lightning bolt (for speed demon)
  lightning: `
    <path d="M13 1L4 14H11L10 23L20 10H13L13 1Z"
          fill="currentColor"/>
  `,

  // Sun (for early bird)
  sun: `
    <circle cx="12" cy="12" r="5" fill="currentColor"/>
    <line x1="12" y1="1" x2="12" y2="4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="12" y1="20" x2="12" y2="23" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="1" y1="12" x2="4" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="20" y1="12" x2="23" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="4.2" y1="4.2" x2="6.3" y2="6.3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="17.7" y1="17.7" x2="19.8" y2="19.8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="4.2" y1="19.8" x2="6.3" y2="17.7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    <line x1="17.7" y1="6.3" x2="19.8" y2="4.2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
  `,

  // Moon (for night owl)
  moon: `
    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"
          fill="currentColor"/>
  `,

  // Check circle (for zero inbox)
  'check-circle': `
    <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/>
    <path d="M8 12L11 15L17 9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  `,

  // Trophy (for milestones)
  trophy: `
    <path d="M6 9H4C3 9 2 8 2 7V5C2 4 3 3 4 3H6V2H18V3H20C21 3 22 4 22 5V7
             C22 8 21 9 20 9H18C18 13 15 15.5 13 16V19H17V22H7V19H11V16
             C9 15.5 6 13 6 9Z
             M4 5V7H6V5H4Z
             M18 5V7H20V5H18Z"
          fill="currentColor"/>
  `,

  // Medal (for task master)
  medal: `
    <circle cx="12" cy="14" r="6" fill="currentColor"/>
    <path d="M8 2L12 8L16 2" fill="none" stroke="currentColor" stroke-width="2"/>
    <circle cx="12" cy="14" r="3" fill="none" stroke="rgba(0,0,0,0.3)" stroke-width="1"/>
  `,

  // Star (for category master and legendary)
  star: `
    <polygon points="12,2 15,9 22,9 17,14 19,21 12,17 5,21 7,14 2,9 9,9"
             fill="currentColor"/>
  `,

  // Crown (for centurion and cosmic legend)
  crown: `
    <path d="M2 17L5 7L9 12L12 4L15 12L19 7L22 17V21H2V17Z"
          fill="currentColor"/>
  `,

  // Rocket (for first task)
  rocket: `
    <path d="M12 2C12 2 14 4 14 8V12L17 15V18L12 16L7 18V15L10 12V8
             C10 4 12 2 12 2Z
             M8 19L7 22H17L16 19H8Z"
          fill="currentColor"/>
  `,
};

/**
 * Scale spacing value by density (dp to px)
 */
function dp(dpValue, density) {
  return Math.floor(dpValue * density);
}

/**
 * Generate a single achievement badge element for Satori
 * @param {Object} achievement - Achievement definition
 * @param {number} size - Badge size in dp
 * @param {number} density - Screen density
 * @returns {Object} Satori element
 */
function generateBadgeElement(achievement, size, density) {
  const badgeSize = dp(size, density);
  const iconSize = dp(size * 0.5, density);
  const fontSize = dp(size * 0.2, density);

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        width: badgeSize,
        height: badgeSize + dp(12, density), // Extra space for label
        marginRight: dp(6, density),
      },
      children: [
        // Badge circle with icon
        {
          type: 'div',
          props: {
            style: {
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: badgeSize,
              height: badgeSize,
              borderRadius: '50%',
              backgroundColor: achievement.color || '#FFD700',
              boxShadow: `0 0 ${dp(8, density)}px ${achievement.color || '#FFD700'}40`,
              border: `${dp(2, density)}px solid rgba(255, 255, 255, 0.3)`,
            },
            children: {
              type: 'div',
              props: {
                style: {
                  color: '#FFFFFF',
                  fontSize: iconSize,
                  fontWeight: 600,
                  textShadow: '0 1px 2px rgba(0,0,0,0.3)',
                },
                // Use simple text symbols instead of SVG paths for Satori compatibility
                children: getIconSymbol(achievement.icon),
              },
            },
          },
        },
        // Badge label (short name)
        {
          type: 'div',
          props: {
            style: {
              color: 'rgba(255, 255, 255, 0.9)',
              fontSize: Math.max(fontSize, 8),
              fontWeight: 500,
              marginTop: dp(3, density),
              textAlign: 'center',
              maxWidth: badgeSize + dp(10, density),
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            },
            children: getShortName(achievement.name),
          },
        },
      ],
    },
  };
}

/**
 * Get icon symbol for achievement (Satori-compatible)
 * Since Satori doesn't support embedded SVG, we use Unicode symbols
 */
function getIconSymbol(iconName) {
  const symbols = {
    flame: '🔥',       // Will render as text
    fire: '🔥',
    lightning: '⚡',
    sun: '☀',
    moon: '🌙',
    'check-circle': '✓',
    trophy: '🏆',
    medal: '🎖',
    star: '★',
    crown: '👑',
    rocket: '🚀',
  };

  // Fallback symbols that work better in Inter font
  const interSymbols = {
    flame: '▲',       // Triangle up (fire-like)
    fire: '▲',
    lightning: '⚡',   // Lightning symbol exists
    sun: '●',         // Circle for sun
    moon: '◐',        // Half moon
    'check-circle': '✓',
    trophy: '★',      // Star for trophy
    medal: '◆',       // Diamond for medal
    star: '★',
    crown: '♛',       // Crown symbol
    rocket: '▲',      // Upward triangle
  };

  return interSymbols[iconName] || '●';
}

/**
 * Get shortened name for badge display
 */
function getShortName(name) {
  const shortNames = {
    '3-Day Streak': '3 Days',
    'Week Warrior': '7 Days',
    'Fortnight Force': '14 Days',
    'Monthly Master': '30 Days',
    'Centurion': '100 Days',
    'Speed Demon': 'Speed',
    'Early Bird': 'Early',
    'Night Owl': 'Night',
    'Zero Inbox': 'Clear',
    'Half Century': '50',
    'Century Club': '100',
    'Task Master': '500',
    'Legendary': '1000',
    'Cosmic Legend': '5000',
    'First Step': '1st',
  };

  return shortNames[name] || name.substring(0, 8);
}

/**
 * Generate progress ring element for next milestone
 * @param {Object} inProgress - Achievement in progress
 * @param {number} size - Ring size in dp
 * @param {number} density - Screen density
 * @param {Object} colors - Color palette
 * @returns {Object} Satori element
 */
function generateProgressRing(inProgress, size, density, colors) {
  if (!inProgress) return null;

  const ringSize = dp(size, density);
  const strokeWidth = dp(3, density);
  const radius = (ringSize - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const progress = Math.min(inProgress.progress || 0, 100);
  const dashoffset = circumference * (1 - progress / 100);

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        marginLeft: dp(8, density),
      },
      children: [
        // Progress ring container
        {
          type: 'div',
          props: {
            style: {
              position: 'relative',
              width: ringSize,
              height: ringSize,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            },
            children: [
              // Progress text in center
              {
                type: 'div',
                props: {
                  style: {
                    color: colors.textPrimary,
                    fontSize: dp(10, density),
                    fontWeight: 600,
                  },
                  children: `${Math.round(progress)}%`,
                },
              },
            ],
          },
        },
        // Label
        {
          type: 'div',
          props: {
            style: {
              color: colors.textSecondary,
              fontSize: dp(8, density),
              marginTop: dp(2, density),
              textAlign: 'center',
            },
            children: `${inProgress.current}/${inProgress.target}`,
          },
        },
      ],
    },
  };
}

/**
 * Generate achievement bar element for wallpaper
 * @param {Array} achievements - Array of achievements to display (max 3)
 * @param {Object} inProgress - Next achievement in progress
 * @param {Object} layout - Layout configuration
 * @param {Object} colors - Color palette
 * @returns {Object} Satori element or null
 */
function generateAchievementBar(achievements, inProgress, layout, colors) {
  const { density, margins, typography } = layout;

  if ((!achievements || achievements.length === 0) && !inProgress) {
    return null;
  }

  const badgeElements = (achievements || []).map(a =>
    generateBadgeElement(a, 36, density)
  );

  const progressElement = inProgress
    ? generateProgressRing(inProgress, 36, density, colors)
    : null;

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-start',
        padding: `${dp(8, density)}px ${margins.horizontal}px`,
        backgroundColor: 'rgba(0, 0, 0, 0.2)',
        borderRadius: dp(12, density),
        marginBottom: dp(12, density),
      },
      children: [
        // Achievement badges
        ...badgeElements,
        // Progress ring for next achievement
        progressElement,
      ].filter(Boolean),
    },
  };
}

/**
 * Render achievements layer to SVG
 * @param {Object} layout - Layout configuration
 * @param {Array} achievements - Array of earned achievements
 * @param {Array} inProgress - Achievements in progress
 * @param {Object} colors - Color palette
 * @param {Object} options - Rendering options
 * @returns {Promise<string>} SVG string
 */
async function renderAchievementsToSvg(layout, achievements, inProgress, colors, options = {}) {
  const { width, height, density, margins, typography } = layout;
  const { maxBadges = 3, showProgress = true } = options;

  // Take only the top achievements to display
  const displayAchievements = (achievements || []).slice(0, maxBadges);

  // Get the most relevant in-progress achievement
  const nextAchievement = showProgress && inProgress && inProgress.length > 0
    ? inProgress[0]
    : null;

  if (displayAchievements.length === 0 && !nextAchievement) {
    // Return empty SVG if nothing to display
    return `<svg width="${width}" height="${height}"></svg>`;
  }

  // Generate achievement bar element
  const achievementBar = generateAchievementBar(
    displayAchievements,
    nextAchievement,
    layout,
    colors
  );

  // Position at top of screen (after status bar safe zone)
  const element = {
    type: 'div',
    props: {
      style: {
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        paddingTop: dp(60, density), // Safe zone for status bar
      },
      children: achievementBar,
    },
  };

  // Generate SVG using satori
  const svg = await satori(element, {
    width,
    height,
    fonts,
  });

  return svg;
}

/**
 * Generate inline achievement display for wallpaper header
 * This is a simpler version that can be embedded in the task list
 * @param {Array} achievements - Achievements to display
 * @param {number} density - Screen density
 * @param {Object} colors - Color palette
 * @returns {Object|null} Satori element or null
 */
function generateInlineAchievements(achievements, density, colors) {
  if (!achievements || achievements.length === 0) return null;

  const badges = achievements.slice(0, 3).map(a => ({
    type: 'div',
    props: {
      style: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: dp(24, density),
        height: dp(24, density),
        borderRadius: '50%',
        backgroundColor: a.color || '#FFD700',
        marginRight: dp(4, density),
        boxShadow: `0 0 ${dp(6, density)}px ${a.color || '#FFD700'}30`,
      },
      children: {
        type: 'div',
        props: {
          style: {
            color: '#FFFFFF',
            fontSize: dp(12, density),
            fontWeight: 600,
          },
          children: getIconSymbol(a.icon),
        },
      },
    },
  }));

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        alignItems: 'center',
        marginBottom: dp(8, density),
      },
      children: badges,
    },
  };
}

/**
 * Format streak text for display
 */
function formatStreakText(streak) {
  if (streak <= 0) return null;
  if (streak === 1) return '1 day streak';
  return `${streak} day streak`;
}

/**
 * Generate streak display element
 * @param {number} streak - Current streak count
 * @param {number} density - Screen density
 * @param {Object} colors - Color palette
 * @returns {Object|null} Satori element or null
 */
function generateStreakDisplay(streak, density, colors) {
  if (!streak || streak <= 0) return null;

  // Determine flame intensity based on streak length
  let flameColor = '#FF6B35'; // Default orange
  if (streak >= 100) flameColor = '#FFD700'; // Gold
  else if (streak >= 30) flameColor = '#FF9F1C'; // Bright orange
  else if (streak >= 7) flameColor = '#FF8C42'; // Medium orange

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        alignItems: 'center',
        padding: `${dp(4, density)}px ${dp(8, density)}px`,
        backgroundColor: 'rgba(255, 107, 53, 0.15)',
        borderRadius: dp(12, density),
        border: `1px solid ${flameColor}40`,
      },
      children: [
        // Flame icon
        {
          type: 'div',
          props: {
            style: {
              color: flameColor,
              fontSize: dp(14, density),
              marginRight: dp(4, density),
            },
            children: '▲', // Flame symbol
          },
        },
        // Streak text
        {
          type: 'div',
          props: {
            style: {
              color: colors.textPrimary,
              fontSize: dp(11, density),
              fontWeight: 500,
            },
            children: formatStreakText(streak),
          },
        },
      ],
    },
  };
}

module.exports = {
  renderAchievementsToSvg,
  generateAchievementBar,
  generateBadgeElement,
  generateProgressRing,
  generateInlineAchievements,
  generateStreakDisplay,
  getIconSymbol,
  ACHIEVEMENT_ICONS,
};
