package com.delivery.dao;

import com.delivery.database.DatabaseConnection;
import com.delivery.util.Result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TransferDAO - Data Access Object for package transfer operations
 * Handles all database queries related to facility-to-facility transfers
 */
public class TransferDAO {

    /**
     * Initiate a transfer for a package from one facility to another
     * Creates a record in package_transfers table with 'pending' status
     *
     * @param packageId Package to transfer
     * @param fromFacilityId Source facility
     * @param toFacilityId Destination facility
     * @param initiatedBy User ID initiating the transfer
     * @return Result containing transfer ID or error message
     */
    public static Result<Long, String> initiateTransfer(long packageId, long fromFacilityId,
                                                        long toFacilityId, long initiatedBy) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();

        // First verify package is at the source facility
        String verifySQL = "SELECT current_facility_id FROM packages WHERE package_id = ?";
        try (PreparedStatement verifyStmt = conn.prepareStatement(verifySQL)) {
            verifyStmt.setLong(1, packageId);
            try (ResultSet rs = verifyStmt.executeQuery()) {
                if (!rs.next()) {
                    return Result.err("Package not found");
                }
                Long currentFacility = rs.getLong("current_facility_id");
                if (rs.wasNull()) {
                    return Result.err("Package has no current facility");
                }
                if (currentFacility != fromFacilityId) {
                    return Result.err("Package is not at the source facility");
                }
            }
        } catch (SQLException e) {
            return Result.err("Error verifying package location: " + e.getMessage());
        }

