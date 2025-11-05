package com.delivery.util;

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