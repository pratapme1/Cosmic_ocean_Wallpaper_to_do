/**
 * Layout System for Wallpaper Generation
 * Based on ANDROID_UI_UX_DESIGN_SYSTEM.md specifications
 *
 * Implements:
 * - Safe zone calculations
 * - Layout zones (percentage-based)
 * - Responsive breakpoints
 * - Typography scaling
 */

/**
 * Calculate safe zone insets based on device resolution
 * @param {number} width - Screen width in pixels
 * @param {number} height - Screen height in pixels
 * @returns {object} Safe zone insets
 */
function calculateSafeZones(width, height) {
  // Density calculation - optimized for lock screen readability
  // Reduced from 2.5x to 2.0x on large screens for better spacing
  // - 393px @ ~240dpi = 1.0x density (small screens)
  // - 720px @ ~320dpi = 1.5x density
  // - 1080px @ ~400dpi = 2.0x density (reduced from 2.5x)
  // - 1440px @ ~560dpi = 2.5x density (reduced from 3.5x)
  const density = width >= 1440 ? 2.5 :
                  width >= 1080 ? 2.0 :
                  width >= 720  ? 1.5 :
                  width >= 540  ? 1.2 :
                  1.0; // Small screens (< 540px) = 1.0x to fit content

  // Status bar height (typically 24-48dp)
  const statusBarHeight = Math.floor(32 * density);

  // Camera cutout height (0-32dp, reserve space)
  const cutoutHeight = Math.floor(20 * density);

  // Navigation bar (20-48dp depending on gesture/button nav)
  const navBarHeight = Math.floor(40 * density);

  // Lock screen clock zone (80-140dp, we reserve 140dp to cover all OEMs)
  const clockZoneHeight = Math.floor(120 * density);

  // Edge insets for horizontal margins
  const edgeInset = Math.floor(16 * density);

  return {
    top: Math.max(statusBarHeight, cutoutHeight),
    clockZone: clockZoneHeight,
    bottom: navBarHeight,
    left: edgeInset,
    right: edgeInset,
    density
  };
}

/**
 * Calculate layout zones using percentage-based distribution
 * Zone distribution:
 * - System Zone: 8% (status bar + cutout)
 * - Clock Zone: 12% (lock screen clock)
 * - Scene Zone: 40% (wallpaper beauty, particles)
 * - Transition Zone: 5% (gradient blend)
 * - Task Zone: 28% (task display)
 * - Interaction Zone: 7% (quick actions)
 * - Navigation Zone: 5% (nav bar)
 *
 * @param {number} width - Screen width
 * @param {number} height - Screen height
 * @param {object} safeInsets - Safe zone insets
 * @returns {object} Layout zones with coordinates
 */
function calculateLayoutZones(width, height, safeInsets) {
  const systemZoneHeight = safeInsets.top;
  const navZoneHeight = safeInsets.bottom;

  // Available height for our content
  const availableHeight = height - systemZoneHeight - navZoneHeight;

  // Percentage-based zones
  const clockZoneHeight = Math.floor(availableHeight * 0.12);
  const sceneZoneHeight = Math.floor(availableHeight * 0.40);
  const transitionZoneHeight = Math.floor(availableHeight * 0.05);
  const taskZoneHeight = Math.floor(availableHeight * 0.28);
  const interactionZoneHeight = Math.floor(availableHeight * 0.07);

  // Calculate Y positions
  let currentY = systemZoneHeight;

  const zones = {
    system: {
      y: 0,
      height: systemZoneHeight,
      centerY: systemZoneHeight / 2
    },
    clock: {
      y: currentY,
      height: clockZoneHeight,
      centerY: currentY + clockZoneHeight / 2
    },
    scene: {
      y: currentY + clockZoneHeight,
      height: sceneZoneHeight,
      centerY: currentY + clockZoneHeight + sceneZoneHeight / 2
    },
    transition: {
      y: currentY + clockZoneHeight + sceneZoneHeight,
      height: transitionZoneHeight,
      centerY: currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight / 2
    },
    task: {
      y: currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight,
      height: taskZoneHeight,
      centerY: currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight + taskZoneHeight / 2
    },
    interaction: {
      y: currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight + taskZoneHeight,
      height: interactionZoneHeight,
      centerY: currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight + taskZoneHeight + interactionZoneHeight / 2
    },
    navigation: {
      y: height - navZoneHeight,
      height: navZoneHeight,
      centerY: height - navZoneHeight / 2
    }
  };

  return zones;
}

/**
 * Get responsive breakpoint category
 * @param {number} width - Screen width in dp
 * @returns {string} Category: compact, standard, large, xlarge
 */
function getBreakpointCategory(width) {
  if (width < 360) return 'compact';
  if (width < 400) return 'standard';
  if (width < 600) return 'large';
  return 'xlarge';
}

/**
 * Calculate responsive margins based on breakpoint
 * @param {string} category - Breakpoint category
 * @param {number} density - Screen density
 * @returns {object} Margin values
 */
function getResponsiveMargins(category, density) {
  const baseDp = {
    compact: 16,
    standard: 24,
    large: 32,
    xlarge: 48
  };

  const dp = baseDp[category] || 24;
  return {
    horizontal: Math.floor(dp * density),
    vertical: Math.floor(dp * density * 0.75)
  };
}

/**
 * Typography scale based on Material Design 3
 * Optimized for lock screen readability
 * @param {number} density - Screen density
 * @param {string} category - Breakpoint category
 * @returns {object} Font sizes in pixels
 */
function getTypographyScale(density, category) {
  // Scale factor based on screen size
  const scaleFactor = {
    compact: 0.9,
    standard: 1.0,
    large: 1.05,
    xlarge: 1.1
  }[category] || 1.0;

  // Base sizes in sp (scaled pixels)
  const baseSizes = {
    displayLarge: 32,
    displayMedium: 28,
    headlineLarge: 24,
    headlineMedium: 20,
    titleLarge: 18,
    titleMedium: 16,
    bodyLarge: 16,
    bodyMedium: 14,
    labelLarge: 14,
    labelMedium: 12,
    labelSmall: 10
  };

  // Apply scale factor and density
  const scale = {};
  for (const [key, baseSp] of Object.entries(baseSizes)) {
    const scaledSize = baseSp * scaleFactor;
    // Enforce min/max bounds
    const minSize = baseSp * 0.85;
    const maxSize = baseSp * 1.2;
    const clampedSize = Math.max(minSize, Math.min(maxSize, scaledSize));
    scale[key] = Math.floor(clampedSize * density);
  }

  return scale;
}

/**
 * Get device-specific layout configuration
 * @param {number} width - Screen width
 * @param {number} height - Screen height
 * @returns {object} Complete layout configuration
 */
function getLayoutConfig(width, height) {
  const safeZones = calculateSafeZones(width, height);
  const layoutZones = calculateLayoutZones(width, height, safeZones);
  const category = getBreakpointCategory(width / safeZones.density);
  const margins = getResponsiveMargins(category, safeZones.density);
  const typography = getTypographyScale(safeZones.density, category);

  return {
    width,
    height,
    safeZones,
    layoutZones,
    category,
    margins,
    typography,
    density: safeZones.density
  };
}

module.exports = {
  calculateSafeZones,
  calculateLayoutZones,
  getBreakpointCategory,
  getResponsiveMargins,
  getTypographyScale,
  getLayoutConfig
};
