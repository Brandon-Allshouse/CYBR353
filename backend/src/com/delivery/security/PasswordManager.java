package com.delivery.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class PasswordManager {
    
    // Security configuration constants
    private static final int SALT_LENGTH = 32;
    private static final int HASH_ITERATIONS = 10000;
    private static final int HASH_LENGTH = 256;
    
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    public static String hashPassword(String password, String salt) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), 
            saltBytes, 
            HASH_ITERATIONS, 
            HASH_LENGTH
        );
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        
        // Clear sensitive data
        spec.clearPassword();
        
        return Base64.getEncoder().encodeToString(hash);
    }
    
    public static boolean verifyPassword(String password, String salt, String expectedHash) {
        try {
            String computedHash = hashPassword(password, salt);
            return computedHash.equals(expectedHash);
        } catch (Exception e) {
            AuditLogger.logSecurityEvent("PASSWORD_VERIFICATION_ERROR", 
                "Error verifying password", "SYSTEM");
            return false;
        }
    }
    
    public static boolean validatePasswordStrength(String password) {
        if (password == null || password.length() < 12) {
            return false;
        }
        
        // Check for uppercase, lowercase, digit, and special character
        boolean hasUpper = !password.equals(password.toLowerCase());
        boolean hasLower = !password.equals(password.toUpperCase());
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
    
    public static int getPasswordStrength(String password) {
        if (password == null) return 0;
        
        int score = 0;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score++;
        
        return Math.min(score, 4);
    }
}