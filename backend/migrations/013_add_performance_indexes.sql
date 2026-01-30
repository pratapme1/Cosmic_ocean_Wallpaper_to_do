-- Migration 013: Add Performance Indexes
-- Date: 2026-01-30
-- Purpose: Fix DB timeouts by adding missing indexes for frequent queries

-- TASKS TABLE INDEXES
-- Support: SELECT * FROM tasks WHERE user_id = $1 AND completed = false
CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, completed, archived);

-- Support: Urgency calculation and sorting by due date
CREATE INDEX IF NOT EXISTS idx_tasks_user_due ON tasks(user_id, due_date, due_time);

-- Support: Priority sorting
CREATE INDEX IF NOT EXISTS idx_tasks_user_priority ON tasks(user_id, priority DESC);

-- USERS TABLE INDEXES
-- Support: Fast authenticatin via wallpaper_token
CREATE INDEX IF NOT EXISTS idx_users_wallpaper_token ON users(wallpaper_token);

-- Support: Fast user lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
