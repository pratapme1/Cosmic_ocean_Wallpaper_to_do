/**
 * Comprehensive Atmosphere Controller Test Suite
 * Tests urgency calculation and visual parameter mapping
 *
 * Test Coverage:
 * - Urgency score calculation (overdue, critical, urgent, attention)
 * - Score to state conversion
 * - Visual parameter retrieval
 * - Task count calculations
 * - Animation and color multiplier helpers
 * - Edge cases and null handling
 */

const {
  AtmosphereController,
  URGENCY_STATES,
  VISUAL_PARAMS,
  SCORE_THRESHOLDS
} = require('../services/atmosphere-controller');

describe('Atmosphere Controller - Comprehensive Test Suite', () => {
  let controller;

  beforeEach(() => {
    controller = new AtmosphereController();
  });

  // ============================================================
  // URGENCY SCORE CALCULATION TESTS (10 cases)
  // ============================================================
  describe('Urgency Score Calculation', () => {
    test('returns 0 for no tasks', () => {
      const score = controller.calculateUrgencyScore([]);
      expect(score).toBe(0);
    });

    test('returns 0 for null tasks', () => {
      const score = controller.calculateUrgencyScore(null);
      expect(score).toBe(0);
    });

    test('returns 0 when all tasks are completed', () => {
      const tasks = [
        { id: '1', title: 'Done', completed: true },
        { id: '2', title: 'Also Done', completed: true }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(0);
    });

    test('returns 0 when all tasks are archived', () => {
      const tasks = [
        { id: '1', title: 'Archived', archived: true },
        { id: '2', title: 'Also Archived', archived: true }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(0);
    });

    test('adds overdue weight (25) for overdue task', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Overdue', dueDate: pastDue.toISOString(), completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(25);
    });

    test('adds critical weight (20) for task due within 2 hours', () => {
      const now = new Date();
      const soonDue = new Date(now.getTime() + 1 * 60 * 60 * 1000); // 1 hour

      const tasks = [
        { id: '1', title: 'Critical', dueDate: soonDue.toISOString(), completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(20);
    });

    test('adds urgent weight (10) for task due within 8 hours', () => {
      const now = new Date();
      const dueIn5Hours = new Date(now.getTime() + 5 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Urgent', dueDate: dueIn5Hours.toISOString(), completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(10);
    });

    test('adds attention weight (5) for task due within 24 hours', () => {
      const now = new Date();
      const dueIn12Hours = new Date(now.getTime() + 12 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Attention', dueDate: dueIn12Hours.toISOString(), completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(5);
    });

    test('adds 1 point for task with no due date', () => {
      const tasks = [
        { id: '1', title: 'No Due Date', completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(1);
    });

    test('adds priority bonus for high priority tasks', () => {
      const now = new Date();
      const dueIn12Hours = new Date(now.getTime() + 12 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'High Priority', dueDate: dueIn12Hours.toISOString(), priority: 1, completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      // attention weight (5) + priority bonus (5) = 10
      expect(score).toBe(10);
    });

    test('caps score at 100', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000);

      // 5 overdue tasks = 125 points, but capped at 100
      const tasks = Array(5).fill(null).map((_, i) => ({
        id: String(i),
        title: `Overdue ${i}`,
        dueDate: pastDue.toISOString(),
        completed: false
      }));

      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(100);
    });

    test('handles due_date snake_case property', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Overdue', due_date: pastDue.toISOString(), completed: false }
      ];
      const score = controller.calculateUrgencyScore(tasks);
      expect(score).toBe(25);
    });
  });

  // ============================================================
  // SCORE TO STATE CONVERSION TESTS (6 cases)
  // ============================================================
  describe('Score to State Conversion', () => {
    test('returns CLEAR for score 0-20', () => {
      expect(controller.scoreToState(0)).toBe(URGENCY_STATES.CLEAR);
      expect(controller.scoreToState(10)).toBe(URGENCY_STATES.CLEAR);
      expect(controller.scoreToState(20)).toBe(URGENCY_STATES.CLEAR);
    });

    test('returns CALM for score 21-40', () => {
      expect(controller.scoreToState(21)).toBe(URGENCY_STATES.CALM);
      expect(controller.scoreToState(30)).toBe(URGENCY_STATES.CALM);
      expect(controller.scoreToState(40)).toBe(URGENCY_STATES.CALM);
    });

    test('returns ATTENTION for score 41-60', () => {
      expect(controller.scoreToState(41)).toBe(URGENCY_STATES.ATTENTION);
      expect(controller.scoreToState(50)).toBe(URGENCY_STATES.ATTENTION);
      expect(controller.scoreToState(60)).toBe(URGENCY_STATES.ATTENTION);
    });

    test('returns URGENT for score 61-80', () => {
      expect(controller.scoreToState(61)).toBe(URGENCY_STATES.URGENT);
      expect(controller.scoreToState(70)).toBe(URGENCY_STATES.URGENT);
      expect(controller.scoreToState(80)).toBe(URGENCY_STATES.URGENT);
    });

    test('returns CRITICAL for score 81-100', () => {
      expect(controller.scoreToState(81)).toBe(URGENCY_STATES.CRITICAL);
      expect(controller.scoreToState(90)).toBe(URGENCY_STATES.CRITICAL);
      expect(controller.scoreToState(100)).toBe(URGENCY_STATES.CRITICAL);
    });

    test('returns CRITICAL for score above 100', () => {
      expect(controller.scoreToState(150)).toBe(URGENCY_STATES.CRITICAL);
    });
  });

  // ============================================================
  // VISUAL PARAMS TESTS (6 cases)
  // ============================================================
  describe('Visual Parameters', () => {
    test('returns correct params for CLEAR state', () => {
      const params = controller.getVisualParams(URGENCY_STATES.CLEAR, 10);
      expect(params.animationSpeed).toBe(0.5);
      expect(params.colorIntensity).toBe(0.6);
      expect(params.breathingRate).toBe(0.02);
    });

    test('returns correct params for CALM state', () => {
      const params = controller.getVisualParams(URGENCY_STATES.CALM, 30);
      expect(params.animationSpeed).toBe(0.8);
      expect(params.colorIntensity).toBe(0.7);
    });

    test('returns correct params for CRITICAL state', () => {
      const params = controller.getVisualParams(URGENCY_STATES.CRITICAL, 90);
      expect(params.animationSpeed).toBe(1.5);
      expect(params.colorIntensity).toBe(1.0);
      expect(params.breathingRate).toBe(0.08);
    });

    test('interpolates particle count within range', () => {
      const paramsLow = controller.getVisualParams(URGENCY_STATES.CALM, 21);
      const paramsHigh = controller.getVisualParams(URGENCY_STATES.CALM, 40);

      expect(paramsLow.particleCount).toBeLessThan(paramsHigh.particleCount);
    });

    test('returns calm params as fallback for unknown state', () => {
      const params = controller.getVisualParams('invalid_state', 50);
      expect(params).toEqual(VISUAL_PARAMS.calm);
    });

    test('all states have required visual properties', () => {
      for (const state of Object.values(URGENCY_STATES)) {
        const params = VISUAL_PARAMS[state];
        expect(params).toHaveProperty('particleCount');
        expect(params).toHaveProperty('animationSpeed');
        expect(params).toHaveProperty('colorIntensity');
        expect(params).toHaveProperty('breathingRate');
        expect(params).toHaveProperty('glowIntensity');
        expect(params).toHaveProperty('transitionDuration');
      }
    });
  });

  // ============================================================
  // CALCULATE STATE TESTS (4 cases)
  // ============================================================
  describe('Calculate State', () => {
    test('returns complete state object', () => {
      const tasks = [{ id: '1', title: 'Task', completed: false }];
      const state = controller.calculateState(tasks);

      expect(state).toHaveProperty('urgencyScore');
      expect(state).toHaveProperty('state');
      expect(state).toHaveProperty('stateName');
      expect(state).toHaveProperty('visualParams');
      expect(state).toHaveProperty('taskCounts');
      expect(state).toHaveProperty('timestamp');
    });

    test('returns CLEAR state for empty tasks', () => {
      const state = controller.calculateState([]);
      expect(state.state).toBe(URGENCY_STATES.CLEAR);
      expect(state.urgencyScore).toBe(0);
    });

    test('correctly categorizes mixed task states', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 1 * 60 * 60 * 1000);
      const dueIn3Hours = new Date(now.getTime() + 3 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Overdue', dueDate: pastDue.toISOString(), completed: false },
        { id: '2', title: 'Urgent', dueDate: dueIn3Hours.toISOString(), completed: false }
      ];

      const state = controller.calculateState(tasks);
      // overdue (25) + urgent (10) = 35, which is CALM state
      expect(state.urgencyScore).toBe(35);
      expect(state.state).toBe(URGENCY_STATES.CALM);
    });

    test('timestamp is valid ISO string', () => {
      const state = controller.calculateState([]);
      expect(() => new Date(state.timestamp)).not.toThrow();
    });
  });

  // ============================================================
  // TASK COUNTS TESTS (5 cases)
  // ============================================================
  describe('Task Counts', () => {
    test('returns zero counts for null tasks', () => {
      const counts = controller.getTaskCounts(null);
      expect(counts).toEqual({ total: 0, overdue: 0, urgent: 0, upcoming: 0, noDate: 0 });
    });

    test('counts overdue tasks correctly', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 1 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', dueDate: pastDue.toISOString(), completed: false },
        { id: '2', dueDate: pastDue.toISOString(), completed: false }
      ];

      const counts = controller.getTaskCounts(tasks);
      expect(counts.overdue).toBe(2);
      expect(counts.total).toBe(2);
    });

    test('counts urgent tasks (< 8 hours) correctly', () => {
      const now = new Date();
      const dueIn4Hours = new Date(now.getTime() + 4 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', dueDate: dueIn4Hours.toISOString(), completed: false }
      ];

      const counts = controller.getTaskCounts(tasks);
      expect(counts.urgent).toBe(1);
    });

    test('counts upcoming tasks (> 8 hours) correctly', () => {
      const now = new Date();
      const dueIn24Hours = new Date(now.getTime() + 24 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', dueDate: dueIn24Hours.toISOString(), completed: false }
      ];

      const counts = controller.getTaskCounts(tasks);
      expect(counts.upcoming).toBe(1);
    });

    test('counts tasks with no due date correctly', () => {
      const tasks = [
        { id: '1', title: 'No date', completed: false },
        { id: '2', title: 'Also no date', completed: false }
      ];

      const counts = controller.getTaskCounts(tasks);
      expect(counts.noDate).toBe(2);
    });

    test('excludes completed and archived tasks', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 1 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', dueDate: pastDue.toISOString(), completed: true },
        { id: '2', dueDate: pastDue.toISOString(), archived: true },
        { id: '3', dueDate: pastDue.toISOString(), completed: false }
      ];

      const counts = controller.getTaskCounts(tasks);
      expect(counts.total).toBe(1);
      expect(counts.overdue).toBe(1);
    });
  });

  // ============================================================
  // HELPER METHODS TESTS (5 cases)
  // ============================================================
  describe('Helper Methods', () => {
    test('getDominantUrgency returns state string', () => {
      const tasks = [];
      const urgency = controller.getDominantUrgency(tasks);
      expect(urgency).toBe(URGENCY_STATES.CLEAR);
    });

    test('shouldAnimate returns true for attention+ states', () => {
      expect(controller.shouldAnimate('attention')).toBe(true);
      expect(controller.shouldAnimate('urgent')).toBe(true);
      expect(controller.shouldAnimate('critical')).toBe(true);
    });

    test('shouldAnimate returns false for calm states', () => {
      expect(controller.shouldAnimate('clear')).toBe(false);
      expect(controller.shouldAnimate('calm')).toBe(false);
    });

    test('getColorMultiplier returns correct intensity', () => {
      expect(controller.getColorMultiplier('clear')).toBe(0.6);
      expect(controller.getColorMultiplier('critical')).toBe(1.0);
    });

    test('getColorMultiplier returns default for unknown state', () => {
      expect(controller.getColorMultiplier('unknown')).toBe(0.7);
    });

    test('getParticleDensity returns correct count', () => {
      expect(controller.getParticleDensity('clear')).toBe(30);
      expect(controller.getParticleDensity('critical')).toBe(100);
    });

    test('getAnimationSpeed returns correct multiplier', () => {
      expect(controller.getAnimationSpeed('clear')).toBe(0.5);
      expect(controller.getAnimationSpeed('critical')).toBe(1.5);
    });
  });

  // ============================================================
  // CUSTOM OPTIONS TESTS (3 cases)
  // ============================================================
  describe('Custom Options', () => {
    test('respects custom overdue weight', () => {
      const customController = new AtmosphereController({ overdueWeight: 50 });
      const now = new Date();
      const pastDue = new Date(now.getTime() - 1 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', dueDate: pastDue.toISOString(), completed: false }
      ];

      const score = customController.calculateUrgencyScore(tasks);
      expect(score).toBe(50);
    });

    test('respects custom max score', () => {
      const customController = new AtmosphereController({ maxScore: 50, overdueWeight: 30 });
      const now = new Date();
      const pastDue = new Date(now.getTime() - 1 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', dueDate: pastDue.toISOString(), completed: false },
        { id: '2', dueDate: pastDue.toISOString(), completed: false }
      ];

      const score = customController.calculateUrgencyScore(tasks);
      expect(score).toBe(50); // Capped at maxScore
    });

    test('merges options with defaults', () => {
      const customController = new AtmosphereController({ overdueWeight: 50 });
      expect(customController.options.overdueWeight).toBe(50);
      expect(customController.options.criticalWeight).toBe(20); // Default
    });
  });

  // ============================================================
  // URGENCY STATES AND THRESHOLDS TESTS (2 cases)
  // ============================================================
  describe('Constants', () => {
    test('URGENCY_STATES has all 5 states', () => {
      expect(Object.keys(URGENCY_STATES)).toHaveLength(5);
      expect(URGENCY_STATES).toHaveProperty('CLEAR');
      expect(URGENCY_STATES).toHaveProperty('CALM');
      expect(URGENCY_STATES).toHaveProperty('ATTENTION');
      expect(URGENCY_STATES).toHaveProperty('URGENT');
      expect(URGENCY_STATES).toHaveProperty('CRITICAL');
    });

    test('SCORE_THRESHOLDS covers 0-100 range without gaps', () => {
      expect(SCORE_THRESHOLDS.clear.min).toBe(0);
      expect(SCORE_THRESHOLDS.clear.max).toBe(20);
      expect(SCORE_THRESHOLDS.calm.min).toBe(20);
      expect(SCORE_THRESHOLDS.calm.max).toBe(40);
      expect(SCORE_THRESHOLDS.attention.min).toBe(40);
      expect(SCORE_THRESHOLDS.attention.max).toBe(60);
      expect(SCORE_THRESHOLDS.urgent.min).toBe(60);
      expect(SCORE_THRESHOLDS.urgent.max).toBe(80);
      expect(SCORE_THRESHOLDS.critical.min).toBe(80);
      expect(SCORE_THRESHOLDS.critical.max).toBe(100);
    });
  });
});
