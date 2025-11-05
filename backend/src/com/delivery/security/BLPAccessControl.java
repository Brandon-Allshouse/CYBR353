package com.delivery.security;

import com.delivery.util.Result;

public class BLPAccessControl {

    /**
     * Check if subject can read object
     */
    public static boolean checkReadAccess(SecurityLevel subjectClearance, SecurityLevel objectClassification) {
        // Can read if subject's clearance >= object's classification
        boolean allowed = subjectClearance.ordinal() >= objectClassification.ordinal();
        
        if (!allowed) {
            AuditLogger.logSecurityEvent("BLP_READ_DENIED", 
                String.format("Read denied: %s cannot read %s", 
                    subjectClearance.name(), objectClassification.name()),
                "SYSTEM");
        }
        
        return allowed;
    }

    /**
     * Check if subject can write to object
     */
    public static boolean checkWriteAccess(SecurityLevel subjectClearance, SecurityLevel objectClassification) {
        // Can write if subject's clearance <= object's classification
        boolean allowed = subjectClearance.ordinal() <= objectClassification.ordinal();
        
        if (!allowed) {
            AuditLogger.logSecurityEvent("BLP_WRITE_DENIED",
                String.format("Write denied: %s cannot write to %s",
                    subjectClearance.name(), objectClassification.name()),
                "SYSTEM");
        }
        
        return allowed;
    }

    /**
     * Comprehensive access check with operation type
     */
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
     * Gets the classification level for different data types
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
