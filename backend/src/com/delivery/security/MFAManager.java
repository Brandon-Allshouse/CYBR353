package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;
import java.sql.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class MFAManager {
    
    private static final int MFA_CODE_LENGTH = 6;
    private static final int MFA_EXPIRY_MINUTES = 5;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generates a 6-digit MFA code and stores it in database
     */
    public static Result<String, String> generateMFACode(Long userId, String username) {
        if (userId == null || username == null) {
            return Result.err("User ID and username are required");
        }

        // Generate 6-digit code
        int code = 100000 + random.nextInt(900000);
        String mfaCode = String.valueOf(code);

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            // Calculate expiry time
            Timestamp expiryTime = Timestamp.from(Instant.now().plus(MFA_EXPIRY_MINUTES, ChronoUnit.MINUTES));

            String sql = "INSERT INTO mfa_codes (user_id, code, expiry_time, used, created_at) " +
                        "VALUES (?, ?, ?, FALSE, NOW())";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setString(2, mfaCode);
                stmt.setTimestamp(3, expiryTime);
                stmt.executeUpdate();

                AuditLogger.log(userId, username, "MFA_CODE_GENERATED", "SUCCESS", 
                              "MFA code generated");

                // In production, send via email/SMS
                System.out.println("========================================");
                System.out.println("MFA CODE for " + username + ": " + mfaCode);
                System.out.println("Expires in " + MFA_EXPIRY_MINUTES + " minutes");
                System.out.println("========================================");

                return Result.ok(mfaCode);
            }

        } catch (SQLException e) {
            AuditLogger.logError("MFA_GENERATION_ERROR", e.getMessage(), username, null);
            return Result.err("Failed to generate MFA code: " + e.getMessage());
        }
    }

    /**
     * Validates an MFA code against the database
     */
    public static Result<Boolean, String> validateMFACode(Long userId, String username, String code) {
        if (userId == null || code == null) {
            return Result.err("User ID and code are required");
        }

        if (code.length() != MFA_CODE_LENGTH || !code.matches("\\d{6}")) {
            return Result.err("Invalid MFA code format");
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "SELECT code_id FROM mfa_codes " +
                        "WHERE user_id = ? AND code = ? " +
                        "AND expiry_time > NOW() AND used = FALSE";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setString(2, code);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int codeId = rs.getInt("code_id");

                        // Mark code as used
                        String updateSql = "UPDATE mfa_codes SET used = TRUE WHERE code_id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, codeId);
                            updateStmt.executeUpdate();
                        }

                        AuditLogger.log(userId, username, "MFA_VALIDATION", "SUCCESS", 
                                      "MFA code validated successfully");
                        return Result.ok(true);
                    } else {
                        AuditLogger.log(userId, username, "MFA_VALIDATION", "DENIED", 
                                      "Invalid or expired MFA code");
                        return Result.ok(false);
                    }
                }
            }

        } catch (SQLException e) {
            AuditLogger.logError("MFA_VALIDATION_ERROR", e.getMessage(), username, null);
            return Result.err("Failed to validate MFA code: " + e.getMessage());
        }
    }

    /**
     * Cleans up expired MFA codes
     */
    public static Result<Integer, String> cleanupExpiredCodes() {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "DELETE FROM mfa_codes WHERE expiry_time < NOW()";
            
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate(sql);

                if (deleted > 0) {
                    AuditLogger.log("SYSTEM", "MFA_CLEANUP", "SUCCESS", 
                                  deleted + " expired MFA codes deleted");
                }

                return Result.ok(deleted);
            }

        } catch (SQLException e) {
            return Result.err("Failed to cleanup MFA codes: " + e.getMessage());
        }
    }
}
