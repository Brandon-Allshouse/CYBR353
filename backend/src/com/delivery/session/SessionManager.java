package com.delivery.session;

import com.delivery.security.SecurityLevel;
import com.delivery.util.Result;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session management with automatic expiry and extension
 * Sessions timeout after configured period (default 1 hour) and extend on each access
 */
public class SessionManager {
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final long timeoutSeconds;

    static {
        String t = System.getenv("SESSION_TIMEOUT_SECONDS");
        timeoutSeconds = (t != null) ? Long.parseLong(t) : 3600;
    }

    public static class Session {
        public final String username;
        public final SecurityLevel clearance;
        public final String role;
        public Instant expiry;

        public Session(String username, String role, SecurityLevel clearance, Instant expiry) {
            this.username = username;
            this.role = role;
            this.clearance = clearance;
            this.expiry = expiry;
        }
    }

    public static String createSession(String username, String role, SecurityLevel clearance) {
        String token = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(timeoutSeconds);
        sessions.put(token, new Session(username, role, clearance, expiry));
        return token;
    }

    // Sliding window expiry: each access extends session lifetime
    public static Result<Session, String> getSession(String token) {
        if (token == null || token.isEmpty()) {
            return Result.err("Token is required");
        }

        Session s = sessions.get(token);
        if (s == null) {
            return Result.err("Session not found");
        }

        if (Instant.now().isAfter(s.expiry)) {
            sessions.remove(token);
            return Result.err("Session expired");
        }

        s.expiry = Instant.now().plusSeconds(timeoutSeconds);
        return Result.ok(s);
    }

    public static void invalidate(String token) {
        sessions.remove(token);
    }
}