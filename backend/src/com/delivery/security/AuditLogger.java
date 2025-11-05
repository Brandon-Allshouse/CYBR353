package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AuditLogger {

    // Inserts audit record into audit_log table. Pass null for userId/ipAddress if unavailable.
    public static synchronized Result<Void, String> log(Long userId, String username, String action,
                                                         String result, String ipAddress, String details) {
        if (username == null || action == null || result == null) {
            return Result.err("Username, action, and result are required");
        }

        String sql = "INSERT INTO audit_log (user_id, username, action, result, ip_address, details) VALUES (?, ?, ?, ?, ?, ?)";

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
    }
