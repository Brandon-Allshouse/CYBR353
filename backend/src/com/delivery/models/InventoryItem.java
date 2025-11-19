package com.delivery.models;

import java.sql.Timestamp;

/**
 * InventoryItem - Represents a package in facility inventory with full details
 * Joins data from: inventory, packages, orders, facilities, addresses tables
 */
public class InventoryItem {
    // Inventory fields
    private long inventoryId;
    private long facilityId;
    private String facilityName;
    private Timestamp arrivalTime;
    private Timestamp departureTime;
    private String inventoryStatus;  // in_stock, checked_out, transferred

    // Package fields
    private long packageId;
    private String trackingNumber;
    private String packageStatus;    // created, at_facility, in_transit, out_for_delivery, delivered, returned, lost
    private double weightKg;
    private double lengthCm;
    private double widthCm;
    private double heightCm;
    private boolean fragile;
    private boolean signatureRequired;

    // Order fields
    private long orderId;
    private long customerId;
    private String customerName;
    private String orderStatus;

    // Address fields
    private String pickupAddress;
    private String deliveryAddress;
    private String deliveryInstructions;

    // Default constructor
    public InventoryItem() {}

    // Full constructor
    public InventoryItem(long inventoryId, long facilityId, String facilityName, Timestamp arrivalTime,
                        Timestamp departureTime, String inventoryStatus, long packageId, String trackingNumber,
                        String packageStatus, double weightKg, double lengthCm, double widthCm, double heightCm,
                        boolean fragile, boolean signatureRequired, long orderId, long customerId,
                        String customerName, String orderStatus, String pickupAddress, String deliveryAddress,
                        String deliveryInstructions) {
        this.inventoryId = inventoryId;
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.inventoryStatus = inventoryStatus;
        this.packageId = packageId;
        this.trackingNumber = trackingNumber;
        this.packageStatus = packageStatus;
        this.weightKg = weightKg;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.fragile = fragile;
        this.signatureRequired = signatureRequired;
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.orderStatus = orderStatus;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.deliveryInstructions = deliveryInstructions;
    }

    // Getters and Setters
    public long getInventoryId() { return inventoryId; }
    public void setInventoryId(long inventoryId) { this.inventoryId = inventoryId; }

    public long getFacilityId() { return facilityId; }
    public void setFacilityId(long facilityId) { this.facilityId = facilityId; }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }

    public Timestamp getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Timestamp arrivalTime) { this.arrivalTime = arrivalTime; }

    public Timestamp getDepartureTime() { return departureTime; }
    public void setDepartureTime(Timestamp departureTime) { this.departureTime = departureTime; }

    public String getInventoryStatus() { return inventoryStatus; }
    public void setInventoryStatus(String inventoryStatus) { this.inventoryStatus = inventoryStatus; }

    public long getPackageId() { return packageId; }
    public void setPackageId(long packageId) { this.packageId = packageId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getPackageStatus() { return packageStatus; }
    public void setPackageStatus(String packageStatus) { this.packageStatus = packageStatus; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public double getLengthCm() { return lengthCm; }
    public void setLengthCm(double lengthCm) { this.lengthCm = lengthCm; }

    public double getWidthCm() { return widthCm; }
    public void setWidthCm(double widthCm) { this.widthCm = widthCm; }

    public double getHeightCm() { return heightCm; }
    public void setHeightCm(double heightCm) { this.heightCm = heightCm; }

    public boolean isFragile() { return fragile; }
    public void setFragile(boolean fragile) { this.fragile = fragile; }

    public boolean isSignatureRequired() { return signatureRequired; }
    public void setSignatureRequired(boolean signatureRequired) { this.signatureRequired = signatureRequired; }

    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }

    public long getCustomerId() { return customerId; }
    public void setCustomerId(long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getDeliveryInstructions() { return deliveryInstructions; }
    public void setDeliveryInstructions(String deliveryInstructions) { this.deliveryInstructions = deliveryInstructions; }

    /**
     * Converts this inventory item to JSON format
     * @return JSON string representation
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"inventoryId\":").append(inventoryId).append(",");
        json.append("\"facilityId\":").append(facilityId).append(",");
        json.append("\"facilityName\":\"").append(escapeJson(facilityName)).append("\",");
        json.append("\"arrivalTime\":\"").append(arrivalTime != null ? arrivalTime.toString() : "").append("\",");
        json.append("\"departureTime\":").append(departureTime != null ? "\"" + departureTime.toString() + "\"" : "null").append(",");
        json.append("\"inventoryStatus\":\"").append(escapeJson(inventoryStatus)).append("\",");
        json.append("\"packageId\":").append(packageId).append(",");
        json.append("\"trackingNumber\":\"").append(escapeJson(trackingNumber)).append("\",");
        json.append("\"packageStatus\":\"").append(escapeJson(packageStatus)).append("\",");
        json.append("\"weightKg\":").append(weightKg).append(",");
        json.append("\"lengthCm\":").append(lengthCm).append(",");
        json.append("\"widthCm\":").append(widthCm).append(",");
        json.append("\"heightCm\":").append(heightCm).append(",");
        json.append("\"fragile\":").append(fragile).append(",");
        json.append("\"signatureRequired\":").append(signatureRequired).append(",");
        json.append("\"orderId\":").append(orderId).append(",");
        json.append("\"customerId\":").append(customerId).append(",");
        json.append("\"customerName\":\"").append(escapeJson(customerName)).append("\",");
        json.append("\"orderStatus\":\"").append(escapeJson(orderStatus)).append("\",");
        json.append("\"pickupAddress\":\"").append(escapeJson(pickupAddress)).append("\",");
        json.append("\"deliveryAddress\":\"").append(escapeJson(deliveryAddress)).append("\",");
        json.append("\"deliveryInstructions\":\"").append(escapeJson(deliveryInstructions != null ? deliveryInstructions : "")).append("\"");
        json.append("}");
        return json.toString();
    }

    /**
     * Helper method to escape JSON strings
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
