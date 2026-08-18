import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { ticketService, ActiveTicket } from '../../services/ticket';
import { sseService } from '../../services/sse';
import toast from 'react-hot-toast';

// ===== UPDATE: Use ActiveTicket type =====
interface EmergencyAlert {
    message: string;
    ticketNumber: string;
    priority: string;
    options: {
        wait: boolean;
        internalTransfer: boolean;
        externalTransfer: boolean;
    };
}

// ===== ADD: TicketUpdate interface =====
interface TicketUpdate {
    status?: string;
    doctorName?: string;
    queuePosition?: number;
    estimatedWaitMinutes?: number;
}

export const PatientDashboard: React.FC = () => {
    const navigate = useNavigate();
    const { user, logout, patientMode, setPatientMode } = useAuthStore();
    const [hasActiveTicket, setHasActiveTicket] = useState(false);
    const [activeTicket, setActiveTicket] = useState<ActiveTicket | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [emergencyAlert, setEmergencyAlert] = useState<EmergencyAlert | null>(null);
    const [showEmergencyOptions, setShowEmergencyOptions] = useState(false);
    const [showTransferModal, setShowTransferModal] = useState(false);
    const [isProcessing, setIsProcessing] = useState(false);

    // ===== ADD: Ref to track if emergency has been handled =====
    const emergencyHandledRef = useRef(false);
    // ===== ADD: Ref to track if emergency toast has been shown =====
    const emergencyToastShownRef = useRef(false);
    // ===== ADD: Ref to track last emergency timestamp =====
    const lastEmergencyTimeRef = useRef<number>(0);
    // ===== ADD: Ref to track the current ticket number for session storage =====
    const currentTicketNumberRef = useRef<string | null>(null);

    // ===== If no mode selected, redirect to landing =====
    useEffect(() => {
        if (!patientMode && !hasActiveTicket) {
            navigate('/patient/landing');
            return;
        }
    }, [patientMode, hasActiveTicket, navigate]);

    // ===== LOAD SESSION STATE ON MOUNT =====
    useEffect(() => {
        // Check if there's a saved emergency state for this ticket
        if (activeTicket?.ticketNumber) {
            const savedState = sessionStorage.getItem(`emergencyHandled_${activeTicket.ticketNumber}`);
            if (savedState === 'true') {
                emergencyHandledRef.current = true;
                console.log('🔄 Emergency already handled (from session storage)');
            }
            currentTicketNumberRef.current = activeTicket.ticketNumber;
        }
    }, [activeTicket]);

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const checkActiveTicket = async () => {
            if (!isMounted) return;

            try {
                if (user?.role !== 'PATIENT') {
                    if (isMounted) {
                        setHasActiveTicket(false);
                        setIsLoading(false);
                    }
                    return;
                }

                const hasActive = await ticketService.hasActiveTicket();
                if (!isMounted) return;

                setHasActiveTicket(hasActive);

                if (hasActive) {
                    const ticket = await ticketService.getActiveTicket();
                    if (isMounted) {
                        setActiveTicket(ticket);
                        // Update the current ticket number ref
                        currentTicketNumberRef.current = ticket.ticketNumber;

                        // Check session storage for emergency state
                        const savedState = sessionStorage.getItem(`emergencyHandled_${ticket.ticketNumber}`);
                        if (savedState === 'true') {
                            emergencyHandledRef.current = true;
                            console.log('🔄 Emergency already handled for this ticket');
                        }

                        // If they have an active ticket, they must be at the hospital
                        setPatientMode('walkin');
                    }
                } else {
                    if (isMounted) {
                        setActiveTicket(null);
                        currentTicketNumberRef.current = null;
                        // Clear any saved emergency state
                        const keys = Object.keys(sessionStorage);
                        keys.forEach(key => {
                            if (key.startsWith('emergencyHandled_')) {
                                sessionStorage.removeItem(key);
                            }
                        });
                    }
                }
            } catch (error) {
                if (isMounted) {
                    setHasActiveTicket(false);
                    setActiveTicket(null);
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        checkActiveTicket();

        return () => {
            isMounted = false;
            sseService.disconnect();
            // ===== RESET refs on unmount =====
            emergencyHandledRef.current = false;
            emergencyToastShownRef.current = false;
        };
    }, [user, navigate, setPatientMode]);

    // Connect to SSE when active ticket exists
    useEffect(() => {
        if (hasActiveTicket && activeTicket?.ticketNumber) {
            connectSSE(activeTicket.ticketNumber);
        }

        // ===== RESET refs when ticket changes =====
        return () => {
            // Don't reset emergencyHandledRef here - it should persist for the ticket
            // Only reset if the ticket number changes
        };
    }, [hasActiveTicket, activeTicket]);

    const connectSSE = (ticketNumber: string) => {
        sseService.connectToTicket(
            ticketNumber,
            (data: TicketUpdate) => {
                console.log('📡 Ticket update:', data);
                checkActiveTicket();

                // ===== CLOSE EMERGENCY MODAL IF STATUS CHANGED =====
                if (data.status && data.status !== 'EMERGENCY') {
                    setShowEmergencyOptions(false);
                    setEmergencyAlert(null);
                    setShowTransferModal(false);
                    // ===== RESET refs when status changes from emergency =====
                    emergencyHandledRef.current = false;
                    emergencyToastShownRef.current = false;
                    // ===== Clear session storage =====
                    if (ticketNumber) {
                        sessionStorage.removeItem(`emergencyHandled_${ticketNumber}`);
                    }
                }

                // ===== CLOSE EMERGENCY MODAL IF DOCTOR CHANGED =====
                if (data.doctorName && showEmergencyOptions) {
                    setShowEmergencyOptions(false);
                    setEmergencyAlert(null);
                    setShowTransferModal(false);
                    toast.success(`✅ Transferred to ${data.doctorName}`);
                    // ===== RESET refs on transfer =====
                    emergencyHandledRef.current = false;
                    emergencyToastShownRef.current = false;
                    if (ticketNumber) {
                        sessionStorage.removeItem(`emergencyHandled_${ticketNumber}`);
                    }
                }
            },
            (emergencyData: EmergencyAlert) => {
                console.log('🚨 Emergency alert received:', emergencyData);

                // ===== CHECK IF EMERGENCY HAS ALREADY BEEN HANDLED =====
                // Check ref first
                if (emergencyHandledRef.current) {
                    console.log('🛑 Emergency already handled (ref), ignoring duplicate');
                    return;
                }

                // Check session storage
                if (ticketNumber) {
                    const savedState = sessionStorage.getItem(`emergencyHandled_${ticketNumber}`);
                    if (savedState === 'true') {
                        emergencyHandledRef.current = true;
                        console.log('🛑 Emergency already handled (session), ignoring duplicate');
                        return;
                    }
                }

                // ===== PREVENT DUPLICATE TOASTS =====
                const now = Date.now();
                if (!emergencyToastShownRef.current || (now - lastEmergencyTimeRef.current) > 5000) {
                    emergencyToastShownRef.current = true;
                    lastEmergencyTimeRef.current = now;
                    toast.error('🚨 EMERGENCY: Doctor has been called away!');
                } else {
                    console.log('🛑 Duplicate emergency toast suppressed');
                }

                setEmergencyAlert(emergencyData);
                setShowEmergencyOptions(true);
            }
        );
    };

    const checkActiveTicket = async () => {
        try {
            if (user?.role !== 'PATIENT') {
                setHasActiveTicket(false);
                return;
            }

            const hasActive = await ticketService.hasActiveTicket();
            setHasActiveTicket(hasActive);

            if (hasActive) {
                const ticket = await ticketService.getActiveTicket();
                setActiveTicket(ticket);
                currentTicketNumberRef.current = ticket.ticketNumber;
            } else {
                setActiveTicket(null);
                currentTicketNumberRef.current = null;
            }
        } catch (error) {
            setHasActiveTicket(false);
            setActiveTicket(null);
        }
    };

    // ===== FIXED: Added facilityId and departmentId =====
    const handleEmergencyChoice = async (choice: string) => {
        if (!activeTicket?.id) {
            toast.error('No active ticket found');
            return;
        }

        setIsProcessing(true);
        try {
            await ticketService.handleEmergencyChoice(
                activeTicket.id,
                choice,
                activeTicket.facilityId,    // ADD THIS
                activeTicket.departmentId   // ADD THIS
            );

            // ===== MARK EMERGENCY AS HANDLED =====
            emergencyHandledRef.current = true;
            // ===== Save to session storage to persist across refreshes =====
            if (activeTicket.ticketNumber) {
                sessionStorage.setItem(`emergencyHandled_${activeTicket.ticketNumber}`, 'true');
                console.log('💾 Emergency state saved to session storage');
            }

            // ===== CLOSE ALL MODALS =====
            setShowEmergencyOptions(false);
            setShowTransferModal(false);
            setEmergencyAlert(null);
            emergencyToastShownRef.current = false;

            // ===== REFRESH TICKET STATUS =====
            await checkActiveTicket();

            // ===== SHOW SUCCESS MESSAGE =====
            if (choice === 'WAIT') {
                toast.success('✅ You chose to wait. You will be notified when the doctor returns.');
            } else if (choice === 'INTERNAL_TRANSFER') {
                toast.success('✅ You have been transferred to another doctor.');
            }
        } catch (error: any) {
            const errorMsg = error.response?.data?.message || 'Failed to process emergency choice';
            toast.error(errorMsg);

            // If internal transfer failed due to no other doctor, keep modal open
            if (choice === 'INTERNAL_TRANSFER' && errorMsg.includes('No other doctor')) {
                setShowTransferModal(false);
                toast.error('No other doctor available. Please choose "Wait" instead.');
            } else {
                // For other errors, close modals and refresh
                setShowEmergencyOptions(false);
                setShowTransferModal(false);
                setEmergencyAlert(null);
                await checkActiveTicket();
            }
        } finally {
            setIsProcessing(false);
        }
    };

    const dismissEmergency = async () => {
        await handleEmergencyChoice('WAIT');
    };

    const handleLogout = async () => {
        // Clear any saved emergency state on logout
        const keys = Object.keys(sessionStorage);
        keys.forEach(key => {
            if (key.startsWith('emergencyHandled_')) {
                sessionStorage.removeItem(key);
            }
        });
        await logout();
        navigate('/login');
    };

    const getStatusDisplay = (status: string) => {
        const statusMap: Record<string, { icon: string; message: string; color: string }> = {
            'CHECKED_IN': {
                icon: '📋',
                message: 'Waiting for triage...',
                color: 'text-blue-600'
            },
            'TRIAGED': {
                icon: '🔄',
                message: 'Waiting for doctor...',
                color: 'text-indigo-600'
            },
            'IN_CONSULTATION': {
                icon: '👨‍⚕️',
                message: 'In consultation with doctor',
                color: 'text-green-600'
            },
            'LAB_PENDING': {
                icon: '🔬',
                message: 'Lab test in progress',
                color: 'text-purple-600'
            },
            'LAB_COMPLETED': {
                icon: '✅',
                message: 'Lab results ready',
                color: 'text-teal-600'
            },
            'CONSULTATION_DONE': {
                icon: '📝',
                message: 'Consultation complete',
                color: 'text-green-600'
            },
            'PAYMENT_PENDING': {
                icon: '💰',
                message: 'Payment pending',
                color: 'text-yellow-600'
            },
            'DISCHARGED': {
                icon: '🎉',
                message: 'Visit complete',
                color: 'text-gray-600'
            },
            'CANCELLED': {
                icon: '❌',
                message: 'Cancelled',
                color: 'text-red-600'
            }
        };
        return statusMap[status] || { icon: '📋', message: status || 'Unknown', color: 'text-gray-500' };
    };

    // ===== Determine if walk-in mode =====
    const isWalkIn = patientMode === 'walkin' || hasActiveTicket;

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">
                            Welcome, {user?.firstName} {user?.lastName}
                        </h1>
                        <p className="text-gray-600">
                            {isWalkIn ? '🏥 Walk-in Visit' : '📅 Appointment Booking'}
                        </p>
                        {activeTicket?.ticketNumber && (
                            <p className="text-xs text-gray-400 mt-1">
                                Ticket: {activeTicket.ticketNumber} • Status: {activeTicket.status}
                            </p>
                        )}
                    </div>
                    <div className="flex gap-2">
                        <button
                            onClick={() => {
                                const keys = Object.keys(sessionStorage);
                                keys.forEach(key => {
                                    if (key.startsWith('emergencyHandled_')) {
                                        sessionStorage.removeItem(key);
                                    }
                                });
                                setPatientMode(null);
                                navigate('/patient/landing');
                            }}
                            className="px-3 py-1 text-sm border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            Change Mode
                        </button>
                        <button
                            onClick={handleLogout}
                            className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
                        >
                            Logout
                        </button>
                    </div>
                </div>

                {/* Emergency Alert */}
                {showEmergencyOptions && emergencyAlert && (
                    <div className="mb-6 p-6 bg-red-50 rounded-lg border-2 border-red-500">
                        <div className="flex items-start justify-between">
                            <div className="flex-1">
                                <div className="flex items-center space-x-2">
                                    <span className="text-3xl">🚨</span>
                                    <h3 className="text-lg font-bold text-red-700">Emergency Mode Activated</h3>
                                </div>
                                <p className="text-sm text-red-600 mt-2">
                                    {emergencyAlert.message || 'The doctor has been called to an emergency. Please choose what you\'d like to do:'}
                                </p>
                                <div className="mt-4 flex flex-wrap gap-3">
                                    <button
                                        onClick={() => handleEmergencyChoice('WAIT')}
                                        disabled={isProcessing}
                                        className="px-4 py-2 bg-gray-600 text-white rounded-md hover:bg-gray-700 text-sm transition-colors disabled:opacity-50"
                                    >
                                        ⏳ Wait for Doctor
                                    </button>
                                    <button
                                        onClick={() => setShowTransferModal(true)}
                                        disabled={isProcessing}
                                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm transition-colors disabled:opacity-50"
                                    >
                                        🔄 Internal Transfer
                                    </button>
                                </div>
                                <p className="text-xs text-red-500 mt-2">
                                    ⏰ Doctor will return in ~30 minutes. If you don't choose, you will automatically wait.
                                </p>
                            </div>
                            <button
                                onClick={dismissEmergency}
                                disabled={isProcessing}
                                className="text-gray-400 hover:text-gray-600 ml-4"
                            >
                                ✕
                            </button>
                        </div>
                    </div>
                )}

                {/* Transfer Modal */}
                {showTransferModal && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                        <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                            <div className="text-center">
                                <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-blue-100 mb-4">
                                    <span className="text-2xl">🔄</span>
                                </div>
                                <h3 className="text-lg font-semibold text-gray-900 mb-2">Transfer to Another Doctor</h3>
                                <p className="text-sm text-gray-600 mb-4 text-left">
                                    If you choose to transfer internally:
                                </p>
                                <ul className="text-xs text-gray-600 text-left list-disc list-inside space-y-2 mb-6 bg-gray-50 p-3 rounded-md">
                                    <li>Your current ticket queue position will be reassigned.</li>
                                    <li>You will be routed to the next available doctor in the department.</li>
                                    <li>Your medical history and previous triage details remain securely attached.</li>
                                </ul>
                                <div className="flex space-x-3">
                                    <button
                                        onClick={() => handleEmergencyChoice('INTERNAL_TRANSFER')}
                                        disabled={isProcessing}
                                        className="flex-1 py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm font-medium disabled:opacity-50"
                                    >
                                        {isProcessing ? 'Processing...' : 'Confirm Transfer'}
                                    </button>
                                    <button
                                        onClick={() => setShowTransferModal(false)}
                                        disabled={isProcessing}
                                        className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50 text-sm font-medium"
                                    >
                                        Cancel
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Active Ticket Status */}
                {isWalkIn && hasActiveTicket && activeTicket && (
                    <div className="mb-6 p-4 bg-blue-50 rounded-lg border border-blue-200">
                        <div className="flex items-start justify-between">
                            <div>
                                <div className="flex items-center space-x-2">
                                    <span className="text-2xl">{getStatusDisplay(activeTicket.status).icon}</span>
                                    <span className="font-semibold text-blue-800">
                                        Active Ticket: {activeTicket.ticketNumber}
                                    </span>
                                    <span className={`text-xs px-2 py-1 rounded-full ${
                                        activeTicket.status === 'IN_CONSULTATION' ? 'bg-green-100 text-green-800' :
                                            activeTicket.status === 'LAB_PENDING' ? 'bg-purple-100 text-purple-800' :
                                                activeTicket.status === 'PAYMENT_PENDING' ? 'bg-yellow-100 text-yellow-800' :
                                                    'bg-blue-100 text-blue-800'
                                    }`}>
                                        {activeTicket.status.replace('_', ' ')}
                                    </span>
                                </div>
                                <p className="text-sm text-blue-700 mt-1">
                                    {getStatusDisplay(activeTicket.status).message}
                                </p>
                                {activeTicket.queuePosition && (
                                    <p className="text-xs text-blue-600 mt-1">
                                        Queue Position: #{activeTicket.queuePosition}
                                    </p>
                                )}
                                {activeTicket.doctorName && (
                                    <p className="text-xs text-blue-600 mt-1">
                                        👨‍⚕️ Doctor: {activeTicket.doctorName}
                                    </p>
                                )}
                            </div>
                            <button
                                onClick={() => navigate(`/patient/queue/${activeTicket.ticketNumber}`)}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm"
                            >
                                View Queue
                            </button>
                        </div>
                    </div>
                )}

                {/* Dashboard Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-6">
                    {isWalkIn && (
                        <>
                            <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <h3 className="text-lg font-semibold text-gray-900">🏥 New Check-In</h3>
                                        <p className="text-sm text-gray-500 mt-1">Start a new visit</p>
                                    </div>
                                    <span className="text-3xl">🏥</span>
                                </div>
                                <button
                                    onClick={() => navigate('/patient/checkin')}
                                    disabled={hasActiveTicket}
                                    className={`mt-4 w-full py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white ${
                                        hasActiveTicket
                                            ? 'bg-gray-400 cursor-not-allowed'
                                            : 'bg-primary-600 hover:bg-primary-700'
                                    }`}
                                >
                                    {hasActiveTicket ? 'You have an active ticket' : 'Check In Now'}
                                </button>
                            </div>

                            <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <h3 className="text-lg font-semibold text-gray-900">📋 Queue Status</h3>
                                        <p className="text-sm text-gray-500 mt-1">Check your position</p>
                                    </div>
                                    <span className="text-3xl">📋</span>
                                </div>
                                <button
                                    onClick={() => navigate(hasActiveTicket && activeTicket ? `/patient/queue/${activeTicket.ticketNumber}` : '/patient/queue')}
                                    disabled={!hasActiveTicket}
                                    className={`mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium ${
                                        hasActiveTicket
                                            ? 'bg-white text-gray-700 hover:bg-gray-50'
                                            : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                                    }`}
                                >
                                    {hasActiveTicket ? 'View Queue' : 'No Active Ticket'}
                                </button>
                            </div>
                        </>
                    )}

                    {!isWalkIn && (
                        <>
                            <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <h3 className="text-lg font-semibold text-gray-900">📅 My Appointments</h3>
                                        <p className="text-sm text-gray-500 mt-1">Book and manage appointments</p>
                                    </div>
                                    <span className="text-3xl">📅</span>
                                </div>
                                <button
                                    onClick={() => navigate('/patient/my-appointments')}
                                    className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                                >
                                    View Appointments
                                </button>
                            </div>

                            <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                                <div className="flex items-start justify-between">
                                    <div>
                                        <h3 className="text-lg font-semibold text-gray-900">📅 Book Appointment</h3>
                                        <p className="text-sm text-gray-500 mt-1">Schedule a future visit</p>
                                    </div>
                                    <span className="text-3xl">📅</span>
                                </div>
                                <button
                                    onClick={() => navigate('/patient/appointments/book')}
                                    className="mt-4 w-full py-2 px-4 bg-green-600 text-white rounded-md shadow-sm text-sm font-medium hover:bg-green-700"
                                >
                                    Book Now
                                </button>
                            </div>
                        </>
                    )}

                    <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                        <div className="flex items-start justify-between">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">📊 Medical History</h3>
                                <p className="text-sm text-gray-500 mt-1">View past visits</p>
                            </div>
                            <span className="text-3xl">📊</span>
                        </div>
                        <button
                            onClick={() => navigate('/patient/history')}
                            className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                        >
                            View History
                        </button>
                    </div>

                    <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                        <div className="flex items-start justify-between">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">💰 Billing</h3>
                                <p className="text-sm text-gray-500 mt-1">View and pay bills</p>
                            </div>
                            <span className="text-3xl">💰</span>
                        </div>
                        <button
                            onClick={() => navigate('/patient/billing')}
                            className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                        >
                            View Bills
                        </button>
                    </div>

                    <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                        <div className="flex items-start justify-between">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">👤 My Profile</h3>
                                <p className="text-sm text-gray-500 mt-1">Manage your account</p>
                            </div>
                            <span className="text-3xl">👤</span>
                        </div>
                        <button
                            onClick={() => navigate('/patient/profile')}
                            className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                        >
                            View Profile
                        </button>
                    </div>
                </div>

                <div className="mt-8 p-4 bg-gray-50 rounded-lg border border-gray-200">
                    <div className="flex items-center space-x-2">
                        <span className="text-green-500">✓</span>
                        <span className="text-sm text-gray-600">
                            Your data is protected with field-level AES-256-GCM encryption
                        </span>
                    </div>
                    <div className="flex items-center space-x-2 mt-1">
                        <span className="text-green-500">✓</span>
                        <span className="text-sm text-gray-600">
                            All PHI is scrubbed before AI analysis
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
};