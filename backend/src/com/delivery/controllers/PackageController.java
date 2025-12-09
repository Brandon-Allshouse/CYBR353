package com.delivery.controllers;

import com.delivery.database.DatabaseConnection;
import com.delivery.security.SecurityManager;
import com.delivery.security.SecurityManager.AuditLogger;
import com.delivery.security.SecurityManager.InputSanitizer;
import com.delivery.session.SessionManager;
import com.delivery.util.Result;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // POST /package/create
public static void handleCreatePackage(HttpExchange exchange) throws IOException {
    String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

    // CORS
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

    // Get session to identify customer
    String token = extractToken(exchange);
    Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
    if (sessionResult.isErr()) {
        AuditLogger.log(null, null, "CREATE_PACKAGE", "denied", clientIp,
            "Session validation failed");
        respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
        return;
    }

    SessionManager.Session session = sessionResult.unwrap();

    // Read JSON body
    String body = readStream(exchange.getRequestBody());
    
    // Parse nested JSON manually
    String tracking = extractJsonField(body, "trackingNumber");
    String weight = extractJsonField(body, "weight");
    String length = extractJsonField(body, "length");
    String width = extractJsonField(body, "width");
    String height = extractJsonField(body, "height");
    
    // Extract delivery address fields
    String deliveryStreet = extractNestedJsonField(body, "deliveryAddress", "streetAddress");
    String deliveryCity = extractNestedJsonField(body, "deliveryAddress", "city");
    String deliveryState = extractNestedJsonField(body, "deliveryAddress", "state");
    String deliveryZip = extractNestedJsonField(body, "deliveryAddress", "zipCode");
    String deliveryInstructions = extractNestedJsonField(body, "deliveryAddress", "deliveryInstructions");

    // Validate required fields
    if (tracking == null || weight == null || length == null || width == null || height == null ||
        deliveryStreet == null || deliveryCity == null || deliveryState == null || deliveryZip == null) {
        AuditLogger.log(null, session.username, "CREATE_PACKAGE", "denied", clientIp,
            "Missing required fields");
        respondJson(exchange, 400, "{\"error\":\"All package details and delivery address fields required\"}");
        return;
    }

    // Sanitize inputs
    var tRes = InputSanitizer.sanitizeString(tracking);
    var wRes = InputSanitizer.sanitizeString(weight);
    var lRes = InputSanitizer.sanitizeString(length);
    var wiRes = InputSanitizer.sanitizeString(width);
    var hRes = InputSanitizer.sanitizeString(height);
    var streetRes = InputSanitizer.sanitizeString(deliveryStreet);
    var cityRes = InputSanitizer.sanitizeString(deliveryCity);
    var stateRes = InputSanitizer.sanitizeString(deliveryState);
    var zipRes = InputSanitizer.sanitizeString(deliveryZip);
    var instrRes = deliveryInstructions != null ? InputSanitizer.sanitizeString(deliveryInstructions) : 
                   SecurityManager.Result.ok("");

    if (tRes.isErr() || wRes.isErr() || lRes.isErr() || wiRes.isErr() || hRes.isErr() ||
        streetRes.isErr() || cityRes.isErr() || stateRes.isErr() || zipRes.isErr() || instrRes.isErr()) {
        AuditLogger.log(null, session.username, "CREATE_PACKAGE", "error", clientIp,
            "Sanitization failed");
        respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
        return;
    }

    String trackingNumber = tRes.unwrap();
    String sanitizedStreet = streetRes.unwrap();
    String sanitizedCity = cityRes.unwrap();
    String sanitizedState = stateRes.unwrap();
    String sanitizedZip = zipRes.unwrap();
    String sanitizedInstr = instrRes.unwrap();

    double weightKg, lengthCm, widthCm, heightCm;

    try {
        weightKg = Double.parseDouble(wRes.unwrap());
        lengthCm = Double.parseDouble(lRes.unwrap());
        widthCm = Double.parseDouble(wiRes.unwrap());
        heightCm = Double.parseDouble(hRes.unwrap());
    } catch (NumberFormatException e) {
        respondJson(exchange, 400, "{\"error\":\"Numeric fields must be valid numbers\"}");
        return;
    }

    // DB connection
    Result<Connection, String> connRes = DatabaseConnection.getConnection();
    if (connRes.isErr()) {
        AuditLogger.log(null, session.username, "CREATE_PACKAGE", "error", clientIp,
            "DB connection failed");
        respondJson(exchange, 500, "{\"error\":\"Server error\"}");
        return;
    }

    Connection conn = connRes.unwrap();

    try {
        conn.setAutoCommit(false);

        // Get customer ID from session
        long customerId = getUserId(conn, session.username);
        if (customerId == -1) {
            conn.rollback();
            respondJson(exchange, 404, "{\"error\":\"User not found\"}");
            return;
        }

        // 1. Create delivery address
// 1. Create delivery address
String insertAddress =
    "INSERT INTO addresses (user_id, address_type, street_address, city, state, zip_code, delivery_instructions) " +
    "VALUES (?, ?, ?, ?, ?, ?, ?)";

long deliveryAddressId;
try (PreparedStatement addrStmt = conn.prepareStatement(insertAddress, PreparedStatement.RETURN_GENERATED_KEYS)) {
    addrStmt.setLong(1, customerId);                // user_id
    addrStmt.setString(2, "delivery");              // address_type
    addrStmt.setString(3, sanitizedStreet);         // street_address
    addrStmt.setString(4, sanitizedCity);           // city
    addrStmt.setString(5, sanitizedState);          // state
    addrStmt.setString(6, sanitizedZip);            // zip_code
    addrStmt.setString(7, sanitizedInstr.isEmpty() ? null : sanitizedInstr); // delivery_instructions
    addrStmt.executeUpdate();

    try (ResultSet keys = addrStmt.getGeneratedKeys()) {
        if (!keys.next()) {
            conn.rollback();
            respondJson(exchange, 500, "{\"error\":\"Failed to create address\"}");
            return;
        }
        deliveryAddressId = keys.getLong(1);
    }
}

// 2. Create order - use East Coast Hub (facility_id = 3) as default pickup
// First, get or create the pickup address for East Coast Hub
// 2. Create order - use East Coast Hub as default pickup
long defaultPickupAddressId = 1; // Update this with your actual address_id

String insertOrder =
    "INSERT INTO orders (customer_id, pickup_address_id, delivery_address_id, order_status, total_cost) " +
    "VALUES (?, ?, ?, 'pending', 0.00)";

long orderId;
try (PreparedStatement orderStmt = conn.prepareStatement(insertOrder, PreparedStatement.RETURN_GENERATED_KEYS)) {
    orderStmt.setLong(1, customerId);
    orderStmt.setLong(2, defaultPickupAddressId);  // East Coast Hub pickup address
    orderStmt.setLong(3, deliveryAddressId);
    orderStmt.executeUpdate();

    try (ResultSet keys = orderStmt.getGeneratedKeys()) {
        if (!keys.next()) {
            conn.rollback();
            respondJson(exchange, 500, "{\"error\":\"Failed to create order\"}");
            return;
        }
        orderId = keys.getLong(1);
    }
}

        // 3. Create package
// 3. Create package
String insertPackage =
    "INSERT INTO packages (order_id, tracking_number, weight_kg, length_cm, width_cm, height_cm, package_status) " +
    "VALUES (?, ?, ?, ?, ?, ?, ?)";

long packageId;
try (PreparedStatement pkgStmt = conn.prepareStatement(insertPackage, PreparedStatement.RETURN_GENERATED_KEYS)) {
    pkgStmt.setLong(1, orderId);                    // order_id
    pkgStmt.setString(2, trackingNumber);           // tracking_number
    pkgStmt.setDouble(3, weightKg);                 // weight_kg
    pkgStmt.setDouble(4, lengthCm);                 // length_cm
    pkgStmt.setDouble(5, widthCm);                  // width_cm
    pkgStmt.setDouble(6, heightCm);                 // height_cm
    pkgStmt.setString(7, "pending");                // package_status
    pkgStmt.executeUpdate();

    try (ResultSet keys = pkgStmt.getGeneratedKeys()) {
        if (!keys.next()) {
            conn.rollback();
            respondJson(exchange, 500, "{\"error\":\"Failed to create package\"}");
            return;
        }
        packageId = keys.getLong(1);
    }
}

        // 4. Create initial status history
        String insertHistory =
            "INSERT INTO delivery_status_history (package_id, status, location, notes) " +
            "VALUES (?, 'created', 'Awaiting Pickup', 'Package created by customer')";
        
        try (PreparedStatement histStmt = conn.prepareStatement(insertHistory)) {
            histStmt.setLong(1, packageId);
            histStmt.executeUpdate();
        }

        conn.commit();

        AuditLogger.log(customerId, session.username, "CREATE_PACKAGE", "success", clientIp,
            "Created package " + trackingNumber + " with delivery to " + sanitizedCity + ", " + sanitizedState);

        // Build JSON response
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,");
        json.append("\"package\":{");
        json.append("\"packageId\":").append(packageId).append(",");
        json.append("\"trackingNumber\":\"").append(escapeJson(trackingNumber)).append("\",");
        json.append("\"status\":\"created\",");
        json.append("\"weightKg\":").append(weightKg).append(",");
        json.append("\"dimensions\":{");
        json.append("\"lengthCm\":").append(lengthCm).append(",");
        json.append("\"widthCm\":").append(widthCm).append(",");
        json.append("\"heightCm\":").append(heightCm);
        json.append("},");
        json.append("\"deliveryAddress\":{");
        json.append("\"streetAddress\":\"").append(escapeJson(sanitizedStreet)).append("\",");
        json.append("\"city\":\"").append(escapeJson(sanitizedCity)).append("\",");
        json.append("\"state\":\"").append(escapeJson(sanitizedState)).append("\",");
        json.append("\"zipCode\":\"").append(escapeJson(sanitizedZip)).append("\"");
        json.append("}}}");

        respondJson(exchange, 201, json.toString());

    } catch (SQLException e) {
        try { conn.rollback(); } catch (Exception ignored) {}
        respondJson(exchange, 500, "{\"error\":\"Database error\"}");
        e.printStackTrace();
    } finally {
        try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
    }
}

