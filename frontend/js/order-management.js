// Order Management JavaScript

// Get orders from localStorage or initialize empty array
let orders = JSON.parse(localStorage.getItem('orders')) || [];

// Generate tracking ID
function generateTrackingId() {
    return 'TRK-' + Math.random().toString(36).substr(2, 9).toUpperCase();
}

// Save orders to localStorage
function saveOrders() {
    localStorage.setItem('orders', JSON.stringify(orders));
}

// Display orders in table
function displayOrders() {
    const tableBody = document.getElementById('ordersTable');
    
    if (orders.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="6" class="no-data">No orders yet. Create an order above.</td></tr>';
        return;
    }
    
    tableBody.innerHTML = orders.map(order => `
        <tr>
            <td>${order.id}</td>
            <td>${order.customerName}</td>
            <td>${order.pickupAddress}</td>
            <td>${order.deliveryAddress}</td>
            <td><span class="status-badge status-pending">${order.status}</span></td>
            <td>
                <button class="btn btn-danger" onclick="deleteOrder('${order.id}')">Delete</button>
            </td>
        </tr>
    `).join('');
}

// Delete order
function deleteOrder(orderId) {
    if (confirm('Are you sure you want to delete this order?')) {
        orders = orders.filter(order => order.id !== orderId);
        saveOrders();
        displayOrders();
        showMessage('Order deleted successfully', 'success');
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

// Handle form submission
document.getElementById('orderForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const newOrder = {
        id: generateTrackingId(),
        customerName: document.getElementById('customerName').value,
        customerEmail: document.getElementById('customerEmail').value,
        pickupAddress: document.getElementById('pickupAddress').value,
        deliveryAddress: document.getElementById('deliveryAddress').value,
        packageDetails: document.getElementById('packageDetails').value,
        status: 'Order Placed',
        location: 'Facility',
        estimatedDelivery: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toLocaleDateString(),
        createdAt: new Date().toISOString()
    };
    
    orders.push(newOrder);
    saveOrders();
    displayOrders();
    
    // Reset form
    this.reset();
    
    showMessage(`Order created successfully! Tracking ID: ${newOrder.id}`, 'success');
});

// Initial display
displayOrders();
