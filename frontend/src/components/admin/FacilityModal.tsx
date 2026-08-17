import React, { useState, useRef } from 'react';
import { adminService, Facility } from '../../services/admin';
import toast from 'react-hot-toast';

interface FacilityModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
    facility?: Facility;
}

export const FacilityModal: React.FC<FacilityModalProps> = ({
                                                                isOpen,
                                                                onClose,
                                                                onSuccess,
                                                                facility
                                                            }) => {
    const [isLoading, setIsLoading] = useState(false);
    const isMounted = useRef(true);

    const [formData, setFormData] = useState({
        name: facility?.name || '',
        code: facility?.code || '',
        address: facility?.address || '',
        phone: facility?.phone || '',
        email: facility?.email || '',
        isActive: facility?.isActive !== undefined ? facility.isActive : true
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);

        try {
            if (facility) {
                await adminService.updateFacility(facility.id, formData);
                toast.success('Facility updated successfully!');
            } else {
                await adminService.createFacility(formData);
                toast.success('Facility created successfully!');
            }

            // Reset form after successful creation
            if (!facility) {
                setFormData({
                    name: '',
                    code: '',
                    address: '',
                    phone: '',
                    email: '',
                    isActive: true
                });
            }

            onSuccess();
            onClose();

        } catch (error: any) {
            toast.error(error.response?.data?.message || error.message || 'Operation failed');
        } finally {
            if (isMounted.current) {
                setIsLoading(false);
            }
        }
    };

    const handleClose = () => {
        setFormData({
            name: facility?.name || '',
            code: facility?.code || '',
            address: facility?.address || '',
            phone: facility?.phone || '',
            email: facility?.email || '',
            isActive: facility?.isActive !== undefined ? facility.isActive : true
        });
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto">
                <div className="flex justify-between items-center mb-4">
                    <h2 className="text-xl font-bold text-gray-900">
                        {facility ? '✏️ Edit Facility' : '🏥 New Facility'}
                    </h2>
                    <button
                        type="button"
                        onClick={handleClose}
                        className="text-gray-400 hover:text-gray-600"
                    >
                        ✕
                    </button>
                </div>

                <form onSubmit={handleSubmit} noValidate>
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700">
                                Facility Name <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={formData.name}
                                onChange={(e) => setFormData({...formData, name: e.target.value})}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="e.g., Remera Health Center"
                                disabled={isLoading}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700">
                                Facility Code <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="text"
                                required
                                value={formData.code}
                                onChange={(e) => setFormData({...formData, code: e.target.value.toUpperCase()})}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="e.g., RHC"
                                maxLength={10}
                                disabled={isLoading}
                            />
                            <p className="text-xs text-gray-400 mt-1">Unique identifier for the facility</p>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700">Address</label>
                            <input
                                type="text"
                                value={formData.address}
                                onChange={(e) => setFormData({...formData, address: e.target.value})}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="e.g., Remera, Kigali"
                                disabled={isLoading}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700">Phone</label>
                            <input
                                type="tel"
                                value={formData.phone}
                                onChange={(e) => setFormData({...formData, phone: e.target.value})}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="e.g., +250788000000"
                                disabled={isLoading}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700">Email</label>
                            <input
                                type="email"
                                value={formData.email}
                                onChange={(e) => setFormData({...formData, email: e.target.value})}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="e.g., facility@mvura.rw"
                                disabled={isLoading}
                            />
                        </div>

                        <div className="flex items-center">
                            <input
                                type="checkbox"
                                checked={formData.isActive}
                                onChange={(e) => setFormData({...formData, isActive: e.target.checked})}
                                className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                                disabled={isLoading}
                            />
                            <label className="ml-2 block text-sm text-gray-900">Active</label>
                        </div>
                    </div>

                    <div className="mt-6 flex space-x-3">
                        <button
                            type="submit"
                            disabled={isLoading}
                            className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {isLoading ? 'Saving...' : facility ? 'Update' : 'Create'}
                        </button>
                        <button
                            type="button"
                            onClick={handleClose}
                            disabled={isLoading}
                            className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
                        >
                            Cancel
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};