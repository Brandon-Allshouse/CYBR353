// orders.js - frontend interactions for placing orders

async function placeOrder(event) {
    // TODO: implement frontend call to POST /orders
    if (event) event.preventDefault();

    alert('Placing order...');
    const trackingNumber = document.getElementById('tracking').value;
    const weight = parseFloat(document.getElementById('weight').value);
    const length = parseFloat(document.getElementById('length').value);
    const width = parseFloat(document.getElementById('width').value);
    const height = parseFloat(document.getElementById('height').value);

    if (!trackingNumber || isNaN(weight) || isNaN(length) || isNaN(width) || isNaN(height)) {
        alert("Please fill out all fields correctly.");
        return;
    }

    const orderData = {
            trackingNumber: trackingNumber,
            weight: weight,
            length: length,
            width: width,
            height: height
        };
     try {
            const response = await fetch('http://localhost:8081/api/order/place/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(orderData)
            });

            const data = await response.json();

            if (response.ok) {
                alert('Package created successfully!');
                addPackageToTable()
            } else {
                alert('Failed to create package. Please try again.');
            }
        } catch (error) {
            alert(error);
            console.error('Login error:', error);
        }
    console.log('placeOrder stub', orderData);
}

function addPackageToTable(pkg) {
    const table = document.getElementById("returnablePackagesTable");

    // Clear “No packages” message
    if (table.children.length === 1 &&
        table.children[0].querySelector(".no-data")) {
        table.innerHTML = "";
    }


    const row = document.createElement("tr");

    row.innerHTML = `
        <td>
            ${document.getElementById('tracking').value}
        </td>
        <td>
            ${parseFloat(document.getElementById('weight').value)}
        </td>
        <td>
            ${parseFloat(document.getElementById('length').value)}
        </td>
        <td>
            ${parseFloat(document.getElementById('width').value)}
        </td>
        <td>
            ${parseFloat(document.getElementById('height').value)}
        </td>
        <td>
            Created
        </td>
    `;

    table.appendChild(row);
}



/*export async function getOrderStatus(orderId) {
    // TODO: implement frontend call to GET /orders/{id}
    const orderData = {
        orderId: orderId
    };
    try {
        const response = await fetch('http://localhost:8081/api/order/get/', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(orderData)
        });

        const data = await response.json();

        if (response.ok) {
            alert('Order status retrieved successfully!');
        } else {
            alert('Failed to retrieve order status. Please try again.');
        }
    } catch (error) {
        alert(error);
        console.error('Error retrieving order status:', error);
    }
    console.log('getOrderStatus stub', trackingNumber);
}*/


document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("placeForm");

    if (form) {
        form.addEventListener("submit", placeOrder);
    }
});