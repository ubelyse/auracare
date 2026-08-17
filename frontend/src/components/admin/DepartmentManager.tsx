import React, { useState, useEffect, useRef } from 'react';
import { adminService, Department } from '../../services/admin';
import toast from 'react-hot-toast';

interface DepartmentManagerProps {
    facilityId: string;
    facilityName: string;
    onRefresh?: () => void;
}

export const DepartmentManager: React.FC<DepartmentManagerProps> = ({
                                                                        facilityId,
                                                                        facilityName,
                                                                        onRefresh
                                                                    }) => {
    const [departments, setDepartments] = useState<Department[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [editingDept, setEditingDept] = useState<Department | null>(null);
    const [formData, setFormData] = useState({
        name: '',
        code: '',
        description: '',
        active: true
    });
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        loadDepartments();

        return () => {
            isMounted.current = false;
        };
    }, [facilityId]);

    const loadDepartments = async () => {
        setIsLoading(true);
        try {
            const data = await adminService.getDepartmentsByFacility(facilityId);
            if (isMounted.current) {
                setDepartments(data || []);
            }
        } catch (error) {
            if (isMounted.current) {
                toast.error('Failed to load departments');
            }
        } finally {
            if (isMounted.current) {
                setIsLoading(false);
            }
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            if (editingDept) {
                await adminService.updateDepartment(editingDept.id, {
                    ...formData,
                    facilityId
                });
                toast.success('Department updated!');
            } else {
                await adminService.createDepartment({
                    ...formData,
                    facilityId
                });
                toast.success('Department created!');
            }
            setShowModal(false);
            setEditingDept(null);
            setFormData({ name: '', code: '', description: '', active: true });
            await loadDepartments();
            if (onRefresh) onRefresh();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Operation failed');
        }
    };

    const handleEdit = (dept: Department) => {
        setEditingDept(dept);
        setFormData({
            name: dept.name,
            code: dept.code,
            description: dept.description || '',
            active: dept.active
        });
        setShowModal(true);
    };

    // ===== FIXED: Use async/await instead of promise chain =====
    const handleToggleActive = async (dept: Department) => {
        if (!confirm(`Are you sure you want to ${dept.active ? 'deactivate' : 'activate'} ${dept.name}?`)) {
            return;
        }
        try {
            await adminService.updateDepartment(dept.id, {
                ...dept,
                active: !dept.active,
                facilityId
            });
            toast.success(`Department ${dept.active ? 'deactivated' : 'activated'} successfully`);
            await loadDepartments();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Operation failed');
        }
    };

    if (isLoading) {
        return <div className="text-center py-4">Loading departments...</div>;
    }

    return (
        <div>
            <div className="mb-4">
                <button
                    onClick={() => {
                        setEditingDept(null);
                        setFormData({ name: '', code: '', description: '', active: true });
                        setShowModal(true);
                    }}
                    className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                >
                    + Add Department
                </button>
            </div>

            <div className="space-y-2">
                {departments.length === 0 ? (
                    <p className="text-gray-500 text-center py-4">No departments configured</p>
                ) : (
                    departments.map((dept) => (
                        <div
                            key={dept.id}
                            className="flex items-center justify-between p-3 border rounded-lg hover:bg-gray-50"
                        >
                            <div>
                                <div className="flex items-center space-x-2">
                                    <span className="font-medium text-gray-900">{dept.name}</span>
                                    <span className="text-xs text-gray-500">({dept.code})</span>
                                    <span className={`text-xs px-2 py-0.5 rounded-full ${
                                        dept.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                    }`}>
                                        {dept.active ? 'Active' : 'Inactive'}
                                    </span>
                                </div>
                                {dept.description && (
                                    <p className="text-sm text-gray-500">{dept.description}</p>
                                )}
                            </div>
                            <div className="flex space-x-2">
                                <button
                                    onClick={() => handleEdit(dept)}
                                    className="text-blue-600 hover:text-blue-800 text-sm"
                                >
                                    Edit
                                </button>
                                <button
                                    onClick={() => handleToggleActive(dept)}
                                    className="text-sm text-yellow-600 hover:text-yellow-800"
                                >
                                    {dept.active ? 'Deactivate' : 'Activate'}
                                </button>
                            </div>
                        </div>
                    ))
                )}
            </div>

            {/* Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                        <h2 className="text-xl font-bold text-gray-900 mb-4">
                            {editingDept ? 'Edit Department' : 'New Department'}
                        </h2>
                        <form onSubmit={handleSubmit}>
                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Department Name *</label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., General Medicine"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Department Code *</label>
                                    <input
                                        type="text"
                                        required
                                        value={formData.code}
                                        onChange={(e) => setFormData({...formData, code: e.target.value.toUpperCase()})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        placeholder="e.g., GEN"
                                        maxLength={10}
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Description</label>
                                    <textarea
                                        value={formData.description}
                                        onChange={(e) => setFormData({...formData, description: e.target.value})}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                        rows={2}
                                        placeholder="Brief description of the department"
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
                                    {editingDept ? 'Update' : 'Create'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setShowModal(false);
                                        setEditingDept(null);
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