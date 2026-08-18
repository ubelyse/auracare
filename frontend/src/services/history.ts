import api from './api';
import { MedicalRecord } from '../types/medical';

// ===== ADD: Stats type =====
export interface HistoryStats {
    totalVisits: number;
    lastVisit: string;
    upcomingAppointments: number;
}

// ===== ADD: Search result type =====
export interface SearchResult {
    records: MedicalRecord[];
    count: number;
    keyword: string;
}

export const historyService = {
    // ===== FIXED: Added || [] fallback =====
    async getRecords(): Promise<{ records: MedicalRecord[]; count: number }> {
        const response = await api.get('/patient/history/records');
        const records = response.data.records || [];
        return {
            records,
            count: records.length
        };
    },

    async getRecord(recordId: string): Promise<MedicalRecord> {
        const response = await api.get(`/patient/history/records/${recordId}`);
        return response.data;
    },

    // ===== FIXED: Added proper return type with keyword =====
    async searchRecords(keyword: string): Promise<SearchResult> {
        const response = await api.get('/patient/history/search', {
            params: { keyword }
        });
        const records = response.data.records || [];
        return {
            records,
            count: records.length,
            keyword: response.data.keyword || keyword
        };
    },

    // ===== FIXED: Added proper type =====
    async getStats(): Promise<HistoryStats> {
        const response = await api.get('/patient/history/stats');
        return {
            totalVisits: response.data.totalVisits || 0,
            lastVisit: response.data.lastVisit || '2024-01-01',
            upcomingAppointments: response.data.upcomingAppointments || 0
        };
    }
};