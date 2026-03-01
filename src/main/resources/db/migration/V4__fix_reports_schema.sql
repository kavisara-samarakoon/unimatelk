-- V4__fix_reports_schema.sql
-- Purpose: Make sure the `reports` table matches the Report entity fields
-- Compatible with MySQL / MariaDB

-- 1) Add missing columns safely (only if they do not already exist)

SET @db_name = DATABASE();

-- details (TEXT)
SET @has_details =
(
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'reports'
    AND COLUMN_NAME = 'details'
);
SET @sql_details = IF(@has_details = 0,
  'ALTER TABLE reports ADD COLUMN details TEXT NULL AFTER reason;',
  'SELECT 1;'
);
PREPARE stmt FROM @sql_details;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- status (VARCHAR)
SET @has_status =
(
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'reports'
    AND COLUMN_NAME = 'status'
);
SET @sql_status = IF(@has_status = 0,
  "ALTER TABLE reports ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN' AFTER details;",
  'SELECT 1;'
);
PREPARE stmt FROM @sql_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- resolved_at (DATETIME)
SET @has_resolved_at =
(
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'reports'
    AND COLUMN_NAME = 'resolved_at'
);
SET @sql_resolved_at = IF(@has_resolved_at = 0,
  'ALTER TABLE reports ADD COLUMN resolved_at DATETIME NULL AFTER created_at;',
  'SELECT 1;'
);
PREPARE stmt FROM @sql_resolved_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- resolved_by_user_id (BIGINT)
SET @has_resolved_by =
(
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'reports'
    AND COLUMN_NAME = 'resolved_by_user_id'
);
SET @sql_resolved_by = IF(@has_resolved_by = 0,
  'ALTER TABLE reports ADD COLUMN resolved_by_user_id BIGINT NULL AFTER resolved_at;',
  'SELECT 1;'
);
PREPARE stmt FROM @sql_resolved_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 2) Add indexes safely (only if they do not already exist)

-- Index for filtering reports by status
SET @has_idx_status =
(
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'reports'
    AND INDEX_NAME = 'idx_reports_status'
);
SET @sql_idx_status = IF(@has_idx_status = 0,
  'CREATE INDEX idx_reports_status ON reports(status);',
  'SELECT 1;'
);
PREPARE stmt FROM @sql_idx_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Index for sorting / filtering by created_at
SET @has_idx_created =
(
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'reports'
    AND INDEX_NAME = 'idx_reports_created_at'
);
SET @sql_idx_created = IF(@has_idx_created = 0,
  'CREATE INDEX idx_reports_created_at ON reports(created_at);',
  'SELECT 1;'
);
PREPARE stmt FROM @sql_idx_created;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 3) Optional: add foreign key for resolved_by_user_id if you want strict integrity
-- (Only do this if your `users` table name is correct and engine supports it.)
-- If you are unsure, skip it to avoid migration failure.

-- Example (UNCOMMENT only if your users table is named `app_users` or `users` and you confirmed it):
-- ALTER TABLE reports
--   ADD CONSTRAINT fk_reports_resolved_by
--   FOREIGN KEY (resolved_by_user_id) REFERENCES app_users(id);