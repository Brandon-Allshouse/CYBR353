package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// RouteController - exposes route planning and assignment APIs
public class RouteController {
    public static void handlePlanRoutes(HttpExchange exchange) throws IOException {
        // TODO: Create planned routes from packages and drivers
        exchange.sendResponseHeaders(501, -1);
    }

    public static void handleGetRoute(HttpExchange exchange) throws IOException {
        // TODO: Return a specific route
        exchange.sendResponseHeaders(501, -1);
    }
}
