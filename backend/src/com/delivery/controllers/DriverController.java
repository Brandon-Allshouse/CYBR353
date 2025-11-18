package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// DriverController - endpoints for drivers to retrieve routes and update delivery status
public class DriverController {
    public static void handleGetRoute(HttpExchange exchange) throws IOException {
        // TODO: Return assigned route for authenticated driver
        exchange.sendResponseHeaders(501, -1);
    }

    public static void handleUpdateDeliveryStatus(HttpExchange exchange) throws IOException {
        // TODO: Driver updates package delivery status (delivered, exception, etc.)
        exchange.sendResponseHeaders(501, -1);
    }
}
