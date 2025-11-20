class AdminDashboard {
    constructor() {
        this.baseUrl = 'http://localhost:8081';
        this.init();
    }

    init() {
        const token = this.getSessionToken();
        if (!token) {
            this.showAlert('No active session. Redirecting to login...', 'error');
            setTimeout(() => window.location.href = '../login.html', 2000);
            return;
        }

        this.loadUsers();
        this.loadLogs();

        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.action-menu')) {
                document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show'));
            }
        });
    }

    getSessionToken() {
        const user = sessionStorage.getItem('user');
        if (!user) return null;
        try {
            return JSON.parse(user).token;
        } catch {
            return null;
        }
    }

    async makeRequest(url, options = {}) {
        const token = this.getSessionToken();
        if (!token) {
            this.showAlert('Session expired. Please log in again.', 'error');
            setTimeout(() => window.location.href = '../login.html', 2000);
            return null;
        }

        const headers = {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
            ...options.headers
        };

        try {
            const response = await fetch(url, { ...options, headers, credentials: 'include' });
            if (response.status === 401 || response.status === 403) {
                this.showAlert('Access denied. Insufficient permissions.', 'error');
                setTimeout(() => {
                    sessionStorage.clear();
                    window.location.href = '../login.html';
                }, 2000);
                return null;
            }
            return response;
        } catch (error) {
            this.showAlert('Unable to connect to server.', 'error');
            return null;
        }
    }

    async loadUsers() {
        const container = document.getElementById('usersContainer');
        const response = await this.makeRequest(`${this.baseUrl}/admin/users`);

        if (!response || !response.ok) {
            container.innerHTML = '<p class="loading">Failed to load users</p>';
            return;
        }

        const data = await response.json();
        this.renderUsers(data.users);
    }

    renderUsers(users) {
        const container = document.getElementById('usersContainer');

        if (!users || users.length === 0) {
            container.innerHTML = '<p class="loading">No users found</p>';
            return;
        }

        const table = document.createElement('table');
        table.innerHTML = `
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Username</th>
                    <th>Email</th>
                    <th>Full Name</th>
                    <th>Role</th>
                    <th>Clearance</th>
                    <th>Status</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody></tbody>
        `;

        const tbody = table.querySelector('tbody');
        users.forEach(user => {
            const row = document.createElement('tr');
            const isAdmin = user.role === 'admin';

            row.innerHTML = `
                <td>${user.user_id}</td>
                <td><strong>${this.escape(user.username)}</strong></td>
                <td>${user.email || 'N/A'}</td>
                <td>${user.full_name || 'N/A'}</td>
                <td><span class="badge badge-${user.role}">${user.role}</span></td>
                <td>${user.clearance_level}</td>
                <td><span class="badge badge-${user.account_status}">${user.account_status}</span></td>
                <td>
                    ${isAdmin ?
                        '<span class="protected">Protected</span>' :
                        `<div class="action-menu">
                            <button class="manage-btn" data-user-id="${user.user_id}">Manage ▼</button>
                            <div class="dropdown">
                                <div class="dropdown-header">Change Role</div>
                                <div class="dropdown-item" data-action="role" data-user="${user.user_id}" data-value="customer">Customer (Clearance 0)</div>
                                <div class="dropdown-item" data-action="role" data-user="${user.user_id}" data-value="driver">Driver (Clearance 1)</div>
                                <div class="dropdown-item" data-action="role" data-user="${user.user_id}" data-value="manager">Manager (Clearance 2)</div>
                                <div class="dropdown-header">Change Status</div>
                                <div class="dropdown-item" data-action="status" data-user="${user.user_id}" data-value="active">✓ Activate</div>
                                <div class="dropdown-item" data-action="status" data-user="${user.user_id}" data-value="suspended">⏸ Suspend</div>
                                <div class="dropdown-item" data-action="status" data-user="${user.user_id}" data-value="revoked">✕ Revoke</div>
                            </div>
                        </div>`
                    }
                </td>
            `;
            tbody.appendChild(row);
        });

        container.innerHTML = '';
        container.appendChild(table);
        this.attachHandlers();
    }

    attachHandlers() {
        // Manage button clicks
        document.querySelectorAll('.manage-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const dropdown = btn.nextElementSibling;
                const wasShown = dropdown.classList.contains('show');

                // Close all dropdowns
                document.querySelectorAll('.dropdown').forEach(d => d.classList.remove('show'));

                // Toggle this one
                if (!wasShown) {
                    dropdown.classList.add('show');
                }
            });
        });

        // Dropdown item clicks
        document.querySelectorAll('.dropdown-item').forEach(item => {
            item.addEventListener('click', async (e) => {
                const action = item.dataset.action;
                const userId = item.dataset.user;
                const value = item.dataset.value;

                if (action === 'role') {
                    await this.changeRole(userId, value);
                } else if (action === 'status') {
                    await this.changeStatus(userId, value);
                }

                // Close dropdown
                item.closest('.dropdown').classList.remove('show');
            });
        });
    }

    async changeRole(userId, role) {
        const response = await this.makeRequest(
            `${this.baseUrl}/admin/users/${userId}/role`,
            {
                method: 'PUT',
                body: JSON.stringify({ role })
            }
        );

        if (response && response.ok) {
            this.showAlert(`User role updated to ${role}`, 'success');
            this.loadUsers();
            this.loadLogs();
        } else {
            this.showAlert('Failed to update role', 'error');
        }
    }

    async changeStatus(userId, status) {
        const response = await this.makeRequest(
            `${this.baseUrl}/admin/users/${userId}/status`,
            {
                method: 'PUT',
                body: JSON.stringify({ status })
            }
        );

        if (response && response.ok) {
            this.showAlert(`Account status updated to ${status}`, 'success');
            this.loadUsers();
            this.loadLogs();
        } else {
            this.showAlert('Failed to update status', 'error');
        }
    }

    async loadLogs() {
        const container = document.getElementById('logsContainer');
        const response = await this.makeRequest(`${this.baseUrl}/admin/logs?limit=50`);

        if (!response || !response.ok) {
            container.innerHTML = '<p class="loading">Failed to load logs</p>';
            return;
        }

        const data = await response.json();
        this.renderLogs(data.logs);
    }

    renderLogs(logs) {
        const container = document.getElementById('logsContainer');

        if (!logs || logs.length === 0) {
            container.innerHTML = '<p class="loading">No logs found</p>';
            return;
        }

        const table = document.createElement('table');
        table.innerHTML = `
            <thead>
                <tr>
                    <th>Timestamp</th>
                    <th>Username</th>
                    <th>Action</th>
                    <th>Result</th>
                    <th>IP Address</th>
                    <th>Details</th>
                </tr>
            </thead>
            <tbody></tbody>
        `;

        const tbody = table.querySelector('tbody');
        logs.forEach(log => {
            const row = document.createElement('tr');
            const timestamp = new Date(log.timestamp).toLocaleString();

            row.innerHTML = `
                <td style="font-size: 12px;">${timestamp}</td>
                <td><strong>${this.escape(log.username)}</strong></td>
                <td>${this.escape(log.action)}</td>
                <td><span class="badge badge-${log.result}">${log.result}</span></td>
                <td style="font-family: monospace; font-size: 12px;">${log.ip_address || 'N/A'}</td>
                <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${log.details || ''}">${log.details || 'N/A'}</td>
            `;
            tbody.appendChild(row);
        });

        container.innerHTML = '';
        container.appendChild(table);
    }

    showAlert(message, type = 'success') {
        const container = document.getElementById('alertContainer');
        const alert = document.createElement('div');
        alert.className = `alert alert-${type}`;
        alert.textContent = message;

        container.innerHTML = '';
        container.appendChild(alert);

        setTimeout(() => alert.remove(), 5000);
    }

    escape(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

let dashboard;

function refreshLogs() {
    if (dashboard) dashboard.loadLogs();
}

function logout() {
    sessionStorage.clear();
    window.location.href = '/login.html';
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        dashboard = new AdminDashboard();
    });
} else {
    dashboard = new AdminDashboard();
}
