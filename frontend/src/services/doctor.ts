import api from './api';
import { Ticket } from '../types/ticket';
import { Facility } from './admin';

// ===== Types =====
export interface QueueMetrics {
    total: number;
    emergency: number;
    high: number;
    medium: number;
    low: number;
    averageWaitMinutes: number;
}

export interface EmergencyChoiceResult {
    message: string;
    ticket: Ticket;
    status: string;
}

export type EmergencyChoice = 'WAIT' | 'INTERNAL_TRANSFER' | 'EXTERNAL_TRANSFER';

export interface EmergencyStatus {
    active: boolean;
    endsAt?: string;
}

export interface LabService {
    id: string;
    serviceCode: string;
    serviceName: string;
    category: string;
    basePrice: number;
    mutuellePrice: number;
    rssbPrice: number;
    mmiPrice: number;
    description: string;
    active: boolean;
}

export interface BatchOrderResponse {
    message: string;
    successCount: number;
    totalCount: number;
    results: Array<{
        serviceCode: string;
        status: 'success' | 'failed';
        serviceName?: string;
        error?: string;
    }>;
    errors?: string[];
}

export const doctorService = {
    // ==================== QUEUE MANAGEMENT ====================

    async getDoctorQueue(): Promise<{ tickets: Ticket[]; count: number }> {
        const response = await api.get('/doctor/queue');
        return response.data;
    },

    async getQueueMetrics(facilityId: string, departmentId: string): Promise<QueueMetrics> {
        const response = await api.get('/doctor/metrics', {
            params: { facilityId, departmentId }
        });
        return response.data;
    },

    // ==================== CONSULTATION MANAGEMENT ====================

    async startConsultation(ticketId: string): Promise<Ticket> {
        if (!ticketId || typeof ticketId !== 'string') {
            throw new Error('Invalid ticket ID');
        }

        try {
            const response = await api.post('/doctor/consultation/start', null, {
                params: { ticketId }
            });
            return response.data.ticket;
        } catch (error: any) {
            console.error('Failed to start consultation:', {
                ticketId: ticketId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    async completeConsultation(ticketId: string): Promise<Ticket> {
        if (!ticketId || typeof ticketId !== 'string') {
            throw new Error('Invalid ticket ID');
        }

        try {
            const response = await api.post('/doctor/consultation/complete', null, {
                params: { ticketId }
            });
            return response.data.ticket;
        } catch (error: any) {
            console.error('Failed to complete consultation:', {
                ticketId: ticketId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    // ==================== LAB MANAGEMENT ====================

    async getLabServices(): Promise<{ labServices: LabService[]; count: number }> {
        const response = await api.get('/doctor/lab-services');
        return response.data;
    },

    async orderLabTest(ticketId: string, serviceCode: string): Promise<Ticket> {
        if (!ticketId || typeof ticketId !== 'string') {
            throw new Error('Invalid ticket ID');
        }
        if (!serviceCode || typeof serviceCode !== 'string') {
            throw new Error('Invalid service code');
        }

        try {
            const response = await api.post('/doctor/lab/order', null, {
                params: { ticketId, serviceCode }
            });
            return response.data.ticket;
        } catch (error: any) {
            console.error('Failed to order lab test:', {
                ticketId: ticketId.substring(0, 8) + '...',
                serviceCode: serviceCode.substring(0, 10) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    async batchOrderLabs(ticketId: string, serviceCodes: string[]): Promise<BatchOrderResponse> {
        if (!ticketId || typeof ticketId !== 'string') {
            throw new Error('Invalid ticket ID');
        }
        if (!Array.isArray(serviceCodes) || serviceCodes.length === 0) {
            throw new Error('At least one service code is required');
        }
        if (serviceCodes.length > 20) {
            throw new Error('Maximum 20 lab services per batch order');
        }

        try {
            const response = await api.post('/doctor/lab/batch-order', serviceCodes, {
                params: { ticketId }
            });
            return response.data;
        } catch (error: any) {
            console.error('Failed to batch order labs:', {
                ticketId: ticketId.substring(0, 8) + '...',
                count: serviceCodes.length,
                status: error.response?.status
            });
            throw error;
        }
    },

    async completeLabTest(ticketId: string, result: string): Promise<Ticket> {
        if (!ticketId || typeof ticketId !== 'string') {
            throw new Error('Invalid ticket ID');
        }
        if (!result || typeof result !== 'string' || result.trim().length === 0) {
            throw new Error('Lab results are required');
        }
        if (result.length > 5000) {
            throw new Error('Lab results exceed maximum length (5000 characters)');
        }

        try {
            const response = await api.post('/doctor/lab/complete', null, {
                params: { ticketId, result: result.trim() }
            });
            return response.data.ticket;
        } catch (error: any) {
            console.error('Failed to complete lab test:', {
                ticketId: ticketId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    // ==================== EMERGENCY MANAGEMENT ====================

    async activateEmergency(facilityId: string, departmentId: string, duration: number): Promise<void> {
        if (!facilityId || typeof facilityId !== 'string') {
            throw new Error('Invalid facility ID');
        }
        if (!departmentId || typeof departmentId !== 'string') {
            throw new Error('Invalid department ID');
        }
        if (!duration || duration < 5 || duration > 60) {
            throw new Error('Duration must be between 5 and 60 minutes');
        }

        try {
            const response = await api.post('/emergency/activate', null, {
                params: { facilityId, departmentId, durationMinutes: duration }
            });
            return response.data;
        } catch (error: any) {
            console.error('Failed to activate emergency mode:', {
                facilityId: facilityId.substring(0, 8) + '...',
                departmentId: departmentId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    async deactivateEmergency(facilityId: string, departmentId: string): Promise<void> {
        if (!facilityId || typeof facilityId !== 'string') {
            throw new Error('Invalid facility ID');
        }
        if (!departmentId || typeof departmentId !== 'string') {
            throw new Error('Invalid department ID');
        }

        try {
            const response = await api.post('/emergency/deactivate', null, {
                params: { facilityId, departmentId }
            });
            return response.data;
        } catch (error: any) {
            console.error('Failed to deactivate emergency mode:', {
                facilityId: facilityId.substring(0, 8) + '...',
                departmentId: departmentId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    async getEmergencyStatus(facilityId: string, departmentId: string): Promise<EmergencyStatus> {
        if (!facilityId || typeof facilityId !== 'string') {
            throw new Error('Invalid facility ID');
        }
        if (!departmentId || typeof departmentId !== 'string') {
            throw new Error('Invalid department ID');
        }

        try {
            const response = await api.get('/emergency/status', {
                params: { facilityId, departmentId }
            });
            return response.data;
        } catch (error: any) {
            console.error('Failed to get emergency status:', {
                facilityId: facilityId.substring(0, 8) + '...',
                departmentId: departmentId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    },

    async handleEmergencyChoice(ticketId: string, choice: EmergencyChoice, targetFacilityId?: string): Promise<EmergencyChoiceResult> {
        if (!ticketId || typeof ticketId !== 'string') {
            throw new Error('Invalid ticket ID');
        }
        if (!choice || !['WAIT', 'INTERNAL_TRANSFER', 'EXTERNAL_TRANSFER'].includes(choice)) {
            throw new Error('Invalid emergency choice');
        }

        try {
            const response = await api.post('/emergency/choice', null, {
                params: { ticketId, choice, targetFacilityId }
            });
            return response.data;
        } catch (error: any) {
            console.error('Failed to handle emergency choice:', {
                ticketId: ticketId.substring(0, 8) + '...',
                choice,
                status: error.response?.status
            });
            throw error;
        }
    },

    async getAvailableFacilities(facilityId: string, departmentCode: string): Promise<Facility[]> {
        if (!facilityId || typeof facilityId !== 'string') {
            throw new Error('Invalid facility ID');
        }
        if (!departmentCode || typeof departmentCode !== 'string') {
            throw new Error('Invalid department code');
        }

        try {
            const response = await api.get('/emergency/available-facilities', {
                params: { facilityId, departmentCode }
            });
            return response.data.facilities || [];
        } catch (error: any) {
            console.error('Failed to get available facilities:', {
                facilityId: facilityId.substring(0, 8) + '...',
                status: error.response?.status
            });
            throw error;
        }
    }
};