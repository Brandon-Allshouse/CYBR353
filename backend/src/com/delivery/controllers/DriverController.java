package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.models.User;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

// DriverController - endpoints for delivery drivers (routes, status updates)
public class DriverController {

    // GET /driver/route
    public static void handleGetRoute(HttpExchange exchange) throws IOException {
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
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Get session token from cookie or Authorization header
        String token = extractToken(exchange);

        // Validate session
        Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, null, "GET_ROUTE", "denied", clientIp,
                "Session validation failed: " + sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Verify user has driver role
        if (!"driver".equals(session.role)) {
            AuditLogger.log(null, session.username, "GET_ROUTE", "denied", clientIp,
                "Access denied - requires driver role");
            respondJson(exchange, 403, "{\"error\":\"Forbidden - Driver access required\"}");
            return;
        }

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, session.username, "GET_ROUTE", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Get driver's user_id
            long driverId = getUserId(conn, session.username);
            if (driverId == -1) {
                AuditLogger.log(null, session.username, "GET_ROUTE", "error", clientIp,
                    "Driver user not found");
                respondJson(exchange, 404, "{\"error\":\"Driver not found\"}");
                return;
            }

            // Query for today's assigned route
            String routeQuery =
                "SELECT r.route_id, r.route_name, r.route_date, r.estimated_duration_minutes, " +
                "       r.total_stops, r.route_status, ra.vehicle_id, ra.started_at, " +
                "       f.facility_name, f.address AS facility_address " +
                "FROM route_assignments ra " +
                "JOIN routes r ON ra.route_id = r.route_id " +
                "JOIN facilities f ON r.facility_id = f.facility_id " +
                "WHERE ra.driver_id = ? " +
                "  AND r.route_date = CURDATE() " +
                "  AND r.route_status IN ('planned', 'in_progress') " +
                "ORDER BY r.route_date DESC, r.created_at DESC " +
                "LIMIT 1";

            try (PreparedStatement routeStmt = conn.prepareStatement(routeQuery)) {
                routeStmt.setLong(1, driverId);

                try (ResultSet routeRs = routeStmt.executeQuery()) {
                    if (!routeRs.next()) {
                        AuditLogger.log(driverId, session.username, "GET_ROUTE", "success", clientIp,
                            "No route assigned for today");
                        respondJson(exchange, 200,
                            "{\"message\":\"No route assigned for today\",\"route\":null}");
                        return;
                    }

                    long routeId = routeRs.getLong("route_id");
                    String routeName = routeRs.getString("route_name");
                    String routeDate = routeRs.getString("route_date");
                    int estimatedDuration = routeRs.getInt("estimated_duration_minutes");
                    int totalStops = routeRs.getInt("total_stops");
                    String routeStatus = routeRs.getString("route_status");
                    String vehicleId = routeRs.getString("vehicle_id");
                    Timestamp startedAt = routeRs.getTimestamp("started_at");
                    String facilityName = routeRs.getString("facility_name");
                    String facilityAddress = routeRs.getString("facility_address");

                    // Query for packages in this route
                    String packagesQuery =
                        "SELECT p.package_id, p.tracking_number, p.package_status, " +
                        "       p.weight_kg, p.fragile, p.signature_required, " +
                        "       rp.stop_sequence, rp.estimated_arrival, " +
                        "       a.street_address, a.city, a.state, a.zip_code, " +
                        "       a.delivery_instructions, " +
                        "       u.full_name AS customer_name, u.phone AS customer_phone " +
                        "FROM route_packages rp " +
                        "JOIN packages p ON rp.package_id = p.package_id " +
                        "JOIN orders o ON p.order_id = o.order_id " +
                        "JOIN addresses a ON o.delivery_address_id = a.address_id " +
                        "JOIN users u ON o.customer_id = u.user_id " +
                        "WHERE rp.route_id = ? " +
                        "ORDER BY rp.stop_sequence";

                    List<Map<String, Object>> packages = new ArrayList<>();

                    try (PreparedStatement packagesStmt = conn.prepareStatement(packagesQuery)) {
                        packagesStmt.setLong(1, routeId);

                        try (ResultSet packagesRs = packagesStmt.executeQuery()) {
                            while (packagesRs.next()) {
                                Map<String, Object> pkg = new HashMap<>();
                                pkg.put("packageId", packagesRs.getLong("package_id"));
                                pkg.put("trackingNumber", packagesRs.getString("tracking_number"));
                                pkg.put("status", packagesRs.getString("package_status"));
                                pkg.put("weightKg", packagesRs.getDouble("weight_kg"));
                                pkg.put("fragile", packagesRs.getBoolean("fragile"));
                                pkg.put("signatureRequired", packagesRs.getBoolean("signature_required"));
                                pkg.put("stopSequence", packagesRs.getInt("stop_sequence"));
                                pkg.put("estimatedArrival", packagesRs.getTimestamp("estimated_arrival"));
                                pkg.put("streetAddress", packagesRs.getString("street_address"));
                                pkg.put("city", packagesRs.getString("city"));
                                pkg.put("state", packagesRs.getString("state"));
                                pkg.put("zipCode", packagesRs.getString("zip_code"));
                                pkg.put("deliveryInstructions", packagesRs.getString("delivery_instructions"));
                                pkg.put("customerName", packagesRs.getString("customer_name"));
                                pkg.put("customerPhone", packagesRs.getString("customer_phone"));
                                packages.add(pkg);
                            }
                        }
                    }

                    // Build JSON response
                    StringBuilder json = new StringBuilder();
                    json.append("{\"route\":{");
                    json.append("\"routeId\":").append(routeId).append(",");
                    json.append("\"routeName\":\"").append(escapeJson(routeName)).append("\",");
                    json.append("\"routeDate\":\"").append(routeDate).append("\",");
                    json.append("\"estimatedDurationMinutes\":").append(estimatedDuration).append(",");
                    json.append("\"totalStops\":").append(totalStops).append(",");
                    json.append("\"routeStatus\":\"").append(routeStatus).append("\",");
                    json.append("\"vehicleId\":\"").append(vehicleId != null ? escapeJson(vehicleId) : "").append("\",");
                    json.append("\"startedAt\":").append(startedAt != null ? "\"" + startedAt.toString() + "\"" : "null").append(",");
                    json.append("\"facilityName\":\"").append(escapeJson(facilityName)).append("\",");
                    json.append("\"facilityAddress\":\"").append(escapeJson(facilityAddress)).append("\",");
                    json.append("\"packages\":[");

                    for (int i = 0; i < packages.size(); i++) {
                        Map<String, Object> pkg = packages.get(i);
                        if (i > 0) json.append(",");
                        json.append("{");
                        json.append("\"packageId\":").append(pkg.get("packageId")).append(",");
                        json.append("\"trackingNumber\":\"").append(escapeJson((String)pkg.get("trackingNumber"))).append("\",");
                        json.append("\"status\":\"").append(escapeJson((String)pkg.get("status"))).append("\",");
                        json.append("\"weightKg\":").append(pkg.get("weightKg")).append(",");
                        json.append("\"fragile\":").append(pkg.get("fragile")).append(",");
                        json.append("\"signatureRequired\":").append(pkg.get("signatureRequired")).append(",");
                        json.append("\"stopSequence\":").append(pkg.get("stopSequence")).append(",");
                        json.append("\"estimatedArrival\":").append(pkg.get("estimatedArrival") != null ? "\"" + pkg.get("estimatedArrival").toString() + "\"" : "null").append(",");
                        json.append("\"streetAddress\":\"").append(escapeJson((String)pkg.get("streetAddress"))).append("\",");
                        json.append("\"city\":\"").append(escapeJson((String)pkg.get("city"))).append("\",");
                        json.append("\"state\":\"").append(escapeJson((String)pkg.get("state"))).append("\",");
                        json.append("\"zipCode\":\"").append(escapeJson((String)pkg.get("zipCode"))).append("\",");
                        json.append("\"deliveryInstructions\":\"").append(escapeJson((String)pkg.get("deliveryInstructions"))).append("\",");
                        json.append("\"customerName\":\"").append(escapeJson((String)pkg.get("customerName"))).append("\",");
                        json.append("\"customerPhone\":\"").append(escapeJson((String)pkg.get("customerPhone"))).append("\"");
                        json.append("}");
                    }

                    json.append("]}}");

                    AuditLogger.log(driverId, session.username, "GET_ROUTE", "success", clientIp,
                        String.format("Retrieved route %d with %d packages", routeId, packages.size()));

                    respondJson(exchange, 200, json.toString());
                }
            }

        } catch (SQLException e) {
            System.err.println("SQL error during route retrieval: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "GET_ROUTE", "error", clientIp,
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

    // Update delivery status
    public static void handleUpdateDeliveryStatus(HttpExchange exchange) throws IOException {
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
            AuditLogger.log(null, null, "UPDATE_DELIVERY_STATUS", "denied", clientIp,
                "Session validation failed: " + sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Verify user has driver role
        if (!"driver".equals(session.role)) {
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "denied", clientIp,
                "Access denied - requires driver role");
            respondJson(exchange, 403, "{\"error\":\"Forbidden - Driver access required\"}");
            return;
        }

        // Parse request body
        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);

        String packageIdStr = parsed.get("packageId");
        String status = parsed.get("status");
        String notes = parsed.get("notes");
        String location = parsed.get("location");

        // Validate inputs
        if (packageIdStr == null || status == null) {
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "denied", clientIp,
                "Missing required fields: packageId and status required");
            respondJson(exchange, 400, "{\"error\":\"packageId and status are required\"}");
            return;
        }

        long packageId;
        try {
            packageId = Long.parseLong(packageIdStr);
        } catch (NumberFormatException e) {
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "denied", clientIp,
                "Invalid packageId format");
            respondJson(exchange, 400, "{\"error\":\"Invalid packageId format\"}");
            return;
        }

