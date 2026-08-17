// types/index.ts

export interface User {
    id: string;
    username: string;
    firstName: string;
    lastName: string;
    email: string;
    phone?: string;                              // ← ADDED: Phone number
    role: 'DISTRICT_ADMIN' | 'FACILITY_ADMIN' | 'DOCTOR' | 'STAFF' | 'PATIENT';
    facilityId?: string;
    facilityName?: string;
    departmentId?: string;
    departmentName?: string;
    departmentCode?: string;
    isActive: boolean;
    active?: boolean;                            // ← ADDED: Alias for isActive
    emailVerified: boolean;
    mfaEnabled: boolean;
    gender?: 'MALE' | 'FEMALE' | 'OTHER';       // ← ADDED: Gender
    dateOfBirth?: string;                        // ← ADDED: Date of birth
}

export interface Facility {
    id: string;
    name: string;
    code: string;
    address: string;
    phone: string;
    email: string;
    isActive: boolean;
    active?: boolean;                            // ← ADDED: Alias for isActive
}

export interface LoginResponse {
    accessToken?: string;
    refreshToken?: string;
    user?: User;
    requiresMfa?: boolean;
    mfaSetupRequired?: boolean;
    qrCodeUrl?: string;
    userId?: string;
}

export interface AuditLog {
    id: string;
    userId: string;
    username: string;
    action: string;
    resourceType: string;
    resourceId: string;
    ipAddress: string;
    details: string;
    createdAt: string;
}