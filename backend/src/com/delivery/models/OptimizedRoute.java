package com.delivery.models;

import java.util.List;

/**
 * OptimizedRoute - Response containing optimized route details
 */
public class OptimizedRoute {
    private List<Location> route;
    private double totalDistance;
    private int estimatedDuration;

    public OptimizedRoute() {}

    public OptimizedRoute(List<Location> route, double totalDistance, int estimatedDuration) {
        this.route = route;
        this.totalDistance = totalDistance;
        this.estimatedDuration = estimatedDuration;
    }

    public List<Location> getRoute() { return route; }
    public void setRoute(List<Location> route) { this.route = route; }

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }

    public int getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(int estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"route\":[");
        for (int i = 0; i < route.size(); i++) {
            if (i > 0) json.append(",");
            json.append(route.get(i).toJson());
        }
        json.append("],");
        json.append("\"totalDistance\":").append(totalDistance).append(",");
        json.append("\"estimatedDuration\":").append(estimatedDuration).append(",");
        json.append("\"stops\":").append(route.size() - 1);
        json.append("}");
        return json.toString();
    }
}