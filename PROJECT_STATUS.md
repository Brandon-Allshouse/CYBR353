# Optimized Delivery System - Project Status

**Last Updated:** 2025-11-19 (Inventory frontend completed)
**Course:** CYBR 353 (Cybersecurity)
**Team:** Brody Scott, Dawson Pfabe, Brandon Allshouse, Tyler Slack
**Current Phase:** Core Feature Development (Inventory System 100% Complete - Backend + Frontend)

---

## 🎯 PROJECT OVERVIEW

A secure package delivery management system with Bell-LaPadula (BLP) mandatory access control, demonstrating defense-in-depth security architecture for a cybersecurity course.

**Key Security Features:**
- Bell-LaPadula mandatory access control (4 clearance levels)
- Comprehensive audit logging
- SHA-256 password hashing with unique salts
- Account lockout (3 failed attempts, 30-minute lockout)
- Rate limiting (token bucket algorithm)
- SQL injection prevention (prepared statements)
- XSS protection (input sanitization)
- Session management with HttpOnly cookies
- 2FA framework (MFA code generation ready)

---

## 📊 COMPLETION STATUS

### ✅ FULLY IMPLEMENTED (70% Security, 30% Business Logic)

**Latest Completion (2025-11-19):** Inventory Management Frontend - fully integrated with backend APIs

**Authentication & Security (100%)**
- ✅ User login with reCAPTCHA
- ✅ Customer registration
- ✅ Session management (UUID tokens, 1-hour timeout)
- ✅ Password hashing (SHA-256 + salt)
- ✅ Account lockout system
- ✅ Rate limiting (5 login attempts/min, 60 general/min)
- ✅ Bell-LaPadula access control
- ✅ Comprehensive audit logging
- ✅ MFA code generation framework (email integration pending)

**Inventory Management (100% ✅ COMPLETE - BOTH BACKEND + FRONTEND)**
- ✅ View all inventory across facilities (backend + frontend)
- ✅ View facility-specific inventory (backend + frontend)
- ✅ Search by tracking number (backend + frontend)
- ✅ List all facilities (backend + frontend)
- ✅ Complex SQL joins (6 tables: inventory, packages, orders, facilities, addresses, users)
- ✅ BLP enforcement (SECRET clearance required for managers)
- ✅ Full backend API (DAO → Service → Controller)
- ✅ Frontend UI with real-time backend integration
- ✅ Statistics dashboard (total packages, facilities, in-stock count)
- ✅ Interactive package details modal
- ✅ Professional styling matching existing design system
- ✅ Session management and error handling
- ✅ XSS protection and input validation
- **FILE:** `frontend/management/view-inventory.html` (fully functional)
- **TEST:** Login as manager1/mgr123 → http://localhost:8081/management/view-inventory.html

**Admin Features (80%)**
- ✅ View audit logs
- ✅ List all users
- ✅ Update user roles
- ✅ Update account status
- ❌ Real-time monitoring dashboard
- ❌ Bulk export detection

---

### ⚠️ PARTIALLY IMPLEMENTED (UI exists, backend missing)

**Customer Features (10%)**
- ✅ Registration page (functional)
- ✅ Login page (functional)
- ✅ Customer dashboard UI (exists but no data)
- ❌ Order placement endpoint
- ❌ Package tracking endpoint
- ❌ Order history display
- ❌ Package editing
- ❌ Return requests

**Driver Features (5%)**
- ✅ Driver dashboard UI (exists)
- ✅ Route view UI (exists)
- ❌ View assigned route endpoint
- ❌ Update delivery status endpoint
- ❌ Delivery confirmation

**Manager Features (30%)**
- ✅ Management dashboard UI
- ✅ Inventory viewing (COMPLETE)
- ❌ Route assignment algorithm
- ❌ Facility transfer system
- ❌ Inventory reports/exports

---

### ❌ NOT IMPLEMENTED (0%)

**Core Missing Features:**
1. **Order Placement System** (Use Case 2)
   - Customer creates delivery order
   - Payment processing
   - Tracking ID generation
   - Add to facility inventory

2. **Package Tracking** (Use Case 3)
   - Query package by tracking number
   - Delivery history timeline
   - Customer notifications

