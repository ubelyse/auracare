import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

export const FacilityAdminDashboard: React.FC = () => {
    const navigate = useNavigate();
    const { user, logout } = useAuthStore();

    // ===== ADD: Redirect if no user =====
    useEffect(() => {
        if (!user) {
            navigate('/login');
            return;
        }
        // Optional: Check if user has the right role
        if (user.role !== 'FACILITY_ADMIN') {
            navigate('/login');
            return;
        }
    }, [user, navigate]);

    const handleLogout = async () => {
        await logout();
        navigate('/login');
    };

    // ===== ADD: Show message if no facilityId =====
    if (!user?.facilityId) {
        return (
            <div className="max-w-7xl mx-auto p-6">
                <div className="bg-white rounded-lg shadow-lg p-8 text-center">
                    <div className="text-4xl mb-4">⚠️</div>
                    <h2 className="text-xl font-semibold text-gray-900">No Facility Assigned</h2>
                    <p className="text-gray-500 mt-2">
                        You don't have a facility assigned to your account.
                        Please contact your district admin.
                    </p>
                    <button
                        onClick={handleLogout}
                        className="mt-4 px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
                    >
                        Logout
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-7xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">
                            🏢 Facility Admin Dashboard
                        </h1>
                        <p className="text-sm text-gray-500">
                            {user?.facilityName || 'Facility'} - Manage your facility
                        </p>
                    </div>
                    <div className="flex gap-2">
                        {/* ===== ADD: Back to Dashboard button ===== */}
                        <button
                            onClick={() => navigate('/admin/dashboard')}
                            className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
                        >
                            ← Back
                        </button>
                        <button
                            onClick={handleLogout}
                            className="px-4 py-2 border border-red-300 rounded-md text-sm font-medium text-red-600 hover:bg-red-50"
                        >
                            Logout
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-6">
                    {/* Staff Management */}
                    <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                        <div className="flex items-start justify-between">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">Staff Management</h3>
                                <p className="text-sm text-gray-500 mt-1">Manage facility staff</p>
                            </div>
                            <span className="text-3xl">👥</span>
                        </div>
                        <button
                            onClick={() => navigate(`/admin/facility/${user.facilityId}/staff`)}
                            className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                        >
                            Manage Staff
                        </button>
                    </div>

                    {/* Department Management */}
                    <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                        <div className="flex items-start justify-between">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">Departments</h3>
                                <p className="text-sm text-gray-500 mt-1">Manage departments</p>
                            </div>
                            <span className="text-3xl">📋</span>
                        </div>
                        <button
                            onClick={() => navigate(`/admin/facility/${user.facilityId}/departments`)}
                            className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                        >
                            Manage Departments
                        </button>
                    </div>

                    {/* Financial */}
                    <div className="border rounded-lg p-6 hover:shadow-md transition-shadow">
                        <div className="flex items-start justify-between">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">Financial</h3>
                                <p className="text-sm text-gray-500 mt-1">View facility revenue</p>
                            </div>
                            <span className="text-3xl">💰</span>
                        </div>
                        <button
                            onClick={() => navigate('/admin/financial')}
                            className="mt-4 w-full py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                        >
                            View Financials
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};