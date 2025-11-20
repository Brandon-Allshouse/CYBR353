package com.delivery.controllers;

import com.delivery.services.TransferService;
import com.delivery.session.SessionManager;
import com.delivery.util.Result;
import com.delivery.database.DatabaseConnection;

import com.sun.net.httpserver.HttpExchange;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TransferController - HTTP endpoints for package transfers between facilities
 * Handles initiate transfer, complete transfer, and view transfers
 */
public class TransferController {

    /**
     * POST /api/transfers/initiate
     * Initiate a package transfer from one facility to another
     *
     * Request body: {
     *   "packageId": 123,
     *   "fromFacilityId": 1,
     *   "toFacilityId": 2
     * }
     */
    public static void handleInitiateTransfer(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        // Verify session
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Get userId from username
        Result<Long, String> userIdResult = getUserIdFromUsername(session.username);
        if (userIdResult.isErr()) {
            respondJson(exchange, 500, "{\"error\":\"" + userIdResult.unwrapErr() + "\"}");
            return;
        }
        long userId = userIdResult.unwrap();

        // Parse request body
        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);

        long packageId;
        long fromFacilityId;
        long toFacilityId;

        try {
            packageId = Long.parseLong(parsed.getOrDefault("packageId", "0"));
            fromFacilityId = Long.parseLong(parsed.getOrDefault("fromFacilityId", "0"));
            toFacilityId = Long.parseLong(parsed.getOrDefault("toFacilityId", "0"));
        } catch (NumberFormatException e) {
            respondJson(exchange, 400, "{\"error\":\"invalid input - IDs must be numbers\"}");
            return;
        }

        // Call service layer
        Result<Long, String> result = TransferService.initiateTransfer(
            packageId, fromFacilityId, toFacilityId,
            userId, session.username, session.clearance,
            clientIp
        );

