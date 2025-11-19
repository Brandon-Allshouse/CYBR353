package com.delivery.dao;

import com.delivery.database.DatabaseConnection;
import com.delivery.models.Facility;
import com.delivery.models.InventoryItem;
import com.delivery.util.Result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * InventoryDAO - Data Access Object for inventory operations
 * Handles all database queries related to facility inventory
 */
public class InventoryDAO {

    /**
     * Get all inventory items for a specific facility
     * Joins inventory, packages, orders, facilities, addresses, and users tables
     *
     * @param facilityId The facility ID to query
     * @return Result containing list of InventoryItems or error message
     */
    public static Result<List<InventoryItem>, String> getInventoryByFacility(long facilityId) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();
        List<InventoryItem> items = new ArrayList<>();

        // Complex join query to get all related data
        String sql = "SELECT " +
                    "i.inventory_id, i.facility_id, i.arrival_time, i.departure_time, i.inventory_status, " +
                    "p.package_id, p.tracking_number, p.package_status, p.weight_kg, p.length_cm, " +
                    "p.width_cm, p.height_cm, p.fragile, p.signature_required, " +
                    "o.order_id, o.customer_id, o.order_status, " +
                    "f.facility_name, " +
                    "u.full_name AS customer_name, " +
                    "CONCAT(pickup.street_address, ', ', pickup.city, ', ', pickup.state, ' ', pickup.zip_code) AS pickup_address, " +
                    "CONCAT(delivery.street_address, ', ', delivery.city, ', ', delivery.state, ' ', delivery.zip_code) AS delivery_address, " +
                    "delivery.delivery_instructions " +
                    "FROM inventory i " +
                    "INNER JOIN packages p ON i.package_id = p.package_id " +
                    "INNER JOIN orders o ON p.order_id = o.order_id " +
                    "INNER JOIN facilities f ON i.facility_id = f.facility_id " +
                    "INNER JOIN users u ON o.customer_id = u.user_id " +
                    "INNER JOIN addresses pickup ON o.pickup_address_id = pickup.address_id " +
                    "INNER JOIN addresses delivery ON o.delivery_address_id = delivery.address_id " +
                    "WHERE i.facility_id = ? " +
                    "ORDER BY i.arrival_time DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, facilityId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    InventoryItem item = new InventoryItem();

                    // Inventory fields
                    item.setInventoryId(rs.getLong("inventory_id"));
                    item.setFacilityId(rs.getLong("facility_id"));
                    item.setFacilityName(rs.getString("facility_name"));
                    item.setArrivalTime(rs.getTimestamp("arrival_time"));
                    item.setDepartureTime(rs.getTimestamp("departure_time"));
                    item.setInventoryStatus(rs.getString("inventory_status"));

                    // Package fields
                    item.setPackageId(rs.getLong("package_id"));
                    item.setTrackingNumber(rs.getString("tracking_number"));
                    item.setPackageStatus(rs.getString("package_status"));
                    item.setWeightKg(rs.getDouble("weight_kg"));
                    item.setLengthCm(rs.getDouble("length_cm"));
                    item.setWidthCm(rs.getDouble("width_cm"));
                    item.setHeightCm(rs.getDouble("height_cm"));
                    item.setFragile(rs.getBoolean("fragile"));
                    item.setSignatureRequired(rs.getBoolean("signature_required"));

                    // Order fields
                    item.setOrderId(rs.getLong("order_id"));
                    item.setCustomerId(rs.getLong("customer_id"));
                    item.setCustomerName(rs.getString("customer_name"));
                    item.setOrderStatus(rs.getString("order_status"));

                    // Address fields
                    item.setPickupAddress(rs.getString("pickup_address"));
                    item.setDeliveryAddress(rs.getString("delivery_address"));
                    item.setDeliveryInstructions(rs.getString("delivery_instructions"));

