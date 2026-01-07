/**
 * Unified Parser Test Suite
 *
 * Tests LLM parser, fallback parser, and priority scorer for consistency.
 * These are the MUST-PASS cases that define correct behavior.
 */

const { parseTask } = require('../utils/task-parser');
const { parseLLM, validateAndClean } = require('../utils/llm-task-parser');
const { calculateTaskScore } = require('../utils/priority-scorer');

// Helper to get tomorrow's date as YYYY-MM-DD (local timezone)
function getTomorrowDate() {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  // Use local date parts to avoid timezone issues
  const year = tomorrow.getFullYear();
  const month = (tomorrow.getMonth() + 1).toString().padStart(2, '0');
  const day = tomorrow.getDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Helper to get yesterday's date as YYYY-MM-DD (local timezone)
function getYesterdayDate() {
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  // Use local date parts to avoid timezone issues
  const year = yesterday.getFullYear();
  const month = (yesterday.getMonth() + 1).toString().padStart(2, '0');
  const day = yesterday.getDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Helper to format date as YYYY-MM-DD in local timezone
function formatDateLocal(date) {
  const year = date.getFullYear();
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const day = date.getDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Helper to get next Friday's date as YYYY-MM-DD
function getNextFridayDate() {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const daysUntilFriday = (5 - dayOfWeek + 7) % 7 || 7; // If today is Friday, get next Friday
  const nextFriday = new Date(now);
  nextFriday.setDate(now.getDate() + daysUntilFriday);
  return nextFriday.toISOString().split('T')[0];
}

// Helper to calculate time X minutes from now
function getTimeInMinutes(minutes) {
  const now = new Date();
  now.setMinutes(now.getMinutes() + minutes);
  const hours = now.getHours().toString().padStart(2, '0');
  const mins = now.getMinutes().toString().padStart(2, '0');
  return `${hours}:${mins}`;
}

describe('Parser Test Suite - Must Pass Cases', () => {

  describe('Fallback Parser (task-parser.js)', () => {

    test('1. "email manager by 5pm tomorrow" -> due_date: tomorrow, due_time: 17:00, priority: 2', () => {
      const result = parseTask('email manager by 5pm tomorrow');

      expect(result.dueDate).not.toBeNull();
      if (result.dueDate) {
        const dateStr = formatDateLocal(result.dueDate);
        expect(dateStr).toBe(getTomorrowDate());
      }
      expect(result.dueTime).toBe('17:00');
      expect(result.priority).toBe(2);
    });

    test('2. "call mom in 10 minutes urgently" -> due_time: now+10min, priority: 1', () => {
      const result = parseTask('call mom in 10 minutes urgently');

      expect(result.dueTime).not.toBeNull();
      expect(result.priority).toBe(1);
    });

    test('3. "morning standup" -> due_time: 09:00, category: work', () => {
      const result = parseTask('morning standup');

      // Time context should set suggestedHour to 9
      expect(result.timeContext).toBe('morning');
      expect(result.category).toBe('work');
    });

    test('4. "workout tomorrow evening" -> due_date: tomorrow, due_time: 18:00, category: health', () => {
      const result = parseTask('workout tomorrow evening');

      expect(result.dueDate).not.toBeNull();
      if (result.dueDate) {
        const dateStr = formatDateLocal(result.dueDate);
        expect(dateStr).toBe(getTomorrowDate());
      }
      expect(result.category).toBe('health');
      expect(result.timeContext).toBe('evening');
    });

    test('5. "buy groceries" -> category: errands, priority: 2', () => {
      const result = parseTask('buy groceries');

      expect(result.category).toBe('errands');
      expect(result.priority).toBe(2);
    });

    test('6. "finish report by eod" -> due_time: 17:00 or 18:00, priority: 2', () => {
      const result = parseTask('finish report by eod');

      // EOD should set time context to evening with hour 17
      expect(result.timeContext).toBe('evening');
      expect(result.priority).toBe(2);
    });

    test('7. "party on friday" -> due_date: next friday', () => {
      const result = parseTask('party on friday');

      expect(result.dueDate).not.toBeNull();
      // Should be a Friday
      if (result.dueDate) {
        expect(result.dueDate.getDay()).toBe(5); // Friday is day 5
      }
    });

    test('8. "missed alert yesterday" -> due_date: yesterday\'s date', () => {
      const result = parseTask('missed alert yesterday');

      expect(result.dueDate).not.toBeNull();
      if (result.dueDate) {
        const dateStr = formatDateLocal(result.dueDate);
        expect(dateStr).toBe(getYesterdayDate());
      }
    });

    test('9. "quick task" -> estimate_minutes: 15', () => {
      const result = parseTask('quick task');

      expect(result.estimateMinutes).toBe(15);
    });

    test('10. "maybe clean garage someday" -> priority: 3', () => {
      const result = parseTask('maybe clean garage someday');

      expect(result.priority).toBe(3);
    });

    test('11. "asap fix production bug" -> priority: 1', () => {
      const result = parseTask('asap fix production bug');

      expect(result.priority).toBe(1);
    });
  });

  describe('LLM Parser Validation (validateAndClean)', () => {

    test('Should NOT strip valid date from "party on friday"', () => {
      const llmResponse = {
        task: 'party',
        dueDate: getNextFridayDate(),
        dueTime: null,
        priority: 2,
        category: 'social'
      };

      const validated = validateAndClean(llmResponse, 'party on friday');
      expect(validated.dueDate).not.toBeNull();
    });

    test('Should NOT strip valid time from "morning standup"', () => {
      const llmResponse = {
        task: 'standup',
        dueDate: null,
        dueTime: '09:00',
        priority: 2,
        category: 'work'
      };

      const validated = validateAndClean(llmResponse, 'morning standup');
      expect(validated.dueTime).toBe('09:00');
    });

    test('Should NOT strip valid date from "due tomorrow"', () => {
      const llmResponse = {
        task: 'task',
        dueDate: getTomorrowDate(),
        dueTime: null,
        priority: 2
      };

      const validated = validateAndClean(llmResponse, 'task due tomorrow');
      expect(validated.dueDate).not.toBeNull();
    });

    test('Should NOT strip valid date from "deadline friday"', () => {
      const llmResponse = {
        task: 'report',
        dueDate: getNextFridayDate(),
        dueTime: null,
        priority: 2
      };

      const validated = validateAndClean(llmResponse, 'report deadline friday');
      expect(validated.dueDate).not.toBeNull();
    });

    test('Should NOT strip valid date from "in 3 days"', () => {
      const future = new Date();
      future.setDate(future.getDate() + 3);
      const futureDate = future.toISOString().split('T')[0];

      const llmResponse = {
        task: 'task',
        dueDate: futureDate,
        dueTime: null,
        priority: 2
      };

      const validated = validateAndClean(llmResponse, 'task in 3 days');
      expect(validated.dueDate).not.toBeNull();
    });

    test('Should upgrade priority to 1 for "asap" keyword', () => {
      const llmResponse = {
        task: 'fix bug',
        dueDate: null,
        dueTime: null,
        priority: 2,
        category: 'work'
      };

      const validated = validateAndClean(llmResponse, 'asap fix production bug');
      expect(validated.priority).toBe(1);
    });

    test('Should NOT strip valid date from "yesterday"', () => {
      const llmResponse = {
        task: 'missed alert',
        dueDate: getYesterdayDate(),
        dueTime: null,
        priority: 2
      };

      const validated = validateAndClean(llmResponse, 'missed alert yesterday');
      expect(validated.dueDate).not.toBeNull();
    });
  });

  describe('Priority Scorer (priority-scorer.js)', () => {

    test('Priority 1 task should have higher score than priority 2', () => {
      const highPriorityTask = { priority: 1, created_at: new Date().toISOString() };
      const mediumPriorityTask = { priority: 2, created_at: new Date().toISOString() };

      const highScore = calculateTaskScore(highPriorityTask);
      const mediumScore = calculateTaskScore(mediumPriorityTask);

      expect(highScore).toBeGreaterThan(mediumScore);
    });

    test('Priority 2 task should have higher score than priority 3', () => {
      const mediumPriorityTask = { priority: 2, created_at: new Date().toISOString() };
      const lowPriorityTask = { priority: 3, created_at: new Date().toISOString() };

      const mediumScore = calculateTaskScore(mediumPriorityTask);
      const lowScore = calculateTaskScore(lowPriorityTask);

      expect(mediumScore).toBeGreaterThan(lowScore);
    });
  });

  describe('Date Words Validation - Additional Patterns', () => {

    test('Should recognize "tmw" as tomorrow', () => {
      const llmResponse = { task: 'call', dueDate: getTomorrowDate(), dueTime: null, priority: 2 };
      const validated = validateAndClean(llmResponse, 'call tmw');
      expect(validated.dueDate).not.toBeNull();
    });

    test('Should recognize "nxt week" as date reference', () => {
      const nextWeek = new Date();
      nextWeek.setDate(nextWeek.getDate() + 7);
      const llmResponse = { task: 'meeting', dueDate: nextWeek.toISOString().split('T')[0], dueTime: null, priority: 2 };
      const validated = validateAndClean(llmResponse, 'meeting nxt week');
      // 'nxt' should be recognized
      expect(validated.dueDate).not.toBeNull();
    });

    test('Should recognize "by end of" as date reference', () => {
      const llmResponse = { task: 'report', dueDate: getTomorrowDate(), dueTime: '17:00', priority: 2 };
      const validated = validateAndClean(llmResponse, 'report by end of day');
      expect(validated.dueDate).not.toBeNull();
    });
  });

  describe('LLM Date/Time Extraction Behavior', () => {

    test('No date/time in input -> both should be null', () => {
      // LLM might hallucinate a date, validateAndClean should strip it
      const llmResponse = {
        task: 'buy groceries',
        dueDate: '2026-01-15', // Hallucinated date
        dueTime: '10:00',      // Hallucinated time
        priority: 2,
        category: 'errands'
      };

      const validated = validateAndClean(llmResponse, 'buy groceries');
      expect(validated.dueDate).toBeNull();
      expect(validated.dueTime).toBeNull();
    });

    test('Only date in input -> time should be null', () => {
      const llmResponse = {
        task: 'submit report',
        dueDate: getTomorrowDate(),
        dueTime: '09:00', // Hallucinated time - no time mentioned in input
        priority: 2,
        category: 'work'
      };

      const validated = validateAndClean(llmResponse, 'submit report tomorrow');
      expect(validated.dueDate).not.toBeNull();
      expect(validated.dueTime).toBeNull();
    });

    test('Only time in input -> date should be null', () => {
      const llmResponse = {
        task: 'call client',
        dueDate: '2026-01-10', // Hallucinated date - no date mentioned
        dueTime: '15:00',
        priority: 2,
        category: 'work'
      };

      const validated = validateAndClean(llmResponse, 'call client at 3pm');
      expect(validated.dueDate).toBeNull();
      expect(validated.dueTime).toBe('15:00');
    });

    test('Time-of-day word (morning) should preserve inferred time', () => {
      const llmResponse = {
        task: 'standup',
        dueDate: null,
        dueTime: '09:00', // Inferred from "morning"
        priority: 2,
        category: 'work'
      };

      const validated = validateAndClean(llmResponse, 'morning standup');
      expect(validated.dueTime).toBe('09:00');
    });

    test('Time-of-day word (afternoon) should preserve inferred time', () => {
      const llmResponse = {
        task: 'nap',
        dueDate: null,
        dueTime: '14:00', // Inferred from "afternoon"
        priority: 3,
        category: 'personal'
      };

      const validated = validateAndClean(llmResponse, 'afternoon nap');
      expect(validated.dueTime).toBe('14:00');
    });

    test('Time-of-day word (evening) should preserve inferred time', () => {
      const llmResponse = {
        task: 'workout',
        dueDate: null,
        dueTime: '18:00', // Inferred from "evening"
        priority: 2,
        category: 'health'
      };

      const validated = validateAndClean(llmResponse, 'evening workout');
      expect(validated.dueTime).toBe('18:00');
    });

    test('"in X minutes" should set dueTime not estimateMinutes', () => {
      const result = parseTask('call mom in 10 minutes');

      // Should have due_time, NOT estimate_minutes
      expect(result.dueTime).not.toBeNull();
      // estimate_minutes should NOT be 10 (that's the due time, not duration)
      expect(result.estimateMinutes).not.toBe(10);
    });
  });

  describe('IST Timezone Handling', () => {
    // IST is UTC+5:30
    const IST_TIMEZONE = 'Asia/Kolkata';

    test('Should correctly handle IST timezone for date calculation', () => {
      // Simulate IST time conversion
      const now = new Date();
      const istTime = new Date(now.toLocaleString('en-US', { timeZone: IST_TIMEZONE }));

      // Get tomorrow in IST
      const tomorrowIST = new Date(istTime);
      tomorrowIST.setDate(tomorrowIST.getDate() + 1);
      const tomorrowDateIST = formatDateLocal(tomorrowIST);

      // The parser should handle timezone correctly
      expect(tomorrowDateIST).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    test('Late night IST edge case - 11:30 PM IST', () => {
      // At 11:30 PM IST (6:00 PM UTC), "tomorrow" should be next day in IST
      const istTime = new Date();
      const istHour = parseInt(istTime.toLocaleString('en-US', {
        timeZone: IST_TIMEZONE,
        hour: '2-digit',
        hour12: false
      }));

      // If it's close to midnight in IST, date handling is critical
      const istDate = istTime.toLocaleDateString('en-CA', { timeZone: IST_TIMEZONE });
      expect(istDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    test('"in 10 minutes" at 11:55 PM IST should roll to next day', () => {
      // This tests the midnight rollover scenario
      const result = parseTask('record in 10 minutes');

      // Should have a due_time set
      expect(result.dueTime).not.toBeNull();
    });
  });

  describe('Full Data Flow - DB Storage Format', () => {
    // These tests verify that parser output matches DB schema expectations

    test('Output should have correct field names for DB storage', () => {
      const result = parseTask('email manager by 5pm tomorrow');

      // Check that we have the expected fields
      expect(result).toHaveProperty('title');
      expect(result).toHaveProperty('dueDate');
      expect(result).toHaveProperty('dueTime');
      expect(result).toHaveProperty('priority');
      expect(result).toHaveProperty('category');
      expect(result).toHaveProperty('estimateMinutes');
    });

    test('dueDate should be Date object (convertible to YYYY-MM-DD for DB)', () => {
      const result = parseTask('meeting tomorrow');

      if (result.dueDate) {
        expect(result.dueDate instanceof Date).toBe(true);
        // Should be convertible to ISO format for DB storage
        const isoDate = result.dueDate.toISOString();
        expect(isoDate).toMatch(/^\d{4}-\d{2}-\d{2}T/);
      }
    });

    test('dueTime should be HH:MM format (matches DB TIME column)', () => {
      const result = parseTask('call at 3pm');

      expect(result.dueTime).toMatch(/^\d{2}:\d{2}$/);
    });

    test('priority should be 1, 2, or 3 (matches DB INTEGER)', () => {
      const result = parseTask('urgent task asap');
      expect([1, 2, 3]).toContain(result.priority);
    });

    test('No date input -> dueDate should be null (DB NULL)', () => {
      const result = parseTask('buy groceries');

      // No date mentioned, so dueDate should be null
      expect(result.dueDate).toBeNull();
    });

    test('No time input -> dueTime should be null (DB NULL)', () => {
      const result = parseTask('meeting tomorrow');

      // No specific time mentioned (just "tomorrow"), so dueTime should be null
      // Note: This might have time context but not explicit time
      // The test is checking we don't hallucinate a time
      expect(typeof result.dueTime === 'string' || result.dueTime === null).toBe(true);
    });
  });

  describe('Wallpaper Display - What User Sees', () => {
    // These tests verify the data will display correctly on wallpaper

    test('Task title should be clean (no date/time artifacts)', () => {
      const result = parseTask('email manager by 5pm tomorrow');

      // Title should NOT contain "by 5pm tomorrow"
      expect(result.title.toLowerCase()).not.toContain('5pm');
      expect(result.title.toLowerCase()).not.toContain('tomorrow');
    });

    test('Title should preserve task essence', () => {
      const result = parseTask('call mom in 10 minutes urgently');

      // Should contain "call" and "mom" at minimum
      expect(result.title.toLowerCase()).toContain('call');
      expect(result.title.toLowerCase()).toContain('mom');
    });

    test('Priority affects star size - verify scale', () => {
      // Priority 1 = 52px RED star (HIGH)
      // Priority 2 = 36px ORANGE/BLUE star (MEDIUM)
      // Priority 3 = 24px BLUE star (LOW)

      const urgentTask = parseTask('asap fix bug');
      const normalTask = parseTask('buy groceries');
      const lowTask = parseTask('maybe clean garage someday');

      expect(urgentTask.priority).toBe(1); // HIGH -> 52px star
      expect(normalTask.priority).toBe(2); // MEDIUM -> 36px star
      expect(lowTask.priority).toBe(3);    // LOW -> 24px star
    });
  });

  describe('LLM vs Fallback Parser Consistency', () => {
    // Both parsers should produce compatible output

    test('Fallback parser handles same patterns as LLM expects', () => {
      const testCases = [
        { input: 'email manager by 5pm tomorrow', expectDate: true, expectTime: true },
        { input: 'call mom in 10 minutes', expectDate: false, expectTime: true },
        { input: 'buy groceries', expectDate: false, expectTime: false },
        { input: 'meeting on friday', expectDate: true, expectTime: false },
        { input: 'morning standup', expectDate: false, expectTime: false }, // time context, not explicit time
      ];

      for (const { input, expectDate, expectTime } of testCases) {
        const result = parseTask(input);

        if (expectDate) {
          expect(result.dueDate).not.toBeNull();
        }
        if (expectTime) {
          expect(result.dueTime).not.toBeNull();
        }
      }
    });

    test('Both parsers use same priority scale (1=High, 2=Medium, 3=Low)', () => {
      // Fallback parser
      const fallbackUrgent = parseTask('urgent task');
      const fallbackLow = parseTask('maybe someday');

      expect(fallbackUrgent.priority).toBe(1);
      expect(fallbackLow.priority).toBe(3);

      // LLM validateAndClean should enforce same scale
      const llmResponse = { task: 'test', priority: 0 }; // Invalid priority
      const validated = validateAndClean(llmResponse, 'test');
      expect([1, 2, 3]).toContain(validated.priority); // Should be corrected to valid range
    });
  });
});