3. **Route Optimization** (Use Case 4)
   - Algorithm to optimize delivery routes
   - Driver assignment logic
   - Time estimates

4. **Driver Operations** (Use Case 5)
   - Update delivery status
   - Mark delivered/attempted
   - Customer notifications

5. **Facility Transfers** (Use Case 6)
   - Transfer packages between facilities
   - Update inventory automatically

6. **Returns Processing** (Use Case 9)
   - Return requests
   - Return label generation
   - Reverse logistics

---

## 🏗️ ARCHITECTURE

### Technology Stack

**Backend:**
- Java 21 LTS
- Built-in HttpServer (no Spring Boot)
- MySQL 8.0+ with InnoDB
- JDBC (mysql-connector-j-8.4.0.jar)

**Frontend:**
- Vanilla JavaScript (no frameworks)
- HTML5, CSS3
- SPA routing (custom router.js)
- Google reCAPTCHA v2

**Security:**
- Bell-LaPadula mandatory access control
- SHA-256 + salt password hashing
- Session-based authentication
- Rate limiting (token bucket)
- Audit logging to MySQL

### Directory Structure

```
Optimized-Delivery-System/
├── backend/
│   ├── lib/
│   │   └── mysql-connector-j-8.4.0.jar
│   └── src/com/delivery/
│       ├── Main.java                  # HTTP server + route registration
│       ├── controllers/               # HTTP endpoints
│       │   ├── AuthenticationController.java  ✅ COMPLETE
│       │   ├── CustomerController.java        ✅ COMPLETE
│       │   ├── AdminController.java           ✅ COMPLETE
│       │   ├── InventoryController.java       ✅ COMPLETE
│       │   ├── DriverController.java          ❌ STUB
│       │   ├── RouteController.java           ❌ STUB
│       │   ├── ManagementController.java      ❌ STUB
│       │   ├── OrdersController.java          ❌ STUB
│       │   ├── PackageController.java         ❌ STUB
│       │   └── PaymentController.java         ❌ STUB
│       ├── models/
│       │   ├── User.java                      ✅ COMPLETE
│       │   ├── InventoryItem.java             ✅ COMPLETE
│       │   ├── Facility.java                  ⚠️  STUB
│       │   └── PackageItem.java               ⚠️  STUB
│       ├── security/
│       │   └── SecurityManager.java           ✅ COMPLETE (11 nested classes)
│       ├── session/
│       │   └── SessionManager.java            ✅ COMPLETE
│       ├── database/
│       │   └── DatabaseConnection.java        ✅ COMPLETE
│       ├── dao/
│       │   ├── InventoryDAO.java              ✅ COMPLETE
│       │   ├── PackageDAO.java                ❌ STUB
│       │   ├── OrderDAO.java                  ❌ STUB
│       │   └── RouteDAO.java                  ❌ STUB
│       ├── services/
│       │   ├── InventoryService.java          ✅ COMPLETE
│       │   ├── PackageService.java            ❌ STUB
│       │   ├── OrderService.java              ❌ STUB
│       │   ├── RouteService.java              ❌ STUB
│       │   └── EmailService.java              ❌ STUB
│       └── util/
│           ├── Result.java                    ✅ COMPLETE (Rust-inspired)
│           ├── EnvLoader.java                 ✅ COMPLETE
│           ├── PasswordUtil.java              ✅ COMPLETE
│           └── StaticFileHandler.java         ✅ COMPLETE
├── frontend/
│   ├── login.html                    ✅ FUNCTIONAL
│   ├── register.html                 ✅ FUNCTIONAL
│   ├── customer/                     ⚠️  UI exists, no backend
│   ├── driver/                       ⚠️  UI exists, no backend
│   ├── management/                   ⚠️  UI exists, inventory backend ready
│   ├── admin/                        ✅ FUNCTIONAL
│   ├── css/
│   │   ├── main.css                  ✅ Complete styling
│   │   └── styles.css                ✅ Login page styling
│   └── js/
│       ├── auth.js                   ✅ Login handler
│       ├── register.js               ✅ Registration handler
│       ├── router.js                 ✅ SPA routing
│       ├── inventory.js              ⚠️  Needs backend integration
│       ├── tracking.js               ❌ STUB
│       ├── orders.js                 ❌ STUB
│       ├── driver.js                 ❌ STUB
│       └── management.js             ❌ STUB
├── database/
│   └── schema.sql                    ✅ Complete with test data
└── Program Documents/
    └── UseCase                       ✅ 10 use cases + 10 misuse cases
```

