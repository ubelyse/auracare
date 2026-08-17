import React, { createContext, useContext, ReactNode } from 'react';
import { useAuthStore } from '../stores/authStore';

/*
  This replaces the old dual system (ThemeContext.tsx + theme/themes.ts, which
  were duplicate copies of the same hex-value object). Pages no longer read
  theme.primary / theme.background as JS values — they read CSS variables
  (var(--accent), var(--surface), etc.) set by whichever theme-* class wraps
  them. This context's only job now is: given a role, which class applies.
*/

export type ThemeClass = 'theme-patient' | 'theme-doctor' | 'theme-staff' | 'theme-admin';

const ROLE_THEME: Record<string, ThemeClass> = {
    PATIENT: 'theme-patient',
    DOCTOR: 'theme-doctor',
    STAFF: 'theme-staff',
    FACILITY_ADMIN: 'theme-admin',
    DISTRICT_ADMIN: 'theme-admin',
};

// Human label for the scope an admin is operating at
// ===== FIXED: Export if used elsewhere, or remove if unused =====
export const ROLE_SCOPE_LABEL: Record<string, string> = {
    FACILITY_ADMIN: 'Facility administration',
    DISTRICT_ADMIN: 'District network',
};

export const getThemeClass = (role?: string): ThemeClass =>
    ROLE_THEME[role || 'PATIENT'] || 'theme-patient';

interface ThemeContextType {
    themeClass: ThemeClass;
    role: string;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const { user } = useAuthStore();
    const role = user?.role || 'PATIENT';
    const themeClass = getThemeClass(role);

    return (
        <ThemeContext.Provider value={{ themeClass, role }}>
            {children}
        </ThemeContext.Provider>
    );
};

export const useTheme = () => {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within a ThemeProvider');
    }
    return context;
};