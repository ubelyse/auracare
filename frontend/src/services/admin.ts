import api from './api';

// ===== EXISTING INTERFACES =====
export interface Facility {
    id: string;
    name: string;
    code: string;
    address: string;
    phone: string;
    email: string;
    active: boolean;
}

export interface Department {
    id: string;
    facilityId: string;
    name: string;
    code: string;
    description: string;
    active: boolean;
}

export interface StaffUser {
    id: string;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    role: string;
    active: boolean;
    facilityId?: string;
    facilityName?: string;
}

export interface User {
    id: string;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    phone?: string;
    role: string;
    active: boolean;
    isActive?: boolean;
    emailVerified?: boolean;
    facilityId?: string;
    facilityName?: string;
}

// ===== ADD: Insurance Provider types =====
export interface InsuranceProvider {
    id: string;
    code: string;
    name: string;
    patientCoPayPercentage: number;
    maxCoverageAmount: number;
    active: boolean;
    contactEmail?: string;
    contactPhone?: string;
    requirements?: string;
}

// ===== ADD: Service Pricing types =====
export interface ServicePricing {
    id: string;
    serviceCode: string;
    serviceName: string;
    category: string;
    basePrice: number;
    mutuellePrice?: number;
    rssbPrice?: number;
    privatePrice?: number;
    description?: string;
    active: boolean;
    facilityId?: string | null;
}

// ===== ADD: Telemetry types =====
export interface TelemetryData {
    facilities: FacilityMetrics[];
    totalPatients: number;
    totalStaff: number;
    activeFacilities: number;
    averageWaitTime: number;
    updatedAt: string;
}

export interface FacilityMetrics {
    id: string;
    name: string;
    code: string;
    address?: string;
    phone?: string;
    email?: string;
    active: boolean;
    isActive: boolean;
    activePatients: number;
    doctorCount: number;
    staffCount: number;
    avgWaitMinutes: number;
    doctorToPatientRatio: string;
    departments: DepartmentMetrics[];
    priorityDistribution: Record<string, number>;
}

export interface DepartmentMetrics {
    id: string;
    name: string;
    code: string;
    description?: string;
    active: boolean;
    patients: number;
    doctorCount?: number;
}

