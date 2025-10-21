package com.delivery.security;

import com.delivery.util.EnvLoader;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AuditLogger {
    private static final String path = EnvLoader.get("AUDIT_LOG_PATH") != null ? EnvLoader.get("AUDIT_LOG_PATH") : "./backend/audit.log";
    private static final DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    public static synchronized void log(String user, String action, String resource, String result) {
        String ts = fmt.format(Instant.now());
        String entry = String.format("%s | user=%s | action=%s | resource=%s | result=%s", ts, user, action, resource, result);
        try (PrintWriter out = new PrintWriter(new FileWriter(path, true))) {
            out.println(entry);
        } catch (IOException e) {
            System.err.println("Audit log failed: " + e.getMessage());
        }
    }
}
