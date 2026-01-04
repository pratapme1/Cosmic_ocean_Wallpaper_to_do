-- Migration 007: Add NLP metadata fields to tasks table
-- Date: 2026-01-04
-- Purpose: Support comprehensive NLP parsing (context tags, categories, energy, recurring)
-- Epic: Epic 7 - NLP Integration & UX Polish

-- Add NLP metadata columns
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS context_tags TEXT[];
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS energy_level VARCHAR(20);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS time_context VARCHAR(20);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurring_interval VARCHAR(20);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurring_day_of_week INTEGER;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurring_day_of_month INTEGER;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS raw_title TEXT;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_tasks_category ON tasks(category);
CREATE INDEX IF NOT EXISTS idx_tasks_energy_level ON tasks(energy_level);
CREATE INDEX IF NOT EXISTS idx_tasks_context_tags ON tasks USING GIN(context_tags);

-- Add comments for documentation
COMMENT ON COLUMN tasks.category IS 'Auto-detected category: work, personal, health, finance, learning, social, errands, general';
COMMENT ON COLUMN tasks.context_tags IS 'Array of @tags extracted from task input (e.g., @home, @work, @gym)';
COMMENT ON COLUMN tasks.energy_level IS 'Energy required: high, medium, low (detected from keywords)';
COMMENT ON COLUMN tasks.time_context IS 'Time preference: morning, afternoon, evening, night';
COMMENT ON COLUMN tasks.recurring_interval IS 'Recurrence: daily, weekly, biweekly, monthly';
COMMENT ON COLUMN tasks.recurring_day_of_week IS 'Day of week for recurring (0=Sunday, 6=Saturday)';
COMMENT ON COLUMN tasks.recurring_day_of_month IS 'Day of month for recurring (1-31)';
COMMENT ON COLUMN tasks.raw_title IS 'Original user input before NLP parsing';

-- Verification query (run after migration)
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'tasks'
-- AND column_name IN ('category', 'context_tags', 'energy_level', 'time_context', 'recurring_interval', 'recurring_day_of_week', 'recurring_day_of_month', 'raw_title')
-- ORDER BY column_name;