// Helper method to extract simple JSON fields
private static String extractJsonField(String json, String fieldName) {
    String searchStr = "\"" + fieldName + "\":";
    int startIdx = json.indexOf(searchStr);
    if (startIdx == -1) return null;
    
    startIdx += searchStr.length();
    
    // Skip whitespace
    while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) {
        startIdx++;
    }
    
    // Check if it's a string (starts with quote) or number
    if (startIdx < json.length() && json.charAt(startIdx) == '"') {
        startIdx++; // Skip opening quote
        int endIdx = json.indexOf('"', startIdx);
        if (endIdx == -1) return null;
        return json.substring(startIdx, endIdx);
    } else {
        // It's a number
        int endIdx = startIdx;
        while (endIdx < json.length() && (Character.isDigit(json.charAt(endIdx)) || json.charAt(endIdx) == '.')) {
            endIdx++;
        }
        return json.substring(startIdx, endIdx);
    }
}

// Helper method to extract nested JSON fields (e.g., deliveryAddress.city)
private static String extractNestedJsonField(String json, String objectName, String fieldName) {
    String objectStart = "\"" + objectName + "\":{";
    int objStartIdx = json.indexOf(objectStart);
    if (objStartIdx == -1) return null;
    
    int objEndIdx = json.indexOf("}", objStartIdx);
    if (objEndIdx == -1) return null;
    
    String objectContent = json.substring(objStartIdx, objEndIdx + 1);
    return extractJsonField(objectContent, fieldName);
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

        public static void handleOrderEdit(HttpExchange exchange) throws IOException {
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

        // Validate session
        String token = extractToken(exchange);
        Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, null, "API_ORDER_EDIT", "denied", clientIp,
                "Session validation failed: " + sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
            return;
        }
        SessionManager.Session session = sessionResult.unwrap();

        // Parse request body
        String body = readStream(exchange.getRequestBody());
        Map<String, String> parsed = parseJson(body);

        String tracking = parsed.get("tracking_number");
        String weightStr = parsed.get("weight");
        String lengthStr = parsed.get("length");
        String widthStr = parsed.get("width");
        String heightStr = parsed.get("height");
        String notes = parsed.get("notes");

        if (tracking == null || tracking.trim().isEmpty()) {
            AuditLogger.log(null, session.username, "API_ORDER_EDIT", "denied", clientIp,
                "Missing tracking_number in request");
            respondJson(exchange, 400, "{\"error\":\"tracking_number is required\"}");
            return;
        }

        // Sanitize tracking and optional fields (as strings)
        SecurityManager.Result<String, String> tRes = InputSanitizer.sanitizeString(tracking);
        SecurityManager.Result<String, String> wRes = weightStr != null ? InputSanitizer.sanitizeString(weightStr) : SecurityManager.Result.ok(null);
        SecurityManager.Result<String, String> lRes = lengthStr != null ? InputSanitizer.sanitizeString(lengthStr) : SecurityManager.Result.ok(null);
        SecurityManager.Result<String, String> wiRes = widthStr != null ? InputSanitizer.sanitizeString(widthStr) : SecurityManager.Result.ok(null);
        SecurityManager.Result<String, String> hRes = heightStr != null ? InputSanitizer.sanitizeString(heightStr) : SecurityManager.Result.ok(null);
        SecurityManager.Result<String, String> notesRes = notes != null ? InputSanitizer.sanitizeString(notes) : SecurityManager.Result.ok("");

        if (tRes.isErr() || wRes.isErr() || lRes.isErr() || wiRes.isErr() || hRes.isErr() || notesRes.isErr()) {
            AuditLogger.log(null, session.username, "API_ORDER_EDIT", "error", clientIp,
                "Sanitization failed");
            respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
            return;
        }

        String sanitizedTracking = tRes.unwrap();
        String sanitizedWeight = wRes.unwrap();
        String sanitizedLength = lRes.unwrap();
        String sanitizedWidth = wiRes.unwrap();
        String sanitizedHeight = hRes.unwrap();
        String sanitizedNotes = notesRes.unwrap();

        // No numeric fields provided -> nothing to update
        boolean hasUpdate = (sanitizedWeight != null && !sanitizedWeight.isEmpty())
                         || (sanitizedLength != null && !sanitizedLength.isEmpty())
                         || (sanitizedWidth != null && !sanitizedWidth.isEmpty())
                         || (sanitizedHeight != null && !sanitizedHeight.isEmpty());

        if (!hasUpdate) {
            AuditLogger.log(null, session.username, "API_ORDER_EDIT", "denied", clientIp,
                "No updatable fields provided");
            respondJson(exchange, 400, "{\"error\":\"No fields provided to update\"}");
            return;
        }

        // DB connection
        Result<Connection, String> connRes = DatabaseConnection.getConnection();
        if (connRes.isErr()) {
            AuditLogger.log(null, session.username, "API_ORDER_EDIT", "error", clientIp,
                "DB connection failed");
            respondJson(exchange, 500, "{\"error\":\"Server error\"}");
            return;
        }
        Connection conn = connRes.unwrap();

        try {
            conn.setAutoCommit(false);

            // Find package and its order owner
            long packageId = -1;
            long ownerId = -1;
            String currentStatus = null;
            String findQuery = "SELECT p.package_id, p.weight_kg, p.length_cm, p.width_cm, p.height_cm, p.package_status, o.customer_id " +
                               "FROM packages p JOIN orders o ON p.order_id = o.order_id WHERE p.tracking_number = ?";

            try (PreparedStatement findStmt = conn.prepareStatement(findQuery)) {
                findStmt.setString(1, sanitizedTracking);
                try (ResultSet rs = findStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        AuditLogger.log(null, session.username, "API_ORDER_EDIT", "denied", clientIp,
                            "Package not found: " + sanitizedTracking);
                        respondJson(exchange, 404, "{\"error\":\"Package not found\"}");
                        return;
                    }
                    packageId = rs.getLong("package_id");
                    ownerId = rs.getLong("customer_id");
                    currentStatus = rs.getString("package_status");
                }
            }

            // Authorization: allow if owner or manager/admin
            long userId = getUserId(conn, session.username);
            boolean isPrivileged = "manager".equals(session.role) || "admin".equals(session.role);
            if (!isPrivileged && userId != ownerId) {
                conn.rollback();
                AuditLogger.log(userId, session.username, "API_ORDER_EDIT", "denied", clientIp,
                    "User not owner of package");
                respondJson(exchange, 403, "{\"error\":\"Forbidden - not owner of package\"}");
                return;
            }

            // Build dynamic update
            List<String> setParts = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            // For history logging, record old values
            String oldWeight = null, oldLength = null, oldWidth = null, oldHeight = null;
            try (PreparedStatement sel = conn.prepareStatement("SELECT weight_kg, length_cm, width_cm, height_cm FROM packages WHERE package_id = ?")) {
                sel.setLong(1, packageId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        oldWeight = rs.getString("weight_kg");
                        oldLength = rs.getString("length_cm");
                        oldWidth = rs.getString("width_cm");
                        oldHeight = rs.getString("height_cm");
                    }
                }
            }

            if (sanitizedWeight != null && !sanitizedWeight.isEmpty()) {
                double w = Double.parseDouble(sanitizedWeight);
                setParts.add("weight_kg = ?");
                params.add(w);
            }
            if (sanitizedLength != null && !sanitizedLength.isEmpty()) {
                double l = Double.parseDouble(sanitizedLength);
                setParts.add("length_cm = ?");
                params.add(l);
            }
            if (sanitizedWidth != null && !sanitizedWidth.isEmpty()) {
                double wi = Double.parseDouble(sanitizedWidth);
                setParts.add("width_cm = ?");
                params.add(wi);
            }
            if (sanitizedHeight != null && !sanitizedHeight.isEmpty()) {
                double h = Double.parseDouble(sanitizedHeight);
                setParts.add("height_cm = ?");
                params.add(h);
            }

            if (setParts.isEmpty()) {
                conn.rollback();
                respondJson(exchange, 400, "{\"error\":\"No valid numeric fields to update\"}");
                return;
            }

            String updateSql = "UPDATE packages SET " + String.join(", ", setParts) + " WHERE package_id = ?";
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                int idx = 1;
                for (Object p : params) {
                    upd.setObject(idx++, p);
                }
                upd.setLong(idx, packageId);
                upd.executeUpdate();
            }

            // Insert edit history per-field
            String historySql = "INSERT INTO package_edit_history (package_id, edited_by, field_name, old_value, new_value, edit_reason) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement hist = conn.prepareStatement(historySql)) {
                // weight
                if (sanitizedWeight != null && !sanitizedWeight.isEmpty()) {
                    hist.setLong(1, packageId);
                    hist.setLong(2, userId);
                    hist.setString(3, "weight_kg");
                    hist.setString(4, oldWeight);
                    hist.setString(5, sanitizedWeight);
                    hist.setString(6, sanitizedNotes.isEmpty() ? null : sanitizedNotes);
                    hist.executeUpdate();
                }
                // length
                if (sanitizedLength != null && !sanitizedLength.isEmpty()) {
                    hist.setLong(1, packageId);
                    hist.setLong(2, userId);
                    hist.setString(3, "length_cm");
                    hist.setString(4, oldLength);
                    hist.setString(5, sanitizedLength);
                    hist.setString(6, sanitizedNotes.isEmpty() ? null : sanitizedNotes);
                    hist.executeUpdate();
                }
                // width
                if (sanitizedWidth != null && !sanitizedWidth.isEmpty()) {
                    hist.setLong(1, packageId);
                    hist.setLong(2, userId);
                    hist.setString(3, "width_cm");
                    hist.setString(4, oldWidth);
                    hist.setString(5, sanitizedWidth);
                    hist.setString(6, sanitizedNotes.isEmpty() ? null : sanitizedNotes);
                    hist.executeUpdate();
                }
                // height
                if (sanitizedHeight != null && !sanitizedHeight.isEmpty()) {
                    hist.setLong(1, packageId);
                    hist.setLong(2, userId);
                    hist.setString(3, "height_cm");
                    hist.setString(4, oldHeight);
                    hist.setString(5, sanitizedHeight);
                    hist.setString(6, sanitizedNotes.isEmpty() ? null : sanitizedNotes);
                    hist.executeUpdate();
                }
            }

            // Fetch updated package fields to return
            String fetchSql = "SELECT tracking_number, weight_kg, length_cm, width_cm, height_cm, package_status FROM packages WHERE package_id = ?";
            String outTracking = null;
            double outWeight = 0, outLen = 0, outWid = 0, outHei = 0;
            String outStatus = null;
            try (PreparedStatement fetch = conn.prepareStatement(fetchSql)) {
                fetch.setLong(1, packageId);
                try (ResultSet rs = fetch.executeQuery()) {
                    if (rs.next()) {
                        outTracking = rs.getString("tracking_number");
                        outWeight = rs.getDouble("weight_kg");
                        outLen = rs.getDouble("length_cm");
                        outWid = rs.getDouble("width_cm");
                        outHei = rs.getDouble("height_cm");
                        outStatus = rs.getString("package_status");
                    }
                }
            }

            conn.commit();

            AuditLogger.log(userId, session.username, "API_ORDER_EDIT", "success", clientIp,
                String.format("User %s edited package %s (ID %d)", session.username, outTracking, packageId));

            // Build response JSON (manual like rest of file)
            StringBuilder resp = new StringBuilder();
            resp.append("{");
            resp.append("\"tracking_number\":\"").append(escapeJson(outTracking)).append("\",");
            resp.append("\"weight\":").append(outWeight).append(",");
            resp.append("\"length\":").append(outLen).append(",");
            resp.append("\"width\":").append(outWid).append(",");
            resp.append("\"height\":").append(outHei).append(",");
            resp.append("\"status\":\"").append(escapeJson(outStatus)).append("\"");
            resp.append("}");

            respondJson(exchange, 200, resp.toString());

        } catch (NumberFormatException e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            System.err.println("Invalid numeric format: " + e.getMessage());
            AuditLogger.log(null, null, "API_ORDER_EDIT", "error", clientIp,
                "Invalid numeric format: " + e.getMessage());
            respondJson(exchange, 400, "{\"error\":\"Numeric fields must be valid numbers\"}");
        } catch (SQLException e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            e.printStackTrace();
            AuditLogger.log(null, session.username, "API_ORDER_EDIT", "error", clientIp,
                "Database error: " + e.getMessage());
            respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
        } finally {
            try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }
    // POST /package/edit-address
