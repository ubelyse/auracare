import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { billingService } from '../../services/billing';
import { useAuthStore } from '../../stores/authStore';

// ===== Types =====
interface FinancialSummary {
    monthRevenue: number;
    todayRevenue: number;
    pendingBills: number;
    overdueBills: number;
    currency: string;
}

interface ClaimSummary {
    insuranceType: string;
    totalAmount: number;
}

interface ReportData {
    period: { start: string; end: string };
    totalBills: number;
    totalRevenue: number;
    totalPaid: number;
    totalPending: number;
    totalCancelled: number;
    byInsurance: Record<string, number>;
    bills: Array<{
        invoiceNumber: string;
        totalAmount: number;
        patientAmount: number;
        status: string;
        issuedAt: string;
        insuranceType: string;
    }>;
    currency: string;
}

interface RevenueAnalysis {
    period: string;
    facilityId: string | null;
    facilityName: string;
    totalRevenue: number;
    totalPaid: number;
    pendingCount: number;
    dailyRevenue: Record<string, number>;
    dailyPaid: Record<string, number>;
    currency: string;
}

// ===== Simplified Audit Log type =====
interface AuditLog {
    id: string;
    action: string;
    username?: string;
    actorUsername?: string;
    createdAt: string;
}

export const FinancialDashboard: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [activeTab, setActiveTab] = useState<'overview' | 'reports' | 'revenue' | 'claims' | 'audit'>('overview');

    const [summary, setSummary] = useState<FinancialSummary | null>(null);
    const [claims, setClaims] = useState<ClaimSummary[]>([]);
    const [report, setReport] = useState<ReportData | null>(null);
    const [revenue, setRevenue] = useState<RevenueAnalysis | null>(null);
    const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
    const [auditLoading, setAuditLoading] = useState(false);
    const [auditFilters, setAuditFilters] = useState({
        action: '',
        username: '',
        startDate: '',
        endDate: ''
    });

    const [isLoading, setIsLoading] = useState(true);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [period, setPeriod] = useState('month');

    // ===== Load Data on Mount =====
    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadData = async () => {
            if (!isMounted) return;

            setIsLoading(true);
            try {
                // Load facility summary if facilityId exists
                if (user?.facilityId) {
                    try {
                        const summaryData = await billingService.getFacilitySummary(user.facilityId);
                        if (isMounted) {
                            setSummary(summaryData);
                        }
                    } catch (summaryError) {
                        console.warn('Could not load facility summary:', summaryError);
                        if (isMounted) {
                            setSummary({
                                monthRevenue: 0,
                                todayRevenue: 0,
                                pendingBills: 0,
                                overdueBills: 0,
                                currency: 'RWF'
                            });
                        }
                    }
                }

                // Load insurance claims only for DISTRICT_ADMIN
                if (user?.role === 'DISTRICT_ADMIN') {
                    try {
                        const claimsData = await billingService.getInsuranceClaims();
                        if (isMounted) {
                            setClaims(claimsData || []);
                        }
                    } catch (claimsError: any) {
                        console.warn('Could not load insurance claims:', claimsError);
                        if (isMounted) {
                            if (claimsError.response?.status === 403) {
                                toast.error('Access denied. Admin privileges required for insurance claims.');
                            }
                            setClaims([]);
                        }
                    }
                }
            } catch (error: any) {
                if (isMounted) {
                    console.error('Failed to load financial data:', error);
                    toast.error('Failed to load financial data');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadData();

        return () => {
            isMounted = false;
        };
    }, [user, navigate]);

    // ===== Load data when tab changes =====
    useEffect(() => {
        if (activeTab === 'reports') {
            loadReport();
        }
        if (activeTab === 'revenue') {
            loadRevenue();
        }
        if (activeTab === 'audit') {
            loadAuditLogs();
        }
    }, [activeTab]);

    // ===== Load Report =====
    const loadReport = async () => {
        setIsLoading(true);
        try {
            const data = await billingService.generateReport(startDate, endDate);
            setReport(data);
        } catch (error) {
            toast.error('Failed to load report');
            console.error(error);
        } finally {
            setIsLoading(false);
        }
    };

    // ===== Load Revenue =====
    const loadRevenue = async () => {
        setIsLoading(true);
        try {
            const data = await billingService.getRevenueAnalysis(period);
            setRevenue(data);
        } catch (error) {
            toast.error('Failed to load revenue analysis');
            console.error(error);
        } finally {
            setIsLoading(false);
        }
    };

    // ===== Load Audit Logs =====
    const loadAuditLogs = async () => {
        setAuditLoading(true);
        try {
            const data = await billingService.getAuditLogs(
                auditFilters.action,
                auditFilters.username,
                auditFilters.startDate,
                auditFilters.endDate
            );
            setAuditLogs(data.logs || []);
        } catch (error) {
            toast.error('Failed to load audit logs');
            console.error(error);
        } finally {
            setAuditLoading(false);
        }
    };

    // ===== Helper Functions =====
    const getTotalClaimsAmount = () => {
        return claims.reduce((sum, claim) => sum + (claim.totalAmount || 0), 0);
    };

    const getClaimPercentage = (amount: number) => {
        const total = getTotalClaimsAmount();
        return total > 0 ? ((amount / total) * 100).toFixed(1) : '0';
    };

    const getActionColor = (action: string) => {
        const colors: Record<string, string> = {
            'FACILITY_CREATED': 'bg-green-100 text-green-800',
            'FACILITY_UPDATED': 'bg-blue-100 text-blue-800',
            'FACILITY_DEACTIVATED': 'bg-red-100 text-red-800',
            'DEPARTMENT_CREATED': 'bg-green-100 text-green-800',
            'DEPARTMENT_UPDATED': 'bg-blue-100 text-blue-800',
            'STAFF_ASSIGNED': 'bg-purple-100 text-purple-800',
            'STAFF_REMOVED': 'bg-red-100 text-red-800',
            'USER_CREATED': 'bg-green-100 text-green-800',
            'USER_ROLE_UPDATED': 'bg-yellow-100 text-yellow-800',
            'USER_ACTIVE_TOGGLED': 'bg-orange-100 text-orange-800',
            'BILL_GENERATED': 'bg-blue-100 text-blue-800',
            'PAYMENT_PROCESSED': 'bg-green-100 text-green-800',
            'CONSULTATION_STARTED': 'bg-indigo-100 text-indigo-800',
            'CONSULTATION_COMPLETED': 'bg-purple-100 text-purple-800',
            'LAB_ORDERED': 'bg-pink-100 text-pink-800',
            'LAB_COMPLETED': 'bg-teal-100 text-teal-800',
        };
        return colors[action] || 'bg-gray-100 text-gray-800';
    };

    if (isLoading && activeTab !== 'audit') {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading financial data...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-7xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">💰 Financial Dashboard</h1>
                        <p className="text-sm text-gray-500">
                            {user?.role === 'DISTRICT_ADMIN' ? 'All Facilities' : user?.facilityName || 'Facility'}
                        </p>
                    </div>
                    <button
                        onClick={() => window.location.reload()}
                        className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        🔄 Refresh
                    </button>
                </div>

                {/* ===== TAB NAVIGATION ===== */}
                <div className="flex gap-2 mb-6 border-b overflow-x-auto">
                    <button
                        onClick={() => setActiveTab('overview')}
                        className={`px-4 py-2 whitespace-nowrap ${
                            activeTab === 'overview'
                                ? 'border-b-2 border-blue-600 text-blue-600 font-medium'
                                : 'text-gray-500 hover:text-gray-700'
                        }`}
                    >
                        📊 Overview
                    </button>
                    <button
                        onClick={() => setActiveTab('reports')}
                        className={`px-4 py-2 whitespace-nowrap ${
                            activeTab === 'reports'
                                ? 'border-b-2 border-blue-600 text-blue-600 font-medium'
                                : 'text-gray-500 hover:text-gray-700'
                        }`}
                    >
                        📋 Reports
                    </button>
                    <button
                        onClick={() => setActiveTab('revenue')}
                        className={`px-4 py-2 whitespace-nowrap ${
                            activeTab === 'revenue'
                                ? 'border-b-2 border-blue-600 text-blue-600 font-medium'
                                : 'text-gray-500 hover:text-gray-700'
                        }`}
                    >
                        📈 Revenue
                    </button>
                    <button
                        onClick={() => setActiveTab('claims')}
                        className={`px-4 py-2 whitespace-nowrap ${
                            activeTab === 'claims'
                                ? 'border-b-2 border-blue-600 text-blue-600 font-medium'
                                : 'text-gray-500 hover:text-gray-700'
                        }`}
                    >
                        🏦 Claims
                    </button>
                    <button
                        onClick={() => setActiveTab('audit')}
                        className={`px-4 py-2 whitespace-nowrap ${
                            activeTab === 'audit'
                                ? 'border-b-2 border-blue-600 text-blue-600 font-medium'
                                : 'text-gray-500 hover:text-gray-700'
                        }`}
                    >
                        📜 Audit Logs
                    </button>
                </div>

                {/* ===== TAB CONTENT ===== */}

                {/* OVERVIEW TAB */}
                {activeTab === 'overview' && (
                    <>
                        {summary && (
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
                                <div className="bg-green-50 rounded-lg p-6">
                                    <p className="text-sm text-green-600">Today's Revenue</p>
                                    <p className="text-2xl font-bold text-green-900">
                                        {summary.todayRevenue?.toLocaleString() || '0'} {summary.currency || 'RWF'}
                                    </p>
                                </div>
                                <div className="bg-blue-50 rounded-lg p-6">
                                    <p className="text-sm text-blue-600">Monthly Revenue</p>
                                    <p className="text-2xl font-bold text-blue-900">
                                        {summary.monthRevenue?.toLocaleString() || '0'} {summary.currency || 'RWF'}
                                    </p>
                                </div>
                                <div className="bg-yellow-50 rounded-lg p-6">
                                    <p className="text-sm text-yellow-600">Pending Bills</p>
                                    <p className="text-2xl font-bold text-yellow-900">{summary.pendingBills || 0}</p>
                                </div>
                                <div className="bg-red-50 rounded-lg p-6">
                                    <p className="text-sm text-red-600">Overdue Bills</p>
                                    <p className="text-2xl font-bold text-red-900">{summary.overdueBills || 0}</p>
                                </div>
                            </div>
                        )}

                        {user?.role === 'DISTRICT_ADMIN' && claims.length > 0 && (
                            <div className="mt-8">
                                <h2 className="text-lg font-semibold text-gray-900 mb-4">Insurance Claims Summary</h2>
                                <div className="border rounded-lg overflow-hidden">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                        <tr>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Insurance Type
                                            </th>
                                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Total Claims
                                            </th>
                                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Percentage
                                            </th>
                                        </tr>
                                        </thead>
                                        <tbody className="bg-white divide-y divide-gray-200">
                                        {claims.map((claim, index) => (
                                            <tr key={index}>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                                                    {claim.insuranceType || 'Unknown'}
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-900">
                                                    {claim.totalAmount?.toLocaleString() || '0'} RWF
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">
                                                    {getClaimPercentage(claim.totalAmount || 0)}%
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {user?.role === 'DISTRICT_ADMIN' && claims.length === 0 && (
                            <div className="mt-8 p-6 bg-gray-50 rounded-lg text-center">
                                <p className="text-gray-500">No insurance claims data available</p>
                            </div>
                        )}
                    </>
                )}

                {/* REPORTS TAB */}
                {activeTab === 'reports' && (
                    <div>
                        <div className="flex flex-wrap gap-4 mb-6">
                            <div>
                                <label className="block text-sm text-gray-600">Start Date</label>
                                <input
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    className="border rounded-md px-3 py-2"
                                />
                            </div>
                            <div>
                                <label className="block text-sm text-gray-600">End Date</label>
                                <input
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    className="border rounded-md px-3 py-2"
                                />
                            </div>
                            <div className="flex items-end">
                                <button
                                    onClick={loadReport}
                                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    Generate Report
                                </button>
                            </div>
                        </div>

                        {report && (
                            <div className="space-y-4">
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                    <div className="bg-gray-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-gray-500">Total Bills</p>
                                        <p className="text-xl font-bold">{report.totalBills}</p>
                                    </div>
                                    <div className="bg-green-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-green-600">Total Revenue</p>
                                        <p className="text-xl font-bold text-green-900">{report.totalRevenue.toLocaleString()} RWF</p>
                                    </div>
                                    <div className="bg-blue-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-blue-600">Total Paid</p>
                                        <p className="text-xl font-bold text-blue-900">{report.totalPaid.toLocaleString()} RWF</p>
                                    </div>
                                    <div className="bg-yellow-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-yellow-600">Pending</p>
                                        <p className="text-xl font-bold text-yellow-900">{report.totalPending}</p>
                                    </div>
                                </div>

                                <div className="border rounded-lg overflow-hidden">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                        <tr>
                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Invoice</th>
                                            <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Total</th>
                                            <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Patient</th>
                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Status</th>
                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Insurance</th>
                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Date</th>
                                        </tr>
                                        </thead>
                                        <tbody className="divide-y divide-gray-200">
                                        {report.bills?.slice(0, 10).map((bill, i) => (
                                            <tr key={i}>
                                                <td className="px-4 py-2 text-sm">{bill.invoiceNumber}</td>
                                                <td className="px-4 py-2 text-sm text-right">{bill.totalAmount.toLocaleString()} RWF</td>
                                                <td className="px-4 py-2 text-sm text-right">{bill.patientAmount.toLocaleString()} RWF</td>
                                                <td className="px-4 py-2 text-sm">
                                                    <span className={`px-2 py-1 rounded-full text-xs ${
                                                        bill.status === 'PAID' ? 'bg-green-100 text-green-800' :
                                                            bill.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' :
                                                                'bg-red-100 text-red-800'
                                                    }`}>
                                                        {bill.status}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-2 text-sm">{bill.insuranceType}</td>
                                                <td className="px-4 py-2 text-sm">{new Date(bill.issuedAt).toLocaleDateString()}</td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                    {report.bills && report.bills.length > 10 && (
                                        <p className="p-2 text-sm text-gray-500 text-center">Showing first 10 of {report.bills.length} bills</p>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* REVENUE TAB */}
                {activeTab === 'revenue' && (
                    <div>
                        <div className="flex flex-wrap gap-4 mb-6">
                            <div>
                                <label className="block text-sm text-gray-600">Period</label>
                                <select
                                    value={period}
                                    onChange={(e) => setPeriod(e.target.value)}
                                    className="border rounded-md px-3 py-2"
                                >
                                    <option value="day">Last 24 Hours</option>
                                    <option value="week">Last 7 Days</option>
                                    <option value="month">Last 30 Days</option>
                                    <option value="year">Last 365 Days</option>
                                </select>
                            </div>
                            <div className="flex items-end">
                                <button
                                    onClick={loadRevenue}
                                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    Load Revenue
                                </button>
                            </div>
                        </div>

                        {revenue && (
                            <div className="space-y-4">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <div className="bg-green-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-green-600">Total Revenue</p>
                                        <p className="text-xl font-bold text-green-900">{revenue.totalRevenue.toLocaleString()} RWF</p>
                                    </div>
                                    <div className="bg-blue-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-blue-600">Total Paid</p>
                                        <p className="text-xl font-bold text-blue-900">{revenue.totalPaid.toLocaleString()} RWF</p>
                                    </div>
                                    <div className="bg-yellow-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-yellow-600">Pending</p>
                                        <p className="text-xl font-bold text-yellow-900">{revenue.pendingCount}</p>
                                    </div>
                                </div>

                                <div className="border rounded-lg overflow-hidden">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                        <tr>
                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Date</th>
                                            <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Revenue</th>
                                            <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Paid</th>
                                        </tr>
                                        </thead>
                                        <tbody className="divide-y divide-gray-200">
                                        {Object.entries(revenue.dailyRevenue || {}).map(([date, amount]) => (
                                            <tr key={date}>
                                                <td className="px-4 py-2 text-sm">{date}</td>
                                                <td className="px-4 py-2 text-sm text-right">{amount.toLocaleString()} RWF</td>
                                                <td className="px-4 py-2 text-sm text-right">
                                                    {(revenue.dailyPaid?.[date] || 0).toLocaleString()} RWF
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* CLAIMS TAB */}
                {activeTab === 'claims' && (
                    <div>
                        {claims.length > 0 ? (
                            <div className="space-y-4">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <div className="bg-blue-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-blue-600">Total Claims</p>
                                        <p className="text-xl font-bold text-blue-900">{getTotalClaimsAmount().toLocaleString()} RWF</p>
                                    </div>
                                    <div className="bg-green-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-green-600">Claims Processed</p>
                                        <p className="text-xl font-bold text-green-900">{claims.length}</p>
                                    </div>
                                    <div className="bg-purple-50 rounded-lg p-4 text-center">
                                        <p className="text-sm text-purple-600">Insurance Types</p>
                                        <p className="text-xl font-bold text-purple-900">{claims.length}</p>
                                    </div>
                                </div>

                                <div className="border rounded-lg overflow-hidden">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                        <tr>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Insurance Type
                                            </th>
                                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Total Claims
                                            </th>
                                            <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Percentage
                                            </th>
                                        </tr>
                                        </thead>
                                        <tbody className="bg-white divide-y divide-gray-200">
                                        {claims.map((claim, index) => (
                                            <tr key={index}>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                                                    {claim.insuranceType || 'Unknown'}
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-900">
                                                    {claim.totalAmount?.toLocaleString() || '0'} RWF
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">
                                                    {getClaimPercentage(claim.totalAmount || 0)}%
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        ) : (
                            <div className="p-6 bg-gray-50 rounded-lg text-center">
                                <p className="text-gray-500">No insurance claims data available</p>
                            </div>
                        )}
                    </div>
                )}

                {/* ===== AUDIT LOGS TAB ===== */}
                {activeTab === 'audit' && (
                    <div>
                        {/* Filters */}
                        <div className="flex flex-wrap gap-4 mb-6">
                            <div>
                                <label className="block text-sm text-gray-600">Action</label>
                                <input
                                    type="text"
                                    placeholder="e.g. FACILITY_CREATED"
                                    value={auditFilters.action}
                                    onChange={(e) => setAuditFilters(prev => ({ ...prev, action: e.target.value }))}
                                    className="border rounded-md px-3 py-2 w-48"
                                />
                            </div>
                            <div>
                                <label className="block text-sm text-gray-600">Username</label>
                                <input
                                    type="text"
                                    placeholder="e.g. admin"
                                    value={auditFilters.username}
                                    onChange={(e) => setAuditFilters(prev => ({ ...prev, username: e.target.value }))}
                                    className="border rounded-md px-3 py-2 w-48"
                                />
                            </div>
                            <div>
                                <label className="block text-sm text-gray-600">Start Date</label>
                                <input
                                    type="date"
                                    value={auditFilters.startDate}
                                    onChange={(e) => setAuditFilters(prev => ({ ...prev, startDate: e.target.value }))}
                                    className="border rounded-md px-3 py-2"
                                />
                            </div>
                            <div>
                                <label className="block text-sm text-gray-600">End Date</label>
                                <input
                                    type="date"
                                    value={auditFilters.endDate}
                                    onChange={(e) => setAuditFilters(prev => ({ ...prev, endDate: e.target.value }))}
                                    className="border rounded-md px-3 py-2"
                                />
                            </div>
                            <div className="flex items-end gap-2">
                                <button
                                    onClick={loadAuditLogs}
                                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    🔍 Search
                                </button>
                                <button
                                    onClick={() => {
                                        setAuditFilters({ action: '', username: '', startDate: '', endDate: '' });
                                        loadAuditLogs();
                                    }}
                                    className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                                >
                                    Clear
                                </button>
                            </div>
                        </div>

                        {/* Audit Logs Table - Simplified */}
                        {auditLoading ? (
                            <div className="flex justify-center items-center py-12">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                            </div>
                        ) : auditLogs.length > 0 ? (
                            <div className="border rounded-lg overflow-hidden">
                                <div className="overflow-x-auto">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                        <tr>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Action
                                            </th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Username
                                            </th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Created At
                                            </th>
                                        </tr>
                                        </thead>
                                        <tbody className="bg-white divide-y divide-gray-200">
                                        {auditLogs.map((log) => (
                                            <tr key={log.id} className="hover:bg-gray-50">
                                                <td className="px-6 py-4">
                                                    <span className={`text-xs px-2 py-1 rounded-full ${getActionColor(log.action)}`}>
                                                        {log.action}
                                                    </span>
                                                </td>
                                                <td className="px-6 py-4 text-sm font-medium text-gray-900">
                                                    {log.actorUsername || log.username || 'System'}
                                                </td>
                                                <td className="px-6 py-4 text-sm text-gray-600">
                                                    {new Date(log.createdAt).toLocaleString()}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                                <div className="p-3 text-sm text-gray-500 text-center border-t">
                                    Showing {auditLogs.length} audit logs
                                </div>
                            </div>
                        ) : (
                            <div className="p-6 bg-gray-50 rounded-lg text-center">
                                <p className="text-gray-500">No audit logs found</p>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};