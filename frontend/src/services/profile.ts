// src/services/profile.ts
import api from './api';

// ===== ADD: Profile types =====
export interface Profile {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
    dateOfBirth: string;
    gender: string;
    chronicConditions: string;
    allergies: string;
    emergencyContactName: string;
    emergencyContactPhone: string;
    bloodType: string;
}

export interface ProfileUpdateRequest {
    firstName: string;
    lastName: string;
    phone: string;
    dateOfBirth: string | null;
    gender: string;
    chronicConditions: string;
    allergies: string;
    emergencyContactName: string;
    emergencyContactPhone: string;
    bloodType: string;
}

// ===== FIXED: Changed from 'profile' to 'profileService' =====
export const profileService = {
    async getProfile(): Promise<Profile> {
        const response = await api.get('/patient/profile');
        return response.data;
    },

    async updateProfile(data: ProfileUpdateRequest): Promise<{ message: string; patient: Profile }> {
        const response = await api.put('/patient/profile', data);
        return response.data;
    }
};