        if (result.isOk()) {
            long transferId = result.unwrap();
            String response = String.format(
                "{\"success\":true,\"transferId\":%d,\"message\":\"Transfer initiated successfully\"}",
                transferId
            );
            respondJson(exchange, 201, response);
        } else {
            String errorMsg = escapeJson(result.unwrapErr());
            String response = String.format("{\"error\":\"%s\"}", errorMsg);

            // Determine status code based on error message
            int statusCode = 500;
            if (errorMsg.contains("Access denied") || errorMsg.contains("Insufficient clearance")) {
                statusCode = 403;
            } else if (errorMsg.contains("not found") || errorMsg.contains("Invalid")) {
                statusCode = 400;
            }

            respondJson(exchange, statusCode, response);
        }
    }

    /**
     * PUT /api/transfers/complete/:transferId
     * Complete a pending transfer
     */
    public static void handleCompleteTransfer(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "PUT, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        // Verify session
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Extract transfer ID from path: /api/transfers/complete/123
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length < 5) {
            respondJson(exchange, 400, "{\"error\":\"transfer ID required in path\"}");
            return;
        }

        long transferId;
        try {
            transferId = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            respondJson(exchange, 400, "{\"error\":\"invalid transfer ID\"}");
            return;
        }

        // Call service layer
        Result<String, String> result = TransferService.completeTransfer(
            transferId, 0, session.username, session.clearance, clientIp
        );

        if (result.isOk()) {
            String response = String.format(
                "{\"success\":true,\"message\":\"%s\"}",
                escapeJson(result.unwrap())
            );
            respondJson(exchange, 200, response);
        } else {
            String errorMsg = escapeJson(result.unwrapErr());
            String response = String.format("{\"error\":\"%s\"}", errorMsg);

            int statusCode = 500;
            if (errorMsg.contains("Access denied") || errorMsg.contains("Insufficient clearance")) {
                statusCode = 403;
            } else if (errorMsg.contains("not found") || errorMsg.contains("already completed") ||
                       errorMsg.contains("cancelled")) {
                statusCode = 400;
            }

            respondJson(exchange, statusCode, response);
        }
    }

    /**
     * GET /api/transfers/pending
     * Get all pending transfers
     */
    public static void handleGetPendingTransfers(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        // Verify session
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Call service layer
        Result<List<Map<String, Object>>, String> result = TransferService.getPendingTransfers(
            0, session.username, session.clearance, clientIp
        );

        if (result.isOk()) {
            List<Map<String, Object>> transfers = result.unwrap();

            // Build JSON response manually
            StringBuilder json = new StringBuilder("{\"transfers\":[");
            boolean first = true;

            for (Map<String, Object> transfer : transfers) {
                if (!first) json.append(",");
                first = false;

                json.append("{");
                json.append("\"transferId\":").append(transfer.get("transferId")).append(",");
                json.append("\"packageId\":").append(transfer.get("packageId")).append(",");
                json.append("\"trackingNumber\":\"").append(escapeJson((String)transfer.get("trackingNumber"))).append("\",");
                json.append("\"fromFacility\":\"").append(escapeJson((String)transfer.get("fromFacility"))).append("\",");
                json.append("\"toFacility\":\"").append(escapeJson((String)transfer.get("toFacility"))).append("\",");
                json.append("\"status\":\"").append(escapeJson((String)transfer.get("status"))).append("\",");
                json.append("\"initiatedBy\":\"").append(escapeJson((String)transfer.get("initiatedBy"))).append("\",");
                json.append("\"initiatedAt\":\"").append(escapeJson((String)transfer.get("initiatedAt"))).append("\"");
                json.append("}");
            }

            json.append("]}");
            respondJson(exchange, 200, json.toString());
        } else {
            String errorMsg = escapeJson(result.unwrapErr());
            String response = String.format("{\"error\":\"%s\"}", errorMsg);

            int statusCode = errorMsg.contains("Access denied") ? 403 : 500;
            respondJson(exchange, statusCode, response);
        }
    }

    /**
     * GET /api/transfers/tracking/:trackingNumber
     * Get transfer details by tracking number
     */
    public static void handleGetTransferByTracking(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        // Verify session
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Extract tracking number from path
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length < 5) {
            respondJson(exchange, 400, "{\"error\":\"tracking number required in path\"}");
            return;
        }

        String trackingNumber = parts[4];

        // Call service layer
        Result<Map<String, Object>, String> result = TransferService.getTransferByTracking(
            trackingNumber, 0, session.username, session.clearance, clientIp
        );

        if (result.isOk()) {
            Map<String, Object> transfer = result.unwrap();

            // Build JSON response
            StringBuilder json = new StringBuilder("{");
            json.append("\"transferId\":").append(transfer.get("transferId")).append(",");
            json.append("\"packageId\":").append(transfer.get("packageId")).append(",");
            json.append("\"trackingNumber\":\"").append(escapeJson((String)transfer.get("trackingNumber"))).append("\",");
            json.append("\"fromFacilityId\":").append(transfer.get("fromFacilityId")).append(",");
            json.append("\"fromFacility\":\"").append(escapeJson((String)transfer.get("fromFacility"))).append("\",");
            json.append("\"toFacilityId\":").append(transfer.get("toFacilityId")).append(",");
            json.append("\"toFacility\":\"").append(escapeJson((String)transfer.get("toFacility"))).append("\",");
            json.append("\"status\":\"").append(escapeJson((String)transfer.get("status"))).append("\",");
            json.append("\"currentFacilityId\":").append(transfer.get("currentFacilityId")).append(",");
            json.append("\"initiatedAt\":\"").append(escapeJson((String)transfer.get("initiatedAt"))).append("\"");

            if (transfer.containsKey("completedAt")) {
                json.append(",\"completedAt\":\"").append(escapeJson((String)transfer.get("completedAt"))).append("\"");
            }

            json.append("}");
            respondJson(exchange, 200, json.toString());
        } else {
            String errorMsg = escapeJson(result.unwrapErr());
            String response = String.format("{\"error\":\"%s\"}", errorMsg);

            int statusCode = 500;
            if (errorMsg.contains("Access denied")) {
                statusCode = 403;
            } else if (errorMsg.contains("not found") || errorMsg.contains("Invalid")) {
                statusCode = 404;
            }

            respondJson(exchange, statusCode, response);
        }
    }

    // ===== Helper Methods =====

    private static Result<SessionManager.Session, String> getSessionFromRequest(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return Result.err("No session cookie");
        }

        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            cookie = cookie.trim();
            if (cookie.startsWith("SESSION=")) {
                String token = cookie.substring("SESSION=".length());
                return SessionManager.getSession(token);
            }
        }

        return Result.err("Session token not found");
    }

    private static Result<Long, String> getUserIdFromUsername(String username) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        try (Connection conn = connResult.unwrap()) {
            String sql = "SELECT user_id FROM users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Result.ok(rs.getLong("user_id"));
                    } else {
                        return Result.err("User not found");
                    }
                }
            }
        } catch (Exception e) {
            return Result.err("Database error: " + e.getMessage());
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

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
