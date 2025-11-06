package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class LoginLockout {
    
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    /**
     * Records a failed login attempt
     */
    public static Result<Integer, String> recordFailedAttempt(String username) {
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

                            AuditLogger.log(userId, username, "ACCOUNT_LOCKED", "SUCCESS",
                                          String.format("Account locked after %d failed attempts", attempts));
                        } else {
                            // Increment attempts
                            updateSql = "UPDATE users SET failed_attempts = ? WHERE user_id = ?";
                            updateStmt = conn.prepareStatement(updateSql);
                            updateStmt.setInt(1, attempts);
                            updateStmt.setLong(2, userId);

                            updateStmt.executeUpdate();

                            AuditLogger.log(userId, username, "FAILED_LOGIN", "DENIED",
                                          String.format("Failed attempt %d of %d", attempts, MAX_LOGIN_ATTEMPTS));
                        }

                        return Result.ok(attempts);
                    } else {
                        return Result.err("User not found");
                    }
                }
            }

        } catch (SQLException e) {
            AuditLogger.logError("LOCKOUT_ERROR", "Error recording failed attempt: " + e.getMessage(), 
                               username, null);
            return Result.err("Failed to record login attempt: " + e.getMessage());
        }
    }

    /**
     * Checks if an account is currently locked
     */
    public static Result<Boolean, String> isAccountLocked(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Result.err("Username cannot be empty");
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "SELECT lockout_until FROM users WHERE username = ? AND lockout_until > NOW()";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp lockoutUntil = rs.getTimestamp("lockout_until");
                        AuditLogger.log(username, "LOCKOUT_CHECK", "DENIED",
                                      "Account locked until " + lockoutUntil);
                        return Result.ok(true);
                    }
                    return Result.ok(false);
                }
            }

        } catch (SQLException e) {
            AuditLogger.logError("LOCKOUT_CHECK_ERROR", "Error checking lockout: " + e.getMessage(),
                               username, null);
            return Result.err("Failed to check lockout status: " + e.getMessage());
        }
    }

    /**
     * Resets failed login attempts after successful login
     */
    public static Result<Void, String> resetFailedAttempts(Long userId, String username) {
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
                stmt.executeUpdate();

                AuditLogger.log(userId, username, "LOGIN_SUCCESS", "SUCCESS",
                              "Failed attempts reset after successful login");

                return Result.ok(null);
            }

        } catch (SQLException e) {
            AuditLogger.logError("RESET_ATTEMPTS_ERROR", "Error resetting attempts: " + e.getMessage(),
                               username, null);
            return Result.err("Failed to reset attempts: " + e.getMessage());
        }
    }

    /**
     * Gets remaining login attempts before lockout
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
}