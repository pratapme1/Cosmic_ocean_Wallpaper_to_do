-- Epic 10 Phase 3: Environment Settings Migration
-- Run this in Supabase SQL Editor to add environment settings columns

-- Add environment settings columns to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS time_of_day_mode VARCHAR(20) DEFAULT 'auto';
ALTER TABLE users ADD COLUMN IF NOT EXISTS manual_time_period VARCHAR(20) DEFAULT 'morning';
ALTER TABLE users ADD COLUMN IF NOT EXISTS weather_overlay_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS particle_intensity VARCHAR(20) DEFAULT 'medium';

-- Verify columns were added
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'users'
AND column_name IN ('time_of_day_mode', 'manual_time_period', 'weather_overlay_enabled', 'particle_intensity');
