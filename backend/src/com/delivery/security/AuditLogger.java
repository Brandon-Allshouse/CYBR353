package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AuditLogger {

    public static synchronized Result<Void, String> log(Long userId, String username, String action,
                                                         String result, String ipAddress, String details) {
        if (username == null || action == null || result == null) {
            return Result.err("Username, action, and result are required for audit logging");
        }

        // Validate result is lowercase and one of the allowed enum values
        if (!result.equals("success") && !result.equals("denied") && !result.equals("error")) {
            System.err.println("WARNING: Invalid audit result value '" + result + "' - must be lowercase: success, denied, or error");
            // Auto-fix common mistakes by converting to lowercase
            result = result.toLowerCase();
            // If still invalid, default to error
            if (!result.equals("success") && !result.equals("denied") && !result.equals("error")) {
                result = "error";
            }
        }

        String sql = "INSERT INTO audit_log (user_id, username, action, result, ip_address, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            // Fallback: log to console if database unavailable
            System.err.println("AUDIT LOG (DB UNAVAILABLE): " + username + " " + action + " " + result +
                             " IP:" + ipAddress + " " + details);
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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
            return Result.ok(null);

        } catch (SQLException e) {
            // Fallback: log to console on SQL error
            System.err.println("AUDIT LOG ERROR: " + e.getMessage());
            System.err.println("Failed to log: " + username + " " + action + " " + result);
            return Result.err("Audit log insert failed: " + e.getMessage());
        }
    }

    public static Result<Void, String> log(Long userId, String username, String action,
                                          String result, String details) {
        return log(userId, username, action, result, null, details);
    }

    public static Result<Void, String> log(String username, String action, String result,
                                          String ipAddress, String details) {
        return log(null, username, action, result, ipAddress, details);
    }

    /**
     * Logs security events (BLP violations, rate limiting, etc.)
     * Automatically uses appropriate result based on event type keywords
     *
     * @param userId User ID (can be null)
     * @param username Username or identifier
     * @param eventType Type of security event (e.g., "BLP_READ_DENIED", "RATE_LIMIT_EXCEEDED")
     * @param ipAddress Client IP address (can be null)
     * @param description Event description
     */
    public static Result<Void, String> logSecurityEvent(Long userId, String username, String eventType,
                                                        String ipAddress, String description) {
        // Determine appropriate result based on event type
        String result;
        if (eventType.contains("DENIED") || eventType.contains("BLOCKED") || eventType.contains("LOCKED") ||
            eventType.contains("EXCEEDED") || eventType.contains("VIOLATION")) {
            result = "denied";
        } else if (eventType.contains("ERROR") || eventType.contains("FAILED")) {
            result = "error";
        } else if (eventType.contains("SUCCESS") || eventType.contains("CREATED") || eventType.contains("CLEARED")) {
            result = "success";
        } else {
            // Default to success for informational events
            result = "success";
        }

        return log(userId, username, eventType, result, ipAddress, description);
    }

    /**
     * Logs error events with optional stack trace
     * Stack traces are truncated to 500 characters to prevent log bloat
     */
    public static Result<Void, String> logError(String errorType, String errorMessage,
                                               String username, String ipAddress, String stackTrace) {
        String details = "Error: " + errorMessage;
        if (stackTrace != null && !stackTrace.isEmpty()) {
            String truncated = stackTrace.substring(0, Math.min(500, stackTrace.length()));
            details += "\nStack: " + truncated;
        }

        return log(null, username != null ? username : "SYSTEM", errorType, "error", ipAddress, details);
    }

    /**
     * Simplified error logging without stack trace
     */
    public static Result<Void, String> logError(String errorType, String errorMessage, String username) {
        return logError(errorType, errorMessage, username, null, null);
    }
}
