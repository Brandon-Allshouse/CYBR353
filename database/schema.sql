DROP DATABASE IF EXISTS delivery_system;
CREATE DATABASE delivery_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE delivery_system;

-- Users table with BLP security fields and login lockout tracking
CREATE TABLE users (
    user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    email VARCHAR(255) NULL COMMENT 'Email address (customers)',
    phone VARCHAR(20) NULL COMMENT 'Phone number (customers)',
    full_name VARCHAR(100) NULL COMMENT 'Full name (customers)',
    role ENUM('customer', 'driver', 'manager', 'admin') NOT NULL,
    clearance_level TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=Unclassified, 1=Confidential, 2=Secret, 3=Top Secret',
    account_status ENUM('active', 'suspended', 'revoked') NOT NULL DEFAULT 'active' COMMENT 'Account status for admin management',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_attempts INT DEFAULT 0 COMMENT 'Failed login attempts counter for account lockout',
    lockout_until TIMESTAMP NULL COMMENT 'Account locked until this time (NULL if not locked)',

    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_lockout (lockout_until),
    INDEX idx_account_status (account_status),
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

-- Audit log table for tracking access attempts and modifications
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

-- Facilities table for warehouses and distribution centers
CREATE TABLE facilities (
    facility_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    facility_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    phone VARCHAR(20) NULL,
    capacity INT UNSIGNED NOT NULL COMMENT 'Max packages facility can hold',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_city_state (city, state)
) ENGINE=InnoDB;

-- Addresses table for customer pickup and delivery locations
CREATE TABLE addresses (
    address_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    address_type ENUM('pickup', 'delivery', 'both') NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    delivery_instructions TEXT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_zip_code (zip_code)
) ENGINE=InnoDB;

-- Orders table for customer package delivery orders
CREATE TABLE orders (
    order_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    pickup_address_id BIGINT UNSIGNED NOT NULL,
    delivery_address_id BIGINT UNSIGNED NOT NULL,
    order_status ENUM('pending', 'confirmed', 'in_transit', 'delivered', 'cancelled', 'returned') NOT NULL DEFAULT 'pending',
    total_cost DECIMAL(10, 2) NOT NULL,
    payment_status ENUM('pending', 'completed', 'failed', 'refunded') NOT NULL DEFAULT 'pending',
    estimated_delivery TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (pickup_address_id) REFERENCES addresses(address_id) ON DELETE RESTRICT,
    FOREIGN KEY (delivery_address_id) REFERENCES addresses(address_id) ON DELETE RESTRICT,
    INDEX idx_customer_id (customer_id),
    INDEX idx_order_status (order_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- Packages table for individual packages
CREATE TABLE packages (
    package_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    tracking_number VARCHAR(50) NOT NULL UNIQUE,
    current_facility_id BIGINT UNSIGNED NULL,
    package_status ENUM('created', 'at_facility', 'in_transit', 'out_for_delivery', 'delivered', 'returned', 'lost') NOT NULL DEFAULT 'created',
    weight_kg DECIMAL(8, 2) NOT NULL,
    length_cm DECIMAL(8, 2) NOT NULL,
    width_cm DECIMAL(8, 2) NOT NULL,
    height_cm DECIMAL(8, 2) NOT NULL,
    fragile BOOLEAN DEFAULT FALSE,
    signature_required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP NULL,

    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE RESTRICT,
    FOREIGN KEY (current_facility_id) REFERENCES facilities(facility_id) ON DELETE SET NULL,
    INDEX idx_tracking_number (tracking_number),
    INDEX idx_order_id (order_id),
    INDEX idx_package_status (package_status),
    INDEX idx_current_facility (current_facility_id)
) ENGINE=InnoDB;

-- Payment information table
CREATE TABLE payments (
    payment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    payment_method ENUM('credit_card', 'debit_card', 'paypal', 'cash') NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_status ENUM('pending', 'completed', 'failed', 'refunded') NOT NULL DEFAULT 'pending',
    transaction_id VARCHAR(100) NULL,
    processed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE RESTRICT,
    INDEX idx_order_id (order_id),
    INDEX idx_payment_status (payment_status)
) ENGINE=InnoDB;

-- Routes table for delivery routes
CREATE TABLE routes (
    route_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_name VARCHAR(100) NOT NULL,
    facility_id BIGINT UNSIGNED NOT NULL,
    route_date DATE NOT NULL,
    estimated_duration_minutes INT UNSIGNED NOT NULL,
    total_stops INT UNSIGNED NOT NULL,
    route_status ENUM('planned', 'in_progress', 'completed', 'cancelled') NOT NULL DEFAULT 'planned',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,

    FOREIGN KEY (facility_id) REFERENCES facilities(facility_id) ON DELETE RESTRICT,
    INDEX idx_route_date (route_date),
    INDEX idx_facility_id (facility_id),
    INDEX idx_route_status (route_status)
) ENGINE=InnoDB;

-- Route assignments table for assigning routes to drivers
CREATE TABLE route_assignments (
    assignment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT UNSIGNED NOT NULL,
    driver_id BIGINT UNSIGNED NOT NULL,
    vehicle_id VARCHAR(50) NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,

    FOREIGN KEY (route_id) REFERENCES routes(route_id) ON DELETE CASCADE,
    FOREIGN KEY (driver_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    INDEX idx_route_id (route_id),
    INDEX idx_driver_id (driver_id),
    UNIQUE KEY unique_route_driver (route_id, driver_id)
) ENGINE=InnoDB;

-- Route packages junction table for packages in routes
CREATE TABLE route_packages (
    route_package_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT UNSIGNED NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    stop_sequence INT UNSIGNED NOT NULL,
    estimated_arrival TIMESTAMP NULL,

    FOREIGN KEY (route_id) REFERENCES routes(route_id) ON DELETE CASCADE,
    FOREIGN KEY (package_id) REFERENCES packages(package_id) ON DELETE CASCADE,
    INDEX idx_route_id (route_id),
    INDEX idx_package_id (package_id),
    UNIQUE KEY unique_route_package (route_id, package_id)
) ENGINE=InnoDB;

-- Delivery status history for tracking package updates
CREATE TABLE delivery_status_history (
    history_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    package_id BIGINT UNSIGNED NOT NULL,
    status ENUM('created', 'at_facility', 'in_transit', 'out_for_delivery', 'delivered', 'returned', 'lost', 'exception') NOT NULL,
    location VARCHAR(255) NULL,
    updated_by BIGINT UNSIGNED NULL,
    notes TEXT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (package_id) REFERENCES packages(package_id) ON DELETE CASCADE,
    FOREIGN KEY (updated_by) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_package_id (package_id),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB;

-- Package transfers between facilities
CREATE TABLE package_transfers (
    transfer_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    package_id BIGINT UNSIGNED NOT NULL,
    from_facility_id BIGINT UNSIGNED NOT NULL,
    to_facility_id BIGINT UNSIGNED NOT NULL,
    transfer_status ENUM('pending', 'in_transit', 'completed', 'cancelled') NOT NULL DEFAULT 'pending',
    initiated_by BIGINT UNSIGNED NOT NULL,
    transport_method VARCHAR(50) NULL,
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,

    FOREIGN KEY (package_id) REFERENCES packages(package_id) ON DELETE CASCADE,
    FOREIGN KEY (from_facility_id) REFERENCES facilities(facility_id) ON DELETE RESTRICT,
    FOREIGN KEY (to_facility_id) REFERENCES facilities(facility_id) ON DELETE RESTRICT,
    FOREIGN KEY (initiated_by) REFERENCES users(user_id) ON DELETE RESTRICT,
    INDEX idx_package_id (package_id),
    INDEX idx_transfer_status (transfer_status),
    INDEX idx_from_facility (from_facility_id),
    INDEX idx_to_facility (to_facility_id)
) ENGINE=InnoDB;

-- Facility inventory for tracking packages at each facility
CREATE TABLE inventory (
    inventory_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    facility_id BIGINT UNSIGNED NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    arrival_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    departure_time TIMESTAMP NULL,
    inventory_status ENUM('in_stock', 'checked_out', 'transferred') NOT NULL DEFAULT 'in_stock',

    FOREIGN KEY (facility_id) REFERENCES facilities(facility_id) ON DELETE CASCADE,
    FOREIGN KEY (package_id) REFERENCES packages(package_id) ON DELETE CASCADE,
    INDEX idx_facility_id (facility_id),
    INDEX idx_package_id (package_id),
    INDEX idx_inventory_status (inventory_status)
) ENGINE=InnoDB;

-- Package returns tracking
CREATE TABLE package_returns (
    return_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    original_order_id BIGINT UNSIGNED NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    return_reason TEXT NULL,
    return_status ENUM('requested', 'approved', 'in_transit', 'received', 'processed', 'denied') NOT NULL DEFAULT 'requested',
    requested_by BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    facility_id BIGINT UNSIGNED NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,

    FOREIGN KEY (original_order_id) REFERENCES orders(order_id) ON DELETE RESTRICT,
    FOREIGN KEY (package_id) REFERENCES packages(package_id) ON DELETE RESTRICT,
    FOREIGN KEY (requested_by) REFERENCES users(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (facility_id) REFERENCES facilities(facility_id) ON DELETE SET NULL,
    INDEX idx_package_id (package_id),
    INDEX idx_return_status (return_status),
    INDEX idx_requested_by (requested_by)
) ENGINE=InnoDB;

-- Package edit history for audit trail
CREATE TABLE package_edit_history (
    edit_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    package_id BIGINT UNSIGNED NOT NULL,
    edited_by BIGINT UNSIGNED NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    edit_reason TEXT NULL,
    edited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (package_id) REFERENCES packages(package_id) ON DELETE CASCADE,
    FOREIGN KEY (edited_by) REFERENCES users(user_id) ON DELETE RESTRICT,
    INDEX idx_package_id (package_id),
    INDEX idx_edited_by (edited_by),
    INDEX idx_edited_at (edited_at)
) ENGINE=InnoDB;

-- Insert test users with different clearance levels
-- Password hashing: SHA-256(password + salt)
INSERT INTO users (username, password_hash, salt, email, phone, full_name, role, clearance_level) VALUES
-- Unclassified Customer (password: cust123)
('customer1', '89aef0b5cf9f99948b628f896b389b0716a49a28f25d8bcd54ca8a23c9f57231', '6229fb329c71be1f7b5a09098d9b7ee9',
 'customer1@example.com', '(555) 123-4567', 'John Customer', 'customer', 0),

-- Confidential Driver (password: driver123)
('driver1', '5da135c0e7849087335fa9991cee8b6a9b95e8c993e8985247bcc5ed70768052', '859dca20bbe7a43724f7f7672ae52578',
 'driver1@example.com', '(555) 234-5678', 'Sarah Driver', 'driver', 1),

-- Secret Manager (password: mgr123)
('manager1', '1394a9fc85abb93902ae39705bc7bf3cd80e930fcd85b6e250ab9432d6df1ad6', '05a2b0d8103b5aa272bb19ceb135db6a',
 'manager1@example.com', '(555) 345-6789', 'Michael Manager', 'manager', 2),

-- Top Secret Admin (password: admin123)
('admin', 'ab118b881ca2e8b15b63b4facefb8982f7d965597dc93c3791b4e3d8fdae7445', '6d2533dc0220cc3cb4f422648f194574',
 'admin@example.com', '(555) 456-7890', 'Alice Admin', 'admin', 3);

-- Insert test facilities
INSERT INTO facilities (facility_name, address, city, state, zip_code, phone, capacity) VALUES
('Main Distribution Center', '123 Warehouse Blvd', 'Denver', 'CO', '80202', '(303) 555-1000', 50000),
('West Coast Hub', '456 Pacific Way', 'Los Angeles', 'CA', '90001', '(310) 555-2000', 35000),
('East Coast Hub', '789 Atlantic Ave', 'New York', 'NY', '10001', '(212) 555-3000', 40000);

-- Insert test addresses for customer1
INSERT INTO addresses (user_id, address_type, street_address, city, state, zip_code, delivery_instructions, is_default) VALUES
(1, 'both', '100 Main St', 'Denver', 'CO', '80203', 'Leave at front door', TRUE),
(1, 'delivery', '200 Oak Ave', 'Boulder', 'CO', '80301', 'Ring doorbell twice', FALSE);

-- Insert test order for customer1
INSERT INTO orders (customer_id, pickup_address_id, delivery_address_id, order_status, total_cost, payment_status, estimated_delivery) VALUES
(1, 1, 2, 'pending', 25.99, 'pending', DATE_ADD(NOW(), INTERVAL 3 DAY));

-- Insert test package
INSERT INTO packages (order_id, tracking_number, current_facility_id, package_status, weight_kg, length_cm, width_cm, height_cm, fragile, signature_required) VALUES
(1, 'PKG1234567890', 1, 'at_facility', 2.5, 30, 20, 15, FALSE, FALSE);

-- Insert initial delivery status
INSERT INTO delivery_status_history (package_id, status, location, updated_by, notes) VALUES
(1, 'created', 'Main Distribution Center', NULL, 'Package created and received at facility');

-- Insert test inventory entry
INSERT INTO inventory (facility_id, package_id, inventory_status) VALUES
(1, 1, 'in_stock');

SELECT '============================================================' AS '';
SELECT 'DATABASE CREATED SUCCESSFULLY' AS '';
SELECT '============================================================' AS '';
SELECT 'Test Login Credentials:' AS '';
SELECT '  customer1 / cust123' AS '';
SELECT '  driver1 / driver123' AS '';
SELECT '  manager1 / mgr123' AS '';
SELECT '  admin / admin123' AS '';
SELECT '============================================================' AS '';
