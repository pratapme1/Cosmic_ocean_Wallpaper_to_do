/**
 * Text Renderer using Satori
 * Converts text to SVG paths - works on serverless without system fonts
 */

const satori = require('satori').default;
const fs = require('fs');
const path = require('path');

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
 * Render text layer to SVG using satori
 * @param {object} layout - Layout configuration
 * @param {array} tasks - Array of tasks
 * @param {object} colors - Color palette
 * @param {boolean} doneForToday - Done for today flag
 * @param {object} intelligentMessage - Optional intelligent message from MessageEngine
 * @returns {Promise<string>} SVG string
 */
async function renderTextToSvg(layout, tasks, colors, doneForToday, intelligentMessage = null) {
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
    // Task list
    const topTasks = tasks.slice(0, 3);
    const remainingCount = Math.max(0, tasks.length - 3);

    const taskElements = topTasks.map((task, index) => {
      const priorityColor = task.priority === 2 ? colors.accentSecondary :
                           task.priority === 1 ? colors.accentPrimary :
                           colors.textSecondary;

      // Truncate long titles
      const maxTitleLength = 35;
      let title = task.title || 'Untitled';
      if (title.length > maxTitleLength) {
        title = title.substring(0, maxTitleLength - 3) + '...';
      }

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
                        // Combine date and time: "2026-01-03" + "20:27:49"
                        fullDueDate = new Date(`${task.due_date}T${task.due_time}`);
                        console.log(`[COUNTDOWN DEBUG] Task: ${task.title}`);
                        console.log(`[COUNTDOWN DEBUG] due_date: ${task.due_date}, due_time: ${task.due_time}`);
                        console.log(`[COUNTDOWN DEBUG] fullDueDate: ${fullDueDate}`);
                      } else {
                        // Just date, use end of day
                        fullDueDate = new Date(`${task.due_date}T23:59:59`);
                      }
                    }

                    const countdown = getLiveCountdown(fullDueDate);
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

    // Current time
    const currentTime = new Date().toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
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
          // Spacer
          {
            type: 'div',
            props: {
              style: { flex: 1 },
            },
          },
          // Time at bottom
          {
            type: 'div',
            props: {
              style: {
                color: colors.textSecondary,
                fontSize: typography.displayLarge,
                fontWeight: 300,
                textAlign: 'center',
                opacity: 0.5,
                marginBottom: margins.vertical,
              },
              children: currentTime,
            },
          },
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
 * @param {Date} dueDate - Task due date
 * @returns {object|null} Countdown object or null
 */
function getLiveCountdown(dueDate) {
  if (!dueDate) return null;

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
