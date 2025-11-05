package com.delivery.security;

import com.delivery.util.Result;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int LOGIN_MAX_REQUESTS = 5; // Stricter for login
    private static final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Checks if a request should be allowed
     */
    public static Result<Boolean, String> allowRequest(String identifier, String action) {
        if (identifier == null || action == null) {
            return Result.err("Identifier and action are required");
        }

        String key = identifier + ":" + action;
        int maxRequests = action.equalsIgnoreCase("LOGIN") ? LOGIN_MAX_REQUESTS : MAX_REQUESTS_PER_MINUTE;
        
        RateLimitInfo info = rateLimitMap.get(key);
        Instant now = Instant.now();

        if (info == null) {
            // First request
            info = new RateLimitInfo(1, now);
            rateLimitMap.put(key, info);
            return Result.ok(true);
        }

        long secondsElapsed = now.getEpochSecond() - info.windowStart.getEpochSecond();
        
        if (secondsElapsed < 60) {
            if (info.requestCount >= maxRequests) {
                AuditLogger.logSecurityEvent("RATE_LIMIT_EXCEEDED",
                    String.format("Rate limit exceeded for %s on %s (%d requests)", 
                                identifier, action, info.requestCount),
                    identifier);
                return Result.err(String.format("Rate limit exceeded. Max %d requests/minute.", maxRequests));
            }
            info.requestCount++;
        } else {
            // New time window
            info.requestCount = 1;
            info.windowStart = now;
        }

        return Result.ok(true);
    }

    /**
     * Clears rate limit for an identifier
     */
    public static Result<Void, String> clearRateLimit(String identifier) {
        if (identifier == null) {
            return Result.err("Identifier cannot be null");
        }

        rateLimitMap.entrySet().removeIf(entry -> entry.getKey().startsWith(identifier + ":"));
        
        AuditLogger.logSecurityEvent("RATE_LIMIT_CLEARED",
            "Rate limit cleared for " + identifier, identifier);
        
        return Result.ok(null);
    }

    /**
     * Temporarily bans an identifier
     */
    public static Result<Void, String> temporaryBan(String identifier, int durationSeconds) {
        if (identifier == null) {
            return Result.err("Identifier cannot be null");
        }

        String key = identifier + ":BANNED";
        RateLimitInfo info = new RateLimitInfo(Integer.MAX_VALUE, Instant.now());
        rateLimitMap.put(key, info);

        AuditLogger.logSecurityEvent("TEMPORARY_BAN",
            String.format("Identifier %s banned for %d seconds", identifier, durationSeconds),
            identifier);

        // Schedule removal
        new Thread(() -> {
            try {
                Thread.sleep(durationSeconds * 1000L);
                rateLimitMap.remove(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return Result.ok(null);
    }

    /**
     * Checks if identifier is banned
     */
    public static Result<Boolean, String> isBanned(String identifier) {
        if (identifier == null) {
            return Result.err("Identifier cannot be null");
        }

        boolean banned = rateLimitMap.containsKey(identifier + ":BANNED");
        return Result.ok(banned);
    }

    /**
     * Cleanup old rate limit entries
     */
    public static Result<Integer, String> cleanupRateLimits() {
        Instant cutoff = Instant.now().minusSeconds(120);
        int removed = 0;

        var iterator = rateLimitMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().windowStart.isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }

        return Result.ok(removed);
    }

    private static class RateLimitInfo {
        int requestCount;
        Instant windowStart;

        RateLimitInfo(int requestCount, Instant windowStart) {
            this.requestCount = requestCount;
            this.windowStart = windowStart;
        }
    }
}
