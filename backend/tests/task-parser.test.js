/**
 * Comprehensive NLP Task Parser Test Suite
 * 50+ test cases covering all parsing features
 */

const {
  parseTask,
  parseTimeEstimate,
  extractContextTags,
  extractRecurring,
  extractTimeContext,
  extractDuration,
  detectEnergy,
  inferPriority,
  detectCategory
} = require('../utils/task-parser');

describe('Task Parser - Comprehensive Test Suite', () => {
  // ============================================================
  // DURATION TESTS (10 cases)
  // ============================================================
  describe('Duration Parsing', () => {
    test('parses minutes format "30m"', () => {
      const result = parseTask('Review PR 30m');
      expect(result.estimateMinutes).toBe(30);
      expect(result.title).toBe('Review PR');
    });

    test('parses minutes format "30min"', () => {
      const result = parseTask('Quick call 30min');
      expect(result.estimateMinutes).toBe(30);
    });

    test('parses hours format "1h"', () => {
      const result = parseTask('Team meeting 1h');
      expect(result.estimateMinutes).toBe(60);
    });

    test('parses hours format "1.5h"', () => {
      const result = parseTask('Deep work session 1.5h');
      expect(result.estimateMinutes).toBe(90);
    });

    test('parses combined format "1h30m"', () => {
      const result = parseTask('Project review 1h30m');
      expect(result.estimateMinutes).toBe(90);
    });

    test('parses "2 hours" natural language', () => {
      const result = parseTask('Workshop 2 hours');
      expect(result.estimateMinutes).toBe(120);
    });

    test('parses "90 minutes" natural language', () => {
      const result = parseTask('Training session 90 minutes');
      expect(result.estimateMinutes).toBe(90);
    });

    test('parses "quick" keyword as 15 minutes', () => {
      const result = parseTask('Quick email check');
      expect(result.estimateMinutes).toBe(15);
    });

    test('parses "long" keyword as 120 minutes', () => {
      const result = parseTask('Long strategy session');
      expect(result.estimateMinutes).toBe(120);
    });

    test('returns null for no duration specified', () => {
      const result = parseTask('Buy groceries');
      expect(result.estimateMinutes).toBeNull();
    });
  });

  // ============================================================
  // DATE/TIME TESTS (15 cases)
  // ============================================================
  describe('Date/Time Parsing', () => {
    test('parses "tomorrow"', () => {
      const result = parseTask('Call mom tomorrow');
      expect(result.dueDate).not.toBeNull();
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      expect(result.dueDate.getDate()).toBe(tomorrow.getDate());
    });

    test('parses "next Monday"', () => {
      const result = parseTask('Team standup next Monday');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getDay()).toBe(1); // Monday
    });

    test('parses "in 3 days"', () => {
      const result = parseTask('Submit report in 3 days');
      expect(result.dueDate).not.toBeNull();
      const expected = new Date();
      expected.setDate(expected.getDate() + 3);
      expect(result.dueDate.getDate()).toBe(expected.getDate());
    });

    test('parses "at 3pm"', () => {
      const result = parseTask('Meeting at 3pm today');
      expect(result.dueTime).toBe('15:00');
    });

    test('parses "at 14:30"', () => {
      const result = parseTask('Call at 14:30 tomorrow');
      expect(result.dueTime).toBe('14:30');
    });

    test('handles "morning" time context', () => {
      const result = parseTask('Exercise in the morning');
      expect(result.timeContext).toBe('morning');
    });

    test('handles "afternoon" time context', () => {
      const result = parseTask('Meditation this afternoon');
      expect(result.timeContext).toBe('afternoon');
    });

    test('handles "tonight" time context', () => {
      const result = parseTask('Watch movie tonight');
      expect(result.timeContext).toBe('evening');
    });

    test('handles "after lunch" time context', () => {
      const result = parseTask('Call client after lunch');
      expect(result.timeContext).toBe('afternoon');
    });

    test('parses specific date "Dec 25"', () => {
      const result = parseTask('Christmas party Dec 25');
      expect(result.dueDate).not.toBeNull();
      expect(result.dueDate.getMonth()).toBe(11); // December
      expect(result.dueDate.getDate()).toBe(25);
    });

    test('parses ISO date "2026-01-15"', () => {
      const result = parseTask('Deadline 2026-01-15');
      expect(result.dueDate).not.toBeNull();
    });

    test('parses "next week"', () => {
      const result = parseTask('Review goals next week');
      expect(result.dueDate).not.toBeNull();
    });

    test('handles past dates gracefully', () => {
      const result = parseTask('Remember yesterday'); // chrono should handle
      // Past dates are still parsed, just with past date value
      expect(result.title).toContain('Remember');
    });

    test('handles ambiguous dates with forward preference', () => {
      // "Friday" should parse to next Friday if today is after Friday
      const result = parseTask('Meet Friday');
      expect(result.dueDate).not.toBeNull();
    });

    test('returns null for no date specified', () => {
      const result = parseTask('Buy groceries');
      expect(result.dueDate).toBeNull();
    });
  });

  // ============================================================
  // CONTEXT TAG TESTS (8 cases)
  // ============================================================
  describe('Context Tag Extraction', () => {
    test('extracts single @home tag', () => {
      const result = parseTask('@home clean kitchen');
      expect(result.context).toContain('@home');
      expect(result.title).toBe('Clean kitchen');
    });

    test('extracts single @work tag', () => {
      const result = parseTask('Finish report @work');
      expect(result.context).toContain('@work');
      expect(result.title).toBe('Finish report');
    });

    test('extracts multiple tags @work @computer', () => {
      const result = parseTask('@work @computer finish presentation');
      expect(result.context).toContain('@work');
      expect(result.context).toContain('@computer');
    });

    test('extracts @phone tag at end', () => {
      const result = parseTask('Call mom @phone');
      expect(result.context).toContain('@phone');
    });

    test('extracts @gym tag', () => {
      const result = parseTask('@gym upper body workout');
      expect(result.context).toContain('@gym');
    });

    test('extracts @errands tag', () => {
      const result = parseTask('Pick up dry cleaning @errands');
      expect(result.context).toContain('@errands');
    });

    test('returns empty array for no context', () => {
      const result = parseTask('Buy milk');
      expect(result.context).toEqual([]);
    });

    test('handles duplicate context tags', () => {
      const result = parseTask('@home @home clean');
      expect(result.context.filter(c => c === '@home').length).toBe(1);
    });
  });

  // ============================================================
  // PRIORITY TESTS (8 cases)
  // ============================================================
  describe('Priority Inference', () => {
    test('detects P1 from "URGENT:" prefix', () => {
      const result = parseTask('URGENT: fix production bug');
      expect(result.priority).toBe(1);
    });

    test('detects P1 from "important" keyword', () => {
      const result = parseTask('Important meeting prep');
      expect(result.priority).toBe(1);
    });

    test('detects P1 from "ASAP" keyword', () => {
      const result = parseTask('Review contract ASAP');
      expect(result.priority).toBe(1);
    });

    test('detects P1 from "critical" keyword', () => {
      const result = parseTask('Critical system update');
      expect(result.priority).toBe(1);
    });

    test('detects P3 from "someday" keyword', () => {
      const result = parseTask('Someday learn piano');
      expect(result.priority).toBe(3);
    });

    test('detects P3 from "when possible" keyword', () => {
      const result = parseTask('Review code when possible');
      expect(result.priority).toBe(3);
    });

    test('detects P3 from "maybe" keyword', () => {
      const result = parseTask('Maybe read that book');
      expect(result.priority).toBe(3);
    });

    test('defaults to P2 with no keywords', () => {
      const result = parseTask('Buy groceries');
      expect(result.priority).toBe(2);
    });
  });

  // ============================================================
  // CATEGORY TESTS (8 cases)
  // ============================================================
  describe('Category Detection', () => {
    test('detects health category from "workout"', () => {
      const result = parseTask('Morning workout at gym');
      expect(result.category).toBe('health');
    });

    test('detects health category from "gym" context', () => {
      const result = parseTask('@gym cardio session');
      expect(result.category).toBe('health');
    });

    test('detects finance category from "pay bill"', () => {
      const result = parseTask('Pay credit card bill');
      expect(result.category).toBe('finance');
    });

    test('detects learning category from "study"', () => {
      const result = parseTask('Study for exam');
      expect(result.category).toBe('learning');
    });

    test('detects social category from "dinner with friends"', () => {
      const result = parseTask('Dinner with friends');
      expect(result.category).toBe('social');
    });

    test('detects work category from "meeting"', () => {
      const result = parseTask('Team meeting 10am');
      expect(result.category).toBe('work');
    });

    test('detects errands category from "buy"', () => {
      const result = parseTask('Buy groceries at store');
      expect(result.category).toBe('errands');
    });

    test('defaults to general for unclear tasks', () => {
      const result = parseTask('Think about things');
      expect(result.category).toBe('general');
    });
  });

  // ============================================================
  // RECURRING PATTERN TESTS (7 cases)
  // ============================================================
  describe('Recurring Pattern Recognition', () => {
    test('recognizes "every day" as daily', () => {
      const result = parseTask('Water plants every day');
      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('daily');
    });

    test('recognizes "daily" as daily', () => {
      const result = parseTask('Daily meditation');
      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('daily');
    });

    test('recognizes "weekly" as weekly', () => {
      const result = parseTask('Weekly team meeting');
      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('weekly');
    });

    test('recognizes "every month" as monthly', () => {
      const result = parseTask('Pay rent every month');
      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('monthly');
    });

    test('recognizes "every Monday" with day of week', () => {
      const result = parseTask('Run every Monday');
      expect(result.recurring).not.toBeNull();
      expect(result.recurring.interval).toBe('weekly');
      expect(result.recurring.dayOfWeek).toBe(1);
    });

    test('recognizes "every Friday"', () => {
      const result = parseTask('Team lunch every Friday');
      expect(result.recurring).not.toBeNull();
      expect(result.recurring.dayOfWeek).toBe(5);
    });

    test('returns null for non-recurring', () => {
      const result = parseTask('Buy milk');
      expect(result.recurring).toBeNull();
    });
  });

  // ============================================================
  // ENERGY LEVEL TESTS (6 cases)
  // ============================================================
  describe('Energy Level Detection', () => {
    test('detects high energy from "focus"', () => {
      const result = parseTask('Focus on deep work');
      expect(result.energy).toBe('high');
    });

    test('detects high energy from "challenging"', () => {
      const result = parseTask('Challenging algorithm problem');
      expect(result.energy).toBe('high');
    });

    test('detects low energy from "quick"', () => {
      const result = parseTask('Quick email reply');
      expect(result.energy).toBe('low');
    });

    test('detects low energy from "easy"', () => {
      const result = parseTask('Easy filing task');
      expect(result.energy).toBe('low');
    });

    test('detects low energy from "routine"', () => {
      const result = parseTask('Routine data entry');
      expect(result.energy).toBe('low');
    });

    test('defaults to medium energy', () => {
      const result = parseTask('Review document');
      expect(result.energy).toBe('medium');
    });
  });

  // ============================================================
  // EDGE CASES & INTEGRATION TESTS (8 cases)
  // ============================================================
  describe('Edge Cases & Integration', () => {
    test('handles empty string', () => {
      const result = parseTask('');
      expect(result.title).toBe('New Task');
      expect(result.confidence).toBe(0);
    });

    test('handles null input', () => {
      const result = parseTask(null);
      expect(result.title).toBe('New Task');
    });

    test('handles undefined input', () => {
      const result = parseTask(undefined);
      expect(result.title).toBe('New Task');
    });

    test('handles complex input with multiple extractions', () => {
      const result = parseTask('URGENT: @work @computer finish report tomorrow 2h');
      expect(result.priority).toBe(1);
      expect(result.context).toContain('@work');
      expect(result.context).toContain('@computer');
      expect(result.estimateMinutes).toBe(120);
      expect(result.dueDate).not.toBeNull();
      expect(result.category).toBe('work');
    });

    test('preserves raw title', () => {
      const input = '@home clean kitchen 30m tomorrow';
      const result = parseTask(input);
      expect(result.rawTitle).toBe(input);
    });

    test('cleans up extra whitespace in title', () => {
      const result = parseTask('  Buy   groceries   @home  ');
      expect(result.title).not.toMatch(/\s\s/);
    });

    test('capitalizes first letter of title', () => {
      const result = parseTask('buy milk');
      expect(result.title).toBe('Buy milk');
    });

    test('provides confidence score', () => {
      const result = parseTask('@home clean kitchen 30m tomorrow urgent');
      expect(result.confidence).toBeGreaterThan(0);
      expect(result.confidence).toBeLessThanOrEqual(1);
    });
  });

  // ============================================================
  // BACKWARD COMPATIBILITY TESTS (3 cases)
  // ============================================================
  describe('Backward Compatibility (parseTimeEstimate)', () => {
    test('returns title, dueDate, estimateMinutes format', () => {
      const result = parseTimeEstimate('Review PR 30m tomorrow');
      expect(result).toHaveProperty('title');
      expect(result).toHaveProperty('dueDate');
      expect(result).toHaveProperty('estimateMinutes');
    });

    test('parses duration correctly', () => {
      const result = parseTimeEstimate('Task 45m');
      expect(result.estimateMinutes).toBe(45);
    });

    test('parses date correctly', () => {
      const result = parseTimeEstimate('Task tomorrow');
      expect(result.dueDate).not.toBeNull();
    });
  });

  // ============================================================
  // HELPER FUNCTION TESTS (5 cases)
  // ============================================================
  describe('Helper Functions', () => {
    test('extractContextTags works standalone', () => {
      const { contexts, cleanedText } = extractContextTags('@home @work task');
      expect(contexts).toContain('@home');
      expect(contexts).toContain('@work');
      expect(cleanedText).toBe('task');
    });

    test('extractDuration works standalone', () => {
      const { estimateMinutes, cleanedText } = extractDuration('task 45m');
      expect(estimateMinutes).toBe(45);
      expect(cleanedText).toBe('task');
    });

    test('detectEnergy works standalone', () => {
      expect(detectEnergy('Focus session')).toBe('high');
      expect(detectEnergy('Quick email')).toBe('low');
      expect(detectEnergy('Normal task')).toBe('medium');
    });

    test('detectCategory works standalone', () => {
      expect(detectCategory('gym workout', [])).toBe('health');
      expect(detectCategory('pay bill', [])).toBe('finance');
    });

    test('inferPriority works standalone', () => {
      const { priority } = inferPriority('URGENT task', null);
      expect(priority).toBe(1);
    });
  });
});

// Run with: npm test -- --testPathPattern=task-parser
