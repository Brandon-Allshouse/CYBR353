package com.delivery.controllers;

import com.delivery.services.RouteService;
import com.delivery.models.RouteRequest;
import com.delivery.models.OptimizedRoute;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * RouteController - Handles route planning and optimization
 */
public class RouteController {
    private static final RouteService routeService = new RouteService();

    /**
     * POST /api/routes/optimize
     * Optimizes a delivery route based on warehouse and delivery addresses
     */
    public static void handleOptimizeRoute(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        try {
            // Read request body
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            RouteRequest request = RouteRequest.fromJson(requestBody);

            // Optimize route
            OptimizedRoute optimizedRoute = routeService.optimizeRoute(request);

            // Send response
            String jsonResponse = optimizedRoute.toJson();
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();

        } catch (Exception e) {
            String errorJson = "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            byte[] errorBytes = errorJson.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, errorBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(errorBytes);
            os.close();
        }
    }

    /**
     * GET /api/routes/driver/{driverId}
     * Gets assigned routes for a specific driver
     */
    public static void handleGetDriverRoute(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        try {
            // Extract driverId from path (e.g., /api/routes/driver/123)
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            long driverId = Long.parseLong(parts[parts.length - 1]);

            // Get driver routes
            String routesJson = routeService.getDriverRoutes(driverId);

            byte[] responseBytes = routesJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();

        } catch (Exception e) {
            String errorJson = "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            byte[] errorBytes = errorJson.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, errorBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(errorBytes);
            os.close();
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