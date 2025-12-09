// driver.js - frontend for drivers to view route and update status

console.log('driver.js loaded');

/**
 * Fetch driver's assigned route from backend
 */
async function fetchDriverRoute() {
    console.log('Fetching driver route from /api/driver/route');
    try {
        const response = await fetch('http://localhost:8081/api/driver/route', {
            method: 'GET',
            credentials: 'include'
        });

        console.log('Driver route response status:', response.status);

        if (!response.ok) {
            console.error('Driver route endpoint returned:', response.status);
            return { success: false, error: `HTTP ${response.status}` };
        }

        const data = await response.json();
        console.log('Driver route data:', data);

        if (data.route) {
            return { success: true, route: data.route };
        } else {
            return { success: true, route: null, message: data.message || 'No route assigned' };
        }
    } catch (error) {
        console.error('Error fetching driver route:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Update delivery status for a package
 */
async function updateDeliveryStatus(packageId, status, notes = '', location = '') {
    console.log('Updating delivery status:', { packageId, status, notes, location });
    try {
        const response = await fetch('http://localhost:8081/api/driver/status', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({ packageId, status, notes, location })
        });

        const data = await response.json();

        if (response.ok) {
            return { success: true, data };
        } else {
            return { success: false, error: data.error || 'Failed to update status' };
        }
    } catch (error) {
        console.error('Error updating delivery status:', error);
        return { success: false, error: error.message };
    }
}

// Driver dashboard initialization
function initializeDriverDashboard() {
    console.log('Initializing driver dashboard page...');

    async function loadRoute() {
        console.log('Loading driver route...');
        const result = await fetchDriverRoute();

        if (result.success && result.route) {
            const route = result.route;
            console.log(`Loaded route with ${route.packages.length} packages`);

            // Update summary cards
            const totalStopsEl = document.getElementById('totalStops');
            const completedEl = document.getElementById('completedDeliveries');
            const remainingEl = document.getElementById('remainingDeliveries');

            if (totalStopsEl) totalStopsEl.textContent = route.totalStops || route.packages.length;

            const completed = route.packages.filter(p => p.status === 'delivered').length;
            const remaining = route.packages.length - completed;

            if (completedEl) completedEl.textContent = completed;
            if (remainingEl) remainingEl.textContent = remaining;

            // Update route summary
            const summaryEl = document.getElementById('routeSummary');
            if (summaryEl) {
                summaryEl.innerHTML = `
                    <strong>Route:</strong> ${route.routeName}<br>
                    <strong>Date:</strong> ${route.routeDate}<br>
                    <strong>Status:</strong> ${route.routeStatus}<br>
                    <strong>Facility:</strong> ${route.facilityName}<br>
                    <strong>Vehicle:</strong> ${route.vehicleId || 'Not assigned'}<br>
                    <strong>Estimated Duration:</strong> ${route.estimatedDurationMinutes} minutes
                `;
            }

            // Populate packages table
            const tbody = document.getElementById('driverAssignmentsTable');
            if (tbody) {
                tbody.innerHTML = '';

                route.packages.forEach(pkg => {
                    const row = document.createElement('tr');
                    const fullAddress = `${pkg.streetAddress}, ${pkg.city}, ${pkg.state} ${pkg.zipCode}`;

                    // Status badge with color
                    let statusBadge = pkg.status;
                    if (pkg.status === 'delivered') {
                        statusBadge = `<span style="color: green; font-weight: bold;">✓ ${pkg.status}</span>`;
                    } else if (pkg.status === 'out_for_delivery') {
                        statusBadge = `<span style="color: orange;">→ ${pkg.status}</span>`;
                    }

                    // Action buttons
                    let actionButtons = '';
                    if (pkg.status !== 'delivered') {
                        actionButtons = `
                            <button class="btn-small" onclick="markDelivered(${pkg.packageId}, '${pkg.trackingNumber}')">
                                Mark Delivered
                            </button>
                        `;
                    }

                    row.innerHTML = `
                        <td>${pkg.stopSequence}</td>
                        <td>${pkg.trackingNumber}</td>
                        <td>${pkg.customerName || 'N/A'}</td>
                        <td>${fullAddress}</td>
                        <td>${pkg.estimatedArrival || 'N/A'}</td>
                        <td>${statusBadge}</td>
                        <td>${actionButtons}</td>
                    `;
                    tbody.appendChild(row);
                });

                console.log(`Loaded ${route.packages.length} packages into table`);
            }
        } else if (result.success && !result.route) {
            console.log('No route assigned for today');
            const tbody = document.getElementById('driverAssignmentsTable');
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="7" class="no-data">No route assigned for today.</td></tr>';
            }
            const summaryEl = document.getElementById('routeSummary');
            if (summaryEl) {
                summaryEl.textContent = result.message || 'No active route';
            }
        } else {
            console.error('Failed to load route:', result.error);
            alert('Failed to load route: ' + result.error);
        }
    }

    loadRoute();
    window.reloadRoute = loadRoute;
}

// Mark package as delivered
window.markDelivered = async function(packageId, trackingNumber) {
    const confirmed = confirm(`Mark package ${trackingNumber} as delivered?`);
    if (!confirmed) return;

    const result = await updateDeliveryStatus(packageId, 'delivered', 'Delivered to customer', '');

    if (result.success) {
        alert(`Package ${trackingNumber} marked as delivered!`);
        if (window.reloadRoute) {
            window.reloadRoute();
        }
    } else {
        alert(`Failed to update status: ${result.error}`);
    }
};

// View route page initialization
function initializeViewRoute() {
    console.log('Initializing view route page...');

    async function loadRouteStops() {
        console.log('Loading route stops...');
        const result = await fetchDriverRoute();

        const tbody = document.getElementById('routeStopsTable');
        if (!tbody) return;

        if (result.success && result.route) {
            const route = result.route;
            console.log(`Loaded route with ${route.packages.length} packages`);

            tbody.innerHTML = '';

            // Group packages by delivery address
            const addressMap = new Map();
            route.packages.forEach(pkg => {
                const key = `${pkg.streetAddress}, ${pkg.city}, ${pkg.state} ${pkg.zipCode}`;
                if (!addressMap.has(key)) {
                    addressMap.set(key, {
                        address: key,
                        packages: [],
                        stopSequence: pkg.stopSequence,
                        estimatedArrival: pkg.estimatedArrival,
                        deliveryInstructions: pkg.deliveryInstructions
                    });
                }
                addressMap.get(key).packages.push(pkg);
            });

            // Display each stop
            let stopNumber = 1;
            addressMap.forEach((stop) => {
                const row = document.createElement('tr');
                const packageList = stop.packages.map(p => p.trackingNumber).join(', ');
                const allDelivered = stop.packages.every(p => p.status === 'delivered');

                row.innerHTML = `
                    <td>${stopNumber}</td>
                    <td>${stop.address}</td>
                    <td>${stop.estimatedArrival || 'N/A'}</td>
                    <td>${packageList}</td>
                    <td>${stop.deliveryInstructions || 'None'}</td>
                    <td>${allDelivered ? '<span style="color: green;">Delivered</span>' : '<span style="color: orange;">Pending</span>'}</td>
                `;
                tbody.appendChild(row);
                stopNumber++;
            });

            console.log(`Loaded ${addressMap.size} stops`);
        } else if (result.success && !result.route) {
            tbody.innerHTML = '<tr><td colspan="6" class="no-data">No route assigned for today.</td></tr>';
        } else {
            tbody.innerHTML = '<tr><td colspan="6" class="no-data">Error loading route.</td></tr>';
        }
    }

    loadRouteStops();
}

// Auto-initialize based on which page elements exist
console.log('Checking page elements...');
console.log('Document readyState:', document.readyState);

function initializePage() {
    if (document.getElementById('driverAssignmentsTable')) {
        console.log('Driver dashboard page detected');
        initializeDriverDashboard();
    } else if (document.getElementById('routeStopsTable')) {
        console.log('View route page detected');
        initializeViewRoute();
    } else {
        console.log('No driver page elements found');
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializePage);
} else {
    initializePage();
}