        // Validate status value
        List<String> validStatuses = List.of("created", "at_facility", "in_transit",
            "out_for_delivery", "delivered", "returned", "lost", "exception");
        if (!validStatuses.contains(status)) {
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "denied", clientIp,
                "Invalid status value: " + status);
            respondJson(exchange, 400, "{\"error\":\"Invalid status value\"}");
            return;
        }

        // Sanitize inputs
        SecurityManager.Result<String, String> statusResult = InputSanitizer.sanitizeString(status);
        SecurityManager.Result<String, String> notesResult = notes != null ?
            InputSanitizer.sanitizeString(notes) : SecurityManager.Result.ok("");
        SecurityManager.Result<String, String> locationResult = location != null ?
            InputSanitizer.sanitizeString(location) : SecurityManager.Result.ok("");

        if (statusResult.isErr() || notesResult.isErr() || locationResult.isErr()) {
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "error", clientIp,
                "Input sanitization failed");
            respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
            return;
        }

        String sanitizedStatus = statusResult.unwrap();
        String sanitizedNotes = notesResult.unwrap();
        String sanitizedLocation = locationResult.unwrap();

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Get driver's user_id
            long driverId = getUserId(conn, session.username);
            if (driverId == -1) {
                AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "error", clientIp,
                    "Driver user not found");
                respondJson(exchange, 404, "{\"error\":\"Driver not found\"}");
                return;
            }

            // Begin transaction
            conn.setAutoCommit(false);

            try {
                // Verify package exists and driver is assigned to its route
                String verifyQuery =
                    "SELECT p.package_id, p.tracking_number, p.package_status " +
                    "FROM packages p " +
                    "JOIN route_packages rp ON p.package_id = rp.package_id " +
                    "JOIN routes r ON rp.route_id = r.route_id " +
                    "JOIN route_assignments ra ON r.route_id = ra.route_id " +
                    "WHERE p.package_id = ? AND ra.driver_id = ? " +
                    "  AND r.route_date = CURDATE()";

                String trackingNumber;
                String currentStatus;

                try (PreparedStatement verifyStmt = conn.prepareStatement(verifyQuery)) {
                    verifyStmt.setLong(1, packageId);
                    verifyStmt.setLong(2, driverId);

                    try (ResultSet verifyRs = verifyStmt.executeQuery()) {
                        if (!verifyRs.next()) {
                            conn.rollback();
                            AuditLogger.log(driverId, session.username, "UPDATE_DELIVERY_STATUS", "denied", clientIp,
                                "Package not found in driver's route or access denied");
                            respondJson(exchange, 404,
                                "{\"error\":\"Package not found in your assigned route\"}");
                            return;
                        }

                        trackingNumber = verifyRs.getString("tracking_number");
                        currentStatus = verifyRs.getString("package_status");
                    }
                }

                // Update package status (skip 'exception' as it's not a valid package_status)
                if (!"exception".equals(sanitizedStatus)) {
                    String updatePackageQuery = "UPDATE packages SET package_status = ?";
                    if ("delivered".equals(sanitizedStatus)) {
                        updatePackageQuery += ", delivered_at = CURRENT_TIMESTAMP";
                    }
                    updatePackageQuery += " WHERE package_id = ?";

                    try (PreparedStatement updateStmt = conn.prepareStatement(updatePackageQuery)) {
                        updateStmt.setString(1, sanitizedStatus);
                        updateStmt.setLong(2, packageId);
                        updateStmt.executeUpdate();
                    }
                }

                // Insert into delivery status history
                String historyQuery =
                    "INSERT INTO delivery_status_history (package_id, status, location, updated_by, notes) " +
                    "VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement historyStmt = conn.prepareStatement(historyQuery)) {
                    historyStmt.setLong(1, packageId);
                    historyStmt.setString(2, sanitizedStatus);
                    historyStmt.setString(3, sanitizedLocation.isEmpty() ? null : sanitizedLocation);
                    historyStmt.setLong(4, driverId);
                    historyStmt.setString(5, sanitizedNotes.isEmpty() ? null : sanitizedNotes);
                    historyStmt.executeUpdate();
                }

                // Update order status if delivered
                if ("delivered".equals(sanitizedStatus)) {
                    String updateOrderQuery =
                        "UPDATE orders o " +
                        "JOIN packages p ON o.order_id = p.order_id " +
                        "SET o.order_status = 'delivered' " +
                        "WHERE p.package_id = ? AND o.order_status = 'in_transit'";

                    try (PreparedStatement updateOrderStmt = conn.prepareStatement(updateOrderQuery)) {
                        updateOrderStmt.setLong(1, packageId);
                        updateOrderStmt.executeUpdate();
                    }
                }

                // Commit transaction
                conn.commit();

                AuditLogger.log(driverId, session.username, "UPDATE_DELIVERY_STATUS", "success", clientIp,
                    String.format("Updated package %s (ID: %d) from '%s' to '%s'",
                        trackingNumber, packageId, currentStatus, sanitizedStatus));

                String response = String.format(
                    "{\"success\":true,\"message\":\"Status updated successfully\",\"packageId\":%d,\"trackingNumber\":\"%s\",\"newStatus\":\"%s\"}",
                    packageId, escapeJson(trackingNumber), escapeJson(sanitizedStatus)
                );

                respondJson(exchange, 200, response);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("SQL error during status update: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "UPDATE_DELIVERY_STATUS", "error", clientIp,
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

    // Extracts authentication token from cookie or Authorization header
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

    // Gets user ID from username
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

    // Escapes special characters in JSON
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
