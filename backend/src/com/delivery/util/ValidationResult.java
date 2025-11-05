package com.delivery.util;

/**
 * ValidationResult - Encapsulates validation results with error messages
 * Used throughout the system for input validation
 */
public class ValidationResult {
    private boolean valid = true;
    private StringBuilder errors = new StringBuilder();
    
    public void addError(String error) {
        valid = false;
        if (errors.length() > 0) {
            errors.append("; ");
        }
        errors.append(error);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getErrors() {
        return errors.toString();
    }
    
    public boolean hasErrors() {
        return !valid;
    }
    
    public void reset() {
        valid = true;
        errors = new StringBuilder();
    }
}