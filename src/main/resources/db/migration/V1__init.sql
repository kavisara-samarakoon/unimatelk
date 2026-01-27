CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(320) NOT NULL,
  name VARCHAR(200) NOT NULL,
  picture_url VARCHAR(500) NULL,
  role VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  last_active_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  campus VARCHAR(120) NOT NULL,
  degree VARCHAR(200) NOT NULL,
  year_of_study INT NOT NULL,
  gender VARCHAR(20) NOT NULL,
  gender_preference VARCHAR(20) NOT NULL,
  move_in_month VARCHAR(20) NULL,
  bio VARCHAR(800) NULL,
  phone VARCHAR(50) NULL,
  facebook_url VARCHAR(300) NULL,
  instagram_url VARCHAR(300) NULL,
  profile_photo_path VARCHAR(500) NULL,
  cover_photo_path VARCHAR(500) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_profiles_user_id (user_id),
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_profiles_campus (campus),
  INDEX idx_profiles_degree (degree),
  INDEX idx_profiles_year (year_of_study)
) ENGINE=InnoDB;

CREATE TABLE preferences (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  sleep_schedule TINYINT NULL,
  cleanliness TINYINT NULL,
  noise_tolerance TINYINT NULL,
  guests TINYINT NULL,
  smoking_ok BOOLEAN NULL,
  drinking_ok BOOLEAN NULL,
  introvert TINYINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_preferences_user_id (user_id),
  CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE match_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL,
  UNIQUE KEY uk_match_from_to (from_user_id, to_user_id),
  CONSTRAINT fk_match_from FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_match_to FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_match_to (to_user_id)
) ENGINE=InnoDB;

CREATE TABLE chat_rooms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE chat_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_room_member (room_id, user_id),
  CONSTRAINT fk_chat_member_room FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_chat_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  sender_user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  content VARCHAR(2000) NULL,
  attachment_url VARCHAR(600) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_message_room FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_messages_room_created (room_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE blocks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  blocker_user_id BIGINT NOT NULL,
  blocked_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_blocks_pair (blocker_user_id, blocked_user_id),
  CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_user_id BIGINT NOT NULL,
  reported_user_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reports_pair (reporter_user_id, reported_user_id),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_reported FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_reports_reported_created (reported_user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE moderation_cases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reported_user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  action VARCHAR(30) NULL,
  resolution_note VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at TIMESTAMP NULL,
  resolved_by_user_id BIGINT NULL,
  CONSTRAINT fk_case_reported FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_case_resolved_by FOREIGN KEY (resolved_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_cases_status (status)
) ENGINE=InnoDB;
