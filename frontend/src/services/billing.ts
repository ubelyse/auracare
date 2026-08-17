import api from './api';
import { Billing, PaymentResult } from '../types/billing';

// ===== Financial Summary type =====
export interface FinancialSummary {
    monthRevenue: number;
    todayRevenue: number;
    pendingBills: number;
    overdueBills: number;
    currency: string;
}

// ===== Insurance Claim type =====
export interface InsuranceClaim {
    insuranceType: string;
    totalAmount: number;
}

// ===== Report Data type =====
export interface ReportData {
    period: { start: string; end: string };
    totalBills: number;
    totalRevenue: number;
    totalPaid: number;
    totalPending: number;
    totalCancelled: number;
    byInsurance: Record<string, number>;
    bills: Array<{
        invoiceNumber: string;
        totalAmount: number;
        patientAmount: number;
        status: string;
        issuedAt: string;
        insuranceType: string;
    }>;
    currency: string;
}

// ===== Revenue Analysis type =====
export interface RevenueAnalysis {
    period: string;
    facilityId: string | null;
    facilityName: string;
    totalRevenue: number;
    totalPaid: number;
    pendingCount: number;
    dailyRevenue: Record<string, number>;
    dailyPaid: Record<string, number>;
    currency: string;
}

// ===== Simplified Audit Log types =====
export interface AuditLog {
    id: string;
    action: string;
    username?: string;
    actorUsername?: string;
    createdAt: string;
}

export interface AuditLogResponse {
    logs: AuditLog[];
    count: number;
}

export const billingService = {
    async generateBill(ticketId: string): Promise<Billing> {
        const response = await api.post('/billing/generate', null, {
            params: { ticketId }
        });
        return response.data.billing;
    },

    async getPatientBills(): Promise<Billing[]> {
        const response = await api.get('/billing/patient');
        return response.data.bills || [];
    },

    async getPatientPendingBills(): Promise<Billing[]> {
        const response = await api.get('/billing/patient/pending');
        return response.data || [];
    },

    async getBill(billingId: string): Promise<Billing> {
        const response = await api.get(`/billing/${billingId}`);
        return response.data;
    },

    // Staff-only manual payment endpoint. Patients should use simulatePayment().
    async processPayment(billingId: string, paymentMethod: string, transactionId: string): Promise<Billing> {
        const response = await api.post('/billing/payment', {
            billingId,
            paymentMethod,
            transactionId,
        });
        return response.data.billing;
    },

    // Patient-facing "Pay Now" — simulated payment gateway
    async simulatePayment(billingId: string, paymentMethod: string): Promise<PaymentResult> {
        const response = await api.post('/billing/payment/simulate', {
            billingId,
            paymentMethod,
        });
        return response.data;
    },

    // ===== Updated to use admin endpoints =====
    async getFacilitySummary(facilityId: string): Promise<FinancialSummary> {
        const response = await api.get(`/admin/financial/summary/${facilityId}`);
        return response.data;
    },

    async getInsuranceClaims(): Promise<InsuranceClaim[]> {
        const response = await api.get('/admin/financial/claims');
        return response.data || [];
    },

    // ===== Generate Report =====
    async generateReport(startDate?: string, endDate?: string, facilityId?: string): Promise<ReportData> {
        const params: Record<string, string> = {};
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;
        if (facilityId) params.facilityId = facilityId;

        const response = await api.get('/admin/reports', { params });
        return response.data;
    },

    // ===== Revenue Analysis =====
    async getRevenueAnalysis(period?: string, facilityId?: string): Promise<RevenueAnalysis> {
        const params: Record<string, string> = {};
        if (period) params.period = period;
        if (facilityId) params.facilityId = facilityId;

        const response = await api.get('/admin/revenue', { params });
        return response.data;
    },

    // ===== Claims Management =====
    async getClaims(insuranceType?: string, status?: string, facilityId?: string): Promise<any> {
        const params: Record<string, string> = {};
        if (insuranceType) params.insuranceType = insuranceType;
        if (status) params.status = status;
        if (facilityId) params.facilityId = facilityId;

        const response = await api.get('/admin/claims', { params });
        return response.data;
    },

    // ===== Audit Logs - Simplified =====
    async getAuditLogs(action?: string, username?: string, startDate?: string, endDate?: string): Promise<AuditLogResponse> {
        const params: Record<string, string> = {};
        if (action) params.action = action;
        if (username) params.username = username;
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;

        const response = await api.get('/admin/audit', { params });
        return response.data;
    }
};