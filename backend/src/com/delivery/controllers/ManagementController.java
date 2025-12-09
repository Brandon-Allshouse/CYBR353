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

public class ManagementController {

    public static void handleAssignRoutes(HttpExchange exchange) throws IOException {
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
            AuditLogger.log(null, null, "ASSIGN_ROUTE", "denied", clientIp,
                "Session validation failed: " + sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Verify user has manager role
        if (!"manager".equals(session.role) && !"admin".equals(session.role)) {
            AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "denied", clientIp,
                "Access denied - requires manager role");
            respondJson(exchange, 403, "{\"error\":\"Forbidden - Manager access required\"}");
            return;
        }

        // Parse request body
        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);

        String driverIdStr = parsed.get("driverId");
        String facilityIdStr = parsed.get("facilityId");
        String routeName = parsed.get("routeName");
        String routeDate = parsed.get("routeDate");
        String estimatedDurationStr = parsed.get("estimatedDurationMinutes");
        String vehicleId = parsed.get("vehicleId");
        String packageIdsStr = parsed.get("packageIds");

        // Validate inputs
        if (driverIdStr == null || facilityIdStr == null || routeName == null ||
            routeDate == null || estimatedDurationStr == null) {
            AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "denied", clientIp,
                "Missing required fields");
            respondJson(exchange, 400,
                "{\"error\":\"driverId, facilityId, routeName, routeDate, and estimatedDurationMinutes are required\"}");
            return;
        }

        long driverId, facilityId;
        int estimatedDuration;
        try {
            driverId = Long.parseLong(driverIdStr);
            facilityId = Long.parseLong(facilityIdStr);
            estimatedDuration = Integer.parseInt(estimatedDurationStr);
        } catch (NumberFormatException e) {
            AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "denied", clientIp,
                "Invalid number format");
            respondJson(exchange, 400, "{\"error\":\"Invalid number format\"}");
            return;
        }

        // Parse package IDs
        List<Long> packageIds = new ArrayList<>();
        System.out.println("DEBUG: Received packageIdsStr = '" + packageIdsStr + "'");
        if (packageIdsStr != null && !packageIdsStr.trim().isEmpty()) {
            try {
                for (String idStr : packageIdsStr.split(",")) {
                    Long id = Long.parseLong(idStr.trim());
                    packageIds.add(id);
                    System.out.println("DEBUG: Parsed package ID: " + id);
                }
            } catch (NumberFormatException e) {
                System.err.println("DEBUG: Failed to parse package IDs from: " + packageIdsStr);
                AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "denied", clientIp,
                    "Invalid package ID format");
                respondJson(exchange, 400, "{\"error\":\"Invalid package ID format\"}");
                return;
            }
        }
        System.out.println("DEBUG: Total package IDs parsed: " + packageIds.size() + " - " + packageIds);

        // Sanitize string inputs
        SecurityManager.Result<String, String> routeNameResult = InputSanitizer.sanitizeString(routeName);
        SecurityManager.Result<String, String> routeDateResult = InputSanitizer.sanitizeString(routeDate);
        SecurityManager.Result<String, String> vehicleIdResult = vehicleId != null ?
            InputSanitizer.sanitizeString(vehicleId) : SecurityManager.Result.ok("");

        if (routeNameResult.isErr() || routeDateResult.isErr() || vehicleIdResult.isErr()) {
            AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "error", clientIp,
                "Input sanitization failed");
            respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
            return;
        }

        String sanitizedRouteName = routeNameResult.unwrap();
        String sanitizedRouteDate = routeDateResult.unwrap();
        String sanitizedVehicleId = vehicleIdResult.unwrap();

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Get manager's user_id
            long managerId = getUserId(conn, session.username);

            // Begin transaction
            conn.setAutoCommit(false);

            try {
                // Verify driver exists and has driver role
                if (!verifyDriverRole(conn, driverId)) {
                    conn.rollback();
                    AuditLogger.log(managerId, session.username, "ASSIGN_ROUTE", "denied", clientIp,
                        "Invalid driver ID or user is not a driver");
                    respondJson(exchange, 400, "{\"error\":\"Invalid driver ID\"}");
                    return;
                }

                // Verify facility exists
                if (!verifyFacilityExists(conn, facilityId)) {
                    conn.rollback();
                    AuditLogger.log(managerId, session.username, "ASSIGN_ROUTE", "denied", clientIp,
                        "Invalid facility ID");
                    respondJson(exchange, 400, "{\"error\":\"Invalid facility ID\"}");
                    return;
                }

                // Create route
                String insertRouteQuery =
                    "INSERT INTO routes (route_name, facility_id, route_date, estimated_duration_minutes, " +
                    "                    total_stops, route_status) " +
                    "VALUES (?, ?, ?, ?, ?, 'planned')";

                long routeId;
                try (PreparedStatement routeStmt = conn.prepareStatement(insertRouteQuery,
                        PreparedStatement.RETURN_GENERATED_KEYS)) {

                    routeStmt.setString(1, sanitizedRouteName);
                    routeStmt.setLong(2, facilityId);
                    routeStmt.setString(3, sanitizedRouteDate);
                    routeStmt.setInt(4, estimatedDuration);
                    routeStmt.setInt(5, packageIds.size());

                    routeStmt.executeUpdate();

                    try (ResultSet generatedKeys = routeStmt.getGeneratedKeys()) {
                        if (!generatedKeys.next()) {
                            conn.rollback();
                            AuditLogger.log(managerId, session.username, "ASSIGN_ROUTE", "error", clientIp,
                                "Failed to create route");
                            respondJson(exchange, 500, "{\"error\":\"Failed to create route\"}");
                            return;
                        }
                        routeId = generatedKeys.getLong(1);
                    }
                }

                // Create route assignment
                String assignmentQuery =
                    "INSERT INTO route_assignments (route_id, driver_id, vehicle_id) " +
                    "VALUES (?, ?, ?)";

                try (PreparedStatement assignStmt = conn.prepareStatement(assignmentQuery)) {
                    assignStmt.setLong(1, routeId);
                    assignStmt.setLong(2, driverId);
                    assignStmt.setString(3, sanitizedVehicleId.isEmpty() ? null : sanitizedVehicleId);
                    assignStmt.executeUpdate();
                }

                // Add packages to route
                if (!packageIds.isEmpty()) {
                    // Update package status FIRST to avoid lock escalation with foreign key constraint
                    // (route_packages has FK to packages, INSERT takes shared lock, UPDATE needs exclusive lock)
                    String updatePackageQuery = "UPDATE packages SET package_status = 'out_for_delivery' WHERE package_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updatePackageQuery)) {
                        for (Long packageId : packageIds) {
                            updateStmt.setLong(1, packageId);
                            updateStmt.executeUpdate();
                        }
                    }

                    // Now insert into route_packages
                    String routePackageQuery =
                        "INSERT INTO route_packages (route_id, package_id, stop_sequence) " +
                        "VALUES (?, ?, ?)";

                    try (PreparedStatement packageStmt = conn.prepareStatement(routePackageQuery)) {
                        for (int i = 0; i < packageIds.size(); i++) {
                            packageStmt.setLong(1, routeId);
                            packageStmt.setLong(2, packageIds.get(i));
                            packageStmt.setInt(3, i + 1);
                            packageStmt.executeUpdate();
                        }
                    }
                }

                // Commit transaction
                conn.commit();

                AuditLogger.log(managerId, session.username, "ASSIGN_ROUTE", "success", clientIp,
                    String.format("Created route %d and assigned to driver %d with %d packages",
                        routeId, driverId, packageIds.size()));

                String response = String.format(
                    "{\"success\":true,\"message\":\"Route assigned successfully\",\"routeId\":%d,\"driverId\":%d,\"packageCount\":%d}",
                    routeId, driverId, packageIds.size()
                );

                respondJson(exchange, 201, response);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("SQL error during route assignment: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "ASSIGN_ROUTE", "error", clientIp,
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

    public static void handleInventoryReport(HttpExchange exchange) throws IOException {
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

        // Get session token
        String token = extractToken(exchange);

        // Validate session
        Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, null, "INVENTORY_REPORT", "denied", clientIp,
                "Session validation failed: " + sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Verify user has manager role
        if (!"manager".equals(session.role) && !"admin".equals(session.role)) {
            AuditLogger.log(null, session.username, "INVENTORY_REPORT", "denied", clientIp,
                "Access denied - requires manager role");
            respondJson(exchange, 403, "{\"error\":\"Forbidden - Manager access required\"}");
            return;
        }

        // Parse query parameters
        String query = exchange.getRequestURI().getQuery();
        Long facilityId = null;
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "facilityId".equals(keyValue[0])) {
                    try {
                        facilityId = Long.parseLong(keyValue[1]);
                    } catch (NumberFormatException e) {
                        respondJson(exchange, 400, "{\"error\":\"Invalid facilityId format\"}");
                        return;
                    }
                }
            }
        }

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            System.err.println("Database connection error: " + connResult.unwrapErr());
            AuditLogger.log(null, session.username, "INVENTORY_REPORT", "error", clientIp,
                "Database connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            long managerId = getUserId(conn, session.username);

            // Build query based on whether facilityId is specified
            String inventoryQuery;
            if (facilityId != null) {
                inventoryQuery =
                    "SELECT f.facility_id, f.facility_name, f.address, f.city, f.state, " +
                    "       f.capacity, " +
                    "       COUNT(DISTINCT i.package_id) as package_count, " +
                    "       SUM(CASE WHEN i.inventory_status = 'in_stock' THEN 1 ELSE 0 END) as in_stock_count, " +
                    "       SUM(CASE WHEN i.inventory_status = 'checked_out' THEN 1 ELSE 0 END) as checked_out_count " +
                    "FROM facilities f " +
                    "LEFT JOIN inventory i ON f.facility_id = i.facility_id AND i.departure_time IS NULL " +
                    "WHERE f.facility_id = ? " +
                    "GROUP BY f.facility_id, f.facility_name, f.address, f.city, f.state, f.capacity";
            } else {
                inventoryQuery =
                    "SELECT f.facility_id, f.facility_name, f.address, f.city, f.state, " +
                    "       f.capacity, " +
                    "       COUNT(DISTINCT i.package_id) as package_count, " +
                    "       SUM(CASE WHEN i.inventory_status = 'in_stock' THEN 1 ELSE 0 END) as in_stock_count, " +
                    "       SUM(CASE WHEN i.inventory_status = 'checked_out' THEN 1 ELSE 0 END) as checked_out_count " +
                    "FROM facilities f " +
                    "LEFT JOIN inventory i ON f.facility_id = i.facility_id AND i.departure_time IS NULL " +
                    "GROUP BY f.facility_id, f.facility_name, f.address, f.city, f.state, f.capacity " +
                    "ORDER BY f.facility_name";
            }

            List<Map<String, Object>> facilities = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(inventoryQuery)) {
                if (facilityId != null) {
                    stmt.setLong(1, facilityId);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> facility = new HashMap<>();
                        facility.put("facilityId", rs.getLong("facility_id"));
                        facility.put("facilityName", rs.getString("facility_name"));
                        facility.put("address", rs.getString("address"));
                        facility.put("city", rs.getString("city"));
                        facility.put("state", rs.getString("state"));
                        facility.put("capacity", rs.getInt("capacity"));
                        facility.put("packageCount", rs.getInt("package_count"));
                        facility.put("inStockCount", rs.getInt("in_stock_count"));
                        facility.put("checkedOutCount", rs.getInt("checked_out_count"));

                        int capacity = rs.getInt("capacity");
                        int packageCount = rs.getInt("package_count");
                        facility.put("utilizationPercent", capacity > 0 ? (packageCount * 100.0 / capacity) : 0);

                        facilities.add(facility);
                    }
                }
            }

            // Get detailed package information if specific facility requested
            if (facilityId != null && !facilities.isEmpty()) {
                String packagesQuery =
                    "SELECT p.package_id, p.tracking_number, p.package_status, p.weight_kg, " +
                    "       i.inventory_status, i.arrival_time, " +
                    "       o.order_id, a.city as destination_city, a.state as destination_state " +
                    "FROM inventory i " +
                    "JOIN packages p ON i.package_id = p.package_id " +
                    "JOIN orders o ON p.order_id = o.order_id " +
                    "JOIN addresses a ON o.delivery_address_id = a.address_id " +
                    "WHERE i.facility_id = ? AND i.departure_time IS NULL " +
                    "ORDER BY i.arrival_time DESC " +
                    "LIMIT 100";

                List<Map<String, Object>> packages = new ArrayList<>();

                try (PreparedStatement packagesStmt = conn.prepareStatement(packagesQuery)) {
                    packagesStmt.setLong(1, facilityId);

                    try (ResultSet packagesRs = packagesStmt.executeQuery()) {
                        while (packagesRs.next()) {
                            Map<String, Object> pkg = new HashMap<>();
                            pkg.put("packageId", packagesRs.getLong("package_id"));
                            pkg.put("trackingNumber", packagesRs.getString("tracking_number"));
                            pkg.put("status", packagesRs.getString("package_status"));
                            pkg.put("weightKg", packagesRs.getDouble("weight_kg"));
                            pkg.put("inventoryStatus", packagesRs.getString("inventory_status"));
                            pkg.put("arrivalTime", packagesRs.getTimestamp("arrival_time"));
                            pkg.put("orderId", packagesRs.getLong("order_id"));
                            pkg.put("destinationCity", packagesRs.getString("destination_city"));
                            pkg.put("destinationState", packagesRs.getString("destination_state"));
                            packages.add(pkg);
                        }
                    }
                }

                facilities.get(0).put("packages", packages);
            }

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{\"facilities\":[");

            for (int i = 0; i < facilities.size(); i++) {
                Map<String, Object> fac = facilities.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"facilityId\":").append(fac.get("facilityId")).append(",");
                json.append("\"facilityName\":\"").append(escapeJson((String)fac.get("facilityName"))).append("\",");
                json.append("\"address\":\"").append(escapeJson((String)fac.get("address"))).append("\",");
                json.append("\"city\":\"").append(escapeJson((String)fac.get("city"))).append("\",");
                json.append("\"state\":\"").append(escapeJson((String)fac.get("state"))).append("\",");
                json.append("\"capacity\":").append(fac.get("capacity")).append(",");
                json.append("\"packageCount\":").append(fac.get("packageCount")).append(",");
                json.append("\"inStockCount\":").append(fac.get("inStockCount")).append(",");
                json.append("\"checkedOutCount\":").append(fac.get("checkedOutCount")).append(",");
                json.append("\"utilizationPercent\":").append(String.format("%.2f", fac.get("utilizationPercent")));

                // Add packages if present
                if (fac.containsKey("packages")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> packages = (List<Map<String, Object>>) fac.get("packages");
                    json.append(",\"packages\":[");

                    for (int j = 0; j < packages.size(); j++) {
                        Map<String, Object> pkg = packages.get(j);
                        if (j > 0) json.append(",");
                        json.append("{");
                        json.append("\"packageId\":").append(pkg.get("packageId")).append(",");
                        json.append("\"trackingNumber\":\"").append(escapeJson((String)pkg.get("trackingNumber"))).append("\",");
                        json.append("\"status\":\"").append(escapeJson((String)pkg.get("status"))).append("\",");
                        json.append("\"weightKg\":").append(pkg.get("weightKg")).append(",");
                        json.append("\"inventoryStatus\":\"").append(escapeJson((String)pkg.get("inventoryStatus"))).append("\",");
                        json.append("\"arrivalTime\":\"").append(pkg.get("arrivalTime").toString()).append("\",");
                        json.append("\"orderId\":").append(pkg.get("orderId")).append(",");
                        json.append("\"destinationCity\":\"").append(escapeJson((String)pkg.get("destinationCity"))).append("\",");
                        json.append("\"destinationState\":\"").append(escapeJson((String)pkg.get("destinationState"))).append("\"");
                        json.append("}");
                    }

                    json.append("]");
                }

                json.append("}");
            }

            json.append("]}");

            AuditLogger.log(managerId, session.username, "INVENTORY_REPORT", "success", clientIp,
                String.format("Retrieved inventory report for %d facilities", facilities.size()));

            respondJson(exchange, 200, json.toString());

        } catch (SQLException e) {
            System.err.println("SQL error during inventory report: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.log(null, session.username, "INVENTORY_REPORT", "error", clientIp,
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

    // Retrieves user_id for a given username
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

    // Verifies that a user exists and has driver role
    private static boolean verifyDriverRole(Connection conn, long userId) throws SQLException {
        String query = "SELECT role FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return "driver".equals(rs.getString("role"));
                }
            }
        }
        return false;
    }

    // Verifies that a facility exists
    private static boolean verifyFacilityExists(Connection conn, long facilityId) throws SQLException {
        String query = "SELECT facility_id FROM facilities WHERE facility_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, facilityId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
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

        // Split by comma but respect quoted strings
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        for (String p : parts) {
            int idx = p.indexOf(":");
            if (idx <= 0) continue;
            String k = p.substring(0, idx).trim().replaceAll("\"", "");
            String v = p.substring(idx+1).trim().replaceAll("\"", "");
            map.put(k, v);
        }
        return map;
    }

    // GET /api/management/drivers - Get all active drivers for route assignment
    public static void handleGetDrivers(HttpExchange exchange) throws IOException {
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

        // Get session token
        String token = extractToken(exchange);

        // Validate session
        Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
        if (sessionResult.isErr()) {
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Verify user has manager or admin role
        if (!"manager".equals(session.role) && !"admin".equals(session.role)) {
            AuditLogger.log(null, session.username, "GET_DRIVERS", "denied", clientIp,
                "Access denied - requires manager role");
            respondJson(exchange, 403, "{\"error\":\"Forbidden - Manager access required\"}");
            return;
        }

        // Get database connection
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            respondJson(exchange, 500, "{\"error\":\"Server error\"}");
            return;
        }

        Connection conn = connResult.unwrap();

        try {
            // Query all active drivers
            String query =
                "SELECT user_id, username, full_name, email, phone " +
                "FROM users " +
                "WHERE role = 'driver' AND account_status = 'active' " +
                "ORDER BY username";

            List<Map<String, Object>> drivers = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> driver = new HashMap<>();
                    driver.put("user_id", rs.getLong("user_id"));
                    driver.put("username", rs.getString("username"));
                    driver.put("full_name", rs.getString("full_name"));
                    driver.put("email", rs.getString("email"));
                    driver.put("phone", rs.getString("phone"));
                    drivers.add(driver);
                }
            }

            AuditLogger.log(null, session.username, "GET_DRIVERS", "success", clientIp,
                "Retrieved " + drivers.size() + " drivers");

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{\"success\":true,\"drivers\":[");

            for (int i = 0; i < drivers.size(); i++) {
                Map<String, Object> driver = drivers.get(i);
                if (i > 0) json.append(",");

                json.append("{");
                json.append("\"user_id\":").append(driver.get("user_id")).append(",");
                json.append("\"username\":\"").append(escapeJson((String) driver.get("username"))).append("\",");
                json.append("\"full_name\":\"").append(escapeJson((String) driver.get("full_name"))).append("\",");
                json.append("\"email\":\"").append(escapeJson((String) driver.get("email"))).append("\",");
                json.append("\"phone\":\"").append(escapeJson((String) driver.get("phone"))).append("\"");
                json.append("}");
            }

            json.append("]}");

            respondJson(exchange, 200, json.toString());

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
            respondJson(exchange, 500, "{\"error\":\"Database error\"}");
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    private static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Prevents JSON injection attacks
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