                    items.add(item);
                }
            }

            return Result.ok(items);

        } catch (SQLException e) {
            return Result.err("SQL error: " + e.getMessage());
        }
    }

    /**
     * Get all inventory items across all facilities (for admin/manager overview)
     *
     * @return Result containing list of all InventoryItems or error message
     */
    public static Result<List<InventoryItem>, String> getAllInventory() {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();
        List<InventoryItem> items = new ArrayList<>();

        String sql = "SELECT " +
                    "i.inventory_id, i.facility_id, i.arrival_time, i.departure_time, i.inventory_status, " +
                    "p.package_id, p.tracking_number, p.package_status, p.weight_kg, p.length_cm, " +
                    "p.width_cm, p.height_cm, p.fragile, p.signature_required, " +
                    "o.order_id, o.customer_id, o.order_status, " +
                    "f.facility_name, " +
                    "u.full_name AS customer_name, " +
                    "CONCAT(pickup.street_address, ', ', pickup.city, ', ', pickup.state, ' ', pickup.zip_code) AS pickup_address, " +
                    "CONCAT(delivery.street_address, ', ', delivery.city, ', ', delivery.state, ' ', delivery.zip_code) AS delivery_address, " +
                    "delivery.delivery_instructions " +
                    "FROM inventory i " +
                    "INNER JOIN packages p ON i.package_id = p.package_id " +
                    "INNER JOIN orders o ON p.order_id = o.order_id " +
                    "INNER JOIN facilities f ON i.facility_id = f.facility_id " +
                    "INNER JOIN users u ON o.customer_id = u.user_id " +
                    "INNER JOIN addresses pickup ON o.pickup_address_id = pickup.address_id " +
                    "INNER JOIN addresses delivery ON o.delivery_address_id = delivery.address_id " +
                    "WHERE i.inventory_status = 'in_stock' " +
                    "ORDER BY i.facility_id, i.arrival_time DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                InventoryItem item = new InventoryItem();

                // Inventory fields
                item.setInventoryId(rs.getLong("inventory_id"));
                item.setFacilityId(rs.getLong("facility_id"));
                item.setFacilityName(rs.getString("facility_name"));
                item.setArrivalTime(rs.getTimestamp("arrival_time"));
                item.setDepartureTime(rs.getTimestamp("departure_time"));
                item.setInventoryStatus(rs.getString("inventory_status"));

                // Package fields
                item.setPackageId(rs.getLong("package_id"));
                item.setTrackingNumber(rs.getString("tracking_number"));
                item.setPackageStatus(rs.getString("package_status"));
                item.setWeightKg(rs.getDouble("weight_kg"));
                item.setLengthCm(rs.getDouble("length_cm"));
                item.setWidthCm(rs.getDouble("width_cm"));
                item.setHeightCm(rs.getDouble("height_cm"));
                item.setFragile(rs.getBoolean("fragile"));
                item.setSignatureRequired(rs.getBoolean("signature_required"));

                // Order fields
                item.setOrderId(rs.getLong("order_id"));
                item.setCustomerId(rs.getLong("customer_id"));
                item.setCustomerName(rs.getString("customer_name"));
                item.setOrderStatus(rs.getString("order_status"));

                // Address fields
                item.setPickupAddress(rs.getString("pickup_address"));
                item.setDeliveryAddress(rs.getString("delivery_address"));
                item.setDeliveryInstructions(rs.getString("delivery_instructions"));

                items.add(item);
            }

            return Result.ok(items);

        } catch (SQLException e) {
            return Result.err("SQL error: " + e.getMessage());
        }
    }

    /**
     * Get all facilities
     *
     * @return Result containing list of facilities or error message
     */
    public static Result<List<Facility>, String> getAllFacilities() {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();
        List<Facility> facilities = new ArrayList<>();

        String sql = "SELECT facility_id, facility_name, address, city, state, zip_code, phone, capacity " +
                    "FROM facilities ORDER BY facility_name";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Facility facility = new Facility();
                facility.facilityId = rs.getLong("facility_id");
                facility.facilityName = rs.getString("facility_name");
                facility.address = rs.getString("address") + ", " +
                                 rs.getString("city") + ", " +
                                 rs.getString("state") + " " +
                                 rs.getString("zip_code");

                facilities.add(facility);
            }

            return Result.ok(facilities);

        } catch (SQLException e) {
            return Result.err("SQL error: " + e.getMessage());
        }
    }

    /**
     * Get inventory item by tracking number
     *
     * @param trackingNumber The tracking number to search for
     * @return Result containing InventoryItem or error message
     */
    public static Result<InventoryItem, String> getInventoryByTrackingNumber(String trackingNumber) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();

        String sql = "SELECT " +
                    "i.inventory_id, i.facility_id, i.arrival_time, i.departure_time, i.inventory_status, " +
                    "p.package_id, p.tracking_number, p.package_status, p.weight_kg, p.length_cm, " +
                    "p.width_cm, p.height_cm, p.fragile, p.signature_required, " +
                    "o.order_id, o.customer_id, o.order_status, " +
                    "f.facility_name, " +
                    "u.full_name AS customer_name, " +
                    "CONCAT(pickup.street_address, ', ', pickup.city, ', ', pickup.state, ' ', pickup.zip_code) AS pickup_address, " +
                    "CONCAT(delivery.street_address, ', ', delivery.city, ', ', delivery.state, ' ', delivery.zip_code) AS delivery_address, " +
                    "delivery.delivery_instructions " +
                    "FROM inventory i " +
                    "INNER JOIN packages p ON i.package_id = p.package_id " +
                    "INNER JOIN orders o ON p.order_id = o.order_id " +
                    "INNER JOIN facilities f ON i.facility_id = f.facility_id " +
                    "INNER JOIN users u ON o.customer_id = u.user_id " +
                    "INNER JOIN addresses pickup ON o.pickup_address_id = pickup.address_id " +
                    "INNER JOIN addresses delivery ON o.delivery_address_id = delivery.address_id " +
                    "WHERE p.tracking_number = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackingNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    InventoryItem item = new InventoryItem();

                    // Inventory fields
                    item.setInventoryId(rs.getLong("inventory_id"));
                    item.setFacilityId(rs.getLong("facility_id"));
                    item.setFacilityName(rs.getString("facility_name"));
                    item.setArrivalTime(rs.getTimestamp("arrival_time"));
                    item.setDepartureTime(rs.getTimestamp("departure_time"));
                    item.setInventoryStatus(rs.getString("inventory_status"));

                    // Package fields
                    item.setPackageId(rs.getLong("package_id"));
                    item.setTrackingNumber(rs.getString("tracking_number"));
                    item.setPackageStatus(rs.getString("package_status"));
                    item.setWeightKg(rs.getDouble("weight_kg"));
                    item.setLengthCm(rs.getDouble("length_cm"));
                    item.setWidthCm(rs.getDouble("width_cm"));
                    item.setHeightCm(rs.getDouble("height_cm"));
                    item.setFragile(rs.getBoolean("fragile"));
                    item.setSignatureRequired(rs.getBoolean("signature_required"));

                    // Order fields
                    item.setOrderId(rs.getLong("order_id"));
                    item.setCustomerId(rs.getLong("customer_id"));
                    item.setCustomerName(rs.getString("customer_name"));
                    item.setOrderStatus(rs.getString("order_status"));

                    // Address fields
                    item.setPickupAddress(rs.getString("pickup_address"));
                    item.setDeliveryAddress(rs.getString("delivery_address"));
                    item.setDeliveryInstructions(rs.getString("delivery_instructions"));

                    return Result.ok(item);
                } else {
                    return Result.err("Package not found in inventory");
                }
            }

        } catch (SQLException e) {
            return Result.err("SQL error: " + e.getMessage());
        }
    }

    /**
     * Get inventory count statistics for a facility
     *
     * @param facilityId The facility ID
     * @return Result containing count or error message
     */
    public static Result<Integer, String> getInventoryCount(long facilityId) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();

        String sql = "SELECT COUNT(*) AS count FROM inventory WHERE facility_id = ? AND inventory_status = 'in_stock'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, facilityId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Result.ok(rs.getInt("count"));
                }
                return Result.ok(0);
            }

        } catch (SQLException e) {
            return Result.err("SQL error: " + e.getMessage());
        }
    }
}
