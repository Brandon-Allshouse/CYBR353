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
│   │   └── mysql-connector.jar
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
- [ ] Create `SecurityLevel.java` enum (UNCLASSIFIED, CONFIDENTIAL, SECRET, TOP_SECRET)
- [ ] Create `BLPAccessControl.java` class:
  - Implement `checkReadAccess()` - enforce "no read up" (user clearance >= object classification)
  - Implement `checkWriteAccess()` - enforce "no write down" (user clearance <= object classification)
- [ ] Create `AuditLogger.java` - log all access attempts with timestamp, user, action, result

### Backend - Core Functionality
- [ ] Create `EnvLoader.java` - read `.env` file and load variables
- [ ] Create `DatabaseConnection.java` - use environment variables for connection:
  ```java
  String url = "jdbc:mysql://" + EnvLoader.get("DB_HOST") + ":" + 
               EnvLoader.get("DB_PORT") + "/" + EnvLoader.get("DB_NAME");
  String user = EnvLoader.get("DB_USER");
  String password = EnvLoader.get("DB_PASSWORD");
  ```
- [ ] Create `User.java` model with clearance level field
- [ ] Create `PasswordUtil.java` - implement salt generation and SHA-256 hashing
- [ ] Create `AuthenticationController.java`:
  - Validate credentials against hashed passwords
  - Return user role AND clearance level on successful login
  - Implement failed attempt counter (3 attempts = account lock)
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
- [ ] Test account lockout after 3 failed attempts

## Setup Instructions

### Prerequisites
- MySQL Server 8.0+ (recommend MySQL 8.4 LTS or latest)
- Java JDK 17+ (recommend Java 21 LTS)
- Git 2.40+

### First-Time Setup

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd delivery-system
   ```

2. **Set up environment variables (IMPORTANT!):**
   ```bash
   # Copy the example env file
   cp .env.example .env
   
   # Edit .env with your local MySQL credentials
   # Use any text editor (notepad, nano, vim, VS Code, etc.)
   nano .env
   ```
   
   **Your `.env` file should look like this:**
   ```
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=delivery_system
   DB_USER=root
   DB_PASSWORD=your_mysql_password_here
   SERVER_PORT=8080
   ```
   
   **⚠️ CRITICAL: Never commit your .env file to Git!**
   - The `.gitignore` file already blocks it
   - Each team member uses their own local `.env` with their own MySQL password

3. **Download MySQL Connector/J:**
   - Download latest version (8.4+) from: https://dev.mysql.com/downloads/connector/j/
   - Place `mysql-connector-java-x.x.xx.jar` in the `backend/lib/` folder

4. **Setup Database:**
   ```bash
   # Login to MySQL
   mysql -u root -p
   
   # Create database and tables
   source database/schema.sql
   
   # Verify tables were created
   USE delivery_system;
   SHOW TABLES;
   
   # Exit MySQL
   exit;
   ```

5. **Compile and Run Backend:**
   ```bash
   # Navigate to backend source
   cd backend/src
   
   # Compile all Java files (Windows)
   javac -cp ".;../lib/mysql-connector.jar" com/delivery/**/*.java
   
   # Compile all Java files (Mac/Linux)
   javac -cp ".:../lib/mysql-connector.jar" com/delivery/**/*.java
   
   # Run the server (Windows)
   java -cp ".;../lib/mysql-connector.jar" com.delivery.Main
   
   # Run the server (Mac/Linux)
   java -cp ".:../lib/mysql-connector.jar" com.delivery.Main
   ```
   Server should start on `http://localhost:8080`

6. **Open Frontend:**
   - Simply open `frontend/login.html` in your browser
   - Or right-click the file and "Open with Chrome/Firefox"

### Helpful Commands for Development

```bash
# Check if MySQL is running
# Windows:
net start | find "MySQL"

# Mac:
brew services list | grep mysql

# Linux:
systemctl status mysql

# Check if Java is installed
java -version
javac -version

# Check if port 8080 is in use
# Windows:
netstat -ano | findstr :8080

# Mac/Linux:
lsof -i :8080

# Kill process on port 8080 if needed
# Windows: (use PID from netstat command)
taskkill /PID <PID> /F

# Mac/Linux:
kill -9 $(lsof -ti:8080)

# Quick restart backend (from project root)
cd backend/src && javac -cp ".:../lib/mysql-connector.jar" com/delivery/**/*.java && java -cp ".:../lib/mysql-connector.jar" com.delivery.Main
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
```bash
# Verify MySQL is running and credentials are correct
mysql -u root -p -e "SELECT 1;"
```

**Port 8080 Already in Use:**
- Kill the process using commands above, or
- Change port in `Main.java` and `auth.js`

**ClassNotFoundException for MySQL Driver:**
- Verify `mysql-connector.jar` is in `backend/lib/`
- Check classpath in compile/run commands

**CORS Errors:**
- Open HTML file directly in browser (not through file explorer preview)
- Or use Live Server extension in VS Code

## Test Credentials (by Clearance Level)

- **Unclassified Customer:** `customer1` / `cust123` (Clearance: 0)
- **Confidential Driver:** `driver1` / `driver123` (Clearance: 1)
- **Secret Manager:** `manager1` / `mgr123` (Clearance: 2)
- **Top Secret Admin:** `admin` / `admin123` (Clearance: 3)

## BLP Security Implementation Notes

- All users assigned clearance level (0-3) upon account creation
- Read access: Users can only read data at or below their clearance level
- Write access: Users can only write to data at or above their clearance level
- All access attempts logged to `audit_log` table
- Failed BLP checks return "Access Denied" with reason logged

## Localhost Development Notes

- Backend runs on `http://localhost:8080` (or port specified in `.env`)
- Frontend can be opened directly in browser (uses `file://` protocol)
- No CORS issues since everything is local
- MySQL runs on default `localhost:3306`
- No HTTPS needed for localhost testing
- Session management uses in-memory storage (resets on server restart)

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
