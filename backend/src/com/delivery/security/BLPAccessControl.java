package com.delivery.security;

public class BLPAccessControl {

    // No read up
    public static boolean checkReadAccess(SecurityLevel userClearance, SecurityLevel objectClass) {
        return userClearance.ordinal() >= objectClass.ordinal();
    }

    // No write down
    public static boolean checkWriteAccess(SecurityLevel userClearance, SecurityLevel objectClass) {
        return userClearance.ordinal() <= objectClass.ordinal();
    }
}
