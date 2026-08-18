// src/pages/Patient/BookAppointment.tsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { appointmentService, Appointment } from '../../services/appointment';
import { ticketService } from '../../services/ticket';
import { Department } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';

// ===== ADD: Facility interface =====
interface Facility {
    id: string;
    name: string;
    code: string;
    active: boolean;
}

// ===== ADD: Doctor interface =====
interface Doctor {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
}

// ===== ADD: Department interface with availableDoctors =====
interface DepartmentWithDoctors {
    id: string;
    name: string;
    code: string;
    description: string;
    active: boolean;
    availableDoctors: Doctor[];
}

export const BookAppointment: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [isLoading, setIsLoading] = useState(false);
    const [facilities, setFacilities] = useState<Facility[]>([]);
    const [departments, setDepartments] = useState<DepartmentWithDoctors[]>([]);
    const [doctors, setDoctors] = useState<Doctor[]>([]);
    const [hasActiveTicket, setHasActiveTicket] = useState(false);
    const [formData, setFormData] = useState({
        facilityId: '',
        departmentId: '',
        doctorId: '',
        appointmentDate: '',
        appointmentTime: ''
    });

    // Load facilities using ticketService (public)
    useEffect(() => {
        loadFacilities();
        checkActiveTicket();
    }, []);

    // ===== ADD: Check if patient has active ticket =====
    const checkActiveTicket = async (): Promise<void> => {
        try {
            const hasActive = await ticketService.hasActiveTicket();
            setHasActiveTicket(hasActive);
        } catch (error) {
            // Silent fail
        }
    };

    // Load departments when facility changes
    useEffect(() => {
        if (formData.facilityId) {
            loadDepartments(formData.facilityId);
        } else {
            setDepartments([]);
        }
    }, [formData.facilityId]);

    // Load doctors when department changes
    useEffect(() => {
        if (formData.departmentId) {
            loadDoctors(formData.departmentId);
        } else {
            setDoctors([]);
        }
    }, [formData.departmentId]);

    const loadFacilities = async (): Promise<void> => {
        try {
            const data = await ticketService.getFacilities();
            setFacilities(data || []);
        } catch (error) {
            toast.error('Failed to load facilities');
        }
    };

    const loadDepartments = async (facilityId: string): Promise<void> => {
        try {
            const data = await ticketService.getDepartmentsWithDoctors(facilityId);
            setDepartments(data || []);
        } catch (error) {
            toast.error('Failed to load departments');
        }
    };

    const loadDoctors = async (departmentId: string): Promise<void> => {
        try {
            const data = await ticketService.getAvailableDoctors(departmentId);
            setDoctors(data || []);
        } catch (error) {
            // Silent fail - doctors are optional
            setDoctors([]);
        }
    };

    // ===== VALIDATION: Check if time is in the past =====
    const isTimeInPast = (date: string, time: string): boolean => {
        const selectedDateTime = new Date(`${date}T${time}`);
        const now = new Date();

        if (selectedDateTime.toDateString() === now.toDateString()) {
            return selectedDateTime < now;
        }
        return false;
    };

    // ===== VALIDATION: Check if time is within working hours =====
    const isValidWorkingHours = (time: string): boolean => {
        const hour = parseInt(time.split(':')[0]);
        const minutes = parseInt(time.split(':')[1]);

        if (hour < 8 || (hour === 17 && minutes > 0) || hour > 17) {
            return false;
        }
        return true;
    };

    // ===== VALIDATION: Check if date is in the past =====
    const isDateInPast = (date: string): boolean => {
        const selectedDate = new Date(date);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        return selectedDate < today;
    };

    const handleSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();

        // ===== VALIDATE: Date not in past =====
        if (isDateInPast(formData.appointmentDate)) {
            toast.error('Cannot book appointments in the past');
            return;
        }

        // ===== VALIDATE: Time within working hours =====
        if (!isValidWorkingHours(formData.appointmentTime)) {
            toast.error('Please select a time between 8:00 AM and 5:00 PM');
            return;
        }

        // ===== VALIDATE: Time not in past for today =====
        if (isTimeInPast(formData.appointmentDate, formData.appointmentTime)) {
            toast.error('Cannot book a time that has already passed today');
            return;
        }

        setIsLoading(true);
        try {
            // Send local time string without timezone conversion
            const localDateTime = `${formData.appointmentDate}T${formData.appointmentTime}:00`;

            const response = await appointmentService.bookAppointment(
                formData.facilityId,
                formData.departmentId,
                formData.doctorId || null,
                localDateTime
            );

            toast.success(`Appointment booked for ${formData.appointmentDate} at ${formData.appointmentTime}`);
            navigate('/patient/my-appointments');
        } catch (error: any) {
            toast.error(error.response?.data?.error || 'Booking failed');
        } finally {
            setIsLoading(false);
        }
    };

    const getMinDate = (): string => {
        const today = new Date();
        return today.toISOString().split('T')[0];
    };

    // ===== HELPER: Get facility name =====
    const getFacilityName = (facilityId: string): string => {
        const facility = facilities.find(f => f.id === facilityId);
        return facility?.name || 'Selected';
    };

    // ===== HELPER: Get department name =====
    const getDepartmentName = (departmentId: string): string => {
        const department = departments.find(d => d.id === departmentId);
        return department?.name || 'Selected';
    };

    // ===== HELPER: Get doctor name =====
    const getDoctorName = (doctorId: string): string => {
        const doctor = doctors.find(d => d.id === doctorId);
        if (doctor) {
            return `Dr. ${doctor.firstName} ${doctor.lastName}`;
        }
        return '';
    };

    return (
        <div className="max-w-2xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-900">📅 Book Appointment</h1>
                    <button
                        onClick={() => navigate('/patient/my-appointments')}
                        className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        ← My Appointments
                    </button>
                </div>

                {/* ===== FIXED: "View My Queue" button with ticket number ===== */}
                {hasActiveTicket && (
                    <div className="mb-6 p-4 bg-yellow-50 rounded-lg border border-yellow-200">
                        <div className="flex items-center gap-2">
                            <span className="text-xl">⚠️</span>
                            <div>
                                <p className="text-sm font-medium text-yellow-800">
                                    You already have an active ticket
                                </p>
                                <p className="text-xs text-yellow-700">
                                    Please complete your current visit before booking another appointment.
                                </p>
                            </div>
                        </div>
                        <button
                            onClick={async () => {
                                try {
                                    const ticket = await ticketService.getActiveTicket();
                                    if (ticket?.ticketNumber) {
                                        navigate(`/patient/queue/${ticket.ticketNumber}`);
                                    } else {
                                        navigate('/patient/queue');
                                    }
                                } catch {
                                    navigate('/patient/queue');
                                }
                            }}
                            className="mt-2 px-4 py-1 bg-yellow-600 text-white rounded-md text-sm hover:bg-yellow-700"
                        >
                            View My Queue
                        </button>
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Facility Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Facility <span className="text-red-500">*</span>
                        </label>
                        <select
                            required
                            value={formData.facilityId}
                            onChange={(e) => setFormData({ ...formData, facilityId: e.target.value, departmentId: '', doctorId: '' })}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={hasActiveTicket}
                        >
                            <option value="">Select a facility...</option>
                            {facilities.map((facility) => (
                                <option key={facility.id} value={facility.id}>
                                    {facility.name} ({facility.code})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Department Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Department <span className="text-red-500">*</span>
                        </label>
                        <select
                            required
                            value={formData.departmentId}
                            onChange={(e) => setFormData({ ...formData, departmentId: e.target.value, doctorId: '' })}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={!formData.facilityId || hasActiveTicket}
                        >
                            <option value="">
                                {formData.facilityId ? 'Select a department...' : 'Please select a facility first'}
                            </option>
                            {departments.map((dept) => (
                                <option key={dept.id} value={dept.id}>
                                    {dept.name} ({dept.code})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Doctor Selection (Optional) */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Doctor <span className="text-xs text-gray-400">(optional)</span>
                        </label>
                        {doctors.length > 0 ? (
                            <select
                                value={formData.doctorId}
                                onChange={(e) => setFormData({ ...formData, doctorId: e.target.value })}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                disabled={!formData.departmentId || hasActiveTicket}
                            >
                                <option value="">Any doctor</option>
                                {doctors.map((doctor) => (
                                    <option key={doctor.id} value={doctor.id}>
                                        Dr. {doctor.firstName} {doctor.lastName}
                                    </option>
                                ))}
                            </select>
                        ) : (
                            <div className="mt-1 p-3 bg-gray-50 rounded-md border border-gray-200">
                                <p className="text-sm text-gray-500">
                                    {formData.departmentId ? 'No doctors available in this department' : 'Select a department first'}
                                </p>
                            </div>
                        )}
                    </div>

                    {/* Date Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Date <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="date"
                            required
                            min={getMinDate()}
                            value={formData.appointmentDate}
                            onChange={(e) => setFormData({ ...formData, appointmentDate: e.target.value })}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={hasActiveTicket}
                        />
                    </div>

                    {/* Time Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Time <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="time"
                            required
                            min="08:00"
                            max="17:00"
                            step="1800"
                            value={formData.appointmentTime}
                            onChange={(e) => setFormData({ ...formData, appointmentTime: e.target.value })}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={hasActiveTicket}
                        />
                        <p className="text-xs text-gray-400 mt-1">Available hours: 8:00 AM - 5:00 PM</p>
                    </div>

                    {/* Summary Section */}
                    {formData.facilityId && formData.departmentId && formData.appointmentDate && formData.appointmentTime && (
                        <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                            <p className="text-sm font-medium text-blue-800">Appointment Summary:</p>
                            <ul className="text-sm text-blue-700 mt-1 space-y-1">
                                <li>🏥 {getFacilityName(formData.facilityId)}</li>
                                <li>📋 {getDepartmentName(formData.departmentId)}</li>
                                {formData.doctorId && (
                                    <li>👨‍⚕️ {getDoctorName(formData.doctorId)}</li>
                                )}
                                <li>📅 {new Date(`${formData.appointmentDate}T${formData.appointmentTime}`).toLocaleString()}</li>
                            </ul>
                            <p className="text-xs text-blue-600 mt-2">
                                ✅ Check-in window: 30 minutes before your appointment time
                            </p>
                        </div>
                    )}

                    {/* Submit Button */}
                    <div className="flex gap-3 pt-4">
                        <button
                            type="submit"
                            disabled={isLoading || hasActiveTicket}
                            className={`flex-1 py-3 px-4 rounded-md text-white font-medium ${
                                hasActiveTicket
                                    ? 'bg-gray-400 cursor-not-allowed'
                                    : 'bg-primary-600 hover:bg-primary-700'
                            } disabled:opacity-50 disabled:cursor-not-allowed`}
                        >
                            {hasActiveTicket ? '⚠️ Active Ticket Exists' : isLoading ? 'Booking...' : '📅 Book Appointment'}
                        </button>
                        <button
                            type="button"
                            onClick={() => navigate('/patient/dashboard')}
                            className="py-3 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            Cancel
                        </button>
                    </div>
                </form>

                {/* Info Box */}
                <div className="mt-6 p-4 bg-yellow-50 rounded-lg border border-yellow-200">
                    <p className="text-sm text-yellow-800">
                        <span className="font-medium">⏰ Reminder:</span>
                    </p>
                    <ul className="text-xs text-yellow-700 mt-1 space-y-1">
                        <li>• Check-in opens <strong>30 minutes</strong> before your appointment</li>
                        <li>• Check-in closes <strong>15 minutes</strong> after your appointment time</li>
                        <li>• If you're late, your appointment will be cancelled</li>
                        <li>• Booked appointments get <strong>HIGH priority</strong> in the queue</li>
                    </ul>
                </div>
            </div>
        </div>
    );
};