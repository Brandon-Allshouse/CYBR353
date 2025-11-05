package com.delivery.security;

import java.util.regex.Pattern;

public class InputSanitizer {
    
    // Regex patterns for validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[(]?[0-9]{3}[)]?[-\\s.]?[0-9]{3}[-\\s.]?[0-9]{4,6}$"
    );
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s]+$");
    private static final Pattern TRACKING_ID_PATTERN = Pattern.compile("^D-[0-9]{3}-[0-9]{3}$");
    
    public static String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove SQL injection attempts
        String sanitized = input.replaceAll("['\"\\\\;]", "");
        
        // Remove script tags and HTML
        sanitized = sanitized.replaceAll("<script.*?>.*?</script>", "");
        sanitized = sanitized.replaceAll("<.*?>", "");
        
        // Remove potential command injection characters
        sanitized = sanitized.replaceAll("[;&|`$()]", "");
        
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        
        return sanitized.trim();
    }
    
 
    public static String sanitizeForSQL(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove SQL dangerous characters
        String sanitized = input.replaceAll("['\"\\\\]", "");
        sanitized = sanitized.replaceAll("--", "");
        sanitized = sanitized.replaceAll(";", "");
        sanitized = sanitized.replaceAll("/\\*.*?\\*/", ""); // Remove SQL comments
        
        return sanitized.trim();
    }
    
 
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    
    public static boolean isValidTrackingId(String trackingId) {
        return trackingId != null && TRACKING_ID_PATTERN.matcher(trackingId).matches();
    }
    
    public static boolean isAlphanumeric(String input) {
        return input != null && ALPHANUMERIC_PATTERN.matcher(input).matches();
    }
    
    public static boolean isValidIntegerRange(int value, int min, int max) {
        return value >= min && value <= max;
    }
    
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        
        // Remove path traversal attempts
        String sanitized = filename.replaceAll("\\.\\.", "");
        sanitized = sanitized.replaceAll("[/\\\\]", "");
        
        // Remove dangerous characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "");
        
        return sanitized.trim();
    }
    public static boolean isValidURL(String url) {
        if (url == null) {
            return false;
        }
        
        // Check for dangerous protocols
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.startsWith("javascript:") || 
            lowerUrl.startsWith("data:") || 
            lowerUrl.startsWith("vbscript:")) {
            return false;
        }
        
        // Must start with http or https
        return lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://");
    }
    
    public static String encodeHTML(String input) {
        if (input == null) {
            return null;
        }
        
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }
    
    public static boolean isValidLength(String input, int maxLength) {
        return input != null && input.length() <= maxLength;
    }
    
    public static String sanitizeAddress(String address) {
        if (address == null) {
            return null;
        }
        
        // Remove dangerous characters
        String sanitized = address.replaceAll("[<>\"';\\\\]", "");
        return sanitized.trim();
    }
}