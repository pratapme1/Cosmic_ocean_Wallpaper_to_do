/**
 * Text Renderer using Satori
 * Converts text to SVG paths - works on serverless without system fonts
 */

const satori = require('satori').default;
const fs = require('fs');
const path = require('path');
const { createCanvas } = require('canvas');

// Load font files as ArrayBuffer (required by satori)
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

// Category symbol mapping (Inter-compatible, no emojis)
const CATEGORY_SYMBOLS = {
  work: '■',        // Square
  personal: '◆',    // Diamond
  health: '▲',      // Triangle
  finance: '$',     // Dollar
  learning: '●',    // Circle
  social: '◐',      // Half circle
  errands: '▪',     // Small square
  general: '•'      // Bullet
};

// Energy level symbols
const ENERGY_SYMBOLS = {
  high: '▲▲',       // Double triangle up
  medium: '▬',      // Horizontal bar
  low: '▼'         // Triangle down
};

// Category colors for badges (subtle pastels)
const CATEGORY_COLORS = {
  work: 'rgba(100, 149, 237, 0.25)',    // Cornflower blue
  personal: 'rgba(147, 112, 219, 0.25)', // Medium purple
  health: 'rgba(60, 179, 113, 0.25)',    // Medium sea green
  finance: 'rgba(218, 165, 32, 0.25)',   // Goldenrod
  learning: 'rgba(30, 144, 255, 0.25)',  // Dodger blue
  social: 'rgba(255, 105, 180, 0.25)',   // Hot pink
  errands: 'rgba(255, 140, 0, 0.25)',    // Dark orange
  general: 'rgba(128, 128, 128, 0.25)'   // Gray
};

/**
 * Scale spacing value by density (dp → px)
 * @param {number} dpValue - Value in density-independent pixels
 * @param {number} density - Screen density multiplier
 * @returns {number} Scaled pixel value
 */
function dp(dpValue, density) {
  return Math.floor(dpValue * density);
}

/**
 * Truncate text to fit within maxWidth pixels using proper font measurement
 * Based on WallpaperGenerator.kt approach - uses pixel-based measurement instead of character count
 *
 * @param {string} text - Text to truncate
 * @param {number} fontSize - Font size in pixels
 * @param {number} fontWeight - Font weight (400 or 500)
 * @param {number} maxWidth - Maximum width in pixels
 * @returns {string} Truncated text with ellipsis if needed
 */
function truncateText(text, fontSize, fontWeight, maxWidth) {
  if (!text || maxWidth <= 0) return '';

  // Create a canvas context for text measurement
  const canvas = createCanvas(1, 1);
  const ctx = canvas.getContext('2d');
  ctx.font = `${fontWeight} ${fontSize}px Inter`;

  // Measure full text width
  const fullWidth = ctx.measureText(text).width;
  if (fullWidth <= maxWidth) {
    return text;
  }

  // Need to truncate - measure ellipsis
  const ellipsis = '...';
  const ellipsisWidth = ctx.measureText(ellipsis).width;
  const availableWidth = maxWidth - ellipsisWidth;

  if (availableWidth <= 0) {
    return ellipsis;
  }

  // Binary search for the maximum number of characters that fit
  let left = 0;
  let right = text.length;
  let bestFit = 0;

  while (left <= right) {
    const mid = Math.floor((left + right) / 2);
    const substring = text.substring(0, mid);
    const width = ctx.measureText(substring).width;

    if (width <= availableWidth) {
      bestFit = mid;
      left = mid + 1;
    } else {
      right = mid - 1;
    }
  }

  if (bestFit === 0) {
    return ellipsis;
  }

  return text.substring(0, bestFit) + ellipsis;
}

/**
 * Render text layer to SVG using satori
 * @param {object} layout - Layout configuration
 * @param {array} tasks - Array of tasks
 * @param {object} colors - Color palette
 * @param {boolean} doneForToday - Done for today flag
 * @param {object} intelligentMessage - Optional intelligent message from MessageEngine
 * @param {string} timezone - User's timezone (e.g., 'Asia/Kolkata', 'America/New_York')
 * @returns {Promise<string>} SVG string
 */
