/**
 * NLP Integration Tests
 * Tests that the comprehensive NLP parser is properly integrated into the API
 * Epic 7: NLP Integration & UX Polish
 */

const request = require('supertest');
const { parseTask } = require('../utils/task-parser');

// Note: These tests require a running server with database access
// Run with: npm test -- nlp-integration.test.js

describe('NLP Integration Tests', () => {
  let app;
  let authToken;
  let testUserId;

  beforeAll(async () => {
    // Import app after environment is set up
    app = require('../server');

    // Register test user and get token
    const timestamp = Date.now();
    const testEmail = `nlp-test-${timestamp}@example.com`;

    const registerRes = await request(app)
      .post('/api/auth/register')
      .send({
        email: testEmail,
        password: 'test123456'
      });

    if (registerRes.status === 201) {
      authToken = registerRes.body.accessToken;
      testUserId = registerRes.body.user.id;
      console.log(`Test user created: ${testEmail}`);
    } else {
      throw new Error(`Failed to create test user: ${registerRes.body.error}`);
    }
  });

  afterAll(async () => {
    // Clean up: delete all tasks created by test user
    if (authToken) {
      await request(app)
        .delete('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`);
    }
  });

  describe('Context Tag Extraction', () => {
    test('Should extract @work tag and set category to work', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: '@work finish report tomorrow 2h' });

      expect(res.status).toBe(201);
      expect(res.body.category).toBe('work');
      expect(res.body.context_tags).toContain('@work');
      expect(res.body.estimate_minutes).toBe(120);
      expect(res.body.title).toBe('Finish report');
      expect(res.body.raw_title).toBe('@work finish report tomorrow 2h');
    });

    test('Should extract @home tag and set category to personal', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: '@home clean kitchen 30m' });

      expect(res.status).toBe(201);
      expect(res.body.context_tags).toContain('@home');
      expect(res.body.estimate_minutes).toBe(30);
      expect(res.body.title).toBe('Clean kitchen');
    });

    test('Should extract @gym tag and set category to health', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: '@gym upper body workout 1h' });

      expect(res.status).toBe(201);
      expect(res.body.context_tags).toContain('@gym');
      expect(res.body.category).toBe('health');
      expect(res.body.estimate_minutes).toBe(60);
    });

    test('Should extract multiple context tags', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: '@work @computer finish presentation' });

      expect(res.status).toBe(201);
      expect(res.body.context_tags).toContain('@work');
      expect(res.body.context_tags).toContain('@computer');
      expect(res.body.category).toBe('work');
    });
  });

  describe('Priority Inference from Keywords', () => {
    test('Should infer P1 from "URGENT:" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'URGENT: call client ASAP' });

      expect(res.status).toBe(201);
      expect(res.body.priority).toBe(1);  // P1 from "URGENT"
      expect(res.body.title).toContain('call client');  // Title contains core task
    });

    test('Should infer P1 from "important" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'important meeting prep' });

      expect(res.status).toBe(201);
      expect(res.body.priority).toBe(1);
    });

    test('Should infer P3 from "someday" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'someday learn piano' });

      expect(res.status).toBe(201);
      expect(res.body.priority).toBe(3);  // P3 from "someday"
    });

    test('Should infer P3 from "maybe" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'maybe read that book' });

      expect(res.status).toBe(201);
      expect(res.body.priority).toBe(3);
    });
  });

  describe('Category Detection', () => {
    const testCases = [
      { input: 'workout at gym tomorrow', expectedCategory: 'health' },
      { input: 'pay credit card bill', expectedCategory: 'finance' },
      { input: 'study for exam', expectedCategory: 'learning' },
      { input: 'dinner with friends Friday', expectedCategory: 'social' },
      { input: 'buy groceries at store', expectedCategory: 'errands' },
      { input: 'prepare presentation for client', expectedCategory: 'work' }
    ];

    test.each(testCases)(
      'Should detect category: $expectedCategory for "$input"',
      async ({ input, expectedCategory }) => {
        const res = await request(app)
          .post('/api/tasks')
          .set('Authorization', `Bearer ${authToken}`)
          .send({ rawTitle: input });

        expect(res.status).toBe(201);
        expect(res.body.category).toBe(expectedCategory);
      }
    );
  });

  describe('Energy Level Detection', () => {
    test('Should detect high energy from "deep work" keywords', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'deep work session on complex analysis' });

      expect(res.status).toBe(201);
      expect(res.body.energy_level).toBe('high');
    });

    test('Should detect high energy from "challenging" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'challenging algorithm problem' });

      expect(res.status).toBe(201);
      expect(res.body.energy_level).toBe('high');
    });

    test('Should detect low energy from "quick" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'quick email reply' });

      expect(res.status).toBe(201);
      expect(res.body.energy_level).toBe('low');
    });

    test('Should detect low energy from "easy" keyword', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'easy filing task' });

      expect(res.status).toBe(201);
      expect(res.body.energy_level).toBe('low');
    });

    test('Should default to medium energy for normal tasks', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'review document' });

      expect(res.status).toBe(201);
      expect(res.body.energy_level).toBe('medium');
    });
  });

  describe('Recurring Pattern Recognition', () => {
    test('Should parse "every Monday" recurring pattern', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'team meeting every Monday at 10am' });

      expect(res.status).toBe(201);
      expect(res.body.recurring_interval).toBe('weekly');
      expect(res.body.recurring_day_of_week).toBe(1);  // Monday
      expect(res.body.title).toContain('Team meeting');
    });

    test('Should parse "every day" recurring pattern', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'water plants every day' });

      expect(res.status).toBe(201);
      expect(res.body.recurring_interval).toBe('daily');
    });

    test('Should parse "weekly" recurring pattern', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'weekly team sync' });

      expect(res.status).toBe(201);
      expect(res.body.recurring_interval).toBe('weekly');
    });
  });

  describe('Time Context Extraction', () => {
    test('Should extract "morning" time context', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'exercise in the morning' });

      expect(res.status).toBe(201);
      expect(res.body.time_context).toBe('morning');
    });

    test('Should extract "afternoon" time context', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'call client this afternoon' });

      expect(res.status).toBe(201);
      expect(res.body.time_context).toBe('afternoon');
    });

    test('Should extract "evening" time context from "tonight"', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'watch movie tonight' });

      expect(res.status).toBe(201);
      expect(res.body.time_context).toBe('evening');
    });
  });

  describe('Complex Integration Scenarios', () => {
    test('Should handle complex input with multiple NLP features', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: 'URGENT: @work @computer finish report tomorrow 2h deep work' });

      expect(res.status).toBe(201);
      expect(res.body.priority).toBe(1);  // From "URGENT"
      expect(res.body.context_tags).toContain('@work');
      expect(res.body.context_tags).toContain('@computer');
      expect(res.body.estimate_minutes).toBe(120);  // 2h
      expect(res.body.category).toBe('work');
      expect(res.body.energy_level).toBe('high');  // From "deep work"
      expect(res.body.due_date).not.toBeNull();  // "tomorrow"
    });

    test('Should preserve raw title for reference', async () => {
      const input = '@home clean kitchen 30m this evening';
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: input });

      expect(res.status).toBe(201);
      expect(res.body.raw_title).toBe(input);
      expect(res.body.title).not.toBe(input);  // Should be cleaned
    });

    test('Should handle fallback gracefully for invalid input', async () => {
      const res = await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: '' });

      expect(res.status).toBe(201);
      expect(res.body.title).toBe('New Task');  // Fallback
      expect(res.body.category).toBe('general');
      expect(res.body.priority).toBe(2);
    });
  });

  describe('GET /api/tasks - Verify NLP Fields Returned', () => {
    test('Should return all NLP fields when fetching tasks', async () => {
      // Create a task with NLP data
      await request(app)
        .post('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`)
        .send({ rawTitle: '@work URGENT: finish presentation 1h' });

      // Fetch all tasks
      const res = await request(app)
        .get('/api/tasks')
        .set('Authorization', `Bearer ${authToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body)).toBe(true);
      expect(res.body.length).toBeGreaterThan(0);

      // Find our task
      const task = res.body.find(t => t.raw_title === '@work URGENT: finish presentation 1h');
      expect(task).toBeDefined();
      expect(task.category).toBe('work');
      expect(task.context_tags).toContain('@work');
      expect(task.priority).toBe(1);
      expect(task.estimate_minutes).toBe(60);
      expect(task.energy_level).toBeDefined();
      expect(task.raw_title).toBeDefined();
    });
  });
});

// Run with: npm test -- nlp-integration.test.js
