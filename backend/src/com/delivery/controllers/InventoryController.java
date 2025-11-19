package com.delivery.controllers;

import com.delivery.models.Facility;
import com.delivery.models.InventoryItem;
import com.delivery.security.SecurityManager.AuditLogger;
import com.delivery.services.InventoryService;
import com.delivery.session.SessionManager;
import com.delivery.util.Result;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * InventoryController - HTTP endpoints for inventory management
 * Handles requests for viewing facility inventory (managers only)
 */
public class InventoryController {

    /**
     * Handle GET /api/inventory - Get all inventory across all facilities
     * Requires SECRET clearance (manager or admin)
     */
    public static void handleGetAllInventory(HttpExchange exchange) throws IOException {
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

        // Get session and verify authentication
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, "<unknown>", "VIEW_INVENTORY", "denied", clientIp, sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Get inventory from service (includes BLP check)
        Result<List<InventoryItem>, String> inventoryResult =
            InventoryService.getAllInventory(session.clearance, session.username);

        if (inventoryResult.isErr()) {
            String error = inventoryResult.unwrapErr();
            if (error.contains("Access denied")) {
                AuditLogger.log(null, session.username, "VIEW_INVENTORY", "denied", clientIp,
                              "Insufficient clearance: " + session.clearance.name());
                respondJson(exchange, 403, "{\"error\":\"" + error + "\"}");
            } else {
                AuditLogger.log(null, session.username, "VIEW_INVENTORY", "error", clientIp, error);
                respondJson(exchange, 500, "{\"error\":\"" + error + "\"}");
            }
            return;
        }

        // Convert inventory items to JSON array
        List<InventoryItem> items = inventoryResult.unwrap();
        String json = inventoryListToJson(items);

        AuditLogger.log(null, session.username, "VIEW_INVENTORY", "success", clientIp,
                       "Retrieved " + items.size() + " inventory items");
        respondJson(exchange, 200, json);
    }

    /**
     * Handle GET /api/inventory/facility/:id - Get inventory for specific facility
     * Requires SECRET clearance (manager or admin)
     */
    public static void handleGetInventoryByFacility(HttpExchange exchange) throws IOException {
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

        // Extract facility ID from path: /api/inventory/facility/1
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 5) {
            respondJson(exchange, 400, "{\"error\":\"Facility ID required\"}");
            return;
        }

        long facilityId;
        try {
            facilityId = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            respondJson(exchange, 400, "{\"error\":\"Invalid facility ID\"}");
            return;
        }

        // Get session and verify authentication
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, "<unknown>", "VIEW_FACILITY_INVENTORY", "denied", clientIp,
                          sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Get inventory from service (includes BLP check)
        Result<List<InventoryItem>, String> inventoryResult =
            InventoryService.getInventoryByFacility(facilityId, session.clearance, session.username);

        if (inventoryResult.isErr()) {
            String error = inventoryResult.unwrapErr();
            if (error.contains("Access denied")) {
                AuditLogger.log(null, session.username, "VIEW_FACILITY_INVENTORY", "denied", clientIp,
                              "Insufficient clearance for facility " + facilityId);
                respondJson(exchange, 403, "{\"error\":\"" + error + "\"}");
            } else {
                AuditLogger.log(null, session.username, "VIEW_FACILITY_INVENTORY", "error", clientIp, error);
                respondJson(exchange, 500, "{\"error\":\"" + error + "\"}");
            }
            return;
        }

        // Convert inventory items to JSON array
        List<InventoryItem> items = inventoryResult.unwrap();
        String json = inventoryListToJson(items);

