import api from './api';
import { FacilityTransfer } from '../types/transfer';

// ===== ADD: Transfer type =====
export type TransferType = 'EMERGENCY' | 'ROUTINE' | 'SPECIALIST_REFERRAL' | 'PATIENT_REQUEST';

export const transferService = {
    async initiateTransfer(
        ticketId: string,
        toFacilityId: string,
        reason: string,
        type: TransferType
    ): Promise<FacilityTransfer> {
        const response = await api.post('/transfer/initiate', null, {
            params: { ticketId, toFacilityId, reason, type }
        });
        return response.data.transfer;
    },

    async approveTransfer(transferId: string): Promise<FacilityTransfer> {
        const response = await api.post(`/transfer/approve/${transferId}`);
        return response.data.transfer;
    },

    // ===== FIXED: Added || [] fallback =====
    async getPendingTransfers(): Promise<FacilityTransfer[]> {
        const response = await api.get('/transfer/pending');
        return response.data.transfers || [];
    },

    // ===== FIXED: Added || [] fallback =====
    async getTicketTransferHistory(ticketId: string): Promise<FacilityTransfer[]> {
        const response = await api.get(`/transfer/history/ticket/${ticketId}`);
        return response.data.transfers || [];
    },

    async getTransferStatus(transferId: string): Promise<FacilityTransfer> {
        const response = await api.get(`/transfer/status/${transferId}`);
        return response.data;
    }
};