// management.js - frontend for route assignment, transfers, and inventory
// NOTE: This is a regular script (not a module), so no exports

console.log('🔵 management.js loaded');

/**
 * Assign routes to drivers - calls backend API
 */
async function assignRoutes(payload) {
    try {
        const response = await fetch('http://localhost:8081/api/management/assign-routes', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.ok) {
            return { success: true, data };
        } else {
            return { success: false, error: data.error || 'Failed to assign routes' };
        }
    } catch (error) {
        console.error('Error assigning routes:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Get drivers - calls management endpoint
 */
async function getDrivers() {
    console.log('🔍 Fetching drivers from /api/management/drivers');
    try {
        const response = await fetch('http://localhost:8081/api/management/drivers', {
            method: 'GET',
            credentials: 'include'
        });

        console.log('🔍 Drivers response status:', response.status);

        if (!response.ok) {
            console.error('❌ Drivers endpoint returned:', response.status);
            return { success: false, error: `HTTP ${response.status}` };
        }

        const data = await response.json();
        console.log('🔍 Drivers data:', data);

        if (data.success) {
            return { success: true, drivers: data.drivers || [] };
        } else {
            return { success: false, error: data.error || 'Failed to fetch drivers' };
        }
    } catch (error) {
        console.error('❌ Error fetching drivers:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Get all facilities
 */
async function getFacilities() {
    console.log('🔍 Fetching facilities from /api/facilities');
    try {
        const response = await fetch('http://localhost:8081/api/facilities', {
            method: 'GET',
            credentials: 'include'
        });

        console.log('🔍 Facilities response status:', response.status);

        if (!response.ok) {
            console.error('❌ Facilities endpoint returned:', response.status);
            return { success: false, error: `HTTP ${response.status}` };
        }

        const data = await response.json();
        console.log('🔍 Facilities data:', data);

        // Handle both formats: {facilities: [...]} or [{...}]
        if (Array.isArray(data)) {
            return { success: true, facilities: data };
        } else if (data.facilities) {
            return { success: true, facilities: data.facilities };
        } else {
            return { success: true, facilities: [] };
        }
    } catch (error) {
        console.error('❌ Error fetching facilities:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Get inventory/packages for route assignment
 */
async function getInventory(facilityId = null) {
    try {
        const url = facilityId
            ? `http://localhost:8081/api/inventory/facility/${facilityId}`
            : 'http://localhost:8081/api/inventory';

        const response = await fetch(url, {
            method: 'GET',
            credentials: 'include'
        });

        if (!response.ok) {
            console.error('Inventory endpoint returned:', response.status);
            return { success: false, error: `HTTP ${response.status}` };
        }

        const data = await response.json();

        // Handle both formats: {inventory: [...]} or [{...}]
        if (Array.isArray(data)) {
            return { success: true, inventory: data };
        } else if (data.inventory) {
            return { success: true, inventory: data.inventory };
        } else {
            return { success: true, inventory: [] };
        }
    } catch (error) {
        console.error('Error fetching inventory:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Create transfer - calls backend API
 */
async function createTransfer(payload) {
    try {
        const response = await fetch('http://localhost:8081/api/transfers/initiate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.ok) {
            return { success: true, data };
        } else {
            return { success: false, error: data.error || 'Failed to create transfer' };
        }
    } catch (error) {
        console.error('Error creating transfer:', error);
        return { success: false, error: error.message };
    }
}

// =============================================================================
// ROUTE ASSIGNMENT PAGE LOGIC
// =============================================================================

function initializeRouteAssignment() {
    console.log('🚀 Initializing route assignment page...');

    let currentFacilityId = null;

    // Load drivers into dropdown
    async function loadDrivers() {
        console.log('📋 Loading drivers...');
        const result = await getDrivers();

        if (result.success) {
            const select = document.getElementById('driverSelect');
            if (!select) {
                console.error('❌ driverSelect element not found!');
                return;
            }

            select.innerHTML = '<option value="">-- Select driver --</option>';

            result.drivers.forEach(driver => {
                const option = document.createElement('option');
                option.value = driver.user_id;
                option.textContent = `${driver.username}${driver.full_name ? ' - ' + driver.full_name : ''}`;
                select.appendChild(option);
            });

            console.log(`✅ Loaded ${result.drivers.length} drivers`);
        } else {
            console.error('❌ Failed to load drivers:', result.error);
            alert('Failed to load drivers: ' + result.error);
        }
    }

    // Load facilities into dropdown
    async function loadFacilities() {
        console.log('📋 Loading facilities...');
        const result = await getFacilities();

        if (result.success) {
            const select = document.getElementById('facilitySelect');
            if (!select) {
                console.error('❌ facilitySelect element not found!');
                return;
            }

            select.innerHTML = '<option value="">-- Select facility --</option>';

            result.facilities.forEach(facility => {
                const option = document.createElement('option');
                option.value = facility.facility_id || facility.facilityId;
                option.textContent = facility.facility_name || facility.facilityName;
                select.appendChild(option);
            });

            console.log(`✅ Loaded ${result.facilities.length} facilities`);
        } else {
            console.error('❌ Failed to load facilities:', result.error);
            alert('Failed to load facilities: ' + result.error);
        }
    }

    // Load unassigned packages for selected facility
    async function loadUnassignedPackages(facilityId) {
        console.log('Loading packages for facility:', facilityId);
        const result = await getInventory(facilityId);

        if (result.success) {
            console.log(`Received ${result.inventory.length} total packages from API`);
            const tbody = document.getElementById('unassignedPackagesTable');

            // Filter for packages ready for delivery (created or at_facility status)
            // Exclude: in_transit, out_for_delivery, delivered, returned, lost
            const unassigned = result.inventory.filter(item => {
                const status = item.packageStatus;
                return status === 'at_facility' || status === 'created';
            });

            console.log(`Filtered to ${unassigned.length} unassigned packages (status: created or at_facility)`);

            if (unassigned.length === 0) {
                console.log('No packages found. Package statuses in inventory:',
                    result.inventory.map(p => p.packageStatus).join(', '));
                tbody.innerHTML = '<tr><td colspan="7" class="no-data">No unassigned packages at this facility.</td></tr>';
                return;
            }

            tbody.innerHTML = '';

            // Sort by delivery ZIP code for better route planning
            unassigned.sort((a, b) => {
                // Extract ZIP from deliveryAddress string (format: "street, city, state ZIP")
                const getZip = (addr) => {
                    if (!addr) return '';
                    const parts = addr.split(' ');
                    return parts[parts.length - 1] || '';
                };
                const zipA = getZip(a.deliveryAddress);
                const zipB = getZip(b.deliveryAddress);
                return zipA.localeCompare(zipB);
            });

            unassigned.forEach(pkg => {
                const row = document.createElement('tr');
                // Extract city and ZIP from deliveryAddress (format: "street, city, state ZIP")
                const deliveryParts = (pkg.deliveryAddress || 'N/A').split(', ');
                const street = deliveryParts[0] || 'N/A';
                const cityStateParts = deliveryParts.slice(1).join(', ').split(' ');
                const zip = cityStateParts[cityStateParts.length - 1] || 'N/A';
                const state = cityStateParts[cityStateParts.length - 2] || '';
                const city = cityStateParts.slice(0, -2).join(' ') || 'N/A';

                row.innerHTML = `
                    <td><input type="checkbox" class="package-select" data-package-id="${pkg.packageId}"></td>
                    <td>${pkg.trackingNumber}</td>
                    <td>${street}</td>
                    <td>${city}</td>
                    <td>${zip}</td>
                    <td>${pkg.weightKg} kg</td>
                    <td>${pkg.packageStatus}</td>
                `;
                tbody.appendChild(row);
            });

            console.log(`Loaded ${unassigned.length} unassigned packages`);
        } else {
            console.error('Failed to load packages:', result.error);
            const tbody = document.getElementById('unassignedPackagesTable');
            tbody.innerHTML = '<tr><td colspan="7" class="no-data">Error loading packages.</td></tr>';
        }
    }

    // Facility selection change handler
    const facilitySelect = document.getElementById('facilitySelect');
    if (facilitySelect) {
        facilitySelect.addEventListener('change', (e) => {
            currentFacilityId = e.target.value;
            if (currentFacilityId) {
                loadUnassignedPackages(currentFacilityId);
            } else {
                const tbody = document.getElementById('unassignedPackagesTable');
                tbody.innerHTML = '<tr><td colspan="7" class="no-data">Please select a facility.</td></tr>';
            }
        });
    }

    // Handle form submission
    const form = document.getElementById('assignRouteForm');
    if (form) {
        let isSubmitting = false;

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            // Check and set isSubmitting FIRST to prevent any race conditions
            if (isSubmitting) {
                console.log('Submission already in progress, ignoring duplicate submit');
                return;
            }
            isSubmitting = true;

            // Disable submit button immediately
            const submitBtn = form.querySelector('button[type="submit"]');
            const originalBtnText = submitBtn ? submitBtn.textContent : '';
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Assigning Route...';
            }

            const driverId = document.getElementById('driverSelect').value;
            const facilityId = document.getElementById('facilitySelect').value;

            if (!driverId || !facilityId) {
                alert('Please select a driver and a facility');
                isSubmitting = false;
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = originalBtnText;
                }
                return;
            }

            // Get selected packages
            const selectedCheckboxes = document.querySelectorAll('.package-select:checked');
            console.log('DEBUG: Found', selectedCheckboxes.length, 'checked checkboxes');

            const packageIds = Array.from(selectedCheckboxes).map(cb => {
                const id = cb.dataset.packageId;
                console.log('DEBUG: Collecting package ID:', id);
                return id;
            });

            console.log('DEBUG: Total packageIds collected:', packageIds);

            if (packageIds.length === 0) {
                alert('Please select at least one package to assign to this route');
                isSubmitting = false;
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = originalBtnText;
                }
                return;
            }

            console.log('DEBUG: Will send packageIds as comma-separated string:', packageIds.join(','));

            // Auto-generate route name
            const routeName = `Route-${new Date().toISOString().split('T')[0]}-${Date.now()}`;

            // Prepare payload for backend
            const payload = {
                driverId: parseInt(driverId),
                facilityId: parseInt(facilityId),
                routeName: routeName,
                routeDate: new Date().toISOString().split('T')[0],
                estimatedDurationMinutes: 60 + (packageIds.length * 15),
                vehicleId: 'VEH-001',
                packageIds: packageIds.join(',')
            };

            console.log('Submitting route assignment:', payload);

            const result = await assignRoutes(payload);

            if (result.success) {
                alert(`Route assigned successfully!\n\n${packageIds.length} packages assigned to driver.`);
                // Reset form and reload packages
                form.reset();
                currentFacilityId = null;
                const tbody = document.getElementById('unassignedPackagesTable');
                tbody.innerHTML = '<tr><td colspan="7" class="no-data">Please select a facility.</td></tr>';
            } else {
                alert(`Failed to assign route: ${result.error}`);
            }

            // Re-enable submit button
            isSubmitting = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = originalBtnText;
            }
        });
    }

    // Initialize - load data
    loadDrivers();
    loadFacilities();
}

// =============================================================================
// AUTO-INITIALIZE
// =============================================================================

console.log('🔍 Checking for assignRouteForm element...');
console.log('🔍 Document readyState:', document.readyState);

// Check if the form exists, if so, initialize
if (document.getElementById('assignRouteForm')) {
    console.log('✅ assignRouteForm found! Initializing...');
    initializeRouteAssignment();
} else {
    console.log('⚠️ assignRouteForm not found. Waiting for DOM...');

    // Fallback: Try after DOM loads
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            console.log('🔄 DOMContentLoaded fired');
            if (document.getElementById('assignRouteForm')) {
                initializeRouteAssignment();
            }
        });
    } else {
        // DOM already loaded but form not found - this page doesn't have the form
        console.log('ℹ️ This page does not have route assignment form');
    }
}
