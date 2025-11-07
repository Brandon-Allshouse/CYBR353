# Optimized Delivery System

**Team Members:** Brody Scott, Dawson Pfabe, Brandon Allshouse, Tyler Slack
**Course:** CYBR 353

A secure delivery management system demonstrating professional cybersecurity practices including Bell-LaPadula mandatory access control, defense-in-depth security architecture, and comprehensive audit logging.

## Table of Contents

- [Tech Stack](#tech-stack)
- [Security Features](#security-features)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [API Endpoints](#api-endpoints)
- [Error Handling: Result<T, E> Pattern](#error-handling-resultt-e-pattern)
- [Bell-LaPadula Access Control](#bell-lapadula-access-control)
- [Setup Instructions](#setup-instructions)
- [Running the Application](#running-the-application)
- [PowerShell Commands (Windows)](#powershell-commands-windows)
- [Test Credentials](#test-credentials)
- [Development Notes](#development-notes)
- [Git Workflow](#git-workflow)
- [Troubleshooting](#troubleshooting)
- [Milestone Progress](#milestone-1-checklist-login-with-blp-security)

## Tech Stack

- **Frontend:** HTML5, CSS3, JavaScript (vanilla - no frameworks)
- **Backend:** Java 21 LTS with built-in `HttpServer` (no Spring Boot)
- **Database:** MySQL 8.0+ with InnoDB engine
- **Security Model:** Bell-LaPadula (BLP) Mandatory Access Control
- **Authentication:** SHA-256 password hashing with salt
- **Session Management:** In-memory with configurable expiry
- **Deployment:** Localhost development environment (port 8081)

## Security Features

This project implements **defense-in-depth** with multiple layers of security:

### Authentication & Authorization
- **Password Security:**
  - SHA-256 hashing with 16-byte random salt per user
  - Password strength validation (min 8 chars, uppercase, lowercase, digit, special char)
  - Secure password comparison using hex string matching

- **Session Management:**
  - UUID-based session tokens
  - HttpOnly cookies (XSS protection)
  - Configurable session timeout (default: 1 hour)
  - Session data includes username, role, and clearance level

- **Bell-LaPadula Access Control:**
  - Four clearance levels: Unclassified (0), Confidential (1), Secret (2), Top Secret (3)
  - **No Read Up:** Users can only read data at or below their clearance
  - **No Write Down:** Users can only write data at or above their clearance
  - All access decisions logged to audit trail

### Attack Prevention
- **Brute Force Protection:**
  - Account lockout after 3 failed login attempts
  - 30-minute lockout duration
  - Failed attempts tracked per user account

- **Rate Limiting:**
  - Token bucket algorithm with time-window tracking
  - Login endpoint: 5 requests/minute per identifier
  - General endpoints: 60 requests/minute
  - Temporary ban capability for severe violations

- **Input Validation & Sanitization:**
  - Multi-layer defense against injection attacks
  - SQL injection prevention via prepared statements
  - XSS protection through HTML encoding and sanitization
  - Command injection prevention (shell metacharacter removal)
  - Business rule validation (email format, phone format, tracking IDs)

- **Two-Factor Authentication (MFA):**
  - 6-digit time-limited codes
  - 5-minute expiration window
  - Single-use codes (prevents replay attacks)
  - Stored in database with expiry tracking

### Audit & Monitoring
- **Comprehensive Audit Logging:**
  - All authentication attempts logged with IP address
  - BLP access control decisions tracked
  - Account lockout events recorded
  - Failed login attempts captured
  - Stack trace logging for errors (truncated to 500 chars)
  - Synchronized logging to prevent race conditions

### Input Security
The `InputSanitizer.java` and `InputValidator.java` classes provide:
- Email validation with regex patterns
- Phone number validation (international format support)
- Username validation (3-20 alphanumeric + underscore)
- Tracking ID format validation (`D-XXX-XXX`)
- URL validation (blocks `javascript:`, `data:`, `vbscript:` schemes)
- HTML tag stripping and encoding
- Shell metacharacter removal (`;&|$()`)
- Null byte filtering

## Project Structure

```
Optimized-Delivery-System/
├── .env.example              # Environment variables template
├── .env                      # Local config (NEVER COMMIT)
├── .gitignore                # Git exclusions (.env, *.class, etc.)
├── README.md                 # This file
├── LICENSE                   # Project license
│
├── .github/
│   └── workflows/
│       └── codeql.yml        # GitHub security scanning
│
├── frontend/
│   ├── login.html            # Login page UI
│   ├── css/
│   │   └── styles.css        # Modern responsive styling
│   └── js/
│       └── auth.js           # Authentication handler
│   └── *-dashboard.html      # [IN DEVELOPMENT] Role-based dashboards
│
├── backend/
│   ├── lib/
│   │   └── mysql-connector-j-8.4.0.jar    # JDBC driver (2.5 MB)
│   └── src/
│       └── com/delivery/
│           ├── Main.java                   # HTTP server entry point
│           │
│           ├── controllers/
│           │   └── AuthenticationController.java
│           │
│           ├── models/
│           │   └── User.java              # User entity with clearance
│           │
│           ├── security/
│           │   ├── SecurityLevel.java     # BLP clearance enum (0-3)
│           │   ├── BLPAccessControl.java  # Read/write access enforcement
│           │   ├── AuditLogger.java       # Database audit logging
│           │   ├── PasswordManager.java   # Password hashing & validation
│           │   ├── LoginLockout.java      # Account lockout mechanism
│           │   ├── RateLimiter.java       # Request rate limiting
│           │   ├── MFAManager.java        # Two-factor authentication
│           │   ├── InputValidator.java    # Business rule validation
│           │   └── InputSanitizer.java    # Injection defense
│           │
│           ├── session/
│           │   └── SessionManager.java    # In-memory session store
│           │
│           ├── database/
│           │   └── DatabaseConnection.java # MySQL connection
│           │
│           └── util/
│               ├── Result.java            # Rust-style error handling
│               ├── ValidationResult.java  # Validation error accumulation
│               ├── PasswordUtil.java      # SHA-256 hashing utility
│               └── EnvLoader.java         # .env file parser
│
└── database/
    └── schema.sql             # MySQL schema with test data
```

## Database Schema

**Database:** `delivery_system` (UTF8MB4 encoding)

### Users Table
```sql
CREATE TABLE users (
    user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,      -- SHA-256 hex (64 chars)
    salt VARCHAR(64) NOT NULL,                -- Base64 encoded (16 bytes)
    role ENUM('customer', 'driver', 'manager', 'admin') NOT NULL,
    clearance_level TINYINT UNSIGNED NOT NULL DEFAULT 0,  -- 0-3 (BLP)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_username (username),
    CONSTRAINT chk_clearance_level CHECK (clearance_level BETWEEN 0 AND 3)
) ENGINE=InnoDB;
```

**Clearance Levels:**
- `0` = Unclassified (customers)
- `1` = Confidential (drivers)
- `2` = Secret (managers)
- `3` = Top Secret (admins)

### Security Labels Table
```sql
CREATE TABLE security_labels (
    label_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL COMMENT 'Data type classification',
    object_id BIGINT UNSIGNED NOT NULL COMMENT 'Record ID',
    classification_level TINYINT UNSIGNED NOT NULL COMMENT '0=U, 1=C, 2=S, 3=TS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_object (object_type, object_id),
    UNIQUE KEY unique_object (object_type, object_id)
) ENGINE=InnoDB;
```

**Example Classifications:**
| Data Type | Classification Level |
|-----------|---------------------|
| PUBLIC_INFO, MARKETING | 0 (Unclassified) |
| PACKAGE_INFO, ROUTE_INFO, TRACKING | 1 (Confidential) |
| USER_PII, PAYMENT_INFO, FACILITY_INVENTORY | 2 (Secret) |
| OPTIMIZATION_ALGORITHM, SYSTEM_LOGS, AUDIT_LOGS | 3 (Top Secret) |

### Audit Log Table
```sql
CREATE TABLE audit_log (
    audit_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT UNSIGNED NULL,
    username VARCHAR(50) NULL,
    action VARCHAR(50) NOT NULL,              -- LOGIN, BLP_READ_DENIED, etc.
    result ENUM('success', 'denied', 'error') NOT NULL,
    ip_address VARCHAR(45) NULL,              -- IPv4/IPv6 support
    details TEXT NULL,                        -- Additional context

    INDEX idx_timestamp (timestamp),
    INDEX idx_username (username)
) ENGINE=InnoDB;
```

**Logged Actions:**
- `LOGIN` - Authentication attempts
- `BLP_READ_DENIED` / `BLP_WRITE_DENIED` - Access control violations
- `ACCOUNT_LOCKED` - Account lockout events
- `FAILED_LOGIN` - Failed authentication
- `MFA_CODE_GENERATED` / `MFA_VERIFIED` - Two-factor auth events
- `LOCKOUT_CHECK` - Account status checks

### MFA Codes Table ✅
**Status:** Fully implemented in `schema.sql`

The `mfa_codes` table is now included in the schema and supports:
- 6-digit time-limited codes
- 5-minute expiration window
- Single-use validation (prevents replay attacks)
- Automatic cascade deletion when user is removed

```sql
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
```

### Login Lockout Fields ✅
**Status:** Fully implemented in `schema.sql`

The `users` table now includes lockout tracking columns:
- `failed_attempts` - Counter for consecutive failed login attempts
- `lockout_until` - Timestamp when account lockout expires

```sql
-- Included in users table:
failed_attempts INT DEFAULT 0 COMMENT 'Failed login attempts counter for account lockout',
lockout_until TIMESTAMP NULL COMMENT 'Account locked until this time (NULL if not locked)',
INDEX idx_lockout (lockout_until)
```

**Lockout Behavior:**
- Account locks after 3 failed attempts
- Lockout duration: 30 minutes
- Automatic unlock when lockout_until expires
- Counter resets to 0 on successful login

## API Endpoints

### POST `/login`
**Purpose:** User authentication with BLP clearance verification

**Request:**
```json
{
    "username": "admin",
    "password": "admin123"
}
```

**Success Response (200):**
```json
{
    "username": "admin",
    "role": "admin",
    "clearanceLevel": 3,
    "token": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Headers Set:**
```
Set-Cookie: SESSION=<token>; Path=/; HttpOnly
```

**Error Responses:**
- `401 Unauthorized` - Invalid credentials or account locked
- `400 Bad Request` - Missing username/password
- `500 Internal Server Error` - Database/server error

**Security:**
- All attempts logged to `audit_log` with IP address
- Rate limited to 5 requests/minute per IP
- Account locked after 3 failed attempts (30 min duration)
- Prepared statements prevent SQL injection
- Consistent error messages prevent username enumeration

### GET `/whoami`
**Purpose:** Verify session and retrieve user information

**Authentication:** Session cookie or Bearer token required

**Request Headers:**
```
Cookie: SESSION=<token>
OR
Authorization: Bearer <token>
```

**Success Response (200):**
```json
{
    "username": "admin",
    "role": "admin",
    "clearance": "TOP_SECRET"
}
```

**Error Response (401):**
```json
{
    "error": "unauthorized"
}
```

## Error Handling: Result<T, E> Pattern

This project uses **Result<T, E>** for type-safe error handling instead of exceptions. This approach provides:
- **Security:** Forces explicit error handling, eliminates silent failures
- **Standardization:** Consistent error handling across all backend code
- **Performance:** No exception stack trace overhead
- **Clarity:** Errors are part of the method signature

### Using Result<T, E>

All backend methods that can fail return `Result<T, E>` where:
- `T` is the success type
- `E` is the error type (usually `String`)

**Example - Checking results:**
```java
Result<Connection, String> result = DatabaseConnection.getConnection();

if (result.isOk()) {
    Connection conn = result.unwrap();
    // Use connection
} else {
    String error = result.unwrapErr();
    // Handle error
    System.err.println("Database error: " + error);
}
```

**Example - Chaining operations:**
```java
Result<String, String> hashResult = PasswordUtil.hashPassword(password, salt);
if (hashResult.isErr()) {
    return Result.err("Password hashing failed: " + hashResult.unwrapErr());
}
String hash = hashResult.unwrap();
```

**Example - Transforming results:**
```java
Result<SecurityLevel, String> levelResult = SecurityLevel.fromInt(clearanceLevel);
return levelResult.map(level -> new User(id, username, role, level));
```

### Methods That Use Result

- `EnvLoader.get(String key)` → `Result<String, String>`
- `DatabaseConnection.getConnection()` → `Result<Connection, String>`
- `PasswordUtil.hashPassword(String, String)` → `Result<String, String>`
- `AuditLogger.log(...)` → `Result<Void, String>`
- `SessionManager.getSession(String token)` → `Result<Session, String>`
- `SecurityLevel.fromString(String)` → `Result<SecurityLevel, String>`
- `SecurityLevel.fromInt(int)` → `Result<SecurityLevel, String>`
- `LoginLockout.recordFailedAttempt(String)` → `Result<Integer, String>`
- `MFAManager.generateMFACode(Long, String)` → `Result<String, String>`

### Why Not Exceptions?

Exceptions in Java have security and reliability issues:
- Can be silently swallowed with empty catch blocks
- Don't appear in method signatures (unchecked exceptions)
- Performance overhead from stack trace generation
- Easy to forget to handle error cases
- Can leak sensitive information in stack traces

**Result forces you to handle errors explicitly at compile time.**

## Bell-LaPadula Access Control

The system implements the **Bell-LaPadula (BLP) security model** for mandatory access control.

### Security Levels

```java
public enum SecurityLevel {
    UNCLASSIFIED(0),    // Public information
    CONFIDENTIAL(1),    // Internal use only
    SECRET(2),          // Sensitive data
    TOP_SECRET(3)       // Highly classified
}
```

### Access Rules

Implemented in `BLPAccessControl.java`:

**No Read Up:**
```java
public static boolean checkReadAccess(SecurityLevel userLevel, SecurityLevel dataLevel) {
    return userLevel.ordinal() >= dataLevel.ordinal();
}
```
- Users can only **read** data at or **below** their clearance level
- Example: A Confidential user (level 1) cannot read Secret data (level 2)

**No Write Down:**
```java
public static boolean checkWriteAccess(SecurityLevel userLevel, SecurityLevel dataLevel) {
    return userLevel.ordinal() <= dataLevel.ordinal();
}
```
- Users can only **write** data at or **above** their clearance level
- Example: A Secret user (level 2) cannot write to Unclassified data (level 0)

### Implementation Example

```java
SecurityLevel userClearance = SecurityLevel.CONFIDENTIAL;  // Level 1
SecurityLevel dataClearance = SecurityLevel.SECRET;         // Level 2

// Check read access
if (BLPAccessControl.checkReadAccess(userClearance, dataClearance)) {
    // Allow read
} else {
    // Deny - user cannot read up
    AuditLogger.log(userId, username, "BLP_READ_DENIED", "DENIED",
                   "Attempted to read SECRET data with CONFIDENTIAL clearance");
}
```

### BLP Enforcement

- All users assigned a clearance level (0-3) upon account creation
- All data objects classified in `security_labels` table
- Access checks performed before any data operation
- All denied access attempts logged to `audit_log`
- Frontend dashboards will enforce BLP rules (in development)

## Setup Instructions

### Prerequisites

**Java Development Kit (JDK):**
- **Windows:**
  1. Download Java 21 LTS from: https://adoptium.net/
  2. Run installer, select "Add to PATH"
  3. Verify: Open CMD and run `java -version`

- **Ubuntu/Debian:**
  ```bash
  sudo apt update
  sudo apt install openjdk-21-jdk
  java -version
  ```

**MySQL Server:**
- **Windows:**
  1. Download MySQL Installer from: https://dev.mysql.com/downloads/installer/
  2. Choose "Developer Default" setup
  3. Set root password during installation (remember this!)
  4. MySQL runs as Windows service automatically

- **Ubuntu/Debian:**
  ```bash
  sudo apt update
  sudo apt install mysql-server
  sudo systemctl start mysql
  sudo mysql_secure_installation
  # Set root password when prompted
  ```

**Git:**
- **Windows:**
  1. Download from: https://git-scm.com/download/win
  2. Run installer with default options
  3. Use Git Bash or Command Prompt

- **Ubuntu/Debian:**
  ```bash
  sudo apt install git
  git --version
  ```

### First-Time Setup

**1. Clone the repository:**
```bash
# Both Windows and Linux
git clone <repository-url>
cd Optimized-Delivery-System
```

**2. Download MySQL Connector/J JAR file:**

This is the JDBC driver that lets Java talk to MySQL.

**Option A: Direct Download (Recommended)**
1. Go to: https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/
2. Download: `mysql-connector-j-8.4.0.jar` (2.5 MB) - **NOT** the javadoc JAR
3. Create `backend/lib/` folder if it doesn't exist
4. Place the JAR file in `backend/lib/`
5. Verify: File size should be ~2.5MB

**Option B: From MySQL Website**
1. Go to: https://dev.mysql.com/downloads/connector/j/
2. Select "Platform Independent"
3. Download ZIP/TAR archive
4. Extract and find `mysql-connector-j-8.4.0.jar` inside
5. Copy to `backend/lib/`

**IMPORTANT:** If you installed via `.deb` on Ubuntu, the connector is system-wide but you still need the JAR file in your project for development:
```bash
# Find where it's installed
dpkg -L mysql-connector-j | grep jar

# Copy to your project
cp /usr/share/java/mysql-connector-j-8.4.0.jar backend/lib/
```

**3. Set up environment variables:**

```bash
# Copy the example file
cp .env.example .env

# Edit with your preferred editor
# Windows: notepad .env
# Linux: nano .env
```

**Your `.env` file should contain:**
```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=delivery_system
DB_USER=root
DB_PASSWORD=your_actual_mysql_password
SERVER_PORT=8081
SESSION_TIMEOUT_SECONDS=3600
```

**Note:** Audit logging is stored in the MySQL `audit_log` table, not in files.

**CRITICAL: Never commit your .env file to Git!**

**4. Setup Database:**

**Windows (Command Prompt or PowerShell):**
```cmd
mysql -u root -p
```

**Ubuntu/Linux:**
```bash
sudo mysql -u root -p
# OR if you set up a password during installation:
mysql -u root -p
```

**Then in MySQL prompt (both OS):**
```sql
source database/schema.sql;
-- OR if source doesn't work:
-- Copy/paste the contents of schema.sql

-- Verify
USE delivery_system;
SHOW TABLES;
-- Should see: users, security_labels, audit_log

SELECT username, role, clearance_level FROM users;
-- Should see 4 test users

exit;
```

## Running the Application

### Compile and Run Backend

**Windows (Command Prompt):**
```cmd
cd backend\src
javac -cp ".;..\lib\mysql-connector-j-8.4.0.jar" com\delivery\**\*.java
java -cp ".;..\lib\mysql-connector-j-8.4.0.jar" com.delivery.Main
```

**Ubuntu/Linux (Terminal):**
```bash
cd backend/src
javac -cp ".:../lib/mysql-connector-j-8.4.0.jar" com/delivery/**/*.java
java -cp ".:../lib/mysql-connector-j-8.4.0.jar" com.delivery.Main
```

**Expected Output:**
```
Server started on http://localhost:8081
```

> **Note:** Port is 8081 by default (configurable in `.env`)

### Open Frontend

1. Navigate to `frontend/login.html`
2. Right-click and select "Open with" your browser (Chrome, Firefox, etc.)
3. Or drag the file into your browser window

**Login with test credentials:**
- Username: `admin`
- Password: `admin123`

## PowerShell Commands (Windows)

If you're using **PowerShell** instead of Command Prompt on Windows, use these commands:

**Compile Java files:**
```powershell
cd backend\src
javac -cp ".;..\lib\mysql-connector-j-8.4.0.jar" (Get-ChildItem -Path com\delivery -Recurse -Filter *.java | ForEach-Object { $_.FullName })
```

**Run the server:**
```powershell
java -cp ".;..\lib\mysql-connector-j-8.4.0.jar" com.delivery.Main
```

**Quick restart (one-liner):**
```powershell
cd backend\src; javac -cp ".;..\lib\mysql-connector-j-8.4.0.jar" (Get-ChildItem -Path com\delivery -Recurse -Filter *.java | ForEach-Object { $_.FullName }); java -cp ".;..\lib\mysql-connector-j-8.4.0.jar" com.delivery.Main
```

## Test Credentials

Test users are pre-inserted with different clearance levels:

| Username | Password | Role | Clearance Level | Access |
|----------|----------|------|----------------|--------|
| `customer1` | `cust123` | customer | 0 (Unclassified) | Public data only |
| `driver1` | `driver123` | driver | 1 (Confidential) | Routes, packages |
| `manager1` | `mgr123` | manager | 2 (Secret) | PII, payments, inventory |
| `admin` | `admin123` | admin | 3 (Top Secret) | Full system access |

**Password Hashing:** SHA-256(password + salt) - see `schema.sql` for pre-computed hashes

## Development Notes

### Helpful Commands

**Check if MySQL is running:**

**Windows:**
```cmd
sc query MySQL80
```

**Ubuntu/Linux:**
```bash
sudo systemctl status mysql
```

**Start/Stop MySQL:**

**Windows:**
```cmd
net start MySQL80
net stop MySQL80
```

**Ubuntu/Linux:**
```bash
sudo systemctl start mysql
sudo systemctl stop mysql
sudo systemctl restart mysql
```

**Check if Java is installed:**
```bash
# Both Windows and Linux
java -version
javac -version
```

**Check if port 8081 is in use:**

**Windows:**
```cmd
netstat -ano | findstr :8081
```

**Ubuntu/Linux:**
```bash
sudo lsof -i :8081
```

**Kill process on port 8081:**

**Windows:**
```cmd
# Get PID from netstat command above, then:
taskkill /PID <PID> /F
```

**Ubuntu/Linux:**
```bash
sudo kill -9 $(sudo lsof -ti:8081)
```

**Quick backend restart:**

**Windows:**
```cmd
cd backend\src && javac -cp ".;..\lib\mysql-connector-j-8.4.0.jar" com\delivery\**\*.java && java -cp ".;..\lib\mysql-connector-j-8.4.0.jar" com.delivery.Main
```

**Ubuntu/Linux:**
```bash
cd backend/src && javac -cp ".:../lib/mysql-connector-j-8.4.0.jar" com/delivery/**/*.java && java -cp ".:../lib/mysql-connector-j-8.4.0.jar" com.delivery.Main
```

### Localhost Configuration

- Backend runs on `http://localhost:8081` (configurable via `.env`)
- Frontend opens directly in browser (`file://` protocol)
- No CORS issues since everything is local
- MySQL runs on default `localhost:3306`
- No HTTPS needed for localhost testing
- Session management uses in-memory storage (resets on server restart)
- Sessions automatically expire after 1 hour (configurable via `SESSION_TIMEOUT_SECONDS`)

### Environment Variables (.env)

**What is a .env file?**
A `.env` file stores sensitive configuration (like database passwords) that should NOT be committed to Git. Each team member has their own `.env` file with their own local credentials.

**Files you'll have:**
- `.env.example` - Template showing what variables are needed (COMMIT THIS)
- `.env` - Your actual credentials (NEVER COMMIT THIS)
- `.gitignore` - Contains `.env` to prevent accidental commits

**How to use:**
1. Copy `.env.example` to `.env`
2. Fill in your own MySQL password in `.env`
3. Code reads from `.env` using `EnvLoader.java`
4. Never push `.env` to GitHub

**Variable Reference:**
| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_HOST` | MySQL server hostname | `localhost` |
| `DB_PORT` | MySQL server port | `3306` |
| `DB_NAME` | Database name | `delivery_system` |
| `DB_USER` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | *(your password)* |
| `SERVER_PORT` | HTTP server port | `8081` |
| `SESSION_TIMEOUT_SECONDS` | Session expiry time | `3600` (1 hour) |

## Git Workflow

---
## ⚠️ CRITICAL: ALWAYS PULL BEFORE MAKING CHANGES ⚠️

**PULL FIRST, CODE SECOND!** Before you start working, before you create a branch, before you change a single file:

```bash
git pull origin main
```

**Why this matters:**
- Prevents divergent branches that cause merge conflicts
- Ensures you're working with the latest code
- Avoids overwriting teammate's work
- Saves hours of merge conflict resolution

**If you forget to pull first and get divergent branches:**
1. Commit or stash your current changes
2. Pull with rebase: `git pull --rebase origin main`
3. Resolve any conflicts
4. Continue working

---

**IMPORTANT: Never push directly to main! Always use branches and test your code first.**

```bash
# STEP 1: ALWAYS PULL FIRST (most important step!)
git pull origin main

# Create feature branch for your task
git checkout -b feature/login-page
# or
git checkout -b feature/blp-dashboard
# or
git checkout -b fix/authentication-bug

# Check what files you've changed
git status

# Add your changes
git add .

# Commit with descriptive message
git commit -m "Implement BLP access control for package dashboard"

# BEFORE PUSHING: Test that your code works!
# - Does it compile without errors?
# - Did you test the functionality?
# - Did you break anything that was working?

# Push your branch (NOT main)
git push origin feature/login-page

# Create Pull Request on GitHub for team review
# Only merge to main after someone reviews it

# After merge, switch back and update
git checkout main
git pull origin main

# Delete old feature branch
git branch -d feature/login-page
```

**Branch Naming Convention:**
- `feature/description` - for new features
- `fix/description` - for bug fixes
- `docs/description` - for documentation updates

**Rules:**
- **⚠️ ALWAYS PULL FIRST:** Run `git pull origin main` before starting ANY work (prevents divergent branches!)
- **DON'T:** `git push origin main` unless you're 100% sure
- **DON'T:** Push code that doesn't compile
- **DON'T:** Push code you haven't tested
- **DON'T:** Ever commit your `.env` file (it has your passwords!)
- **DON'T:** Start coding without pulling latest changes first
- **DO:** Pull before creating a new branch
- **DO:** Pull before committing changes
- **DO:** Create a branch for each task
- **DO:** Test locally before pushing
- **DO:** Write clear commit messages
- **DO:** Keep `.env` in `.gitignore`

## Troubleshooting

**MySQL Connection Failed:**

**Windows:**
```cmd
# Check if MySQL service is running
sc query MySQL80

# Try connecting manually
mysql -u root -p
```

**Ubuntu/Linux:**
```bash
# Check MySQL status
sudo systemctl status mysql

# Try connecting
mysql -u root -p

# Check if MySQL is listening on port 3306
sudo netstat -tlnp | grep 3306
```

**Java ClassNotFoundException for MySQL Driver:**
- Verify `mysql-connector-j-8.4.0.jar` exists in `backend/lib/`
- Check your classpath in compile/run commands
- Make sure you're using semicolon (`;`) on Windows and colon (`:`) on Linux in classpath

**Port 8081 Already in Use:**
- Use kill commands above to stop the process
- Or change `SERVER_PORT` in `.env` to a different port (like 8082)

**"javac not found" or "java not found":**
- Java is not installed or not in PATH
- Windows: Reinstall Java and check "Add to PATH"
- Linux: Run `sudo apt install openjdk-21-jdk`

**MySQL Access Denied:**
- Check username/password in `.env` file
- Try connecting manually: `mysql -u root -p`
- You may need to create a new MySQL user or reset root password

**Frontend can't connect to backend:**
- Make sure backend is running (check terminal for "Server started" message)
- Check that `auth.js` is pointing to correct URL
- Default URL: `http://localhost:8081/login`
- Open browser console (F12) to see any errors

**Compilation errors about missing classes:**
- Ensure all `.java` files are in correct package directories
- Check that classpath includes MySQL connector JAR
- Try cleaning: delete all `.class` files and recompile

**Session expires too quickly:**
- Increase `SESSION_TIMEOUT_SECONDS` in `.env`
- Default is 3600 seconds (1 hour)
- Remember to restart the server after changing `.env`

## Milestone 1 Checklist: Login with BLP Security

### Database Setup
- [x] Create MySQL database `delivery_system`
- [x] Create `.env` file from `.env.example` with your MySQL credentials
- [x] Add `.env` to `.gitignore` (already configured)
- [x] Create `users` table with BLP security fields:
  - `user_id`, `username`, `password_hash`, `salt`, `role`, `clearance_level`, `created_at`
  - Role options: 'customer', 'driver', 'manager', 'admin'
  - Clearance levels: 0 (Unclassified), 1 (Confidential), 2 (Secret), 3 (Top Secret)
- [x] Insert test users with different clearance levels
- [x] Create `security_labels` table for BLP object classification
- [x] Create `audit_log` table for tracking access attempts
- [x] Add `failed_attempts` and `lockout_until` columns to `users` table
- [x] Create `mfa_codes` table for two-factor authentication

### Backend - Security Layer
- [x] Create `SecurityLevel.java` enum (UNCLASSIFIED, CONFIDENTIAL, SECRET, TOP_SECRET)
- [x] Create `BLPAccessControl.java` class:
  - [x] Implement `checkReadAccess()` - enforce "no read up"
  - [x] Implement `checkWriteAccess()` - enforce "no write down"
- [x] Create `AuditLogger.java` - comprehensive audit logging with IP tracking
- [x] Create `PasswordManager.java` - password hashing and strength validation
- [x] Create `LoginLockout.java` - account lockout after failed attempts
- [x] Create `RateLimiter.java` - brute force protection
- [x] Create `MFAManager.java` - two-factor authentication
- [x] Create `InputValidator.java` - business rule validation
- [x] Create `InputSanitizer.java` - injection defense

### Backend - Core Functionality
- [x] Create `EnvLoader.java` - read `.env` file and load variables
- [x] Create `DatabaseConnection.java` - use environment variables for connection
- [x] Create `User.java` model with clearance level field
- [x] Create `PasswordUtil.java` - SHA-256 hashing with salt
- [x] Create `AuthenticationController.java`:
  - [x] Validate credentials against hashed passwords
  - [x] Return user role AND clearance level on successful login
  - [x] Log all authentication attempts with IP address
- [x] Set up HTTP server with `/login` POST endpoint
- [x] Implement `SessionManager.java` with clearance level tracking
- [x] Create `Result.java` for type-safe error handling
- [x] Add `/whoami` endpoint for session verification

### Frontend Development
- [x] Create `login.html` with username/password form and error display
- [x] Style with `styles.css` (modern responsive design)
- [x] Create `auth.js`:
  - [x] Handle form submission and client-side validation
  - [x] Send POST request to `/login` endpoint
  - [x] Store user clearance level in sessionStorage
  - [x] Redirect based on role (customer/driver/manager/admin dashboards)
- [ ] Create role-based dashboard pages with BLP enforcement *(IN DEVELOPMENT)*

### Integration & BLP Testing
- [x] Connect frontend to backend `/login` endpoint
- [x] Implement comprehensive audit logging
- [x] Test login with users at different clearance levels
- [ ] Verify BLP access control prevents unauthorized access on dashboards *(dashboards in development)*
- [x] Test with invalid credentials and SQL injection attempts
- [x] Verify rate limiting and account lockout mechanisms
- [x] Integrate LoginLockout into authentication flow
- [x] Test account lockout after 3 failed attempts
- [x] Verify lockout duration (30 minutes)
- [x] Test session expiry and token validation
- [x] Verify audit logging captures user_id and IP address for all events

---

## Project Status

### ✅ Fully Implemented & Tested

**Authentication & Security:**
- SHA-256 password hashing with unique salt per user
- Session management with automatic expiry (1 hour default)
- Bell-LaPadula access control implementation (4 clearance levels)
- Result<T,E> pattern for type-safe error handling
- Login endpoint with credential validation

**Account Protection:**
- Account lockout after 3 failed login attempts (30-minute duration)
- Failed attempt counter tracking in database
- Automatic lockout expiration
- LoginLockout fully integrated into authentication flow

**Audit Logging:**
- Comprehensive audit trail for all security events
- User ID and IP address tracking for every action
- Standardized lowercase enum values (success/denied/error)
- Login attempts, lockout events, BLP violations all logged
- Database-backed persistent audit log

**Database:**
- Complete schema with users, audit_log, security_labels, mfa_codes tables
- Lockout columns (failed_attempts, lockout_until) operational
- Indexes optimized for query performance
- Foreign key constraints and data integrity

**Input Security:**
- SQL injection prevention via prepared statements
- XSS protection through HTML encoding and sanitization
- Input validation for emails, phones, usernames, tracking IDs
- Password strength validation (8+ chars, uppercase, lowercase, digit, special)

**Rate Limiting:**
- Token bucket algorithm with 60-second windows
- Login endpoint: 5 requests/minute
- General endpoints: 60 requests/minute
- Temporary ban capability

**MFA Support:**
- MFA code generation (6-digit, 5-minute expiry)
- Single-use code validation (prevents replay attacks)
- Database table with automatic cleanup
- Framework ready for email/SMS integration

### 🚧 In Development

**Frontend Dashboards:**
- Customer dashboard (UI in development)
- Driver dashboard (UI in development)
- Manager dashboard (UI in development)
- Admin dashboard (UI in development)
- BLP-enforced data access controls for dashboards

**Testing:**
- Comprehensive end-to-end testing of dashboard BLP enforcement
- Load testing for concurrent user sessions
- Security penetration testing
