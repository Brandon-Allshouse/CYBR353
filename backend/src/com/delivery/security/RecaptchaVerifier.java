package com.delivery.security;

import com.delivery.util.EnvLoader;
import com.delivery.util.Result;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Google reCAPTCHA v2 verification service
 * Validates reCAPTCHA tokens submitted from frontend forms
 *
 * reCAPTCHA v2 prevents bot attacks and automated abuse
 * Site key (public) is embedded in frontend, secret key is server-side only
 */
public class RecaptchaVerifier {

    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    /**
     * Verifies a reCAPTCHA v2 response token with Google's API
     *
     * @param recaptchaToken The g-recaptcha-response token from frontend
     * @param clientIp The IP address of the client (optional but recommended)
     * @return Result<Boolean, String> - true if verification passed, error message otherwise
     */
    public static Result<Boolean, String> verifyRecaptcha(String recaptchaToken, String clientIp) {
        if (recaptchaToken == null || recaptchaToken.trim().isEmpty()) {
            return Result.err("reCAPTCHA token is required");
        }

        // Get secret key from environment
        Result<String, String> secretResult = EnvLoader.get("RECAPTCHA_SECRET_KEY");
        if (secretResult.isErr()) {
            // Log error but don't expose to user
            System.err.println("RECAPTCHA_SECRET_KEY not configured: " + secretResult.unwrapErr());
            return Result.err("reCAPTCHA verification is not configured on server");
        }

        String secretKey = secretResult.unwrap();

        try {
            // Build POST request parameters
            StringBuilder postData = new StringBuilder();
            postData.append("secret=").append(URLEncoder.encode(secretKey, StandardCharsets.UTF_8));
            postData.append("&response=").append(URLEncoder.encode(recaptchaToken, StandardCharsets.UTF_8));

            if (clientIp != null && !clientIp.isEmpty()) {
                postData.append("&remoteip=").append(URLEncoder.encode(clientIp, StandardCharsets.UTF_8));
            }

            // Send POST request to Google's verification endpoint
            URL url = URI.create(RECAPTCHA_VERIFY_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postData.length()));
            conn.setDoOutput(true);

            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return Result.err("reCAPTCHA verification service returned error: " + responseCode);
            }

            // Parse JSON response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            String jsonResponse = response.toString();

            // Simple JSON parsing - look for "success": true
            // More robust would use a JSON library, but avoiding dependencies
            boolean success = jsonResponse.contains("\"success\": true") ||
                            jsonResponse.contains("\"success\":true");

            if (success) {
                AuditLogger.logSecurityEvent(null, "SYSTEM", "RECAPTCHA_VERIFIED", clientIp,
                    "reCAPTCHA verification successful");
                return Result.ok(true);
            } else {
                // Extract error codes if present (for logging)
                String errorInfo = extractErrors(jsonResponse);
                AuditLogger.logSecurityEvent(null, "SYSTEM", "RECAPTCHA_FAILED", clientIp,
                    "reCAPTCHA verification failed: " + errorInfo);
                return Result.err("reCAPTCHA verification failed. Please try again.");
            }

        } catch (Exception e) {
            System.err.println("reCAPTCHA verification error: " + e.getMessage());
            e.printStackTrace();
            AuditLogger.logError("RECAPTCHA_ERROR", e.getMessage(), "SYSTEM", clientIp,
                e.getStackTrace().toString());
            return Result.err("reCAPTCHA verification error. Please try again.");
        }
    }

    /**
     * Extracts error codes from Google's JSON response for logging
     * Common error codes:
     * - missing-input-secret: Secret key is missing
     * - invalid-input-secret: Secret key is invalid
     * - missing-input-response: Response token is missing
     * - invalid-input-response: Response token is invalid or expired
     * - bad-request: Request is malformed
     * - timeout-or-duplicate: Token has already been used
     */
    private static String extractErrors(String jsonResponse) {
        try {
            // Look for "error-codes": ["..."]
            int errorStart = jsonResponse.indexOf("\"error-codes\"");
            if (errorStart == -1) {
                return "unknown error";
            }

            int arrayStart = jsonResponse.indexOf("[", errorStart);
            int arrayEnd = jsonResponse.indexOf("]", arrayStart);

            if (arrayStart != -1 && arrayEnd != -1) {
                String errors = jsonResponse.substring(arrayStart + 1, arrayEnd);
                // Remove quotes and clean up
                errors = errors.replaceAll("\"", "").trim();
                return errors.isEmpty() ? "unknown error" : errors;
            }

            return "unknown error";
        } catch (Exception e) {
            return "error parsing response";
        }
    }

    /**
     * Validates reCAPTCHA configuration is present in environment
     * Should be called on server startup to fail fast if misconfigured
     */
    public static Result<Void, String> validateConfiguration() {
        Result<String, String> secretResult = EnvLoader.get("RECAPTCHA_SECRET_KEY");
        if (secretResult.isErr()) {
            return Result.err("RECAPTCHA_SECRET_KEY not found in .env file");
        }

        String secret = secretResult.unwrap();
        if (secret.trim().isEmpty()) {
            return Result.err("RECAPTCHA_SECRET_KEY is empty");
        }

        // Verify it's not the example/placeholder value
        if (secret.equals("your_recaptcha_secret_key_here")) {
            return Result.err("RECAPTCHA_SECRET_KEY is still set to placeholder value");
        }

        return Result.ok(null);
    }
}
