import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ticketService } from '../../services/ticket';
import { sseService } from '../../services/sse';
import { useAuthStore } from '../../stores/authStore';

// ===== Types =====
interface TicketStatus {
    id?: string;
    ticketNumber: string;
    status: string;
    priority: string;
    queuePosition: number;
    estimatedWaitMinutes: number;
    facilityId?: string;
    facilityName?: string;
    departmentId?: string;
    departmentName?: string;
    departmentCode?: string;
    // ===== ADD THESE FIELDS =====
    message?: string;
    isFirstInLine?: boolean;
    isNearFront?: boolean;
    hasLongWait?: boolean;
    patientName?: string;
    doctorName?: string;
    triageScore?: number;
    isBooked?: boolean;
    // ===========================
}

interface EmergencyData {
    message: string;
    options?: {
        wait: boolean;
        internalTransfer: boolean;
        externalTransfer: boolean;
    };
}

interface Facility {
    id: string;
    name: string;
    code: string;
}

export const QueueStatus: React.FC = () => {
    const { ticketNumber } = useParams<{ ticketNumber: string }>();
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [status, setStatus] = useState<TicketStatus | null>(null);
    const [ticketId, setTicketId] = useState<string>('');
    const [isLoading, setIsLoading] = useState(true);
    const [showEmergencyOptions, setShowEmergencyOptions] = useState(false);
    const [emergencyData, setEmergencyData] = useState<EmergencyData | null>(null);
    const [availableFacilities, setAvailableFacilities] = useState<Facility[]>([]);
    const [selectedFacility, setSelectedFacility] = useState<string>('');
    const [isProcessing, setIsProcessing] = useState(false);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;

        if (!user) {
            navigate('/login');
            return;
        }

        loadStatus();
        connectSSE();

        return () => {
            isMounted.current = false;
            sseService.disconnect();
        };
    }, [ticketNumber]);

    const connectSSE = () => {
        if (!ticketNumber) return;

        sseService.connectToTicket(
            ticketNumber,
            (data) => {
                if (!isMounted.current) return;

                console.log('📡 SSE data received:', data);

                // Update status with SSE data
                setStatus(prev => ({
                    ...prev,
                    ...data
                }));

                // ===== CHECK IF EMERGENCY MODE IS DEACTIVATED =====
                // If status changed from emergency-related status back to normal
                if (data.status === 'TRIAGED' || data.status === 'IN_CONSULTATION' || data.status === 'LAB_PENDING' || data.status === 'LAB_COMPLETED') {
                    // Close emergency modal if it's open
                    if (showEmergencyOptions) {
                        console.log('🔄 Emergency modal closed - status changed to:', data.status);
                        setShowEmergencyOptions(false);
                        setEmergencyData(null);
                        toast.success('✅ Emergency resolved. Back to normal queue.');
                    }
                }

                // ===== CHECK IF DOCTOR WAS REASSIGNED =====
                if (data.doctorName) {
                    console.log('👨‍⚕️ Doctor assigned/reassigned:', data.doctorName);
                    // If emergency modal is open and doctor changed, close it
                    if (showEmergencyOptions) {
                        setShowEmergencyOptions(false);
                        setEmergencyData(null);
                        toast.success(`✅ You have been transferred to ${data.doctorName}`);
                    }
                }

                // Status change notifications
                if (data.status === 'IN_CONSULTATION') {
                    toast.success('👨‍⚕️ A doctor is now seeing you!');
                } else if (data.status === 'LAB_PENDING') {
                    toast.info('🔬 Lab test ordered. You will be called shortly.');
                } else if (data.status === 'LAB_COMPLETED') {
                    toast.info('✅ Lab results ready.');
                } else if (data.status === 'CONSULTATION_DONE') {
                    toast.success('✅ Consultation complete. Please proceed to billing.');
                } else if (data.status === 'PAYMENT_PENDING') {
                    toast.info('💰 Bill generated. Please proceed to payment.');
                } else if (data.status === 'DISCHARGED') {
                    toast.success('🎉 Your visit is complete. Thank you!');
                    setTimeout(() => navigate('/patient/dashboard'), 3000);
                }
            },
            (emergencyData) => {
                if (!isMounted.current) return;
                console.log('🚨 Emergency alert received:', emergencyData);
                handleEmergencyAlert(emergencyData);
            }
        );
    };

    const loadStatus = async () => {
        if (!ticketNumber) {
            setIsLoading(false);
            return;
        }

        setIsLoading(true);
        try {
            const data = await ticketService.getTicketStatus(ticketNumber);
            if (isMounted.current) {
                console.log('🔴🔴🔴 Status data from API:', data);
                console.log('🔴🔴🔴 departmentCode from API:', data.departmentCode);
                console.log('🔴🔴🔴 facilityId from API:', data.facilityId);

                let id = data.id;
                if (!id) {
                    try {
                        const activeTicket = await ticketService.getActiveTicket();
                        if (activeTicket && activeTicket.ticketNumber === ticketNumber) {
                            id = activeTicket.id;
                            console.log('🔴🔴🔴 Got ID from active ticket:', id);
                        }
                    } catch (e) {
                        console.warn('Could not fetch active ticket:', e);
                    }
                }

                if (id) {
                    setTicketId(id);
                    console.log('🔴🔴🔴 Ticket ID stored:', id);
                }

                // If status is not in emergency mode, close emergency modal
                if (data.status !== 'EMERGENCY' && showEmergencyOptions) {
                    setShowEmergencyOptions(false);
                    setEmergencyData(null);
                }

                setStatus(data);
            }
        } catch (error: any) {
            if (isMounted.current) {
                if (error.response?.status === 404) {
                    toast.error('Ticket not found');
                    setTimeout(() => navigate('/patient/dashboard'), 2000);
                } else {
                    toast.error('Failed to load ticket status');
                }
            }
        } finally {
            if (isMounted.current) {
                setIsLoading(false);
            }
        }
    };

    const handleEmergencyAlert = async (data: EmergencyData) => {
        setEmergencyData(data);
        setShowEmergencyOptions(true);
        toast.error('🚨 EMERGENCY: Doctor has been called away!');

        // DEBUG: Log what we have before making the API call
        console.log('🔴🔴🔴 EMERGENCY - status object:', status);
        console.log('🔴🔴🔴 EMERGENCY - facilityId:', status?.facilityId);
        console.log('🔴🔴🔴 EMERGENCY - departmentCode:', status?.departmentCode);
        console.log('🔴🔴🔴 EMERGENCY - departmentId:', status?.departmentId);
        console.log('🔴🔴🔴 EMERGENCY - departmentName:', status?.departmentName);

        // Validate required fields
        if (!status?.facilityId) {
            console.error('🔴🔴🔴 ERROR: facilityId is missing from status!');
            toast.error('Facility information is missing. Please refresh and try again.');
            return;
        }

        if (!status?.departmentCode) {
            console.error('🔴🔴🔴 ERROR: departmentCode is missing from status!');
            console.error('🔴🔴🔴 Full status object:', JSON.stringify(status, null, 2));
            toast.error('Department code is missing. Please refresh and try again.');
            return;
        }

        try {
            console.log('🔴🔴🔴 Calling getAvailableFacilities with:', {
                facilityId: status.facilityId,
                departmentCode: status.departmentCode
            });

            const facilities = await ticketService.getAvailableFacilities(
                status.facilityId,
                status.departmentCode
            );

            if (isMounted.current) {
                console.log('🔴🔴🔴 Available facilities:', facilities);
                setAvailableFacilities(facilities || []);
            }
        } catch (error: any) {
            console.error('🔴🔴🔴 Failed to fetch available facilities:', error);
            console.error('🔴🔴🔴 Error response:', error.response?.data);
            console.error('🔴🔴🔴 Error status:', error.response?.status);

            // Show specific error message to user
            if (error.response?.status === 400) {
                toast.error('Missing facility or department information. Please refresh.');
            } else {
                toast.error('Could not load available facilities for transfer.');
            }
        }
    };

    const handleEmergencyChoice = async (choice: string) => {
        setIsProcessing(true);
        try {
            let idToSend = ticketId;
            if (!idToSend) {
                idToSend = status?.id || status?.ticketNumber || ticketNumber || '';
            }

            console.log('🔴🔴🔴 Final ticketId being sent:', idToSend);

            if (!idToSend) {
                toast.error('Ticket not found');
                return;
            }

            const response = await ticketService.handleEmergencyChoice(
                idToSend,
                choice,
                choice === 'EXTERNAL_TRANSFER' ? selectedFacility : undefined
            );

            console.log('🔴🔴🔴 Emergency choice response:', response);

            // ===== CLOSE EMERGENCY MODAL IMMEDIATELY =====
            setShowEmergencyOptions(false);
            setEmergencyData(null);
            setAvailableFacilities([]);
            setSelectedFacility('');

            // ===== RELOAD STATUS TO GET UPDATED TICKET =====
            await loadStatus();

            // Show success message based on choice
            if (choice === 'WAIT') {
                toast.success('✅ You chose to wait. You will be notified when the doctor returns.');
            } else if (choice === 'INTERNAL_TRANSFER') {
                toast.success('✅ You have been transferred to another doctor.');
            } else if (choice === 'EXTERNAL_TRANSFER') {
                toast.success('✅ You are being transferred to another facility.');
                setTimeout(() => {
                    navigate('/patient/dashboard');
                }, 3000);
            }

        } catch (error: any) {
            console.error('🔴🔴🔴 Emergency choice error:', error);
            const errorMsg = error.response?.data?.message ||
                error.response?.data?.error ||
                'Failed to process emergency choice';
            toast.error(errorMsg);

            // If internal transfer failed because no other doctor available
            if (choice === 'INTERNAL_TRANSFER' && errorMsg.includes('No other doctor')) {
                // Keep emergency modal open with updated message
                toast.info('No other doctor available. Please choose "Wait" or "External Transfer".');
            } else {
                // For other errors, close modal and reload
                setShowEmergencyOptions(false);
                setEmergencyData(null);
                await loadStatus();
            }
        } finally {
            setIsProcessing(false);
        }
    };

    const getPriorityColor = (priority: string) => {
        switch (priority) {
            case 'EMERGENCY': return 'bg-red-600';
            case 'HIGH': return 'bg-orange-500';
            case 'MEDIUM': return 'bg-yellow-500';
            default: return 'bg-gray-500';
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'CHECKED_IN': return 'bg-blue-500';
            case 'TRIAGED': return 'bg-indigo-500';
            case 'IN_CONSULTATION': return 'bg-green-500';
            case 'LAB_PENDING': return 'bg-purple-500';
            case 'LAB_COMPLETED': return 'bg-teal-500';
            case 'CONSULTATION_DONE': return 'bg-green-600';
            case 'PAYMENT_PENDING': return 'bg-yellow-500';
            case 'DISCHARGED': return 'bg-gray-500';
            default: return 'bg-gray-400';
        }
    };

    const getStatusDisplay = (status: string) => {
        const statusMap: Record<string, { icon: string; message: string; color: string }> = {
            'CHECKED_IN': {
                icon: '📋',
                message: 'You have been checked in. Waiting for triage...',
                color: 'text-blue-600'
            },
            'TRIAGED': {
                icon: '🔄',
                message: 'You have been triaged. Waiting for doctor...',
                color: 'text-indigo-600'
            },
            'IN_CONSULTATION': {
                icon: '👨‍⚕️',
                message: 'You are currently in consultation with the doctor.',
                color: 'text-green-600'
            },
            'LAB_PENDING': {
                icon: '🔬',
                message: 'Lab test ordered. Waiting for results...',
                color: 'text-purple-600'
            },
            'LAB_COMPLETED': {
                icon: '✅',
                message: 'Lab results ready. Doctor will review shortly.',
                color: 'text-teal-600'
            },
            'CONSULTATION_DONE': {
                icon: '📝',
                message: 'Consultation complete. Bill generated.',
                color: 'text-green-600'
            },
            'PAYMENT_PENDING': {
                icon: '💰',
                message: 'Please proceed to billing desk or pay online.',
                color: 'text-yellow-600'
            },
            'DISCHARGED': {
                icon: '🎉',
                message: 'Your visit is complete. Thank you!',
                color: 'text-gray-600'
            },
            'CANCELLED': {
                icon: '❌',
                message: 'Ticket has been cancelled.',
                color: 'text-red-600'
            }
        };
        return statusMap[status] || { icon: '📋', message: 'Status unknown', color: 'text-gray-500' };
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading your queue status...</p>
                </div>
            </div>
        );
    }

    if (!status) {
        return (
            <div className="max-w-md mx-auto p-6">
                <div className="bg-white rounded-lg shadow-lg p-8 text-center">
                    <h2 className="text-xl font-semibold text-gray-900 mb-4">No Active Ticket</h2>
                    <p className="text-gray-600 mb-6">You don't have any active tickets.</p>
                    <button
                        onClick={() => navigate('/patient/checkin')}
                        className="inline-flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-primary-600 hover:bg-primary-700"
                    >
                        Check In Now
                    </button>
                </div>
            </div>
        );
    }

    const statusDisplay = getStatusDisplay(status.status);

    if (showEmergencyOptions) {
        return (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                    <div className="text-center">
                        <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                            <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                        </div>
                        <h3 className="text-lg font-semibold text-gray-900 mb-2">🚨 Emergency Mode</h3>
                        <p className="text-sm text-gray-600 mb-4">
                            {emergencyData?.message || 'The doctor has been called to an emergency. Please choose what you\'d like to do:'}
                        </p>

                        <div className="space-y-3">
                            <button
                                onClick={() => handleEmergencyChoice('WAIT')}
                                disabled={isProcessing}
                                className="w-full py-3 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50"
                            >
                                ⏳ Wait for Doctor
                                <p className="text-xs text-gray-500 mt-1">Continue waiting in queue</p>
                            </button>

                            <button
                                onClick={() => handleEmergencyChoice('INTERNAL_TRANSFER')}
                                disabled={isProcessing}
                                className="w-full py-3 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50"
                            >
                                🔄 Internal Transfer
                                <p className="text-xs text-gray-500 mt-1">See another doctor at this facility</p>
                            </button>

                            <div className="border-t border-gray-200 pt-3">
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Transfer to Another Facility
                                </label>
                                <select
                                    value={selectedFacility}
                                    onChange={(e) => setSelectedFacility(e.target.value)}
                                    className="w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    disabled={isProcessing}
                                >
                                    <option value="">Select facility</option>
                                    {availableFacilities.map((facility) => (
                                        <option key={facility.id} value={facility.id}>
                                            {facility.name}
                                        </option>
                                    ))}
                                </select>
                                <button
                                    onClick={() => handleEmergencyChoice('EXTERNAL_TRANSFER')}
                                    disabled={!selectedFacility || isProcessing}
                                    className="w-full mt-2 py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    🏥 Transfer Now
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-md mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="text-center">
                    <div className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-primary-100 mb-4">
                        <span className="text-3xl font-bold text-primary-600">#{status.ticketNumber}</span>
                    </div>

                    <h2 className="text-xl font-semibold text-gray-900">Your Queue Status</h2>

                    <div className="mt-4 flex justify-center space-x-4">
                        <span className={`px-3 py-1 rounded-full text-white text-sm font-medium ${getPriorityColor(status.priority)}`}>
                            {status.priority}
                        </span>
                        <span className={`px-3 py-1 rounded-full text-white text-sm font-medium ${getStatusColor(status.status)}`}>
                            {status.status.replace('_', ' ')}
                        </span>
                    </div>

                    {status.doctorName && (
                        <div className="mt-2 text-sm text-gray-600">
                            👨‍⚕️ Doctor: <span className="font-medium">{status.doctorName}</span>
                        </div>
                    )}

                    {/* ===== PATIENT-FRIENDLY MESSAGE ===== */}
                    {status.message ? (
                        <div className={`mt-4 p-4 rounded-lg ${
                            status.isFirstInLine
                                ? 'bg-green-50 border border-green-200'
                                : status.isNearFront
                                    ? 'bg-blue-50 border border-blue-200'
                                    : 'bg-gray-50 border border-gray-200'
                        }`}>
                            <div className="flex items-center justify-center space-x-2">
                                <span className="text-2xl">
                                    {status.isFirstInLine ? '🎯' :
                                        status.isNearFront ? '📋' : '💡'}
                                </span>
                                <p className={`text-sm font-medium ${
                                    status.isFirstInLine
                                        ? 'text-green-800'
                                        : status.isNearFront
                                            ? 'text-blue-800'
                                            : 'text-gray-700'
                                }`}>
                                    {status.message}
                                </p>
                            </div>
                            {status.isFirstInLine && (
                                <p className="text-xs text-green-600 mt-2">
                                    ✅ Please have your ID and insurance ready.
                                </p>
                            )}
                        </div>
                    ) : (
                        // Fallback to hard-coded status message if no message from backend
                        <div className="mt-4 p-4 bg-gray-50 rounded-lg">
                            <div className="flex items-center justify-center space-x-2">
                                <span className="text-2xl">{statusDisplay.icon}</span>
                                <p className={`text-sm font-medium ${statusDisplay.color}`}>
                                    {statusDisplay.message}
                                </p>
                            </div>
                        </div>
                    )}

                    <div className="mt-6 grid grid-cols-2 gap-4">
                        <div className="bg-gray-50 rounded-lg p-4">
                            <p className="text-sm text-gray-500">Queue Position</p>
                            <p className="text-2xl font-bold text-gray-900">#{status.queuePosition}</p>
                            {status.queuePosition === 1 && (
                                <p className="text-xs text-green-600 mt-1 font-semibold">✅ You're next!</p>
                            )}
                        </div>
                        <div className={`rounded-lg p-4 ${status.queuePosition === 1 ? 'bg-green-50' : 'bg-gray-50'}`}>
                            <p className="text-sm text-gray-500">Est. Wait Time</p>
                            {status.queuePosition === 1 ? (
                                <>
                                    <p className="text-2xl font-bold text-green-600">~5 min</p>
                                    <p className="text-xs text-green-600 mt-1">Doctor is ready for you</p>
                                </>
                            ) : (
                                <>
                                    <p className="text-2xl font-bold text-gray-900">{status.estimatedWaitMinutes} min</p>
                                    <p className="text-xs text-gray-400 mt-1">Based on queue position</p>
                                </>
                            )}
                        </div>
                    </div>

                    {/* ===== QUICK TIPS FOR FIRST PATIENT ===== */}
                    {status.isFirstInLine && (
                        <div className="mt-4 p-3 bg-yellow-50 rounded-lg border border-yellow-200">
                            <div className="flex items-start space-x-2">
                                <span className="text-lg">📍</span>
                                <div className="text-left">
                                    <p className="text-xs font-medium text-yellow-800">Quick Tips:</p>
                                    <ul className="text-xs text-yellow-700 space-y-0.5 mt-0.5">
                                        <li>✅ Be ready for your consultation</li>
                                        <li>🏥 Please be at the hospital or within 5 minutes</li>
                                        <li>📄 Have your ID and insurance ready</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* ===== LONG WAIT TIPS ===== */}
                    {status.hasLongWait && (
                        <div className="mt-4 p-3 bg-gray-50 rounded-lg border border-gray-200">
                            <div className="flex items-start space-x-2">
                                <span className="text-lg">💡</span>
                                <div className="text-left">
                                    <p className="text-xs font-medium text-gray-700">While You Wait:</p>
                                    <ul className="text-xs text-gray-600 space-y-0.5 mt-0.5">
                                        <li>☕ Grab a coffee or snack</li>
                                        <li>📱 Check your phone or read a book</li>
                                        <li>🔄 You'll get a notification when it's your turn</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="mt-4 flex items-center justify-center">
                        <div className="flex items-center space-x-2">
                            <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                            <span className="text-xs text-gray-500">Live updates connected</span>
                        </div>
                    </div>

                    <div className="mt-6 p-4 bg-blue-50 rounded-lg">
                        <p className="text-sm text-blue-800">
                            <span className="font-medium">Real-time:</span> You'll receive instant updates when your status changes.
                        </p>
                    </div>

                    <button
                        onClick={() => navigate('/patient/dashboard')}
                        className="mt-6 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
                    >
                        Back to Dashboard
                    </button>
                </div>
            </div>
        </div>
    );
};