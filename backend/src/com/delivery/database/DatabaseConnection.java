package com.delivery.database;

import com.delivery.util.EnvLoader;
import com.delivery.util.Result;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static Connection conn = null;

    public static Result<Connection, String> getConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                return Result.ok(conn);
            }
        } catch (SQLException e) {
            // Fall through to create new connection
        }

        Result<String, String> hostResult = EnvLoader.get("DB_HOST");
        Result<String, String> portResult = EnvLoader.get("DB_PORT");
        Result<String, String> nameResult = EnvLoader.get("DB_NAME");
        Result<String, String> userResult = EnvLoader.get("DB_USER");
        Result<String, String> passwordResult = EnvLoader.get("DB_PASSWORD");

        if (hostResult.isErr()) return Result.err("DB_HOST not configured");
        if (portResult.isErr()) return Result.err("DB_PORT not configured");
        if (nameResult.isErr()) return Result.err("DB_NAME not configured");
        if (userResult.isErr()) return Result.err("DB_USER not configured");
        if (passwordResult.isErr()) return Result.err("DB_PASSWORD not configured");

        String url = "jdbc:mysql://" + hostResult.unwrap() + ":" + portResult.unwrap() + "/" +
                     nameResult.unwrap() + "?serverTimezone=UTC&useSSL=false";

        try {
            conn = DriverManager.getConnection(url, userResult.unwrap(), passwordResult.unwrap());
            return Result.ok(conn);
        } catch (SQLException e) {
            return Result.err("Database connection failed: " + e.getMessage());
        }
    }
}
