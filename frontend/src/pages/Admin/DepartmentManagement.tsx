import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService, Department, Facility } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';

export const DepartmentManagement: React.FC = () => {
    const { facilityId } = useParams<{ facilityId: string }>();
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [departments, setDepartments] = useState<Department[]>([]);
    const [facility, setFacility] = useState<Facility | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editingDept, setEditingDept] = useState<Department | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [formData, setFormData] = useState({
        name: '',
        code: '',
        description: '',
        active: true
    });

    useEffect(() => {
        let isMounted = true;

        if (!user) {
            navigate('/login');
            return;
        }

        const loadData = async (): Promise<void> => {
            setIsLoading(true);
            try {
                if (facilityId) {
                    const [facilityData, deptData] = await Promise.all([
                        adminService.getFacility(facilityId),
                        adminService.getDepartmentsByFacility(facilityId)
                    ]);

                    if (isMounted) {
                        setFacility(facilityData);
                        setDepartments(deptData || []);
                    }
                }
            } catch (error: any) {
                if (isMounted) {
                    toast.error('Failed to load departments');
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
    }, [facilityId, user, navigate]);

    const handleSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();

        // ===== FIXED: Ensure facilityId is defined =====
        if (!facilityId) {
            toast.error('Facility ID is required');
            return;
        }

        setIsSubmitting(true);
        try {
            const departmentData = {
                name: formData.name,
                code: formData.code,
                description: formData.description,
                active: formData.active,
                facilityId: facilityId // Now guaranteed to be a string
            };

            if (editingDept) {
                await adminService.updateDepartment(editingDept.id, departmentData);
                toast.success('Department updated successfully!');
            } else {
                await adminService.createDepartment(departmentData);
                toast.success('Department created successfully!');
            }
            setShowModal(false);
            setEditingDept(null);
            setFormData({ name: '', code: '', description: '', active: true });
            await loadData(); // ===== FIXED: Added await =====
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Operation failed');
        } finally {
            setIsSubmitting(false);
        }
    };

    // ===== FIXED: Added explicit return type =====
    const loadData = async (): Promise<void> => {
        // ===== FIXED: Check if facilityId exists =====
        if (!facilityId) {
            toast.error('No facility selected');
            setIsLoading(false);
            return;
        }

        setIsLoading(true);
        try {
            const [facilityData, deptData] = await Promise.all([
                adminService.getFacility(facilityId),
                adminService.getDepartmentsByFacility(facilityId)
            ]);
            setFacility(facilityData);
            setDepartments(deptData || []);
        } catch (error: any) {
            toast.error('Failed to load departments');
        } finally {
            setIsLoading(false);
        }
    };

    const handleEdit = (dept: Department): void => {
        setEditingDept(dept);
        setFormData({
            name: dept.name,
            code: dept.code,
            description: dept.description || '',
            active: dept.active
        });
        setShowModal(true);
    };

    const handleToggleActive = async (dept: Department): Promise<void> => {
        if (!confirm(`Are you sure you want to ${dept.active ? 'deactivate' : 'activate'} ${dept.name}?`)) {
            return;
        }

        // ===== FIXED: Ensure facilityId is defined =====
        if (!facilityId) {
            toast.error('Facility ID is required');
            return;
        }

        try {
            await adminService.updateDepartment(dept.id, {
                ...dept,
                active: !dept.active,
                facilityId: facilityId // Now guaranteed to be a string
            });
            toast.success(`Department ${dept.active ? 'deactivated' : 'activated'} successfully`);
            await loadData(); // ===== FIXED: Added await =====
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Operation failed');
        }
    };

    const getStatusBadge = (active: boolean): string => {
        return active
            ? 'bg-green-100 text-green-800'
            : 'bg-gray-100 text-gray-800';
    };

    // ===== FIXED: Check facilityId early =====
    if (!facilityId) {
        return (
            <div className="max-w-5xl mx-auto p-6">
                <div className="bg-white rounded-lg shadow-lg p-8 text-center">
                    <div className="text-6xl mb-4">⚠️</div>
                    <h2 className="text-xl font-bold text-gray-900">No Facility Selected</h2>
                    <p className="text-gray-500 mt-2">Please select a facility first.</p>
                    <button
                        onClick={() => navigate('/admin/dashboard')}
                        className="mt-4 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                    >
                        ← Back to Dashboard
                    </button>
                </div>
            </div>
        );
    }

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading departments...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-5xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg">
                {/* Header */}
                <div className="p-6 border-b border-gray-200">
                    <div className="flex justify-between items-center flex-wrap gap-4">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
                                📋 Department Management
                            </h1>
                            <p className="text-sm text-gray-500">
                                {facility?.name || 'Facility'} - Manage departments
                            </p>
                            <p className="text-xs text-gray-400 mt-1">
                                Total departments: <span className="font-semibold">{departments?.length || 0}</span>
                            </p>
                        </div>
                        <button
                            onClick={() => navigate('/admin/dashboard')}
                            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50 transition-colors"
                        >
                            ← Back to Dashboard
                        </button>
                    </div>
                </div>

                {/* Actions */}
                <div className="p-4 border-b border-gray-200 bg-gray-50">
                    <button
                        onClick={() => {
                            setEditingDept(null);
                            setFormData({ name: '', code: '', description: '', active: true });
                            setShowModal(true);
                        }}
                        className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors flex items-center gap-2"
                    >
                        <span>➕</span> Add Department
                    </button>
                </div>

                {/* Departments List */}
                <div className="p-6">
                    {!departments || departments.length === 0 ? (
                        <div className="text-center py-12">
                            <div className="text-6xl mb-4">📋</div>
                            <h3 className="text-lg font-medium text-gray-900">No Departments</h3>
                            <p className="text-gray-500 mt-2">
                                Get started by adding your first department
                            </p>
                            <button
                                onClick={() => {
                                    setEditingDept(null);
                                    setFormData({ name: '', code: '', description: '', active: true });
                                    setShowModal(true);
                                }}
                                className="mt-4 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                            >
                                + Add Department
                            </button>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {departments.map((dept) => (
                                <div
                                    key={dept.id}
                                    className={`border rounded-lg p-4 hover:shadow-md transition-shadow ${
                                        !dept.active ? 'opacity-60 bg-gray-50' : ''
                                    }`}
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <div className="flex items-center gap-2">
                                                <span className="font-semibold text-gray-900 text-lg">
                                                    {dept.name}
                                                </span>
                                                <span className="text-xs text-gray-500">
                                                    ({dept.code})
                                                </span>
                                            </div>
                                            {dept.description && (
                                                <p className="text-sm text-gray-500 mt-1">
                                                    {dept.description}
                                                </p>
                                            )}
                                        </div>
                                        <span className={`text-xs px-2 py-1 rounded-full ${getStatusBadge(dept.active)}`}>
                                            {dept.active ? '🟢 Active' : '🔴 Inactive'}
                                        </span>
                                    </div>

                                    {/* Action Buttons */}
                                    <div className="mt-4 pt-3 border-t border-gray-200 flex gap-2">
                                        <button
                                            onClick={() => handleEdit(dept)}
                                            className="px-3 py-1.5 bg-blue-100 text-blue-800 rounded-md text-sm hover:bg-blue-200 transition-colors"
                                        >
                                            ✏️ Edit
                                        </button>
                                        <button
                                            onClick={() => handleToggleActive(dept)}
                                            className={`px-3 py-1.5 rounded-md text-sm transition-colors ${
                                                dept.active
                                                    ? 'bg-yellow-100 text-yellow-800 hover:bg-yellow-200'
                                                    : 'bg-green-100 text-green-800 hover:bg-green-200'
                                            }`}
                                        >
                                            {dept.active ? '🔴 Deactivate' : '🟢 Activate'}
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="px-6 py-3 border-t border-gray-200 bg-gray-50 rounded-b-lg">
                    <div className="text-sm text-gray-500">
                        Showing {departments?.length || 0} department{(departments?.length || 0) !== 1 ? 's' : ''}
                        {departments && departments.some(d => !d.active) && (
                            <span className="ml-2 text-yellow-600">
                                ({departments.filter(d => !d.active).length} inactive)
                            </span>
                        )}
                    </div>
                </div>
            </div>

            {/* Create/Edit Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-xl font-bold text-gray-900">
                                {editingDept ? '✏️ Edit Department' : '➕ New Department'}
                            </h2>
                            <button
                                type="button"
                                onClick={() => {
                                    setShowModal(false);
                                    setEditingDept(null);
                                }}
                                className="text-gray-400 hover:text-gray-600"
                            >
                                ✕
                            </button>
                        </div>

                        <form onSubmit={handleSubmit}>
                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">
                                        Department Name <span className="text-red-500">*</span>
                                    </label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., General Medicine"
                                        disabled={isSubmitting}
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700">
                                        Department Code <span className="text-red-500">*</span>
                                    </label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.code}
                                        onChange={(e) => setFormData({...formData, code: e.target.value.toUpperCase()})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., GEN"
                                        maxLength={10}
                                        disabled={isSubmitting}
                                    />
                                    <p className="text-xs text-gray-400 mt-1">
                                        Unique identifier (max 10 characters)
                                    </p>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700">
                                        Description
                                    </label>
                                    <textarea
                                        value={formData.description}
                                        onChange={(e) => setFormData({...formData, description: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        rows={3}
                                        placeholder="Brief description of the department"
                                        disabled={isSubmitting}
                                    />
                                </div>

                                <div className="flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={formData.active}
                                        onChange={(e) => setFormData({...formData, active: e.target.checked})}
                                        className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                                        disabled={isSubmitting}
                                    />
                                    <label className="ml-2 block text-sm text-gray-900">
                                        Active
                                    </label>
                                </div>

                                {editingDept && (
                                    <div className="p-3 bg-blue-50 rounded-md border border-blue-200">
                                        <p className="text-sm text-blue-700">
                                            Editing: {editingDept.name}
                                        </p>
                                    </div>
                                )}
                            </div>

                            <div className="mt-6 flex space-x-3">
                                <button
                                    type="submit"
                                    disabled={isSubmitting}
                                    className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    {isSubmitting ? 'Saving...' : (editingDept ? 'Update Department' : 'Create Department')}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setShowModal(false);
                                        setEditingDept(null);
                                    }}
                                    className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                                    disabled={isSubmitting}
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