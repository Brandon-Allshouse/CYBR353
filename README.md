# Optimized Delivery System

**Team Members:** Brody Scott, Dawson Pfabe, Brandon Allshouse, Tyler Slack  
**Course:** CYBR 353

## Tech Stack

- **Frontend:** HTML, CSS, JavaScript
- **Backend:** Java (using built-in HttpServer - no Spring Boot)
- **Database:** MySQL (with BLP security model)
- **Deployment:** Localhost only

## Project Structure

```
delivery-system/
├── .env.example          # Template for environment variables
├── .env                  # Your local config (DO NOT COMMIT)
├── .gitignore            # Includes .env
├── frontend/
│   ├── login.html
│   ├── css/
│   │   └── styles.css
│   └── js/
│       └── auth.js
├── backend/
│   ├── lib/
│   │   └── mysql-connector-j-8.4.0.jar
│   └── src/
│       └── com/delivery/
│           ├── Main.java
│           ├── controllers/
│           ├── models/
│           ├── security/
│           └── database/
│               └── DatabaseConnection.java
└── database/
    └── schema.sql
```

## Milestone 1 Checklist: Login with BLP Security

### Database Setup
- [ ] Create MySQL database `delivery_system`
- [ ] Create `.env` file from `.env.example` with your MySQL credentials
- [ ] Add `.env` to `.gitignore` (should already be there)
- [ ] Create `users` table with BLP security fields:
  - `user_id`, `username`, `password_hash`, `salt`, `role`, `clearance_level`, `created_at`
  - Role options: 'customer', 'driver', 'manager', 'admin'
  - Clearance levels: 0 (Unclassified), 1 (Confidential), 2 (Secret), 3 (Top Secret)
- [ ] Insert test users with different clearance levels
- [ ] Create `security_labels` table for BLP object classification
- [ ] Create `audit_log` table for tracking access attempts

### Backend - Security Layer
- [x] Create `SecurityLevel.java` enum (UNCLASSIFIED, CONFIDENTIAL, SECRET, TOP_SECRET)
- [x] Create `BLPAccessControl.java` class:
  - Implement `checkReadAccess()` - enforce "no read up" (user clearance >= object classification)
  - Implement `checkWriteAccess()` - enforce "no write down" (user clearance <= object classification)
- [x] Create `AuditLogger.java` - log all access attempts with timestamp, user, action, result

### Backend - Core Functionality
- [x] Create `EnvLoader.java` - read `.env` file and load variables
- [x] Create `DatabaseConnection.java` - use environment variables for connection:
  ```java
  String url = "jdbc:mysql://" + EnvLoader.get("DB_HOST") + ":" + 
               EnvLoader.get("DB_PORT") + "/" + EnvLoader.get("DB_NAME");
  String user = EnvLoader.get("DB_USER");
  String password = EnvLoader.get("DB_PASSWORD");
  ```
- [x] Create `User.java` model with clearance level field
- [ ] Create `PasswordUtil.java` - implement salt generation and SHA-256 hashing
- [ ] Create `AuthenticationController.java`:
  - Validate credentials against hashed passwords
  - Return user role AND clearance level on successful login
  - Log all authentication attempts
- [ ] Set up HTTP server with `/login` POST endpoint
- [ ] Implement session management with clearance level tracking

### Frontend Development
- [ ] Create `login.html` with username/password form and error display
- [ ] Style with `styles.css` (centered form, professional design)
- [ ] Create `auth.js`:
  - Handle form submission and client-side validation
  - Send POST request to `/login` endpoint
  - Store user clearance level in session on success
  - Redirect based on role (customer/driver/manager/admin dashboards)

