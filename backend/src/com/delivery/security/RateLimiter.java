package com.delivery.security;

import com.delivery.util.Result;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiting to prevent brute force attacks and API abuse
 * Tracks requests per identifier:action pair with sliding 60-second windows
 */
public class RateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 2;
    private static final int LOGIN_MAX_REQUESTS = 5;
    private static final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    public static Result<Boolean, String> allowRequest(String identifier, String action) {
        if (identifier == null || action == null) {
            return Result.err("Identifier and action are required");
        }

        String key = identifier + ":" + action;
        int maxRequests = action.equalsIgnoreCase("LOGIN") ? LOGIN_MAX_REQUESTS : MAX_REQUESTS_PER_MINUTE;

        RateLimitInfo info = rateLimitMap.get(key);
        Instant now = Instant.now();

        if (info == null) {
            info = new RateLimitInfo(1, now);
            rateLimitMap.put(key, info);
            return Result.ok(true);
        }

        long secondsElapsed = now.getEpochSecond() - info.windowStart.getEpochSecond();

        // Reset counter if window expired, otherwise increment within current window
        if (secondsElapsed < 60) {
            if (info.requestCount >= maxRequests) {
                // Log rate limit violation for security monitoring (IP not available in rate limiter context)
                AuditLogger.logSecurityEvent(null, identifier, "RATE_LIMIT_EXCEEDED", null,
                    String.format("Rate limit exceeded on %s (%d requests in window)", action, info.requestCount));
                return Result.err(String.format("Rate limit exceeded. Max %d requests/minute.", maxRequests));
            }
            info.requestCount++;
        } else {
            info.requestCount = 1;
            info.windowStart = now;
        }

        return Result.ok(true);
    }

    public static Result<Void, String> clearRateLimit(String identifier) {
        if (identifier == null) {
            return Result.err("Identifier cannot be null");
        }

        rateLimitMap.entrySet().removeIf(entry -> entry.getKey().startsWith(identifier + ":"));

        // Log rate limit reset for audit trail
        AuditLogger.logSecurityEvent(null, identifier, "RATE_LIMIT_CLEARED", null,
            "Rate limit counters cleared");

        return Result.ok(null);
    }

    // Creates background thread to auto-expire ban after duration
    public static Result<Void, String> temporaryBan(String identifier, int durationSeconds) {
        if (identifier == null) {
            return Result.err("Identifier cannot be null");
        }

        String key = identifier + ":BANNED";
        RateLimitInfo info = new RateLimitInfo(Integer.MAX_VALUE, Instant.now());
        rateLimitMap.put(key, info);

        // Log temporary ban event for security monitoring
        AuditLogger.logSecurityEvent(null, identifier, "TEMPORARY_BAN", null,
            String.format("Identifier banned for %d seconds", durationSeconds));

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

    public static Result<Boolean, String> isBanned(String identifier) {
        if (identifier == null) {
            return Result.err("Identifier cannot be null");
        }

        boolean banned = rateLimitMap.containsKey(identifier + ":BANNED");
        return Result.ok(banned);
    }

    // Removes stale entries older than 2 minutes to prevent memory leaks
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



//fix