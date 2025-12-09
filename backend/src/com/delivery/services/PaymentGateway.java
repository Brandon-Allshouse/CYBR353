package com.delivery.services;

// PaymentGateway - test-friendly payment stub
public class PaymentGateway {
    public static boolean charge(String paymentMethod, double amount) {
        // TODO: Integrate with real payment provider for production
        System.out.println("[PaymentGateway] charge method=" + paymentMethod + " amount=" + amount);
        return true; // simulate success
    }
}