### Integration & BLP Testing
- [ ] Connect frontend to backend `/login` endpoint
- [ ] Test login with users at different clearance levels
- [ ] Verify BLP access control prevents unauthorized access
- [ ] Test with invalid credentials and SQL injection attempts
- [ ] Verify audit logs capture all access attempts

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
cd delivery-system
```

**2. Download MySQL Connector/J JAR file:**

This is the JDBC driver that lets Java talk to MySQL.

**Option A: Direct Download (Recommended)**
1. Go to: https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/
2. Download: `mysql-connector-j-8.4.0.jar`
3. Create `backend/lib/` folder if it doesn't exist
4. Place the JAR file in `backend/lib/`

**Option B: From MySQL Website**
1. Go to: https://dev.mysql.com/downloads/connector/j/
2. Select "Platform Independent" 
3. Download ZIP/TAR archive
4. Extract and find `mysql-connector-j-8.4.0.jar` inside
5. Copy to `backend/lib/`

**IMPORTANT:** If you installed via `.deb` on Ubuntu, the connector is system-wide but you still need the JAR file in your project for development. Copy it from:
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
```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=delivery_system
DB_USER=root
DB_PASSWORD=your_actual_mysql_password
SERVER_PORT=8080
SESSION_TIMEOUT=30
AUDIT_LOGGING_ENABLED=true
LOG_FILE_PATH=logs/audit.log
```

**CRITICAL: Never commit your .env file to Git!**

**4. Setup Database:**

**Windows (Command Prompt or PowerShell):**
```cmd
mysql -u root -p
```

**Ubuntu/Linux:**
```bash
sudo mysql -u root -p
```

**Then in MySQL prompt (both OS):**
```sql
source database/schema.sql;
-- OR if source doesn't work:
-- Copy/paste the contents of schema.sql

-- Verify
USE delivery_system;
SHOW TABLES;
exit;
```

**5. Compile and Run Backend:**

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

You should see: `Server started on http://localhost:8080`

**6. Open Frontend:**
- Navigate to `frontend/login.html`
- Right-click and select "Open with" your browser (Chrome, Firefox, etc.)
- Or drag the file into your browser window

### Helpful Development Commands

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

**Check if port 8080 is in use:**

**Windows:**
```cmd
netstat -ano | findstr :8080
```

**Ubuntu/Linux:**
```bash
sudo lsof -i :8080
```

**Kill process on port 8080:**

**Windows:**
```cmd
# Get PID from netstat command above, then:
taskkill /PID <PID> /F
```

**Ubuntu/Linux:**
```bash
sudo kill -9 $(sudo lsof -ti:8080)
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

### Git Workflow

**IMPORTANT: Never push directly to main! Always use branches and test your code first.**

```bash
# Pull latest changes before starting work
git pull origin main

# Create feature branch for your task
git checkout -b feature/login-page
# or
git checkout -b feature/database-connection
# or
git checkout -b feature/authentication-controller

# Check what files you've changed
git status

# Add your changes
git add .

# Commit with descriptive message
git commit -m "Implement login authentication with BLP checks"

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
- DON'T: `git push origin main` unless you're 100% sure
- DON'T: Push code that doesn't compile
- DON'T: Push code you haven't tested
- DON'T: Ever commit your `.env` file (it has your passwords!)
- DO: Create a branch for each task
- DO: Test locally before pushing
- DO: Write clear commit messages
- DO: Pull from main frequently to stay updated
- DO: Keep `.env` in `.gitignore`

### Troubleshooting

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

**Port 8080 Already in Use:**
- Use kill commands above to stop the process
- Or change `SERVER_PORT` in `.env` to a different port (like 8081)

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
- Check that `auth.js` is pointing to correct URL: `http://localhost:8080/login`
- Open browser console (F12) to see any errors

## Test Credentials (by Clearance Level)

- **Unclassified Customer:** `customer1` / `cust123` (Clearance: 0)
- **Confidential Driver:** `driver1` / `driver123` (Clearance: 1)
- **Secret Manager:** `manager1` / `mgr123` (Clearance: 2)
- **Top Secret Admin:** `admin` / `admin123` (Clearance: 3)

## Localhost Development Notes

- Backend runs on `http://localhost:8080` (or port specified in `.env`)
- Frontend can be opened directly in browser (uses `file://` protocol)
- No CORS issues since everything is local
- MySQL runs on default `localhost:3306`
- No HTTPS needed for localhost testing
- Session management uses in-memory storage (resets on server restart)
- Sessions automatically expire after 30 minutes of inactivity (configurable via SESSION_TIMEOUT in `.env`)

## Environment Variables (.env)

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

## BLP Security Implementation Notes

- All users assigned clearance level (0-3) upon account creation
- Read access: Users can only read data at or below their clearance level
- Write access: Users can only write to data at or above their clearance level
- All access attempts logged to `audit_log` table
- Failed BLP checks return "Access Denied" with reason logged
