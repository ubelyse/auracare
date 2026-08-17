import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';

interface ServicePricing {
    id: string;
    serviceCode: string;
    serviceName: string;
    category: string;
    basePrice: number;
    mutuellePrice: number;    // Patient pays 10%
    rssbPrice: number;        // Patient pays 15%
    mmiPrice: number;         // Patient pays 15%
    description: string;
    active: boolean;
    facilityId?: string | null;
}

// ===== CO-PAY PERCENTAGES (Patient pays these percentages) =====
const CO_PAY_PERCENTAGES = {
    MUTUELLE: 10,   // Patient pays 10%
    RSSB: 15,       // Patient pays 15%
    MMI: 15,        // Patient pays 15%
    UNINSURED: 100, // Patient pays 100%
};

export const ServicePricingManagement: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [pricings, setPricings] = useState<ServicePricing[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editingPricing, setEditingPricing] = useState<ServicePricing | null>(null);
    const [formData, setFormData] = useState({
        serviceCode: '',
        serviceName: '',
        category: 'CONSULTATION',
        basePrice: 0,
        mutuellePrice: 0,    // Patient pays 10% of base price
        rssbPrice: 0,        // Patient pays 15% of base price
        mmiPrice: 0,         // Patient pays 15% of base price
        description: '',
        active: true,
        facilityId: null as string | null,
    });

    const categories = [
        'CONSULTATION',
        'LAB',
        'PROCEDURE',
        'MEDICATION',
        'IMAGING',
        'SURGERY',
        'OTHER'
    ];

    // ===== AUTO-CALCULATE PATIENT PAYMENTS (Co-pay) =====
    const calculatePatientPayments = (basePrice: number) => {
        const mutuellePrice = Math.round(basePrice * (CO_PAY_PERCENTAGES.MUTUELLE / 100));
        const rssbPrice = Math.round(basePrice * (CO_PAY_PERCENTAGES.RSSB / 100));
        const mmiPrice = Math.round(basePrice * (CO_PAY_PERCENTAGES.MMI / 100));
        return { mutuellePrice, rssbPrice, mmiPrice };
    };

    // ===== When Base Price changes, auto-calculate patient payments =====
    const handleBasePriceChange = (value: number) => {
        const { mutuellePrice, rssbPrice, mmiPrice } = calculatePatientPayments(value);
        setFormData({
            ...formData,
            basePrice: value,
            mutuellePrice,
            rssbPrice,
            mmiPrice,
        });
    };

    // ===== When editing, set the form data =====
    const handleEdit = (pricing: ServicePricing) => {
        setEditingPricing(pricing);
        setFormData({
            serviceCode: pricing.serviceCode,
            serviceName: pricing.serviceName,
            category: pricing.category,
            basePrice: pricing.basePrice,
            mutuellePrice: pricing.mutuellePrice || 0,
            rssbPrice: pricing.rssbPrice || 0,
            mmiPrice: pricing.mmiPrice || 0,
            description: pricing.description || '',
            active: pricing.active,
            facilityId: pricing.facilityId || null,
        });
        setShowModal(true);
    };

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadPricings = async () => {
            if (!isMounted) return;

            setIsLoading(true);
            try {
                const data = await adminService.getServicePricing();
                if (isMounted) {
                    setPricings(data || []);
                }
            } catch (error: any) {
                if (isMounted) {
                    toast.error(error.response?.data?.message || 'Failed to load service pricing');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadPricings();

        return () => {
            isMounted = false;
        };
    }, [user, navigate]);

    const loadPricings = async () => {
        try {
            const data = await adminService.getServicePricing();
            setPricings(data || []);
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Failed to load service pricing');
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            if (editingPricing) {
                await adminService.updateServicePricing(editingPricing.id, formData);
                toast.success('Service pricing updated!');
            } else {
                await adminService.createServicePricing(formData);
                toast.success('Service pricing created!');
            }
            setShowModal(false);
            setEditingPricing(null);
            setFormData({
                serviceCode: '',
                serviceName: '',
                category: 'CONSULTATION',
                basePrice: 0,
                mutuellePrice: 0,
                rssbPrice: 0,
                mmiPrice: 0,
                description: '',
                active: true,
                facilityId: null,
            });
            loadPricings();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Operation failed');
        }
    };

    const handleDeactivate = async (pricingId: string) => {
        if (!confirm('Deactivate this service pricing?')) {
            return;
        }
        try {
            await adminService.deleteServicePricing(pricingId);
            toast.success('Service deactivated');
            loadPricings();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Failed to deactivate service');
        }
    };

    const getCategoryBadge = (category: string) => {
        const colors: Record<string, string> = {
            CONSULTATION: 'bg-blue-100 text-blue-800',
            LAB: 'bg-purple-100 text-purple-800',
            PROCEDURE: 'bg-orange-100 text-orange-800',
            MEDICATION: 'bg-green-100 text-green-800',
            IMAGING: 'bg-pink-100 text-pink-800',
            SURGERY: 'bg-red-100 text-red-800',
            OTHER: 'bg-gray-100 text-gray-800',
        };
        return colors[category] || 'bg-gray-100 text-gray-800';
    };

    const formatPrice = (price: number) => {
        return price.toLocaleString('rw-RW');
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading service pricing...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-7xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg">
                <div className="p-6 border-b border-gray-200">
                    <div className="flex justify-between items-center flex-wrap gap-4">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">💰 Service Pricing</h1>
                            <p className="text-sm text-gray-500">Manage service prices and patient co-pay amounts</p>
                        </div>
                        <div className="flex items-center gap-3">
                            <button
                                onClick={() => {
                                    setEditingPricing(null);
                                    setFormData({
                                        serviceCode: '',
                                        serviceName: '',
                                        category: 'CONSULTATION',
                                        basePrice: 0,
                                        mutuellePrice: 0,
                                        rssbPrice: 0,
                                        mmiPrice: 0,
                                        description: '',
                                        active: true,
                                        facilityId: null,
                                    });
                                    setShowModal(true);
                                }}
                                className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                            >
                                + Add Service
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
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Code</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Service</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Category</th>
                            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Base Price</th>
                            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Mutuelle (10%)</th>
                            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">RSSB (15%)</th>
                            <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">MMI (15%)</th>
                            <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                            <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                        </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                        {pricings.length === 0 ? (
                            <tr>
                                <td colSpan={9} className="px-6 py-8 text-center text-gray-500">
                                    No service pricing found
                                </td>
                            </tr>
                        ) : (
                            pricings.map((pricing) => (
                                <tr key={pricing.id} className="hover:bg-gray-50">
                                    <td className="px-4 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                                        {pricing.serviceCode}
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-900">
                                        {pricing.serviceName}
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap">
                                        <span className={`text-xs px-2 py-1 rounded-full ${getCategoryBadge(pricing.category)}`}>
                                            {pricing.category}
                                        </span>
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-gray-900">
                                        {formatPrice(pricing.basePrice)} RWF
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-green-600">
                                        {pricing.mutuellePrice ? formatPrice(pricing.mutuellePrice) : '-'}
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-blue-600">
                                        {pricing.rssbPrice ? formatPrice(pricing.rssbPrice) : '-'}
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-sm text-right text-purple-600">
                                        {pricing.mmiPrice ? formatPrice(pricing.mmiPrice) : '-'}
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-center">
                                        <span className={`px-2 py-1 text-xs rounded-full ${
                                            pricing.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                        }`}>
                                            {pricing.active ? 'Active' : 'Inactive'}
                                        </span>
                                    </td>
                                    <td className="px-4 py-4 whitespace-nowrap text-center text-sm">
                                        <button
                                            onClick={() => handleEdit(pricing)}
                                            className="text-blue-600 hover:text-blue-800 mr-3"
                                        >
                                            Edit
                                        </button>
                                        <button
                                            onClick={() => handleDeactivate(pricing.id)}
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

                <div className="px-6 py-3 border-t border-gray-200 bg-gray-50 rounded-b-lg">
                    <div className="text-sm text-gray-500">
                        Total services: <span className="font-semibold">{pricings.length}</span>
                    </div>
                </div>
            </div>

            {/* Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full p-6 max-h-[90vh] overflow-y-auto">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-xl font-bold text-gray-900">
                                {editingPricing ? 'Edit Service Pricing' : 'New Service Pricing'}
                            </h2>
                            <button
                                onClick={() => {
                                    setShowModal(false);
                                    setEditingPricing(null);
                                }}
                                className="text-gray-400 hover:text-gray-600"
                            >
                                ✕
                            </button>
                        </div>

                        <form onSubmit={handleSubmit}>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Service Code *</label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.serviceCode}
                                        onChange={(e) => setFormData({...formData, serviceCode: e.target.value.toUpperCase()})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., CONS-001"
                                        disabled={!!editingPricing}
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Service Name *</label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.serviceName}
                                        onChange={(e) => setFormData({...formData, serviceName: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., General Consultation"
                                    />
                                </div>
                            </div>

                            <div className="mt-4">
                                <label className="block text-sm font-medium text-gray-700">Category *</label>
                                <select
                                    required
                                    value={formData.category}
                                    onChange={(e) => setFormData({...formData, category: e.target.value})}
                                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                >
                                    {categories.map((cat) => (
                                        <option key={cat} value={cat}>{cat}</option>
                                    ))}
                                </select>
                            </div>

                            {/* ===== ONLY BASE PRICE IS EDITABLE ===== */}
                            <div className="mt-4">
                                <label className="block text-sm font-medium text-gray-700">
                                    Base Price (RWF) *
                                    <span className="text-xs text-gray-500 ml-2">(Patient co-pay amounts auto-calculate)</span>
                                </label>
                                <input
                                    type="number"
                                    required
                                    value={formData.basePrice}
                                    onChange={(e) => handleBasePriceChange(Number(e.target.value))}
                                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    placeholder="e.g., 15000"
                                    min={0}
                                />
                            </div>

                            {/* ===== AUTO-CALCULATED PATIENT PAYMENTS (READ-ONLY) ===== */}
                            <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-3">
                                <div className="bg-green-50 p-3 rounded-md border border-green-200">
                                    <label className="block text-xs font-medium text-green-700">Mutuelle (10%)</label>
                                    <div className="text-lg font-semibold text-green-800">
                                        {formatPrice(formData.mutuellePrice)} RWF
                                    </div>
                                    <p className="text-xs text-green-600">Patient pays 10%</p>
                                    <input type="hidden" value={formData.mutuellePrice} name="mutuellePrice" />
                                </div>

                                <div className="bg-blue-50 p-3 rounded-md border border-blue-200">
                                    <label className="block text-xs font-medium text-blue-700">RSSB (15%)</label>
                                    <div className="text-lg font-semibold text-blue-800">
                                        {formatPrice(formData.rssbPrice)} RWF
                                    </div>
                                    <p className="text-xs text-blue-600">Patient pays 15%</p>
                                    <input type="hidden" value={formData.rssbPrice} name="rssbPrice" />
                                </div>

                                <div className="bg-purple-50 p-3 rounded-md border border-purple-200">
                                    <label className="block text-xs font-medium text-purple-700">MMI (15%)</label>
                                    <div className="text-lg font-semibold text-purple-800">
                                        {formatPrice(formData.mmiPrice)} RWF
                                    </div>
                                    <p className="text-xs text-purple-600">Patient pays 15%</p>
                                    <input type="hidden" value={formData.mmiPrice} name="mmiPrice" />
                                </div>
                            </div>

                            <div className="mt-4 p-3 bg-gray-50 rounded-md border border-gray-200">
                                <p className="text-sm text-gray-600">
                                    💡 <span className="font-medium">Uninsured patients:</span> Pay 100% = {formatPrice(formData.basePrice)} RWF
                                </p>
                            </div>

                            <div className="mt-4">
                                <label className="block text-sm font-medium text-gray-700">Description</label>
                                <textarea
                                    value={formData.description}
                                    onChange={(e) => setFormData({...formData, description: e.target.value})}
                                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    rows={2}
                                    placeholder="Brief description of the service"
                                />
                            </div>

                            <div className="mt-4 flex items-center space-x-4">
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

                            {editingPricing && (
                                <div className="mt-4 p-3 bg-blue-50 rounded-md border border-blue-200">
                                    <p className="text-sm text-blue-700">
                                        Editing: {editingPricing.serviceCode} - {editingPricing.serviceName}
                                    </p>
                                </div>
                            )}

                            <div className="mt-6 flex space-x-3">
                                <button
                                    type="submit"
                                    className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                                >
                                    {editingPricing ? 'Update' : 'Create'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setShowModal(false);
                                        setEditingPricing(null);
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