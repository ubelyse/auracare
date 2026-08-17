import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { historyService } from '../../services/history';
import { useAuthStore } from '../../stores/authStore';

// ===== UPDATED: Match backend MedicalRecord entity exactly =====
interface MedicalRecord {
    id: string;
    recordType: string;
    summary: string;
    details: string;
    metadata: string;
    doctorName?: string;
    doctorId?: string;
    recordDate: string;
    createdAt: string;
}

export const PatientHistory: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [records, setRecords] = useState<MedicalRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadHistory = async () => {
            if (!isMounted) return;

            try {
                const data = await historyService.getRecords();
                console.log('🔴 Records from API:', data); // ← Debug log
                if (isMounted) {
                    setRecords(data.records || []);
                }
            } catch (error: any) {
                if (isMounted) {
                    toast.error(error.response?.data?.message || 'Failed to load medical history');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadHistory();

        return () => {
            isMounted = false;
        };
    }, [user, navigate]);

    const getRecordTypeColor = (type: string) => {
        const colors: Record<string, string> = {
            CONSULTATION: 'bg-blue-100 text-blue-800',
            LAB_RESULT: 'bg-purple-100 text-purple-800',
            PRESCRIPTION: 'bg-green-100 text-green-800',
            BILLING: 'bg-yellow-100 text-yellow-800',
            SURGERY: 'bg-red-100 text-red-800',
            EMERGENCY: 'bg-orange-100 text-orange-800',
            ROUTINE: 'bg-gray-100 text-gray-800'
        };
        return colors[type] || 'bg-gray-100 text-gray-800';
    };

    const getRecordTypeIcon = (type: string) => {
        const icons: Record<string, string> = {
            CONSULTATION: '👨‍⚕️',
            LAB_RESULT: '🔬',
            PRESCRIPTION: '💊',
            BILLING: '💰',
            SURGERY: '🏥',
            EMERGENCY: '🚨',
            ROUTINE: '📋'
        };
        return icons[type] || '📋';
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading medical history...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-900">📋 Medical History</h1>
                    <button
                        onClick={() => navigate('/patient/dashboard')}
                        className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        ← Back to Dashboard
                    </button>
                </div>

                {records.length === 0 ? (
                    <div className="text-center py-12">
                        <div className="text-6xl mb-4">📋</div>
                        <h3 className="text-lg font-semibold text-gray-900">No Medical Records</h3>
                        <p className="text-gray-500 mt-2">You don't have any medical records yet.</p>
                        <button
                            onClick={() => navigate('/patient/checkin')}
                            className="mt-4 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                        >
                            Start Your First Check-In
                        </button>
                    </div>
                ) : (
                    <div className="space-y-4">
                        {records.map((record) => {
                            const recordType = record.recordType || 'ROUTINE';

                            return (
                                <div key={record.id} className="border rounded-lg p-4 hover:shadow-md transition-shadow">
                                    <div className="flex justify-between items-start">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="text-xl">{getRecordTypeIcon(recordType)}</span>
                                                <h3 className="font-semibold text-gray-900">
                                                    {record.summary || 'Medical Record'}
                                                </h3>
                                                <span className={`text-xs px-2 py-1 rounded-full ${getRecordTypeColor(recordType)}`}>
                                                    {recordType.replace('_', ' ')}
                                                </span>
                                            </div>
                                            <p className="text-sm text-gray-600 mt-1">
                                                {record.details || 'No details available'}
                                            </p>
                                            {record.metadata && (
                                                <p className="text-xs text-gray-500 mt-1">{record.metadata}</p>
                                            )}
                                            <p className="text-xs text-gray-400 mt-1">
                                                📅 {new Date(record.recordDate || record.createdAt).toLocaleString()}
                                            </p>
                                            {record.doctorName && (
                                                <p className="text-xs text-gray-400 mt-1">
                                                    👨‍⚕️ Doctor: {record.doctorName}
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}

                {records.length > 0 && (
                    <div className="mt-4 text-sm text-gray-500">
                        Total records: <span className="font-semibold">{records.length}</span>
                    </div>
                )}
            </div>
        </div>
    );
};