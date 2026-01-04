-- Migration 008 Rollback: Epic 8 - LLM Message Intelligence System
-- Date: 2026-01-05
-- Purpose: Rollback message intelligence tables

-- Drop tables in reverse order (respect foreign keys)
DROP TABLE IF EXISTS message_history CASCADE;
DROP TABLE IF EXISTS message_cache CASCADE;
DROP TABLE IF EXISTS parse_analytics CASCADE;

-- Note: This rollback removes all message intelligence data
-- Run this only if you need to completely remove Epic 8 message features
