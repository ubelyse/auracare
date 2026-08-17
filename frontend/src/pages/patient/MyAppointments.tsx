import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { appointmentService, Appointment } from '../../services/appointment';
import { useAuthStore } from '../../stores/authStore';

export const MyAppointments: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [upcomingAppointments, setUpcomingAppointments] = useState<Appointment[]>([]);
    const [pastAppointments, setPastAppointments] = useState<Appointment[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (!user) {
            navigate('/login');
            return;
        }
        loadAppointments();
    }, [user]);

    const loadAppointments = async () => {
        setIsLoading(true);
        try {
            const [upcomingRes, historyRes] = await Promise.all([
                appointmentService.getUpcomingAppointments(),
                appointmentService.getAppointmentHistory()
            ]);

            const now = new Date();

            const allUpcoming = upcomingRes.appointments || [];
            const allHistory = historyRes.appointments || [];

            // Filter upcoming: ONLY future SCHEDULED appointments
            const filteredUpcoming = allUpcoming.filter(appt => {
                const apptTime = new Date(appt.appointmentDateTime);
                return apptTime.getTime() > now.getTime() && appt.status === 'SCHEDULED';
            });

            // Filter past: include all history appointments that are in the past OR not SCHEDULED
            const filteredPast = allHistory.filter(appt => {
                const apptTime = new Date(appt.appointmentDateTime);
                return apptTime.getTime() < now.getTime() || appt.status !== 'SCHEDULED';
            });

            // Remove duplicates
            const upcomingIds = new Set(filteredUpcoming.map(a => a.id));
            const uniquePast = filteredPast.filter(appt => !upcomingIds.has(appt.id));

            setUpcomingAppointments(filteredUpcoming);
            setPastAppointments(uniquePast);
        } catch (error) {
            toast.error('Failed to load appointments');
        } finally {
            setIsLoading(false);
        }
    };

    const handleCheckIn = async (appointmentId: string) => {
        try {
            const result = await appointmentService.checkInFromAppointment(appointmentId);
            toast.success(`✅ Check-in successful! Ticket: ${result.ticketNumber}`);
            navigate(`/patient/queue/${result.ticketNumber}`);
        } catch (error: any) {
            toast.error(error.response?.data?.error || 'Check-in failed');
        }
    };

    const handleCancel = async (appointmentId: string) => {
        if (!confirm('Are you sure you want to cancel this appointment?')) return;
        try {
            await appointmentService.cancelAppointment(appointmentId);
            toast.success('Appointment cancelled');
            loadAppointments();
        } catch (error: any) {
            toast.error(error.response?.data?.error || 'Failed to cancel');
        }
    };

    const getStatusBadge = (status: string) => {
        const colors: Record<string, string> = {
            SCHEDULED: 'bg-blue-100 text-blue-800',
            CHECKED_IN: 'bg-green-100 text-green-800',
            COMPLETED: 'bg-gray-100 text-gray-800',
            CANCELLED: 'bg-red-100 text-red-800',
            NO_SHOW: 'bg-yellow-100 text-yellow-800'
        };
        return colors[status] || 'bg-gray-100 text-gray-800';
    };

    const formatTime = (dateTime: string) => {
        const date = new Date(dateTime);
        return date.toLocaleString();
    };

    const getCheckInWindowStatus = (appointment: Appointment) => {
        const now = new Date();
        const opens = new Date(appointment.checkInOpens);
        const closes = new Date(appointment.checkInCloses);

        if (now < opens) {
            return { text: `Opens at ${opens.toLocaleTimeString()}`, canCheckIn: false };
        } else if (now > closes) {
            return { text: 'Window closed', canCheckIn: false };
        } else {
            return { text: 'Window open! ✅', canCheckIn: true };
        }
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-900">📅 My Appointments</h1>
                    <button
                        onClick={() => navigate('/patient/book-appointment')}
                        className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                    >
                        + Book New
                    </button>
                </div>

                {/* Upcoming Appointments */}
                <div className="mb-8">
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Upcoming</h2>
                    {upcomingAppointments.length === 0 ? (
                        <p className="text-gray-500">No upcoming appointments</p>
                    ) : (
                        <div className="space-y-4">
                            {upcomingAppointments.map((appt) => {
                                const windowStatus = getCheckInWindowStatus(appt);
                                return (
                                    <div key={appt.id} className="border rounded-lg p-4 hover:shadow-md transition-shadow">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <span className={`text-xs px-2 py-1 rounded-full ${getStatusBadge(appt.status)}`}>
                                                        {appt.status}
                                                    </span>
                                                    <span className="text-sm text-gray-500">
                                                        {appt.facilityName} • {appt.departmentName}
                                                    </span>
                                                </div>
                                                <p className="text-sm font-medium text-gray-900 mt-1">
                                                    📅 {formatTime(appt.appointmentDateTime)}
                                                </p>
                                                {appt.doctorName && (
                                                    <p className="text-sm text-gray-600">👨‍⚕️ Dr. {appt.doctorName}</p>
                                                )}
                                                <p className="text-xs text-gray-500 mt-1">
                                                    Check-in window: {new Date(appt.checkInOpens).toLocaleTimeString()} - {new Date(appt.checkInCloses).toLocaleTimeString()}
                                                </p>
                                                {appt.status === 'SCHEDULED' && (
                                                    <p className={`text-xs mt-1 ${
                                                        windowStatus.canCheckIn ? 'text-green-600' : 'text-yellow-600'
                                                    }`}>
                                                        {windowStatus.canCheckIn ? '✅ Window is open!' : `⏳ ${windowStatus.text}`}
                                                    </p>
                                                )}
                                            </div>
                                            <div className="flex flex-col gap-2">
                                                {appt.status === 'SCHEDULED' && windowStatus.canCheckIn ? (
                                                    <button
                                                        onClick={() => handleCheckIn(appt.id)}
                                                        className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 text-sm"
                                                    >
                                                        ✅ Check In Now
                                                    </button>
                                                ) : appt.status === 'SCHEDULED' && !windowStatus.canCheckIn && (
                                                    <span className="text-xs text-yellow-600">
                                                        ⏳ {windowStatus.text}
                                                    </span>
                                                )}
                                                {appt.status === 'SCHEDULED' && (
                                                    <button
                                                        onClick={() => handleCancel(appt.id)}
                                                        className="px-4 py-2 bg-red-100 text-red-600 rounded-md hover:bg-red-200 text-sm"
                                                    >
                                                        Cancel
                                                    </button>
                                                )}
                                                {appt.status === 'CHECKED_IN' && (
                                                    <span className="text-xs text-green-600">✅ Checked in</span>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Past Appointments - WITH CHECK-IN WINDOW STATUS */}
                <div>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Past</h2>
                    {pastAppointments.length === 0 ? (
                        <p className="text-gray-500">No past appointments</p>
                    ) : (
                        <div className="space-y-4">
                            {pastAppointments.map((appt) => {
                                // ===== GET CHECK-IN WINDOW STATUS =====
                                const windowStatus = getCheckInWindowStatus(appt);

                                return (
                                    <div key={appt.id} className="border rounded-lg p-4 bg-gray-50">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <span className={`text-xs px-2 py-1 rounded-full ${getStatusBadge(appt.status)}`}>
                                                        {appt.status}
                                                    </span>
                                                    <span className="text-sm text-gray-500">
                                                        {appt.facilityName} • {appt.departmentName}
                                                    </span>
                                                </div>
                                                <p className="text-sm text-gray-600 mt-1">
                                                    📅 {formatTime(appt.appointmentDateTime)}
                                                </p>
                                                {appt.doctorName && (
                                                    <p className="text-sm text-gray-600">👨‍⚕️ Dr. {appt.doctorName}</p>
                                                )}
                                                {/* ===== SHOW CHECK-IN WINDOW STATUS ===== */}
                                                <p className="text-xs text-gray-500 mt-1">
                                                    Check-in window: {new Date(appt.checkInOpens).toLocaleTimeString()} - {new Date(appt.checkInCloses).toLocaleTimeString()}
                                                </p>
                                                {appt.status === 'SCHEDULED' && (
                                                    <p className={`text-xs mt-1 ${
                                                        windowStatus.canCheckIn ? 'text-green-600' : 'text-yellow-600'
                                                    }`}>
                                                        {windowStatus.canCheckIn ? '✅ Window is open!' : `⏳ ${windowStatus.text}`}
                                                    </p>
                                                )}
                                            </div>
                                            {/* ===== CHECK-IN BUTTON FOR PAST SECTION ===== */}
                                            <div className="flex flex-col gap-2">
                                                {appt.status === 'SCHEDULED' && windowStatus.canCheckIn ? (
                                                    <button
                                                        onClick={() => handleCheckIn(appt.id)}
                                                        className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 text-sm"
                                                    >
                                                        ✅ Check In Now
                                                    </button>
                                                ) : appt.status === 'SCHEDULED' && !windowStatus.canCheckIn && (
                                                    <span className="text-xs text-yellow-600">
                                                        ⏳ {windowStatus.text}
                                                    </span>
                                                )}
                                                {appt.status === 'CHECKED_IN' && (
                                                    <span className="text-xs text-green-600">✅ Checked in</span>
                                                )}
                                                {appt.status === 'CANCELLED' && (
                                                    <span className="text-xs text-red-600">❌ Cancelled</span>
                                                )}
                                                {appt.status === 'COMPLETED' && (
                                                    <span className="text-xs text-gray-600">✅ Completed</span>
                                                )}
                                                {appt.status === 'NO_SHOW' && (
                                                    <span className="text-xs text-yellow-600">⚠️ No Show</span>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                <button
                    onClick={() => navigate('/patient/dashboard')}
                    className="mt-6 px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                >
                    ← Back to Dashboard
                </button>
            </div>
        </div>
    );
};