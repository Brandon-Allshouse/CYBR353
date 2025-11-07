package com.delivery.security;

import com.delivery.util.Result;

/**
 * Implements Bell-LaPadula (BLP) mandatory access control model
 * Enforces "no read up" and "no write down" security properties
 */
public class BLPAccessControl {

    /**
     * Simple Security Property ("no read up"): subject can only read objects at or below their clearance
     * Example: CONFIDENTIAL user can read UNCLASSIFIED and CONFIDENTIAL, but not SECRET
     */
    public static boolean checkReadAccess(SecurityLevel subjectClearance, SecurityLevel objectClassification) {
        boolean allowed = subjectClearance.ordinal() >= objectClassification.ordinal();

        if (!allowed) {
            // Log access denial for security audit trail (IP not available for internal BLP checks)
            AuditLogger.logSecurityEvent(null, "SYSTEM", "BLP_READ_DENIED", null,
                String.format("Read denied: %s clearance cannot read %s data",
                    subjectClearance.name(), objectClassification.name()));
        }

        return allowed;
    }

    /**
     * Star Property ("no write down"): subject can only write to objects at or above their clearance
     * Prevents classified information from being leaked to lower security levels
     * Example: SECRET user cannot write to CONFIDENTIAL objects
     */
    public static boolean checkWriteAccess(SecurityLevel subjectClearance, SecurityLevel objectClassification) {
        boolean allowed = subjectClearance.ordinal() <= objectClassification.ordinal();

        if (!allowed) {
            // Log access denial for security audit trail (IP not available for internal BLP checks)
            AuditLogger.logSecurityEvent(null, "SYSTEM", "BLP_WRITE_DENIED", null,
                String.format("Write denied: %s clearance cannot write to %s data",
                    subjectClearance.name(), objectClassification.name()));
        }

        return allowed;
    }

    public static Result<Boolean, String> checkAccess(SecurityLevel subjectClearance,
                                                      SecurityLevel objectClassification,
                                                      String operation) {
        if (subjectClearance == null) {
            return Result.err("Subject clearance cannot be null");
        }
        if (objectClassification == null) {
            return Result.err("Object classification cannot be null");
        }

        boolean allowed;
        if ("READ".equalsIgnoreCase(operation)) {
            allowed = checkReadAccess(subjectClearance, objectClassification);
        } else if ("WRITE".equalsIgnoreCase(operation)) {
            allowed = checkWriteAccess(subjectClearance, objectClassification);
        } else {
            return Result.err("Invalid operation: " + operation + " (must be READ or WRITE)");
        }

        return Result.ok(allowed);
    }

    /**
     * Maps business data types to BLP classification levels
     * Default to TOP_SECRET for unknown types (fail-secure)
     */
    public static SecurityLevel getDataClassification(String dataType) {
        if (dataType == null) {
            return SecurityLevel.TOP_SECRET;
        }

        switch (dataType.toUpperCase()) {
            case "PUBLIC_INFO":
            case "MARKETING":
                return SecurityLevel.UNCLASSIFIED;

            case "PACKAGE_INFO":
            case "ROUTE_INFO":
            case "TRACKING":
                return SecurityLevel.CONFIDENTIAL;

            case "USER_PII":
            case "PAYMENT_INFO":
            case "FACILITY_INVENTORY":
                return SecurityLevel.SECRET;

            case "OPTIMIZATION_ALGORITHM":
            case "SYSTEM_LOGS":
            case "AUDIT_LOGS":
                return SecurityLevel.TOP_SECRET;

            default:
                return SecurityLevel.TOP_SECRET;
        }
    }
}
