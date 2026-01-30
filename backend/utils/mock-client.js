/**
 * A simple in-memory mock of the Postgres Client for testing/dev without DB.
 */
class MockClient {
  constructor() {
    this.tasks = [];
    this.users = [{ id: '00000000-0000-0000-0000-000000000000', email: 'test@cosmic.app' }];
    this.stats = [];
    console.log("⚠️  Using In-Memory Mock Database");
  }

  async connect() {
    return this;
  }

  release() {
    // No-op for mock client
  }

  async query(text, params = []) {
    const normalizedText = text.trim().toUpperCase();

    // INSERT INTO users
    if (normalizedText.startsWith('INSERT INTO USERS')) {
      const newUser = {
        id: params[0],
        email: params[1],
        password_hash: params[2],
        theme: params[3] || 'cosmic',
        timezone: params[4] || 'UTC',
        resolution: params[5] || '1080x1920',
        wallpaper_token: params[6] || null,
        setup_complete: false,
        done_for_today: false,
        done_for_today_at: null,
        created_at: new Date(),
        updated_at: new Date()
      };
      this.users.push(newUser);
      return { rows: [newUser] };
    }

    // UPDATE users
    if (normalizedText.startsWith('UPDATE USERS')) {
      const userId = params[params.length - 1]; // Last param is usually the WHERE id
      const user = this.users.find(u => u.id === userId);
      if (user) {
        // Update fields based on query
        if (normalizedText.includes('DONE_FOR_TODAY')) {
          user.done_for_today = true;
          user.done_for_today_at = new Date();
        }
        if (normalizedText.includes('WALLPAPER_TOKEN')) {
          user.wallpaper_token = params[0];
        }
        if (normalizedText.includes('THEME')) {
          user.theme = params[0];
        }
        user.updated_at = new Date();
        return { rows: [user] };
      }
      return { rows: [] };
    }

    // DELETE FROM users
    if (normalizedText.startsWith('DELETE FROM USERS')) {
      const userId = params[0];
      const index = this.users.findIndex(u => u.id === userId);
      if (index !== -1) {
        const deleted = this.users.splice(index, 1);
        // Also delete user's tasks and stats
        this.tasks = this.tasks.filter(t => t.user_id !== userId);
        this.stats = this.stats.filter(s => s.user_id !== userId);
        return { rows: deleted };
      }
      return { rows: [] };
    }

    // SELECT FROM users
    if (normalizedText.includes('SELECT') && normalizedText.includes('FROM USERS')) {
      const userId = params[0];
      const user = this.users.find(u => u.id === userId || u.email === userId);
      if (user) {
        return { rows: [user] };
      }
      return { rows: [] };
    }

    // SELECT * FROM tasks
    if (normalizedText.includes('SELECT * FROM TASKS')) {
      const userId = params[0];
      let rows = this.tasks.filter(t => !t.completed && t.user_id === userId);
      if (normalizedText.includes('SNOOZED_UNTIL')) {
        const now = new Date();
        rows = rows.filter(t => !t.snoozed_until || new Date(t.snoozed_until) < now);
      }
      return { rows };
    }

    // INSERT INTO tasks
    if (normalizedText.startsWith('INSERT INTO TASKS')) {
      const newTask = {
        id: crypto.randomUUID(),
        user_id: params[0],
        title: params[1],
        estimate_minutes: params[2],
        priority: params[3],
        due_date: params[4],
        context_location: params[5],
        context_time: params[6],
        x: params[7],
        y: params[8],
        is_subtask: params[9],
        is_recurring: params[10],
        echo_interval: params[11],
        created_at: new Date(),
        completed: false,
        times_rescheduled: 0,
        snoozed_until: null
      };
      this.tasks.push(newTask);
      return { rows: [newTask] };
    }

    // DELETE FROM tasks (Reset/Delete)
    if (normalizedText.startsWith('DELETE FROM TASKS')) {
      const userId = params[0];

      // Delete all tasks for user
      if (params.length === 1) {
        this.tasks = this.tasks.filter(t => t.user_id !== userId);
        return { rows: [] };
      }

      // Delete completed tasks
      if (normalizedText.includes('COMPLETED = TRUE')) {
        this.tasks = this.tasks.filter(t => t.user_id !== userId || !t.completed);
        return { rows: [] };
      }

      // Delete specific task
      const id = params[1];
      const index = this.tasks.findIndex(t => t.id === id && t.user_id === userId);
      if (index !== -1) {
        const deleted = this.tasks.splice(index, 1);
        return { rows: deleted };
      }
      return { rows: [] };
    }

    // UPDATE tasks (PATCH)
    if (normalizedText.startsWith('UPDATE TASKS SET')) {
      const userId = params[0];
      const id = params[1];
      const task = this.tasks.find(t => t.id === id && t.user_id === userId);
      if (task) {
        task.completed = true;
        task.completed_at = new Date();
        return { rows: [task] };
      }
      return { rows: [] };
    }

    // UPDATE tasks (Snooze)
    if (normalizedText.includes('UPDATE TASKS') && normalizedText.includes('ORIGINAL_DUE_DATE')) {
      const userId = params[0];
      const id = params[1];
      const task = this.tasks.find(t => t.id === id && t.user_id === userId);
      if (task) {
        task.original_due_date = task.original_due_date || task.due_date;
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        task.due_date = tomorrow;
        task.snoozed_until = tomorrow;
        task.times_rescheduled = (task.times_rescheduled || 0) + 1;
        return { rows: [task] };
      }
      return { rows: [] };
    }

    // INSERT INTO user_stats
    if (normalizedText.startsWith('INSERT INTO USER_STATS')) {
      const newStat = {
        user_id: params[0],
        week_start: params[1],
        tasks_completed: params[2] || 1,
        tasks_completed_via_widget: params[3] || 0,
        app_opens: params[4] || 0,
        widget_interactions: params[5] || 0,
        created_at: new Date(),
        updated_at: new Date()
      };

      // Check for existing stat (for ON CONFLICT DO UPDATE)
      const existing = this.stats.find(s =>
        s.user_id === newStat.user_id &&
        s.week_start?.toString() === newStat.week_start?.toString()
      );

      if (existing) {
        // Update existing stat
        existing.tasks_completed += newStat.tasks_completed;
        existing.tasks_completed_via_widget += newStat.tasks_completed_via_widget;
        existing.app_opens += newStat.app_opens;
        existing.widget_interactions += newStat.widget_interactions;
        existing.updated_at = new Date();
        return { rows: [existing] };
      } else {
        // Create new stat
        this.stats.push(newStat);
        return { rows: [newStat] };
      }
    }

    // SELECT FROM user_stats
    if (normalizedText.includes('SELECT') && normalizedText.includes('FROM USER_STATS')) {
      const userId = params[0];
      const userStats = this.stats.filter(s => s.user_id === userId);
      return { rows: userStats };
    }

    return { rows: [] };
  }
}

const crypto = require('crypto');
module.exports = { MockClient };