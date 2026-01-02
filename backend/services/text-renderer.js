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

/**
 * Render text layer to SVG using satori
 * @param {object} layout - Layout configuration
 * @param {array} tasks - Array of tasks
 * @param {object} colors - Color palette
 * @param {boolean} doneForToday - Done for today flag
 * @returns {Promise<string>} SVG string
 */
async function renderTextToSvg(layout, tasks, colors, doneForToday) {
  const { width, height, layoutZones, typography, margins } = layout;
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
                marginTop: 20,
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

      return {
        type: 'div',
        props: {
          key: index,
          style: {
            display: 'flex',
            flexDirection: 'column',
            marginBottom: margins.vertical,
          },
          children: [
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
                        width: 12,
                        height: 12,
                        borderRadius: '50%',
                        backgroundColor: priorityColor,
                        marginRight: 12,
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
                ],
              },
            },
            task.estimate_minutes ? {
              type: 'div',
              props: {
                style: {
                  color: colors.textSecondary,
                  fontSize: typography.bodyMedium,
                  marginLeft: 24,
                  marginTop: 4,
                },
                children: formatEstimate(task.estimate_minutes),
              },
            } : null,
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
          // Header
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
              children: 'RIGHT NOW',
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
                marginTop: 10,
              },
              children: `+ ${remainingCount} more today`,
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
 * Format time estimate
 */
function formatEstimate(minutes) {
  if (minutes < 60) return `~${minutes}min`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return mins === 0 ? `~${hours}h` : `~${hours}h ${mins}m`;
}

module.exports = {
  renderTextToSvg,
};
