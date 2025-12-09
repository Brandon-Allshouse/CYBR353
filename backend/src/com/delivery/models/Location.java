package com.delivery.models;

/**
 * Location - Represents a geographic location with address and coordinates
 */
public class Location {
    private String address;
    private double lat;
    private double lon;

    public Location() {}

    public Location(String address, double lat, double lon) {
        this.address = address;
        this.lat = lat;
        this.lon = lon;
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String toJson() {
        return "{" +
               "\"address\":\"" + escapeJson(address) + "\"," +
               "\"lat\":" + lat + "," +
               "\"lon\":" + lon +
               "}";
    }

    public static Location fromJson(String json) {
        // Simple JSON parsing
        json = json.trim().replaceAll("[{}\"]", "");
        String[] pairs = json.split(",");
        
        Location loc = new Location();
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            String key = kv[0].trim();
            String value = kv[1].trim();
            
            if ("address".equals(key)) {
                loc.setAddress(value);
            } else if ("lat".equals(key)) {
                loc.setLat(Double.parseDouble(value));
            } else if ("lon".equals(key)) {
                loc.setLon(Double.parseDouble(value));
            }
        }
        return loc;
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