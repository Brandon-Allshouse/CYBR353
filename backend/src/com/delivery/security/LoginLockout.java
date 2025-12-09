package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Account lockout mechanism to prevent brute force attacks
 * Locks accounts after MAX_LOGIN_ATTEMPTS failed login attempts for LOCKOUT_DURATION_MINUTES
 * All operations logged to audit_log with IP tracking for security compliance
 */
public class LoginLockout {

    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    /**
     * Lockout status containing user_id and locked state
     * Used to return both pieces of information from isAccountLocked()
     */
    public static class LockoutStatus {
        public final Long userId;
        public final boolean isLocked;
        public final Timestamp lockoutUntil;

        public LockoutStatus(Long userId, boolean isLocked, Timestamp lockoutUntil) {
            this.userId = userId;
            this.isLocked = isLocked;
            this.lockoutUntil = lockoutUntil;
        }
    }

    /**
     * Records a failed login attempt and locks account if threshold exceeded
     *
     * @param username Username that failed authentication
     * @param ipAddress Client IP address making the attempt (for audit logging)
     * @return Result containing number of failed attempts, or error message
     */
    public static Result<Integer, String> recordFailedAttempt(String username, String ipAddress) {
        if (username == null || username.trim().isEmpty()) {
            return Result.err("Username cannot be empty");
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String selectSql = "SELECT user_id, failed_attempts FROM users WHERE username = ?";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, username);

                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        long userId = rs.getLong("user_id");
                        int attempts = rs.getInt("failed_attempts") + 1;

                        String updateSql;
                        PreparedStatement updateStmt;

                        if (attempts >= MAX_LOGIN_ATTEMPTS) {
                            // Lock the account
                            Timestamp lockoutUntil = Timestamp.from(
                                Instant.now().plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES)
                            );

                            updateSql = "UPDATE users SET failed_attempts = ?, lockout_until = ? WHERE user_id = ?";
                            updateStmt = conn.prepareStatement(updateSql);
                            updateStmt.setInt(1, attempts);
                            updateStmt.setTimestamp(2, lockoutUntil);
                            updateStmt.setLong(3, userId);

                            updateStmt.executeUpdate();

                            AuditLogger.log(userId, username, "ACCOUNT_LOCKED", "success", ipAddress,
                                          String.format("Account locked after %d failed attempts. Locked until: %s",
                                                       attempts, lockoutUntil));
                        } else {
                            // Increment attempts
                            updateSql = "UPDATE users SET failed_attempts = ? WHERE user_id = ?";
                            updateStmt = conn.prepareStatement(updateSql);
                            updateStmt.setInt(1, attempts);
                            updateStmt.setLong(2, userId);

                            updateStmt.executeUpdate();

                            AuditLogger.log(userId, username, "FAILED_LOGIN_ATTEMPT", "denied", ipAddress,
                                          String.format("Failed login attempt %d of %d", attempts, MAX_LOGIN_ATTEMPTS));
                        }

                        return Result.ok(attempts);
                    } else {
                        AuditLogger.log(username, "FAILED_LOGIN_ATTEMPT", "denied", ipAddress,
                                      "User not found");
                        return Result.err("User not found");
                    }
                }
            }

        } catch (SQLException e) {
            AuditLogger.logError("LOCKOUT_ERROR", "Error recording failed attempt: " + e.getMessage(),
                               username, ipAddress, e.getStackTrace().toString());
            return Result.err("Failed to record login attempt: " + e.getMessage());
        }
    }

    /**
     * Checks if an account is currently locked out
     * Returns LockoutStatus containing userId, isLocked flag, and lockout expiration time
     *
     * @param username Username to check
     * @param ipAddress Client IP address (for audit logging)
     * @return Result containing LockoutStatus with userId and lock information
     */
    public static Result<LockoutStatus, String> isAccountLocked(String username, String ipAddress) {
        if (username == null || username.trim().isEmpty()) {
            return Result.err("Username cannot be empty");
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            // First check if account is locked
            String sql = "SELECT user_id, lockout_until FROM users WHERE username = ? AND lockout_until > NOW()";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Account is locked
                        Long userId = rs.getLong("user_id");
                        Timestamp lockoutUntil = rs.getTimestamp("lockout_until");
                        AuditLogger.log(userId, username, "LOCKOUT_CHECK", "denied", ipAddress,
                                      "Account locked until " + lockoutUntil);
                        return Result.ok(new LockoutStatus(userId, true, lockoutUntil));
                    }
                }
            }

            // Account not locked, but we need to get the user_id for consistent logging
            String userIdSql = "SELECT user_id FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(userIdSql)) {
                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Long userId = rs.getLong("user_id");
                        return Result.ok(new LockoutStatus(userId, false, null));
                    } else {
                        // User doesn't exist - return null userId
                        return Result.ok(new LockoutStatus(null, false, null));
                    }
                }
            }

        } catch (SQLException e) {
            AuditLogger.logError("LOCKOUT_CHECK_ERROR", "Error checking lockout: " + e.getMessage(),
                               username, ipAddress, null);
            return Result.err("Failed to check lockout status: " + e.getMessage());
        }
    }

    /**
     * Resets failed login attempts after successful authentication
     *
     * @param userId User ID to reset
     * @param username Username (for audit logging)
     * @param ipAddress Client IP address (for audit logging)
     * @return Result indicating success or failure
     */
    public static Result<Void, String> resetFailedAttempts(Long userId, String username, String ipAddress) {
        if (userId == null) {
            return Result.err("User ID cannot be null");
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "UPDATE users SET failed_attempts = 0, lockout_until = NULL WHERE user_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                int updated = stmt.executeUpdate();

                if (updated > 0) {
                    AuditLogger.log(userId, username, "LOCKOUT_RESET", "success", ipAddress,
                                  "Failed attempts reset after successful login");
                }

                return Result.ok(null);
            }

        } catch (SQLException e) {
            AuditLogger.logError("RESET_ATTEMPTS_ERROR", "Error resetting attempts: " + e.getMessage(),
                               username, ipAddress, null);
            return Result.err("Failed to reset attempts: " + e.getMessage());
        }
    }

    /**
     * Gets remaining login attempts before account lockout
     * This method does not log to audit (informational only)
     *
     * @param username Username to check
     * @return Result containing remaining attempts count
     */
    public static Result<Integer, String> getRemainingAttempts(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Result.err("Username cannot be empty");
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "SELECT failed_attempts FROM users WHERE username = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int attempts = rs.getInt("failed_attempts");
                        int remaining = Math.max(0, MAX_LOGIN_ATTEMPTS - attempts);
                        return Result.ok(remaining);
                    }
                    return Result.err("User not found");
                }
            }

        } catch (SQLException e) {
            return Result.err("Failed to get remaining attempts: " + e.getMessage());
        }
    }

    /**
     * Gets the maximum allowed login attempts before lockout
     * @return Maximum allowed attempts
     */
    public static int getMaxAttempts() {
        return MAX_LOGIN_ATTEMPTS;
    }

    /**
     * Gets the lockout duration in minutes
     * @return Lockout duration in minutes
     */
    public static int getLockoutDurationMinutes() {
        return LOCKOUT_DURATION_MINUTES;
    }
}
