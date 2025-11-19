// register.js - Customer Registration Handler
class RegistrationHandler {
    constructor() {
        this.form = document.getElementById('registerForm');
        this.nameInput = document.getElementById('name');
        this.emailInput = document.getElementById('email');
        this.phoneInput = document.getElementById('phone');
        this.passwordInput = document.getElementById('password');
        this.confirmPasswordInput = document.getElementById('confirmPassword');
        this.registerBtn = document.getElementById('registerBtn');
        this.errorDisplay = document.getElementById('errorDisplay');
        this.successDisplay = document.getElementById('successDisplay');

        if (!this.form) {
            console.error('Registration form not found');
            return;
        }

        this.init();
    }

    init() {
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));

        if (this.nameInput) {
            this.nameInput.addEventListener('input', () => this.clearFieldError('name'));
        }
        if (this.emailInput) {
            this.emailInput.addEventListener('input', () => this.clearFieldError('email'));
        }
        if (this.phoneInput) {
            this.phoneInput.addEventListener('input', () => this.clearFieldError('phone'));
        }
        if (this.passwordInput) {
            this.passwordInput.addEventListener('input', () => this.clearFieldError('password'));
        }
        if (this.confirmPasswordInput) {
            this.confirmPasswordInput.addEventListener('input', () => this.clearFieldError('confirmPassword'));
        }
    }

    async handleSubmit(e) {
        e.preventDefault();

        // Clear previous messages
        this.clearAllErrors();
        this.successDisplay.style.display = 'none';

        // Validate form
        if (!this.validateForm()) {
            return;
        }

        // Get reCAPTCHA token
        const recaptchaResponse = grecaptcha.getResponse();
        if (!recaptchaResponse) {
            this.showFieldError('recaptcha', 'Please complete the reCAPTCHA verification');
            return;
        }

        // Get form data
        const registrationData = {
            name: this.nameInput.value.trim(),
            email: this.emailInput.value.trim(),
            phone: this.phoneInput.value.trim(),
            password: this.passwordInput.value,
            recaptchaToken: recaptchaResponse
        };

        // Show loading state
        this.setLoading(true);

        try {
            // Send registration request to backend
            const response = await fetch('http://localhost:8081/api/customer/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(registrationData)
            });

            const data = await response.json();

            if (response.ok) {
                // Registration successful
                this.showSuccess(data.message || 'Account created successfully! Redirecting to login...');

                // Reset form
                this.form.reset();
                grecaptcha.reset();

                // Redirect to login after 2 seconds
                setTimeout(() => {
                    if (window.appRouter) {
                        window.appRouter.navigate('/login');
                    } else {
                        window.location.href = '/login';
                    }
                }, 2000);
            } else {
                // Registration failed
                this.showError(data.error || 'Registration failed. Please try again.');
                grecaptcha.reset();
            }
        } catch (error) {
            console.error('Registration error:', error);
            this.showError('Unable to connect to server. Please try again.');
            grecaptcha.reset();
        } finally {
            this.setLoading(false);
        }
    }

    validateForm() {
        let isValid = true;

        // Validate name
        const name = this.nameInput.value.trim();
        if (!name) {
            this.showFieldError('name', 'Name is required');
            isValid = false;
        } else if (name.length < 2) {
            this.showFieldError('name', 'Name must be at least 2 characters');
            isValid = false;
        } else if (!/^[a-zA-Z\s'-]+$/.test(name)) {
            this.showFieldError('name', 'Name contains invalid characters');
            isValid = false;
        }

        // Validate email
        const email = this.emailInput.value.trim();
        if (!email) {
            this.showFieldError('email', 'Email is required');
            isValid = false;
        } else if (!/^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email)) {
            this.showFieldError('email', 'Please enter a valid email address');
            isValid = false;
        }

        // Validate phone
        const phone = this.phoneInput.value.trim();
        if (!phone) {
            this.showFieldError('phone', 'Phone number is required');
            isValid = false;
        } else if (!/^[+]?[(]?[0-9]{3}[)]?[-\s.]?[0-9]{3}[-\s.]?[0-9]{4,6}$/.test(phone)) {
            this.showFieldError('phone', 'Please enter a valid phone number');
            isValid = false;
        }

        // Validate password
        const password = this.passwordInput.value;
        if (!password) {
            this.showFieldError('password', 'Password is required');
            isValid = false;
        } else if (password.length < 8) {
            this.showFieldError('password', 'Password must be at least 8 characters');
            isValid = false;
        } else if (!/[A-Z]/.test(password)) {
            this.showFieldError('password', 'Password must contain at least one uppercase letter');
            isValid = false;
        } else if (!/[a-z]/.test(password)) {
            this.showFieldError('password', 'Password must contain at least one lowercase letter');
            isValid = false;
        } else if (!/\d/.test(password)) {
            this.showFieldError('password', 'Password must contain at least one digit');
            isValid = false;
        } else if (!/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) {
            this.showFieldError('password', 'Password must contain at least one special character');
            isValid = false;
        }

        // Validate password confirmation
        const confirmPassword = this.confirmPasswordInput.value;
        if (!confirmPassword) {
            this.showFieldError('confirmPassword', 'Please confirm your password');
            isValid = false;
        } else if (password !== confirmPassword) {
            this.showFieldError('confirmPassword', 'Passwords do not match');
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
        this.clearFieldError('name');
        this.clearFieldError('email');
        this.clearFieldError('phone');
        this.clearFieldError('password');
        this.clearFieldError('confirmPassword');
        this.clearFieldError('recaptcha');
    }

    showError(message) {
        this.errorDisplay.textContent = message;
        this.errorDisplay.classList.add('show');
    }

    showSuccess(message) {
        this.successDisplay.textContent = message;
        this.successDisplay.style.display = 'block';
        this.successDisplay.classList.add('show');
    }

    setLoading(isLoading) {
        this.registerBtn.disabled = isLoading;
        if (isLoading) {
            this.registerBtn.classList.add('loading');
        } else {
            this.registerBtn.classList.remove('loading');
        }
    }
}

// Initialize registration handler when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => new RegistrationHandler());
} else {
    new RegistrationHandler();
}
