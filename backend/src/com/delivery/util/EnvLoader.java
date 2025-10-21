package com.delivery.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader {
    private static final Map<String,String> env = new HashMap<>();

    static {
        loadDotEnv();
    }

    private static void loadDotEnv() {
        File f = new File(".env");
        if (!f.exists()) return; // silently continue; fallback to system env
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx <= 0) continue;
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                env.put(key, val);
            }
        } catch (IOException e) {
            System.err.println("Failed to load .env: " + e.getMessage());
        }
    }

    public static String get(String key) {
        String v = env.get(key);
        if (v != null) return v;
        return System.getenv(key);
    }
}
