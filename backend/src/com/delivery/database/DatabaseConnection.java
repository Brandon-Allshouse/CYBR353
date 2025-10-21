package com.delivery.database;

import com.delivery.util.EnvLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static Connection conn = null;

    public static Connection getConnection() throws SQLException {
        if (conn != null && !conn.isClosed()) return conn;
        String host = EnvLoader.get("DB_HOST");
        String port = EnvLoader.get("DB_PORT");
        String name = EnvLoader.get("DB_NAME");
        String user = EnvLoader.get("DB_USER");
        String password = EnvLoader.get("DB_PASSWORD");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + name + "?serverTimezone=UTC&useSSL=false";
        try {
            conn = DriverManager.getConnection(url, user, password);
            return conn;
        } catch (SQLException e) {
            throw e;
        }
    }
}
