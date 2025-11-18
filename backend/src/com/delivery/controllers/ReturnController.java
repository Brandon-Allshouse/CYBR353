package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// ReturnController - handle returns and return processing
public class ReturnController {
    public static void handleRequestReturn(HttpExchange exchange) throws IOException {
        // TODO: Implement return request processing
        exchange.sendResponseHeaders(501, -1);
    }

    public static void handleProcessReturn(HttpExchange exchange) throws IOException {
        // TODO: Process return and update inventory
        exchange.sendResponseHeaders(501, -1);
    }
}
