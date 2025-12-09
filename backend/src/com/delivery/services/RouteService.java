package com.delivery.services;

import com.delivery.dao.RouteDAO;
import com.delivery.database.DatabaseConnection;
import com.delivery.models.*;
import com.delivery.util.Result;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

/**
 * RouteService - Handles route optimization logic
 */
public class RouteService {
    private final GeocodingService geocodingService;

    public RouteService() {
        this.geocodingService = new GeocodingService();
    }

    /**
     * Optimizes a route using nearest-neighbor algorithm
     */
    public OptimizedRoute optimizeRoute(RouteRequest request) throws Exception {
        // 1. Geocode warehouse address
        Location warehouse = geocodingService.geocode(request.getWarehouseAddress());

        // 2. Geocode all delivery addresses
        List<Location> deliveries = new ArrayList<>();
        for (String address : request.getDeliveryAddresses()) {
            try {
                Location location = geocodingService.geocode(address);
                deliveries.add(location);
            } catch (Exception e) {
                throw new Exception("Failed to geocode address: " + address + " - " + e.getMessage());
            }
        }

        // 3. Run optimization algorithm
        List<Location> optimizedRoute = nearestNeighborOptimization(warehouse, deliveries);

        // 4. Calculate total distance and duration
        double totalDistance = calculateTotalDistance(optimizedRoute, warehouse);
        int estimatedDuration = (int) Math.round((totalDistance / 40.0) * 60); // 40 km/h avg speed

        // 5. Save to database if driverId + facilityId provided
        if (request.getDriverId() > 0 && request.getFacilityId() > 0) {
            // Get DB connection via Result<Connection,String>
            Result<Connection, String> connResult = DatabaseConnection.getConnection();
            if (connResult.isErr()) {
                throw new Exception("Failed to get database connection: " + connResult.unwrapErr());
            }

            Connection rawConn = connResult.unwrap();
            try (Connection conn = rawConn) {
                // Build route JSON
                StringBuilder routeJson = new StringBuilder("[");
                for (int i = 0; i < optimizedRoute.size(); i++) {
                    if (i > 0) routeJson.append(",");
                    routeJson.append(optimizedRoute.get(i).toJson());
                }
                routeJson.append("]");

                String routeDate = request.getRouteDate() != null ?
                        request.getRouteDate() : LocalDate.now().toString();

                Result<Long, String> result = RouteDAO.saveOptimizedRoute(
                        conn,
                        request.getDriverId(),
                        warehouse.getAddress(),
                        routeJson.toString(),
                        totalDistance,
                        estimatedDuration,
                        request.getFacilityId(),
                        routeDate
                );

                if (result.isErr()) {
                    throw new Exception("Failed to save route: " + result.unwrapErr());
                }
            }
        }

        // 6. Return optimized route
        return new OptimizedRoute(optimizedRoute, totalDistance, estimatedDuration);
    }

    /**
     * Nearest neighbor algorithm for route optimization
     */
    private List<Location> nearestNeighborOptimization(Location warehouse, List<Location> deliveries) {
        List<Location> route = new ArrayList<>();
        route.add(warehouse);

        List<Location> remaining = new ArrayList<>(deliveries);
        Location current = warehouse;

        while (!remaining.isEmpty()) {
            Location nearest = null;
            double minDistance = Double.MAX_VALUE;

            for (Location loc : remaining) {
                double dist = calculateDistance(current, loc);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = loc;
                }
            }

            route.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return route;
    }

    /**
     * Calculates distance between two locations using Haversine formula
     */
    private double calculateDistance(Location loc1, Location loc2) {
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(loc2.getLat() - loc1.getLat());
        double dLon = Math.toRadians(loc2.getLon() - loc1.getLon());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(loc1.getLat())) *
                        Math.cos(Math.toRadians(loc2.getLat())) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Calculates total distance including return to warehouse
     */
    private double calculateTotalDistance(List<Location> route, Location warehouse) {
        double totalDistance = 0;

        for (int i = 0; i < route.size() - 1; i++) {
            totalDistance += calculateDistance(route.get(i), route.get(i + 1));
        }

        // Add return to warehouse
        totalDistance += calculateDistance(route.get(route.size() - 1), warehouse);

        return totalDistance;
    }

    /**
     * Gets routes assigned to a specific driver
     */
    public String getDriverRoutes(long driverId) throws Exception {
        // Get DB connection via Result<Connection,String>
        Result<Connection, String> connResult = DatabaseConnection.getConnection();
        if (connResult.isErr()) {
            throw new Exception(connResult.unwrapErr());
        }

        Connection rawConn = connResult.unwrap();
        try (Connection conn = rawConn) {
            Result<List<Map<String, Object>>, String> result =
                    RouteDAO.getDriverOptimizedRoutes(conn, driverId);

            if (result.isErr()) {
                throw new Exception(result.unwrapErr());
            }

            List<Map<String, Object>> routes = result.unwrap();

            // Convert to JSON
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < routes.size(); i++) {
                if (i > 0) json.append(",");
                json.append(mapToJson(routes.get(i)));
            }
            json.append("]");

            return json.toString();
        }
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
        }
        json.append("}");
        return json.toString();
    }
}
