/**
 * Color System for Wallpaper Themes
 * Based on ANDROID_UI_UX_DESIGN_SYSTEM.md specifications
 * Includes WCAG-compliant contrast ratios
 */

/**
 * Cosmic Theme Color Palette - PURPLE/BLUE SKY (Space/Nebula)
 */
const COSMIC_PALETTE = {
  calm: {
    bgPrimary: '#1A0F3C',      // Deep purple-blue (twilight sky)
    bgSecondary: '#2D1B5E',    // Rich purple (night sky)
    accentPrimary: '#7B68EE',  // Medium slate blue
    accentSecondary: '#9370DB', // Medium purple
    glow: 'rgba(123, 104, 238, 0.4)',
    particle: '#E6E6FA',       // Lavender stars
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.85)'
  },
  attention: {
    bgPrimary: '#2D1B5E',      // Rich purple
    bgSecondary: '#3F2A7A',    // Purple
    accentPrimary: '#8B7BFF',  // Bright purple
    accentSecondary: '#7B68EE',
    glow: 'rgba(139, 123, 255, 0.45)',
    particle: '#D8BFD8',       // Thistle
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.85)'
  },
  urgent: {
    bgPrimary: '#4B3682',      // Deep purple
    bgSecondary: '#5E4A9E',    // Purple
    accentPrimary: '#9D7FFF',  // Bright purple
    accentSecondary: '#8B7BFF',
    glow: 'rgba(157, 127, 255, 0.5)',
    particle: '#DDA0DD',       // Plum
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  },
  critical: {
    bgPrimary: '#6A4C9C',      // Medium purple (nebula)
    bgSecondary: '#8B6FBD',    // Light purple
    accentPrimary: '#BA55D3',  // Medium orchid
    accentSecondary: '#9D7FFF',
    glow: 'rgba(186, 85, 211, 0.6)',
    particle: '#EE82EE',       // Violet stars
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.95)'
  },
  clear: {
    bgPrimary: '#4169E1',      // Royal blue (clear sky)
    bgSecondary: '#6495ED',    // Cornflower blue
    accentPrimary: '#87CEEB',  // Sky blue
    accentSecondary: '#00BFFF',
    glow: 'rgba(135, 206, 235, 0.5)',
    particle: '#F0F8FF',       // Alice blue
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  }
};

/**
 * Ocean Theme Color Palette - LIGHT BLUE (Tropical Water/Aqua)
 */
const OCEAN_PALETTE = {
  calm: {
    bgPrimary: '#1E90FF',      // Dodger blue (deep water)
    bgSecondary: '#4682B4',    // Steel blue
    accentPrimary: '#00CED1',  // Dark turquoise
    accentSecondary: '#48D1CC', // Medium turquoise
    glow: 'rgba(0, 206, 209, 0.5)',
    particle: '#E0FFFF',       // Light cyan bubbles
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  },
  attention: {
    bgPrimary: '#00BFFF',      // Deep sky blue
    bgSecondary: '#1E90FF',    // Dodger blue
    accentPrimary: '#40E0D0',  // Turquoise
    accentSecondary: '#00CED1',
    glow: 'rgba(64, 224, 208, 0.55)',
    particle: '#AFEEEE',       // Pale turquoise
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  },
  urgent: {
    bgPrimary: '#00CED1',      // Dark turquoise
    bgSecondary: '#20B2AA',    // Light sea green
    accentPrimary: '#7FFFD4',  // Aquamarine
    accentSecondary: '#40E0D0',
    glow: 'rgba(127, 255, 212, 0.6)',
    particle: '#B0E0E6',       // Powder blue
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.95)'
  },
  critical: {
    bgPrimary: '#006080',      // Deep ocean blue (WCAG compliant - was #00CED1)
    bgSecondary: '#008BA3',    // Dark teal (was #48D1CC)
    accentPrimary: '#00CED1',  // Bright turquoise accent (moved from bg)
    accentSecondary: '#40E0D0',
    glow: 'rgba(0, 206, 209, 0.7)',
    particle: '#E0FFFF',       // Light cyan
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.95)'
  },
  clear: {
    bgPrimary: '#87CEEB',      // Sky blue (calm water)
    bgSecondary: '#87CEFA',    // Light sky blue
    accentPrimary: '#ADD8E6',  // Light blue
    accentSecondary: '#B0E0E6',
    glow: 'rgba(173, 216, 230, 0.6)',
    particle: '#F0F8FF',       // Alice blue
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  }
};

/**
 * Fantasy Theme Color Palette - BOLD VIBRANT (Magical/Neon)
 */