public static void handleEditAddress(HttpExchange exchange) throws IOException {
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

    // Validate session
    String token = extractToken(exchange);
    Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
    if (sessionResult.isErr()) {
        AuditLogger.log(null, null, "EDIT_ADDRESS", "denied", clientIp,
            "Session validation failed");
        respondJson(exchange, 401, "{\"error\":\"Unauthorized - Please log in\"}");
        return;
    }
    SessionManager.Session session = sessionResult.unwrap();

    // Parse request body
    String body = readStream(exchange.getRequestBody());
    Map<String, String> parsed = parseJson(body);

    String tracking = parsed.get("tracking_number");
    String streetAddress = parsed.get("street_address");
    String city = parsed.get("city");
    String state = parsed.get("state");
    String zipCode = parsed.get("zip_code");

    if (tracking == null || tracking.trim().isEmpty()) {
        respondJson(exchange, 400, "{\"error\":\"tracking_number is required\"}");
        return;
    }

    // At least one address field must be provided
    boolean hasAddressUpdate = (streetAddress != null && !streetAddress.trim().isEmpty())
                            || (city != null && !city.trim().isEmpty())
                            || (state != null && !state.trim().isEmpty())
                            || (zipCode != null && !zipCode.trim().isEmpty());

    if (!hasAddressUpdate) {
        respondJson(exchange, 400, "{\"error\":\"At least one address field must be provided\"}");
        return;
    }

    // Sanitize inputs
    SecurityManager.Result<String, String> tRes = InputSanitizer.sanitizeString(tracking);
    SecurityManager.Result<String, String> streetRes = streetAddress != null ? 
        InputSanitizer.sanitizeString(streetAddress) : SecurityManager.Result.ok(null);
    SecurityManager.Result<String, String> cityRes = city != null ? 
        InputSanitizer.sanitizeString(city) : SecurityManager.Result.ok(null);
    SecurityManager.Result<String, String> stateRes = state != null ? 
        InputSanitizer.sanitizeString(state) : SecurityManager.Result.ok(null);
    SecurityManager.Result<String, String> zipRes = zipCode != null ? 
        InputSanitizer.sanitizeString(zipCode) : SecurityManager.Result.ok(null);

    if (tRes.isErr() || streetRes.isErr() || cityRes.isErr() || stateRes.isErr() || zipRes.isErr()) {
        respondJson(exchange, 400, "{\"error\":\"Invalid input format\"}");
        return;
    }

    String sanitizedTracking = tRes.unwrap();
    String sanitizedStreet = streetRes.unwrap();
    String sanitizedCity = cityRes.unwrap();
    String sanitizedState = stateRes.unwrap();
    String sanitizedZip = zipRes.unwrap();

    // DB connection
    Result<Connection, String> connRes = DatabaseConnection.getConnection();
    if (connRes.isErr()) {
        respondJson(exchange, 500, "{\"error\":\"Server error\"}");
        return;
    }
    Connection conn = connRes.unwrap();

    try {
        conn.setAutoCommit(false);

        // Find package and get order's delivery address
        long packageId = -1;
        long orderId = -1;
        long addressId = -1;
        long ownerId = -1;
        
        String findQuery = 
            "SELECT p.package_id, p.order_id, o.delivery_address_id, o.customer_id " +
            "FROM packages p " +
            "JOIN orders o ON p.order_id = o.order_id " +
            "WHERE p.tracking_number = ?";

        try (PreparedStatement findStmt = conn.prepareStatement(findQuery)) {
            findStmt.setString(1, sanitizedTracking);
            try (ResultSet rs = findStmt.executeQuery()) {
                if (!rs.next()) {
                    conn.rollback();
                    respondJson(exchange, 404, "{\"error\":\"Package not found\"}");
                    return;
                }
                packageId = rs.getLong("package_id");
                orderId = rs.getLong("order_id");
                addressId = rs.getLong("delivery_address_id");
                ownerId = rs.getLong("customer_id");
            }
        }

        // Authorization: customer must own the package
        long userId = getUserId(conn, session.username);
        boolean isPrivileged = "manager".equals(session.role) || "admin".equals(session.role);
        
        if (!isPrivileged && userId != ownerId) {
            conn.rollback();
            respondJson(exchange, 403, "{\"error\":\"Forbidden - not owner of package\"}");
            return;
        }

        // Build dynamic update for address
        List<String> setParts = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (sanitizedStreet != null && !sanitizedStreet.isEmpty()) {
            setParts.add("street_address = ?");
            params.add(sanitizedStreet);
        }
        if (sanitizedCity != null && !sanitizedCity.isEmpty()) {
            setParts.add("city = ?");
            params.add(sanitizedCity);
        }
        if (sanitizedState != null && !sanitizedState.isEmpty()) {
            setParts.add("state = ?");
            params.add(sanitizedState);
        }
        if (sanitizedZip != null && !sanitizedZip.isEmpty()) {
            setParts.add("zip_code = ?");
            params.add(sanitizedZip);
        }

        if (setParts.isEmpty()) {
            conn.rollback();
            respondJson(exchange, 400, "{\"error\":\"No valid address fields to update\"}");
            return;
        }

        // Update the address
        String updateSql = "UPDATE addresses SET " + String.join(", ", setParts) + " WHERE address_id = ?";
        try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
            int idx = 1;
            for (Object p : params) {
                upd.setObject(idx++, p);
            }
            upd.setLong(idx, addressId);
            upd.executeUpdate();
        }

        // Log the change in package_edit_history
        String historySql = 
            "INSERT INTO package_edit_history (package_id, edited_by, field_name, old_value, new_value, edit_reason) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement hist = conn.prepareStatement(historySql)) {
            hist.setLong(1, packageId);
            hist.setLong(2, userId);
            hist.setString(3, "delivery_address");
            hist.setString(4, "Address updated");
            hist.setString(5, String.format("Street: %s, City: %s, State: %s, Zip: %s", 
                sanitizedStreet != null ? sanitizedStreet : "N/A",
                sanitizedCity != null ? sanitizedCity : "N/A",
                sanitizedState != null ? sanitizedState : "N/A",
                sanitizedZip != null ? sanitizedZip : "N/A"));
            hist.setString(6, "Customer updated delivery address");
            hist.executeUpdate();
        }

        // Fetch updated address
        String fetchSql = "SELECT street_address, city, state, zip_code FROM addresses WHERE address_id = ?";
        String outStreet = "", outCity = "", outState = "", outZip = "";
        
        try (PreparedStatement fetch = conn.prepareStatement(fetchSql)) {
            fetch.setLong(1, addressId);
            try (ResultSet rs = fetch.executeQuery()) {
                if (rs.next()) {
                    outStreet = rs.getString("street_address");
                    outCity = rs.getString("city");
                    outState = rs.getString("state");
                    outZip = rs.getString("zip_code");
                }
            }
        }

        conn.commit();

        AuditLogger.log(userId, session.username, "EDIT_ADDRESS", "success", clientIp,
            String.format("Updated delivery address for package %s", sanitizedTracking));

        // Build response
        StringBuilder resp = new StringBuilder();
        resp.append("{");
        resp.append("\"success\":true,");
        resp.append("\"tracking_number\":\"").append(escapeJson(sanitizedTracking)).append("\",");
        resp.append("\"delivery_address\":{");
        resp.append("\"street_address\":\"").append(escapeJson(outStreet)).append("\",");
        resp.append("\"city\":\"").append(escapeJson(outCity)).append("\",");
        resp.append("\"state\":\"").append(escapeJson(outState)).append("\",");
        resp.append("\"zip_code\":\"").append(escapeJson(outZip)).append("\"");
        resp.append("}}");

        respondJson(exchange, 200, resp.toString());

    } catch (SQLException e) {
        try { conn.rollback(); } catch (Exception ignored) {}
        e.printStackTrace();
        respondJson(exchange, 500, "{\"error\":\"Server error. Please try again later.\"}");
    } finally {
        try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
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
