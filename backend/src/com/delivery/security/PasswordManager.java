package com.delivery.security;

import com.delivery.util.Result;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordManager {
    
    private static final SecureRandom random = new SecureRandom();
    private static final int SALT_LENGTH = 16;

    /**
     * Generates a cryptographically secure random salt
     */
    public static String generateSalt() {
        byte[] saltBytes = new byte[SALT_LENGTH];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * Hashes a password with the provided salt using SHA-256
     */
    public static Result<String, String> hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            return Result.err("Password and salt cannot be null");
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(password.getBytes());
            byte[] hashed = md.digest(salt.getBytes());
            return Result.ok(bytesToHex(hashed));
        } catch (NoSuchAlgorithmException e) {
            return Result.err("SHA-256 algorithm not available: " + e.getMessage());
        }
    }

    /**
     * Verifies a password against a stored hash
     */
    public static Result<Boolean, String> verifyPassword(String password, String salt, String expectedHash) {
        Result<String, String> hashResult = hashPassword(password, salt);
        
        if (hashResult.isErr()) {
            return Result.err(hashResult.unwrapErr());
        }
        
        String computedHash = hashResult.unwrap();
        boolean matches = computedHash.equalsIgnoreCase(expectedHash);
        
        return Result.ok(matches);
    }

    /**
     * Validates password strength according to security policy
     */
    public static Result<Boolean, String> validatePasswordStrength(String password) {
        if (password == null) {
            return Result.err("Password cannot be null");
        }
        if (password.length() < 8) {
            return Result.err("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            return Result.err("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            return Result.err("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            return Result.err("Password must contain at least one digit");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return Result.err("Password must contain at least one special character");
        }

        return Result.ok(true);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}