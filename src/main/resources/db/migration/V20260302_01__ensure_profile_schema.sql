-- V20260302_01__ensure_profile_schema.sql
-- Adds missing columns to `profiles` safely for MySQL/MariaDB (XAMPP).

SET @db := DATABASE();

-- faculty
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=@db AND table_name='profiles' AND column_name='faculty') = 0,
    'ALTER TABLE profiles ADD COLUMN faculty VARCHAR(120) NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cover_photo_path
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=@db AND table_name='profiles' AND column_name='cover_photo_path') = 0,
    'ALTER TABLE profiles ADD COLUMN cover_photo_path VARCHAR(500) NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- contact_visible
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=@db AND table_name='profiles' AND column_name='contact_visible') = 0,
    'ALTER TABLE profiles ADD COLUMN contact_visible TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;