package com.delivery.session;

import com.delivery.security.SecurityLevel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final long timeoutSeconds;

    static {
        String t = System.getenv("SESSION_TIMEOUT_SECONDS");
        if (t == null) t = "3600";
        timeoutSeconds = Long.parseLong(t);
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

    public static Session getSession(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (Instant.now().isAfter(s.expiry)) {
            sessions.remove(token);
            return null;
        }
        // extend session
        s.expiry = Instant.now().plusSeconds(timeoutSeconds);
        return s;
    }

    public static void invalidate(String token) {
        sessions.remove(token);
    }
}