const FANTASY_PALETTE = {
  calm: {
    bgPrimary: '#C71585',      // Medium violet red
    bgSecondary: '#DA70D6',    // Orchid
    accentPrimary: '#FF1493',  // Deep pink
    accentSecondary: '#FF69B4', // Hot pink
    glow: 'rgba(255, 20, 147, 0.5)',
    particle: '#FFB6C1',       // Light pink sparkles
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  },
  attention: {
    bgPrimary: '#DA70D6',      // Orchid
    bgSecondary: '#EE82EE',    // Violet
    accentPrimary: '#FF1493',  // Deep pink
    accentSecondary: '#FF00FF', // Magenta
    glow: 'rgba(255, 20, 147, 0.6)',
    particle: '#FFB6C1',       // Light pink
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  },
  urgent: {
    bgPrimary: '#FF1493',      // Deep pink (bold)
    bgSecondary: '#FF69B4',    // Hot pink
    accentPrimary: '#FF00FF',  // Magenta
    accentSecondary: '#FF1493',
    glow: 'rgba(255, 0, 255, 0.7)',
    particle: '#FFBBFF',       // Bright pink
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.95)'
  },
  critical: {
    bgPrimary: '#8B008B',      // Dark magenta (WCAG compliant - was #FF00FF)
    bgSecondary: '#9B0063',    // Deep magenta (was #FF1493)
    accentPrimary: '#FF00FF',  // Bright magenta accent (moved from bg)
    accentSecondary: '#FF1493', // Deep pink
    glow: 'rgba(255, 0, 255, 0.8)',
    particle: '#FFE4E1',       // Misty rose
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.95)'
  },
  clear: {
    bgPrimary: '#FFB6C1',      // Light pink (gentle magical)
    bgSecondary: '#FFC0CB',    // Pink
    accentPrimary: '#FFD700',  // Gold
    accentSecondary: '#FFA500', // Orange
    glow: 'rgba(255, 215, 0, 0.6)',
    particle: '#FFF5EE',       // Seashell
    textPrimary: '#FFFFFF',
    textSecondary: 'rgba(255, 255, 255, 0.9)'
  }
};

/**
 * Get color palette for theme and urgency
 */
function getColorPalette(theme, urgency) {
  const palettes = {
    cosmic: COSMIC_PALETTE,
    ocean: OCEAN_PALETTE,
    fantasy: FANTASY_PALETTE
  };

  const themePalette = palettes[theme] || COSMIC_PALETTE;
  return themePalette[urgency] || themePalette.calm;
}

/**
 * Parse hex color to RGB components
 */
function hexToRgb(hex) {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : { r: 0, g: 0, b: 0 };
}

/**
 * Parse rgba color string to components
 */
function parseRgba(rgba) {
  const match = rgba.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
  if (match) {
    return {
      r: parseInt(match[1]),
      g: parseInt(match[2]),
      b: parseInt(match[3]),
      a: match[4] ? parseFloat(match[4]) : 1
    };
  }
  return { r: 0, g: 0, b: 0, a: 1 };
}

/**
 * Linear interpolation between two colors
 * @param {string} fromColor - Starting color (hex or rgba)
 * @param {string} toColor - Ending color (hex or rgba)
 * @param {number} progress - Progress from 0 to 1
 * @returns {string} Interpolated color as rgba string
 */
function lerpColor(fromColor, toColor, progress) {
  const from = fromColor.startsWith('#') ? hexToRgb(fromColor) : parseRgba(fromColor);
  const to = toColor.startsWith('#') ? hexToRgb(toColor) : parseRgba(toColor);

  const r = Math.round(from.r + (to.r - from.r) * progress);
  const g = Math.round(from.g + (to.g - from.g) * progress);
  const b = Math.round(from.b + (to.b - from.b) * progress);

  const fromA = from.a !== undefined ? from.a : 1;
  const toA = to.a !== undefined ? to.a : 1;
  const a = fromA + (toA - fromA) * progress;

  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

/**
 * Calculate relative luminance for WCAG contrast
 */
function getLuminance(r, g, b) {
  const [rs, gs, bs] = [r, g, b].map(c => {
    c = c / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

/**
 * Calculate WCAG contrast ratio between two colors
 * @param {string} color1 - First color (hex)
 * @param {string} color2 - Second color (hex)
 * @returns {number} Contrast ratio (1-21)
 */
function getContrastRatio(color1, color2) {
  const rgb1 = hexToRgb(color1);
  const rgb2 = hexToRgb(color2);

  const lum1 = getLuminance(rgb1.r, rgb1.g, rgb1.b);
  const lum2 = getLuminance(rgb2.r, rgb2.g, rgb2.b);

  const lighter = Math.max(lum1, lum2);
  const darker = Math.min(lum1, lum2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Verify WCAG compliance
 * @param {string} textColor - Text color
 * @param {string} bgColor - Background color
 * @param {string} level - AA or AAA
 * @param {boolean} isLargeText - Text is 24px+ or 18px+ bold
 * @returns {object} Compliance result
 */
function verifyWCAG(textColor, bgColor, level = 'AA', isLargeText = false) {
  const ratio = getContrastRatio(textColor, bgColor);
  const required = level === 'AAA'
    ? (isLargeText ? 4.5 : 7)
    : (isLargeText ? 3 : 4.5);

  return {
    ratio,
    required,
    passes: ratio >= required,
    level
  };
}

module.exports = {
  COSMIC_PALETTE,
  OCEAN_PALETTE,
  FANTASY_PALETTE,
  getColorPalette,
  hexToRgb,
  parseRgba,
  lerpColor,
  getContrastRatio,
  verifyWCAG
};
