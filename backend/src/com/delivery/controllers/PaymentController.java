package com.delivery.controllers;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

// PaymentController - payment-related endpoints (if separate from OrdersController)
public class PaymentController {
    public static void handleProcessPayment(HttpExchange exchange) throws IOException {
        // TODO: Implement payment processing endpoint
        exchange.sendResponseHeaders(501, -1);
    }
}