---

## 🔐 SECURITY IMPLEMENTATION DETAILS

### Bell-LaPadula Access Control

**Clearance Levels:**
| Level | Name | Value | Roles | Data Access |
|-------|------|-------|-------|-------------|
| 0 | UNCLASSIFIED | 0 | Customer | Public info, own orders |
| 1 | CONFIDENTIAL | 1 | Driver | Routes, packages, facilities |
| 2 | SECRET | 2 | Manager | Inventory, PII, payments |
| 3 | TOP_SECRET | 3 | Admin | Audit logs, system config |

**BLP Rules:**
- **No Read Up:** User with clearance X can only read data at level ≤ X
- **No Write Down:** User with clearance X can only write data at level ≥ X

**Implementation:**
```java
BLPAccessControl.checkReadAccess(userClearance, dataClassification)
BLPAccessControl.checkWriteAccess(userClearance, dataClassification)
```

All violations are logged to audit_log table.

### Password Security

**Algorithm:** SHA-256(password + salt)
- 16-byte random salt (Base64 encoded)
- Hex string comparison (prevents timing attacks)
- Password strength requirements:
  - Minimum 8 characters
  - At least one uppercase
  - At least one lowercase
  - At least one digit
  - At least one special character

### Session Management

- UUID-based tokens
- HttpOnly cookies (prevents XSS)
- 1-hour timeout (configurable)
- Sliding window expiry
- In-memory storage (no persistence)

### Audit Logging

**All logged events:**
- LOGIN, FAILED_LOGIN
- BLP_READ_DENIED, BLP_WRITE_DENIED
- ACCOUNT_LOCKED, ACCOUNT_UNLOCKED
- MFA_CODE_GENERATED, MFA_VERIFIED
- VIEW_INVENTORY, SEARCH_INVENTORY
- All admin operations

**Log format:**
```
[timestamp] user=username id=userId action=ACTION result=RESULT ip=IP details=details
```

---

## 📋 API ENDPOINTS

### Authentication
- `POST /api/login` - User authentication (reCAPTCHA required)
- `POST /api/customer/register` - Customer registration
- `GET /whoami` - Session verification

### Admin (TOP_SECRET)
- `GET /admin/logs` - View audit logs
- `GET /admin/users` - List all users
- `PUT /admin/users/:id/role` - Update user role
- `PUT /admin/users/:id/status` - Update account status

### Inventory (SECRET - Manager+)
- `GET /api/inventory` - Get all inventory
- `GET /api/inventory/facility/:id` - Get facility inventory
- `GET /api/inventory/search/:tracking` - Search by tracking number

### Facilities (CONFIDENTIAL - Driver+)
- `GET /api/facilities` - List all facilities

### Not Implemented
- ❌ `POST /api/orders` - Place order
- ❌ `GET /api/packages/:tracking` - Track package
- ❌ `POST /api/routes` - Create route
- ❌ `GET /api/driver/route` - Get driver route
- ❌ `PUT /api/packages/:id/status` - Update delivery status
- ❌ `POST /api/transfers` - Create facility transfer
- ❌ `POST /api/returns` - Request return
- ❌ `PUT /api/packages/:id` - Edit package

---

## 🗄️ DATABASE SCHEMA

**Complete Tables (15):**
1. `users` - User accounts with BLP clearance
2. `security_labels` - Object classification
3. `audit_log` - Security event logging
4. `mfa_codes` - 2FA codes
5. `facilities` - Warehouses/distribution centers
6. `addresses` - Customer pickup/delivery locations
7. `orders` - Customer delivery orders
8. `packages` - Individual packages
9. `payments` - Payment information
10. `routes` - Delivery routes
11. `route_assignments` - Driver assignments
12. `route_packages` - Route package junction
13. `delivery_status_history` - Package tracking timeline
14. `package_transfers` - Inter-facility transfers
15. `inventory` - Facility package inventory
16. `package_returns` - Return tracking
17. `package_edit_history` - Audit trail for edits

