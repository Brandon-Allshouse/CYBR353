package com.delivery.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing utilities using SHA-256(password + salt)
 * IMPORTANT: Hash algorithm must match schema.sql test user generation
 */
public class PasswordUtil {
    private static final SecureRandom random = new SecureRandom();

    public static String generateSalt() {
        byte[] s = new byte[16];
        random.nextBytes(s);
        return Base64.getEncoder().encodeToString(s);
    }

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
            return Result.err("SHA-256 algorithm not available");
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
