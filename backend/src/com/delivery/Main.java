package com.delivery;

import com.delivery.controllers.AdminController;
import com.delivery.controllers.AuthenticationController;
import com.delivery.controllers.CustomerController;
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

        // Determine frontend directory path - go up from backend to find frontend
        String backendDir = Paths.get("").toAbsolutePath().toString();
        String frontendPath;
        if (backendDir.endsWith("backend")) {
            // Running from backend directory
            frontendPath = Paths.get(backendDir).getParent().resolve("frontend").toString();
        } else {
            // Running from project root
            frontendPath = Paths.get(backendDir, "frontend").toString();
        }

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
        System.out.println("  POST /login                      - User authentication");
        System.out.println("  POST /customer/register          - Customer registration");
        System.out.println("  GET  /whoami                     - Check session status");
        System.out.println("  GET  /admin/logs                 - View audit logs (Admin only)");
        System.out.println("  GET  /admin/users                - List all users (Admin only)");
        System.out.println("  PUT  /admin/users/:id/role       - Update user role (Admin only)");
        System.out.println("  PUT  /admin/users/:id/status     - Update account status (Admin only)");
        System.out.println("");
        System.out.println("Test credentials:");
        System.out.println("  customer1 / cust123   (Clearance: 0)");
        System.out.println("  driver1 / driver123   (Clearance: 1)");
        System.out.println("  manager1 / mgr123     (Clearance: 2)");
        System.out.println("  admin / admin123      (Clearance: 3)");
        System.out.println("========================================");
    }
}
