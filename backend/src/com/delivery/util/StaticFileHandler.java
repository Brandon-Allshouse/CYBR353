package com.delivery.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HTTP handler for serving static files from the frontend directory.
 * Supports HTML, CSS, JS, and other common web file types.
 */
public class StaticFileHandler implements HttpHandler {
    private final String frontendPath;

    public StaticFileHandler(String frontendPath) {
        this.frontendPath = frontendPath;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();

        // Default to index.html for root path
        if (requestPath.equals("/") || requestPath.isEmpty()) {
            requestPath = "/login.html";
        }

        // Handle SPA routing - if route doesn't exist, redirect to appropriate dashboard
        String filePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        Path path = Paths.get(frontendPath, filePath);
        File file = path.toFile();

        // Security check: prevent directory traversal attacks
        if (!file.getCanonicalPath().startsWith(new File(frontendPath).getCanonicalPath())) {
            sendResponse(exchange, 403, "Forbidden");
            return;
        }

        // If file doesn't exist, handle SPA routing
        if (!file.exists() || !file.isFile()) {
            String spaRedirect = handleSPARoute(requestPath);
            if (spaRedirect != null) {
                // Redirect to the appropriate dashboard HTML file
                filePath = spaRedirect.startsWith("/") ? spaRedirect.substring(1) : spaRedirect;
                path = Paths.get(frontendPath, filePath);
                file = path.toFile();

                // Check if redirect target exists
                if (!file.exists() || !file.isFile()) {
                    sendResponse(exchange, 404, "File not found: " + requestPath);
                    return;
                }
            } else {
                sendResponse(exchange, 404, "File not found: " + requestPath);
                return;
            }
        }

        try {
            // Read file content
            byte[] content = Files.readAllBytes(path);

            // Set content type based on file extension
            String contentType = getContentType(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);

            // Add CORS headers for development
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            // Send response
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        } catch (IOException e) {
            sendResponse(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handle SPA routing by mapping virtual routes to actual HTML files
     * Returns the file path to serve, or null if route is not recognized
     */
    private String handleSPARoute(String requestPath) {
        // Management routes
        if (requestPath.startsWith("/management/")) {
            return "/management/management-dashboard.html";
        }

        // Customer routes
        if (requestPath.startsWith("/customer/")) {
            return "/customer/customer-dashboard.html";
        }

        // Driver routes
        if (requestPath.startsWith("/driver/")) {
            return "/driver/driver-dashboard.html";
        }

        // Admin routes
        if (requestPath.startsWith("/admin/")) {
            return "/admin/admin-dashboard.html";
        }

        // No matching SPA route
        return null;
    }

    private String getContentType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] response = message.getBytes();
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }
}
