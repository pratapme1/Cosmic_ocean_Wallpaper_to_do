-- Migration 011: Create achievements system tables
-- Date: 2026-01-09
-- Purpose: Track user achievements and badges for gamification
-- Epic: Epic 10 Phase 2 - Achievement System

-- ============================================
-- USER_ACHIEVEMENTS TABLE
-- ============================================

-- Store earned achievements
CREATE TABLE IF NOT EXISTS user_achievements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id VARCHAR(50) NOT NULL,
    earned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    dismissed BOOLEAN DEFAULT FALSE,
    dismissed_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, achievement_id)
);

-- Create indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_user_achievements_user_id ON user_achievements(user_id);
CREATE INDEX IF NOT EXISTS idx_user_achievements_earned_at ON user_achievements(earned_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_achievements_dismissed ON user_achievements(user_id, dismissed) WHERE NOT dismissed;

-- Add comments for documentation
COMMENT ON TABLE user_achievements IS 'Stores earned user achievements (badges)';
COMMENT ON COLUMN user_achievements.achievement_id IS 'Achievement type ID (e.g., streak_7, milestone_100, speed_demon)';
COMMENT ON COLUMN user_achievements.earned_at IS 'When the achievement was first earned';
COMMENT ON COLUMN user_achievements.dismissed IS 'User dismissed this achievement from display';
COMMENT ON COLUMN user_achievements.metadata IS 'Additional data (e.g., category for category_master)';

-- ============================================
-- USERS TABLE: Achievement preferences
-- ============================================

-- Add achievement display preferences to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS show_achievements_on_wallpaper BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS achievement_notification_enabled BOOLEAN DEFAULT TRUE;

COMMENT ON COLUMN users.show_achievements_on_wallpaper IS 'Display achievement badges on wallpaper';
COMMENT ON COLUMN users.achievement_notification_enabled IS 'Send notifications for new achievements';

-- ============================================
-- Verification query (run after migration)
-- ============================================
-- SELECT column_name, data_type, column_default, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'user_achievements'
-- ORDER BY ordinal_position;
