package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// TransferController - manage transfers between facilities
public class TransferController {
    public static void handleInitiateTransfer(HttpExchange exchange) throws IOException {
        // TODO: Start a transfer for packages
        exchange.sendResponseHeaders(501, -1);
    }

    public static void handleCompleteTransfer(HttpExchange exchange) throws IOException {
        // TODO: Complete a transfer and update inventory
        exchange.sendResponseHeaders(501, -1);
    }
}
