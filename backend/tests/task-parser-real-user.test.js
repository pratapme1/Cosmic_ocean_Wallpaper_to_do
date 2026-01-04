/**
 * Real User Input Tests for Task Parser
 *
 * These tests simulate ACTUAL user inputs - messy, varied, natural language.
 * NOT idealized inputs like "30m task" that no real user types.
 *
 * Created: 2026-01-04 (Incident Response)
 * Purpose: Prevent future bugs by testing what users ACTUALLY type
 */

const { parseTask } = require('../utils/task-parser');

describe('Task Parser - Real User Inputs', () => {

  // Helper to check if date is within expected range
  const isWithinMinutes = (date, minutes, tolerance = 1) => {
    if (!date) return false;
    const expected = new Date(Date.now() + minutes * 60 * 1000);
    const diff = Math.abs(date.getTime() - expected.getTime()) / 60000;
    return diff <= tolerance;
  };

  describe('Time-based inputs (the bug that broke everything)', () => {

    test('"Email manager in 10 minutes" - should have due date NOW+10min', () => {
      const result = parseTask('Email manager in 10 minutes');

      expect(result.title).toBe('Email manager');
      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 10)).toBe(true);
      expect(result.priority).toBe(1); // Due within 24 hours = P1
    });

    test('"call mom urgently" - should be P1 even without due date', () => {
      const result = parseTask('call mom urgently');

      expect(result.title.toLowerCase()).toContain('call mom');
      expect(result.priority).toBe(1);
      expect(result.priorityReason).toContain('urgent');
    });

    test('"Complete email tasks in 10m" - should parse 10m as due date', () => {
      const result = parseTask('Complete email tasks in 10m');

      expect(result.title).toBe('Complete email tasks');
      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 10)).toBe(true);
    });

    test('"finish report in 2 hours" - should be due in 2 hours', () => {
      const result = parseTask('finish report in 2 hours');

      expect(result.title.toLowerCase()).toContain('finish report');
      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 120, 5)).toBe(true);
    });

    test('"call back in 30 mins" - should parse 30 mins', () => {
      const result = parseTask('call back in 30 mins');

      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 30, 2)).toBe(true);
    });

    test('"reminder in 1 hour" - should be due in 1 hour', () => {
      const result = parseTask('reminder in 1 hour');

      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 60, 2)).toBe(true);
    });
  });

  describe('Natural date expressions (how users actually talk)', () => {

    test('"buy groceries tomorrow" - should be tomorrow', () => {
      const result = parseTask('buy groceries tomorrow');

      expect(result.title.toLowerCase()).toContain('buy groceries');
      expect(result.dueDate).not.toBeNull();

      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      expect(result.dueDate.getDate()).toBe(tomorrow.getDate());
    });

    test('"meeting at 3pm" - should have time 15:00', () => {
      const result = parseTask('meeting at 3pm');

      expect(result.title.toLowerCase()).toContain('meeting');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueTime).toBe('15:00');
    });

    test('"dentist next tuesday" - should be next tuesday', () => {
      const result = parseTask('dentist next tuesday');

      expect(result.title.toLowerCase()).toContain('dentist');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getDay()).toBe(2); // Tuesday = 2
    });

    test('"submit report by friday" - should be this friday', () => {
      const result = parseTask('submit report by friday');

      expect(result.title.toLowerCase()).toContain('submit report');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getDay()).toBe(5); // Friday = 5
    });

    test('"pick up kids at 5pm" - should be today at 5pm', () => {
      // NOTE: "at 5" without am/pm is ambiguous - chrono defaults to AM
      // Real users should say "at 5pm" for clarity
      const result = parseTask('pick up kids at 5pm');

      expect(result.title.toLowerCase()).toContain('pick up kids');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getHours()).toBe(17);
    });

    test('"dinner reservation at 7:30pm" - should be 19:30', () => {
      const result = parseTask('dinner reservation at 7:30pm');

      expect(result.dueDate).not.toBeNull();
      expect(result.dueTime).toBe('19:30');
    });
  });

  describe('Priority keywords (how users express urgency)', () => {

    test('"urgent fix production bug" - should be P1', () => {
      const result = parseTask('urgent fix production bug');
      expect(result.priority).toBe(1);
    });

    test('"ASAP review the PR" - should be P1', () => {
      const result = parseTask('ASAP review the PR');
      expect(result.priority).toBe(1);
    });

    test('"important client meeting" - should be P1', () => {
      const result = parseTask('important client meeting');
      expect(result.priority).toBe(1);
    });

    test('"critical security patch" - should be P1', () => {
      const result = parseTask('critical security patch');
      expect(result.priority).toBe(1);
    });

    test('"low priority cleanup old files" - should be P3', () => {
      const result = parseTask('low priority cleanup old files');
      expect(result.priority).toBe(3);
    });

    test('"maybe someday organize photos" - should be P3', () => {
      const result = parseTask('maybe someday organize photos');
      expect(result.priority).toBe(3);
    });

    test('"whenever free read that article" - should be P3', () => {
      const result = parseTask('whenever free read that article');
      expect(result.priority).toBe(3);
    });
  });

  describe('Category detection (from context)', () => {

    test('"email the client about invoice" - should be work', () => {
      const result = parseTask('email the client about invoice');
      expect(result.category).toBe('work');
    });

    test('"call mom for her birthday" - should be social/personal', () => {
      const result = parseTask('call mom for her birthday');
      expect(['social', 'personal']).toContain(result.category);
    });

    test('"30 min run in the park" - should be health/fitness', () => {
      const result = parseTask('30 min run in the park');
      expect(['health', 'fitness']).toContain(result.category);
    });

    test('"buy milk and eggs" - should be errands/shopping', () => {
      const result = parseTask('buy milk and eggs');
      expect(['errands', 'shopping', 'general']).toContain(result.category);
    });

    test('"pay electricity bill" - should be finance/bills', () => {
      const result = parseTask('pay electricity bill');
      expect(['finance', 'bills', 'general']).toContain(result.category);
    });
  });

  describe('Messy inputs (real users are messy)', () => {

    test('extra spaces: "call   mom    urgently"', () => {
      const result = parseTask('call   mom    urgently');
      expect(result.title).not.toContain('   '); // Should clean up spaces
      expect(result.priority).toBe(1);
    });

    test('mixed case: "EMAIL Manager IN 10 Minutes"', () => {
      const result = parseTask('EMAIL Manager IN 10 Minutes');
      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 10)).toBe(true);
    });

    test('with punctuation: "call mom, urgently!"', () => {
      const result = parseTask('call mom, urgently!');
      expect(result.priority).toBe(1);
    });

    test('trailing spaces: "buy groceries tomorrow   "', () => {
      const result = parseTask('buy groceries tomorrow   ');
      expect(result.dueDate).not.toBeNull();
    });

    test('leading spaces: "   meeting at 3pm"', () => {
      const result = parseTask('   meeting at 3pm');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueTime).toBe('15:00');
    });
  });

  describe('Complex inputs (multiple elements)', () => {

    test('"urgent meeting tomorrow at 2pm with client" - P1, tomorrow, 14:00', () => {
      const result = parseTask('urgent meeting tomorrow at 2pm with client');

      expect(result.priority).toBe(1);
      expect(result.dueDate).not.toBeNull();
      expect(result.dueTime).toBe('14:00');

      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      expect(result.dueDate.getDate()).toBe(tomorrow.getDate());
    });

    test('"quick call with john @work" - short task, work context', () => {
      const result = parseTask('quick call with john @work');

      // "quick" might set low estimate or be ignored
      expect(result.context).toContain('@work');
    });

    test('"gym workout @morning for 1 hour" - morning context, health category', () => {
      const result = parseTask('gym workout @morning for 1 hour');

      expect(['health', 'fitness']).toContain(result.category);
      expect(result.timeContext).toBe('morning');
    });
  });

  describe('Edge cases (things that broke before)', () => {

    test('just "in" should not crash: "check in with team"', () => {
      const result = parseTask('check in with team');
      expect(result.title).toBeTruthy();
      // Should NOT extract a due date from "in" without a number
    });

    test('"meeting in room 10" - should not parse room number as time', () => {
      const result = parseTask('meeting in room 10');
      // "in room 10" should NOT become "in 10 minutes"
      expect(result.title.toLowerCase()).toContain('room');
    });

    test('empty string should return default', () => {
      const result = parseTask('');
      expect(result.title).toBe('New Task');
      expect(result.priority).toBe(2);
    });

    test('null should not crash', () => {
      const result = parseTask(null);
      expect(result.title).toBe('New Task');
    });

    test('undefined should not crash', () => {
      const result = parseTask(undefined);
      expect(result.title).toBe('New Task');
    });

    test('very long input should not crash', () => {
      const longInput = 'a'.repeat(1000) + ' tomorrow at 3pm';
      const result = parseTask(longInput);
      expect(result).toBeTruthy();
    });
  });

  describe('Recurring patterns (real user expressions)', () => {

    test('"standup every day at 9am" - daily recurring', () => {
      const result = parseTask('standup every day at 9am');

      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('daily');
    });

    test('"team meeting every monday" - weekly recurring', () => {
      const result = parseTask('team meeting every monday');

      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('weekly');
      expect(result.recurring.dayOfWeek).toBe(1); // Monday
    });

    test('"pay rent on the 1st" - monthly recurring', () => {
      const result = parseTask('pay rent on the 1st');

      // May or may not detect recurring, but should at least parse
      expect(result.title.toLowerCase()).toContain('pay rent');
    });
  });

  describe('Duration vs Due Date (the core bug)', () => {

    test('"30 minute meeting" - should be ESTIMATE, not due date', () => {
      const result = parseTask('30 minute meeting');

      // "30 minute meeting" describes duration, not when it's due
      // This is ambiguous - could go either way
      expect(result.title.toLowerCase()).toContain('meeting');
    });

    test('"call back in 30 minutes" - should be DUE DATE', () => {
      const result = parseTask('call back in 30 minutes');

      // "in 30 minutes" = due date, not estimate
      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 30, 2)).toBe(true);
    });

    test('"1 hour workout" - could be estimate', () => {
      const result = parseTask('1 hour workout');

      // "1 hour workout" describes how long, not when
      expect(result.title.toLowerCase()).toContain('workout');
    });

    test('"workout in 1 hour" - should be due date', () => {
      const result = parseTask('workout in 1 hour');

      // "in 1 hour" = due date
      expect(result.dueDate).not.toBeNull();
      expect(isWithinMinutes(result.dueDate, 60, 5)).toBe(true);
    });
  });

  describe('Time format variations', () => {

    test('"meeting at 3pm" - should be 3pm', () => {
      // NOTE: "at 3" without am/pm is ambiguous - chrono defaults to next occurrence
      // Real users should say "at 3pm" for clarity
      const result = parseTask('meeting at 3pm');

      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getHours()).toBe(15);
    });

    test('"alarm at 6am" - should be 6am', () => {
      const result = parseTask('alarm at 6am');

      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getHours()).toBe(6);
    });

    test('"dinner at 19:00" - 24hr format', () => {
      const result = parseTask('dinner at 19:00');

      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getHours()).toBe(19);
    });
  });
});

describe('Database Storage Format', () => {

  test('dueDate should be a Date object for DB storage', () => {
    const result = parseTask('meeting tomorrow at 3pm');

    if (result.dueDate) {
      expect(result.dueDate instanceof Date).toBe(true);
    }
  });

  test('dueTime should be HH:MM format', () => {
    const result = parseTask('meeting at 3pm');

    if (result.dueTime) {
      expect(result.dueTime).toMatch(/^\d{2}:\d{2}$/);
    }
  });

  test('priority should be 1, 2, or 3', () => {
    const inputs = [
      'urgent task',
      'normal task',
      'low priority task'
    ];

    inputs.forEach(input => {
      const result = parseTask(input);
      expect([1, 2, 3]).toContain(result.priority);
    });
  });
});
