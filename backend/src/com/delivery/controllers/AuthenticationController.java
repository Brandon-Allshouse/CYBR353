package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.models.User;
import com.delivery.security.AuditLogger;
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
        // Add CORS headers for frontend file:// protocol support
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        // Handle preflight OPTIONS request
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

        if (username == null || password == null) {
            AuditLogger.log(username == null ? "<unknown>" : username, "LOGIN", "error", "Missing credentials");
            respondJson(exchange, 400, "{\"message\":\"username and password required\"}");
            return;
        }

        // Get database connection using Result pattern
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(username, "LOGIN", "error", "Database connection failed");
            respondJson(exchange, 500, "{\"message\":\"internal error\"}");
            return;
        }

        Connection c = connResult.unwrap();

        try {
            // Fixed SQL: user_id (not id), clearance_level (not clearance)
            String sql = "SELECT user_id, password_hash, salt, role, clearance_level FROM users WHERE username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        AuditLogger.log(username, "LOGIN", "denied", "User not found");
                        respondJson(exchange, 401, "{\"message\":\"invalid credentials\"}");
                        return;
                    }
                    int id = rs.getInt("user_id");
                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    String role = rs.getString("role");
                    int clearanceLevel = rs.getInt("clearance_level");

                    // Unwrap Result from PasswordUtil.hashPassword
                    Result<String, String> hashResult = PasswordUtil.hashPassword(password, salt);
                    if (hashResult.isErr()) {
                        System.err.println("Password hashing error: " + hashResult.unwrapErr());
                        AuditLogger.log(username, "LOGIN", "error", "Password hashing failed");
                        respondJson(exchange, 500, "{\"message\":\"internal error\"}");
                        return;
                    }

                    String computed = hashResult.unwrap();
                    if (!computed.equalsIgnoreCase(hash)) {
                        AuditLogger.log(username, "LOGIN", "denied", "Invalid password");
                        respondJson(exchange, 401, "{\"message\":\"invalid credentials\"}");
                        return;
                    }

                    // Use fromInt() instead of fromString() for clearance_level (TINYINT 0-3)
                    Result<SecurityLevel, String> clearanceResult = SecurityLevel.fromInt(clearanceLevel);
                    if (clearanceResult.isErr()) {
                        System.err.println("Invalid clearance level: " + clearanceResult.unwrapErr());
                        AuditLogger.log(username, "LOGIN", "error", "Invalid clearance level");
                        respondJson(exchange, 500, "{\"message\":\"internal error\"}");
                        return;
                    }

                    SecurityLevel clearance = clearanceResult.unwrap();
                    User user = new User(id, username, role, clearance);

                    String token = SessionManager.createSession(user.getUsername(), user.getRole(), user.getClearance());
                    AuditLogger.log(username, "LOGIN", "SUCCESS", "Role: " + role + ", Clearance: " + clearanceLevel);

                    // Fixed JSON format to match frontend expectations
                    // Frontend expects: {username, role, clearanceLevel (number), token}
                    String response = String.format(
                        "{\"username\":\"%s\",\"role\":\"%s\",\"clearanceLevel\":%d,\"token\":\"%s\"}",
                        user.getUsername(),
                        user.getRole(),
                        clearanceLevel,
                        token
                    );
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    // Optionally set cookie
                    exchange.getResponseHeaders().add("Set-Cookie", "SESSION=" + token + "; Path=/; HttpOnly");
                    respondJson(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(username, "LOGIN", "error", "Database error");
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

    // Simple JSON parser for username/password
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
