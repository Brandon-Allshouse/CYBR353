// Routing JavaScript

// Initialize routes data
let routes = JSON.parse(localStorage.getItem('routes')) || [];

// Save routes to localStorage
function saveRoutes() {
    localStorage.setItem('routes', JSON.stringify(routes));
}

// Get orders from localStorage
function getOrders() {
    return JSON.parse(localStorage.getItem('orders')) || [];
}

// Generate route ID
function generateRouteId() {
    return 'RTE-' + Math.random().toString(36).substr(2, 9).toUpperCase();
}

// Display routes in table
function displayRoutes() {
    const tableBody = document.getElementById('routesTable');
    
    if (routes.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="7" class="no-data">No routes assigned yet.</td></tr>';
        return;
    }
    
    tableBody.innerHTML = routes.map(route => `
        <tr>
            <td>${route.id}</td>
            <td>${route.driverName}</td>
            <td>${route.date}</td>
            <td>${route.packageCount}</td>
            <td>${route.estimatedTime}h</td>
            <td><span class="status-badge status-active">${route.status}</span></td>
            <td>
                <button class="btn btn-success" onclick="completeRoute('${route.id}')">Complete</button>
                <button class="btn btn-danger" onclick="deleteRoute('${route.id}')">Delete</button>
            </td>
        </tr>
    `).join('');
}

// Display ready packages
function displayReadyPackages() {
    const orders = getOrders();
    const tableBody = document.getElementById('readyPackagesTable');
    
    // Filter packages that are ready for delivery
    const readyPackages = orders.filter(order => 
        order.status === 'Processing' || order.status === 'Order Placed'
    );
    
    if (readyPackages.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="4" class="no-data">No packages ready for delivery.</td></tr>';
        return;
    }
    
    tableBody.innerHTML = readyPackages.map(pkg => {
        const assigned = routes.some(route => 
            route.packageIds.includes(pkg.id) && route.status === 'Active'
        );
        
        return `
            <tr>
                <td>${pkg.id}</td>
                <td>${pkg.deliveryAddress}</td>
                <td><span class="status-badge status-pending">Standard</span></td>
                <td>${assigned ? 'Yes' : 'No'}</td>
            </tr>
        `;
    }).join('');
}

// Complete route
function completeRoute(routeId) {
    if (confirm('Mark this route as completed?')) {
        const routeIndex = routes.findIndex(r => r.id === routeId);
        if (routeIndex !== -1) {
            routes[routeIndex].status = 'Completed';
            saveRoutes();
            displayRoutes();
            showMessage('Route marked as completed!', 'success');
        }
    }
}

// Delete route
function deleteRoute(routeId) {
    if (confirm('Are you sure you want to delete this route?')) {
        routes = routes.filter(r => r.id !== routeId);
        saveRoutes();
        displayRoutes();
        displayReadyPackages();
        showMessage('Route deleted successfully', 'success');
    }
}

// Show message
function showMessage(message, type) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message message-${type}`;
    messageDiv.textContent = message;
    
    const container = document.querySelector('.container');
    container.insertBefore(messageDiv, container.firstChild);
    
    setTimeout(() => messageDiv.remove(), 3000);
}

// Get driver name by ID
function getDriverName(driverId) {
    const drivers = {
        'DRV-001': 'John Smith',
        'DRV-002': 'Sarah Johnson',
        'DRV-003': 'Mike Wilson'
    };
    return drivers[driverId] || 'Unknown Driver';
}

// Handle route form submission
document.getElementById('routeForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const driverId = document.getElementById('selectDriver').value;
    const routeDate = document.getElementById('routeDate').value;
    const packageIds = document.getElementById('packageIds').value.split(',').map(id => id.trim());
    const estimatedTime = document.getElementById('estimatedTime').value;
    
    // Validate that packages exist
    const orders = getOrders();
    const validPackages = packageIds.filter(id => orders.some(order => order.id === id));
    
    if (validPackages.length === 0) {
        showMessage('No valid package IDs found!', 'error');
        return;
    }
    
    if (validPackages.length !== packageIds.length) {
        showMessage(`Warning: Some package IDs were invalid. Assigned ${validPackages.length} packages.`, 'error');
    }
    
    const newRoute = {
        id: generateRouteId(),
        driverId: driverId,
        driverName: getDriverName(driverId),
        date: routeDate,
        packageIds: validPackages,
        packageCount: validPackages.length,
        estimatedTime: estimatedTime,
        status: 'Active',
        createdAt: new Date().toISOString()
    };
    
    routes.push(newRoute);
    saveRoutes();
    displayRoutes();
    displayReadyPackages();
    
    this.reset();
    showMessage(`Route assigned successfully! Route ID: ${newRoute.id}`, 'success');
});

// Initialize
displayRoutes();
displayReadyPackages();
