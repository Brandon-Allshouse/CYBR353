package com.delivery;

import com.delivery.controllers.AdminController;
import com.delivery.controllers.OrdersController;
import com.delivery.controllers.PackageController;
import com.delivery.controllers.AuthenticationController;
import com.delivery.controllers.CustomerController;
import com.delivery.controllers.InventoryController;
import com.delivery.controllers.TransferController;
import com.delivery.controllers.DriverController;
import com.delivery.controllers.ManagementController;
import com.delivery.util.EnvLoader;
import com.delivery.util.Result;
import com.delivery.util.StaticFileHandler;
import com.delivery.security.SecurityManager.AuditLogger;
import com.delivery.session.SessionManager;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        Result<String, String> portResult = EnvLoader.get("SERVER_PORT");
        int port = 8081;
        if (portResult.isOk()) {
            try {
                port = Integer.parseInt(portResult.unwrap());
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API endpoint for login (POST requests)
        server.createContext("/api/login", (exchange) -> {
            AuthenticationController.handleLogin(exchange);
        });

        // Customer registration endpoint - Use Case 1: Create new customer account
        server.createContext("/api/customer/register", (exchange) -> {
            CustomerController.handleRegistration(exchange);
        });

        // Protected endpoint for session verification - demonstrates BLP clearance levels in response
        server.createContext("/whoami", (exchange) -> {
            // Capture client IP for audit logging
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

            // CORS headers required for browser-based clients (e.g., file:// protocol frontends)
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Support dual authentication: HTTP-only cookies (more secure) or Bearer tokens (API clients)
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
                if (auth.startsWith("Bearer ")) token = auth.substring(7);
            }

            Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
            if (sessionResult.isErr()) {
                AuditLogger.log(null, "<unknown>", "WHOAMI", "denied", clientIp, sessionResult.unwrapErr());
                String resp = "{\"error\":\"unauthorized\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            SessionManager.Session s = sessionResult.unwrap();
            String resp = String.format("{\"username\":\"%s\",\"role\":\"%s\",\"clearance\":\"%s\"}", s.username, s.role, s.clearance.name());
            AuditLogger.log(null, s.username, "WHOAMI", "success", clientIp, "Session verified");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length());
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });

        // Admin endpoints - require TOP_SECRET clearance (admin role)
        server.createContext("/admin/logs", AdminController::handleGetLogs);
        server.createContext("/admin/users", (exchange) -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/admin/users") && "GET".equalsIgnoreCase(method)) {
                AdminController.handleGetUsers(exchange);
            } else if (path.equals("/admin/users") && "OPTIONS".equalsIgnoreCase(method)) {
                AdminController.handleGetUsers(exchange);
            } else if (path.matches("/admin/users/\\d+/role")) {
                AdminController.handleUpdateUserRole(exchange);
            } else if (path.matches("/admin/users/\\d+/status")) {
                AdminController.handleUpdateUserStatus(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        // Inventory endpoints - require SECRET clearance (manager or admin)
        server.createContext("/api/inventory", (exchange) -> {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/inventory")) {
                // GET /api/inventory - Get all inventory
                InventoryController.handleGetAllInventory(exchange);
            } else if (path.startsWith("/api/inventory/facility/")) {
                // GET /api/inventory/facility/:id - Get inventory for specific facility
                InventoryController.handleGetInventoryByFacility(exchange);
            } else if (path.startsWith("/api/inventory/search/")) {
                // GET /api/inventory/search/:trackingNumber - Search by tracking number
                InventoryController.handleSearchInventory(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        // Facilities endpoint - require CONFIDENTIAL clearance (driver or above)
        server.createContext("/api/facilities", InventoryController::handleGetFacilities);

        // Determine frontend directory path - go up from backend to find frontend
        String backendDir = Paths.get("").toAbsolutePath().toString();
        String frontendPath;
        if (backendDir.contains("backend")) {
            // Running from backend directory or subdirectory (e.g., backend/src)
            // Navigate up to project root by removing everything from "backend" onwards
            String projectRoot = backendDir.substring(0, backendDir.indexOf("backend"));
            frontendPath = Paths.get(projectRoot, "frontend").toString();
        } else {
            // Running from project root
            frontendPath = Paths.get(backendDir, "frontend").toString();
        }

        // Order endpoints
        server.createContext("/api/order", (exchange) -> {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/order/place/")) {
                // POST /api/order/place - Place an order
                PackageController.handleCreatePackage(exchange);
            } else if (path.startsWith("/api/order/edit/")) {
                // POST /api/order/edit/:id - Edit order by ID
                PackageController.handleOrderEdit(exchange);
            } else if (path.startsWith("/api/order/get/")) {
                // GET /api/order/get/:id - Get order by ID
                OrdersController.handleGetOrder(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        // Transfer endpoints - require SECRET clearance (manager or admin)
        // Use Case 6: Transfer packages between facilities
        server.createContext("/api/transfers", (exchange) -> {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/transfers/initiate")) {
                // POST /api/transfers/initiate - Initiate a facility transfer
                TransferController.handleInitiateTransfer(exchange);
            } else if (path.startsWith("/api/transfers/complete/")) {
                // PUT /api/transfers/complete/:transferId - Complete a transfer
                TransferController.handleCompleteTransfer(exchange);
            } else if (path.equals("/api/transfers/pending")) {
                // GET /api/transfers/pending - Get all pending transfers
                TransferController.handleGetPendingTransfers(exchange);
            } else if (path.startsWith("/api/transfers/tracking/")) {
                // GET /api/transfers/tracking/:trackingNumber - Get transfer by tracking number
                TransferController.handleGetTransferByTracking(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        server.createContext("/api/trackPackages", (exchange) -> {
            PackageController.handleTrackPackage(exchange);
        });

        // Package management endpoints
        server.createContext("/api/package/edit", (exchange) -> {
            PackageController.handleEditPackage(exchange);
        });

        // Driver endpoints - require CONFIDENTIAL clearance (driver role)
        server.createContext("/api/driver/route", (exchange) -> {
            DriverController.handleGetRoute(exchange);
        });

        server.createContext("/api/driver/status", (exchange) -> {
            DriverController.handleUpdateDeliveryStatus(exchange);
        });

        // Management endpoints - require SECRET clearance (manager or admin)
        server.createContext("/api/management/assign-routes", (exchange) -> {
            ManagementController.handleAssignRoutes(exchange);
        });

        server.createContext("/api/management/inventory-report", (exchange) -> {
            ManagementController.handleInventoryReport(exchange);
        });

        // Static file handler - serves HTML, CSS, JS files from frontend directory
        // This must be registered LAST as it's a catch-all for unmatched routes
        server.createContext("/", new StaticFileHandler(frontendPath));

        // Thread pool sized for moderate concurrent load - adjust based on production needs
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("========================================");
        System.out.println("Delivery System Server Started!");
        System.out.println("========================================");
        System.out.println("Server listening on: http://localhost:" + port);
        System.out.println("Frontend directory: " + frontendPath);
        System.out.println("");
        System.out.println("Web Interface:");
        System.out.println("  http://localhost:" + port + "/");
        System.out.println("");
        System.out.println("API endpoints:");
        System.out.println("  POST /api/login                        - User authentication");
        System.out.println("  POST /api/customer/register            - Customer registration");
        System.out.println("  GET  /whoami                           - Check session status");
        System.out.println("  GET  /admin/logs                       - View audit logs (Admin only)");
        System.out.println("  GET  /admin/users                      - List all users (Admin only)");
        System.out.println("  PUT  /admin/users/:id/role             - Update user role (Admin only)");
        System.out.println("  PUT  /admin/users/:id/status           - Update account status (Admin only)");
        System.out.println("  GET  /api/inventory                    - Get all inventory (Manager+)");
        System.out.println("  GET  /api/inventory/facility/:id       - Get facility inventory (Manager+)");
        System.out.println("  GET  /api/inventory/search/:tracking   - Search by tracking number (Manager+)");
        System.out.println("  GET  /api/facilities                   - Get all facilities (Driver+)");
        System.out.println("  GET  /api/trackPackages                - Get Package tracking information (Customer+)");
        System.out.println("  POST /api/order/place/                 - Place a new package (Customer+)");
        System.out.println("  POST /api/order/edit/                  - Edit package details (Customer+)");
        System.out.println("  POST /api/package/edit                 - Edit package details (Manager+)");
        System.out.println("  GET  /api/driver/route                 - Get driver's assigned route (Driver)");
        System.out.println("  POST /api/driver/status                - Update delivery status (Driver)");
        System.out.println("  POST /api/management/assign-routes     - Assign routes to drivers (Manager+)");
        System.out.println("  GET  /api/management/inventory-report  - Get inventory reports (Manager+)");
        System.out.println("  POST /api/transfers/initiate           - Initiate facility transfer (Manager+)");
        System.out.println("  PUT  /api/transfers/complete/:id       - Complete transfer (Manager+)");
        System.out.println("  GET  /api/transfers/pending            - List pending transfers (Manager+)");
        System.out.println("  GET  /api/transfers/tracking/:num      - Get transfer by tracking (Manager+)");
        System.out.println("");
        System.out.println("Test credentials:");
        System.out.println("  customer1 / cust123   (Clearance: 0)");
        System.out.println("  driver1 / driver123   (Clearance: 1)");
        System.out.println("  manager1 / mgr123     (Clearance: 2)");
        System.out.println("  admin / admin123      (Clearance: 3)");
        System.out.println("========================================");
    }
}
