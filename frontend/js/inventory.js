// Inventory and Transfer JavaScript

// Initialize inventory data
let inventory = JSON.parse(localStorage.getItem('inventory')) || {
    'FAC-001': [],
    'FAC-002': [],
    'FAC-003': [],
    'FAC-004': []
};

let transfers = JSON.parse(localStorage.getItem('transfers')) || [];

// Save data to localStorage
function saveData() {
    localStorage.setItem('inventory', JSON.stringify(inventory));
    localStorage.setItem('transfers', JSON.stringify(transfers));
}

// Get orders from localStorage
function getOrders() {
    return JSON.parse(localStorage.getItem('orders')) || [];
}

// View inventory for selected facility
function viewInventory() {
    const facilityId = document.getElementById('facilitySelect').value;
    const tableBody = document.getElementById('inventoryTable');
    const packages = inventory[facilityId] || [];
    
    if (packages.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="5" class="no-data">No packages in this facility.</td></tr>';
        return;
    }
    
    tableBody.innerHTML = packages.map(pkg => `
        <tr>
            <td>${pkg.trackingId}</td>
            <td>${pkg.details}</td>
            <td>${pkg.destination}</td>
            <td>${pkg.arrivalDate}</td>
            <td><span class="status-badge status-pending">${pkg.status}</span></td>
        </tr>
    `).join('');
}

// Display active transfers
function displayTransfers() {
    const tableBody = document.getElementById('transfersTable');
    
    if (transfers.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="6" class="no-data">No active transfers.</td></tr>';
        return;
    }
    
    tableBody.innerHTML = transfers.map(transfer => `
        <tr>
            <td>${transfer.id}</td>
            <td>${transfer.trackingId}</td>
            <td>${transfer.source}</td>
            <td>${transfer.destination}</td>
            <td>${transfer.method}</td>
            <td><span class="status-badge status-in-transit">${transfer.status}</span></td>
        </tr>
    `).join('');
}

// Generate transfer ID
function generateTransferId() {
    return 'TRF-' + Math.random().toString(36).substr(2, 9).toUpperCase();
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

// Add sample packages to facilities if inventory is empty
function initializeSampleInventory() {
    const orders = getOrders();
    
    if (orders.length > 0 && Object.values(inventory).every(arr => arr.length === 0)) {
        orders.forEach((order, index) => {
            const facilityId = ['FAC-001', 'FAC-002', 'FAC-003', 'FAC-004'][index % 4];
            inventory[facilityId].push({
                trackingId: order.id,
                details: order.packageDetails || 'Package',
                destination: order.deliveryAddress,
                arrivalDate: new Date(order.createdAt).toLocaleDateString(),
                status: 'In Facility'
            });
        });
        saveData();
    }
}

// Handle transfer form submission
document.getElementById('transferForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    const trackingId = document.getElementById('transferTrackingId').value.trim();
    const source = document.getElementById('sourceFacility').value;
    const destination = document.getElementById('destFacility').value;
    const method = document.getElementById('transportMethod').value;
    
    if (source === destination) {
        showMessage('Source and destination facilities cannot be the same!', 'error');
        return;
    }
    
    // Check if package exists in source facility
    const sourcePackages = inventory[source] || [];
    const packageIndex = sourcePackages.findIndex(pkg => pkg.trackingId === trackingId);
    
    if (packageIndex === -1) {
        showMessage('Package not found in source facility!', 'error');
        return;
    }
    
    // Create transfer
    const transfer = {
        id: generateTransferId(),
        trackingId: trackingId,
        source: source,
        destination: destination,
        method: method,
        status: 'In Transit',
        initiatedAt: new Date().toISOString()
    };
    
    // Remove from source
    const packageData = sourcePackages[packageIndex];
    inventory[source].splice(packageIndex, 1);
    
    // Add to transfers
    transfers.push(transfer);
    
    // Simulate transfer completion after a delay (for demo purposes, we'll add it to destination immediately)
    inventory[destination] = inventory[destination] || [];
    inventory[destination].push({
        ...packageData,
        arrivalDate: new Date().toLocaleDateString()
    });
    
    saveData();
    displayTransfers();
    viewInventory();
    
    this.reset();
    showMessage(`Transfer initiated successfully! Transfer ID: ${transfer.id}`, 'success');
});

// Initialize
initializeSampleInventory();
displayTransfers();