async function renderTextToSvg(layout, tasks, colors, doneForToday, intelligentMessage = null, timezone = 'UTC') {
  const { width, height, layoutZones, typography, margins, density } = layout;
  const taskZone = layoutZones.task;

  let element;

  if (doneForToday) {
    // Celebration mode
    element = {
      type: 'div',
      props: {
        style: {
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        },
        children: [
          {
            type: 'div',
            props: {
              style: {
                color: colors.textPrimary,
                fontSize: typography.displayMedium,
                fontWeight: 400,
                textShadow: '0 0 20px rgba(255,255,255,0.5)',
              },
              children: '✨ DONE FOR TODAY ✨',
            },
          },
          {
            type: 'div',
            props: {
              style: {
                color: colors.textSecondary,
                fontSize: typography.bodyLarge,
                marginTop: dp(10, density),
              },
              children: 'Rest. You earned it.',
            },
          },
        ],
      },
    };
  } else if (tasks.length === 0) {
    // No tasks
    element = {
      type: 'div',
      props: {
        style: {
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        },
        children: {
          type: 'div',
          props: {
            style: {
              color: colors.textPrimary,
              fontSize: typography.headlineLarge,
              fontWeight: 400,
              textShadow: '0 0 20px rgba(255,255,255,0.5)',
            },
            children: 'All clear ✨',
          },
        },
      },
    };
  } else {
    // Task list - show fewer tasks on small screens to fit
    const maxTasks = width < 500 ? 2 : 3; // Small screens = 2 tasks only
    const topTasks = tasks.slice(0, maxTasks);
    const remainingCount = Math.max(0, tasks.length - maxTasks);

    const taskElements = topTasks.map((task, index) => {
      const priorityColor = task.priority === 2 ? colors.accentSecondary :
                           task.priority === 1 ? colors.accentPrimary :
                           colors.textSecondary;

      // Calculate available width for title (pixel-based, not character-based!)
      // Task area uses 88% of screen width (6% margins on each side = 12% total)
      const taskAreaWidth = width * 0.88;
      // Priority dot: 6dp diameter + 12dp margin
      const priorityDotWidth = dp(6, density) + dp(12, density);
      // Available width for title text
      const titleMaxWidth = taskAreaWidth - priorityDotWidth - margins.horizontal;

      // Truncate title using pixel-based measurement
      const title = truncateText(
        task.title || 'Untitled',
        typography.titleLarge,  // Font size
        500,  // Font weight (Medium)
        titleMaxWidth
      );

      // Build category badge if category exists and not 'general'
      const categoryBadge = task.category && task.category !== 'general' ? {
        type: 'div',
        props: {
          style: {
            display: 'flex',
            alignItems: 'center',
            backgroundColor: CATEGORY_COLORS[task.category] || CATEGORY_COLORS.general,
            color: colors.textPrimary,
            fontSize: typography.labelSmall,
            padding: `${dp(2, density)}px ${dp(5, density)}px`,
            borderRadius: dp(4, density),
            marginRight: dp(4, density),
            fontWeight: 600,
            border: `1px solid rgba(255,255,255,0.1)`,
          },
          children: `${CATEGORY_SYMBOLS[task.category] || '•'} ${task.category.toUpperCase()}`,
        },
      } : null;

      // Build context tag (show first tag if exists)
      const contextTag = task.context_tags && task.context_tags.length > 0 ? {
        type: 'div',
        props: {
          style: {
            color: colors.textSecondary,
            fontSize: typography.labelSmall,
            marginLeft: dp(12, density),
            marginTop: dp(2, density),
            opacity: 0.9,
          },
          children: `@ ${task.context_tags[0].toUpperCase().replace('@', '')}`,
        },
      } : null;

      // Build energy indicator for high/low energy (skip medium)
      const energyIndicator = task.energy_level && task.energy_level !== 'medium' ? {
        type: 'div',
        props: {
          style: {
            color: task.energy_level === 'high' ? '#FF6B6B' : '#4ECDC4',
            fontSize: typography.labelSmall,
            marginLeft: dp(4, density),
            fontWeight: 600,
          },
          children: ENERGY_SYMBOLS[task.energy_level] || '',
        },
      } : null;

      return {
        type: 'div',
        props: {
          key: index,
          style: {
            display: 'flex',
            flexDirection: 'column',
            marginBottom: margins.vertical + dp(4, density),
          },
          children: [
            // Category badge row (if exists)
            categoryBadge ? {
              type: 'div',
              props: {
                style: {
                  display: 'flex',
                  alignItems: 'center',
                  marginBottom: dp(3, density),
                  marginLeft: dp(12, density), // Align with title
                },
                children: categoryBadge,
              },
            } : null,
            // Title row with priority indicator
            {
              type: 'div',
              props: {
                style: {
                  display: 'flex',
                  alignItems: 'center',
                },
                children: [
                  {
                    type: 'div',
                    props: {
                      style: {
                        width: dp(6, density),
                        height: dp(6, density),
                        borderRadius: '50%',
                        backgroundColor: priorityColor,
                        marginRight: dp(6, density),
                      },
                    },
                  },
                  {
                    type: 'div',
                    props: {
                      style: {
                        color: colors.textPrimary,
                        fontSize: typography.headlineLarge,
                        fontWeight: 500,
                        textShadow: '2px 2px 4px rgba(0,0,0,0.4)',
                      },
                      children: title,
                    },
                  },
                  energyIndicator,
                ],
              },
            },
            // Task metadata row (countdown, estimate, context tag)
            {
              type: 'div',
              props: {
                style: {
                  display: 'flex',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                },
                children: [
                  // Live countdown (if task has due date within 24h)
                  (() => {
                    // Combine due_date and due_time into a single Date object
                    let fullDueDate = null;
                    if (task.due_date) {
                      if (task.due_time) {
                        // FIX 2026-01-06: Combine date + time properly
                        // PostgreSQL returns DATE as Date object (midnight UTC of that date)
                        // Extract just the date part and combine with TIME
                        const dateStr = task.due_date instanceof Date
                          ? task.due_date.toISOString().split('T')[0]
                          : String(task.due_date).split('T')[0];

                        // FIX v1.5.1: due_time is stored in user's LOCAL timezone, not UTC
                        // The 'Z' suffix was treating it as UTC, causing 5.5h offset for IST users
                        // Solution: Calculate timezone offset and adjust
                        const tempUtc = new Date(`${dateStr}T${task.due_time}Z`);

                        // Get timezone offset (IST = UTC+5:30 = -330 minutes offset)
                        const now = new Date();
                        const utcStr = now.toLocaleString('en-US', { timeZone: 'UTC' });
                        const localStr = now.toLocaleString('en-US', { timeZone: timezone || 'UTC' });
                        const offsetMs = new Date(utcStr) - new Date(localStr);

                        // Adjust: stored time is in local timezone, convert to UTC
                        fullDueDate = new Date(tempUtc.getTime() + offsetMs);

                        console.log(`[COUNTDOWN DEBUG] Task: ${task.title}`);
                        console.log(`[COUNTDOWN DEBUG] due_date: ${dateStr}, due_time: ${task.due_time}, timezone: ${timezone}`);
                        console.log(`[COUNTDOWN DEBUG] offset: ${offsetMs / (1000*60)} minutes`);
                        console.log(`[COUNTDOWN DEBUG] fullDueDate (UTC): ${fullDueDate.toISOString()}`);
                      } else {
                        // FIX 2026-01-06: No time specified, use Date object directly
                        // Bug: Template literal ${dateObject} converts to locale string, creating invalid format
                        // Fix: Use the Date object as-is from PostgreSQL
                        // Note: parseDateTimeForDB stores dates using local date (not UTC), so
                        // the Date object from pg already represents the correct day in user's timezone
                        fullDueDate = task.due_date instanceof Date ? task.due_date : new Date(task.due_date);
                      }
                    }

                    const countdown = getLiveCountdown(fullDueDate, timezone);
                    console.log(`[COUNTDOWN DEBUG] countdown result:`, countdown);
                    if (!countdown) return null;

                    return {
                      type: 'div',
                      props: {
                        style: {
                          color: countdown.color || colors.textSecondary,
                          fontSize: typography.bodyMedium,
                          fontWeight: countdown.isUrgent ? 600 : 400,
                          marginLeft: dp(12, density),
                          marginTop: dp(2, density),
                          marginRight: dp(4, density),
                        },
                        children: countdown.text, // No emoji - Satori can't render it
                      },
                    };
                  })(),
                  // Time estimate
                  task.estimate_minutes ? {
                    type: 'div',
                    props: {
                      style: {
                        color: colors.textSecondary,
                        fontSize: typography.bodyMedium,
                        marginLeft: task.due_date ? dp(4, density) : dp(12, density),
                        marginTop: dp(2, density),
                      },
                      children: formatEstimate(task.estimate_minutes),
                    },
                  } : null,
                  contextTag,
                ].filter(Boolean),
              },
            },
          ].filter(Boolean),
        },
      };
    });

    // Current time - use user's timezone
    const currentTime = new Date().toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
      timeZone: timezone
    });

    // Build intelligent message element if provided
    const messageElement = intelligentMessage ? {
      type: 'div',
      props: {
        style: {
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: margins.vertical + dp(8, density),
          padding: `${dp(6, density)}px ${dp(10, density)}px`,
          backgroundColor: 'rgba(255, 255, 255, 0.08)',
          borderRadius: dp(6, density),
        },
        children: {
          type: 'div',
          props: {
            style: {
              color: colors.textPrimary,
              fontSize: typography.bodyLarge,
              fontWeight: 400,
              textAlign: 'center',
              textShadow: '0 0 15px rgba(255,255,255,0.3)',
            },
            children: intelligentMessage.text,
          },
        },
      },
    } : null;

    // Simple layout - no time display (device lock screen shows time)
    element = {
      type: 'div',
      props: {
        style: {
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          padding: margins.horizontal,
          paddingTop: taskZone.y + margins.vertical,
        },
        children: [
          // Intelligent Message (from Message Engine)
          messageElement,
          // Header - Context-aware based on due dates
          {
            type: 'div',
            props: {
              style: {
                color: colors.textSecondary,
                fontSize: typography.labelLarge,
                letterSpacing: 2,
                marginBottom: margins.vertical + 10,
                textShadow: '2px 2px 4px rgba(0,0,0,0.4)',
              },
              children: getContextHeader(topTasks),
            },
          },
          // Tasks
          ...taskElements,
          // Remaining count
          remainingCount > 0 ? {
            type: 'div',
            props: {
              style: {
                color: colors.textSecondary,
                fontSize: typography.bodyMedium,
                marginTop: dp(5, density),
              },
              children: `+ ${remainingCount} more`,
            },
          } : null,
        ].filter(Boolean),
      },
    };
  }

  // Generate SVG using satori
  const svg = await satori(element, {
    width,
    height,
    fonts,
  });

  return svg;
}

