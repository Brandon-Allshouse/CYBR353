 // track.js – fixed version for SPA routing

async function getTracking(event) {
    // Prevent form submission + page reload if event exists
    if (event) event.preventDefault();

    const trackingInput = document.getElementById("trackingNumber");
    const trackingNumber = trackingInput ? trackingInput.value.trim() : "";

    if (!trackingNumber) {
        alert("Please enter a tracking number.");
        return;
    }

    try {
        const url = `http://localhost:8081/api/trackPackages?trackingNumber=${encodeURIComponent(trackingNumber)}`;

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            alert("Tracking request failed. Tracking number may not exist.");
            return;
        }

        const data = await response.json();

        // Validate expected structure
        if (!data || !data.package) {
            alert("Invalid tracking response from server.");
            return;
        }

        const tbody = document.getElementById("trackingHistoryTable");

        // If table exists, clear placeholder
        if (tbody) {
            if (tbody.querySelector(".no-data")) {
                tbody.innerHTML = "";
            }

            const now = new Date();

            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${now.toLocaleString()}</td>
                <td>${data.package.currentFacility || ""}</td>
                <td>${data.package.status || ""}</td>
                <td>${data.package.notes || ""}</td>
            `;

            tbody.appendChild(row);
        } else {
            console.error("trackingHistoryTable not found in DOM.");
        }

    } catch (err) {
        console.error("Error fetching tracking data:", err);
        alert("An error occurred while tracking the package.");
    }
}


// Attach event listener safely if router replaces DOM
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("trackForm");

    if (form) {
        form.addEventListener("submit", getTracking);
    }
});
