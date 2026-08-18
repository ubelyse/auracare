import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import toast from 'react-hot-toast';
import { authService } from '../services/auth';
import { useAuthStore } from '../stores/authStore';

// ===== ADD: Auth response types =====
interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    user: {
        id: string;
        firstName: string;
        lastName: string;
        email: string;
        role: string;
        username: string;
        facilityId?: string;
        facilityName?: string;
        departmentId?: string;
        departmentName?: string;
    };
}

// ===== UPDATE: Extend LoginResponse to include all possible fields =====
interface LoginResponse {
    mfaSetupRequired?: boolean;
    requiresMfa?: boolean;
    userId?: string;
    qrCodeUrl?: string;
    accessToken?: string;
    refreshToken?: string;
    user?: {
        id: string;
        firstName: string;
        lastName: string;
        email: string;
        role: string;
        username: string;
        facilityId?: string;
        facilityName?: string;
        departmentId?: string;
        departmentName?: string;
    };
}

const loginSchema = z.object({
    username: z.string().min(3, 'Username must be at least 3 characters'),
    password: z.string().min(6, 'Password must be at least 6 characters'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export const Login: React.FC = () => {
    const navigate = useNavigate();
    const [isLoading, setIsLoading] = useState(false);
    const [showMfa, setShowMfa] = useState(false);
    const [mfaUserId, setMfaUserId] = useState<string | null>(null);
    const [totpCode, setTotpCode] = useState('');
    const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
    const { setUser, setTokens } = useAuthStore();

    const { register, handleSubmit, formState: { errors } } = useForm<LoginFormData>({
        resolver: zodResolver(loginSchema),
    });

    const onSubmit = async (data: LoginFormData): Promise<void> => {
        setIsLoading(true);
        try {
            const response: LoginResponse = await authService.login(data.username, data.password);

            // First-time MFA enrollment — show QR code, no session yet.
            if (response.mfaSetupRequired && response.userId) {
                setMfaUserId(response.userId);
                setQrCodeUrl(response.qrCodeUrl || null);
                setShowMfa(true);
                toast.success('Scan the QR code to set up two-factor authentication');
                return;
            }

            // Returning login, MFA already enrolled — just need the code.
            if (response.requiresMfa && response.userId) {
                setMfaUserId(response.userId);
                setQrCodeUrl(null);
                setShowMfa(true);
                toast.success('Please enter your MFA code');
                return;
            }

            // ===== FIXED: Check if we have a full auth response =====
            if (response.accessToken && response.refreshToken && response.user) {
                handleLoginSuccess(response as AuthResponse);
            } else {
                toast.error('Invalid login response from server');
            }
        } catch (error: any) {
            toast.error(error.response?.data?.error || 'Login failed');
        } finally {
            setIsLoading(false);
        }
    };

    const handleMfaSubmit = async (): Promise<void> => {
        if (!mfaUserId || !totpCode) {
            toast.error('Please enter your MFA code');
            return;
        }

        setIsLoading(true);
        try {
            const response: LoginResponse = await authService.verifyMfa(mfaUserId, totpCode);

            // ===== FIXED: Check if we have a full auth response =====
            if (response.accessToken && response.refreshToken && response.user) {
                handleLoginSuccess(response as AuthResponse);
            } else {
                toast.error('Invalid MFA verification response');
            }
        } catch (error: any) {
            toast.error(error.response?.data?.error || 'MFA verification failed');
        } finally {
            setIsLoading(false);
        }
    };

    const handleLoginSuccess = (response: AuthResponse): void => {
        // Tokens are set by the auth store's setTokens method
        setTokens(response.accessToken, response.refreshToken);
        setUser(response.user);

        toast.success(`Welcome back, ${response.user.firstName}!`);

        // ===== UPDATED: Navigate based on role =====
        const roleRoutes: Record<string, string> = {
            'DISTRICT_ADMIN': '/admin/dashboard',
            'FACILITY_ADMIN': '/facility-admin/dashboard',
            'DOCTOR': '/doctor/dashboard',
            'STAFF': '/staff/dashboard',
            'PATIENT': '/patient/landing',  // ← CHANGED: Go to landing instead of dashboard
        };

        navigate(roleRoutes[response.user.role] || '/patient/landing');
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-green-50 via-blue-50 to-purple-50 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md w-full space-y-8">
                {/* Logo & Title */}
                <div className="text-center">
                    <div className="flex justify-center mb-4">
                        <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center shadow-lg transform hover:scale-105 transition-transform duration-200">
                            <span className="text-4xl">🏥</span>
                        </div>
                    </div>
                    <h1 className="text-4xl font-bold bg-gradient-to-r from-primary-600 to-primary-400 bg-clip-text text-transparent">
                        Aura Care
                    </h1>
                    <h2 className="mt-2 text-2xl font-extrabold text-gray-900">
                        Health Platform
                    </h2>
                    <p className="mt-2 text-sm text-gray-600">
                        Secure Patient Queue Management
                    </p>
                </div>

                {/* Card */}
                <div className="bg-white rounded-2xl shadow-xl p-8 border border-gray-100 backdrop-blur-sm">
                    {!showMfa ? (
                        <form className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
                            {/* Username Field */}
                            <div>
                                <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1">
                                    Username
                                </label>
                                <div className="relative">
                                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                        <span className="text-gray-400">👤</span>
                                    </div>
                                    <input
                                        {...register('username')}
                                        type="text"
                                        id="username"
                                        className="appearance-none block w-full pl-10 px-3 py-3 border border-gray-300 rounded-xl placeholder-gray-400 text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200 sm:text-sm"
                                        placeholder="Enter your username"
                                        disabled={isLoading}
                                    />
                                </div>
                                {errors.username && (
                                    <p className="mt-1 text-sm text-red-600">{errors.username.message}</p>
                                )}
                            </div>

                            {/* Password Field */}
                            <div>
                                <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                                    Password
                                </label>
                                <div className="relative">
                                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                        <span className="text-gray-400">🔒</span>
                                    </div>
                                    <input
                                        {...register('password')}
                                        type="password"
                                        id="password"
                                        className="appearance-none block w-full pl-10 px-3 py-3 border border-gray-300 rounded-xl placeholder-gray-400 text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200 sm:text-sm"
                                        placeholder="Enter your password"
                                        disabled={isLoading}
                                    />
                                </div>
                                {errors.password && (
                                    <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>
                                )}
                            </div>

                            {/* Submit Button */}
                            <button
                                type="submit"
                                disabled={isLoading}
                                className="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl text-sm font-medium text-white bg-gradient-to-r from-primary-600 to-primary-500 hover:from-primary-700 hover:to-primary-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 shadow-lg hover:shadow-xl"
                            >
                                {isLoading ? (
                                    <span className="flex items-center gap-2">
                                        <svg className="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                        </svg>
                                        Signing in...
                                    </span>
                                ) : (
                                    'Sign in'
                                )}
                            </button>

                            {/* Register Link */}
                            <div className="text-center">
                                <span className="text-sm text-gray-600">Don't have an account? </span>
                                <a href="/register" className="text-sm font-medium text-primary-600 hover:text-primary-500 transition-colors">
                                    Register here
                                </a>
                            </div>

                            {/* Rate Limit Info */}
                            <div className="text-center">
                                <span className="text-xs text-gray-400">🔒 Rate limit: 5 attempts per 15 minutes</span>
                            </div>
                        </form>
                    ) : (
                        /* MFA Section */
                        <div className="space-y-6">
                            <div className="text-center">
                                <div className="w-16 h-16 rounded-full bg-primary-100 flex items-center justify-center mx-auto mb-4">
                                    <span className="text-3xl">🔐</span>
                                </div>
                                <h3 className="text-lg font-semibold text-gray-900">
                                    {qrCodeUrl ? 'Set Up Two-Factor Authentication' : 'Two-Factor Authentication'}
                                </h3>
                                <p className="text-sm text-gray-500 mt-1">
                                    {qrCodeUrl
                                        ? 'Scan this QR code with Google Authenticator (or similar), then enter the 6-digit code it shows'
                                        : 'Enter the 6-digit code from your authenticator app'}
                                </p>
                            </div>

                            {/* Only shown on first-time enrollment */}
                            {qrCodeUrl && (
                                <div className="flex justify-center">
                                    <img
                                        src={qrCodeUrl}
                                        alt="MFA setup QR code"
                                        className="rounded-lg border border-gray-200 p-2"
                                        width={200}
                                        height={200}
                                    />
                                </div>
                            )}

                            <div>
                                <label htmlFor="totp" className="block text-sm font-medium text-gray-700 mb-1">
                                    Verification Code
                                </label>
                                <input
                                    type="text"
                                    id="totp"
                                    value={totpCode}
                                    onChange={(e) => setTotpCode(e.target.value)}
                                    className="block w-full px-4 py-3 border border-gray-300 rounded-xl placeholder-gray-400 text-gray-900 text-center text-2xl tracking-widest focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-200"
                                    placeholder="000000"
                                    maxLength={6}
                                    disabled={isLoading}
                                />
                            </div>

                            <button
                                onClick={handleMfaSubmit}
                                disabled={isLoading}
                                className="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl text-sm font-medium text-white bg-gradient-to-r from-primary-600 to-primary-500 hover:from-primary-700 hover:to-primary-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 shadow-lg hover:shadow-xl"
                            >
                                {isLoading ? (
                                    <span className="flex items-center gap-2">
                                        <svg className="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                        </svg>
                                        Verifying...
                                    </span>
                                ) : (
                                    qrCodeUrl ? 'Confirm & Enable MFA' : 'Verify MFA'
                                )}
                            </button>

                            <button
                                onClick={() => {
                                    setShowMfa(false);
                                    setTotpCode('');
                                    setQrCodeUrl(null);
                                }}
                                className="w-full text-center text-sm text-gray-500 hover:text-gray-700 transition-colors"
                                disabled={isLoading}
                            >
                                ← Back to login
                            </button>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="text-center">
                    <p className="text-xs text-gray-400">
                        © {new Date().getFullYear()} AuraCare Health Platform. All rights reserved.
                    </p>
                </div>
            </div>
        </div>
    );
};