/**
 * Get context-aware header based on task due dates
 * @param {array} tasks - Array of top tasks
 * @returns {string} Header text
 */
function getContextHeader(tasks) {
  if (!tasks || tasks.length === 0) return 'YOUR TASKS';

  const now = new Date();
  const firstTask = tasks[0];
  const dueDate = firstTask.due_date ? new Date(firstTask.due_date) : null;

  if (!dueDate) {
    return 'FOCUS ON';
  }

  const hoursUntil = (dueDate - now) / (1000 * 60 * 60);

  if (hoursUntil < 0) {
    return 'OVERDUE';
  }
  if (hoursUntil < 2) {
    return 'RIGHT NOW';
  }
  if (hoursUntil < 24 && dueDate.toDateString() === now.toDateString()) {
    return 'TODAY';
  }

  // Check if tomorrow
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (dueDate.toDateString() === tomorrow.toDateString()) {
    return 'TOMORROW';
  }

  // Check if this week
  const daysUntil = Math.ceil(hoursUntil / 24);
  if (daysUntil <= 7) {
    return 'THIS WEEK';
  }

  return 'UPCOMING';
}

/**
 * Format time estimate
 */
function formatEstimate(minutes) {
  if (minutes < 60) return `~${minutes}min`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins === 0 ? `~${hours}h` : `~${hours}h ${mins}m`;
}

