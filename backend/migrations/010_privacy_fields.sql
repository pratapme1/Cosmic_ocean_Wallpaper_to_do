-- Migration 010: Add privacy fields to tasks and users tables
-- Date: 2026-01-09
-- Purpose: Support task privacy/masking on wallpaper lock screen
-- Epic: Epic 10 - Wallpaper Experience Enhancement (Phase 1)

-- ============================================
-- TASKS TABLE: Privacy fields
-- ============================================

-- Add privacy toggle (quick enable/disable)
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS is_private BOOLEAN DEFAULT FALSE;

-- Add privacy level (determines display behavior)
-- Values: public (default), category, initials, hidden, custom
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS privacy_level VARCHAR(20) DEFAULT 'public';

-- Add custom display text (shown when privacy_level='custom')
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS privacy_display VARCHAR(100);

-- Add index for privacy queries
CREATE INDEX IF NOT EXISTS idx_tasks_is_private ON tasks(is_private);
CREATE INDEX IF NOT EXISTS idx_tasks_privacy_level ON tasks(privacy_level);

-- Add comments for documentation
COMMENT ON COLUMN tasks.is_private IS 'Quick toggle: true = apply privacy_level, false = show publicly';
COMMENT ON COLUMN tasks.privacy_level IS 'Privacy display mode: public, category, initials, hidden, custom';
COMMENT ON COLUMN tasks.privacy_display IS 'Custom text to display when privacy_level=custom (e.g., "Personal matter")';

-- ============================================
-- USERS TABLE: Privacy preferences
-- ============================================

-- Default privacy level for new tasks
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_privacy_level VARCHAR(20) DEFAULT 'public';

-- Auto-hide work tasks outside work hours
ALTER TABLE users ADD COLUMN IF NOT EXISTS auto_hide_work_tasks BOOLEAN DEFAULT FALSE;

-- Work hours start time (for auto-hide feature)
ALTER TABLE users ADD COLUMN IF NOT EXISTS work_hours_start TIME DEFAULT '09:00';

-- Work hours end time (for auto-hide feature)
ALTER TABLE users ADD COLUMN IF NOT EXISTS work_hours_end TIME DEFAULT '17:00';

-- Enable biometric reveal on lock screen (future Android feature)
ALTER TABLE users ADD COLUMN IF NOT EXISTS biometric_reveal_enabled BOOLEAN DEFAULT TRUE;

-- Master switch to hide ALL tasks on wallpaper
ALTER TABLE users ADD COLUMN IF NOT EXISTS hide_all_tasks_mode BOOLEAN DEFAULT FALSE;

-- Add comments for documentation
COMMENT ON COLUMN users.default_privacy_level IS 'Default privacy level for new tasks: public, category, initials, hidden, custom';
COMMENT ON COLUMN users.auto_hide_work_tasks IS 'When true, work-category tasks hidden outside work_hours_start/end';
COMMENT ON COLUMN users.work_hours_start IS 'Start of work hours (e.g., 09:00) for auto-hide feature';
COMMENT ON COLUMN users.work_hours_end IS 'End of work hours (e.g., 17:00) for auto-hide feature';
COMMENT ON COLUMN users.biometric_reveal_enabled IS 'Allow biometric unlock to reveal hidden tasks on lock screen';
COMMENT ON COLUMN users.hide_all_tasks_mode IS 'Master switch: when true, wallpaper shows theme only, no tasks';

-- ============================================
-- Verification query (run after migration)
-- ============================================
-- SELECT column_name, data_type, column_default, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'tasks'
-- AND column_name IN ('is_private', 'privacy_level', 'privacy_display')
-- ORDER BY column_name;

-- SELECT column_name, data_type, column_default, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'users'
-- AND column_name IN ('default_privacy_level', 'auto_hide_work_tasks', 'work_hours_start', 'work_hours_end', 'biometric_reveal_enabled', 'hide_all_tasks_mode')
-- ORDER BY column_name;
