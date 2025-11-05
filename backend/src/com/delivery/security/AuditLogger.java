package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AuditLogger {

    /**
     * Inserts audit record into audit_log table
     */
    public static synchronized Result<Void, String> log(Long userId, String username, String action,
                                                         String result, String ipAddress, String details) {
        if (username == null || action == null || result == null) {
            return Result.err("Username, action, and result are required");
        }

        String sql = "INSERT INTO audit_log (user_id, username, action, result, ip_address, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
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
            return Result.err("Audit log insert failed: " + e.getMessage());
        }
    }

    /**
     * Simplified log without userId and ipAddress
     */
    public static Result<Void, String> log(String username, String action, String result, String details) {
        return log(null, username, action, result, null, details);
    }

    /**
     * Log with userId
     */
    public static Result<Void, String> log(Long userId, String username, String action, String result, String details) {
        return log(userId, username, action, result, null, details);
    }

    /**
     * Log security event
     */
    public static Result<Void, String> logSecurityEvent(String eventType, String description, String userId) {
        String result = determineResult(eventType);
        return log(null, userId, eventType, result, null, description);
    }

    /**
     * Log error with stack trace
     */
    public static Result<Void, String> logError(String errorType, String errorMessage, String userId, String stackTrace) {
        String details = String.format("Error: %s\nStack: %s", errorMessage, 
                                      stackTrace != null ? stackTrace.substring(0, Math.min(500, stackTrace.length())) : "");
        return log(null, userId, errorType, "ERROR", null, details);
    }

    /**
     * Determine result based on event type
     */
    private static String determineResult(String eventType) {
        if (eventType.contains("SUCCESS") || eventType.contains("CREATED")) {
            return "SUCCESS";
        } else if (eventType.contains("DENIED") || eventType.contains("BLOCKED") || eventType.contains("LOCKED")) {
            return "DENIED";
        } else if (eventType.contains("ERROR") || eventType.contains("FAILED")) {
            return "ERROR";
        } else {
            return "INFO";
        }
    }
}