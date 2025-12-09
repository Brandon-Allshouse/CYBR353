package com.delivery.services;

import com.delivery.dao.InventoryDAO;
import com.delivery.models.Facility;
import com.delivery.models.InventoryItem;
import com.delivery.security.SecurityManager.BLPAccessControl;
import com.delivery.security.SecurityManager.SecurityLevel;
import com.delivery.util.Result;

import java.util.List;

/**
 * InventoryService - Business logic for facility inventory operations
 * Enforces BLP access control for inventory data (SECRET level)
 */
public class InventoryService {

    /**
     * Get inventory for a specific facility
     * Requires SECRET clearance (managers) as inventory contains sensitive operational data
     *
     * @param facilityId The facility ID
     * @param userClearance The requesting user's clearance level
     * @param username The requesting username for audit logging
     * @return Result containing list of InventoryItems or error message
     */
    public static Result<List<InventoryItem>, String> getInventoryByFacility(long facilityId,
                                                                             SecurityLevel userClearance,
                                                                             String username) {
        // BLP Access Control: Inventory data is SECRET level (managers and above)
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.SECRET)) {
            return Result.err("Access denied: Insufficient clearance to view inventory data");
        }

        // Delegate to DAO
        return InventoryDAO.getInventoryByFacility(facilityId);
    }

    /**
     * Get all inventory across all facilities
     * Requires SECRET clearance (managers) for system-wide inventory view
     *
     * @param userClearance The requesting user's clearance level
     * @param username The requesting username for audit logging
     * @return Result containing list of all InventoryItems or error message
     */
    public static Result<List<InventoryItem>, String> getAllInventory(SecurityLevel userClearance,
                                                                       String username) {
        // BLP Access Control: Inventory data is SECRET level
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.SECRET)) {
            return Result.err("Access denied: Insufficient clearance to view inventory data");
        }

        // Delegate to DAO
        return InventoryDAO.getAllInventory();
    }

    /**
     * Get all facilities (public facility information)
     * Requires at least CONFIDENTIAL clearance (drivers can see facility locations)
     *
     * @param userClearance The requesting user's clearance level
     * @return Result containing list of facilities or error message
     */
    public static Result<List<Facility>, String> getAllFacilities(SecurityLevel userClearance) {
        // BLP Access Control: Public facility info is CONFIDENTIAL level
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.CONFIDENTIAL)) {
            return Result.err("Access denied: Insufficient clearance to view facility data");
        }

        // Delegate to DAO
        return InventoryDAO.getAllFacilities();
    }

    /**
     * Search inventory by tracking number
     * Requires SECRET clearance (managers)
     *
     * @param trackingNumber The tracking number to search for
     * @param userClearance The requesting user's clearance level
     * @param username The requesting username for audit logging
     * @return Result containing InventoryItem or error message
     */
    public static Result<InventoryItem, String> searchByTrackingNumber(String trackingNumber,
                                                                        SecurityLevel userClearance,
                                                                        String username) {
        // BLP Access Control: Inventory lookup is SECRET level
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.SECRET)) {
            return Result.err("Access denied: Insufficient clearance to search inventory");
        }

        // Input validation
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            return Result.err("Tracking number is required");
        }

        // Sanitize input (prevent SQL injection, though prepared statements already do this)
        String sanitized = trackingNumber.trim().replaceAll("[^A-Za-z0-9]", "");
        if (sanitized.isEmpty()) {
            return Result.err("Invalid tracking number format");
        }

        // Delegate to DAO
        return InventoryDAO.getInventoryByTrackingNumber(sanitized);
    }

    /**
     * Get inventory count statistics for a facility
     * Requires SECRET clearance (managers)
     *
     * @param facilityId The facility ID
     * @param userClearance The requesting user's clearance level
     * @return Result containing count or error message
     */
    public static Result<Integer, String> getInventoryCount(long facilityId,
                                                            SecurityLevel userClearance) {
        // BLP Access Control: Inventory statistics are SECRET level
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.SECRET)) {
            return Result.err("Access denied: Insufficient clearance to view inventory statistics");
        }

        // Validate facility ID
        if (facilityId <= 0) {
            return Result.err("Invalid facility ID");
        }

        // Delegate to DAO
        return InventoryDAO.getInventoryCount(facilityId);
    }
}
