// src/pages/staff/PatientList.tsx

import React, { useState, useEffect } from 'react';
import { staffService } from '../../services/staff';
import toast from 'react-hot-toast';

interface Patient {
    id: string;
    ticketNumber: string;
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
    status: string;
    priority: string;
    queuePosition: number;
    estimatedWaitMinutes: number;
    assignedDoctor?: string;
    createdAt: string;
}

export const PatientList: React.FC = () => {
    const [patients, setPatients] = useState<Patient[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterStatus, setFilterStatus] = useState('ALL');

    useEffect(() => {
        loadPatients();
    }, []);

    const loadPatients = async () => {
        setIsLoading(true);
        try {
            const data = await staffService.getPatients();
            setPatients(data || []);
        } catch (error) {
            toast.error('Failed to load patients');
            console.error('Patients error:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const getStatusColor = (status: string) => {
        const colors: Record<string, string> = {
            'CHECKED_IN': 'bg-blue-100 text-blue-800',
            'TRIAGED': 'bg-indigo-100 text-indigo-800',
            'IN_CONSULTATION': 'bg-green-100 text-green-800',
            'LAB_PENDING': 'bg-purple-100 text-purple-800',
            'LAB_COMPLETED': 'bg-teal-100 text-teal-800',
            'CONSULTATION_DONE': 'bg-gray-100 text-gray-800',
            'DISCHARGED': 'bg-gray-100 text-gray-500',
        };
        return colors[status] || 'bg-gray-100 text-gray-800';
    };

    const getPriorityColor = (priority: string) => {
        const colors: Record<string, string> = {
            'EMERGENCY': 'bg-red-100 text-red-800',
            'HIGH': 'bg-orange-100 text-orange-800',
            'MEDIUM': 'bg-yellow-100 text-yellow-800',
            'LOW': 'bg-green-100 text-green-800',
        };
        return colors[priority] || 'bg-gray-100 text-gray-800';
    };

    const filteredPatients = patients.filter(p => {
        const matchesSearch =
            p.ticketNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
            `${p.firstName} ${p.lastName}`.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesStatus = filterStatus === 'ALL' || p.status === filterStatus;
        return matchesSearch && matchesStatus;
    });

    if (isLoading) {
        return (
            <div className="flex justify-center items-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
                <span className="ml-2 text-gray-600">Loading patients...</span>
            </div>
        );
    }

    return (
        <div className="bg-white rounded-lg shadow">
            {/* Header */}
            <div className="p-4 border-b flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
                <h3 className="text-lg font-semibold text-gray-900">👥 Patients</h3>

                <div className="flex flex-col sm:flex-row gap-2 w-full sm:w-auto">
                    {/* Search */}
                    <input
                        type="text"
                        placeholder="Search patients..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="px-3 py-1.5 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />

                    {/* Filter */}
                    <select
                        value={filterStatus}
                        onChange={(e) => setFilterStatus(e.target.value)}
                        className="px-3 py-1.5 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                    >
                        <option value="ALL">All Status</option>
                        <option value="CHECKED_IN">Checked In</option>
                        <option value="TRIAGED">Triaged</option>
                        <option value="IN_CONSULTATION">In Consultation</option>
                        <option value="LAB_PENDING">Lab Pending</option>
                        <option value="LAB_COMPLETED">Lab Completed</option>
                    </select>
                </div>
            </div>

            {/* Patient List */}
            <div className="overflow-x-auto">
                {filteredPatients.length === 0 ? (
                    <div className="text-center py-12">
                        <p className="text-gray-500">No patients found</p>
                    </div>
                ) : (
                    <table className="w-full">
                        <thead className="bg-gray-50">
                        <tr>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ticket</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Patient</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Priority</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Position</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Wait Time</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-200">
                        {filteredPatients.map((p) => (
                            <tr key={p.id} className="hover:bg-gray-50 transition-colors">
                                <td className="px-4 py-3 text-sm font-medium text-gray-900">
                                    #{p.ticketNumber}
                                </td>
                                <td className="px-4 py-3">
                                    <div className="text-sm font-medium text-gray-900">
                                        {p.firstName} {p.lastName}
                                    </div>
                                    <div className="text-xs text-gray-500">{p.email}</div>
                                </td>
                                <td className="px-4 py-3">
                                        <span className={`text-xs px-2 py-1 rounded-full ${getStatusColor(p.status)}`}>
                                            {p.status.replace('_', ' ')}
                                        </span>
                                </td>
                                <td className="px-4 py-3">
                                        <span className={`text-xs px-2 py-1 rounded-full ${getPriorityColor(p.priority)}`}>
                                            {p.priority}
                                        </span>
                                </td>
                                <td className="px-4 py-3 text-sm text-gray-600">
                                    #{p.queuePosition}
                                </td>
                                <td className="px-4 py-3 text-sm text-gray-600">
                                    {p.estimatedWaitMinutes} min
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Footer */}
            <div className="p-4 border-t text-sm text-gray-500">
                Total: {filteredPatients.length} patient{filteredPatients.length !== 1 ? 's' : ''}
            </div>
        </div>
    );
};

export default PatientList;