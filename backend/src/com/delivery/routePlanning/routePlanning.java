package com.delivery.routePlanning;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import com.delivery.util.Route;
import com.delivery.util.Package;

public class routePlanning {
    
    public static List<List<Package>> createRoutes(List<Package> listOfAllPackages){
        // Creates a list of routes that are each a list of packages that are grouped together
        return null;
    }

    public static Queue<Package> optimizeRoute(List<Package> listOfPackagesOfCreatedRoute){
        // Optimizes the route based on some criteria and put them in a queue based on first in being the first stop
        return null;
    }

    public static void assignRoute(Queue<Package> route, String vehicleId, String driverId){
        // Turn route into a route object
        // Assigns the optimized route to a delivery vehicle and driver. Then log the assignment. 
        // Then store the route in route assignment database.
    }

    public static Route retrieveRoute(String driverId){
        // Retrieve the route assigned to a specific driver from the route assignment database
        return null;
    }
}