export const adminService = {
    // ===== FACILITY MANAGEMENT =====
    async createFacility(facility: Partial<Facility>): Promise<Facility> {
        const response = await api.post('/admin/facilities', facility);
        return response.data.facility;
    },

    async updateFacility(facilityId: string, facility: Partial<Facility>): Promise<Facility> {
        const response = await api.put(`/admin/facilities/${facilityId}`, facility);
        return response.data.facility;
    },

    async deleteFacility(facilityId: string): Promise<void> {
        await api.delete(`/admin/facilities/${facilityId}`);
    },

    async getFacilities(): Promise<Facility[]> {
        const response = await api.get('/admin/facilities');
        return response.data.facilities || [];
    },

    async getFacility(facilityId: string): Promise<Facility> {
        const response = await api.get(`/admin/facilities/${facilityId}`);
        return response.data;
    },

    // ===== DEPARTMENT MANAGEMENT =====
    async createDepartment(department: {
        name: string;
        code: string;
        description: string;
        active: boolean;
        facilityId: string;
    }): Promise<Department> {
        const response = await api.post('/admin/departments', department);
        return response.data.department;
    },

    async updateDepartment(departmentId: string, department: {
        name: string;
        code: string;
        description: string;
        active: boolean;
        facilityId: string;
    }): Promise<Department> {
        const response = await api.put(`/admin/departments/${departmentId}`, department);
        return response.data.department;
    },

    async getDepartmentsByFacility(facilityId: string): Promise<Department[]> {
        const response = await api.get(`/admin/facilities/${facilityId}/departments`);
        return response.data.departments || [];
    },

    // ===== STAFF MANAGEMENT =====
    async assignStaff(userId: string, facilityId: string, role: string, isPrimary: boolean): Promise<StaffUser> {
        const response = await api.post('/admin/staff/assign', null, {
            params: { userId, facilityId, role, isPrimary }
        });
        return response.data.user;
    },

    async removeStaff(userId: string, facilityId: string): Promise<StaffUser> {
        const response = await api.post('/admin/staff/remove', null, {
            params: { userId, facilityId }
        });
        return response.data.user;
    },

    async getStaffByFacility(facilityId: string): Promise<StaffUser[]> {
        const response = await api.get(`/admin/facilities/${facilityId}/staff`);
        return response.data.staff || [];
    },

    // ===== DOCTOR-DEPARTMENT ASSIGNMENT =====
    async assignDoctorToDepartment(doctorId: string, departmentId: string): Promise<StaffUser> {
        const response = await api.post('/admin/doctors/department/assign', null, {
            params: { doctorId, departmentId }
        });
        return response.data.doctor;
    },

    async removeDoctorFromDepartment(doctorId: string, departmentId: string): Promise<StaffUser> {
        const response = await api.post('/admin/doctors/department/remove', null, {
            params: { doctorId, departmentId }
        });
        return response.data.doctor;
    },

    async getDoctorsByDepartment(departmentId: string): Promise<StaffUser[]> {
        const response = await api.get(`/admin/departments/${departmentId}/doctors`);
        return response.data.doctors || [];
    },

    // ===== TELEMETRY =====
    async getTelemetry(): Promise<TelemetryData> {
        const response = await api.get('/admin/telemetry');
        return response.data;
    },

    async getFacilityTelemetry(facilityId: string): Promise<FacilityMetrics> {
        const response = await api.get(`/admin/telemetry/facility/${facilityId}`);
        return response.data;
    },

    // ===== USER MANAGEMENT =====
    async getUsers(): Promise<User[]> {
        const response = await api.get('/admin/users');
        return response.data.users || [];
    },

    async createUser(userData: {
        username: string;
        email: string;
        password: string;
        firstName: string;
        lastName: string;
        phone: string;
        role: string;
    }): Promise<User> {
        const response = await api.post('/admin/users', userData);
        return response.data.user;
    },

    async updateUserRole(userId: string, role: string): Promise<User> {
        const response = await api.put(`/admin/users/${userId}/role`, null, {
            params: { role }
        });
        return response.data.user;
    },

    async toggleUserActive(userId: string): Promise<User> {
        const response = await api.post(`/admin/users/${userId}/toggle-active`);
        return response.data.user;
    },

    // ===== INSURANCE PROVIDER MANAGEMENT =====
    async getInsuranceProviders(): Promise<InsuranceProvider[]> {
        const response = await api.get('/admin/insurance-providers');
        return response.data || [];
    },

    async createInsuranceProvider(provider: Partial<InsuranceProvider>): Promise<InsuranceProvider> {
        const response = await api.post('/admin/insurance-providers', provider);
        return response.data.provider;
    },

    async updateInsuranceProvider(providerId: string, provider: Partial<InsuranceProvider>): Promise<InsuranceProvider> {
        const response = await api.put(`/admin/insurance-providers/${providerId}`, provider);
        return response.data.provider;
    },

    async deleteInsuranceProvider(providerId: string): Promise<void> {
        await api.delete(`/admin/insurance-providers/${providerId}`);
    },

    // ===== SERVICE PRICING MANAGEMENT =====
    async getServicePricing(): Promise<ServicePricing[]> {
        const response = await api.get('/admin/service-pricing');
        return response.data || [];
    },

    async getServicePricingByCategory(category: string): Promise<ServicePricing[]> {
        const response = await api.get(`/admin/service-pricing/category/${category}`);
        return response.data || [];
    },

    async getServicePricingByFacility(facilityId: string): Promise<ServicePricing[]> {
        const response = await api.get(`/admin/service-pricing/facility/${facilityId}`);
        return response.data || [];
    },

    async createServicePricing(pricing: Partial<ServicePricing>): Promise<ServicePricing> {
        const response = await api.post('/admin/service-pricing', pricing);
        return response.data.pricing;
    },

    async updateServicePricing(pricingId: string, pricing: Partial<ServicePricing>): Promise<ServicePricing> {
        const response = await api.put(`/admin/service-pricing/${pricingId}`, pricing);
        return response.data.pricing;
    },

    async deleteServicePricing(pricingId: string): Promise<void> {
        await api.delete(`/admin/service-pricing/${pricingId}`);
    }
};