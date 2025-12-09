package com.delivery.models;

import java.sql.Timestamp;
import java.util.List;

/**
 * RouteAssignment - Represents an assigned route to a driver
 */
public class RouteAssignment {
    private long id;
    private long driverId;
    private String warehouseAddress;
    private String routeJson;
    private double totalDistance;
    private int estimatedDuration;
    private Timestamp createdAt;

    public RouteAssignment() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDriverId() { return driverId; }
    public void setDriverId(long driverId) { this.driverId = driverId; }

    public String getWarehouseAddress() { return warehouseAddress; }
    public void setWarehouseAddress(String warehouseAddress) { this.warehouseAddress = warehouseAddress; }

    public String getRouteJson() { return routeJson; }
    public void setRouteJson(String routeJson) { this.routeJson = routeJson; }

    public void setRoute(List<Location> route) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < route.size(); i++) {
            if (i > 0) json.append(",");
            json.append(route.get(i).toJson());
        }
        json.append("]");
        this.routeJson = json.toString();
    }

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }

    public int getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(int estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String toJson() {
        return "{" +
               "\"id\":" + id + "," +
               "\"driverId\":" + driverId + "," +
               "\"warehouseAddress\":\"" + escapeJson(warehouseAddress) + "\"," +
               "\"route\":" + routeJson + "," +
               "\"totalDistance\":" + totalDistance + "," +
               "\"estimatedDuration\":" + estimatedDuration + "," +
               "\"createdAt\":\"" + (createdAt != null ? createdAt.toString() : "") + "\"" +
               "}";
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}