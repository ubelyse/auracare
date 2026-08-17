import api from './api';

export interface Appointment {
    id: string;
    patientId: string;
    patientName: string;
    facilityId: string;
    facilityName: string;
    departmentId: string;
    departmentName: string;
    doctorId?: string;
    doctorName?: string;
    appointmentDateTime: string;
    checkInOpens: string;
    checkInCloses: string;
    status: 'SCHEDULED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
}

export interface BookAppointmentRequest {
    facilityId: string;
    departmentId: string;
    doctorId?: string;
    appointmentTime: string;
}

export const appointmentService = {
    // ===== BOOK APPOINTMENT =====
    async bookAppointment(
        facilityId: string,
        departmentId: string,
        doctorId: string | null,
        appointmentTime: string
    ): Promise<{ appointment: Appointment; message: string }> {
        const response = await api.post('/appointments/book', null, {
            params: { facilityId, departmentId, doctorId, appointmentTime }
        });
        return response.data;
    },

    // ===== CHECK IN FROM APPOINTMENT =====
    async checkInFromAppointment(appointmentId: string): Promise<{
        ticketNumber: string;
        ticket: any;
        priority: string;
    }> {
        const response = await api.post('/appointments/checkin', null, {
            params: { appointmentId }
        });
        return response.data;
    },

    // ===== GET UPCOMING APPOINTMENTS =====
    async getUpcomingAppointments(): Promise<{ appointments: Appointment[]; count: number }> {
        const response = await api.get('/appointments/upcoming');
        return response.data;
    },

    // ===== GET APPOINTMENT HISTORY =====
    async getAppointmentHistory(): Promise<{ appointments: Appointment[]; count: number }> {
        const response = await api.get('/appointments/history');
        return response.data;
    },

    // ===== CANCEL APPOINTMENT =====
    async cancelAppointment(appointmentId: string): Promise<{ message: string }> {
        const response = await api.post(`/appointments/cancel/${appointmentId}`);
        return response.data;
    },

    // ===== CHECK IF PATIENT CAN CHECK IN =====
    async checkAppointmentWindow(appointmentId: string): Promise<{
        canCheckIn: boolean;
        checkInOpens: string;
        checkInCloses: string;
        status: string;
    }> {
        const response = await api.get(`/appointments/check-window/${appointmentId}`);
        return response.data;
    }
};