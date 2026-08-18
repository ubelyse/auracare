// src/services/staff.ts
import api from './api';

export interface DashboardStats {
    totalPatients: number;
    waitingPatients: number;
    inConsultation: number;
    completedToday: number;
}

export interface Notification {
    id: string;
    message: string;
    read: boolean;
    createdAt: string;
}

export interface Patient {
    id: string;
    ticketNumber: string;
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
    status: string;
    priority: string;
    queuePosition: number;
    estimatedWaitMinutes: number;
    assignedDoctor?: string;
    createdAt: string;
}

export interface BillingItem {
    id: string;
    invoiceNumber: string;
    patientName: string;
    amount: number;
    status: 'PAID' | 'PENDING' | 'OVERDUE';
    issuedAt: string;
    dueDate: string;
}

export const staffService = {
    // ===== DASHBOARD =====
    async getDashboardStats(): Promise<DashboardStats> {
        const response = await api.get('/staff/dashboard/stats');
        return response.data;
    },

    // ===== QUEUE =====
    async getQueue(): Promise<Patient[]> {
        const response = await api.get('/staff/queue');
        return response.data;
    },

    // ===== PATIENTS =====
    async getPatients(): Promise<Patient[]> {
        const response = await api.get('/staff/patients');
        return response.data;
    },

    // ===== BILLING =====
    async getBilling(): Promise<BillingItem[]> {
        const response = await api.get('/staff/billing');
        return response.data;
    },

    // ===== NOTIFICATIONS =====
    async getNotifications(): Promise<Notification[]> {
        const response = await api.get('/staff/notifications');
        return response.data;
    },

    async markNotificationRead(id: string): Promise<void> {
        await api.put(`/staff/notifications/${id}/read`);
    },

    // ===== CONSULTATION =====
    async startConsultation(ticketId: string): Promise<void> {
        await api.post(`/staff/consultation/start/${ticketId}`);
    },

    async completeConsultation(ticketId: string): Promise<void> {
        await api.post(`/staff/consultation/complete/${ticketId}`);
    },

    // ===== PATIENT DETAILS =====
    async getPatientDetails(patientId: string): Promise<Patient> {
        const response = await api.get(`/staff/patients/${patientId}`);
        return response.data;
    },

    // ===== BILLING DETAILS =====
    async getBillingDetails(billingId: string): Promise<BillingItem> {
        const response = await api.get(`/staff/billing/${billingId}`);
        return response.data;
    }
};