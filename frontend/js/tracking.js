// Tracking and Status JavaScript

// Get orders from localStorage
function getOrders() {
    return JSON.parse(localStorage.getItem('orders')) || [];
}

// Save orders to localStorage
function saveOrders(orders) {
    localStorage.setItem('orders', JSON.stringify(orders));
}

// Display all packages in status table
function displayAllPackages() {
    const orders = getOrders();
    const tableBody = document.getElementById('statusTable');
    
    if (orders.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="6" class="no-data">No packages to track yet.</td></tr>';
        return;
    }
    
    tableBody.innerHTML = orders.map(order => `
        <tr>
            <td>${order.id}</td>
            <td>${order.customerName}</td>
            <td><span class="status-badge status-${order.status.toLowerCase().replace(/ /g, '-')}">${order.status}</span></td>
            <td>${order.location}</td>
            <td>${order.estimatedDelivery}</td>
            <td>
                <button class="btn" onclick="viewDetails('${order.id}')">View</button>
            </td>
        </tr>
    `).join('');
}

// View package details
function viewDetails(trackingId) {
    const orders = getOrders();
    const order = orders.find(o => o.id === trackingId);
    
    if (order) {
        document.getElementById('trackingId').value = trackingId;
        showTrackingResult(order);
    }
}

// Show tracking result
function showTrackingResult(order) {
    const resultDiv = document.getElementById('trackingResult');
    
    document.getElementById('resultTrackingId').textContent = order.id;
    document.getElementById('resultCustomer').textContent = order.customerName;
    document.getElementById('resultStatus').textContent = order.status;
    document.getElementById('resultStatus').className = `status-badge status-${order.status.toLowerCase().replace(/ /g, '-')}`;
    document.getElementById('resultLocation').textContent = order.location;
    document.getElementById('resultEstimate').textContent = order.estimatedDelivery;
    
    resultDiv.style.display = 'block';
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

// Handle tracking form submission
document.getElementById('trackingForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const trackingId = document.getElementById('trackingId').value.trim();
    const orders = getOrders();
    const order = orders.find(o => o.id === trackingId);
    
    if (order) {
        showTrackingResult(order);
        showMessage('Package found!', 'success');
    } else {
        document.getElementById('trackingResult').style.display = 'none';
        showMessage('Invalid tracking ID. Please check and try again.', 'error');
    }
});

// Handle status update form submission
document.getElementById('updateStatusForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const trackingId = document.getElementById('updateTrackingId').value.trim();
    const newStatus = document.getElementById('newStatus').value;
    const newLocation = document.getElementById('newLocation').value;
    
    const orders = getOrders();
    const orderIndex = orders.findIndex(o => o.id === trackingId);
    
    if (orderIndex !== -1) {
        orders[orderIndex].status = newStatus;
        orders[orderIndex].location = newLocation;
        orders[orderIndex].lastUpdated = new Date().toISOString();
        
        saveOrders(orders);
        displayAllPackages();
        
        this.reset();
        showMessage('Package status updated successfully!', 'success');
    } else {
        showMessage('Invalid tracking ID. Please check and try again.', 'error');
    }
});

// Initial display
displayAllPackages();