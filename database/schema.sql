DROP DATABASE IF EXISTS delivery_system;
CREATE DATABASE delivery_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE delivery_system;

-- Users table with BLP security fields and login lockout tracking
CREATE TABLE users (
    user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    role ENUM('customer', 'driver', 'manager', 'admin') NOT NULL,
    clearance_level TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=Unclassified, 1=Confidential, 2=Secret, 3=Top Secret',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_attempts INT DEFAULT 0 COMMENT 'Failed login attempts counter for account lockout',
    lockout_until TIMESTAMP NULL COMMENT 'Account locked until this time (NULL if not locked)',

    INDEX idx_username (username),
    INDEX idx_lockout (lockout_until),
    CONSTRAINT chk_clearance_level CHECK (clearance_level BETWEEN 0 AND 3)
) ENGINE=InnoDB;

-- Security labels table for BLP object classification
CREATE TABLE security_labels (
    label_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL COMMENT 'Table name',
    object_id BIGINT UNSIGNED NOT NULL COMMENT 'Record ID',
    classification_level TINYINT UNSIGNED NOT NULL COMMENT '0=U, 1=C, 2=S, 3=TS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_object (object_type, object_id),
    UNIQUE KEY unique_object (object_type, object_id)
) ENGINE=InnoDB;

-- Audit log table for tracking access attempts
CREATE TABLE audit_log (
    audit_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT UNSIGNED NULL,
    username VARCHAR(50) NULL,
    action VARCHAR(50) NOT NULL,
    result ENUM('success', 'denied', 'error') NOT NULL,
    ip_address VARCHAR(45) NULL,
    details TEXT NULL,

    INDEX idx_timestamp (timestamp),
    INDEX idx_username (username),
    INDEX idx_action (action),
    INDEX idx_result (result)
) ENGINE=InnoDB;

-- MFA codes table for two-factor authentication
CREATE TABLE mfa_codes (
    code_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(6) NOT NULL,
    expiry_time TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_expiry (user_id, expiry_time),
    INDEX idx_code (code)
) ENGINE=InnoDB;

-- Insert test users with different clearance levels
-- Password hashing: SHA-256(password + salt)
INSERT INTO users (username, password_hash, salt, role, clearance_level) VALUES
-- Unclassified Customer (password: cust123)
('customer1', '89aef0b5cf9f99948b628f896b389b0716a49a28f25d8bcd54ca8a23c9f57231', '6229fb329c71be1f7b5a09098d9b7ee9', 'customer', 0),

-- Confidential Driver (password: driver123)
('driver1', '5da135c0e7849087335fa9991cee8b6a9b95e8c993e8985247bcc5ed70768052', '859dca20bbe7a43724f7f7672ae52578', 'driver', 1),

-- Secret Manager (password: mgr123)
('manager1', '1394a9fc85abb93902ae39705bc7bf3cd80e930fcd85b6e250ab9432d6df1ad6', '05a2b0d8103b5aa272bb19ceb135db6a', 'manager', 2),

-- Top Secret Admin (password: admin123)
('admin', 'ab118b881ca2e8b15b63b4facefb8982f7d965597dc93c3791b4e3d8fdae7445', '6d2533dc0220cc3cb4f422648f194574', 'admin', 3);

SELECT '============================================================' AS '';
SELECT 'DATABASE CREATED SUCCESSFULLY' AS '';
SELECT '============================================================' AS '';
SELECT 'Database: delivery_system' AS '';
SELECT 'Tables: users, security_labels, audit_log, mfa_codes' AS '';
SELECT 'Test Users: customer1, driver1, manager1, admin' AS '';
SELECT '' AS '';
SELECT 'Test Credentials (ready for login testing):' AS '';
SELECT '  customer1 / cust123 (Clearance: 0 - Unclassified)' AS '';
SELECT '  driver1 / driver123 (Clearance: 1 - Confidential)' AS '';
SELECT '  manager1 / mgr123 (Clearance: 2 - Secret)' AS '';
SELECT '  admin / admin123 (Clearance: 3 - Top Secret)' AS '';
SELECT '' AS '';
SELECT 'Password Hash Method: SHA-256(password + salt)' AS '';
SELECT '' AS '';
SELECT 'Security Features Enabled:' AS '';
SELECT '  - Login lockout after 3 failed attempts (30 min)' AS '';
SELECT '  - Two-factor authentication (MFA) support' AS '';
SELECT '  - Comprehensive audit logging with IP tracking' AS '';
SELECT '  - Bell-LaPadula access control (clearance levels 0-3)' AS '';
SELECT '============================================================' AS '';
