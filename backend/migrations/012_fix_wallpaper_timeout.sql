-- Fix wallpaper generation timeouts (2026-01-30)
--
-- Problem: Wallpaper generation queries "all tasks" or performs heavy aggregation
-- causing statement timeouts under load.
-- Solution: Add covering index for the specific columns used in summary stats.
--
-- Used by: buildMessageContext (message-generator-llm.js)
-- Query: COUNT(*) FILTER (...)
-- Columns needed: user_id, completed, archived, due_date, completed_at

CREATE INDEX IF NOT EXISTS idx_tasks_summary
ON tasks(user_id, completed, archived, due_date)
INCLUDE (completed_at);

-- Add comment
COMMENT ON INDEX idx_tasks_summary IS 'Covering index for wallpaper dashboard summary stats';
