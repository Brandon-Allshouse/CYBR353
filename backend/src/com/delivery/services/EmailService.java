package com.delivery.services;

// EmailService - simple stub to send emails (2FA, confirmations)
public class EmailService {
    public static boolean sendEmail(String to, String subject, String body) {
        // TODO: Replace with real implementation or integration
        System.out.println("[EmailService] sendEmail to=" + to + " subject=" + subject);
        return true;
    }
}
