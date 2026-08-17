import React, { useState, useEffect, useRef } from 'react';  // ← ADD useRef
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService, Facility } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';
import { FacilityModal } from '../../components/admin/FacilityModal';

interface TelemetryData {
    facilities: Facility[];
    totalPatients: number;
    totalStaff: number;
    activeFacilities: number;
    averageWaitTime: number;
    updatedAt: string;
}

interface FacilityMetrics extends Facility {
    activePatients: number;
    doctorCount: number;
    staffCount: number;
    avgWaitMinutes: number;
    doctorToPatientRatio: string;
    departments: {
        name: string;
        code: string;
        patients: number;
        active: boolean;
    }[];
    priorityDistribution: Record<string, number>;
}

export const DistrictDashboard: React.FC = () => {
    const navigate = useNavigate();
    const { user, logout } = useAuthStore();
    const [telemetry, setTelemetry] = useState<TelemetryData | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [showFacilityModal, setShowFacilityModal] = useState(false);
    const [editingFacility, setEditingFacility] = useState<Facility | null>(null);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState<string | null>(null);
    const intervalRef = useRef<NodeJS.Timeout | null>(null);  // ← ADD THIS

    // ===== START AUTO-REFRESH =====
    useEffect(() => {
        if (!user) {
            navigate('/login');
            return;
        }
        loadTelemetry();

        // Start auto-refresh
        intervalRef.current = setInterval(loadTelemetry, 30000);

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, [user]);

    // ===== PAUSE AUTO-REFRESH WHEN MODAL IS OPEN =====
    useEffect(() => {
        if (showFacilityModal) {
            // Stop auto-refresh while modal is open
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        } else {
            // Restart auto-refresh when modal closes
            if (!intervalRef.current) {
                intervalRef.current = setInterval(loadTelemetry, 30000);
            }
        }

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, [showFacilityModal]);

    const loadTelemetry = async () => {
        try {
            setIsLoading(true);
            const data = await adminService.getTelemetry();
            setTelemetry(data);
        } catch (error: any) {
            if (error.response?.status === 403) {
                toast.error('Access denied. You need admin privileges.');
            } else if (error.response?.status === 401) {
                toast.error('Session expired. Please login again.');
                navigate('/login');
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleDeleteFacility = async (facilityId: string) => {
        try {
            await adminService.deleteFacility(facilityId);
            toast.success('Facility deactivated successfully');
            setShowDeleteConfirm(null);
            loadTelemetry();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Failed to delete facility');
        }
    };

    const getStatusColor = (activePatients: number, doctorCount: number) => {
        const ratio = doctorCount > 0 ? activePatients / doctorCount : 0;
        if (ratio > 10) return 'text-red-600';
        if (ratio > 5) return 'text-yellow-600';
        return 'text-green-600';
    };

    const getPriorityColor = (priority: string) => {
        const colors: Record<string, string> = {
            EMERGENCY: 'bg-red-600',
            HIGH: 'bg-orange-500',
            MEDIUM: 'bg-yellow-500',
            LOW: 'bg-gray-500'
        };
        return colors[priority] || 'bg-gray-500';
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading dashboard...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50">
            {/* Header */}
            <div className="bg-white shadow-md border-b border-gray-200 sticky top-0 z-10">
                <div className="max-w-7xl mx-auto px-6 py-4">
                    <div className="flex justify-between items-center flex-wrap gap-4">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
                                <span className="text-3xl">🏥</span> Admin Health Dashboard
                            </h1>
                            <p className="text-sm text-gray-500">
                                Live telemetry for all facilities
                            </p>
                            <p className="text-xs text-gray-400 mt-1">
                                Last updated: {telemetry?.updatedAt ? new Date(telemetry.updatedAt).toLocaleString() : 'N/A'}
                            </p>
                        </div>
                        <div className="flex items-center gap-4 flex-wrap">
                            <div className="flex gap-6">
                                <div className="text-center bg-gray-50 rounded-lg px-4 py-2">
                                    <p className="text-xs text-gray-500">Total Patients</p>
                                    <p className="text-2xl font-bold text-gray-900">{telemetry?.totalPatients || 0}</p>
                                </div>
                                <div className="text-center bg-gray-50 rounded-lg px-4 py-2">
                                    <p className="text-xs text-gray-500">Total Staff</p>
                                    <p className="text-2xl font-bold text-gray-900">{telemetry?.totalStaff || 0}</p>
                                </div>
                                <div className="text-center bg-green-50 rounded-lg px-4 py-2">
                                    <p className="text-xs text-green-600">Active Centers</p>
                                    <p className="text-2xl font-bold text-green-600">{telemetry?.activeFacilities || 0}</p>
                                </div>
                                <div className="text-center bg-blue-50 rounded-lg px-4 py-2">
                                    <p className="text-xs text-blue-600">Avg Wait</p>
                                    <p className="text-2xl font-bold text-blue-600">{telemetry?.averageWaitTime || 0} min</p>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <button
                                    onClick={loadTelemetry}
                                    className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50 text-sm transition-colors"
                                >
                                    🔄 Refresh
                                </button>
                                <button
                                    onClick={() => navigate('/admin/service-pricing')}
                                    className="px-4 py-2 border border-green-300 rounded-md text-green-600 hover:bg-green-50 text-sm transition-colors"
                                >
                                    💰 Service Pricing
                                </button>
                                <button
                                    onClick={() => {
                                        logout();
                                        navigate('/login');
                                    }}
                                    className="px-4 py-2 border border-red-300 rounded-md text-red-600 hover:bg-red-50 text-sm transition-colors"
                                >
                                    Logout
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="max-w-7xl mx-auto px-6 py-6">
                {/* Quick Actions */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                    <button
                        onClick={() => {
                            setEditingFacility(null);
                            setShowFacilityModal(true);
                        }}
                        className="bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow text-left border-l-4 border-green-500"
                    >
                        <h4 className="font-medium text-gray-900">➕ New Facility</h4>
                        <p className="text-sm text-gray-500">Add a health center</p>
                    </button>
                    <button
                        onClick={() => navigate('/admin/users')}
                        className="bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow text-left border-l-4 border-blue-500"
                    >
                        <h4 className="font-medium text-gray-900">👥 Users</h4>
                        <p className="text-sm text-gray-500">Manage system users</p>
                    </button>
                    <button
                        onClick={() => navigate('/admin/financial')}
                        className="bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow text-left border-l-4 border-yellow-500"
                    >
                        <h4 className="font-medium text-gray-900">💰 Financial</h4>
                        <p className="text-sm text-gray-500">Revenue & claims</p>
                    </button>
                    <button
                        onClick={() => navigate('/admin/transfers')}
                        className="bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow text-left border-l-4 border-purple-500"
                    >
                        <h4 className="font-medium text-gray-900">🔄 Transfers</h4>
                        <p className="text-sm text-gray-500">Manage patient transfers</p>
                    </button>
                </div>

                {/* Facility Cards */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {telemetry?.facilities?.map((facility: FacilityMetrics) => (
                        <div
                            key={facility.id}
                            className={`bg-white rounded-lg shadow-lg overflow-hidden hover:shadow-xl transition-all duration-300 ${
                                !facility.isActive ? 'opacity-60' : ''
                            }`}
                        >
                            {/* Facility Header */}
                            <div className="p-4 border-b bg-gradient-to-r from-gray-50 to-white">
                                <div className="flex items-start justify-between">
                                    <div className="flex-1">
                                        <h3 className="font-semibold text-gray-900 text-lg">{facility.name}</h3>
                                        <p className="text-sm text-gray-500">{facility.code}</p>
                                        {facility.address && (
                                            <p className="text-xs text-gray-400 mt-1">{facility.address}</p>
                                        )}
                                    </div>
                                    <div className="flex flex-col items-end gap-1">
                                        <span className={`text-xs px-2 py-1 rounded-full ${
                                            facility.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                        }`}>
                                            {facility.isActive ? '🟢 Active' : '🔴 Inactive'}
                                        </span>
                                        <span className="text-xs text-gray-400">
                                            {facility.departments?.length || 0} depts
                                        </span>
                                    </div>
                                </div>
                            </div>

                            {/* Stats */}
                            <div className="p-4">
                                <div className="grid grid-cols-3 gap-2">
                                    <div className="bg-gray-50 rounded-lg p-3 text-center">
                                        <p className="text-xs text-gray-500">Patients</p>
                                        <p className={`text-xl font-bold ${getStatusColor(facility.activePatients, facility.doctorCount)}`}>
                                            {facility.activePatients}
                                        </p>
                                    </div>
                                    <div className="bg-gray-50 rounded-lg p-3 text-center">
                                        <p className="text-xs text-gray-500">Doctors</p>
                                        <p className="text-xl font-bold text-gray-900">{facility.doctorCount}</p>
                                    </div>
                                    <div className="bg-gray-50 rounded-lg p-3 text-center">
                                        <p className="text-xs text-gray-500">Staff</p>
                                        <p className="text-xl font-bold text-gray-900">{facility.staffCount}</p>
                                    </div>
                                </div>

                                <div className="mt-3 flex justify-between items-center text-sm">
                                    <div>
                                        <p className="text-xs text-gray-500">Avg Wait Time</p>
                                        <p className="font-semibold text-gray-900">{facility.avgWaitMinutes} min</p>
                                    </div>
                                    <div>
                                        <p className="text-xs text-gray-500">Doctor:Patient Ratio</p>
                                        <p className="font-semibold text-gray-900">{facility.doctorToPatientRatio}</p>
                                    </div>
                                </div>

                                {/* Priority Distribution */}
                                {facility.priorityDistribution && Object.keys(facility.priorityDistribution).length > 0 && (
                                    <div className="mt-3 pt-3 border-t border-gray-200">
                                        <p className="text-xs text-gray-500 mb-2">Queue Priority</p>
                                        <div className="flex gap-1">
                                            {Object.entries(facility.priorityDistribution).map(([priority, count]) => (
                                                <div key={priority} className="flex-1 text-center">
                                                    <div className={`text-xs text-white rounded ${getPriorityColor(priority)} px-1 py-0.5`}>
                                                        {count as number}
                                                    </div>
                                                    <div className="text-[8px] text-gray-500 mt-0.5">{priority}</div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {/* Departments */}
                                {facility.departments && facility.departments.length > 0 && (
                                    <div className="mt-3 pt-3 border-t border-gray-200">
                                        <p className="text-xs text-gray-500 mb-2">Departments</p>
                                        <div className="flex flex-wrap gap-1">
                                            {facility.departments.slice(0, 4).map((dept) => (
                                                <span
                                                    key={dept.code}
                                                    className={`text-xs px-2 py-0.5 rounded-full ${
                                                        dept.active ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-500'
                                                    }`}
                                                >
                                                    {dept.name}: {dept.patients || 0}
                                                </span>
                                            ))}
                                            {facility.departments.length > 4 && (
                                                <span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
                                                    +{facility.departments.length - 4} more
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Action Buttons */}
                            <div className="p-4 bg-gray-50 border-t grid grid-cols-4 gap-2">
                                <button
                                    onClick={() => navigate(`/admin/facility/${facility.id}/staff`)}
                                    className="px-2 py-1.5 bg-blue-100 text-blue-800 rounded-md text-xs hover:bg-blue-200 transition-colors"
                                >
                                    👥 Staff
                                </button>
                                <button
                                    onClick={() => navigate(`/admin/facility/${facility.id}/departments`)}
                                    className="px-2 py-1.5 bg-purple-100 text-purple-800 rounded-md text-xs hover:bg-purple-200 transition-colors"
                                >
                                    📋 Depts
                                </button>
                                <button
                                    onClick={() => {
                                        setEditingFacility(facility);
                                        setShowFacilityModal(true);
                                    }}
                                    className="px-2 py-1.5 bg-yellow-100 text-yellow-800 rounded-md text-xs hover:bg-yellow-200 transition-colors"
                                >
                                    ✏️ Edit
                                </button>
                                <button
                                    onClick={() => setShowDeleteConfirm(facility.id)}
                                    className="px-2 py-1.5 bg-red-100 text-red-800 rounded-md text-xs hover:bg-red-200 transition-colors"
                                >
                                    🗑️ Delete
                                </button>
                            </div>
                        </div>
                    ))}
                </div>

                {/* Empty State */}
                {(!telemetry?.facilities || telemetry.facilities.length === 0) && (
                    <div className="bg-white rounded-lg shadow-lg p-12 text-center">
                        <div className="text-6xl mb-4">🏥</div>
                        <h3 className="text-xl font-semibold text-gray-900">No Facilities Found</h3>
                        <p className="text-gray-500 mt-2">Get started by adding your first health center</p>
                        <button
                            onClick={() => {
                                setEditingFacility(null);
                                setShowFacilityModal(true);
                            }}
                            className="mt-4 px-6 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                        >
                            ➕ Add Facility
                        </button>
                    </div>
                )}
            </div>

            {/* Delete Confirmation Modal */}
            {showDeleteConfirm && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                        <div className="text-center">
                            <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                                <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                </svg>
                            </div>
                            <h3 className="text-lg font-semibold text-gray-900 mb-2">Delete Facility</h3>
                            <p className="text-sm text-gray-500 mb-4">
                                Are you sure you want to deactivate this facility? This action can be reversed.
                            </p>
                            <div className="flex space-x-3">
                                <button
                                    onClick={() => handleDeleteFacility(showDeleteConfirm)}
                                    className="flex-1 py-2 px-4 bg-red-600 text-white rounded-md hover:bg-red-700"
                                >
                                    Yes, Deactivate
                                </button>
                                <button
                                    onClick={() => setShowDeleteConfirm(null)}
                                    className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Facility Modal */}
            <FacilityModal
                isOpen={showFacilityModal}
                onClose={() => {
                    setShowFacilityModal(false);
                    setEditingFacility(null);
                }}
                onSuccess={loadTelemetry}
                facility={editingFacility}
            />
        </div>
    );
};