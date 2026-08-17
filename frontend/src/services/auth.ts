import api from './api';
import { LoginResponse, User } from '../types';

// ===== ADD: Response types =====
interface RegisterResponse {
    message: string;
    userId: string;
    username: string;
    email: string;
}

interface VerifyEmailResponse {
    success: boolean;
    message: string;
}

interface ResendVerificationResponse {
    success: boolean;
    message: string;
}

interface RefreshTokenResponse {
    accessToken: string;
}

export const authService = {
    async login(username: string, password: string): Promise<LoginResponse> {
        try {
            const response = await api.post('/auth/login', { username, password });
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },

    async verifyMfa(userId: string, totpCode: string): Promise<LoginResponse> {
        try {
            const response = await api.post('/auth/verify-mfa', null, {
                params: { userId, totpCode },
            });
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },

    async register(data: {
        username: string;
        email: string;
        password: string;
        firstName: string;
        lastName: string;
        phone: string;
    }): Promise<RegisterResponse> {
        try {
            const response = await api.post('/auth/register', data);
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },

    async verifyEmail(token: string): Promise<VerifyEmailResponse> {
        try {
            const response = await api.post('/auth/verify-email', null, {
                params: { token },
            });
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },

    async resendVerification(email: string): Promise<ResendVerificationResponse> {
        try {
            const response = await api.post('/auth/resend-verification', null, {
                params: { email },
            });
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },

    async refreshToken(refreshToken: string): Promise<RefreshTokenResponse> {
        try {
            const response = await api.post('/auth/refresh', null, {
                params: { refreshToken },
            });
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },

    async logout(): Promise<void> {
        try {
            const refreshToken = localStorage.getItem('refreshToken');
            if (refreshToken) {
                await api.post('/auth/logout', null, { params: { refreshToken } });
            }
        } catch (error: any) {
            // Silent fail - just clear local storage
        } finally {
            localStorage.clear();
            sessionStorage.clear();
        }
    },

    isAuthenticated(): boolean {
        const token = localStorage.getItem('accessToken');
        return !!token;
    },

    getCurrentUser(): User | null {
        try {
            const authStorage = localStorage.getItem('auth-storage');
            if (authStorage) {
                const parsed = JSON.parse(authStorage);
                return parsed.state?.user || null;
            }
            return null;
        } catch (error) {
            return null;
        }
    },

    getAccessToken(): string | null {
        return localStorage.getItem('accessToken');
    },

    getRefreshToken(): string | null {
        return localStorage.getItem('refreshToken');
    },

    isMfaRequired(response: LoginResponse): boolean {
        return (response.requiresMfa === true || response.mfaSetupRequired === true) && !!response.userId;
    }
};