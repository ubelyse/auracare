import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';

// ===== ADD: Proper type =====
interface InsuranceProvider {
    id: string;
    code: string;
    name: string;
    patientCoPayPercentage: number;
    maxCoverageAmount: number;
    active: boolean;
    contactEmail: string;
    contactPhone: string;
    requirements: string;
}

export const InsuranceProviderManagement: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [providers, setProviders] = useState<InsuranceProvider[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editingProvider, setEditingProvider] = useState<InsuranceProvider | null>(null);
    const [formData, setFormData] = useState({
        code: '',
        name: '',
        patientCoPayPercentage: 10,
        maxCoverageAmount: 0,
        active: true,
        contactEmail: '',
        contactPhone: '',
        requirements: '',
    });

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadProviders = async () => {
            if (!isMounted) return;

            setIsLoading(true);
            try {
                const data = await adminService.getInsuranceProviders();
                if (isMounted) {
                    setProviders(data || []);
                }
            } catch (error: any) {
                if (isMounted) {
                    toast.error(error.response?.data?.message || 'Failed to load insurance providers');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadProviders();

        return () => {
            isMounted = false;
        };
    }, [user, navigate]);

    const loadProviders = async () => {
        try {
            const data = await adminService.getInsuranceProviders();
            setProviders(data || []);
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Failed to load insurance providers');
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            if (editingProvider) {
                await adminService.updateInsuranceProvider(editingProvider.id, formData);
                toast.success('Insurance provider updated!');
            } else {
                await adminService.createInsuranceProvider(formData);
                toast.success('Insurance provider created!');
            }
            setShowModal(false);
            setEditingProvider(null);
            setFormData({
                code: '',
                name: '',
                patientCoPayPercentage: 10,
                maxCoverageAmount: 0,
                active: true,
                contactEmail: '',
                contactPhone: '',
                requirements: '',
            });
            loadProviders();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Operation failed');
        }
    };

    const handleEdit = (provider: InsuranceProvider) => {
        setEditingProvider(provider);
        setFormData({
            code: provider.code,
            name: provider.name,
            patientCoPayPercentage: provider.patientCoPayPercentage,
            maxCoverageAmount: provider.maxCoverageAmount || 0,
            active: provider.active,
            contactEmail: provider.contactEmail || '',
            contactPhone: provider.contactPhone || '',
            requirements: provider.requirements || '',
        });
        setShowModal(true);
    };

    const handleDeactivate = async (providerId: string) => {
        if (!confirm('Deactivate this insurance provider?')) {
            return;
        }
        try {
            await adminService.deleteInsuranceProvider(providerId);
            toast.success('Provider deactivated');
            loadProviders();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Failed to deactivate provider');
        }
    };

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
        <div className="max-w-6xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg">
                <div className="p-6 border-b border-gray-200">
                    <div className="flex justify-between items-center flex-wrap gap-4">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">🏥 Insurance Providers</h1>
                            <p className="text-sm text-gray-500">Manage insurance providers and co-pay percentages</p>
                        </div>
                        <div className="flex items-center gap-3">
                            <button
                                onClick={() => {
                                    setEditingProvider(null);
                                    setFormData({
                                        code: '',
                                        name: '',
                                        patientCoPayPercentage: 10,
                                        maxCoverageAmount: 0,
                                        active: true,
                                        contactEmail: '',
                                        contactPhone: '',
                                        requirements: '',
                                    });
                                    setShowModal(true);
                                }}
                                className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                            >
                                + Add Insurance Provider
                            </button>
                            <button
                                onClick={() => navigate('/admin/dashboard')}
                                className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                            >
                                ← Back
                            </button>
                        </div>
                    </div>
                </div>

                <div className="overflow-x-auto p-6">
                    <table className="min-w-full divide-y divide-gray-200">
                        <thead className="bg-gray-50">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Code</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Co-pay %</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                        </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                        {providers.length === 0 ? (
                            <tr>
                                <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                                    No insurance providers found
                                </td>
                            </tr>
                        ) : (
                            providers.map((provider) => (
                                <tr key={provider.id} className="hover:bg-gray-50">
                                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                                        {provider.code}
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                        {provider.name}
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                        {provider.patientCoPayPercentage}%
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap">
                                            <span className={`px-2 py-1 text-xs rounded-full ${
                                                provider.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                            }`}>
                                                {provider.active ? 'Active' : 'Inactive'}
                                            </span>
                                    </td>
                                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                                        <button
                                            onClick={() => handleEdit(provider)}
                                            className="text-blue-600 hover:text-blue-800 mr-3"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => handleDeactivate(provider.id)}
                                            className="text-red-600 hover:text-red-800"
                                        >
                                            Deactivate
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto">
                        <h2 className="text-xl font-bold text-gray-900 mb-4">
                            {editingProvider ? 'Edit Insurance Provider' : 'New Insurance Provider'}
                        </h2>
                        <form onSubmit={handleSubmit}>
                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Code *</label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.code}
                                        onChange={(e) => setFormData({...formData, code: e.target.value.toUpperCase()})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., MUTUELLE"
                                        disabled={!!editingProvider}
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Name *</label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., Mutuelle de Santé"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Patient Co-pay % *</label>
                                    <input
                                        type="number"
                                        required
                                        value={formData.patientCoPayPercentage}
                                        onChange={(e) => setFormData({...formData, patientCoPayPercentage: Number(e.target.value)})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., 10"
                                        min={0}
                                        max={100}
                                    />
                                    <p className="text-xs text-gray-400 mt-1">Percentage patient pays (e.g., 10 = 10%)</p>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Max Coverage Amount (RWF)</label>
                                    <input
                                        type="number"
                                        value={formData.maxCoverageAmount}
                                        onChange={(e) => setFormData({...formData, maxCoverageAmount: Number(e.target.value)})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., 1000000"
                                        min={0}
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Contact Email</label>
                                    <input
                                        type="email"
                                        value={formData.contactEmail}
                                        onChange={(e) => setFormData({...formData, contactEmail: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="insurance@provider.com"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Contact Phone</label>
                                    <input
                                        type="tel"
                                        value={formData.contactPhone}
                                        onChange={(e) => setFormData({...formData, contactPhone: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="+250 788 000 000"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Requirements (JSON)</label>
                                    <textarea
                                        value={formData.requirements}
                                        onChange={(e) => setFormData({...formData, requirements: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        rows={2}
                                        placeholder='{"documents": ["ID", "Insurance Card"]}'
                                    />
                                </div>
                                <div className="flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={formData.active}
                                        onChange={(e) => setFormData({...formData, active: e.target.checked})}
                                        className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                                    />
                                    <label className="ml-2 block text-sm text-gray-900">Active</label>
                                </div>
                            </div>
                            <div className="mt-6 flex space-x-3">
                                <button
                                    type="submit"
                                    className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                                >
                                    {editingProvider ? 'Update' : 'Create'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setShowModal(false);
                                        setEditingProvider(null);
                                    }}
                                    className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};