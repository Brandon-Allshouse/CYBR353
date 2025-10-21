package com.delivery.security;


public enum SecurityLevel {
UNCLASSIFIED,
CONFIDENTIAL,
SECRET,
TOP_SECRET;


public static SecurityLevel fromString(String s) {
if (s == null) return UNCLASSIFIED;
try {
return SecurityLevel.valueOf(s.trim().toUpperCase());
} catch (IllegalArgumentException e) {
return UNCLASSIFIED;
}
}
}