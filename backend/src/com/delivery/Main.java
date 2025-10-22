package com.delivery;

import com.delivery.controllers.AuthenticationController;
import com.delivery.util.EnvLoader;
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
        String portStr = EnvLoader.get("HTTP_PORT");
        int port = 8000;
        if (portStr != null) {
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/login", (exchange) -> {
            AuthenticationController.handleLogin(exchange);
        });

        // Example protected endpoint to check session
        server.createContext("/whoami", (exchange) -> {
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
            // Alternatively accept Authorization Bearer <token>
            if (token == null && exchange.getRequestHeaders().containsKey("Authorization")) {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth.startsWith("Bearer ")) token = auth.substring(7);
            }

            SessionManager.Session s = SessionManager.getSession(token);
            if (s == null) {
                AuditLogger.log("<unknown>", "WHOAMI", "-", "UNAUTHORIZED");
                String resp = "{\"error\":\"unauthorized\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            String resp = String.format("{\"username\":\"%s\",\"role\":\"%s\",\"clearance\":\"%s\"}", s.username, s.role, s.clearance.name());
            AuditLogger.log(s.username, "WHOAMI", "-", "SUCCESS");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length());
            exchange.getResponseBody().write(resp.getBytes());
            exchange.getResponseBody().close();
        });

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Server started on port " + port);
    }
}
