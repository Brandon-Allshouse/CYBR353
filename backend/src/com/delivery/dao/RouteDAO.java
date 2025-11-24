package com.delivery.dao;

import com.delivery.util.Result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RouteDAO - Database access object for route generation and management
 * Supports automatic route generation based on package delivery locations
 */
public class RouteDAO {

    /**
     * Package info needed for route generation
     */
    public static class DeliveryPackage {
        public long packageId;
        public String trackingNumber;
        public String deliveryZipCode;
        public String deliveryCity;
        public String deliveryState;
        public String deliveryAddress;
        public double weightKg;
        public boolean fragile;
        public boolean signatureRequired;

        public DeliveryPackage(long packageId, String trackingNumber, String deliveryZipCode,
                             String deliveryCity, String deliveryState, String deliveryAddress,
                             double weightKg, boolean fragile, boolean signatureRequired) {
            this.packageId = packageId;
            this.trackingNumber = trackingNumber;
            this.deliveryZipCode = deliveryZipCode;
            this.deliveryCity = deliveryCity;
            this.deliveryState = deliveryState;
            this.deliveryAddress = deliveryAddress;
            this.weightKg = weightKg;
            this.fragile = fragile;
            this.signatureRequired = signatureRequired;
        }
    }

    /**
     * Driver info for assignment
     */
    public static class AvailableDriver {
        public long driverId;
        public String username;
        public String fullName;

        public AvailableDriver(long driverId, String username, String fullName) {
            this.driverId = driverId;
            this.username = username;
            this.fullName = fullName;
        }
    }

    /**
     * Get all packages at a facility ready for delivery
     * Status must be 'at_facility' and not already on a route
     */
    public static Result<List<DeliveryPackage>, String> getPackagesReadyForDelivery(
            Connection conn, long facilityId) {

        String query =
            "SELECT p.package_id, p.tracking_number, p.weight_kg, p.fragile, p.signature_required, " +
            "       a.zip_code, a.city, a.state, a.street_address " +
            "FROM packages p " +
            "JOIN orders o ON p.order_id = o.order_id " +
            "JOIN addresses a ON o.delivery_address_id = a.address_id " +
            "WHERE p.current_facility_id = ? " +
            "  AND p.package_status = 'at_facility' " +
            "  AND p.package_id NOT IN (SELECT package_id FROM route_packages) " +
            "ORDER BY a.zip_code, a.city";

        List<DeliveryPackage> packages = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, facilityId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    packages.add(new DeliveryPackage(
                        rs.getLong("package_id"),
                        rs.getString("tracking_number"),
                        rs.getString("zip_code"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("street_address"),
                        rs.getDouble("weight_kg"),
                        rs.getBoolean("fragile"),
                        rs.getBoolean("signature_required")
                    ));
                }
            }

            return Result.ok(packages);

        } catch (SQLException e) {
            return Result.err("Database error while fetching packages: " + e.getMessage());
        }
    }

    /**
     * Get available drivers at a facility (drivers not already assigned to a route on the given date)
     */
    public static Result<List<AvailableDriver>, String> getAvailableDrivers(
            Connection conn, long facilityId, String routeDate) {

        String query =
            "SELECT u.user_id, u.username, u.full_name " +
            "FROM users u " +
            "WHERE u.role = 'driver' " +
            "  AND u.account_status = 'active' " +
            "  AND u.user_id NOT IN ( " +
            "    SELECT ra.driver_id " +
            "    FROM route_assignments ra " +
            "    JOIN routes r ON ra.route_id = r.route_id " +
            "    WHERE r.facility_id = ? AND r.route_date = ? " +
            "  ) " +
            "ORDER BY u.username";

        List<AvailableDriver> drivers = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, facilityId);
            stmt.setString(2, routeDate);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    drivers.add(new AvailableDriver(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name")
                    ));
                }
            }

            return Result.ok(drivers);

        } catch (SQLException e) {
            return Result.err("Database error while fetching drivers: " + e.getMessage());
        }
    }

    /**
     * Create a new route
     * Returns the generated route_id
     */
    public static Result<Long, String> createRoute(Connection conn, String routeName,
                                                   long facilityId, String routeDate,
                                                   int estimatedDurationMinutes, int totalStops) {

        String query =
            "INSERT INTO routes (route_name, facility_id, route_date, estimated_duration_minutes, " +
            "                    total_stops, route_status) " +
            "VALUES (?, ?, ?, ?, ?, 'planned')";

        try (PreparedStatement stmt = conn.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, routeName);
            stmt.setLong(2, facilityId);
            stmt.setString(3, routeDate);
            stmt.setInt(4, estimatedDurationMinutes);
            stmt.setInt(5, totalStops);

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return Result.ok(keys.getLong(1));
                } else {
                    return Result.err("Failed to retrieve generated route ID");
                }
            }

        } catch (SQLException e) {
            return Result.err("Database error while creating route: " + e.getMessage());
        }
    }

    /**
     * Assign packages to a route
     */
    public static Result<Void, String> assignPackagesToRoute(Connection conn, long routeId,
                                                             List<Long> packageIds) {

        String query = "INSERT INTO route_packages (route_id, package_id) VALUES (?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            for (Long packageId : packageIds) {
                stmt.setLong(1, routeId);
                stmt.setLong(2, packageId);
                stmt.addBatch();
            }

            stmt.executeBatch();
            return Result.ok(null);

        } catch (SQLException e) {
            return Result.err("Database error while assigning packages to route: " + e.getMessage());
        }
    }

    /**
     * Update package status to 'out_for_delivery'
     */
    public static Result<Void, String> updatePackageStatus(Connection conn, List<Long> packageIds,
                                                           String newStatus) {

        String query = "UPDATE packages SET package_status = ? WHERE package_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            for (Long packageId : packageIds) {
                stmt.setString(1, newStatus);
                stmt.setLong(2, packageId);
                stmt.addBatch();
            }

            stmt.executeBatch();
            return Result.ok(null);

        } catch (SQLException e) {
            return Result.err("Database error while updating package status: " + e.getMessage());
        }
    }

    /**
     * Assign a driver to a route
     */
    public static Result<Void, String> assignDriverToRoute(Connection conn, long routeId,
                                                           long driverId, String vehicleId) {

        String query =
            "INSERT INTO route_assignments (route_id, driver_id, vehicle_id) " +
            "VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, routeId);
            stmt.setLong(2, driverId);
            stmt.setString(3, vehicleId);

            stmt.executeUpdate();
            return Result.ok(null);

        } catch (SQLException e) {
            return Result.err("Database error while assigning driver: " + e.getMessage());
        }
    }

    /**
     * Get facility name by ID
     */
    public static Result<String, String> getFacilityName(Connection conn, long facilityId) {
        String query = "SELECT facility_name FROM facilities WHERE facility_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, facilityId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Result.ok(rs.getString("facility_name"));
                } else {
                    return Result.err("Facility not found");
                }
            }

        } catch (SQLException e) {
            return Result.err("Database error while fetching facility name: " + e.getMessage());
        }
    }

    /**
     * Verify facility exists
     */
    public static boolean facilityExists(Connection conn, long facilityId) {
        String query = "SELECT 1 FROM facilities WHERE facility_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, facilityId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            return false;
        }
    }
}
