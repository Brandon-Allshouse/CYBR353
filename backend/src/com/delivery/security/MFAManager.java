package com.delivery.security;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;
import java.sql.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Two-factor authentication using time-limited numeric codes
 * Codes are single-use, expire after 5 minutes, and stored in database
 */
public class MFAManager {

    private static final int MFA_CODE_LENGTH = 6;
    private static final int MFA_EXPIRY_MINUTES = 5;
    private static final SecureRandom random = new SecureRandom();

    public static Result<String, String> generateMFACode(Long userId, String username) {
        if (userId == null || username == null) {
            return Result.err("User ID and username are required");
        }

        int code = 100000 + random.nextInt(900000);
        String mfaCode = String.valueOf(code);

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            Timestamp expiryTime = Timestamp.from(Instant.now().plus(MFA_EXPIRY_MINUTES, ChronoUnit.MINUTES));

            String sql = "INSERT INTO mfa_codes (user_id, code, expiry_time, used, created_at) " +
                        "VALUES (?, ?, ?, FALSE, NOW())";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setString(2, mfaCode);
                stmt.setTimestamp(3, expiryTime);
                stmt.executeUpdate();

                // Log MFA code generation for security audit (IP not available in MFA context)
                AuditLogger.log(userId, username, "MFA_CODE_GENERATED", "success",
                              "MFA code generated and stored");

                // TODO: Replace console output with email/SMS delivery service
                System.out.println("========================================");
                System.out.println("MFA CODE for " + username + ": " + mfaCode);
                System.out.println("Expires in " + MFA_EXPIRY_MINUTES + " minutes");
                System.out.println("========================================");

                return Result.ok(mfaCode);
            }

        } catch (SQLException e) {
            // Log MFA generation errors for troubleshooting
            AuditLogger.logError("MFA_GENERATION_ERROR", e.getMessage(), username);
            return Result.err("Failed to generate MFA code: " + e.getMessage());
        }
    }

    // Validates code and marks as used to prevent replay attacks
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

                        String updateSql = "UPDATE mfa_codes SET used = TRUE WHERE code_id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, codeId);
                            updateStmt.executeUpdate();
                        }

                        // Log successful MFA validation
                        AuditLogger.log(userId, username, "MFA_VALIDATION", "success",
                                      "MFA code validated and marked as used");
                        return Result.ok(true);
                    } else {
                        // Log failed MFA validation attempt
                        AuditLogger.log(userId, username, "MFA_VALIDATION", "denied",
                                      "Invalid or expired MFA code provided");
                        return Result.ok(false);
                    }
                }
            }

        } catch (SQLException e) {
            // Log MFA validation errors
            AuditLogger.logError("MFA_VALIDATION_ERROR", e.getMessage(), username);
            return Result.err("Failed to validate MFA code: " + e.getMessage());
        }
    }

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
                    // Log MFA cleanup operations for audit trail
                    AuditLogger.log("SYSTEM", "MFA_CLEANUP", "success", null,
                                  deleted + " expired MFA codes deleted");
                }

                return Result.ok(deleted);
            }

        } catch (SQLException e) {
            return Result.err("Failed to cleanup MFA codes: " + e.getMessage());
        }
    }
}

//mailhog