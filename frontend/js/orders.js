// orders.js - frontend interactions for placing orders

export async function placeOrder(trackingNumber, reason) {
    // TODO: implement frontend call to POST /orders
    const orderData = {
            trackingNumber: trackingNumber,
            reason: reason
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
                alert('Return request successful!');
                addReturnablePackage(trackingNumber, "", "", "");
            } else {
                alert('Failed to request return. Please try again.');
            }
        } catch (error) {
            alert(error);
            console.error('Login error:', error);
        }
    console.log('placeOrder stub', orderData);
}

function addReturnablePackage(tracking, recipient, deliveredDate, status) {
    const tbody = document.getElementById("returnablePackagesTable");

    // Remove "No eligible returns." row if it exists
    const noDataRow = tbody.querySelector(".no-data");
    if (noDataRow) {
        noDataRow.remove();
    }

    // Create new row
    const row = document.createElement("tr");
    row.innerHTML = `
        <td>${tracking}</td>
        <td>${recipient}</td>
        <td>${deliveredDate}</td>
        <td>${status}</td>
        <td><input type="checkbox" class="row-select"></td>
    `;

    tbody.appendChild(row);
}


export async function getOrderStatus(trackingNumber) {
    // TODO: implement frontend call to GET /orders/{id}
    const orderData = {
        trackingNumber: trackingNumber
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
}
