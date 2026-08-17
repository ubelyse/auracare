import api from './api';
import { Ticket, CheckInRequest } from '../types/ticket';
import { Facility } from './admin';

interface TicketStatus {
    ticketNumber: string;
    status: string;
    priority: string;
    queuePosition: number;
    estimatedWaitMinutes: number;
    facilityId: string;
    departmentId: string;
    departmentCode: string;
}

interface DepartmentWithDoctors {
    id: string;
    name: string;
    code: string;
    description: string;
    active: boolean;
    availableDoctors: Doctor[];
}

interface Doctor {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
}

interface EmergencyChoiceResult {
    message: string;
    ticket: Ticket;
    status: string;
}

export interface ActiveTicket {
    id: string;
    ticketNumber: string;
    status: string;
    priority: string;
    queuePosition: number;
    estimatedWaitMinutes: number;
    facilityId: string;
    facilityName: string;
    departmentId: string;
    departmentName: string;
    departmentCode: string;
}

export const ticketService = {
    async initiateCheckIn(data: CheckInRequest): Promise<Ticket> {
        const response = await api.post('/checkin/initiate', data);
        return response.data.ticket;
    },

    async getActiveTicket(): Promise<ActiveTicket> {
        const response = await api.get('/checkin/active');
        return response.data;
    },

    async hasActiveTicket(): Promise<boolean> {
        const response = await api.get('/checkin/has-active');
        return response.data.hasActiveTicket === true;
    },

    async getTicketStatus(ticketNumber: string): Promise<TicketStatus> {
        const response = await api.get(`/checkin/status/${ticketNumber}`);
        return response.data;
    },

    async getFacilities(): Promise<Facility[]> {
        const response = await api.get('/checkin/facilities');
        return response.data || [];
    },

    async getDepartmentsWithDoctors(facilityId: string): Promise<DepartmentWithDoctors[]> {
        const response = await api.get(`/checkin/facilities/${facilityId}/departments`);
        return response.data || [];
    },

    async getAvailableDoctors(departmentId: string): Promise<Doctor[]> {
        const response = await api.get(`/checkin/department/${departmentId}/doctors`);
        return response.data.doctors || [];
    },

    async getAvailableFacilities(facilityId: string, departmentCode: string): Promise<Facility[]> {
        const response = await api.get('/emergency/available-facilities', {
            params: { facilityId, departmentCode }
        });
        return response.data.facilities || [];
    },

    async getDepartmentsByFacility(facilityId: string): Promise<any[]> {
        const response = await api.get(`/checkin/facilities/${facilityId}/departments`);
        return response.data || [];
    },

    async handleEmergencyChoice(ticketId: string, choice: string, targetFacilityId?: string): Promise<EmergencyChoiceResult> {
        const response = await api.post('/emergency/choice', null, {
            params: { ticketId, choice, targetFacilityId }
        });
        return response.data;
    }
};