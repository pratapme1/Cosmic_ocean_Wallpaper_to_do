-- Rollback Migration 011: Remove achievements system
-- Date: 2026-01-09

-- Drop the user_achievements table
DROP TABLE IF EXISTS user_achievements;

-- Remove achievement columns from users table
ALTER TABLE users DROP COLUMN IF EXISTS show_achievements_on_wallpaper;
ALTER TABLE users DROP COLUMN IF EXISTS achievement_notification_enabled;
