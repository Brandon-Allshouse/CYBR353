package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.models.User;
import com.delivery.security.AuditLogger;
import com.delivery.security.SecurityLevel;
import com.delivery.session.SessionManager;
import com.delivery.util.PasswordUtil;

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
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);
        String username = parsed.get("username");
        String password = parsed.get("password");

        if (username == null || password == null) {
            AuditLogger.log(username == null ? "<unknown>" : username, "LOGIN", "-", "MISSING_CREDENTIALS");
            respondJson(exchange, 400, "{\"error\":\"username and password required\"}");
            return;
        }

        try (Connection c = DatabaseConnection.getConnection()) {
            String sql = "SELECT id, password_hash, salt, role, clearance FROM users WHERE username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        AuditLogger.log(username, "LOGIN", "-", "FAILED_USER_NOT_FOUND");
                        respondJson(exchange, 401, "{\"error\":\"invalid credentials\"}");
                        return;
                    }
                    int id = rs.getInt("id");
                    String hash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    String role = rs.getString("role");
                    String clearanceStr = rs.getString("clearance");

                    String computed = PasswordUtil.hashPassword(password, salt);
                    if (!computed.equalsIgnoreCase(hash)) {
                        AuditLogger.log(username, "LOGIN", "-", "FAILED_BAD_PASSWORD");
                        respondJson(exchange, 401, "{\"error\":\"invalid credentials\"}");
                        return;
                    }

                    SecurityLevel clearance = SecurityLevel.fromString(clearanceStr);
                    User user = new User(id, username, role, clearance);

                    String token = SessionManager.createSession(user.getUsername(), user.getRole(), user.getClearance());
                    AuditLogger.log(username, "LOGIN", "-", "SUCCESS");

                    String response = String.format("{\"session_token\":\"%s\",\"role\":\"%s\",\"clearance\":\"%s\"}", token, user.getRole(), user.getClearance().name());
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    // Optionally set cookie
                    exchange.getResponseHeaders().add("Set-Cookie", "SESSION=" + token + "; Path=/; HttpOnly");
                    respondJson(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            AuditLogger.log(username, "LOGIN", "-", "ERROR_DB");
            respondJson(exchange, 500, "{\"error\":\"internal error\"}");
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
