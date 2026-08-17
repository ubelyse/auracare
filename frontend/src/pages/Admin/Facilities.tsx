import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';
import { FacilityModal } from '../../components/admin/FacilityModal';

export const Facilities: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [facilities, setFacilities] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editingFacility, setEditingFacility] = useState<any>(null);

    useEffect(() => {
        if (!user) {
            navigate('/login');
            return;
        }
        loadFacilities();
    }, []);

    const loadFacilities = async () => {
        setIsLoading(true);
        try {
            const data = await adminService.getFacilities();
            setFacilities(data || []);
        } catch (error) {
            toast.error('Failed to load facilities');
        } finally {
            setIsLoading(false);
        }
    };

    const getStatusBadge = (active: boolean) => {
        return active
            ? 'bg-green-100 text-green-800'
            : 'bg-gray-100 text-gray-800';
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading facilities...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-6xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg">
                <div className="p-6 border-b border-gray-200">
                    <div className="flex justify-between items-center">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">🏥 Facilities</h1>
                            <p className="text-sm text-gray-500">Manage all health facilities</p>
                        </div>
                        <button
                            onClick={() => {
                                setEditingFacility(null);
                                setShowModal(true);
                            }}
                            className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                        >
                            + Add Facility
                        </button>
                    </div>
                    <p className="text-xs text-gray-400 mt-2">
                        Total facilities: <span className="font-semibold">{facilities.length}</span>
                    </p>
                </div>

                <div className="p-6">
                    {facilities.length === 0 ? (
                        <div className="text-center py-8">
                            <p className="text-gray-500">No facilities found</p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {facilities.map((facility) => (
                                <div
                                    key={facility.id}
                                    className="border rounded-lg p-4 hover:shadow-md transition-shadow"
                                >
                                    <div className="flex justify-between items-start">
                                        <div>
                                            <h3 className="font-semibold text-gray-900">{facility.name}</h3>
                                            <p className="text-sm text-gray-500">{facility.code}</p>
                                            {facility.address && (
                                                <p className="text-xs text-gray-400 mt-1">{facility.address}</p>
                                            )}
                                        </div>
                                        <span className={`text-xs px-2 py-1 rounded-full ${getStatusBadge(facility.active)}`}>
                                            {facility.active ? 'Active' : 'Inactive'}
                                        </span>
                                    </div>
                                    <div className="mt-3 pt-3 border-t border-gray-200 flex gap-2">
                                        <button
                                            onClick={() => {
                                                setEditingFacility(facility);
                                                setShowModal(true);
                                            }}
                                            className="px-3 py-1 bg-blue-100 text-blue-800 rounded-md text-sm hover:bg-blue-200"
                                        >
                                            ✏️ Edit
                                        </button>
                                        <button
                                            onClick={() => {
                                                if (confirm('Deactivate this facility?')) {
                                                    adminService.deleteFacility(facility.id)
                                                        .then(() => loadFacilities())
                                                        .then(() => toast.success('Facility deactivated'));
                                                }
                                            }}
                                            className="px-3 py-1 bg-red-100 text-red-800 rounded-md text-sm hover:bg-red-200"
                                        >
                                            🗑️ Delete
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            <FacilityModal
                isOpen={showModal}
                onClose={() => {
                    setShowModal(false);
                    setEditingFacility(null);
                }}
                onSuccess={loadFacilities}
                facility={editingFacility}
            />
        </div>
    );
};