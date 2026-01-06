-- Rollback: Epic 8 optimization indexes
-- Run this if migration 009 needs to be reverted

DROP INDEX IF EXISTS idx_tasks_user_created_at;
DROP INDEX IF EXISTS idx_tasks_user_completed_at;
DROP INDEX IF EXISTS idx_tasks_wallpaper_query;
