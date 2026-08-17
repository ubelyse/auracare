import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { ticketService } from '../../services/ticket';

export const PatientLanding: React.FC = () => {
    const navigate = useNavigate();
    const { user, setPatientMode } = useAuthStore(); // Add this to auth store
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const checkActiveTicket = async () => {
            try {
                const hasActive = await ticketService.hasActiveTicket();
                if (hasActive) {
                    // If they already have an active ticket, they must be at the hospital
                    setPatientMode('walkin');
                    navigate('/patient/dashboard');
                }
            } catch (error) {
                console.error('Error checking active ticket:', error);
            } finally {
                setIsLoading(false);
            }
        };

        if (user) {
            checkActiveTicket();
        }
    }, [user, navigate, setPatientMode]);

    const handleAtHospital = () => {
        setPatientMode('walkin');
        navigate('/patient/dashboard');
    };

    const handleBookAppointment = () => {
        setPatientMode('appointment');
        navigate('/patient/dashboard');
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-50 flex items-center justify-center p-4">
            <div className="max-w-2xl w-full">
                <div className="bg-white rounded-2xl shadow-xl p-8">
                    {/* Logo/Header */}
                    <div className="text-center mb-8">
                        <div className="text-6xl mb-4">🏥</div>
                        <h1 className="text-3xl font-bold text-gray-900">
                            Welcome, {user?.firstName}!
                        </h1>
                        <p className="text-gray-500 mt-2">
                            How are you visiting us today?
                        </p>
                    </div>

                    {/* Two Options */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Option 1: At Hospital */}
                        <button
                            onClick={handleAtHospital}
                            className="group p-6 bg-white border-2 border-gray-200 rounded-xl hover:border-blue-500 hover:bg-blue-50 transition-all duration-200 text-left"
                        >
                            <div className="flex items-center gap-4">
                                <span className="text-4xl group-hover:scale-110 transition-transform duration-200">🏥</span>
                                <div>
                                    <h3 className="text-lg font-semibold text-gray-900">
                                        I'm at the Hospital
                                    </h3>
                                    <p className="text-sm text-gray-500">
                                        Check in for a walk-in visit
                                    </p>
                                </div>
                            </div>
                            <div className="mt-3 text-sm text-blue-600 font-medium flex items-center gap-1">
                                Check In Now <span className="group-hover:translate-x-1 transition-transform">→</span>
                            </div>
                        </button>

                        {/* Option 2: Book Appointment */}
                        <button
                            onClick={handleBookAppointment}
                            className="group p-6 bg-white border-2 border-gray-200 rounded-xl hover:border-green-500 hover:bg-green-50 transition-all duration-200 text-left"
                        >
                            <div className="flex items-center gap-4">
                                <span className="text-4xl group-hover:scale-110 transition-transform duration-200">📅</span>
                                <div>
                                    <h3 className="text-lg font-semibold text-gray-900">
                                        Book an Appointment
                                    </h3>
                                    <p className="text-sm text-gray-500">
                                        Schedule a future visit
                                    </p>
                                </div>
                            </div>
                            <div className="mt-3 text-sm text-green-600 font-medium flex items-center gap-1">
                                Schedule Now <span className="group-hover:translate-x-1 transition-transform">→</span>
                            </div>
                        </button>
                    </div>

                    {/* Footer */}
                    <div className="mt-8 pt-6 border-t border-gray-200 text-center">
                        <p className="text-xs text-gray-400">
                            You can change this selection anytime from your profile settings.
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
};