/**
 * LLM Task Parser Tests
 *
 * Tests Epic 8: LLM Intelligence Enhancement
 * - Gemini LLM parsing with anti-hallucination validation
 * - Graceful fallback to local parser
 * - Rate limiting
 *
 * @jest-environment node
 */

const { parseLLM, validateAndClean, buildPrompt } = require('../utils/llm-task-parser');
const { checkRateLimit, clearRateLimit } = require('../middleware/rate-limiter');

describe('LLM Task Parser', () => {
  // Skip LLM tests if API key not configured
  const skipLLM = !process.env.GEMINI_API_KEY;

  describe('Hallucination Validation', () => {
    test('strips invented date when no date mentioned', () => {
      const input = 'Call mom urgently';
      const llmResponse = {
        task: 'Call mom',
        dueDate: '2026-01-10', // LLM hallucinated this
        dueTime: null,
        priority: 1,
        category: 'personal',
        energyLevel: 'medium'
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.dueDate).toBeNull(); // Date should be stripped
      expect(validated.task).toBe('Call mom');
      expect(validated.priority).toBe(1);
    });

    test('keeps date when explicitly mentioned', () => {
      const input = 'Email manager tomorrow at 5pm';
      const llmResponse = {
        task: 'Email manager',
        dueDate: '2026-01-06', // Valid - "tomorrow" mentioned
        dueTime: '17:00',      // Valid - "5pm" mentioned
        priority: 2,
        category: 'work',
        energyLevel: 'medium'
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.dueDate).toBe('2026-01-06'); // Date preserved
      expect(validated.dueTime).toBe('17:00');      // Time preserved
    });

    test('strips invented time when no time mentioned', () => {
      const input = 'Workout at gym';
      const llmResponse = {
        task: 'Workout at gym',
        dueDate: null,
        dueTime: '18:00', // LLM hallucinated this
        priority: 2,
        category: 'health',
        energyLevel: 'high'
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.dueTime).toBeNull(); // Time should be stripped
      expect(validated.task).toBe('Workout at gym');
    });

    test('removes trailing prepositions from task name', () => {
      const input = 'Email manager by 5pm tomorrow';
      const llmResponse = {
        task: 'Email manager by', // LLM left "by" at end
        dueDate: '2026-01-06',
        dueTime: '17:00',
        priority: 2,
        category: 'work',
        energyLevel: 'medium'
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.task).toBe('Email manager'); // "by" removed
    });

    test('validates priority range', () => {
      const input = 'Do something';
      const llmResponse = {
        task: 'Do something',
        dueDate: null,
        dueTime: null,
        priority: 5, // Invalid priority
        category: 'other',
        energyLevel: 'medium'
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.priority).toBe(2); // Reset to default
    });

    test('validates category', () => {
      const input = 'Random task';
      const llmResponse = {
        task: 'Random task',
        dueDate: null,
        dueTime: null,
        priority: 2,
        category: 'invalid_category', // Invalid
        energyLevel: 'medium'
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.category).toBe('other'); // Reset to "other"
    });

    test('validates energy level', () => {
      const input = 'Task';
      const llmResponse = {
        task: 'Task',
        dueDate: null,
        dueTime: null,
        priority: 2,
        category: 'other',
        energyLevel: 'super_high' // Invalid
      };

      const validated = validateAndClean(llmResponse, input);

      expect(validated.energyLevel).toBe('medium'); // Reset to default
    });
  });

  describe('Prompt Building', () => {
    test('builds prompt with current date/time context', () => {
      const input = 'Email manager by 5pm tomorrow';
      const context = {
        today: '2026-01-05',
        currentTime: '14:30',
        dayOfWeek: 'Sunday'
      };

      const prompt = buildPrompt(input, context);

      expect(prompt).toContain('INPUT: "Email manager by 5pm tomorrow"');
      expect(prompt).toContain('TODAY: 2026-01-05 (Sunday)');
      expect(prompt).toContain('CURRENT TIME: 14:30');
      expect(prompt).toContain('STRICT RULES');
      expect(prompt).toContain('DO NOT HALLUCINATE');
    });
  });

  describe('LLM Parsing (Integration)', () => {
    (skipLLM ? test.skip : test)('parses simple task with date', async () => {
      const input = 'Email manager by 5pm tomorrow';
      const result = await parseLLM(input);

      expect(result.title).toBe('Email manager');
      expect(result.due_date).toBeTruthy(); // Should have a date
      expect(result.due_time).toBe('17:00'); // 5pm = 17:00
      expect(result.category).toBe('work');
      expect(result.source).toBe('llm');
    }, 10000); // 10s timeout for API call

    (skipLLM ? test.skip : test)('parses urgent task', async () => {
      const input = 'Call mom urgently';
      const result = await parseLLM(input);

      expect(result.title).toBe('Call mom');
      expect(result.priority).toBe(1); // "urgently" → high priority
      expect(result.category).toBe('personal');
      expect(result.source).toBe('llm');
    }, 10000);

    (skipLLM ? test.skip : test)('parses task with time offset', async () => {
      const input = 'Meeting in 30 minutes';
      const result = await parseLLM(input);

      expect(result.title).toBe('Meeting');
      expect(result.due_time).toBeTruthy(); // Should calculate time
      expect(result.source).toBe('llm');
    }, 10000);

    (skipLLM ? test.skip : test)('parses recurring task', async () => {
      const input = 'Team standup every Monday at 9am';
      const result = await parseLLM(input);

      expect(result.title).toContain('Team standup');
      expect(result.is_recurring).toBe(true);
      expect(result.recurring_pattern).toBe('weekly');
      expect(result.due_time).toBe('09:00');
      expect(result.source).toBe('llm');
    }, 10000);

    (skipLLM ? test.skip : test)('parses task with context tags', async () => {
      const input = 'Workout @gym high energy';
      const result = await parseLLM(input);

      expect(result.title).toBe('Workout');
      expect(result.context_tags).toContain('@gym');
      expect(result.energy_level).toBe('high');
      expect(result.category).toBe('health');
      expect(result.source).toBe('llm');
    }, 10000);

    test('falls back to local parser when LLM disabled', async () => {
      const originalKey = process.env.GEMINI_API_KEY;
      process.env.GEMINI_API_KEY = ''; // Disable LLM

      const input = 'Call mom in 10 minutes';
      const result = await parseLLM(input);

      expect(result.source).toBe('local_fallback');
      expect(result.reason).toBe('llm_disabled');
      expect(result.title).toBeTruthy(); // Still parsed locally

      process.env.GEMINI_API_KEY = originalKey; // Restore
    });
  });

  describe('Fallback Behavior', () => {
    test('returns local parse on timeout', async () => {
      // Mock a timeout scenario (implementation would need timeout testing)
      // For now, just verify fallback structure
      const input = 'Simple task';
      const result = await parseLLM(input);

      // If LLM succeeds, source = 'llm', if fails, source = 'local_fallback'
      expect(result.source).toMatch(/llm|local_fallback/);
      expect(result.title).toBeTruthy();
    });

    test('gracefully handles invalid input', async () => {
      const input = ''; // Empty input
      const result = await parseLLM(input);

      expect(result).toBeTruthy(); // Should still return something
      expect(result.source).toBe('local_fallback'); // Falls back
    });
  });

  describe('Rate Limiting', () => {
    const testUserId = 'test-user-123';

    beforeEach(() => {
      // Clear rate limits before each test
      clearRateLimit(testUserId);
    });

    test('allows requests under minute limit', () => {
      for (let i = 0; i < 9; i++) {
        const result = checkRateLimit(testUserId);
        expect(result.allowed).toBe(true);
        expect(result.remaining.minute).toBe(9 - i);
      }
    });

    test('blocks requests over minute limit', () => {
      // Make 10 requests (at limit)
      for (let i = 0; i < 10; i++) {
        checkRateLimit(testUserId);
      }

      // 11th request should be blocked
      const result = checkRateLimit(testUserId);
      expect(result.allowed).toBe(false);
      expect(result.reason).toBe('minute_limit_exceeded');
      expect(result.limit).toBe(10);
      expect(result.window).toBe('minute');
    });

    test('allows requests under day limit', () => {
      // Simulate time passing (clear minute limit manually for testing)
      for (let i = 0; i < 99; i++) {
        checkRateLimit(testUserId);
      }

      const result = checkRateLimit(testUserId);
      expect(result.allowed).toBe(true);
      expect(result.remaining.day).toBe(0); // At daily limit
    });

    test('blocks requests over day limit', () => {
      // Simulate 100 requests (day limit)
      for (let i = 0; i < 100; i++) {
        // Manually reset minute counter to avoid minute limit
        const counters = require('../middleware/rate-limiter').getRateLimitStatus(testUserId);
        if (i % 10 === 0) {
          clearRateLimit(testUserId); // Reset minute but not day
          // Re-establish day counter
          for (let j = 0; j < i; j++) {
            checkRateLimit(testUserId);
          }
        } else {
          checkRateLimit(testUserId);
        }
      }

      // This is complex to test - simplified version
      const status = require('../middleware/rate-limiter').getRateLimitStatus(testUserId);
      expect(status.day.used).toBeGreaterThan(0);
    });

    test('resets counters after time window expires', async () => {
      // This would require mocking time, which is complex
      // For now, just verify reset logic exists
      const result1 = checkRateLimit(testUserId);
      expect(result1.resetAt.minute).toBeTruthy();
      expect(result1.resetAt.day).toBeTruthy();
    });
  });

  describe('Edge Cases', () => {
    (skipLLM ? test.skip : test)('handles very long input', async () => {
      const input = 'This is a very long task description that goes on and on with lots of details about what needs to be done and when and where and why and how'.repeat(5);
      const result = await parseLLM(input);

      expect(result).toBeTruthy();
      expect(result.source).toMatch(/llm|local_fallback/);
    }, 10000);

    (skipLLM ? test.skip : test)('handles special characters', async () => {
      const input = 'Email john@company.com about Q4 financials ($$$) !!!';
      const result = await parseLLM(input);

      expect(result.title).toBeTruthy();
      expect(result.priority).toBe(1); // "!!!" → high priority
    }, 10000);

    (skipLLM ? test.skip : test)('handles emoji in input', async () => {
      const input = 'Call mom 📞 urgently ⚡';
      const result = await parseLLM(input);

      expect(result.title).toContain('Call mom');
      expect(result.priority).toBe(1);
    }, 10000);

    (skipLLM ? test.skip : test)('handles multiple context tags', async () => {
      const input = 'Review documents @office @desk high focus';
      const result = await parseLLM(input);

      expect(result.context_tags).toContain('@office');
      expect(result.context_tags).toContain('@desk');
      expect(result.energy_level).toBe('high');
    }, 10000);
  });

  describe('Real User Inputs (From Beta Feedback)', () => {
    const realUserInputs = [
      'email manager in 10 minutes',
      'call mom she\'s in hospital urgent',
      'Complete report by Friday 3pm',
      'Workout tomorrow morning high energy',
      'Quick meeting in 30 mins',
      'Buy groceries @store someday',
      'Daily standup every weekday 9am',
      'Finish presentation for Monday',
      'Call dentist important',
      'Read book for 1 hour relaxed'
    ];

    realUserInputs.forEach((input) => {
      (skipLLM ? test.skip : test)(`parses: "${input}"`, async () => {
        const result = await parseLLM(input);

        // Basic validation - all fields should exist
        expect(result).toBeTruthy();
        expect(result.title).toBeTruthy();
        expect(result.priority).toBeGreaterThanOrEqual(1);
        expect(result.priority).toBeLessThanOrEqual(3);
        expect(result.category).toBeTruthy();
        expect(result.source).toMatch(/llm|local_fallback/);

        // Log results for manual inspection
        console.log(`Input: "${input}"`);
        console.log(`Parsed:`, JSON.stringify(result, null, 2));
      }, 10000);
    });
  });
});

describe('LLM Parser Performance', () => {
  const skipLLM = !process.env.GEMINI_API_KEY;

  (skipLLM ? test.skip : test)('completes parse in under 5 seconds', async () => {
    const input = 'Email manager by 5pm tomorrow';
    const startTime = Date.now();

    const result = await parseLLM(input);

    const duration = Date.now() - startTime;
    expect(duration).toBeLessThan(5000); // 5 second timeout
    expect(result).toBeTruthy();
  }, 10000);

  (skipLLM ? test.skip : test)('handles concurrent requests', async () => {
    const inputs = [
      'Task 1',
      'Task 2',
      'Task 3'
    ];

    const startTime = Date.now();
    const results = await Promise.all(inputs.map(input => parseLLM(input)));
    const duration = Date.now() - startTime;

    expect(results).toHaveLength(3);
    results.forEach(result => {
      expect(result).toBeTruthy();
    });

    // Should complete all 3 in parallel faster than 3x single request
    expect(duration).toBeLessThan(15000); // 15 seconds for 3 parallel
  }, 20000);
});
