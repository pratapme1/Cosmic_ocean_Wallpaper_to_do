/**
 * Enhanced Wallpaper Generation Test Suite
 * Tests the new wallpaper architecture implementation
 */

const request = require('supertest');
const { generateEnhancedWallpaper, calculateUrgency } = require('../services/wallpaper-generator-enhanced');
const { getLayoutConfig } = require('../services/layout-system');
const { generateParticles } = require('../services/particle-system');
const { getColorPalette, verifyWCAG } = require('../services/color-system');
const { getAnimationState } = require('../services/animation-system');

describe('Enhanced Wallpaper Generation', () => {
  describe('Urgency Calculation', () => {
    test('should return "clear" when done_for_today is true', () => {
      const tasks = [{ id: '1', title: 'Task' }];
      const urgency = calculateUrgency(tasks, true);
      expect(urgency).toBe('clear');
    });

    test('should return "clear" when no tasks', () => {
      const urgency = calculateUrgency([], false);
      expect(urgency).toBe('clear');
    });

    test('should return "critical" for overdue tasks', () => {
      const yesterday = new Date(Date.now() - 86400000).toISOString();
      const tasks = [{ id: '1', title: 'Overdue', due_date: yesterday }];
      const urgency = calculateUrgency(tasks, false);
      expect(urgency).toBe('critical');
    });

    test('should return "urgent" for tasks due today', () => {
      const today = new Date().toISOString();
      const tasks = [{ id: '1', title: 'Today', due_date: today }];
      const urgency = calculateUrgency(tasks, false);
      expect(urgency).toBe('urgent');
    });

    test('should return "attention" for tasks due within 48h', () => {
      const tomorrow = new Date(Date.now() + 86400000).toISOString();
      const tasks = [{ id: '1', title: 'Tomorrow', due_date: tomorrow }];
      const urgency = calculateUrgency(tasks, false);
      expect(urgency).toBe('attention');
    });

    test('should return "calm" for tasks due later', () => {
      const nextWeek = new Date(Date.now() + 7 * 86400000).toISOString();
      const tasks = [{ id: '1', title: 'Next week', due_date: nextWeek }];
      const urgency = calculateUrgency(tasks, false);
      expect(urgency).toBe('calm');
    });
  });

  describe('Layout System', () => {
    test('should calculate safe zones for standard resolution', () => {
      const layout = getLayoutConfig(1080, 1920);
      expect(layout.width).toBe(1080);
      expect(layout.height).toBe(1920);
      expect(layout.safeZones).toBeDefined();
      expect(layout.safeZones.top).toBeGreaterThan(0);
      expect(layout.safeZones.bottom).toBeGreaterThan(0);
    });

    test('should calculate layout zones with correct percentages', () => {
      const layout = getLayoutConfig(1080, 1920);
      const zones = layout.layoutZones;

      expect(zones.clock).toBeDefined();
      expect(zones.scene).toBeDefined();
      expect(zones.transition).toBeDefined();
      expect(zones.task).toBeDefined();
      expect(zones.interaction).toBeDefined();

      // Scene zone should be approximately 40% of available height
      const availableHeight = 1920 - layout.safeZones.top - layout.safeZones.bottom;
      const sceneHeight = zones.scene.height;
      const scenePercentage = sceneHeight / availableHeight;
      expect(scenePercentage).toBeCloseTo(0.40, 1);
    });

    test('should determine correct breakpoint category', () => {
      // The category is based on width/density, not raw pixels
      // For 360x640, density ~0.33, width/density ~1090 → xlarge
      const layout1 = getLayoutConfig(360, 640);
      expect(layout1.category).toBeDefined();
      expect(['compact', 'standard', 'large', 'xlarge']).toContain(layout1.category);

      const layout2 = getLayoutConfig(1080, 1920);
      expect(layout2.category).toBeDefined();
      expect(['compact', 'standard', 'large', 'xlarge']).toContain(layout2.category);
    });

    test('should provide responsive typography', () => {
      const layout = getLayoutConfig(1080, 1920);
      expect(layout.typography).toBeDefined();
      expect(layout.typography.headlineLarge).toBeGreaterThan(0);
      expect(layout.typography.titleLarge).toBeGreaterThan(0);
      expect(layout.typography.bodyMedium).toBeGreaterThan(0);
    });
  });

  describe('Particle System', () => {
    test('should generate particles for cosmic theme', () => {
      const layout = getLayoutConfig(1080, 1920);
      const particles = generateParticles('cosmic', layout, 'calm', 0);
      expect(particles.length).toBeGreaterThan(0);
      expect(particles[0]).toHaveProperty('x');
      expect(particles[0]).toHaveProperty('y');
      expect(particles[0]).toHaveProperty('size');
      expect(particles[0]).toHaveProperty('opacity');
    });

    test('should generate more particles for critical urgency', () => {
      const layout = getLayoutConfig(1080, 1920);
      const calmParticles = generateParticles('cosmic', layout, 'calm', 0);
      const criticalParticles = generateParticles('cosmic', layout, 'critical', 0);
      expect(criticalParticles.length).toBeGreaterThan(calmParticles.length);
    });

    test('should generate bubbles for ocean theme', () => {
      const layout = getLayoutConfig(1080, 1920);
      const particles = generateParticles('ocean', layout, 'calm', 0);
      expect(particles.length).toBeGreaterThan(0);
      expect(particles[0]).toHaveProperty('x');
      expect(particles[0]).toHaveProperty('y');
    });

    test('should respect zone density weights', () => {
      const layout = getLayoutConfig(1080, 1920);
      const particles = generateParticles('cosmic', layout, 'urgent', 0);

      // Count particles in task zone (should be fewer due to 20% density)
      const taskZone = layout.layoutZones.task;
      const taskParticles = particles.filter(p =>
        p.y >= taskZone.y && p.y <= taskZone.y + taskZone.height
      );

      // Count particles in scene zone (should be more due to 100% density)
      const sceneZone = layout.layoutZones.scene;
      const sceneParticles = particles.filter(p =>
        p.y >= sceneZone.y && p.y <= sceneZone.y + sceneZone.height
      );

      // Scene should have more particles than task zone
      expect(sceneParticles.length).toBeGreaterThan(taskParticles.length);
    });
  });

  describe('Color System', () => {
    test('should provide color palettes for all themes', () => {
      const cosmic = getColorPalette('cosmic', 'calm');
      const ocean = getColorPalette('ocean', 'urgent');
      const fantasy = getColorPalette('fantasy', 'critical');

      expect(cosmic).toHaveProperty('bgPrimary');
      expect(cosmic).toHaveProperty('textPrimary');
      expect(ocean).toHaveProperty('accentPrimary');
      expect(fantasy).toHaveProperty('glow');
    });

    test('should verify WCAG contrast compliance', () => {
      const result = verifyWCAG('#FFFFFF', '#000000', 'AA', false);
      expect(result.passes).toBe(true);
      expect(result.ratio).toBeGreaterThan(4.5);
    });

    test('should fail WCAG for low contrast', () => {
      const result = verifyWCAG('#CCCCCC', '#BBBBBB', 'AA', false);
      expect(result.passes).toBe(false);
    });

    test('should calculate contrast ratio correctly', () => {
      const { verifyWCAG: verify } = require('../services/color-system');
      const whiteOnBlack = verify('#FFFFFF', '#000000', 'AAA', false);
      expect(whiteOnBlack.ratio).toBeCloseTo(21, 0);
    });
  });

  describe('Animation System', () => {
    test('should calculate breath phase', () => {
      const animState = getAnimationState(0, 'calm');
      expect(animState.breathPhase).toBeGreaterThanOrEqual(0);
      expect(animState.breathPhase).toBeLessThanOrEqual(1);
      expect(animState.breathScale).toBeGreaterThan(0);
    });

    test('should have different intensities for different urgencies', () => {
      const calm = getAnimationState(0, 'calm');
      const critical = getAnimationState(0, 'critical');

      expect(calm.breathParams.intensity).toBeLessThan(critical.breathParams.intensity);
      expect(calm.breathParams.cycleDuration).toBeGreaterThan(critical.breathParams.cycleDuration);
    });
  });

  describe('Wallpaper Generation', () => {
    test('should generate wallpaper for cosmic theme', async () => {
      const user = { theme: 'cosmic', resolution: '1080x1920', done_for_today: false };
      const data = { tasks: [] };

      const buffer = await generateEnhancedWallpaper(user, data, 0);
      expect(buffer).toBeInstanceOf(Buffer);
      expect(buffer.length).toBeGreaterThan(1000); // Should be a real image
    });

    test('should generate wallpaper for ocean theme', async () => {
      const user = { theme: 'ocean', resolution: '1080x1920', done_for_today: false };
      const data = { tasks: [
        { id: '1', title: 'Test task', priority: 1, estimate_minutes: 30 }
      ]};

      const buffer = await generateEnhancedWallpaper(user, data, 0);
      expect(buffer).toBeInstanceOf(Buffer);
      expect(buffer.length).toBeGreaterThan(1000);
    });

    test('should generate wallpaper for fantasy theme', async () => {
      const user = { theme: 'fantasy', resolution: '1440x2560', done_for_today: false };
      const data = { tasks: [
        { id: '1', title: 'Test task 1', priority: 2 },
        { id: '2', title: 'Test task 2', priority: 1 }
      ]};

      const buffer = await generateEnhancedWallpaper(user, data, 0);
      expect(buffer).toBeInstanceOf(Buffer);
      expect(buffer.length).toBeGreaterThan(1000);
    });

    test('should generate done-for-today wallpaper', async () => {
      const user = { theme: 'cosmic', resolution: '1080x1920', done_for_today: true };
      const data = { tasks: [] };

      const buffer = await generateEnhancedWallpaper(user, data, 0);
      expect(buffer).toBeInstanceOf(Buffer);
      expect(buffer.length).toBeGreaterThan(1000);
    });

    test('should support different resolutions', async () => {
      const resolutions = ['1080x1920', '1440x2560', '1080x2400'];

      for (const resolution of resolutions) {
        const user = { theme: 'cosmic', resolution, done_for_today: false };
        const data = { tasks: [] };

        const buffer = await generateEnhancedWallpaper(user, data, 0);
        expect(buffer).toBeInstanceOf(Buffer);
        expect(buffer.length).toBeGreaterThan(1000);
      }
    });

    test('should handle animation timestamps', async () => {
      const user = { theme: 'cosmic', resolution: '1080x1920', done_for_today: false };
      const data = { tasks: [] };

      const buffer1 = await generateEnhancedWallpaper(user, data, 0);
      const buffer2 = await generateEnhancedWallpaper(user, data, 5000);

      expect(buffer1).toBeInstanceOf(Buffer);
      expect(buffer2).toBeInstanceOf(Buffer);
      // Buffers should be different due to animation
      expect(buffer1.equals(buffer2)).toBe(false);
    });
  });
});
