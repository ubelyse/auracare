import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface User {
    id: string;
    username: string;
    firstName: string;
    lastName: string;
    email: string;
    role: string;
    facilityId?: string;
    facilityName?: string;
    departmentId?: string;
    departmentName?: string;
    departmentCode?: string;
}

export type PatientMode = 'walkin' | 'appointment' | null;

interface AuthState {
    user: User | null;
    accessToken: string | null;
    refreshToken: string | null;
    isAuthenticated: boolean;
    hasHydrated: boolean;
    patientMode: PatientMode;  // ← ADD THIS
    setUser: (user: User | null) => void;
    setTokens: (accessToken: string, refreshToken: string) => void;
    logout: () => void;
    setHasHydrated: (state: boolean) => void;
    updateUser: (updates: Partial<User>) => void;
    setPatientMode: (mode: PatientMode) => void;  // ← ADD THIS
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            user: null,
            accessToken: null,
            refreshToken: null,
            isAuthenticated: false,
            hasHydrated: false,
            patientMode: null,  // ← ADD THIS

            setUser: (user) => {
                set({
                    user,
                    isAuthenticated: !!user
                });
            },

            setTokens: (accessToken, refreshToken) => {
                if (!accessToken) {
                    console.warn('setTokens called with null/undefined accessToken');
                    return;
                }

                console.log('🔴 Saving tokens to localStorage');

                localStorage.setItem('accessToken', accessToken);
                localStorage.setItem('token', accessToken);
                localStorage.setItem('refreshToken', refreshToken);

                console.log('🔴 Token saved:', localStorage.getItem('token') ? 'YES' : 'NO');

                set({
                    accessToken,
                    refreshToken,
                    isAuthenticated: true
                });
            },

            logout: () => {
                try {
                    localStorage.clear();
                    sessionStorage.clear();
                } catch (e) {
                    // Silent fail - browser storage might be unavailable
                }
                set({
                    user: null,
                    accessToken: null,
                    refreshToken: null,
                    isAuthenticated: false,
                    patientMode: null  // ← ADD THIS
                });
            },

            setHasHydrated: (state) => {
                set({ hasHydrated: state });
            },

            updateUser: (updates) => {
                set((state) => ({
                    user: state.user ? { ...state.user, ...updates } : null
                }));
            },

            // ===== NEW: Set patient mode =====
            setPatientMode: (mode) => {
                set({ patientMode: mode });
            },
        }),
        {
            name: 'auth-storage',
            partialize: (state) => ({
                user: state.user,
                accessToken: state.accessToken,
                refreshToken: state.refreshToken,
                isAuthenticated: state.isAuthenticated,
                patientMode: state.patientMode,  // ← ADD THIS
            }),
            onRehydrateStorage: () => (state) => {
                state?.setHasHydrated(true);
            },
        }
    )
);