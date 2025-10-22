package com.delivery;

import com.delivery.controllers.AuthenticationController;
import com.delivery.util.EnvLoader;
import com.delivery.util.Result;
import com.delivery.security.AuditLogger;
import com.delivery.session.SessionManager;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        // Fixed: EnvLoader.get() returns Result<String, String>
        // Fixed: Use SERVER_PORT (not HTTP_PORT) to match .env.example
        Result<String, String> portResult = EnvLoader.get("SERVER_PORT");
        int port = 8080;  // Default to 8080 to match README
        if (portResult.isOk()) {
            try {
                port = Integer.parseInt(portResult.unwrap());
            } catch (NumberFormatException ignored) {}
        }

        //Create new HTTP server bound to the given port
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        //Endpoint login
        server.createContext("/login", (exchange) -> {
            AuthenticationController.handleLogin(exchange);
        });

        // Example protected endpoint to check session
        server.createContext("/whoami", (exchange) -> {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String token = null;
            //Attempt to extract token from "Cookie" header
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
            // Alternatively accept Authorization Bearer <token>
            if (token == null && exchange.getRequestHeaders().containsKey("Authorization")) {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth.startsWith("Bearer ")) token = auth.substring(7);
            }

            // Fixed: SessionManager.getSession() returns Result<Session, String>
            Result<SessionManager.Session, String> sessionResult = SessionManager.getSession(token);
            if (sessionResult.isErr()) {
                AuditLogger.log("<unknown>", "WHOAMI", "UNAUTHORIZED", sessionResult.unwrapErr());
                String resp = "{\"error\":\"unauthorized\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            SessionManager.Session s = sessionResult.unwrap();
            String resp = String.format("{\"username\":\"%s\",\"role\":\"%s\",\"clearance\":\"%s\"}", s.username, s.role, s.clearance.name());
            AuditLogger.log(s.username, "WHOAMI", "SUCCESS", "Session verified");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length());
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });

        server.setExecutor(Executors.newFixedThreadPool(8));
        //Start HTTP server
        server.start();

        System.out.println("========================================");
        System.out.println("Delivery System Server Started!");
        System.out.println("========================================");
        System.out.println("Server listening on: http://localhost:" + port);
        System.out.println("");
        System.out.println("Available endpoints:");
        System.out.println("  POST /login  - User authentication");
        System.out.println("  GET  /whoami - Check session status");
        System.out.println("");
        System.out.println("Test credentials:");
        System.out.println("  customer1 / cust123   (Clearance: 0)");
        System.out.println("  driver1 / driver123   (Clearance: 1)");
        System.out.println("  manager1 / mgr123     (Clearance: 2)");
        System.out.println("  admin / admin123      (Clearance: 3)");
        System.out.println("========================================");
    }
}
