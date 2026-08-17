import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { transferService } from '../../services/transfer';
import { useAuthStore } from '../../stores/authStore';
import { FacilityTransfer } from '../../types/transfer';

export const TransferManagement: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [transfers, setTransfers] = useState<FacilityTransfer[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [selectedTransfer, setSelectedTransfer] = useState<FacilityTransfer | null>(null);

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadTransfers = async () => {
            if (!isMounted) return;

            setIsLoading(true);
            try {
                const data = await transferService.getPendingTransfers();
                if (isMounted) {
                    setTransfers(data || []);
                }
            } catch (error) {
                if (isMounted) {
                    toast.error('Failed to load transfers');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadTransfers();

        return () => {
            isMounted = false;
        };
    }, [user, navigate]);

    const loadTransfers = async () => {
        setIsLoading(true);
        try {
            const data = await transferService.getPendingTransfers();
            setTransfers(data || []);
        } catch (error) {
            toast.error('Failed to load transfers');
        } finally {
            setIsLoading(false);
        }
    };

    const handleApprove = async (transferId: string) => {
        try {
            const transfer = await transferService.approveTransfer(transferId);
            toast.success('Transfer approved successfully');
            loadTransfers();
            setSelectedTransfer(null);
        } catch (error) {
            toast.error('Failed to approve transfer');
        }
    };

    const getStatusBadge = (status: string) => {
        const colors: Record<string, string> = {
            PENDING: 'bg-yellow-100 text-yellow-800',
            APPROVED: 'bg-blue-100 text-blue-800',
            REJECTED: 'bg-red-100 text-red-800',
            COMPLETED: 'bg-green-100 text-green-800',
            CANCELLED: 'bg-gray-100 text-gray-800'
        };
        return colors[status] || 'bg-gray-100 text-gray-800';
    };

    const getTypeBadge = (type: string) => {
        const colors: Record<string, string> = {
            EMERGENCY: 'bg-red-100 text-red-800',
            ROUTINE: 'bg-blue-100 text-blue-800',
            SPECIALIST_REFERRAL: 'bg-purple-100 text-purple-800',
            PATIENT_REQUEST: 'bg-green-100 text-green-800'
        };
        return colors[type] || 'bg-gray-100 text-gray-800';
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading transfers...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-7xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">🔄 Transfer Management</h1>
                        <p className="text-sm text-gray-500">
                            Manage cross-facility patient transfers
                        </p>
                    </div>
                    <div className="flex gap-2">
                        <button
                            onClick={loadTransfers}
                            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            🔄 Refresh
                        </button>
                        <button
                            onClick={() => navigate('/admin/dashboard')}
                            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            ← Back
                        </button>
                    </div>
                </div>

                {/* Transfer List */}
                <div className="space-y-4">
                    {transfers.length === 0 ? (
                        <div className="text-center py-12">
                            <div className="text-6xl mb-4">📋</div>
                            <p className="text-gray-500">No pending transfers</p>
                            <button
                                onClick={loadTransfers}
                                className="mt-4 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                            >
                                🔄 Refresh
                            </button>
                        </div>
                    ) : (
                        transfers.map((transfer) => (
                            <div
                                key={transfer.id}
                                className="border rounded-lg p-4 hover:shadow-md transition-shadow"
                            >
                                <div className="flex flex-col md:flex-row md:items-center md:justify-between">
                                    <div className="flex items-start space-x-3">
                                        <div>
                                            <div className="flex items-center space-x-2">
                                                <span className="font-medium text-gray-900">
                                                    Transfer #{transfer.id.substring(0, 8)}
                                                </span>
                                                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getStatusBadge(transfer.status)}`}>
                                                    {transfer.status}
                                                </span>
                                                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getTypeBadge(transfer.transferType)}`}>
                                                    {transfer.transferType}
                                                </span>
                                            </div>
                                            <div className="mt-1 text-sm text-gray-600">
                                                <span className="font-medium">From:</span> {transfer.fromFacilityName || transfer.fromFacilityId}
                                                {' → '}
                                                <span className="font-medium">To:</span> {transfer.toFacilityName || transfer.toFacilityId}
                                            </div>
                                            <div className="text-sm text-gray-600">
                                                <span className="font-medium">Reason:</span> {transfer.transferReason}
                                            </div>
                                            <div className="text-xs text-gray-400 mt-1">
                                                Requested: {new Date(transfer.createdAt).toLocaleString()}
                                            </div>
                                        </div>
                                    </div>
                                    <div className="mt-2 md:mt-0 flex space-x-2">
                                        {transfer.status === 'PENDING' && (
                                            <>
                                                <button
                                                    onClick={() => handleApprove(transfer.id)}
                                                    className="px-4 py-1 bg-green-600 text-white rounded-md text-sm hover:bg-green-700"
                                                >
                                                    ✅ Approve
                                                </button>
                                                <button
                                                    onClick={() => setSelectedTransfer(transfer)}
                                                    className="px-4 py-1 border border-gray-300 rounded-md text-sm hover:bg-gray-50"
                                                >
                                                    👁️ Details
                                                </button>
                                            </>
                                        )}
                                        {transfer.status === 'COMPLETED' && (
                                            <span className="px-3 py-1 bg-green-100 text-green-800 rounded-md text-sm">
                                                ✅ Completed
                                            </span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Transfer Details Modal */}
            {selectedTransfer && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full p-6 max-h-[80vh] overflow-y-auto">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">
                                    📋 Transfer Details
                                </h3>
                                <p className="text-sm text-gray-500">
                                    ID: {selectedTransfer.id.substring(0, 8)}
                                </p>
                            </div>
                            <button
                                onClick={() => setSelectedTransfer(null)}
                                className="text-gray-400 hover:text-gray-500"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Status</label>
                                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getStatusBadge(selectedTransfer.status)}`}>
                                        {selectedTransfer.status}
                                    </span>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Type</label>
                                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getTypeBadge(selectedTransfer.transferType)}`}>
                                        {selectedTransfer.transferType}
                                    </span>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">From Facility</label>
                                    <p className="text-sm text-gray-900">{selectedTransfer.fromFacilityName || selectedTransfer.fromFacilityId}</p>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">To Facility</label>
                                    <p className="text-sm text-gray-900">{selectedTransfer.toFacilityName || selectedTransfer.toFacilityId}</p>
                                </div>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700">Reason</label>
                                <p className="text-sm text-gray-900">{selectedTransfer.transferReason}</p>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700">Ticket</label>
                                <p className="text-sm text-gray-900">{selectedTransfer.ticketId}</p>
                            </div>

                            {selectedTransfer.notes && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Notes</label>
                                    <p className="text-sm text-gray-900">{selectedTransfer.notes}</p>
                                </div>
                            )}

                            <div className="border-t border-gray-200 pt-4">
                                <div className="text-xs text-gray-500">
                                    <div>📅 Created: {new Date(selectedTransfer.createdAt).toLocaleString()}</div>
                                    {selectedTransfer.approvedAt && (
                                        <div>✅ Approved: {new Date(selectedTransfer.approvedAt).toLocaleString()}</div>
                                    )}
                                    {selectedTransfer.completedAt && (
                                        <div>✅ Completed: {new Date(selectedTransfer.completedAt).toLocaleString()}</div>
                                    )}
                                </div>
                            </div>

                            {selectedTransfer.status === 'PENDING' && (
                                <div className="flex space-x-3 pt-4 border-t border-gray-200">
                                    <button
                                        onClick={() => handleApprove(selectedTransfer.id)}
                                        className="flex-1 py-2 px-4 bg-green-600 text-white rounded-md hover:bg-green-700"
                                    >
                                        ✅ Approve Transfer
                                    </button>
                                    <button
                                        onClick={() => setSelectedTransfer(null)}
                                        className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                                    >
                                        Close
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};