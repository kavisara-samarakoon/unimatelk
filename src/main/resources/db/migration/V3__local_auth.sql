-- Add local email/password login support + password reset support

ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'GOOGLE';

ALTER TABLE users ADD COLUMN reset_token VARCHAR(120) NULL;
ALTER TABLE users ADD COLUMN reset_token_expires_at DATETIME NULL;

CREATE INDEX idx_users_reset_token ON users (reset_token);