        // Create transfer record
        String sql = "INSERT INTO package_transfers " +
                    "(package_id, from_facility_id, to_facility_id, transfer_status, initiated_by) " +
                    "VALUES (?, ?, ?, 'pending', ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql,
                                            PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, packageId);
            stmt.setLong(2, fromFacilityId);
            stmt.setLong(3, toFacilityId);
            stmt.setLong(4, initiatedBy);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                return Result.err("Failed to create transfer record");
            }

            // Get generated transfer ID
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long transferId = generatedKeys.getLong(1);
                    return Result.ok(transferId);
                } else {
                    return Result.err("Failed to retrieve transfer ID");
                }
            }
        } catch (SQLException e) {
            return Result.err("Database error creating transfer: " + e.getMessage());
        }
    }

    /**
     * Complete a transfer - updates package location and inventory
     * This is a transaction that:
     * 1. Updates transfer status to 'completed'
     * 2. Updates package current_facility_id
     * 3. Updates old inventory record (departure_time, status='transferred')
     * 4. Creates new inventory record at destination
     *
     * @param transferId The transfer to complete
     * @return Result with success message or error
     */
    public static Result<String, String> completeTransfer(long transferId) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();

        try {
            // Start transaction
            conn.setAutoCommit(false);

            // Get transfer details
            String getTransferSQL = "SELECT package_id, from_facility_id, to_facility_id, transfer_status " +
                                   "FROM package_transfers WHERE transfer_id = ?";
            long packageId;
            long fromFacilityId;
            long toFacilityId;
            String status;

            try (PreparedStatement stmt = conn.prepareStatement(getTransferSQL)) {
                stmt.setLong(1, transferId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return Result.err("Transfer not found");
                    }
                    packageId = rs.getLong("package_id");
                    fromFacilityId = rs.getLong("from_facility_id");
                    toFacilityId = rs.getLong("to_facility_id");
                    status = rs.getString("transfer_status");

                    if ("completed".equals(status)) {
                        conn.rollback();
                        return Result.err("Transfer already completed");
                    }
                    if ("cancelled".equals(status)) {
                        conn.rollback();
                        return Result.err("Transfer was cancelled");
                    }
                }
            }

            // 1. Update transfer status to completed
            String updateTransferSQL = "UPDATE package_transfers " +
                                      "SET transfer_status = 'completed', completed_at = NOW() " +
                                      "WHERE transfer_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateTransferSQL)) {
                stmt.setLong(1, transferId);
                stmt.executeUpdate();
            }

            // 2. Update package current facility
            String updatePackageSQL = "UPDATE packages SET current_facility_id = ? WHERE package_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updatePackageSQL)) {
                stmt.setLong(1, toFacilityId);
                stmt.setLong(2, packageId);
                stmt.executeUpdate();
            }

            // 3. Update old inventory record (mark as transferred)
            String updateInventorySQL = "UPDATE inventory " +
                                       "SET departure_time = NOW(), inventory_status = 'transferred' " +
                                       "WHERE package_id = ? AND facility_id = ? AND inventory_status = 'in_stock'";
            try (PreparedStatement stmt = conn.prepareStatement(updateInventorySQL)) {
                stmt.setLong(1, packageId);
                stmt.setLong(2, fromFacilityId);
                stmt.executeUpdate();
            }

            // 4. Create new inventory record at destination
            String insertInventorySQL = "INSERT INTO inventory " +
                                       "(facility_id, package_id, inventory_status) " +
                                       "VALUES (?, ?, 'in_stock')";
            try (PreparedStatement stmt = conn.prepareStatement(insertInventorySQL)) {
                stmt.setLong(1, toFacilityId);
                stmt.setLong(2, packageId);
                stmt.executeUpdate();
            }

            // Commit transaction
            conn.commit();
            conn.setAutoCommit(true);

            return Result.ok("Transfer completed successfully");

        } catch (SQLException e) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (SQLException rollbackEx) {
                return Result.err("Rollback failed: " + rollbackEx.getMessage());
            }
            return Result.err("Database error completing transfer: " + e.getMessage());
        }
    }

    /**
     * Get all pending transfers (for display in UI)
     *
     * @return Result containing list of transfer details or error message
     */
    public static Result<List<Map<String, Object>>, String> getPendingTransfers() {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();
        List<Map<String, Object>> transfers = new ArrayList<>();

        String sql = "SELECT t.transfer_id, t.package_id, t.transfer_status, t.initiated_at, " +
                    "p.tracking_number, " +
                    "f1.facility_name AS from_facility, " +
                    "f2.facility_name AS to_facility, " +
                    "u.full_name AS initiated_by_name " +
                    "FROM package_transfers t " +
                    "INNER JOIN packages p ON t.package_id = p.package_id " +
                    "INNER JOIN facilities f1 ON t.from_facility_id = f1.facility_id " +
                    "INNER JOIN facilities f2 ON t.to_facility_id = f2.facility_id " +
                    "INNER JOIN users u ON t.initiated_by = u.user_id " +
                    "WHERE t.transfer_status IN ('pending', 'in_transit') " +
                    "ORDER BY t.initiated_at DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> transfer = new HashMap<>();
                transfer.put("transferId", rs.getLong("transfer_id"));
                transfer.put("packageId", rs.getLong("package_id"));
                transfer.put("trackingNumber", rs.getString("tracking_number"));
                transfer.put("fromFacility", rs.getString("from_facility"));
                transfer.put("toFacility", rs.getString("to_facility"));
                transfer.put("status", rs.getString("transfer_status"));
                transfer.put("initiatedBy", rs.getString("initiated_by_name"));
                transfer.put("initiatedAt", rs.getTimestamp("initiated_at").toString());
                transfers.add(transfer);
            }

            return Result.ok(transfers);

        } catch (SQLException e) {
            return Result.err("Database error fetching transfers: " + e.getMessage());
        }
    }

    /**
     * Get transfer by tracking number
     *
     * @param trackingNumber Package tracking number
     * @return Result containing transfer details or error message
     */
    public static Result<Map<String, Object>, String> getTransferByTracking(String trackingNumber) {
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            return Result.err("Database connection failed: " + connResult.unwrapErr());
        }

        Connection conn = connResult.unwrap();

        String sql = "SELECT t.transfer_id, t.package_id, t.transfer_status, t.initiated_at, t.completed_at, " +
                    "p.tracking_number, p.current_facility_id, " +
                    "f1.facility_id AS from_facility_id, f1.facility_name AS from_facility, " +
                    "f2.facility_id AS to_facility_id, f2.facility_name AS to_facility " +
                    "FROM package_transfers t " +
                    "INNER JOIN packages p ON t.package_id = p.package_id " +
                    "INNER JOIN facilities f1 ON t.from_facility_id = f1.facility_id " +
                    "INNER JOIN facilities f2 ON t.to_facility_id = f2.facility_id " +
                    "WHERE p.tracking_number = ? " +
                    "ORDER BY t.initiated_at DESC LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackingNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> transfer = new HashMap<>();
                    transfer.put("transferId", rs.getLong("transfer_id"));
                    transfer.put("packageId", rs.getLong("package_id"));
                    transfer.put("trackingNumber", rs.getString("tracking_number"));
                    transfer.put("fromFacilityId", rs.getLong("from_facility_id"));
                    transfer.put("fromFacility", rs.getString("from_facility"));
                    transfer.put("toFacilityId", rs.getLong("to_facility_id"));
                    transfer.put("toFacility", rs.getString("to_facility"));
                    transfer.put("status", rs.getString("transfer_status"));
                    transfer.put("currentFacilityId", rs.getLong("current_facility_id"));
                    transfer.put("initiatedAt", rs.getTimestamp("initiated_at").toString());

                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    if (completedAt != null) {
                        transfer.put("completedAt", completedAt.toString());
                    }

                    return Result.ok(transfer);
                } else {
                    return Result.err("No transfer found for tracking number: " + trackingNumber);
                }
            }
        } catch (SQLException e) {
            return Result.err("Database error: " + e.getMessage());
        }
    }
}
