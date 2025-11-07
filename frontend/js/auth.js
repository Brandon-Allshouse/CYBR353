// Auth.js - Authentication Handler
class AuthHandler {
    constructor() {
        this.form = document.getElementById('loginForm');
        this.usernameInput = document.getElementById('username');
        this.passwordInput = document.getElementById('password');
        this.loginBtn = document.getElementById('loginBtn');
        this.errorDisplay = document.getElementById('errorDisplay');
        
        this.init();
    }

    init() {
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));
        
        // Clear errors on input
        this.usernameInput.addEventListener('input', () => this.clearFieldError('username'));
        this.passwordInput.addEventListener('input', () => this.clearFieldError('password'));
    }

    async handleSubmit(e) {
        e.preventDefault();
        
        // Clear previous errors
        this.clearAllErrors();
        
        // Validate form
        if (!this.validateForm()) {
            return;
        }

        // Get form data
        const credentials = {
            username: this.usernameInput.value.trim(),
            password: this.passwordInput.value
        };

        // Show loading state
        this.setLoading(true);

        try {
            // Send login request to backend server
            const response = await fetch('http://localhost:8081/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(credentials)
            });

            const data = await response.json();

            if (response.ok) {
                // Store user info in session
                this.storeUserSession(data);
                
                // Redirect based on role
                this.redirectUser(data.role || data.clearanceLevel);
            } else {
                // Show error message
                this.showError(data.message || 'Invalid username or password');
            }
        } catch (error) {
            console.error('Login error:', error);
            this.showError('Unable to connect to server. Please try again.');
        } finally {
            this.setLoading(false);
        }
    }

    validateForm() {
        let isValid = true;
        
        // Validate username
        const username = this.usernameInput.value.trim();
        if (!username) {
            this.showFieldError('username', 'Username is required');
            isValid = false;
        } else if (username.length < 3) {
            this.showFieldError('username', 'Username must be at least 3 characters');
            isValid = false;
        }

        // Validate password
        const password = this.passwordInput.value;
        if (!password) {
            this.showFieldError('password', 'Password is required');
            isValid = false;
        } else if (password.length < 6) {
            this.showFieldError('password', 'Password must be at least 6 characters');
            isValid = false;
        }

        return isValid;
    }

    showFieldError(field, message) {
        const input = document.getElementById(field);
        const errorElement = document.getElementById(`${field}Error`);
        
        input.classList.add('error');
        errorElement.textContent = message;
        errorElement.classList.add('show');
    }

    clearFieldError(field) {
        const input = document.getElementById(field);
        const errorElement = document.getElementById(`${field}Error`);
        
        input.classList.remove('error');
        errorElement.classList.remove('show');
    }

    clearAllErrors() {
        this.errorDisplay.classList.remove('show');
        this.clearFieldError('username');
        this.clearFieldError('password');
    }

    showError(message) {
        this.errorDisplay.textContent = message;
        this.errorDisplay.classList.add('show');
    }

    setLoading(isLoading) {
        this.loginBtn.disabled = isLoading;
        if (isLoading) {
            this.loginBtn.classList.add('loading');
        } else {
            this.loginBtn.classList.remove('loading');
        }
    }

    storeUserSession(userData) {
        // Store user data in sessionStorage
        sessionStorage.setItem('user', JSON.stringify({
            username: userData.username,
            role: userData.role || userData.clearanceLevel,
            clearanceLevel: userData.clearanceLevel,
            token: userData.token,
            loginTime: new Date().toISOString()
        }));
    }

    redirectUser(role) {
        // Define dashboard routes based on role
        const dashboards = {
            'customer': '../frontend/customer/customer-dashboard.html',
            'driver': '../frontend/driver/driver-dashboard.html',
            'manager': '../frontend/management/management-dashboard.html',
            'admin': '../frontend/admin/admin-dashboard.html'
        };

        // Normalize role (handle different case formats)
        const normalizedRole = role.toLowerCase();
        
        // Get appropriate dashboard or default to customer
        const dashboardUrl = dashboards[normalizedRole] || dashboards['customer'];
        
        // Redirect with slight delay for UX
        setTimeout(() => {
            window.location.href = dashboardUrl;
        }, 500);
    }
}

// Initialize auth handler when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => new AuthHandler());
} else {
    new AuthHandler();

}

