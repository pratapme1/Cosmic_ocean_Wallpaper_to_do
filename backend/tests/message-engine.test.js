/**
 * Comprehensive Message Engine Test Suite
 * Tests intelligent message generation for wallpaper
 *
 * Test Coverage:
 * - Critical message generation (overdue, urgent)
 * - Achievement message generation (streak, daily, weekly, milestone)
 * - Pattern-based messages
 * - Time context messages (morning, afternoon, evening, night)
 * - Encouragement messages (fallback)
 * - Helper functions (formatTimeAgo, formatTimeLeft, truncateText)
 * - Edge cases and null handling
 */

const { MessageEngine, MESSAGE_TYPES, MESSAGE_TEMPLATES } = require('../services/message-engine');

describe('Message Engine - Comprehensive Test Suite', () => {
  let engine;

  beforeEach(() => {
    engine = new MessageEngine();
  });

  // ============================================================
  // CRITICAL MESSAGE TESTS (5 cases)
  // ============================================================
  describe('Critical Messages', () => {
    test('returns overdue message for task past due date', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000); // 2 hours ago

      const tasks = [
        { id: '1', title: 'Overdue Task', dueDate: pastDue.toISOString(), completed: false, archived: false }
      ];

      const message = engine.generateMessage({}, tasks, now);

      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.CRITICAL);
      expect(message.type).toBe('CRITICAL');
    });

    test('returns urgent message for task due within 2 hours', () => {
      const now = new Date();
      const soonDue = new Date(now.getTime() + 1 * 60 * 60 * 1000); // 1 hour from now

      const tasks = [
        { id: '1', title: 'Urgent Task', dueDate: soonDue.toISOString(), completed: false, archived: false }
      ];

      const message = engine.generateMessage({}, tasks, now);

      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.CRITICAL);
      expect(message.text).toContain('Urgent Task');
    });

    test('prioritizes overdue over urgent tasks', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 1 * 60 * 60 * 1000); // 1 hour ago
      const soonDue = new Date(now.getTime() + 1 * 60 * 60 * 1000); // 1 hour from now

      const tasks = [
        { id: '1', title: 'Urgent Task', dueDate: soonDue.toISOString(), completed: false, archived: false },
        { id: '2', title: 'Overdue Task', dueDate: pastDue.toISOString(), completed: false, archived: false }
      ];

      const message = engine.generateMessage({}, tasks, now);

      expect(message.priority).toBe(MESSAGE_TYPES.CRITICAL);
      expect(message.text).toContain('Overdue');
    });

    test('ignores completed tasks for critical messages', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Done Task', dueDate: pastDue.toISOString(), completed: true, archived: false }
      ];

      const message = engine.generateMessage({}, tasks, now);

      // Should not be critical since task is completed
      expect(message.priority).not.toBe(MESSAGE_TYPES.CRITICAL);
    });

    test('ignores archived tasks for critical messages', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Archived Task', dueDate: pastDue.toISOString(), completed: false, archived: true }
      ];

      const message = engine.generateMessage({}, tasks, now);

      expect(message.priority).not.toBe(MESSAGE_TYPES.CRITICAL);
    });
  });

  // ============================================================
  // ACHIEVEMENT MESSAGE TESTS (6 cases)
  // ============================================================
  describe('Achievement Messages', () => {
    test('returns all-done message when all tasks completed', () => {
      const tasks = [
        { id: '1', title: 'Task 1', completed: true, archived: false },
        { id: '2', title: 'Task 2', completed: true, archived: false }
      ];

      const message = engine.generateMessage({}, tasks, new Date());

      expect(message.priority).toBe(MESSAGE_TYPES.ACHIEVEMENT);
      expect(message.type).toBe('ACHIEVEMENT');
    });

    test('returns streak message for 3+ day streak', () => {
      const tasks = [];
      const stats = { streakDays: 5, completedToday: 1 };

      const message = engine.generateMessage({}, tasks, new Date(), stats);

      expect(message.priority).toBe(MESSAGE_TYPES.ACHIEVEMENT);
      expect(message.text).toContain('5');
    });

    test('returns daily message for 5+ completions today', () => {
      const tasks = [{ id: '1', title: 'Active', completed: false, archived: false }];
      const stats = { streakDays: 0, completedToday: 7 };

      const message = engine.generateMessage({}, tasks, new Date(), stats);

      expect(message.priority).toBe(MESSAGE_TYPES.ACHIEVEMENT);
      expect(message.text).toContain('7');
    });

    test('returns weekly message for 20+ weekly completions', () => {
      const tasks = [{ id: '1', title: 'Active', completed: false, archived: false }];
      const stats = { streakDays: 0, completedToday: 2, completedThisWeek: 25 };

      const message = engine.generateMessage({}, tasks, new Date(), stats);

      expect(message.priority).toBe(MESSAGE_TYPES.ACHIEVEMENT);
      expect(message.text).toContain('25');
    });

    test('returns milestone message at 100 completions', () => {
      const tasks = [{ id: '1', title: 'Active', completed: false, archived: false }];
      const stats = { streakDays: 0, completedToday: 1, completedThisWeek: 5, totalCompleted: 105 };

      const message = engine.generateMessage({}, tasks, new Date(), stats);

      expect(message.priority).toBe(MESSAGE_TYPES.ACHIEVEMENT);
      expect(message.text).toContain('100');
    });

    test('does not return achievement for streak < 3 days', () => {
      const tasks = [{ id: '1', title: 'Active', completed: false, archived: false }];
      const stats = { streakDays: 2, completedToday: 1, completedThisWeek: 3 };

      const message = engine.generateMessage({}, tasks, new Date(), stats);

      // Should fall through to time context or encouragement
      expect(message.priority).not.toBe(MESSAGE_TYPES.ACHIEVEMENT);
    });
  });

  // ============================================================
  // TIME CONTEXT MESSAGE TESTS (4 cases)
  // ============================================================
  describe('Time Context Messages', () => {
    test('returns morning message from 5am-12pm', () => {
      const morningTime = new Date();
      morningTime.setHours(9, 0, 0, 0);

      const tasks = [{ id: '1', title: 'Morning Task', completed: false, archived: false }];

      const message = engine.getTimeContextMessage(morningTime, tasks);

      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.TIME_CONTEXT);
    });

    test('returns afternoon message from 12pm-5pm', () => {
      const afternoonTime = new Date();
      afternoonTime.setHours(14, 0, 0, 0);

      const tasks = [{ id: '1', title: 'Afternoon Task', completed: false, archived: false }];

      const message = engine.getTimeContextMessage(afternoonTime, tasks);

      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.TIME_CONTEXT);
    });

    test('returns evening message from 5pm-9pm', () => {
      const eveningTime = new Date();
      eveningTime.setHours(19, 0, 0, 0);

      const tasks = [{ id: '1', title: 'Evening Task', completed: false, archived: false }];

      const message = engine.getTimeContextMessage(eveningTime, tasks);

      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.TIME_CONTEXT);
    });

    test('returns night message from 9pm-5am', () => {
      const nightTime = new Date();
      nightTime.setHours(23, 0, 0, 0);

      const tasks = [{ id: '1', title: 'Night Task', completed: false, archived: false }];

      const message = engine.getTimeContextMessage(nightTime, tasks);

      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.TIME_CONTEXT);
    });
  });

  // ============================================================
  // ENCOURAGEMENT MESSAGE TESTS (3 cases)
  // ============================================================
  describe('Encouragement Messages', () => {
    test('returns empty message when no tasks', () => {
      const message = engine.getEncouragementMessage([]);

      expect(message.priority).toBe(MESSAGE_TYPES.ENCOURAGEMENT);
      expect(message.type).toBe('ENCOURAGEMENT');
    });

    test('returns general encouragement with active tasks', () => {
      const tasks = [{ id: '1', title: 'Task', completed: false, archived: false }];

      const message = engine.getEncouragementMessage(tasks);

      expect(message.priority).toBe(MESSAGE_TYPES.ENCOURAGEMENT);
    });

    test('returns default message on null template', () => {
      const message = engine.selectMessage(null, {}, MESSAGE_TYPES.ENCOURAGEMENT);

      expect(message.text).toBe('🎯 Focus on the ONE thing');
    });
  });

  // ============================================================
  // HELPER FUNCTION TESTS (8 cases)
  // ============================================================
  describe('Helper Functions', () => {
    describe('formatTimeAgo', () => {
      test('formats minutes ago', () => {
        const result = engine.formatTimeAgo(15 * 60 * 1000); // 15 minutes
        expect(result).toBe('15 min ago');
      });

      test('formats hours ago (singular)', () => {
        const result = engine.formatTimeAgo(1 * 60 * 60 * 1000); // 1 hour
        expect(result).toBe('1 hour ago');
      });

      test('formats hours ago (plural)', () => {
        const result = engine.formatTimeAgo(3 * 60 * 60 * 1000); // 3 hours
        expect(result).toBe('3 hours ago');
      });

      test('formats days ago', () => {
        const result = engine.formatTimeAgo(2 * 24 * 60 * 60 * 1000); // 2 days
        expect(result).toBe('2 days ago');
      });

      test('formats just now', () => {
        const result = engine.formatTimeAgo(30 * 1000); // 30 seconds
        expect(result).toBe('just now');
      });
    });

    describe('formatTimeLeft', () => {
      test('formats minutes left', () => {
        const result = engine.formatTimeLeft(45 * 60 * 1000); // 45 minutes
        expect(result).toBe('45 min');
      });

      test('formats hours with minutes', () => {
        const result = engine.formatTimeLeft(90 * 60 * 1000); // 1.5 hours
        expect(result).toBe('1h 30m');
      });

      test('formats whole hours', () => {
        const result = engine.formatTimeLeft(2 * 60 * 60 * 1000); // 2 hours
        expect(result).toBe('2 hours');
      });
    });

    describe('truncateText', () => {
      test('returns original text if short enough', () => {
        const result = engine.truncateText('Short text', 20);
        expect(result).toBe('Short text');
      });

      test('truncates long text with ellipsis', () => {
        const result = engine.truncateText('This is a very long task title that exceeds the limit', 20);
        expect(result.length).toBe(20);
        expect(result).toContain('...');
      });

      test('handles null text', () => {
        const result = engine.truncateText(null, 20);
        expect(result).toBe('Task');
      });
    });
  });

  // ============================================================
  // EDGE CASES (5 cases)
  // ============================================================
  describe('Edge Cases', () => {
    test('handles null tasks array', () => {
      const message = engine.generateMessage({}, null, new Date());
      expect(message).not.toBeNull();
    });

    test('handles empty tasks array', () => {
      const message = engine.generateMessage({}, [], new Date());
      expect(message).not.toBeNull();
      expect(message.priority).toBe(MESSAGE_TYPES.ENCOURAGEMENT);
    });

    test('handles undefined user', () => {
      const tasks = [{ id: '1', title: 'Task', completed: false, archived: false }];
      const message = engine.generateMessage(undefined, tasks, new Date());
      expect(message).not.toBeNull();
    });

    test('handles task with due_date instead of dueDate', () => {
      const now = new Date();
      const pastDue = new Date(now.getTime() - 2 * 60 * 60 * 1000);

      const tasks = [
        { id: '1', title: 'Task', due_date: pastDue.toISOString(), completed: false, archived: false }
      ];

      const message = engine.generateMessage({}, tasks, now);
      expect(message.priority).toBe(MESSAGE_TYPES.CRITICAL);
    });

    test('getPriorityName returns ENCOURAGEMENT for unknown priority', () => {
      const result = engine.getPriorityName(99);
      expect(result).toBe('ENCOURAGEMENT');
    });
  });

  // ============================================================
  // PATTERN MESSAGE TESTS (2 cases - randomness makes these probabilistic)
  // ============================================================
  describe('Pattern Messages', () => {
    test('returns null for empty stats', () => {
      const message = engine.getPatternMessage(null);
      expect(message).toBeNull();
    });

    test('returns null for stats without patterns', () => {
      const stats = { completedToday: 5 };
      const message = engine.getPatternMessage(stats);
      expect(message).toBeNull();
    });
  });

  // ============================================================
  // MESSAGE TEMPLATE STRUCTURE TESTS (3 cases)
  // ============================================================
  describe('Message Templates', () => {
    test('all critical templates have text and icon', () => {
      for (const category of Object.values(MESSAGE_TEMPLATES.critical)) {
        for (const template of category) {
          expect(template).toHaveProperty('text');
          expect(template).toHaveProperty('icon');
        }
      }
    });

    test('all achievement templates have text and icon', () => {
      for (const category of Object.values(MESSAGE_TEMPLATES.achievement)) {
        for (const template of category) {
          expect(template).toHaveProperty('text');
          expect(template).toHaveProperty('icon');
        }
      }
    });

    test('all timeContext templates have text and icon', () => {
      for (const category of Object.values(MESSAGE_TEMPLATES.timeContext)) {
        for (const template of category) {
          expect(template).toHaveProperty('text');
          expect(template).toHaveProperty('icon');
        }
      }
    });
  });
});
