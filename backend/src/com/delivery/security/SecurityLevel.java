package com.delivery.security;

import com.delivery.util.Result;

public enum SecurityLevel {
    UNCLASSIFIED(0),
    CONFIDENTIAL(1),
    SECRET(2),
    TOP_SECRET(3);

    private final int level;

    SecurityLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    // Parses string to SecurityLevel or returns error
    public static Result<SecurityLevel, String> fromString(String s) {
        if (s == null || s.trim().isEmpty()) {
            return Result.err("Security level string cannot be null or empty");
        }
        try {
            return Result.ok(SecurityLevel.valueOf(s.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Result.err("Invalid security level: " + s);
        }
    }

    // Converts clearance level integer (0-3) to SecurityLevel
    public static Result<SecurityLevel, String> fromInt(int level) {
        for (SecurityLevel sl : SecurityLevel.values()) {
            if (sl.level == level) {
                return Result.ok(sl);
            }
        }
        return Result.err("Invalid clearance level: " + level);
    }
}
