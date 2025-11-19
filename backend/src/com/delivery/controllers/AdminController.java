package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.session.SessionManager;
import com.delivery.util.Result;
import com.delivery.security.SecurityManager;
import static com.delivery.security.SecurityManager.AuditLogger;
import static com.delivery.security.SecurityManager.BLPAccessControl;
import static com.delivery.security.SecurityManager.SecurityLevel;

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

// AdminController - administrative operations (audit logs, user management)
public class AdminController {

    // handleGetLogs - GET /admin/logs
    public static void handleGetLogs(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, "UNKNOWN", "ADMIN_LOGS_ACCESS", "denied", clientIp,
                "Unauthorized access attempt - no valid session");
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        if (!BLPAccessControl.checkReadAccess(session.clearance, SecurityLevel.TOP_SECRET)) {
            AuditLogger.log(null, session.username, "ADMIN_LOGS_ACCESS", "denied", clientIp,
                "BLP violation: " + session.clearance + " attempted to read TOP_SECRET audit logs");
            respondJson(exchange, 403, "{\"error\":\"insufficient clearance\"}");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        int limit = 100;
        int offset = 0;

        if (query != null) {
            Map<String, String> params = parseQueryString(query);
            if (params.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(params.get("limit"));
                    if (limit > 1000) limit = 1000;
                } catch (NumberFormatException ignored) {}
            }
            if (params.containsKey("offset")) {
                try {
                    offset = Integer.parseInt(params.get("offset"));
                } catch (NumberFormatException ignored) {}
            }
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, session.username, "ADMIN_LOGS_ACCESS", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
            return;
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "SELECT audit_id, timestamp, user_id, username, action, result, ip_address, details " +
                        "FROM audit_log ORDER BY timestamp DESC LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    StringBuilder json = new StringBuilder("{\"logs\":[");
                    boolean first = true;

                    while (rs.next()) {
                        if (!first) json.append(",");
                        first = false;

                        json.append("{");
                        json.append("\"audit_id\":").append(rs.getLong("audit_id")).append(",");
                        json.append("\"timestamp\":\"").append(escapeJson(rs.getTimestamp("timestamp").toString())).append("\",");

                        Object userId = rs.getObject("user_id");
                        if (userId != null) {
                            json.append("\"user_id\":").append(userId).append(",");
                        } else {
                            json.append("\"user_id\":null,");
                        }

                        json.append("\"username\":\"").append(escapeJson(rs.getString("username"))).append("\",");
                        json.append("\"action\":\"").append(escapeJson(rs.getString("action"))).append("\",");
                        json.append("\"result\":\"").append(escapeJson(rs.getString("result"))).append("\",");

                        String ipAddress = rs.getString("ip_address");
                        if (ipAddress != null) {
                            json.append("\"ip_address\":\"").append(escapeJson(ipAddress)).append("\",");
                        } else {
                            json.append("\"ip_address\":null,");
                        }

                        String details = rs.getString("details");
                        if (details != null) {
                            json.append("\"details\":\"").append(escapeJson(details)).append("\"");
                        } else {
                            json.append("\"details\":null");
                        }

                        json.append("}");
                    }

                    json.append("]}");

                    AuditLogger.log(null, session.username, "ADMIN_LOGS_VIEWED", "success", clientIp,
                        "Audit logs accessed (limit: " + limit + ", offset: " + offset + ")");

                    respondJson(exchange, 200, json.toString());
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "ADMIN_LOGS_ACCESS", "error", clientIp,
                "Database error: " + e.getMessage());
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        }
    }

    // GET /admin/users
    public static void handleGetUsers(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, "UNKNOWN", "ADMIN_USERS_ACCESS", "denied", clientIp,
                "Unauthorized access attempt");
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        if (session.clearance != SecurityLevel.TOP_SECRET) {
            AuditLogger.log(null, session.username, "ADMIN_USERS_ACCESS", "denied", clientIp,
                "BLP violation: " + session.clearance + " attempted admin user access");
            respondJson(exchange, 403, "{\"error\":\"insufficient clearance\"}");
            return;
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            AuditLogger.log(null, session.username, "ADMIN_USERS_ACCESS", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
            return;
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "SELECT user_id, username, email, phone, full_name, role, " +
                        "clearance_level, account_status, created_at, failed_attempts " +
                        "FROM users ORDER BY created_at DESC";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                StringBuilder json = new StringBuilder("{\"users\":[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("{");
                    json.append("\"user_id\":").append(rs.getLong("user_id")).append(",");
                    json.append("\"username\":\"").append(escapeJson(rs.getString("username"))).append("\",");

                    String email = rs.getString("email");
                    json.append("\"email\":").append(email != null ? "\"" + escapeJson(email) + "\"" : "null").append(",");

                    String phone = rs.getString("phone");
                    json.append("\"phone\":").append(phone != null ? "\"" + escapeJson(phone) + "\"" : "null").append(",");

                    String fullName = rs.getString("full_name");
                    json.append("\"full_name\":").append(fullName != null ? "\"" + escapeJson(fullName) + "\"" : "null").append(",");

                    json.append("\"role\":\"").append(escapeJson(rs.getString("role"))).append("\",");
                    json.append("\"clearance_level\":").append(rs.getInt("clearance_level")).append(",");
                    json.append("\"account_status\":\"").append(escapeJson(rs.getString("account_status"))).append("\",");
                    json.append("\"created_at\":\"").append(escapeJson(rs.getTimestamp("created_at").toString())).append("\",");
                    json.append("\"failed_attempts\":").append(rs.getInt("failed_attempts"));
                    json.append("}");
                }

                json.append("]}");

                AuditLogger.log(null, session.username, "ADMIN_USERS_LISTED", "success", clientIp,
                    "User list accessed");

                respondJson(exchange, 200, json.toString());
            }
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "ADMIN_USERS_ACCESS", "error", clientIp,
                "Database error");
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        }
    }

    // PUT /admin/users/:userId/role
    public static void handleUpdateUserRole(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        if (session.clearance != SecurityLevel.TOP_SECRET) {
            AuditLogger.log(null, session.username, "ADMIN_ROLE_UPDATE", "denied", clientIp,
                "BLP violation: insufficient clearance");
            respondJson(exchange, 403, "{\"error\":\"insufficient clearance\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 4) {
            respondJson(exchange, 400, "{\"error\":\"invalid request\"}");
            return;
        }

        long targetUserId;
        try {
            targetUserId = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            respondJson(exchange, 400, "{\"error\":\"invalid user id\"}");
            return;
        }

        String body = readStream(exchange.getRequestBody());
        Map<String, String> data = parseJson(body);
        String newRole = data.get("role");

        if (newRole == null || newRole.trim().isEmpty()) {
            respondJson(exchange, 400, "{\"error\":\"role is required\"}");
            return;
        }

        if ("admin".equalsIgnoreCase(newRole)) {
            AuditLogger.log(null, session.username, "ADMIN_ROLE_UPDATE", "denied", clientIp,
                "Attempted to promote user " + targetUserId + " to admin (not allowed)");
            respondJson(exchange, 403, "{\"error\":\"cannot promote users to admin role\"}");
            return;
        }

        if (!newRole.matches("^(customer|driver|manager)$")) {
            respondJson(exchange, 400, "{\"error\":\"invalid role (allowed: customer, driver, manager)\"}");
            return;
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
            return;
        }

        try (Connection conn = connResult.unwrap()) {
            String checkSql = "SELECT role, username, clearance_level FROM users WHERE user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, targetUserId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        respondJson(exchange, 404, "{\"error\":\"user not found\"}");
                        return;
                    }

                    String currentRole = rs.getString("role");
                    String targetUsername = rs.getString("username");

                    if ("admin".equals(currentRole)) {
                        AuditLogger.log(null, session.username, "ADMIN_ROLE_UPDATE", "denied", clientIp,
                            "Attempted to modify admin user " + targetUsername);
                        respondJson(exchange, 403, "{\"error\":\"cannot modify admin accounts\"}");
                        return;
                    }

                    int newClearanceLevel = getClearanceLevelForRole(newRole);

                    String updateSql = "UPDATE users SET role = ?, clearance_level = ? WHERE user_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, newRole);
                        updateStmt.setInt(2, newClearanceLevel);
                        updateStmt.setLong(3, targetUserId);

                        int updated = updateStmt.executeUpdate();

                        if (updated > 0) {
                            AuditLogger.log(null, session.username, "ADMIN_ROLE_UPDATED", "success", clientIp,
                                String.format("User %s (ID: %d) role changed from %s to %s (clearance: %d)",
                                    targetUsername, targetUserId, currentRole, newRole, newClearanceLevel));

                            respondJson(exchange, 200, String.format(
                                "{\"success\":true,\"message\":\"Role updated to %s\",\"clearance_level\":%d}",
                                newRole, newClearanceLevel));
                        } else {
                            respondJson(exchange, 500, "{\"error\":\"update failed\"}");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "ADMIN_ROLE_UPDATE", "error", clientIp,
                "Database error");
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        }
    }

    // handleUpdateUserStatus
    public static void handleUpdateUserStatus(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        if (session.clearance != SecurityLevel.TOP_SECRET) {
            AuditLogger.log(null, session.username, "ADMIN_STATUS_UPDATE", "denied", clientIp,
                "BLP violation: insufficient clearance");
            respondJson(exchange, 403, "{\"error\":\"insufficient clearance\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 4) {
            respondJson(exchange, 400, "{\"error\":\"invalid request\"}");
            return;
        }

        long targetUserId;
        try {
            targetUserId = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            respondJson(exchange, 400, "{\"error\":\"invalid user id\"}");
            return;
        }

        String body = readStream(exchange.getRequestBody());
        Map<String, String> data = parseJson(body);
        String newStatus = data.get("status");

        if (newStatus == null || !newStatus.matches("^(active|suspended|revoked)$")) {
            respondJson(exchange, 400, "{\"error\":\"invalid status (allowed: active, suspended, revoked)\"}");
            return;
        }

        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
            return;
        }

        try (Connection conn = connResult.unwrap()) {
            String checkSql = "SELECT role, username, account_status FROM users WHERE user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, targetUserId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        respondJson(exchange, 404, "{\"error\":\"user not found\"}");
                        return;
                    }

                    String role = rs.getString("role");
                    String targetUsername = rs.getString("username");
                    String currentStatus = rs.getString("account_status");

                    if ("admin".equals(role)) {
                        AuditLogger.log(null, session.username, "ADMIN_STATUS_UPDATE", "denied", clientIp,
                            "Attempted to modify admin user " + targetUsername);
                        respondJson(exchange, 403, "{\"error\":\"cannot modify admin accounts\"}");
                        return;
                    }

                    String updateSql = "UPDATE users SET account_status = ? WHERE user_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, newStatus);
                        updateStmt.setLong(2, targetUserId);

                        int updated = updateStmt.executeUpdate();

                        if (updated > 0) {
                            AuditLogger.log(null, session.username, "ADMIN_STATUS_UPDATED", "success", clientIp,
                                String.format("User %s (ID: %d) status changed from %s to %s",
                                    targetUsername, targetUserId, currentStatus, newStatus));

                            respondJson(exchange, 200, String.format(
                                "{\"success\":true,\"message\":\"Account status updated to %s\"}",
                                newStatus));
                        } else {
                            respondJson(exchange, 500, "{\"error\":\"update failed\"}");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "ADMIN_STATUS_UPDATE", "error", clientIp,
                "Database error");
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        }
    }

    private static int getClearanceLevelForRole(String role) {
        switch (role.toLowerCase()) {
            case "customer": return 0;
            case "driver": return 1;
            case "manager": return 2;
            case "admin": return 3;
            default: return 0;
        }
    }

    private static Result<SessionManager.Session, String> getSessionFromRequest(HttpExchange exchange) {
        String token = null;

        if (exchange.getRequestHeaders().containsKey("Cookie")) {
            String cookies = exchange.getRequestHeaders().getFirst("Cookie");
            for (String c : cookies.split(";")) {
                c = c.trim();
                if (c.startsWith("SESSION=")) {
                    token = c.substring("SESSION=".length());
                    break;
                }
            }
        }

        if (token == null && exchange.getRequestHeaders().containsKey("Authorization")) {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            }
        }

        return SessionManager.getSession(token);
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

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                params.put(key, value);
            }
        }
        return params;
    }

    private static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
