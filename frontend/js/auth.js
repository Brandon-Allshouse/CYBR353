// Auth.js - Authentication Handler
class AuthHandler {
    constructor() {
        this.form = document.getElementById('loginForm');
        this.usernameInput = document.getElementById('username');
        this.passwordInput = document.getElementById('password');
        this.loginBtn = document.getElementById('loginBtn');
        this.errorDisplay = document.getElementById('errorDisplay');

        if (!this.form) {
            console.error('Login form not found');
            return;
        }

        this.init();
    }

    init() {
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));

        if (this.usernameInput) {
            this.usernameInput.addEventListener('input', () => this.clearFieldError('username'));
        }
        if (this.passwordInput) {
            this.passwordInput.addEventListener('input', () => this.clearFieldError('password'));
        }
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
            const response = await fetch('http://localhost:8081/api/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(credentials)
            });

            const data = await response.json();

            if (response.ok) {
                // Store user info in session
                this.storeUserSession(data);

                // Prefer role-based redirect (server returns role). Use SPA router when available.
                const role = (data.role || data.clearanceLevel || '').toString().toLowerCase();
                this.storeUserSession(Object.assign({}, data, { role }));
                setTimeout(() => {
                    // map role -> route prefix (manager -> management)
                    const prefix = (role === 'manager') ? 'management' : role;
                    const target = `/${prefix}/dashboard`;
                    if (window.appRouter) {
                        window.appRouter.navigate(target);
                    } else {
                        // fallback to direct navigation to the role's dashboard HTML
                        const fallback = `${prefix}/${prefix}-dashboard.html`;
                        window.location.href = fallback;
                    }
                }, 300);
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

        if (input) input.classList.add('error');
        if (errorElement) {
            errorElement.textContent = message;
            errorElement.classList.add('show');
        }
    }

    clearFieldError(field) {
        const input = document.getElementById(field);
        const errorElement = document.getElementById(`${field}Error`);

        if (input) input.classList.remove('error');
        if (errorElement) errorElement.classList.remove('show');
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
        const dashboards = {
            'customer': 'Templates/Dashboard.html',
            'driver': 'Templates/Dashboard.html',
            'manager': 'Templates/Dashboard.html',
            'admin': 'Templates/Dashboard.html'
        };

        const normalizedRole = role.toLowerCase();
        const dashboardRoute = dashboards[normalizedRole] || dashboards['customer'];

        setTimeout(() => {
            if (window.appRouter) {
                window.appRouter.navigate(dashboardRoute);
            } else {
                console.error('Router not available, falling back to direct navigation');
                window.location.href = dashboardRoute;
            }
        }, 500);
    }
}

// Initialize auth handler when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => new AuthHandler());
} else {
    new AuthHandler();

}

