-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users Table
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  theme VARCHAR(50) DEFAULT 'cosmic',
  resolution VARCHAR(20) DEFAULT '1170x2532',
  display_mode VARCHAR(20) DEFAULT 'one_thing',
  timezone VARCHAR(50) DEFAULT 'UTC',
  setup_complete BOOLEAN DEFAULT FALSE,
  done_for_today BOOLEAN DEFAULT FALSE,
  done_for_today_at TIMESTAMP,
  wallpaper_token VARCHAR(255) UNIQUE,
  -- Epic 10: Privacy settings
  default_privacy_level VARCHAR(20) DEFAULT 'public',
  auto_hide_work_tasks BOOLEAN DEFAULT FALSE,
  work_hours_start TIME DEFAULT '09:00',
  work_hours_end TIME DEFAULT '17:00',
  biometric_reveal_enabled BOOLEAN DEFAULT TRUE,
  hide_all_tasks_mode BOOLEAN DEFAULT FALSE,
  -- Epic 10 Phase 3: Environment settings
  time_of_day_mode VARCHAR(20) DEFAULT 'auto',
  manual_time_period VARCHAR(20) DEFAULT 'morning',
  weather_overlay_enabled BOOLEAN DEFAULT TRUE,
  particle_intensity VARCHAR(20) DEFAULT 'medium',
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Tasks Table
CREATE TABLE IF NOT EXISTS tasks (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  due_date DATE,
  due_time TIME,
  estimate_minutes INT,
  priority INT DEFAULT 0,
  completed BOOLEAN DEFAULT FALSE,
  completed_at TIMESTAMP,
  snoozed_until TIMESTAMP,
  context_location VARCHAR(20),
  context_time VARCHAR(20),
  energy_required VARCHAR(20),
  decay_prompted BOOLEAN DEFAULT FALSE,
  original_due_date DATE,
  times_rescheduled INT DEFAULT 0,
  x DOUBLE PRECISION,
  y DOUBLE PRECISION,
  is_subtask BOOLEAN DEFAULT FALSE,
  is_recurring BOOLEAN DEFAULT FALSE,
  echo_interval VARCHAR(20),
  archived BOOLEAN DEFAULT FALSE,
  archived_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- User Stats Table
CREATE TABLE IF NOT EXISTS user_stats (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  week_start DATE NOT NULL,
  app_opens INT DEFAULT 0,
  widget_interactions INT DEFAULT 0,
  tasks_created INT DEFAULT 0,
  tasks_completed INT DEFAULT 0,
  tasks_completed_via_widget INT DEFAULT 0,
  UNIQUE(user_id, week_start)
);
