package com.delivery.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.delivery.util.EnvLoader;

public class SecurityManager {
    // T: success value type
    // E: error value type
    public static class Result<T, E> {
        private final T ok;
        private final E err;

        private Result(T ok, E err) {
            this.ok = ok;
            this.err = err;
        }

        public static <T, E> Result<T, E> ok(T v) { return new Result<>(v, null); }
        public static <T, E> Result<T, E> err(E e) { return new Result<>(null, e); }

        public boolean isOk() { return err == null; }
        public boolean isErr() { return err != null; }
        public T unwrap() { return ok; }
        public E unwrapErr() { return err; }
    }

    // Collects validation errors for input checks.
    // Simple container with a list of error messages.
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        public void addError(String e) { errors.add(e); }
        public boolean isValid() { return errors.isEmpty(); }
        public List<String> getErrors() { return errors; }
    }

    // Security levels used by BLP checks.
    public enum SecurityLevel {
        UNCLASSIFIED(0), CONFIDENTIAL(1), SECRET(2), TOP_SECRET(3);
        private final int level;
        SecurityLevel(int l) { level = l; }
        public int getLevel() { return level; }
        
        // Convert integer level (0-3) to SecurityLevel enum.
        public static Result<SecurityLevel, String> fromInt(int lvl) {
            for (SecurityLevel sl : SecurityLevel.values()) {
                if (sl.level == lvl) {
                    return Result.ok(sl);
                }
            }
            return Result.err("Invalid clearance level: " + lvl);
        }
    }

    public static class AuditLogger {
        private static final List<String> logs = new ArrayList<>();

    // Log a audit entry.
    public static synchronized Result<Void, String> log(Long userId, String username,
                                String action, String result,
                                String ipAddress, String details) {
            if (username == null || action == null || result == null) {
                return Result.err("Username, action, and result are required");
            }

            String entry = String.format("[%s] user=%s id=%s action=%s result=%s ip=%s details=%s",
                    Instant.now().toString(), username, userId == null ? "-" : userId.toString(),
                    action, result, ipAddress == null ? "-" : ipAddress, details == null ? "" : details);
            logs.add(entry);
            System.out.println("AUDIT: " + entry);

            // Write to database
            com.delivery.util.Result<java.sql.Connection, String> connResult = com.delivery.database.DatabaseConnection.getConnection();
            if (connResult.isOk()) {
                try (java.sql.Connection conn = connResult.unwrap()) {
                    String sql = "INSERT INTO audit_log (user_id, username, action, result, ip_address, details) VALUES (?, ?, ?, ?, ?, ?)";
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                        if (userId != null) {
                            stmt.setLong(1, userId);
                        } else {
                            stmt.setNull(1, java.sql.Types.BIGINT);
                        }
                        stmt.setString(2, username);
                        stmt.setString(3, action);
                        stmt.setString(4, result);
                        stmt.setString(5, ipAddress);
                        stmt.setString(6, details);
                        stmt.executeUpdate();
                    }
                } catch (java.sql.SQLException e) {
                    System.err.println("Failed to write audit log to database: " + e.getMessage());
                }
            }

            return Result.ok(null);
        }

    // string automatically based on the eventType
    public static Result<Void, String> logSecurityEvent(Long userId, String username, String eventType,
                                String ipAddress, String description) {
            String result;
            String up = eventType == null ? "" : eventType.toUpperCase();
            if (up.contains("DENIED") || up.contains("BLOCKED") || up.contains("LOCKED") || up.contains("EXCEEDED") || up.contains("VIOLATION")) {
                result = "denied";
            } else if (up.contains("ERROR") || up.contains("FAILED")) {
                result = "error";
            } else {
                result = "success";
            }
            return log(userId, username == null ? "SYSTEM" : username, eventType == null ? "EVENT" : eventType, result, ipAddress, description);
        }

    // Log an error event with an errorType
    public static Result<Void, String> logError(String errorType, String errorMessage, String username) {
            return log(null, username == null ? "SYSTEM" : username, errorType == null ? "ERROR" : errorType, "error", null,
                    errorMessage == null ? "" : errorMessage);
        }

        // Retrieve the in-memory audit log entries.
        public static List<String> getLogs() { return logs; }
    }

    // Simple account lockout mechanism to prevent brute-force attempts.
    // Tracks failed attempts per username and sets a lockout expiry when the threshold is reached.
    public static class LoginLockout {
        private static final int MAX_LOGIN_ATTEMPTS = 3;
        private static final int LOCKOUT_MINUTES = 30;

        private static class Info { AtomicInteger attempts = new AtomicInteger(0); Instant lockoutUntil = null; }
        private static final Map<String, Info> store = new ConcurrentHashMap<>();

    // Record a failed login attempt. Returns the current number of failed attempts for the username, or 0 if the account is already locked.
    public static Result<Integer, String> recordFailedAttempt(String username, String ipAddress) {
            if (username == null || username.trim().isEmpty()) return Result.err("Username required");
            Info info = store.computeIfAbsent(username, k -> new Info());
            if (info.lockoutUntil != null && Instant.now().isBefore(info.lockoutUntil)) {
                return Result.ok(0); // locked
            }
            int attempts = info.attempts.incrementAndGet();
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                info.lockoutUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60);
                AuditLogger.logSecurityEvent(null, username, "LOCKOUT", ipAddress, "Account locked due to failed attempts");
            }
            AuditLogger.log(null, username, "FAILED_LOGIN", attempts >= MAX_LOGIN_ATTEMPTS ? "denied" : "denied", ipAddress, "failed attempt #" + attempts);
            return Result.ok(attempts);
        }

    // Check whether an account is currently locked due to previous failures.
    public static Result<Boolean, String> isAccountLocked(String username, String ipAddress) {
            if (username == null || username.trim().isEmpty()) return Result.err("Username required");
            Info info = store.get(username);
            boolean locked = info != null && info.lockoutUntil != null && Instant.now().isBefore(info.lockoutUntil);
            return Result.ok(locked);
        }

    // Reset the failed-attempts counter for a username.
    public static Result<Void, String> resetFailedAttempts(String username) {
            if (username == null) return Result.err("Username required");
            store.remove(username);
            AuditLogger.logSecurityEvent(null, username, "RESET_FAILED_ATTEMPTS", null, "Failed attempts reset");
            return Result.ok(null);
        }
    }

    // Implements BLP access checks.
    public static class BLPAccessControl {
        public static boolean checkReadAccess(SecurityLevel subjectClearance, SecurityLevel objectClassification) {
            boolean allowed = subjectClearance.ordinal() >= objectClassification.ordinal();
            if (!allowed) AuditLogger.logSecurityEvent(null, "SYSTEM", "BLP_READ_DENIED", null,
                    String.format("Read denied: %s cannot read %s", subjectClearance, objectClassification));
            return allowed;
        }

        public static boolean checkWriteAccess(SecurityLevel subjectClearance, SecurityLevel objectClassification) {
            boolean allowed = subjectClearance.ordinal() <= objectClassification.ordinal();
            if (!allowed) AuditLogger.logSecurityEvent(null, "SYSTEM", "BLP_WRITE_DENIED", null,
                    String.format("Write denied: %s cannot write to %s", subjectClearance, objectClassification));
            return allowed;
        }
    }

    // Input sanitization and basic format validators (email, phone, username).
    // Removes dangerous characters and simple HTML tags.
    public static class InputSanitizer {
        public static Result<String, String> sanitizeString(String input) {
            if (input == null) return Result.err("Input cannot be null");
            String s = input.replaceAll("[\\'\"\\\\;]", "");
            s = s.replaceAll("<.*?>", "");
            s = s.replaceAll("[;&|`$()]", "");
            return Result.ok(s.trim());
        }

        public static Result<Boolean, String> validateEmail(String email) {
            if (email == null || email.trim().isEmpty()) return Result.err("Email cannot be empty");
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) return Result.err("Invalid email format");
            return Result.ok(true);
        }

        public static Result<Boolean, String> validatePhone(String phone) {
            if (phone == null || phone.trim().isEmpty()) return Result.err("Phone cannot be empty");
            if (!phone.matches("^[+]?[(]?[0-9]{3}[)]?[-\\s.]?[0-9]{3}[-\\s.]?[0-9]{4,6}$")) return Result.err("Invalid phone format");
            return Result.ok(true);
        }

        public static Result<Boolean, String> validateUsername(String username) {
            if (username == null || username.trim().isEmpty()) return Result.err("Username cannot be empty");
            if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) return Result.err("Username must be 3-20 alphanumeric/_");
            return Result.ok(true);
        }
    }

    // Higher level validation routines that use the sanitizer and password rules
    public static class InputValidator {
        public static ValidationResult validateRegistration(String name, String email, String phone, String password) {
            ValidationResult vr = new ValidationResult();
            if (name == null || name.trim().isEmpty()) vr.addError("Name cannot be empty");
            else if (name.length() > 100) vr.addError("Name too long");
            Result<Boolean, String> e = InputSanitizer.validateEmail(email); if (e.isErr()) vr.addError(e.unwrapErr());
            Result<Boolean, String> p = InputSanitizer.validatePhone(phone); if (p.isErr()) vr.addError(p.unwrapErr());
            Result<Boolean, String> pw = PasswordManager.validatePasswordStrength(password); if (pw.isErr()) vr.addError(pw.unwrapErr());
            return vr;
        }
    }

    // In memory MFA manager
    public static class MFAManager {
        private static final int EXPIRY_MINUTES = 5;
        private static final SecureRandom rnd = new SecureRandom();

        private static class MFAInfo { String code; Instant expires; boolean used = false; }
        private static final Map<Long, MFAInfo> codes = new ConcurrentHashMap<>();

    // Generate and store a one-time MFA code for the given userId.
    public static Result<String, String> generateMFACode(Long userId, String username) {
            if (userId == null || username == null) return Result.err("UserId and username required");
            int code = 100000 + rnd.nextInt(900000);
            MFAInfo info = new MFAInfo(); info.code = String.valueOf(code); info.expires = Instant.now().plusSeconds(EXPIRY_MINUTES * 60);
            codes.put(userId, info);
            AuditLogger.log(userId, username, "MFA_CODE_GENERATED", "success", null, "MFA code generated");
            // In real system deliver via SMS/email; here we print
            System.out.println("MFA code for " + username + ": " + info.code + " (expires in " + EXPIRY_MINUTES + "m)");
            return Result.ok(info.code);
        }

    // Validate a provided MFA code for a user and mark it as used.
    public static Result<Boolean, String> validateMFACode(Long userId, String username, String code) {
            if (userId == null || code == null) return Result.err("UserId and code required");
            MFAInfo info = codes.get(userId);
            if (info == null) { AuditLogger.log(userId, username, "MFA_VALIDATION", "denied", null, "No code"); return Result.ok(false); }
            if (info.used || Instant.now().isAfter(info.expires) || !info.code.equals(code)) {
                AuditLogger.log(userId, username, "MFA_VALIDATION", "denied", null, "Invalid or expired code");
                return Result.ok(false);
            }
            info.used = true;
            AuditLogger.log(userId, username, "MFA_VALIDATION", "success", null, "Code validated");
            return Result.ok(true);
        }
    }

    // Password utilities: salt generation, SHA-256 hashing, and strength checks.
    public static class PasswordManager {
        private static final SecureRandom random = new SecureRandom();
        private static final int SALT_LEN = 16;

    // Generate a base64-encoded random salt.
    public static String generateSalt() {
            byte[] b = new byte[SALT_LEN]; random.nextBytes(b); return Base64.getEncoder().encodeToString(b);
        }

    // Hash a password with the provided salt using SHA-256.
    public static Result<String, String> hashPassword(String password, String salt) {
            if (password == null || salt == null) return Result.err("Password and salt required");
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt.getBytes());
                byte[] hashed = md.digest(password.getBytes());
                StringBuilder sb = new StringBuilder(); for (byte bb : hashed) sb.append(String.format("%02x", bb));
                return Result.ok(sb.toString());
            } catch (NoSuchAlgorithmException e) { return Result.err("SHA-256 not available"); }
        }

    // Verify a password by hashing and comparing to the expected hash.
    public static Result<Boolean, String> verifyPassword(String password, String salt, String expectedHash) {
            Result<String, String> r = hashPassword(password, salt); if (r.isErr()) return Result.err(r.unwrapErr());
            return Result.ok(r.unwrap().equalsIgnoreCase(expectedHash));
        }

    // Check password strength against simple policy rules.
    public static Result<Boolean, String> validatePasswordStrength(String password) {
            if (password == null) return Result.err("Password cannot be null");
            if (password.length() < 8) return Result.err("Password must be at least 8 chars");
            if (!password.matches(".*[A-Z].*")) return Result.err("Must contain uppercase");
            if (!password.matches(".*[a-z].*")) return Result.err("Must contain lowercase");
            if (!password.matches(".*\\d.*")) return Result.err("Must contain digit");
            if (!password.matches(".*[!@#$%^&*()_+\\-=[\\]{};':\"\\|,.<>/?].*")) return Result.err("Must contain special char");
            return Result.ok(true);
        }
    }

    // Simple per identifier rate limiter to prevent brute force attacks
    public static class RateLimiter {
        private static final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        private static final Map<String, Instant> windows = new ConcurrentHashMap<>();
        private static final int DEFAULT_MAX = 60; // default per-minute

    // Check and record a request. Returns error result if limit exceeded.
    public static Result<Boolean, String> allowRequest(String identifier, String action, int maxPerMinute) {
            if (identifier == null || action == null) return Result.err("identifier/action required");
            String key = identifier + ":" + action;
            Instant now = Instant.now();
            Instant start = windows.get(key);
            if (start == null || now.isAfter(start.plusSeconds(60))) {
                windows.put(key, now);
                counters.put(key, new AtomicInteger(1));
                return Result.ok(true);
            }
            AtomicInteger c = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
            if (c.incrementAndGet() > (maxPerMinute <= 0 ? DEFAULT_MAX : maxPerMinute)) {
                AuditLogger.logSecurityEvent(null, identifier, "RATE_LIMIT_EXCEEDED", null, "Exceeded " + action);
                return Result.err("Rate limit exceeded");
            }
            return Result.ok(true);
        }
    }

    // Server-side reCAPTCHA verifier. Sends a POST to Google's verify endpoint
    public static class RecaptchaVerifier {
        private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    // Verify the provided reCAPTCHA token with Google's API.
    public static Result<Boolean, String> verifyRecaptcha(String token, String clientIp) {
            if (token == null || token.trim().isEmpty()) return Result.err("reCAPTCHA token required");

            com.delivery.util.Result<String, String> secretResult = EnvLoader.get("RECAPTCHA_SECRET_KEY");
            if (secretResult.isErr()) {
                AuditLogger.logError("RECAPTCHA_CONFIG", "RECAPTCHA_SECRET_KEY not set in environment: " + secretResult.unwrapErr(), "SYSTEM");
                return Result.err("reCAPTCHA not configured on server");
            }
            String secret = secretResult.unwrap();

            try {
                StringBuilder postData = new StringBuilder();
                postData.append("secret=").append(URLEncoder.encode(secret, StandardCharsets.UTF_8.name()));
                postData.append("&response=").append(URLEncoder.encode(token, StandardCharsets.UTF_8.name()));
                if (clientIp != null && !clientIp.trim().isEmpty()) {
                    postData.append("&remoteip=").append(URLEncoder.encode(clientIp, StandardCharsets.UTF_8.name()));
                }

                URL url = new URL(VERIFY_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=" + StandardCharsets.UTF_8.name());
                conn.setDoOutput(true);

                byte[] postBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                    os.flush();
                }

                int code = conn.getResponseCode();
                BufferedReader br;
                if (code >= 200 && code < 300) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                } else {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream() == null ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
                }

                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    resp.append(line).append('\n');
                }

                String json = resp.toString();
                boolean success = json.contains("\"success\": true") || json.contains("\"success\":true");
                if (success) {
                    return Result.ok(true);
                } else {
                    String errors = extractErrors(json);
                    AuditLogger.logError("RECAPTCHA_FAILED", errors + " | raw: " + json, "SYSTEM");
                    return Result.err("reCAPTCHA verification failed: " + errors);
                }

            } catch (Exception e) {
                AuditLogger.logError("RECAPTCHA_ERROR", e.getMessage(), "SYSTEM");
                return Result.err("reCAPTCHA verification error: " + e.getMessage());
            }
        }

        private static String extractErrors(String json) {
            if (json == null) return "unknown error";
            try {
                int idx = json.indexOf("\"error-codes\"");
                if (idx == -1) return "no error-codes provided";
                int a = json.indexOf('[', idx);
                int b = json.indexOf(']', a);
                if (a == -1 || b == -1) return "malformed error-codes";
                String arr = json.substring(a + 1, b).replaceAll("[\"\\s]", "");
                if (arr.isEmpty()) return "no error codes";
                return arr.replaceAll("\\s*,\\s*", ", ");
            } catch (Exception ex) {
                return "error parsing response";
            }
        }
    }
}