**Test Data:**
- 4 users (customer1, driver1, manager1, admin)
- 3 facilities (Denver, LA, NYC)
- 1 order with 1 package (PKG1234567890)
- Package is at facility 1 (Main Distribution Center)

---

## 🔧 DEVELOPMENT SETUP

### Backend Compilation
```bash
cd backend/src
javac -cp ".:../lib/mysql-connector-j-8.4.0.jar" com/delivery/**/*.java
```

### Start Server
```bash
java -cp ".:../lib/mysql-connector-j-8.4.0.jar" com.delivery.Main
```

### Environment Variables (.env)
```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=delivery_system
DB_USER=root
DB_PASSWORD=YourPassword
SERVER_PORT=8081
SESSION_TIMEOUT_SECONDS=3600
RECAPTCHA_SECRET_KEY=6Lf-zAgsAAAAABF-h4Zm5RbcBGtPVJqvFFwJcR1h
```

### Test Credentials
```
customer1 / cust123   (Clearance: 0 - UNCLASSIFIED)
driver1 / driver123   (Clearance: 1 - CONFIDENTIAL)
manager1 / mgr123     (Clearance: 2 - SECRET)
admin / admin123      (Clearance: 3 - TOP_SECRET)
```

---

## 🚀 IMMEDIATE NEXT STEPS

### Priority 1: Core Business Features (Week 1-2)
1. **Email Service Integration**
   - Implement JavaMail with Gmail SMTP
   - Send MFA codes
   - Send order confirmations
   - Send delivery notifications

2. **Order Placement System**
   - `POST /api/orders` endpoint
   - OrderController, OrderService, OrderDAO
   - Payment calculation (simple flat rate or weight-based)
   - Generate tracking ID
   - Add to facility inventory

3. **Package Tracking**
   - `GET /api/packages/:tracking` endpoint
   - PackageController, PackageService, PackageDAO
   - Return delivery history from delivery_status_history table
   - Customer notifications

### Priority 2: Driver & Manager Features (Week 3-4)
4. **Driver Status Updates**
   - `PUT /api/packages/:id/status` endpoint
   - DriverController implementation
   - Update delivery_status_history
   - Trigger customer email notification

5. **Route Optimization**
   - Simple nearest-neighbor greedy algorithm OR
   - Google Maps Distance Matrix API integration
   - RouteController, RouteService implementation
   - Assign routes to drivers

6. **Facility Transfers**
   - `POST /api/transfers` endpoint
   - TransferController, TransferService
   - Update inventory automatically

### Priority 3: Polish & Testing
7. **Frontend Integration**
   - Connect all existing UI pages to backend APIs
   - Replace localStorage with real API calls
   - Add loading states and error handling

8. **Testing**
   - BLP access control tests
   - SQL injection tests
   - XSS tests
   - Brute force login tests

---

## 📝 USE CASE IMPLEMENTATION STATUS

| # | Use Case | Status | Priority | Notes |
|---|----------|--------|----------|-------|
| 1 | Create Customer Account | ✅ 85% | DONE | Missing email verification |
| 2 | Place Delivery Order | ❌ 0% | HIGH | Core feature - needs immediate work |
| 3 | Track Package | ❌ 0% | HIGH | Core feature - needs immediate work |
| 4 | Assign Driver Route | ❌ 10% | MEDIUM | Complex algorithm needed |
| 5 | Update Delivery Status | ❌ 0% | MEDIUM | Driver operations |
| 6 | Transfer Packages | ❌ 0% | MEDIUM | Manager operations |
| 7 | Check Inventory | ✅ 100% | DONE | Complete with CSV export! View/filter/search/export all working |
| 8 | Login | ✅ 90% | DONE | Missing 2FA email integration |
| 9 | Return Package | ❌ 0% | LOW | Nice-to-have |
| 10 | Edit Package Info | ❌ 0% | LOW | Nice-to-have |

---

## 🔄 RECENT CHANGES (2025-11-19)

