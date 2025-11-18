package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// PackageController - package-specific operations (tracking, edits, status)
public class PackageController {
    public static void handleTrackPackage(HttpExchange exchange) throws IOException {
        // TODO: Implement tracking lookup by tracking number
        exchange.sendResponseHeaders(501, -1);
    }

    public static void handleEditPackage(HttpExchange exchange) throws IOException {
        // TODO: Implement package edit (with BLP checks)
        exchange.sendResponseHeaders(501, -1);
    }
}
