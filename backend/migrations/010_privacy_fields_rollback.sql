-- Rollback Migration 010: Remove privacy fields from tasks and users tables
-- Date: 2026-01-09
-- Purpose: Rollback privacy feature if needed

-- ============================================
-- TASKS TABLE: Remove privacy fields
-- ============================================

-- Remove indexes first
DROP INDEX IF EXISTS idx_tasks_is_private;
DROP INDEX IF EXISTS idx_tasks_privacy_level;

-- Remove columns
ALTER TABLE tasks DROP COLUMN IF EXISTS is_private;
ALTER TABLE tasks DROP COLUMN IF EXISTS privacy_level;
ALTER TABLE tasks DROP COLUMN IF EXISTS privacy_display;

-- ============================================
-- USERS TABLE: Remove privacy preferences
-- ============================================

ALTER TABLE users DROP COLUMN IF EXISTS default_privacy_level;
ALTER TABLE users DROP COLUMN IF EXISTS auto_hide_work_tasks;
ALTER TABLE users DROP COLUMN IF EXISTS work_hours_start;
ALTER TABLE users DROP COLUMN IF EXISTS work_hours_end;
ALTER TABLE users DROP COLUMN IF EXISTS biometric_reveal_enabled;
ALTER TABLE users DROP COLUMN IF EXISTS hide_all_tasks_mode;