        AuditLogger.log(null, session.username, "VIEW_FACILITY_INVENTORY", "success", clientIp,
                       "Retrieved " + items.size() + " items for facility " + facilityId);
        respondJson(exchange, 200, json);
    }

    /**
     * Handle GET /api/facilities - Get list of all facilities
     * Requires CONFIDENTIAL clearance (driver or above)
     */
    public static void handleGetFacilities(HttpExchange exchange) throws IOException {
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

        // Get session and verify authentication
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, "<unknown>", "VIEW_FACILITIES", "denied", clientIp,
                          sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Get facilities from service (includes BLP check)
        Result<List<Facility>, String> facilitiesResult =
            InventoryService.getAllFacilities(session.clearance);

        if (facilitiesResult.isErr()) {
            String error = facilitiesResult.unwrapErr();
            if (error.contains("Access denied")) {
                AuditLogger.log(null, session.username, "VIEW_FACILITIES", "denied", clientIp,
                              "Insufficient clearance: " + session.clearance.name());
                respondJson(exchange, 403, "{\"error\":\"" + error + "\"}");
            } else {
                AuditLogger.log(null, session.username, "VIEW_FACILITIES", "error", clientIp, error);
                respondJson(exchange, 500, "{\"error\":\"" + error + "\"}");
            }
            return;
        }

        // Convert facilities to JSON array
        List<Facility> facilities = facilitiesResult.unwrap();
        String json = facilitiesListToJson(facilities);

        AuditLogger.log(null, session.username, "VIEW_FACILITIES", "success", clientIp,
                       "Retrieved " + facilities.size() + " facilities");
        respondJson(exchange, 200, json);
    }

    /**
     * Handle GET /api/inventory/search/:trackingNumber - Search inventory by tracking number
     * Requires SECRET clearance (manager or admin)
     */
    public static void handleSearchInventory(HttpExchange exchange) throws IOException {
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

        // Extract tracking number from path: /api/inventory/search/PKG1234567890
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        if (parts.length < 5) {
            respondJson(exchange, 400, "{\"error\":\"Tracking number required\"}");
            return;
        }

        String trackingNumber = parts[4];

        // Get session and verify authentication
        Result<SessionManager.Session, String> sessionResult = getSessionFromRequest(exchange);
        if (sessionResult.isErr()) {
            AuditLogger.log(null, "<unknown>", "SEARCH_INVENTORY", "denied", clientIp,
                          sessionResult.unwrapErr());
            respondJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }

        SessionManager.Session session = sessionResult.unwrap();

        // Search inventory from service (includes BLP check)
        Result<InventoryItem, String> itemResult =
            InventoryService.searchByTrackingNumber(trackingNumber, session.clearance, session.username);

        if (itemResult.isErr()) {
            String error = itemResult.unwrapErr();
            if (error.contains("Access denied")) {
                AuditLogger.log(null, session.username, "SEARCH_INVENTORY", "denied", clientIp,
                              "Insufficient clearance: " + session.clearance.name());
                respondJson(exchange, 403, "{\"error\":\"" + error + "\"}");
            } else if (error.contains("not found")) {
                AuditLogger.log(null, session.username, "SEARCH_INVENTORY", "success", clientIp,
                              "Tracking number not found: " + trackingNumber);
                respondJson(exchange, 404, "{\"error\":\"" + error + "\"}");
            } else {
                AuditLogger.log(null, session.username, "SEARCH_INVENTORY", "error", clientIp, error);
                respondJson(exchange, 500, "{\"error\":\"" + error + "\"}");
            }
            return;
        }

        // Convert item to JSON
        InventoryItem item = itemResult.unwrap();
        String json = item.toJson();

        AuditLogger.log(null, session.username, "SEARCH_INVENTORY", "success", clientIp,
                       "Found inventory item: " + trackingNumber);
        respondJson(exchange, 200, json);
    }

    // Helper methods

    /**
     * Extract session from HTTP request (cookie or Authorization header)
     */
    private static Result<SessionManager.Session, String> getSessionFromRequest(HttpExchange exchange) {
        String token = null;

        // Try cookie first
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

        // Try Authorization header if no cookie
        if (token == null && exchange.getRequestHeaders().containsKey("Authorization")) {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            }
        }

        if (token == null) {
            return Result.err("No session token provided");
        }

        return SessionManager.getSession(token);
    }

    /**
     * Convert list of InventoryItems to JSON array
     */
    private static String inventoryListToJson(List<InventoryItem> items) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < items.size(); i++) {
            json.append(items.get(i).toJson());
            if (i < items.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Convert list of Facilities to JSON array
     */
    private static String facilitiesListToJson(List<Facility> facilities) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < facilities.size(); i++) {
            Facility f = facilities.get(i);
            json.append("{");
            json.append("\"facilityId\":").append(f.facilityId).append(",");
            json.append("\"facilityName\":\"").append(escapeJson(f.facilityName)).append("\",");
            json.append("\"address\":\"").append(escapeJson(f.address)).append("\"");
            json.append("}");
            if (i < facilities.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Escape JSON strings
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    /**
     * Send JSON response
     */
    private static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
