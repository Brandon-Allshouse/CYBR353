package com.delivery.models;

import java.util.ArrayList;
import java.util.List;

/**
 * RouteRequest - Request body for route optimization
 */
public class RouteRequest {
    private String warehouseAddress;
    private List<String> deliveryAddresses;
    private long driverId;
    private long facilityId;
    private String routeDate;

    public RouteRequest() {
        this.deliveryAddresses = new ArrayList<>();
    }

    public String getWarehouseAddress() { return warehouseAddress; }
    public void setWarehouseAddress(String warehouseAddress) { this.warehouseAddress = warehouseAddress; }

    public List<String> getDeliveryAddresses() { return deliveryAddresses; }
    public void setDeliveryAddresses(List<String> deliveryAddresses) { this.deliveryAddresses = deliveryAddresses; }

    public long getDriverId() { return driverId; }
    public void setDriverId(long driverId) { this.driverId = driverId; }

    public long getFacilityId() { return facilityId; }
    public void setFacilityId(long facilityId) { this.facilityId = facilityId; }

    public String getRouteDate() { return routeDate; }
    public void setRouteDate(String routeDate) { this.routeDate = routeDate; }

    public static RouteRequest fromJson(String json) {
        RouteRequest request = new RouteRequest();
        
        json = json.trim().substring(1, json.length() - 1);
        
        // Extract warehouseAddress
        if (json.contains("\"warehouseAddress\":\"")) {
            int warehouseStart = json.indexOf("\"warehouseAddress\":\"") + 20;
            int warehouseEnd = json.indexOf("\"", warehouseStart);
            request.setWarehouseAddress(json.substring(warehouseStart, warehouseEnd));
        }
        
        // Extract deliveryAddresses array
        if (json.contains("\"deliveryAddresses\":[")) {
            int arrayStart = json.indexOf("[", json.indexOf("\"deliveryAddresses\":")) + 1;
            int arrayEnd = json.indexOf("]", arrayStart);
            String addressesStr = json.substring(arrayStart, arrayEnd);
            
            if (!addressesStr.trim().isEmpty()) {
                String[] addresses = addressesStr.split("\",\"");
                List<String> deliveryList = new ArrayList<>();
                for (String addr : addresses) {
                    deliveryList.add(addr.replace("\"", "").trim());
                }
                request.setDeliveryAddresses(deliveryList);
            }
        }
        
        // Extract driverId
        if (json.contains("\"driverId\":")) {
            int driverIdStart = json.indexOf("\"driverId\":") + 11;
            int driverIdEnd = json.indexOf(",", driverIdStart);
            if (driverIdEnd == -1) driverIdEnd = json.indexOf("}", driverIdStart);
            String driverIdStr = json.substring(driverIdStart, driverIdEnd).trim();
            request.setDriverId(Long.parseLong(driverIdStr));
        }
        
        // Extract facilityId
        if (json.contains("\"facilityId\":")) {
            int facilityIdStart = json.indexOf("\"facilityId\":") + 13;
            int facilityIdEnd = json.indexOf(",", facilityIdStart);
            if (facilityIdEnd == -1) facilityIdEnd = json.indexOf("}", facilityIdStart);
            String facilityIdStr = json.substring(facilityIdStart, facilityIdEnd).trim();
            request.setFacilityId(Long.parseLong(facilityIdStr));
        }
        
        // Extract routeDate
        if (json.contains("\"routeDate\":\"")) {
            int dateStart = json.indexOf("\"routeDate\":\"") + 13;
            int dateEnd = json.indexOf("\"", dateStart);
            request.setRouteDate(json.substring(dateStart, dateEnd));
        }
        
        return request;
    }
}