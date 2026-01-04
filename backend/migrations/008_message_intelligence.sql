-- Migration 008: Epic 8 - LLM Message Intelligence System
-- Date: 2026-01-05
-- Purpose: Add tables for LLM message generation, caching, and analytics

-- ============================================================
-- TABLE 1: parse_analytics
-- Purpose: Track parsing accuracy and user edit rates
-- ============================================================

CREATE TABLE IF NOT EXISTS parse_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    original_input TEXT NOT NULL,
    parsed_output JSONB NOT NULL,        -- Full parsed result
    confidence FLOAT,                     -- LLM confidence score (0.0-1.0)
    source VARCHAR(20) NOT NULL,          -- 'llm', 'local', 'local_fallback', 'error_fallback'
    user_edited BOOLEAN DEFAULT FALSE,    -- Did user change parsed result?
    edited_fields JSONB,                  -- Which fields did user change? {"dueDate": true, "priority": true}
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_parse_analytics_user ON parse_analytics(user_id);
CREATE INDEX IF NOT EXISTS idx_parse_analytics_source ON parse_analytics(source);
CREATE INDEX IF NOT EXISTS idx_parse_analytics_created ON parse_analytics(created_at DESC);

-- ============================================================
-- TABLE 2: message_cache
-- Purpose: Store LLM-generated message variants for rotation
-- ============================================================

CREATE TABLE IF NOT EXISTS message_cache (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    voice VARCHAR(20) NOT NULL,       -- WARM_FRIEND, QUIET_OBSERVER, PLAYFUL, POETIC, DIRECT
    intent VARCHAR(20) NOT NULL,      -- CELEBRATE, NUDGE, TIME_AWARE, STREAK_FOCUS, PERMISSION, FOCUS_NEXT
    generated_at TIMESTAMP DEFAULT NOW(),
    display_order INTEGER NOT NULL,   -- Order for rotation (1-5)
    shown BOOLEAN DEFAULT FALSE,
    shown_at TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_message_cache_user_shown ON message_cache(user_id, shown);
CREATE INDEX IF NOT EXISTS idx_message_cache_generated ON message_cache(generated_at DESC);

-- ============================================================
-- TABLE 3: message_history
-- Purpose: Track shown messages for analytics and learning
-- ============================================================

CREATE TABLE IF NOT EXISTS message_history (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    voice VARCHAR(20) NOT NULL,
    intent VARCHAR(20) NOT NULL,
    context JSONB,                    -- State when message was shown
    shown_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_message_history_user ON message_history(user_id);
CREATE INDEX IF NOT EXISTS idx_message_history_shown ON message_history(shown_at DESC);

-- ============================================================
-- Add comment for documentation
-- ============================================================

COMMENT ON TABLE parse_analytics IS 'Tracks LLM parsing accuracy and user edit rates for Epic 8';
COMMENT ON TABLE message_cache IS 'Stores LLM-generated message variants for rotation in wallpapers';
COMMENT ON TABLE message_history IS 'Audit log of shown messages for analytics and freshness tracking';

-- ============================================================
-- Grant permissions (if using specific user)
-- ============================================================

-- Grant permissions to app user (adjust username if different)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON parse_analytics TO cosmic_ocean_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON message_cache TO cosmic_ocean_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON message_history TO cosmic_ocean_app;
-- GRANT USAGE, SELECT ON SEQUENCE message_cache_id_seq TO cosmic_ocean_app;
-- GRANT USAGE, SELECT ON SEQUENCE message_history_id_seq TO cosmic_ocean_app;
