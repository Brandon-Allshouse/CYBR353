package com.delivery.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader {
    private static final Map<String, String> env = new HashMap<>();
    private static String loadError = null;

    static {
        loadDotEnv();
    }

    private static void loadDotEnv() {
        File f = new File(".env");
        if (!f.exists()) {
            loadError = ".env file not found";
            return;
        }

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
            loadError = "Failed to load .env: " + e.getMessage();
        }
    }

    // Returns environment variable from .env or system env, or error if not found
    public static Result<String, String> get(String key) {
        String v = env.get(key);
        if (v != null) return Result.ok(v);

        v = System.getenv(key);
        if (v != null) return Result.ok(v);

        if (loadError != null) {
            return Result.err("Environment variable '" + key + "' not found (" + loadError + ")");
        }
        return Result.err("Environment variable '" + key + "' not found");
    }
}
