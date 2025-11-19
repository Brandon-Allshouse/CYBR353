package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.models.User;
import com.delivery.security.SecurityManager;
import com.delivery.security.SecurityManager.AuditLogger;
import com.delivery.security.SecurityManager.InputSanitizer;
import com.delivery.security.SecurityManager.InputValidator;
import com.delivery.security.SecurityManager.PasswordManager;
import com.delivery.security.SecurityManager.RateLimiter;
import com.delivery.security.SecurityManager.RecaptchaVerifier;
import com.delivery.security.SecurityManager.ValidationResult;
import com.delivery.util.PasswordUtil;
import com.delivery.util.Result;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/**
 * CustomerController - Handles customer-specific operations
 * Following Bell-LaPadula model, new customer accounts are created with:
 * - Role: customer
 * - Clearance Level: 0 (Unclassified)
 */
public class CustomerController {

    /**
     * Handles customer registration (Use Case 1: Create new customer account)
     * Implements security mitigations:
     * - reCAPTCHA v2 verification (prevents bots)
     * - Input validation and sanitization (SQL injection, XSS prevention)
     * - Password strength requirements
     * - Duplicate account detection
     * - Comprehensive audit logging
     *
     * POST /customer/register
     * Request body: { name, email, phone, password, recaptchaToken }
     */
    public static void handleRegistration(HttpExchange exchange) throws IOException {
        // Capture client IP for security logging and reCAPTCHA verification
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS headers for browser-based clients
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Parse request body
        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);

        String name = parsed.get("name");
        String email = parsed.get("email");
        String phone = parsed.get("phone");
        String password = parsed.get("password");
        String recaptchaToken = parsed.get("recaptchaToken");

        // Verify reCAPTCHA (bot protection)
        SecurityManager.Result<Boolean, String> recaptchaResult = RecaptchaVerifier.verifyRecaptcha(recaptchaToken, clientIp);
        if (recaptchaResult.isErr()) {
            AuditLogger.log(null, email, "REGISTRATION_ATTEMPT", "denied", clientIp,
                "reCAPTCHA verification failed");
            respondJson(exchange, 400, "{\"error\":\"" + recaptchaResult.unwrapErr() + "\"}");
            return;
        }

        // Validate all inputs using existing validators
        ValidationResult validation = InputValidator.validateRegistration(name, email, phone, password);
        if (!validation.isValid()) {
            String errors = String.join(", ", validation.getErrors());
            AuditLogger.log(null, email, "REGISTRATION_ATTEMPT", "denied", clientIp,
                "Input validation failed: " + errors);
            respondJson(exchange, 400, "{\"error\":\"" + escapeJson(errors) + "\"}");
            return;
        }

        // Sanitize inputs (additional XSS protection)
        SecurityManager.Result<String, String> nameResult = InputSanitizer.sanitizeString(name);
        SecurityManager.Result<String, String> emailResult = InputSanitizer.sanitizeString(email);
        SecurityManager.Result<String, String> phoneResult = InputSanitizer.sanitizeString(phone);

        if (nameResult.isErr() || emailResult.isErr() || phoneResult.isErr()) {
            AuditLogger.log(null, email, "REGISTRATION_ATTEMPT", "error", clientIp,
                "Input sanitization failed");
            respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
            return;
        }

        String sanitizedName = nameResult.unwrap();
        String sanitizedEmail = emailResult.unwrap();
        String sanitizedPhone = phoneResult.unwrap();

        // Use email as username for customers (simpler UX)
        String username = sanitizedEmail;

        // Check rate limiting (prevent registration abuse)
        SecurityManager.Result<Boolean, String> rateLimitResult = RateLimiter.allowRequest(clientIp, "REGISTER", 5);
        if (rateLimitResult.isErr()) {
            AuditLogger.log(null, username, "REGISTRATION_ATTEMPT", "denied", clientIp,
                rateLimitResult.unwrapErr());
            respondJson(exchange, 429, "{\"error\":\"Too many registration attempts. Please try again later.\"}");
            return;
        }

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, username, "REGISTRATION_ATTEMPT", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Check for duplicate username/email
            String checkSql = "SELECT user_id FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, username);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        AuditLogger.log(null, username, "REGISTRATION_ATTEMPT", "denied", clientIp,
                            "Duplicate account - email already registered");
                        respondJson(exchange, 409, "{\"error\":\"An account with this email already exists.\"}");
                        return;
                    }
                }
            }

            // Generate salt and hash password
            String salt = PasswordManager.generateSalt();
            Result<String, String> hashResult = PasswordUtil.hashPassword(password, salt);

            if (hashResult.isErr()) {
                System.err.println("Password hashing error: " + hashResult.unwrapErr());
                AuditLogger.log(null, username, "REGISTRATION_ATTEMPT", "error", clientIp,
                    "Password hashing failed");
                respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
                return;
            }

            String passwordHash = hashResult.unwrap();

            // Insert new customer user
            // BLP Model: All customers start with clearance_level = 0 (Unclassified)
            String insertSql = "INSERT INTO users (username, password_hash, salt, email, phone, full_name, role, clearance_level) " +
                             "VALUES (?, ?, ?, ?, ?, ?, 'customer', 0)";

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql,
                    PreparedStatement.RETURN_GENERATED_KEYS)) {

                insertStmt.setString(1, username);
                insertStmt.setString(2, passwordHash);
                insertStmt.setString(3, salt);
                insertStmt.setString(4, sanitizedEmail);
                insertStmt.setString(5, sanitizedPhone);
                insertStmt.setString(6, sanitizedName);

                int rowsAffected = insertStmt.executeUpdate();

                if (rowsAffected == 0) {
                    AuditLogger.log(null, username, "REGISTRATION_ATTEMPT", "error", clientIp,
                        "Failed to insert user into database");
                    respondJson(exchange, 500, "{\"error\":\"Registration failed. Please try again.\"}");
                    return;
                }

                // Get generated user_id
                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long userId = generatedKeys.getLong(1);

                        // Log successful registration
                        AuditLogger.log(userId, username, "REGISTRATION", "success", clientIp,
                            String.format("New customer account created - Name: %s, Phone: %s, Clearance: 0 (Unclassified)",
                                sanitizedName, sanitizedPhone));

                        // Return success response
                        String response = String.format(
                            "{\"success\":true,\"userId\":%d,\"username\":\"%s\",\"message\":\"Account created successfully. You can now log in.\"}",
                            userId, escapeJson(username)
                        );

                        respondJson(exchange, 201, response);
                        return;
                    }
                }

                // Fallback if generated keys not available
                AuditLogger.log(null, username, "REGISTRATION", "success", clientIp,
                    "New customer account created (ID unknown)");
                respondJson(exchange, 201,
                    "{\"success\":true,\"message\":\"Account created successfully. You can now log in.\"}");
            }

        } catch (SQLException e) {
            System.err.println("SQL error during registration: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, username, "REGISTRATION_ATTEMPT", "error", clientIp,
                "Database error: " + e.getMessage());
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    // Helper methods (copied from AuthenticationController pattern)

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static Map<String, String> parseJson(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null) return map;
        s = s.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length()-1);
        String[] parts = s.split(",");
        for (String p : parts) {
            int idx = p.indexOf(":");
            if (idx <= 0) continue;
            String k = p.substring(0, idx).trim().replaceAll("\"", "");
            String v = p.substring(idx+1).trim().replaceAll("\"", "");
            map.put(k, v);
        }
        return map;
    }

    private static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Escapes special characters in strings for JSON output
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
