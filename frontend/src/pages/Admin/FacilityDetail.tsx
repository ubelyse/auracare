import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService, Facility, Department } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';

// ===== ADD: Proper types =====
interface FacilityDetailData extends Facility {
    activePatients: number;
    staffCount: number;
    avgWaitMinutes: number;
    departments: (Department & { patients: number })[];
}

export const FacilityDetail: React.FC = () => {
    const { facilityId } = useParams<{ facilityId: string }>();
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [facility, setFacility] = useState<FacilityDetailData | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadFacility = async () => {
            if (!facilityId) return;

            setIsLoading(true);
            try {
                const data = await adminService.getFacilityTelemetry(facilityId);
                if (isMounted) {
                    setFacility(data);
                }
            } catch (error) {
                if (isMounted) {
                    toast.error('Failed to load facility details');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadFacility();

        return () => {
            isMounted = false;
        };
    }, [facilityId, user, navigate]);

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading facility details...</p>
                </div>
            </div>
        );
    }

    if (!facility) {
        return (
            <div className="max-w-4xl mx-auto p-6">
                <div className="bg-white rounded-lg shadow-lg p-8 text-center">
                    <h2 className="text-xl font-semibold text-gray-900 mb-4">Facility Not Found</h2>
                    <button
                        onClick={() => navigate('/admin/dashboard')}
                        className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                    >
                        Back to Dashboard
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-6xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">{facility.name}</h1>
                        <p className="text-sm text-gray-500">{facility.code}</p>
                        <p className="text-xs text-gray-400 mt-1">{facility.address}</p>
                    </div>
                    <button
                        onClick={() => navigate('/admin/dashboard')}
                        className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        Back to Dashboard
                    </button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="bg-gray-50 rounded-lg p-6">
                        <p className="text-sm text-gray-500">Active Patients</p>
                        <p className="text-2xl font-bold text-gray-900">{facility.activePatients || 0}</p>
                    </div>
                    <div className="bg-gray-50 rounded-lg p-6">
                        <p className="text-sm text-gray-500">Staff Count</p>
                        <p className="text-2xl font-bold text-gray-900">{facility.staffCount || 0}</p>
                    </div>
                    <div className="bg-gray-50 rounded-lg p-6">
                        <p className="text-sm text-gray-500">Avg Wait Time</p>
                        <p className="text-2xl font-bold text-gray-900">{facility.avgWaitMinutes || 0} min</p>
                    </div>
                </div>

                <div className="mt-8">
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Departments</h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {facility.departments?.map((dept) => (
                            <div key={dept.code} className="border rounded-lg p-4">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <h4 className="font-medium text-gray-900">{dept.name}</h4>
                                        <p className="text-sm text-gray-500">{dept.code}</p>
                                    </div>
                                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                                        dept.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                    }`}>
                                        {dept.active ? 'Active' : 'Inactive'}
                                    </span>
                                </div>
                                <p className="mt-2 text-sm text-gray-600">Patients: {dept.patients || 0}</p>
                                {dept.description && (
                                    <p className="mt-1 text-xs text-gray-400">{dept.description}</p>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};