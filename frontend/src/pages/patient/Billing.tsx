import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { billingService } from '../../services/billing';
import { useAuthStore } from '../../stores/authStore';
import { Billing } from '../../types/billing';

// ===== ADD: Interface for bill items =====
interface BillItem {
    description?: string;
    serviceCode?: string;
    serviceName?: string;
    amount: number;
    originalPrice?: number;
    insuranceType?: string;
}

export const PatientBilling: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [bills, setBills] = useState<Billing[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [selectedBill, setSelectedBill] = useState<Billing | null>(null);
    const [paymentMethod, setPaymentMethod] = useState('CASH');
    const [isProcessing, setIsProcessing] = useState(false);

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadBills = async () => {
            if (!isMounted) return;

            setIsLoading(true);
            try {
                const data = await billingService.getPatientBills();
                if (isMounted) {
                    setBills(data || []);
                }
            } catch (error: any) {
                if (isMounted) {
                    toast.error(error.response?.data?.message || 'Failed to load bills');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadBills();

        return () => {
            isMounted = false;
        };
    }, [user, navigate]);

    // ===== FIXED: Make function async and return Promise =====
    const loadBills = async (): Promise<void> => {
        setIsLoading(true);
        try {
            const data = await billingService.getPatientBills();
            setBills(data || []);
        } catch (error: any) {
            toast.error('Failed to load bills');
            setBills([]);
        } finally {
            setIsLoading(false);
        }
    };

    const parseItems = (items: any): BillItem[] => {
        if (!items) return [];
        if (Array.isArray(items)) return items as BillItem[];
        if (typeof items === 'string') {
            try {
                const parsed = JSON.parse(items);
                return Array.isArray(parsed) ? parsed as BillItem[] : [];
            } catch (e) {
                return [];
            }
        }
        return [];
    };

    const handlePayment = async (billingId: string): Promise<void> => {
        setIsProcessing(true);
        try {
            const result = await billingService.simulatePayment(billingId, paymentMethod);

            if (result.success) {
                toast.success('Payment processed successfully!');
                await loadBills(); // ← FIXED: Added await
                setSelectedBill(null);
            } else {
                toast.error(result.message || 'Payment failed');
            }
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Payment processing failed');
        } finally {
            setIsProcessing(false);
        }
    };

    const getStatusBadge = (status: string): string => {
        const colors: Record<string, string> = {
            PENDING: 'bg-yellow-100 text-yellow-800',
            PAID: 'bg-green-100 text-green-800',
            OVERDUE: 'bg-red-100 text-red-800',
            CANCELLED: 'bg-gray-100 text-gray-800',
            REFUNDED: 'bg-blue-100 text-blue-800'
        };
        return colors[status] || 'bg-gray-100 text-gray-800';
    };

    const getStatusIcon = (status: string): string => {
        const icons: Record<string, string> = {
            PENDING: '⏳',
            PAID: '✅',
            OVERDUE: '⚠️',
            CANCELLED: '❌',
            REFUNDED: '🔄'
        };
        return icons[status] || '📋';
    };

    const formatPrice = (amount: number): string => {
        return (amount || 0).toLocaleString('rw-RW');
    };

    // ===== HELPER: Check if patient has insurance =====
    const hasInsurance = (insuranceType: string | undefined): boolean => {
        return !!insuranceType && insuranceType !== 'UNINSURED' && insuranceType !== 'UNKNOWN';
    };

    // ===== HELPER: Get insurance display name =====
    const getInsuranceDisplay = (insuranceType: string | undefined): string => {
        if (!insuranceType || insuranceType === 'UNINSURED' || insuranceType === 'UNKNOWN') {
            return 'None';
        }
        const insuranceMap: Record<string, string> = {
            'MUTUELLE': 'Mutuelle de Sante',
            'RSSB': 'RSSB',
            'MMI': 'MMI',
            'PRIVATE': 'Private Insurance',
            'GOVERNMENT': 'Government Insurance'
        };
        return insuranceMap[insuranceType] || insuranceType;
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading bills...</p>
                </div>
            </div>
        );
    }

    const pendingBills = Array.isArray(bills) ? bills.filter(b => b.status === 'PENDING' || b.status === 'OVERDUE') : [];
    const paidBills = Array.isArray(bills) ? bills.filter(b => b.status === 'PAID') : [];

    return (
        <div className="max-w-4xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">💰 My Bills</h1>
                        <p className="text-sm text-gray-500">
                            View and pay your medical bills
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        <div className="text-sm text-gray-500">
                            {pendingBills.length} pending bill{pendingBills.length !== 1 ? 's' : ''}
                        </div>
                        <button
                            onClick={() => navigate('/patient/dashboard')}
                            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            ← Back
                        </button>
                    </div>
                </div>

                {pendingBills.length > 0 && (
                    <div className="mb-8">
                        <h2 className="text-lg font-semibold text-gray-900 mb-4">⏳ Pending Bills</h2>
                        <div className="space-y-4">
                            {pendingBills.map((bill) => {
                                const parsedItems = parseItems(bill.items);
                                const hasInsuranceType = hasInsurance(bill.insuranceType);

                                return (
                                    <div
                                        key={bill.id}
                                        className="border rounded-lg p-4 hover:shadow-md transition-shadow bg-yellow-50"
                                    >
                                        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
                                            <div className="flex items-start space-x-3">
                                                <span className="text-2xl">{getStatusIcon(bill.status)}</span>
                                                <div>
                                                    <div className="flex items-center space-x-2">
                                                        <span className="font-medium text-gray-900">
                                                            #{bill.invoiceNumber}
                                                        </span>
                                                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getStatusBadge(bill.status)}`}>
                                                            {bill.status}
                                                        </span>
                                                    </div>
                                                    <p className="text-sm text-gray-600">
                                                        Issued: {bill.issuedAt ? new Date(bill.issuedAt).toLocaleDateString() : 'N/A'}
                                                    </p>
                                                    <p className="text-sm text-gray-600">
                                                        Insurance: {getInsuranceDisplay(bill.insuranceType)}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="mt-2 md:mt-0 text-right">
                                                {/* ===== SHOW CORRECT AMOUNT ===== */}
                                                {hasInsuranceType ? (
                                                    <>
                                                        <p className="text-sm text-gray-400 line-through">
                                                            {formatPrice(bill.totalAmount)} RWF
                                                        </p>
                                                        <p className="text-lg font-bold text-green-600">
                                                            {formatPrice(bill.patientAmount)} RWF
                                                        </p>
                                                        <p className="text-xs text-green-600">
                                                            ✅ Insurance covers {formatPrice(bill.totalAmount - bill.patientAmount)} RWF
                                                        </p>
                                                    </>
                                                ) : (
                                                    <p className="text-lg font-bold text-gray-900">
                                                        {formatPrice(bill.patientAmount)} RWF
                                                    </p>
                                                )}
                                                <button
                                                    onClick={() => setSelectedBill(bill)}
                                                    className="mt-2 px-4 py-2 bg-primary-600 text-white rounded-md text-sm hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                                >
                                                    💳 Pay Now
                                                </button>
                                            </div>
                                        </div>

                                        {/* Bill Items Breakdown */}
                                        {parsedItems.length > 0 && (
                                            <div className="mt-3 pt-3 border-t border-gray-200">
                                                <p className="text-xs font-medium text-gray-500 mb-2">Services:</p>
                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-1">
                                                    {parsedItems.map((item, index) => (
                                                        <div key={index} className="flex justify-between text-sm">
                                                            <span className="text-gray-600">
                                                                {item.serviceName || item.description || item.serviceCode || 'Service'}
                                                            </span>
                                                            <span className="font-medium">
                                                                {hasInsuranceType && item.originalPrice ? (
                                                                    <>
                                                                        <span className="text-gray-400 line-through mr-1">
                                                                            {formatPrice(item.originalPrice)}
                                                                        </span>
                                                                        <span className="text-green-600">
                                                                            {formatPrice(item.amount)}
                                                                        </span>
                                                                    </>
                                                                ) : (
                                                                    formatPrice(item.amount)
                                                                )}
                                                                RWF
                                                            </span>
                                                        </div>
                                                    ))}
                                                </div>
                                                {hasInsuranceType && (
                                                    <p className="text-xs text-gray-400 mt-1">
                                                        * Prices shown with {getInsuranceDisplay(bill.insuranceType)} co-pay
                                                    </p>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {paidBills.length > 0 && (
                    <div>
                        <h2 className="text-lg font-semibold text-gray-900 mb-4">✅ Paid Bills</h2>
                        <div className="space-y-4">
                            {paidBills.map((bill) => (
                                <div
                                    key={bill.id}
                                    className="border rounded-lg p-4 hover:shadow-md transition-shadow bg-gray-50"
                                >
                                    <div className="flex flex-col md:flex-row md:items-center md:justify-between">
                                        <div className="flex items-start space-x-3">
                                            <span className="text-2xl">{getStatusIcon(bill.status)}</span>
                                            <div>
                                                <div className="flex items-center space-x-2">
                                                    <span className="font-medium text-gray-900">
                                                        #{bill.invoiceNumber}
                                                    </span>
                                                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${getStatusBadge(bill.status)}`}>
                                                        {bill.status}
                                                    </span>
                                                </div>
                                                <p className="text-sm text-gray-600">
                                                    Paid: {bill.paidAt ? new Date(bill.paidAt).toLocaleDateString() : 'N/A'}
                                                </p>
                                                <p className="text-sm text-gray-600">
                                                    Method: {bill.paymentMethod || 'N/A'}
                                                </p>
                                                <p className="text-sm text-gray-600">
                                                    Insurance: {getInsuranceDisplay(bill.insuranceType)}
                                                </p>
                                            </div>
                                        </div>
                                        <div className="mt-2 md:mt-0 text-right">
                                            <p className="text-lg font-bold text-gray-900">
                                                {formatPrice(bill.patientAmount)} RWF
                                            </p>
                                            <p className="text-xs text-gray-400">
                                                Total: {formatPrice(bill.totalAmount)} RWF
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {bills.length === 0 && (
                    <div className="text-center py-8">
                        <div className="text-6xl mb-4">💰</div>
                        <h3 className="text-lg font-semibold text-gray-900">No Bills Found</h3>
                        <p className="text-gray-500 mt-2">You don't have any bills yet.</p>
                    </div>
                )}
            </div>

            {/* Payment Modal */}
            {selectedBill && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h3 className="text-lg font-semibold text-gray-900">
                                    💳 Payment for #{selectedBill.invoiceNumber}
                                </h3>
                                <p className="text-sm text-gray-500">
                                    Amount due: {formatPrice(selectedBill.patientAmount)} RWF
                                </p>
                                {hasInsurance(selectedBill.insuranceType) && (
                                    <p className="text-xs text-green-600">
                                        ✅ {getInsuranceDisplay(selectedBill.insuranceType)} discount applied
                                    </p>
                                )}
                            </div>
                            <button
                                onClick={() => setSelectedBill(null)}
                                className="text-gray-400 hover:text-gray-500"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700">
                                    Payment Method
                                </label>
                                <select
                                    value={paymentMethod}
                                    onChange={(e) => setPaymentMethod(e.target.value)}
                                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                >
                                    <option value="CASH">Cash</option>
                                    <option value="MOBILE_MONEY">Mobile Money</option>
                                    <option value="CARD">Card</option>
                                    <option value="BANK_TRANSFER">Bank Transfer</option>
                                </select>
                            </div>

                            {/* ===== PAYMENT BREAKDOWN ===== */}
                            <div className="bg-gray-50 rounded-lg p-4 space-y-2">
                                <div className="flex justify-between text-sm">
                                    <span className="text-gray-600">Subtotal</span>
                                    <span>{formatPrice(selectedBill.totalAmount)} RWF</span>
                                </div>
                                {hasInsurance(selectedBill.insuranceType) && (
                                    <>
                                        <div className="flex justify-between text-sm">
                                            <span className="text-gray-600">Insurance Coverage</span>
                                            <span className="text-green-600">
                                                - {formatPrice(selectedBill.totalAmount - selectedBill.patientAmount)} RWF
                                            </span>
                                        </div>
                                        <div className="flex justify-between text-sm">
                                            <span className="text-gray-600">Co-pay ({getInsuranceDisplay(selectedBill.insuranceType)})</span>
                                            <span className="text-blue-600">
                                                {formatPrice(selectedBill.patientAmount)} RWF
                                            </span>
                                        </div>
                                    </>
                                )}
                                <div className="border-t border-gray-200 pt-2 flex justify-between font-bold">
                                    <span>Total Due</span>
                                    <span className="text-primary-600">{formatPrice(selectedBill.patientAmount)} RWF</span>
                                </div>
                            </div>

                            <div className="flex space-x-3">
                                <button
                                    onClick={() => handlePayment(selectedBill.id)}
                                    disabled={isProcessing}
                                    className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50"
                                >
                                    {isProcessing ? 'Processing...' : '💳 Pay Now'}
                                </button>
                                <button
                                    onClick={() => setSelectedBill(null)}
                                    className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                            </div>

                            <div className="text-xs text-gray-500 text-center">
                                Simulated payment processing for demo/testing purposes.
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};