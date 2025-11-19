package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.security.SecurityManager;
import com.delivery.security.SecurityManager.AuditLogger;
import com.delivery.security.SecurityManager.InputSanitizer;
import com.delivery.session.SessionManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

// PackageController - handles package tracking and management
public class PackageController {

    // Handle track package request
    public static void handleTrackPackage(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // CORS headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Parse query parameters
        String query = exchange.getRequestURI().getQuery();
        String trackingNumber = null;

        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "trackingNumber".equals(keyValue[0])) {
                    trackingNumber = keyValue[1];
                }
            }
        }

        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            AuditLogger.log(null, null, "TRACK_PACKAGE", "denied", clientIp,
                "Missing tracking number");
            respondJson(exchange, 400, "{\"error\":\"trackingNumber parameter is required\"}");
            return;
        }

        // Sanitize tracking number
        SecurityManager.Result<String, String> sanitizeResult = InputSanitizer.sanitizeString(trackingNumber);
        if (sanitizeResult.isErr()) {
            AuditLogger.log(null, null, "TRACK_PACKAGE", "error", clientIp,
                "Invalid tracking number format");
            respondJson(exchange, 400, "{\"error\":\"Invalid tracking number format\"}");
            return;
        }

        String sanitizedTrackingNumber = sanitizeResult.unwrap();

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, null, "TRACK_PACKAGE", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Query package information
            String packageQuery =
                "SELECT p.package_id, p.tracking_number, p.package_status, p.weight_kg, " +
                "       p.length_cm, p.width_cm, p.height_cm, p.fragile, p.signature_required, " +
                "       p.created_at, p.delivered_at, " +
                "       o.order_id, o.order_status, o.estimated_delivery, " +
                "       pickup.street_address as pickup_address, pickup.city as pickup_city, " +
                "       pickup.state as pickup_state, pickup.zip_code as pickup_zip, " +
                "       delivery.street_address as delivery_address, delivery.city as delivery_city, " +
                "       delivery.state as delivery_state, delivery.zip_code as delivery_zip, " +
                "       f.facility_name as current_facility " +
                "FROM packages p " +
                "JOIN orders o ON p.order_id = o.order_id " +
                "LEFT JOIN addresses pickup ON o.pickup_address_id = pickup.address_id " +
                "LEFT JOIN addresses delivery ON o.delivery_address_id = delivery.address_id " +
                "LEFT JOIN facilities f ON p.current_facility_id = f.facility_id " +
                "WHERE p.tracking_number = ?";

            Map<String, Object> packageData = null;
            long packageId = -1;

            try (PreparedStatement packageStmt = conn.prepareStatement(packageQuery)) {
                packageStmt.setString(1, sanitizedTrackingNumber);

                try (ResultSet rs = packageStmt.executeQuery()) {
                    if (rs.next()) {
                        packageData = new HashMap<>();
                        packageId = rs.getLong("package_id");
                        packageData.put("packageId", packageId);
                        packageData.put("trackingNumber", rs.getString("tracking_number"));
                        packageData.put("status", rs.getString("package_status"));
                        packageData.put("weightKg", rs.getDouble("weight_kg"));
                        packageData.put("lengthCm", rs.getDouble("length_cm"));
                        packageData.put("widthCm", rs.getDouble("width_cm"));
                        packageData.put("heightCm", rs.getDouble("height_cm"));
                        packageData.put("fragile", rs.getBoolean("fragile"));
                        packageData.put("signatureRequired", rs.getBoolean("signature_required"));
                        packageData.put("createdAt", rs.getTimestamp("created_at"));
                        packageData.put("deliveredAt", rs.getTimestamp("delivered_at"));
                        packageData.put("orderId", rs.getLong("order_id"));
                        packageData.put("orderStatus", rs.getString("order_status"));
                        packageData.put("estimatedDelivery", rs.getTimestamp("estimated_delivery"));
                        packageData.put("pickupAddress", rs.getString("pickup_address"));
                        packageData.put("pickupCity", rs.getString("pickup_city"));
                        packageData.put("pickupState", rs.getString("pickup_state"));
                        packageData.put("pickupZip", rs.getString("pickup_zip"));
                        packageData.put("deliveryAddress", rs.getString("delivery_address"));
                        packageData.put("deliveryCity", rs.getString("delivery_city"));
                        packageData.put("deliveryState", rs.getString("delivery_state"));
                        packageData.put("deliveryZip", rs.getString("delivery_zip"));
                        packageData.put("currentFacility", rs.getString("current_facility"));
                    }
                }
            }

            if (packageData == null) {
                AuditLogger.log(null, null, "TRACK_PACKAGE", "denied", clientIp,
                    "Package not found: " + sanitizedTrackingNumber);
                respondJson(exchange, 404, "{\"error\":\"Package not found\"}");
                return;
            }

            // Query delivery status history
            String historyQuery =
                "SELECT status, location, notes, timestamp, u.full_name as updated_by_name " +
                "FROM delivery_status_history h " +
                "LEFT JOIN users u ON h.updated_by = u.user_id " +
                "WHERE h.package_id = ? " +
                "ORDER BY h.timestamp DESC " +
                "LIMIT 50";

            List<Map<String, Object>> history = new ArrayList<>();

            try (PreparedStatement historyStmt = conn.prepareStatement(historyQuery)) {
                historyStmt.setLong(1, packageId);

                try (ResultSet historyRs = historyStmt.executeQuery()) {
                    while (historyRs.next()) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("status", historyRs.getString("status"));
                        event.put("location", historyRs.getString("location"));
                        event.put("notes", historyRs.getString("notes"));
                        event.put("timestamp", historyRs.getTimestamp("timestamp"));
                        event.put("updatedBy", historyRs.getString("updated_by_name"));
                        history.add(event);
                    }
                }
            }

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{\"package\":{");
            json.append("\"packageId\":").append(packageData.get("packageId")).append(",");
            json.append("\"trackingNumber\":\"").append(escapeJson((String)packageData.get("trackingNumber"))).append("\",");
            json.append("\"status\":\"").append(escapeJson((String)packageData.get("status"))).append("\",");
            json.append("\"weightKg\":").append(packageData.get("weightKg")).append(",");
            json.append("\"dimensions\":{");
            json.append("\"lengthCm\":").append(packageData.get("lengthCm")).append(",");
            json.append("\"widthCm\":").append(packageData.get("widthCm")).append(",");
            json.append("\"heightCm\":").append(packageData.get("heightCm"));
            json.append("},");
            json.append("\"fragile\":").append(packageData.get("fragile")).append(",");
            json.append("\"signatureRequired\":").append(packageData.get("signatureRequired")).append(",");
            json.append("\"createdAt\":\"").append(packageData.get("createdAt").toString()).append("\",");
            json.append("\"deliveredAt\":").append(packageData.get("deliveredAt") != null ? "\"" + packageData.get("deliveredAt").toString() + "\"" : "null").append(",");
            json.append("\"orderId\":").append(packageData.get("orderId")).append(",");
            json.append("\"orderStatus\":\"").append(escapeJson((String)packageData.get("orderStatus"))).append("\",");
            json.append("\"estimatedDelivery\":").append(packageData.get("estimatedDelivery") != null ? "\"" + packageData.get("estimatedDelivery").toString() + "\"" : "null").append(",");
            json.append("\"currentFacility\":").append(packageData.get("currentFacility") != null ? "\"" + escapeJson((String)packageData.get("currentFacility")) + "\"" : "null").append(",");
            json.append("\"pickup\":{");
            json.append("\"address\":\"").append(escapeJson((String)packageData.get("pickupAddress"))).append("\",");
            json.append("\"city\":\"").append(escapeJson((String)packageData.get("pickupCity"))).append("\",");
            json.append("\"state\":\"").append(escapeJson((String)packageData.get("pickupState"))).append("\",");
            json.append("\"zipCode\":\"").append(escapeJson((String)packageData.get("pickupZip"))).append("\"");
            json.append("},");
            json.append("\"delivery\":{");
            json.append("\"address\":\"").append(escapeJson((String)packageData.get("deliveryAddress"))).append("\",");
            json.append("\"city\":\"").append(escapeJson((String)packageData.get("deliveryCity"))).append("\",");
            json.append("\"state\":\"").append(escapeJson((String)packageData.get("deliveryState"))).append("\",");
            json.append("\"zipCode\":\"").append(escapeJson((String)packageData.get("deliveryZip"))).append("\"");
            json.append("},");
            json.append("\"history\":[");

            for (int i = 0; i < history.size(); i++) {
                Map<String, Object> event = history.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"status\":\"").append(escapeJson((String)event.get("status"))).append("\",");
                json.append("\"location\":").append(event.get("location") != null ? "\"" + escapeJson((String)event.get("location")) + "\"" : "null").append(",");
                json.append("\"notes\":").append(event.get("notes") != null ? "\"" + escapeJson((String)event.get("notes")) + "\"" : "null").append(",");
                json.append("\"timestamp\":\"").append(event.get("timestamp").toString()).append("\",");
                json.append("\"updatedBy\":").append(event.get("updatedBy") != null ? "\"" + escapeJson((String)event.get("updatedBy")) + "\"" : "null");
                json.append("}");
            }

            json.append("]}}");

            AuditLogger.log(null, null, "TRACK_PACKAGE", "success", clientIp,
                "Tracked package: " + sanitizedTrackingNumber);

            respondJson(exchange, 200, json.toString());

        } catch (SQLException e) {
            System.err.println("SQL error during package tracking: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, null, "TRACK_PACKAGE", "error", clientIp,
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

    // POST /package/edit
    public static void handleEditPackage(HttpExchange exchange) throws IOException {
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
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Get session token
        String token = extractToken(exchange);

        // Validate session
        Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, null, "EDIT_PACKAGE", "denied", clientIp,
                "Session validation failed: " + sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Verify user has manager or admin role (required for package edits)
        if (!"manager".equals(session.role) && !"admin".equals(session.role)) {
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "denied", clientIp,
                "Access denied - requires manager or admin role");
            respondJson(exchange, 403, "{\"error\":\"Forbidden - Manager or admin access required\"}");
            return;
        }

        // Parse request body
        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);

        String packageIdStr = parsed.get("packageId");
        String field = parsed.get("field");
        String newValue = parsed.get("newValue");
        String reason = parsed.get("reason");

        // Validate inputs
        if (packageIdStr == null || field == null || newValue == null) {
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "denied", clientIp,
                "Missing required fields");
            respondJson(exchange, 400,
                "{\"error\":\"packageId, field, and newValue are required\"}");
            return;
        }

        long packageId;
        try {
            packageId = Long.parseLong(packageIdStr);
        } catch (NumberFormatException e) {
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "denied", clientIp,
                "Invalid packageId format");
            respondJson(exchange, 400, "{\"error\":\"Invalid packageId format\"}");
            return;
        }

        // Validate field - only allow specific editable fields
        List<String> allowedFields = List.of("weight_kg", "length_cm", "width_cm", "height_cm",
            "fragile", "signature_required", "package_status");
        if (!allowedFields.contains(field)) {
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "denied", clientIp,
                "Invalid field: " + field);
            respondJson(exchange, 400, "{\"error\":\"Invalid field. Allowed fields: " + String.join(", ", allowedFields) + "\"}");
            return;
        }

        // Sanitize inputs
        SecurityManager.Result<String, String> fieldResult = InputSanitizer.sanitizeString(field);
        SecurityManager.Result<String, String> newValueResult = InputSanitizer.sanitizeString(newValue);
        SecurityManager.Result<String, String> reasonResult = reason != null ?
            InputSanitizer.sanitizeString(reason) : SecurityManager.Result.ok("");

        if (fieldResult.isErr() || newValueResult.isErr() || reasonResult.isErr()) {
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "error", clientIp,
                "Input sanitization failed");
            respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
            return;
        }

        String sanitizedField = fieldResult.unwrap();
        String sanitizedNewValue = newValueResult.unwrap();
        String sanitizedReason = reasonResult.unwrap();

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Get user ID
            long userId = getUserId(conn, session.username);

            // Begin transaction
            conn.setAutoCommit(false);

            try {
                // Get current value and verify package exists
                String selectQuery = "SELECT " + sanitizedField + ", tracking_number FROM packages WHERE package_id = ?";
                String oldValue;
                String trackingNumber;

                try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
                    selectStmt.setLong(1, packageId);

                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            AuditLogger.log(userId, session.username, "EDIT_PACKAGE", "denied", clientIp,
                                "Package not found");
                            respondJson(exchange, 404, "{\"error\":\"Package not found\"}");
                            return;
                        }

                        oldValue = rs.getString(sanitizedField);
                        trackingNumber = rs.getString("tracking_number");
                    }
                }

                // Update the package field
                String updateQuery = "UPDATE packages SET " + sanitizedField + " = ? WHERE package_id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                    // Set value based on field type
                    if (field.equals("fragile") || field.equals("signature_required")) {
                        updateStmt.setBoolean(1, Boolean.parseBoolean(sanitizedNewValue));
                    } else if (field.equals("package_status")) {
                        updateStmt.setString(1, sanitizedNewValue);
                    } else {
                        // Numeric fields
                        updateStmt.setDouble(1, Double.parseDouble(sanitizedNewValue));
                    }
                    updateStmt.setLong(2, packageId);

                    updateStmt.executeUpdate();
                }

                // Record in edit history
                String historyQuery =
                    "INSERT INTO package_edit_history (package_id, edited_by, field_name, old_value, new_value, edit_reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement historyStmt = conn.prepareStatement(historyQuery)) {
                    historyStmt.setLong(1, packageId);
                    historyStmt.setLong(2, userId);
                    historyStmt.setString(3, sanitizedField);
                    historyStmt.setString(4, oldValue);
                    historyStmt.setString(5, sanitizedNewValue);
                    historyStmt.setString(6, sanitizedReason.isEmpty() ? null : sanitizedReason);

                    historyStmt.executeUpdate();
                }

                // Commit transaction
                conn.commit();

                AuditLogger.log(userId, session.username, "EDIT_PACKAGE", "success", clientIp,
                    String.format("Edited package %s (ID: %d): %s changed from '%s' to '%s'",
                        trackingNumber, packageId, sanitizedField, oldValue, sanitizedNewValue));

                String response = String.format(
                    "{\"success\":true,\"message\":\"Package updated successfully\",\"packageId\":%d,\"trackingNumber\":\"%s\",\"field\":\"%s\",\"oldValue\":\"%s\",\"newValue\":\"%s\"}",
                    packageId, escapeJson(trackingNumber), escapeJson(sanitizedField), escapeJson(oldValue), escapeJson(sanitizedNewValue)
                );

                respondJson(exchange, 200, response);

            } catch (SQLException | NumberFormatException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (NumberFormatException e) {
            System.err.println("Invalid value format: " + e.getMessage());
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "error", clientIp,
                "Invalid value format for field");
            respondJson(exchange, 400, "{\"error\":\"Invalid value format for field\"}");
        } catch (SQLException e) {
            System.err.println("SQL error during package edit: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "EDIT_PACKAGE", "error", clientIp,
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

    // Helper methods

    private static String extractToken(HttpExchange exchange) {
        // Try Authorization header first
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fall back to cookie
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "SESSION".equals(parts[0])) {
                    return parts[1];
                }
            }
        }

        return null;
    }

    private static long getUserId(Connection conn, String username) throws SQLException {
        String query = "SELECT user_id FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("user_id");
                }
            }
        }
        return -1;
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
