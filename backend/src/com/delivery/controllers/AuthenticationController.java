package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.models.User;
import com.delivery.security.AuditLogger;
import com.delivery.security.LoginLockout;
import com.delivery.security.RecaptchaVerifier;
import com.delivery.security.SecurityLevel;
import com.delivery.session.SessionManager;
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

public class AuthenticationController {

    public static void handleLogin(HttpExchange exchange) throws IOException {
        // Capture client IP early for comprehensive audit logging (security requirement)
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS wildcard acceptable for public auth endpoints - restrict for sensitive operations
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

        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);
        String username = parsed.get("username");
        String password = parsed.get("password");
        String recaptchaToken = parsed.get("recaptchaToken");

        if (username == null || password == null) {
            AuditLogger.log(null, username == null ? "<unknown>" : username, "LOGIN", "error", clientIp, "Missing credentials");
            respondJson(exchange, 400, "{\"message\":\"username and password required\"}");
            return;
        }

        // Verify reCAPTCHA (bot protection)
        Result<Boolean, String> recaptchaResult = RecaptchaVerifier.verifyRecaptcha(recaptchaToken, clientIp);
        if (recaptchaResult.isErr()) {
            AuditLogger.log(null, username, "LOGIN", "denied", clientIp, "reCAPTCHA verification failed");
            respondJson(exchange, 400, "{\"message\":\"" + recaptchaResult.unwrapErr() + "\"}");
            return;
        }

        // Check if account is locked due to too many failed login attempts
        Result<LoginLockout.LockoutStatus, String> lockoutResult = LoginLockout.isAccountLocked(username, clientIp);
        if (lockoutResult.isOk()) {
            LoginLockout.LockoutStatus status = lockoutResult.unwrap();
            if (status.isLocked) {
                // Account is locked - log with actual user_id and deny access
                AuditLogger.log(status.userId, username, "LOGIN", "denied", clientIp,
                              "Login attempt while account is locked until " + status.lockoutUntil);
                respondJson(exchange, 401, "{\"message\":\"account temporarily locked\"}");
                return;
            }
        }

        // Get database connection using Result pattern
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, username, "LOGIN", "error", clientIp, "Database connection failed");
            respondJson(exchange, 500, "{\"message\":\"internal error\"}");
            return;
        }

        Connection c = connResult.unwrap();

        try {
            // Retrieve all authentication data including BLP clearance_level (0-3) and account_status
            String sql = "SELECT user_id, password_hash, salt, role, clearance_level, account_status FROM users WHERE username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        AuditLogger.log(null, username, "LOGIN", "denied", clientIp, "User not found");
                        respondJson(exchange, 401, "{\"message\":\"invalid credentials\"}");
                        return;
                    }
                    long id = rs.getLong("user_id");
                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    String role = rs.getString("role");
                    int clearanceLevel = rs.getInt("clearance_level");
                    String accountStatus = rs.getString("account_status");

                    // Check if account is suspended or revoked
                    if ("suspended".equals(accountStatus) || "revoked".equals(accountStatus)) {
                        AuditLogger.log(id, username, "LOGIN", "denied", clientIp,
                            "Account is " + accountStatus + " - login denied");
                        respondJson(exchange, 401, "{\"message\":\"account " + accountStatus + "\"}");
                        return;
                    }

                    // Password verification using SHA-256(password + salt) - matches schema.sql generation
                    Result<String, String> hashResult = PasswordUtil.hashPassword(password, salt);
                    if (hashResult.isErr()) {
                        System.err.println("Password hashing error: " + hashResult.unwrapErr());
                        AuditLogger.log(id, username, "LOGIN", "error", clientIp, "Password hashing failed");
                        respondJson(exchange, 500, "{\"message\":\"internal error\"}");
                        return;
                    }

                    String computed = hashResult.unwrap();
                    if (!computed.equalsIgnoreCase(hash)) {
                        // Increment failed attempt counter (will lock account after 3 failures)
                        LoginLockout.recordFailedAttempt(username, clientIp);
                        AuditLogger.log(id, username, "LOGIN", "denied", clientIp, "Invalid password");
                        respondJson(exchange, 401, "{\"message\":\"invalid credentials\"}");
                        return;
                    }

                    // Convert integer clearance (0-3) to SecurityLevel enum for BLP enforcement
                    Result<SecurityLevel, String> clearanceResult = SecurityLevel.fromInt(clearanceLevel);
                    if (clearanceResult.isErr()) {
                        System.err.println("Invalid clearance level: " + clearanceResult.unwrapErr());
                        AuditLogger.log(id, username, "LOGIN", "error", clientIp, "Invalid clearance level");
                        respondJson(exchange, 500, "{\"message\":\"internal error\"}");
                        return;
                    }

                    SecurityLevel clearance = clearanceResult.unwrap();
                    User user = new User((int)id, username, role, clearance);

                    // Reset failed login attempt counter on successful authentication
                    LoginLockout.resetFailedAttempts(id, username, clientIp);

                    // Session token stored in-memory - consider Redis for distributed deployments
                    String token = SessionManager.createSession(user.getUsername(), user.getRole(), user.getClearance());
                    AuditLogger.log(id, username, "LOGIN", "success", clientIp, "Role: " + role + ", Clearance: " + clearanceLevel);

                    String response = String.format(
                        "{\"username\":\"%s\",\"role\":\"%s\",\"clearanceLevel\":%d,\"token\":\"%s\"}",
                        user.getUsername(),
                        user.getRole(),
                        clearanceLevel,
                        token
                    );
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    // HttpOnly cookie prevents XSS attacks from stealing tokens
                    exchange.getResponseHeaders().add("Set-Cookie", "SESSION=" + token + "; Path=/; HttpOnly");
                    respondJson(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, username, "LOGIN", "error", clientIp, "Database error");
            respondJson(exchange, 500, "{\"message\":\"internal error\"}");
        }
    }

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

    // Minimal JSON parser - avoids Jackson/Gson dependency for simple use case
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
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
