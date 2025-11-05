package com.delivery.security;

import com.delivery.util.Result;
import java.util.regex.Pattern;

public class InputSanitizer {
    
    // Regex patterns for validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[(]?[0-9]{3}[)]?[-\\s.]?[0-9]{3}[-\\s.]?[0-9]{4,6}$"
    );
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern TRACKING_ID_PATTERN = Pattern.compile("^D-[0-9]{3}-[0-9]{3}$");

    /**
     * Sanitizes string input by removing dangerous characters
     */
    public static Result<String, String> sanitizeString(String input) {
        if (input == null) {
            return Result.err("Input cannot be null");
        }

        // Remove SQL injection attempts
        String sanitized = input.replaceAll("['\"\\\\;]", "");
        
        // Remove script tags and HTML
        sanitized = sanitized.replaceAll("<script.*?>.*?</script>", "");
        sanitized = sanitized.replaceAll("<.*?>", "");
        
        // Remove command injection characters
        sanitized = sanitized.replaceAll("[;&|`$()]", "");
        
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        
        return Result.ok(sanitized.trim());
    }

    /**
     * Validates email format
     */
    public static Result<Boolean, String> validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Result.err("Email cannot be empty");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return Result.err("Invalid email format");
        }
        
        return Result.ok(true);
    }

    /**
     * Validates phone number format
     */
    public static Result<Boolean, String> validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return Result.err("Phone number cannot be empty");
        }
        
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return Result.err("Invalid phone number format");
        }
        
        return Result.ok(true);
    }

    /**
     * Validates username format
     */
    public static Result<Boolean, String> validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Result.err("Username cannot be empty");
        }
        
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return Result.err("Username must be 3-20 characters (letters, numbers, underscore only)");
        }
        
        return Result.ok(true);
    }

    /**
     * Validates tracking ID format
     */
    public static Result<Boolean, String> validateTrackingId(String trackingId) {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            return Result.err("Tracking ID cannot be empty");
        }
        
        if (!TRACKING_ID_PATTERN.matcher(trackingId).matches()) {
            return Result.err("Invalid tracking ID format (expected: D-XXX-XXX)");
        }
        
        return Result.ok(true);
    }

    /**
     * Validates URL format
     */
    public static Result<Boolean, String> validateURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            return Result.err("URL cannot be empty");
        }

        String lowerUrl = url.toLowerCase();
        
        // Check for dangerous protocols
        if (lowerUrl.startsWith("javascript:") || 
            lowerUrl.startsWith("data:") || 
            lowerUrl.startsWith("vbscript:")) {
            return Result.err("Dangerous URL protocol detected");
        }

        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return Result.err("URL must start with http:// or https://");
        }

        return Result.ok(true);
    }

    /**
     * Validates string length
     */
    public static Result<Boolean, String> validateLength(String input, int maxLength) {
        if (input == null) {
            return Result.err("Input cannot be null");
        }

        if (input.length() > maxLength) {
            return Result.err("Input exceeds maximum length of " + maxLength + " characters");
        }

        return Result.ok(true);
    }

    /**
     * Validates integer within range
     */
    public static Result<Boolean, String> validateIntegerRange(int value, int min, int max) {
        if (value < min || value > max) {
            return Result.err("Value must be between " + min + " and " + max);
        }
        return Result.ok(true);
    }

    /**
     * Encodes HTML entities to prevent XSS
     */
    public static Result<String, String> encodeHTML(String input) {
        if (input == null) {
            return Result.err("Input cannot be null");
        }

        String encoded = input.replace("&", "&amp;")
                             .replace("<", "&lt;")
                             .replace(">", "&gt;")
                             .replace("\"", "&quot;")
                             .replace("'", "&#x27;")
                             .replace("/", "&#x2F;");

        return Result.ok(encoded);
    }
}