-- Add custom wallpaper support
ALTER TABLE users ADD COLUMN IF NOT EXISTS wallpaper_mode VARCHAR(20) DEFAULT 'generated';
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_wallpaper_path VARCHAR(255);
