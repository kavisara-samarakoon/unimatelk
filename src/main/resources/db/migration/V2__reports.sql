CREATE TABLE IF NOT EXISTS reports (
                                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                       reporter_user_id BIGINT NOT NULL,
                                       reported_user_id BIGINT NOT NULL,
                                       reason VARCHAR(120) NOT NULL,
    details TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    resolved_by_user_id BIGINT NULL,

    INDEX idx_reports_status (status),
    INDEX idx_reports_reported (reported_user_id),
    INDEX idx_reports_reporter (reporter_user_id)
    );
