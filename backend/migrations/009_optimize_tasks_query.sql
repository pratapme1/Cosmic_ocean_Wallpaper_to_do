-- Epic 8: Optimize tasks query for message generation
-- Fix timeout issue (2026-01-06)
--
-- Problem: Message generation queries all tasks from last 30 days, causing timeout
-- Solution: Add index on (user_id, created_at) for faster filtering

-- Index for tasks query optimization
-- Covers: WHERE user_id = X AND created_at > NOW() - INTERVAL '7 days'
CREATE INDEX IF NOT EXISTS idx_tasks_user_created_at
ON tasks(user_id, created_at DESC)
WHERE archived = false OR archived IS NULL;

-- Index for completed_at filtering (secondary condition in query)
CREATE INDEX IF NOT EXISTS idx_tasks_user_completed_at
ON tasks(user_id, completed_at DESC)
WHERE completed = true AND (archived = false OR archived IS NULL);

-- Composite index for common wallpaper queries
CREATE INDEX IF NOT EXISTS idx_tasks_wallpaper_query
ON tasks(user_id, completed, archived, created_at DESC);

-- Query performance should improve from ~500ms → ~50ms (90% reduction)