### Bug Fixes
1. **Fixed SPA Router BasePath Calculation** ✅
   - Issue: Router was calculating basePath from current URL, causing double-path errors
   - Example: `/management/view-inventory.html` → tried to load `/management/management/assign-routes.html`
   - Fix: Changed to `window.location.origin + '/'` in `frontend/js/router.js:4`
   - Result: Page navigation now works correctly throughout the app

2. **Fixed Session Cookie Not Being Saved** ✅
   - Issue: Login fetch request missing `credentials: 'include'`
   - Result: Session cookie wasn't saved, all API calls failed with "Token is required"
   - Fix: Added `credentials: 'include'` to `frontend/js/auth.js:63`
   - Impact: All authenticated API calls now work properly

3. **Fixed DOMContentLoaded Event Not Firing in SPA** ✅
   - Issue: view-inventory.html used `DOMContentLoaded` listener, which never fires when loaded via router
   - Result: Inventory data wasn't loading when navigating via SPA
   - Fix: Changed to IIFE (Immediately Invoked Function Expression) in `view-inventory.html:125`
   - Impact: Inventory data now loads correctly both via direct access and router navigation

4. **Fixed Frontend Directory Path Detection** ✅
   - Issue: When running from `backend/src`, server was serving from `/backend/src/frontend` instead of `/frontend`
   - Fix: Updated path detection logic in `Main.java:137-141` to handle subdirectories
   - Impact: Static files now served from correct location

5. **Fixed Transfer Portal Duplicate Content** ✅
   - Issue: Duplicate `<body>` tag at line 78 causing content duplication
   - Fix: Removed duplicate content and added proper closing tags
   - Impact: Transfer portal page now displays correctly

### New Features
1. **CSV Export for Inventory Reports** ✅
   - Added "Export Report (CSV)" button to view-inventory page
   - Exports all visible inventory items (respects current filter)
   - Filename includes date and facility filter if applied
   - Fully satisfies Use Case 7 requirement: "generate the report and deliver it back to the manager"
   - Location: `frontend/management/view-inventory.html:406-456`

### Inventory System - Now Fully Functional
- ✅ View all inventory across facilities
- ✅ Filter by specific facility
- ✅ Search by tracking number
- ✅ Real-time statistics dashboard (total packages, facilities, in-stock count)
- ✅ Detailed package information modal
- ✅ CSV report export with proper formatting
- ✅ BLP access control (SECRET clearance required)
- ✅ Full audit logging of all operations
- ✅ Session management with HttpOnly cookies
- ✅ CORS headers for cross-origin requests

---

## 🐛 KNOWN ISSUES

1. **Password Hashing Inconsistency**
   - `PasswordUtil.java` may differ from `schema.sql` test user generation
   - Both should use: `SHA256(password + salt)`
   - File: `backend/src/com/delivery/util/PasswordUtil.java:25-35`

2. **Database Connection Not Pooled**
   - Uses single static connection (not production-ready)
   - Consider HikariCP for production
   - File: `backend/src/com/delivery/database/DatabaseConnection.java`

3. **CORS Wildcard**
   - Current: `Access-Control-Allow-Origin: *`
   - Production: Should specify exact frontend origin

---

## 💡 HELPFUL PATTERNS

### Result Pattern (Rust-inspired)
```java
Result<User, String> result = getUserById(id);
if (result.isOk()) {
    User user = result.unwrap();
    // Use user
} else {
    String error = result.unwrapErr();
    // Handle error
}
```

### BLP Access Control
```java
if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.SECRET)) {
    return Result.err("Access denied: Insufficient clearance");
}
```

### Audit Logging
```java
AuditLogger.log(userId, username, "ACTION_NAME", "success", clientIp, "details");
```

### Session Management
```java
String token = SessionManager.createSession(username, role, clearance);
Result<Session, String> session = SessionManager.getSession(token);
```

---

## 📚 DOCUMENTATION REFERENCES

- **USE CASE DOCUMENT:** `Program Documents/UseCase`
- **README:** `README.md` (comprehensive setup guide)
- **BACKEND README:** `README-backend.md` (compilation instructions)
- **DATABASE SCHEMA:** `database/schema.sql` (with test data)
- **THIS FILE:** `PROJECT_STATUS.md` (project status tracker)

