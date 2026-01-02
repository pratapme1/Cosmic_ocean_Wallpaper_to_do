const sharp = require('sharp');
const path = require('path');

const THEMES = {
  cosmic: {
    clear: { r: 10, g: 20, b: 60, title: '#FFFFFF', meta: '#00E5FF' },
    calm: { r: 20, g: 20, b: 25, title: '#FFFFFF', meta: '#3AA0FF' }, // Blue
    urgent: { r: 30, g: 30, b: 60, title: '#FFFFFF', meta: '#FF9A3A' }, // Orange
    critical: { r: 60, g: 10, b: 10, title: '#FFFFFF', meta: '#FF3B30' } // Red
  },
  ocean: {
    clear: { r: 20, g: 120, b: 140, title: '#FFFFFF', meta: '#00FFD9' }, // Celebration - bright teal
    calm: { r: 10, g: 60, b: 100, title: '#FFFFFF', meta: '#76D7FF' }, // Gentle waves - deep blue
    urgent: { r: 15, g: 85, b: 120, title: '#FFFFFF', meta: '#FFD700' }, // Clouds building - medium blue with gold accent
    critical: { r: 5, g: 30, b: 60, title: '#FFFFFF', meta: '#FF6B6B' } // Storm - very dark blue with red accent
  },
  fantasy: {
    clear: { r: 80, g: 40, b: 100, title: '#FFFFFF', meta: '#FFD700' }, // Celebration - purple with gold
    calm: { r: 40, g: 20, b: 60, title: '#FFFFFF', meta: '#B8A0FF' }, // Peaceful forest - deep purple
    urgent: { r: 60, g: 30, b: 80, title: '#FFFFFF', meta: '#FFA500' }, // Magic rising - medium purple with orange
    critical: { r: 80, g: 10, b: 40, title: '#FFFFFF', meta: '#FF3366' } // Dragon fire - dark magenta with bright red
  }
};

/**
 * Generates an SVG string for the 3-task overlay.
 */
function generateOverlaySVG(data, dims, colors) {
  const { tasks = [], remainingCount, currentTime } = data;
  const { width, height } = dims;

  const fontSizeTitle = Math.floor(width * 0.06);
  const fontSizeTask = Math.floor(width * 0.045);
  const fontSizeMeta = Math.floor(width * 0.03);
  const fontSizeTime = Math.floor(width * 0.10);
  
  const textX = Math.floor(width * 0.15);
  let currentY = Math.floor(height * 0.45);

  let tasksHtml = '';
  tasks.forEach((task, index) => {
    const taskColor = task.priority === 1 ? '#FF3B30' : (task.priority === 2 ? '#FF9A3A' : '#3AA0FF');
    tasksHtml += `
      <circle cx="${textX - 30}" cy="${currentY - 15}" r="6" fill="${taskColor}" />
      <text x="${textX}" y="${currentY}" class="task-text">${task.title}</text>
      <text x="${textX}" y="${currentY + 35}" class="meta-text">${task.estimate_minutes ? `~${task.estimate_minutes}m` : ''}</text>
    `;
    currentY += 100;
  });

  return `
    <svg width="${width}" height="${height}">
      <style>
        .header { fill: rgba(255,255,255,0.5); font-family: sans-serif; font-size: ${fontSizeMeta}px; letter-spacing: 2px; }
        .task-text { fill: white; font-family: sans-serif; font-weight: bold; font-size: ${fontSizeTask}px; }
        .meta-text { fill: rgba(255,255,255,0.6); font-family: sans-serif; font-size: ${fontSizeMeta}px; }
        .time { fill: rgba(255,255,255,0.3); font-family: sans-serif; font-weight: 100; font-size: ${fontSizeTime}px; }
      </style>
      
      <text x="${textX}" y="${Math.floor(height * 0.40)}" class="header">RIGHT NOW</text>
      ${tasksHtml}
      
      ${remainingCount > 0 ? `<text x="${textX}" y="${currentY + 20}" class="meta-text">+ ${remainingCount} more today</text>` : ''}
      
      <text x="50%" y="${height - 100}" text-anchor="middle" class="time">${currentTime}</text>
    </svg>
  `;
}

function getUrgencyLevel(topTask) {
  if (!topTask) return 'clear';
  if (topTask.priority === 1) return 'critical';
  if (topTask.priority === 2) return 'urgent';
  return 'calm';
}

/**
 * Generates a simple text-only fallback wallpaper when main generation fails
 */
function generateFallbackWallpaper(width, height, errorMessage = 'Unable to generate wallpaper') {
  const svgFallback = `
    <svg width="${width}" height="${height}">
      <style>
        .error-text { fill: white; font-family: sans-serif; font-size: ${Math.floor(width * 0.04)}px; text-anchor: middle; }
        .icon { fill: rgba(255,255,255,0.3); font-size: ${Math.floor(width * 0.08)}px; }
      </style>
      <rect width="${width}" height="${height}" fill="rgb(20,20,40)" />
      <text x="50%" y="45%" class="icon">⚠️</text>
      <text x="50%" y="55%" class="error-text">${errorMessage}</text>
    </svg>
  `;

  return sharp(Buffer.from(svgFallback)).png().toBuffer();
}

async function generateWallpaper(user, data) {
  try {
    const { theme = 'cosmic', resolution } = user;
    const [width, height] = (resolution || '1080x1920').split('x').map(Number);

    const tasks = data.tasks || [];
    const topTask = tasks.length > 0 ? tasks[0] : null;
    const urgency = getUrgencyLevel(topTask);

    // Get theme palette, fallback to cosmic if theme doesn't exist
    const themeData = THEMES[theme] || THEMES.cosmic;
    const palette = themeData[urgency];

    const svgData = {
      tasks: tasks,
      remainingCount: data.count || 0,
      currentTime: new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
    };

    const background = { create: { width, height, channels: 4, background: { r: palette.r, g: palette.g, b: palette.b, alpha: 1 } } };
    const svgBuffer = Buffer.from(generateOverlaySVG(svgData, { width, height }, palette));

    return await sharp(background).composite([{ input: svgBuffer, blend: 'over' }]).png().toBuffer();
  } catch (err) {
    console.error('Wallpaper generation failed, using fallback:', err);
    // Return simple text-only fallback
    const { resolution } = user;
    const [width, height] = (resolution || '1080x1920').split('x').map(Number);
    return await generateFallbackWallpaper(width, height, 'Cosmic Ocean');
  }
}

module.exports = { generateWallpaper };