-- Rollback Migration 007: Remove NLP metadata fields from tasks table
-- Date: 2026-01-04
-- Purpose: Rollback NLP fields if migration causes issues

-- Drop indexes first
DROP INDEX IF EXISTS idx_tasks_context_tags;
DROP INDEX IF EXISTS idx_tasks_energy_level;
DROP INDEX IF EXISTS idx_tasks_category;

-- Drop columns (use CASCADE to drop dependent objects)
ALTER TABLE tasks DROP COLUMN IF EXISTS category CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS context_tags CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS energy_level CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS time_context CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS recurring_interval CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS recurring_day_of_week CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS recurring_day_of_month CASCADE;
ALTER TABLE tasks DROP COLUMN IF EXISTS raw_title CASCADE;

-- Verify rollback
-- SELECT column_name FROM information_schema.columns
-- WHERE table_name = 'tasks'
-- AND column_name IN ('category', 'context_tags', 'energy_level', 'time_context', 'recurring_interval', 'recurring_day_of_week', 'recurring_day_of_month', 'raw_title');
-- Should return 0 rows
