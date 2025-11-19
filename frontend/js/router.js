// router.js - Dynamic routing system for the delivery application
class Router {
    constructor() {
        // Calculate base path from initial page load (before any pushState)
        this.basePath = window.location.href.substring(0, window.location.href.lastIndexOf('/') + 1);

        this.routes = {
            // Customer routes
            '/customer/dashboard': 'customer/customer-dashboard.html',
            '/customer/info': 'customer/customer-info.html',
            '/customer/packages': 'customer/view-packages.html',
            '/customer/track': 'customer/track-packages.html',
            '/customer/edit': 'customer/edit-packages.html',
            '/customer/return': 'customer/return-packages.html',

            // Driver routes
            '/driver/dashboard': 'driver/driver-dashboard.html',
            '/driver/route': 'driver/view-route.html',
            '/driver/login': 'driver/driver-login.html',

            // Management routes
            '/management/dashboard': 'management/management-dashboard.html',
            '/management/assign-routes': 'management/assign-routes.html',
            '/management/transfer': 'management/transfer-portal.html',
            '/management/inventory': 'management/view-inventory.html',

            // Admin routes
            '/admin/dashboard': 'admin/admin-dashboard.html',
            '/admin/logs': 'admin/view-logs.html',

            // Auth routes
            '/login': 'login.html',
            '/register': 'register.html',
            '/logout': 'login.html'
        };

        this.init();
    }

    init() {
        // Handle navigation events
        window.addEventListener('popstate', () => this.handleRoute());

        // Intercept all anchor clicks for SPA-like navigation
        document.addEventListener('click', (e) => {
            if (e.target.matches('[data-route]')) {
                e.preventDefault();
                const path = e.target.getAttribute('data-route');
                this.navigate(path);
            }
        });
    }

    navigate(path, replace = false) {
        // Handle logout
        if (path === '/logout') {
            this.logout();
            return;
        }

        // Public routes don't need auth (login, register)
        const publicRoutes = ['/login', '/register'];
        const isPublic = publicRoutes.includes(path);

        // Check authentication for protected routes
        if (!isPublic && !this.isAuthenticated()) {
            this.navigate('/login');
            return;
        }

        // Check role-based access for protected routes
        if (!isPublic && !this.hasAccess(path)) {
            this.showAccessDenied();
            return;
        }

        // Update browser history
        if (replace) {
            window.history.replaceState({ path }, '', path);
        } else {
            window.history.pushState({ path }, '', path);
        }

        // Load the content
        this.handleRoute(path);
    }

    handleRoute(path = null) {
        // Get current path from URL or parameter
        const currentPath = path || window.location.pathname;

        // Get the file to load
        const file = this.routes[currentPath];

        if (file) {
            this.loadContent(file);
        } else {
            // Default to login or 404
            this.navigate('/login', true);
        }
    }

    async loadContent(file) {
        try {
            const absolutePath = this.basePath + file;

            const response = await fetch(absolutePath, { cache: 'no-store' });
            if (!response.ok) {
                throw new Error(`Failed to load ${file}`);
            }

            const html = await response.text();

            // Parse HTML and extract body content
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const content = doc.body.innerHTML;

            // Get stylesheets from new page
            const stylesheets = Array.from(doc.querySelectorAll('link[rel="stylesheet"]'))
                .map(link => link.href);


            // Get scripts from new page (exclude router.js since it's already loaded)
            const scripts = Array.from(doc.querySelectorAll('script'))
                .map(s => ({ src: s.src, content: s.textContent }))
                .filter(s => !(s.src && s.src.includes('router.js')));

            // Remove old page-specific stylesheets (keep global styles.css)
            Array.from(document.querySelectorAll('link[rel="stylesheet"]')).forEach(link => {
                if (!link.href.includes('main.css') ) {
                    link.remove();
                }
            });

            // Add new stylesheets
            stylesheets.forEach(href => {
                if (!document.querySelector(`link[href="${href}"]`)) {
                    const link = document.createElement('link');
                    link.rel = 'stylesheet';
                    link.href = href;
                    document.head.appendChild(link);
                }
            });

            // Update the main content area
            document.body.innerHTML = content;

            // Reload scripts for the new page with slight delay to ensure DOM is ready
            setTimeout(() => {
                scripts.forEach(scriptData => {
                    const script = document.createElement('script');
                    if (scriptData.src) {
                        script.src = scriptData.src;
                    } else if (scriptData.content) {
                        script.textContent = scriptData.content;
                    }
                    document.body.appendChild(script);
                });
            }, 10);

            // Re-initialize router listeners
            this.init();

        } catch (error) {
            console.error('Routing error:', error);
            this.showError('Failed to load page');
        }
    }

    isAuthenticated() {
        const user = sessionStorage.getItem('user');
        if (!user) return false;

        try {
            const userData = JSON.parse(user);
            return userData.token && userData.username;
        } catch {
            return false;
        }
    }

    hasAccess(path) {
        const user = this.getCurrentUser();
        if (!user) return false;

        const role = user.role;

        // Define access control
        if (path.startsWith('/customer/') && role !== 'customer') return false;
        if (path.startsWith('/driver/') && role !== 'driver') return false;
        if (path.startsWith('/management/') && role !== 'manager') return false;
        if (path.startsWith('/admin/') && role !== 'admin') return false;

        return true;
    }

    getCurrentUser() {
        const user = sessionStorage.getItem('user');
        if (!user) return null;

        try {
            return JSON.parse(user);
        } catch {
            return null;
        }
    }

    logout() {
        sessionStorage.clear();
        this.navigate('/login', true);
    }

    showAccessDenied() {
        alert('Access denied. You do not have permission to view this page.');
        const user = this.getCurrentUser();
        if (user) {
            this.navigate(`/${user.role}/dashboard`);
        } else {
            this.navigate('/login');
        }
    }

    showError(message) {
        console.error(message);
        alert(message);
    }
}

// Initialize router when DOM is ready and expose globally
window.appRouter = null;
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.appRouter = new Router();
    });
} else {
    window.appRouter = new Router();
}
