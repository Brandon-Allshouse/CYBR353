package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// InventoryController - facility inventory operations
public class InventoryController {
    public static void handleCheckInventory(HttpExchange exchange) throws IOException {
        // TODO: Return inventory list for a facility
        exchange.sendResponseHeaders(501, -1);
    }

    public static void handleCreateTransfer(HttpExchange exchange) throws IOException {
        // TODO: Create a package transfer between facilities
        exchange.sendResponseHeaders(501, -1);
    }
}
