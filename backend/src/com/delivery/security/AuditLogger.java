package com.delivery.security;

import com.delivery.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AuditLogger {

    // Logs to audit_log table. Pass null for userId/ipAddress if not available.
    public static synchronized void log(Long userId, String username, String action, String result, String ipAddress, String details) {
        String sql = "INSERT INTO audit_log (user_id, username, action, result, ip_address, details) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
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

        } catch (SQLException e) {
            throw new RuntimeException("Audit log insert failed", e);
        }
    }

    // Simplified version when only username is available
    public static void log(String username, String action, String result, String details) {
        log(null, username, action, result, null, details);
    }
}