/**
 * Get live countdown for tasks due within 24 hours
 * @param {Date} dueDate - Task due date (in UTC)
 * @param {string} timezone - User's timezone (currently unused, for future formatting)
 * @returns {object|null} Countdown object or null
 */
function getLiveCountdown(dueDate, timezone = 'UTC') {
  if (!dueDate) return null;

  // FIX 2026-01-06: Just use current UTC time directly
  // Bug: Converting to locale string and parsing creates wrong Date object
  // Old: new Date(new Date().toLocaleString('en-US', { timeZone: timezone }))
  //      This interprets IST time as UTC, causing 5.5h offset
  // Fix: Use new Date() directly - both dueDate and now are in UTC
  const now = new Date();
  const due = new Date(dueDate);
  const diffMs = due - now;
  const diffMinutes = Math.floor(diffMs / (1000 * 60));

  // Only show countdown for tasks within 24 hours
  if (Math.abs(diffMinutes) > 24 * 60) {
    // For tasks more than 24h away, show simple day count
    const days = Math.ceil(diffMinutes / (24 * 60));
    if (days === 1) {
      return { text: 'DUE TOMORROW', isUrgent: false, color: null };
    } else if (days > 1 && days <= 7) {
      return { text: `DUE IN ${days}D`, isUrgent: false, color: null };
    }
    return null; // Don't show countdown for tasks > 1 week
  }

  // Overdue
  if (diffMinutes < 0) {
    const absMinutes = Math.abs(diffMinutes);
    const hours = Math.floor(absMinutes / 60);
    const mins = absMinutes % 60;

    let text;
    if (hours === 0) {
      text = mins > 0 ? `${mins}M OVERDUE` : 'OVERDUE';
    } else if (hours < 24) {
      text = mins === 0 ? `${hours}H OVERDUE` : `${hours}H ${mins}M OVERDUE`;
    } else {
      const days = Math.floor(hours / 24);
      text = `${days}D OVERDUE`;
    }

    return { text, isUrgent: true, color: '#FF4444' };
  }

  // Due soon
  const hours = Math.floor(diffMinutes / 60);
  const mins = diffMinutes % 60;

  let text;
  if (hours === 0) {
    text = mins > 0 ? `DUE IN ${mins}M` : 'DUE NOW';
  } else if (hours < 24) {
    text = mins === 0 ? `DUE IN ${hours}H` : `DUE IN ${hours}H ${mins}M`;
  } else {
    text = 'DUE TOMORROW';
  }

  // Mark as urgent if due within 2 hours
  const isUrgent = diffMinutes < 120;
  const color = isUrgent ? '#FF4444' : null;

  return { text, isUrgent, color };
}

module.exports = {
  renderTextToSvg,
};
