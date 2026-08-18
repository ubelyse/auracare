import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../stores/authStore';

// ===== ADD: Custom config type with retry flag =====
interface CustomConfig extends InternalAxiosRequestConfig {
    _retry?: boolean;
}

// ===== ADD: Security headers configuration =====
const api = axios.create({
    // This line is the magic key for environment variables
    baseURL: import.meta.env.VITE_API_URL || '/api',
    timeout: 30000, // 30 seconds timeout
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'X-XSS-Protection': '1; mode=block',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'geolocation=(), microphone=(), camera=()'
    },
});

// ===== Helper: Get token from localStorage =====
const getToken = (): string | null => {
    // First check direct accessToken
    let token = localStorage.getItem('accessToken');

    // If not found, check auth-storage
    if (!token) {
        try {
            const authStorage = localStorage.getItem('auth-storage');
            if (authStorage) {
                const parsed = JSON.parse(authStorage);
                if (parsed.state?.accessToken) {
                    token = parsed.state.accessToken;
                }
            }
        } catch (e) {
            // Silent fail - no need to log in production
        }
    }

    return token;
};

// ===== Helper: Get refresh token =====
const getRefreshToken = (): string | null => {
    let refreshToken = localStorage.getItem('refreshToken');

    if (!refreshToken) {
        try {
            const authStorage = localStorage.getItem('auth-storage');
            if (authStorage) {
                const parsed = JSON.parse(authStorage);
                if (parsed.state?.refreshToken) {
                    refreshToken = parsed.state.refreshToken;
                }
            }
        } catch (e) {
            // Silent fail
        }
    }

    return refreshToken;
};

// ===== Helper: Validate UUID format =====
const isValidUUID = (uuid: string): boolean => {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return uuidRegex.test(uuid);
};

// ===== REQUEST INTERCEPTOR - Add token and security headers =====
api.interceptors.request.use(
    (config) => {
        const token = getToken();

        // Add authorization token if available
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        // Add CSRF protection (if needed)
        const csrfToken = localStorage.getItem('csrfToken');
        if (csrfToken) {
            config.headers['X-CSRF-Token'] = csrfToken;
        }

        // Add request ID for tracing (optional)
        config.headers['X-Request-ID'] = crypto.randomUUID?.() ||
            Math.random().toString(36).substring(2, 15);

        // Ensure HTTPS in production
        if (process.env.NODE_ENV === 'production' && config.url?.startsWith('http://')) {
            config.url = config.url.replace('http://', 'https://');
        }

        // Log non-sensitive request info for debugging
        if (process.env.NODE_ENV === 'development') {
            console.log(`🚀 ${config.method?.toUpperCase()} ${config.url}`, {
                headers: { ...config.headers, Authorization: 'Bearer [REDACTED]' }
            });
        }

        return config;
    },
    (error) => {
        console.error('Request interceptor error:', error);
        return Promise.reject(error);
    }
);

// ===== RESPONSE INTERCEPTOR - Handle token refresh and errors =====
api.interceptors.response.use(
    (response) => {
        // Log non-sensitive response info for debugging
        if (process.env.NODE_ENV === 'development') {
            console.log(`✅ ${response.config.method?.toUpperCase()} ${response.config.url}`, {
                status: response.status,
                dataSize: JSON.stringify(response.data).length
            });
        }
        return response;
    },
    async (error: AxiosError) => {
        const originalRequest = error.config as CustomConfig;

        // Handle network errors
        if (!error.response) {
            console.error('Network error - no response received:', error.message);
            return Promise.reject({
                ...error,
                message: 'Network error. Please check your connection.'
            });
        }

        // Handle timeout errors
        if (error.code === 'ECONNABORTED') {
            console.error('Request timeout:', error.config?.url);
            return Promise.reject({
                ...error,
                message: 'Request timeout. Please try again.'
            });
        }

        // ===== TOKEN REFRESH LOGIC =====
        // If it's a 401 and we haven't retried yet
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;

            try {
                const refreshToken = getRefreshToken();

                if (!refreshToken) {
                    throw new Error('No refresh token available');
                }

                // Call refresh endpoint
                const response = await api.post('/auth/refresh', null, {
                    params: { refreshToken }
                });

                const { accessToken, refreshToken: newRefreshToken } = response.data;

                // Store the new tokens
                if (accessToken) {
                    localStorage.setItem('accessToken', accessToken);
                }

                if (newRefreshToken) {
                    localStorage.setItem('refreshToken', newRefreshToken);
                }

                // Update the auth store
                try {
                    const { setTokens } = useAuthStore.getState();
                    if (setTokens) {
                        setTokens(accessToken, newRefreshToken || refreshToken);
                    }
                } catch (storeError) {
                    console.warn('Could not update auth store:', storeError);
                }

                // Retry the original request with new token
                originalRequest.headers.Authorization = `Bearer ${accessToken}`;
                return api(originalRequest);

            } catch (refreshError) {
                // Refresh failed - logout and redirect to login
                console.error('Token refresh failed:', refreshError);

                try {
                    const { logout } = useAuthStore.getState();
                    if (logout) {
                        logout();
                    } else {
                        // Fallback: clear storage
                        localStorage.removeItem('accessToken');
                        localStorage.removeItem('refreshToken');
                        localStorage.removeItem('auth-storage');
                    }
                } catch (storeError) {
                    // Store might not be initialized - clear everything
                    localStorage.clear();
                }

                // Redirect to login
                const currentPath = window.location.pathname;
                const loginUrl = `/login?redirect=${encodeURIComponent(currentPath)}`;
                window.location.href = loginUrl;

                return Promise.reject(refreshError);
            }
        }

        // ===== HANDLE SPECIFIC ERROR STATUS CODES =====
        if (error.response?.status === 403) {
            console.warn('Forbidden access attempt:', error.config?.url);
            // Could redirect to unauthorized page or show notification
        }

        if (error.response?.status === 429) {
            console.warn('Rate limit exceeded:', error.config?.url);
            // Could implement retry with backoff
        }

        if (error.response?.status === 500) {
            console.error('Server error:', {
                url: error.config?.url,
                method: error.config?.method,
                status: error.response.status
            });
        }

        // ===== ADD MORE DETAILED ERROR MESSAGE =====
        const errorData = error.response?.data as any;
        let errorMessage = 'An unexpected error occurred';

        if (errorData?.message) {
            errorMessage = errorData.message;
        } else if (errorData?.error) {
            errorMessage = errorData.error;
        } else if (typeof errorData === 'string') {
            errorMessage = errorData;
        }

        // Create a custom error object with more context
        const enhancedError = {
            ...error,
            userMessage: errorMessage,
            status: error.response?.status,
            url: error.config?.url,
            method: error.config?.method
        };

        return Promise.reject(enhancedError);
    }
);

// ===== HELPER: Add CSRF protection (call on login) =====
export const setCSRFToken = (token: string) => {
    localStorage.setItem('csrfToken', token);
};

// ===== HELPER: Clear security tokens (call on logout) =====
export const clearSecurityTokens = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('csrfToken');
    localStorage.removeItem('auth-storage');
};

// ===== HELPER: Setup with authentication =====
export const setupAuth = (accessToken: string, refreshToken: string) => {
    localStorage.setItem('accessToken', accessToken);
    if (refreshToken) {
        localStorage.setItem('refreshToken', refreshToken);
    }
};

export default api;