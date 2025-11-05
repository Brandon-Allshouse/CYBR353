package com.delivery.security;

import com.delivery.util.ValidationResult;

/**
 * Validates all user inputs according to business rules and security policies
 */
public class InputValidator {
    
    public static ValidationResult validateRegistration(String name, String email, 
                                                       String phone, String password) {
        ValidationResult result = new ValidationResult();
        
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            result.addError("Name cannot be empty");
        } else if (name.length() > 100) {
            result.addError("Name too long (max 100 characters)");
        } else if (!name.matches("^[a-zA-Z\\s'-]+$")) {
            result.addError("Name contains invalid characters");
        }
        
        // Validate email
        if (!InputSanitizer.isValidEmail(email)) {
            result.addError("Invalid email format");
        }
        
        // Validate phone
        if (!InputSanitizer.isValidPhone(phone)) {
            result.addError("Invalid phone number format");
        }
        
        // Validate password
        if (!PasswordManager.validatePasswordStrength(password)) {
            result.addError("Password must be at least 12 characters with uppercase, lowercase, digit, and special character");
        }
        
        return result;
    }
    
    public static ValidationResult validatePackageOrder(String pickupAddress, 
                                                       String deliveryAddress, 
                                                       String packageDetails) {
        ValidationResult result = new ValidationResult();
        
        if (pickupAddress == null || pickupAddress.trim().isEmpty()) {
            result.addError("Pickup address is required");
        } else if (pickupAddress.length() > 255) {
            result.addError("Pickup address too long (max 255 characters)");
        }
        
        if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
            result.addError("Delivery address is required");
        } else if (deliveryAddress.length() > 255) {
            result.addError("Delivery address too long (max 255 characters)");
        }
        
        if (packageDetails == null || packageDetails.trim().isEmpty()) {
            result.addError("Package details are required");
        } else if (packageDetails.length() > 500) {
            result.addError("Package details too long (max 500 characters)");
        }
        
        return result;
    }
    
    public static ValidationResult validateTrackingId(String trackingId) {
        ValidationResult result = new ValidationResult();
        
        if (!InputSanitizer.isValidTrackingId(trackingId)) {
            result.addError("Invalid tracking ID format (expected: D-XXX-XXX)");
        }
        
        return result;
    }
    
    public static ValidationResult validateLoginCredentials(String email, String password) {
        ValidationResult result = new ValidationResult();
        
        if (email == null || email.trim().isEmpty()) {
            result.addError("Email is required");
        } else if (!InputSanitizer.isValidEmail(email)) {
            result.addError("Invalid email format");
        }
        
        if (password == null || password.isEmpty()) {
            result.addError("Password is required");
        }
        
        return result;
    }
    
    public static ValidationResult validateRouteAssignment(int routeId, int driverId, String routeDate) {
        ValidationResult result = new ValidationResult();
        
        if (routeId <= 0) {
            result.addError("Invalid route ID");
        }
        
        if (driverId <= 0) {
            result.addError("Invalid driver ID");
        }
        
        if (routeDate == null || !routeDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            result.addError("Invalid date format (expected: YYYY-MM-DD)");
        }
        
        return result;
    }
    
    public static ValidationResult validateInventoryTransfer(int sourceFacilityId, 
                                                            int destFacilityId, 
                                                            java.util.List<String> packageIds) {
        ValidationResult result = new ValidationResult();
        
        if (sourceFacilityId <= 0) {
            result.addError("Invalid source facility ID");
        }
        
        if (destFacilityId <= 0) {
            result.addError("Invalid destination facility ID");
        }
        
        if (sourceFacilityId == destFacilityId) {
            result.addError("Source and destination facilities must be different");
        }
        
        if (packageIds == null || packageIds.isEmpty()) {
            result.addError("At least one package must be specified");
        } else if (packageIds.size() > 100) {
            result.addError("Too many packages (max 100 per transfer)");
        }
        
        return result;
    }
    
    public static ValidationResult validateUserUpdate(int userId, String name, String phone) {
        ValidationResult result = new ValidationResult();
        
        if (userId <= 0) {
            result.addError("Invalid user ID");
        }
        
        if (name != null && !name.isEmpty()) {
            if (name.length() > 100) {
                result.addError("Name too long (max 100 characters)");
            } else if (!name.matches("^[a-zA-Z\\s'-]+$")) {
                result.addError("Name contains invalid characters");
            }
        }
        
        if (phone != null && !phone.isEmpty()) {
            if (!InputSanitizer.isValidPhone(phone)) {
                result.addError("Invalid phone number format");
            }
        }
        
        return result;
    }
    
    public static ValidationResult validateMFACode(String code) {
        ValidationResult result = new ValidationResult();
        
        if (code == null || code.length() != 6) {
            result.addError("MFA code must be 6 digits");
        } else if (!code.matches("\\d{6}")) {
            result.addError("MFA code must contain only digits");
        }
        
        return result;
    }
    
    public static ValidationResult validateDeliveryInstructions(String instructions) {
        ValidationResult result = new ValidationResult();
        
        if (instructions != null && instructions.length() > 500) {
            result.addError("Delivery instructions too long (max 500 characters)");
        }
        
        return result;
    }
    
    public static ValidationResult validateFacilityId(int facilityId) {
        ValidationResult result = new ValidationResult();
        
        if (facilityId <= 0) {
            result.addError("Invalid facility ID");
        }
        
        return result;
    }
}
