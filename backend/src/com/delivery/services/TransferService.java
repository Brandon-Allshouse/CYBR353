package com.delivery.services;

import com.delivery.dao.TransferDAO;
import com.delivery.security.SecurityManager.BLPAccessControl;
import com.delivery.security.SecurityManager.SecurityLevel;
import com.delivery.security.SecurityManager.AuditLogger;
import com.delivery.util.Result;

import java.util.List;
import java.util.Map;

/**
 * TransferService - Business logic for package transfers between facilities
 * Enforces BLP access control (SECRET clearance required for managers)
 */
public class TransferService {

    /**
     * Initiate a package transfer from one facility to another
     * Requires SECRET clearance (manager level)
     *
     * @param packageId Package to transfer
     * @param fromFacilityId Source facility
     * @param toFacilityId Destination facility
     * @param userId User initiating the transfer
     * @param username Username for audit logging
     * @param userClearance User's clearance level
     * @param clientIp Client IP for audit logging
     * @return Result containing transfer ID or error message
     */
    public static Result<Long, String> initiateTransfer(long packageId, long fromFacilityId,
                                                        long toFacilityId, long userId,
                                                        String username, SecurityLevel userClearance,
                                                        String clientIp) {
        // BLP Access Control: Transfers require SECRET clearance (managers)
        if (!BLPAccessControl.checkWriteAccess(userClearance, SecurityLevel.SECRET)) {
            AuditLogger.log(userId, username, "TRANSFER_INITIATE_DENIED",
                          "failure", clientIp,
                          "Insufficient clearance for transfer (requires SECRET)");
            return Result.err("Access denied: Insufficient clearance for transfers");
        }

        // Validate input
        if (fromFacilityId == toFacilityId) {
            AuditLogger.log(userId, username, "TRANSFER_INITIATE_FAILED",
                          "failure", clientIp,
                          "Cannot transfer to same facility");
            return Result.err("Source and destination facilities cannot be the same");
        }

        if (fromFacilityId <= 0 || toFacilityId <= 0 || packageId <= 0) {
            return Result.err("Invalid facility or package ID");
        }

        // Call DAO to create transfer
        Result<Long, String> result = TransferDAO.initiateTransfer(packageId, fromFacilityId,
                                                                   toFacilityId, userId);

        if (result.isOk()) {
            AuditLogger.log(userId, username, "TRANSFER_INITIATED",
                          "success", clientIp,
                          String.format("Transfer %d created for package %d: facility %d -> %d",
                                      result.unwrap(), packageId, fromFacilityId, toFacilityId));
        } else {
            AuditLogger.log(userId, username, "TRANSFER_INITIATE_FAILED",
                          "failure", clientIp,
                          "Error: " + result.unwrapErr());
        }

        return result;
    }

    /**
     * Complete a pending transfer
     * Requires SECRET clearance (manager level)
     *
     * @param transferId Transfer to complete
     * @param userId User completing the transfer
     * @param username Username for audit logging
     * @param userClearance User's clearance level
     * @param clientIp Client IP for audit logging
     * @return Result with success message or error
     */
    public static Result<String, String> completeTransfer(long transferId, long userId,
                                                          String username, SecurityLevel userClearance,
                                                          String clientIp) {
        // BLP Access Control: Transfers require SECRET clearance (managers)
        if (!BLPAccessControl.checkWriteAccess(userClearance, SecurityLevel.SECRET)) {
            AuditLogger.log(userId, username, "TRANSFER_COMPLETE_DENIED",
                          "failure", clientIp,
                          "Insufficient clearance for completing transfer");
            return Result.err("Access denied: Insufficient clearance for transfers");
        }

        if (transferId <= 0) {
            return Result.err("Invalid transfer ID");
        }

        // Call DAO to complete transfer (handles transaction)
        Result<String, String> result = TransferDAO.completeTransfer(transferId);

        if (result.isOk()) {
            AuditLogger.log(userId, username, "TRANSFER_COMPLETED",
                          "success", clientIp,
                          String.format("Transfer %d completed", transferId));
        } else {
            AuditLogger.log(userId, username, "TRANSFER_COMPLETE_FAILED",
                          "failure", clientIp,
                          "Error: " + result.unwrapErr());
        }

        return result;
    }

    /**
     * Get all pending transfers
     * Requires SECRET clearance (manager level)
     *
     * @param userId User ID
     * @param username Username for audit logging
     * @param userClearance User's clearance level
     * @param clientIp Client IP for audit logging
     * @return Result containing list of pending transfers or error
     */
    public static Result<List<Map<String, Object>>, String> getPendingTransfers(long userId,
                                                                                 String username,
                                                                                 SecurityLevel userClearance,
                                                                                 String clientIp) {
        // BLP Access Control: Viewing transfers requires SECRET clearance
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.SECRET)) {
            AuditLogger.log(userId, username, "VIEW_TRANSFERS_DENIED",
                          "failure", clientIp,
                          "Insufficient clearance to view transfers");
            return Result.err("Access denied: Insufficient clearance to view transfers");
        }

        Result<List<Map<String, Object>>, String> result = TransferDAO.getPendingTransfers();

        if (result.isOk()) {
            AuditLogger.log(userId, username, "VIEW_TRANSFERS",
                          "success", clientIp,
                          String.format("Retrieved %d pending transfers", result.unwrap().size()));
        }

        return result;
    }

    /**
     * Get transfer details by tracking number
     * Requires at least CONFIDENTIAL clearance
     *
     * @param trackingNumber Package tracking number
     * @param userId User ID
     * @param username Username for audit logging
     * @param userClearance User's clearance level
     * @param clientIp Client IP for audit logging
     * @return Result containing transfer details or error
     */
    public static Result<Map<String, Object>, String> getTransferByTracking(String trackingNumber,
                                                                             long userId,
                                                                             String username,
                                                                             SecurityLevel userClearance,
                                                                             String clientIp) {
        // BLP Access Control: Requires at least CONFIDENTIAL clearance
        if (!BLPAccessControl.checkReadAccess(userClearance, SecurityLevel.CONFIDENTIAL)) {
            AuditLogger.log(userId, username, "TRANSFER_LOOKUP_DENIED",
                          "failure", clientIp,
                          "Insufficient clearance for transfer lookup");
            return Result.err("Access denied: Insufficient clearance");
        }

        // Input validation
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            return Result.err("Tracking number is required");
        }

        // Sanitize input (prevent SQL injection, though we use prepared statements)
        String sanitized = trackingNumber.trim().replaceAll("[^a-zA-Z0-9]", "");
        if (sanitized.isEmpty()) {
            return Result.err("Invalid tracking number format");
        }

        Result<Map<String, Object>, String> result = TransferDAO.getTransferByTracking(sanitized);

        if (result.isOk()) {
            AuditLogger.log(userId, username, "TRANSFER_LOOKUP",
                          "success", clientIp,
                          "Tracking: " + sanitized);
        }

        return result;
    }
}
