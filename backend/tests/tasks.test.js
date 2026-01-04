/**
 * E2E Tests: Task CRUD Operations
 * Tests task creation, retrieval, updating, and deletion
 */

const request = require('supertest');
const path = require('path');
const app = require('../server');
const { createTestUser, createTestTask, getTasks, updateTask, deleteTask, cleanupTestUser } = require('./helpers');

describe('Task CRUD Operations', () => {
  let accessToken;
  let userId;

  beforeEach(async () => {
    // Create a test user
    const user = await createTestUser(app);
    accessToken = user.accessToken;
    userId = user.user.id;
  });

  afterEach(async () => {
    // Cleanup
    await cleanupTestUser(app, accessToken);
  });

  describe('POST /api/tasks', () => {
    it('should create a new task successfully', async () => {
      // NOTE: "in 30m" is a DUE DATE (when to do it), not estimate (how long it takes)
      // Use "30m task" format for estimates, "in 30m" for due dates
      const task = await createTestTask(app, accessToken, {
        rawTitle: 'Buy groceries 30m',  // No "in" = estimate of duration
        priority: 1
      });

      expect(task).toHaveProperty('id');
      expect(task.title).toBe('Buy groceries');
      expect(task.estimate_minutes).toBe(30);
      expect(task.priority).toBe(1);
      expect(task.completed).toBe(false);
      expect(task.user_id).toBe(userId);
    });

    it('should create task with context information', async () => {
      const task = await createTestTask(app, accessToken, {
        rawTitle: 'Call dentist',
        context_location: 'home',
        context_time: 'morning'
      });

      expect(task.context_location).toBe('home');
      expect(task.context_time).toBe('morning');
    });

    it('should create task with spatial coordinates', async () => {
      const task = await createTestTask(app, accessToken, {
        rawTitle: 'Review PR',
        x: 100,
        y: 200
      });

      expect(task.x).toBe(100);
      expect(task.y).toBe(200);
    });

    it('should create recurring task', async () => {
      const task = await createTestTask(app, accessToken, {
        rawTitle: 'Daily standup',
        is_recurring: true,
        echo_interval: 'daily'
      });

      expect(task.is_recurring).toBe(true);
      expect(task.echo_interval).toBe('daily');
    });

    it('should parse due date from natural language', async () => {
      const task = await createTestTask(app, accessToken, {
        rawTitle: 'Submit report tomorrow at 5pm'
      });

      expect(task.title).toBe('Submit report');
      expect(task.due_date).toBeTruthy();
    });

    it('should reject task creation without authentication', async () => {
      await request(app)
        .post('/api/tasks')
        .send({
          rawTitle: 'Unauthorized task'
        })
        .expect(401);
    });
  });

  describe('GET /api/tasks', () => {
    it('should return empty array when no tasks exist', async () => {
      const tasks = await getTasks(app, accessToken);
      expect(Array.isArray(tasks)).toBe(true);
      expect(tasks.length).toBe(0);
    });

    it('should return all user tasks', async () => {
      // Create multiple tasks
      await createTestTask(app, accessToken, { rawTitle: 'Task 1', priority: 0 });
      await createTestTask(app, accessToken, { rawTitle: 'Task 2', priority: 1 });
      await createTestTask(app, accessToken, { rawTitle: 'Task 3', priority: 2 });

      const tasks = await getTasks(app, accessToken);
      expect(tasks.length).toBe(3);
    });

    it('should not return completed tasks', async () => {
      // Create tasks
      const task1 = await createTestTask(app, accessToken, { rawTitle: 'Active task' });
      const task2 = await createTestTask(app, accessToken, { rawTitle: 'Complete task' });

      // Complete one task
      await updateTask(app, accessToken, task2.id, { completed: true });

      // Get tasks
      const tasks = await getTasks(app, accessToken);
      expect(tasks.length).toBe(1);
      expect(tasks[0].id).toBe(task1.id);
    });

    it('should not return snoozed tasks', async () => {
      // Create task
      const task = await createTestTask(app, accessToken, { rawTitle: 'Snoozed task' });

      // Snooze it
      await request(app)
        .post(`/api/tasks/${task.id}/snooze`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      // Get tasks
      const tasks = await getTasks(app, accessToken);
      expect(tasks.length).toBe(0);
    });

    it('should prioritize tasks correctly', async () => {
      // Create tasks with different priorities (0=Low, 1=Medium, 2=High)
      await createTestTask(app, accessToken, { rawTitle: 'Low priority', priority: 0 });
      await createTestTask(app, accessToken, { rawTitle: 'Medium priority', priority: 1 });
      await createTestTask(app, accessToken, { rawTitle: 'High priority', priority: 2 });

      const tasks = await getTasks(app, accessToken);

      // High priority (2) should be first
      expect(tasks[0].priority).toBe(2);
      expect(tasks[0].title).toBe('High priority');
    });

    it('should filter tasks by context', async () => {
      await createTestTask(app, accessToken, { rawTitle: 'Home task', context_location: 'home' });
      await createTestTask(app, accessToken, { rawTitle: 'Work task', context_location: 'work' });

      const tasks = await getTasks(app, accessToken, { location: 'home' });

      // Context filtering should prioritize home tasks
      const homeTask = tasks.find(t => t.context_location === 'home');
      expect(homeTask).toBeTruthy();
    });
  });

  describe('GET /api/tasks/:id', () => {
    it('should return a specific task', async () => {
      const createdTask = await createTestTask(app, accessToken, { rawTitle: 'Specific task' });

      const response = await request(app)
        .get(`/api/tasks/${createdTask.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(response.body.id).toBe(createdTask.id);
      expect(response.body.title).toBe('Specific task');
    });

    it('should return 404 for non-existent task', async () => {
      const fakeId = '00000000-0000-0000-0000-000000000000'; // Non-existent UUID

      await request(app)
        .get(`/api/tasks/${fakeId}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);
    });

    it('should not return tasks from other users', async () => {
      // Create another user and task
      const otherUser = await createTestUser(app);
      const otherTask = await createTestTask(app, otherUser.accessToken, { rawTitle: 'Other user task' });

      // Try to access other user's task
      await request(app)
        .get(`/api/tasks/${otherTask.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);

      // Cleanup other user
      await cleanupTestUser(app, otherUser.accessToken);
    });
  });

  describe('PATCH /api/tasks/:id', () => {
    it('should update task title', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Original title' });

      const updated = await updateTask(app, accessToken, task.id, {
        title: 'Updated title'
      });

      expect(updated.title).toBe('Updated title');
      expect(updated.updated_at).not.toBe(task.updated_at);
    });

    it('should complete a task', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task to complete' });

      const updated = await updateTask(app, accessToken, task.id, {
        completed: true
      });

      expect(updated.completed).toBe(true);
      expect(updated.completed_at).toBeTruthy();
    });

    it('should track completion via widget', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Widget task' });

      await updateTask(app, accessToken, task.id, {
        completed: true,
        source: 'widget'
      });

      // Verify stats were updated (check via user stats endpoint)
      const statsResponse = await request(app)
        .get('/api/user/stats/weekly')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(statsResponse.body.weeks.length).toBeGreaterThan(0);
    });

    it('should update task priority', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task', priority: 0 });

      const updated = await updateTask(app, accessToken, task.id, {
        priority: 2
      });

      expect(updated.priority).toBe(2);
    });

    it('should update multiple fields at once', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Multi-update task' });

      const updated = await updateTask(app, accessToken, task.id, {
        title: 'New title',
        priority: 1,
        context_location: 'work'
      });

      expect(updated.title).toBe('New title');
      expect(updated.priority).toBe(1);
      expect(updated.context_location).toBe('work');
    });
  });

  describe('POST /api/tasks/:id/snooze', () => {
    it('should snooze a task until tomorrow', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task to snooze' });

      const response = await request(app)
        .post(`/api/tasks/${task.id}/snooze`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(response.body.snoozed_until).toBeTruthy();
      expect(response.body.times_rescheduled).toBe(task.times_rescheduled + 1);

      // Verify due date was pushed to tomorrow
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      const snoozeDate = new Date(response.body.due_date);
      expect(snoozeDate.getDate()).toBe(tomorrow.getDate());
    });

    it('should preserve original due date on first snooze', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task with due date today' });

      const response = await request(app)
        .post(`/api/tasks/${task.id}/snooze`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      if (task.due_date) {
        expect(response.body.original_due_date).toBeTruthy();
      }
    });
  });

  describe('DELETE /api/tasks/:id', () => {
    it('should delete a specific task', async () => {
      const task = await createTestTask(app, accessToken, { rawTitle: 'Task to delete' });

      await deleteTask(app, accessToken, task.id);

      // Verify task is gone
      await request(app)
        .get(`/api/tasks/${task.id}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);
    });

    it('should return 404 when deleting non-existent task', async () => {
      const fakeId = '00000000-0000-0000-0000-000000000000'; // Non-existent UUID

      await request(app)
        .delete(`/api/tasks/${fakeId}`)
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(404);
    });
  });

  describe('DELETE /api/tasks', () => {
    it('should delete all user tasks', async () => {
      // Create multiple tasks
      await createTestTask(app, accessToken, { rawTitle: 'Task 1' });
      await createTestTask(app, accessToken, { rawTitle: 'Task 2' });
      await createTestTask(app, accessToken, { rawTitle: 'Task 3' });

      // Delete all
      await request(app)
        .delete('/api/tasks')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      // Verify all tasks are gone
      const tasks = await getTasks(app, accessToken);
      expect(tasks.length).toBe(0);
    });
  });

  describe('POST /api/done-for-today', () => {
    it('should mark user as done for today', async () => {
      const response = await request(app)
        .post('/api/done-for-today')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(response.body.success).toBe(true);

      // Verify user status
      const userResponse = await request(app)
        .get('/api/user')
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      expect(userResponse.body.done_for_today).toBe(true);
      expect(userResponse.body.done_for_today_at).toBeTruthy();
    });
  });
});
