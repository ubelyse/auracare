import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { adminService, Department, Facility, StaffUser, User } from '../../services/admin';
import { useAuthStore } from '../../stores/authStore';

export const StaffManagement: React.FC = () => {
    const { facilityId } = useParams<{ facilityId: string }>();
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [staff, setStaff] = useState<StaffUser[]>([]);
    const [allUsers, setAllUsers] = useState<User[]>([]);
    const [departments, setDepartments] = useState<Department[]>([]);
    const [facility, setFacility] = useState<Facility | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [selectedUser, setSelectedUser] = useState('');
    const [selectedRole, setSelectedRole] = useState('DOCTOR');
    const [selectedDepartment, setSelectedDepartment] = useState('');

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
                if (facilityId) {
                    const [facilityData, staffData, usersData, deptData] = await Promise.all([
                        adminService.getFacility(facilityId),
                        adminService.getStaffByFacility(facilityId),
                        adminService.getUsers(),
                        adminService.getDepartmentsByFacility(facilityId)
                    ]);

                    // ===== ADD THESE DEBUG LOGS =====
                    console.log('🔴 STAFF DATA:', staffData);
                    console.log('🔴 IS ARRAY?', Array.isArray(staffData));
                    console.log('🔴 LENGTH:', staffData?.length);
                    if (Array.isArray(staffData) && staffData.length > 0) {
                        console.log('🔴 FIRST STAFF:', staffData[0]);
                    }

                    if (isMounted) {
                        setFacility(facilityData);
                        setStaff(staffData || []);
                        setAllUsers(usersData || []);
                        setDepartments(deptData || []);
                    }
                }
            } catch (error) {
                if (isMounted) {
                    toast.error('Failed to load staff data');
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

    const loadData = async () => {
        setIsLoading(true);
        try {
            if (facilityId) {
                const [facilityData, staffData, usersData, deptData] = await Promise.all([
                    adminService.getFacility(facilityId),
                    adminService.getStaffByFacility(facilityId),
                    adminService.getUsers(),
                    adminService.getDepartmentsByFacility(facilityId)
                ]);
                setFacility(facilityData);
                setStaff(staffData || []);
                setAllUsers(usersData || []);
                setDepartments(deptData || []);
            }
        } catch (error) {
            toast.error('Failed to load staff data');
        } finally {
            setIsLoading(false);
        }
    };

    const handleAssign = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedUser) {
            toast.error('Please select a user');
            return;
        }

        if (selectedRole === 'DOCTOR' && !selectedDepartment) {
            toast.error('Please select a department for the doctor');
            return;
        }

        try {
            // 1. Assign staff to facility
            await adminService.assignStaff(
                selectedUser,
                facilityId!,
                selectedRole,
                true
            );

            // 2. If role is DOCTOR, assign them to the chosen department
            if (selectedRole === 'DOCTOR' && selectedDepartment) {
                await adminService.assignDoctorToDepartment(selectedUser, selectedDepartment);
            }

            toast.success('Staff assigned successfully!');
            setShowModal(false);
            setSelectedUser('');
            setSelectedDepartment('');
            setSelectedRole('DOCTOR');
            loadData();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Assignment failed');
        }
    };

    const handleRemove = async (userId: string) => {
        if (!confirm('Remove this staff member from the facility?')) return;
        try {
            await adminService.removeStaff(userId, facilityId!);
            toast.success('Staff removed successfully');
            loadData();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Removal failed');
        }
    };

    const getAvailableUsers = () => {
        const assignedIds = staff ? staff.map(s => s.id) : [];
        return allUsers ? allUsers.filter(u =>
            !assignedIds.includes(u.id) &&
            u.role !== 'DISTRICT_ADMIN' &&
            u.role !== 'PATIENT'
        ) : [];
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading staff...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">
                            👥 Staff Management
                        </h1>
                        <p className="text-sm text-gray-500">
                            {facility?.name || 'Facility'} - Manage staff assignments
                        </p>
                    </div>
                    <button
                        onClick={() => navigate('/admin/dashboard')}
                        className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        ← Back to Dashboard
                    </button>
                </div>

                <div className="mb-6">
                    <button
                        onClick={() => {
                            setSelectedUser('');
                            setSelectedDepartment('');
                            setSelectedRole('DOCTOR');
                            setShowModal(true);
                        }}
                        className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                    >
                        + Assign Staff
                    </button>
                </div>

                <div className="space-y-2">
                    {!staff || staff.length === 0 ? (
                        <p className="text-gray-500 text-center py-8">No staff assigned to this facility</p>
                    ) : (
                        staff.map((person) => (
                            <div
                                key={person.id}
                                className="flex items-center justify-between p-4 border rounded-lg hover:bg-gray-50"
                            >
                                <div>
                                    <div className="flex items-center space-x-3">
                                        <span className="font-medium text-gray-900">
                                            {person.firstName} {person.lastName}
                                        </span>
                                        <span className={`text-xs px-2 py-0.5 rounded-full ${
                                            person.role === 'DOCTOR' ? 'bg-blue-100 text-blue-800' :
                                                person.role === 'FACILITY_ADMIN' ? 'bg-purple-100 text-purple-800' :
                                                    'bg-gray-100 text-gray-800'
                                        }`}>
                                            {person.role}
                                        </span>
                                        <span className={`text-xs px-2 py-0.5 rounded-full ${
                                            person.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                        }`}>
                                            {person.active ? 'Active' : 'Inactive'}
                                        </span>
                                    </div>
                                    <p className="text-sm text-gray-500">{person.email}</p>
                                </div>
                                <button
                                    onClick={() => handleRemove(person.id)}
                                    className="text-red-600 hover:text-red-800 text-sm"
                                >
                                    Remove
                                </button>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Assign Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                        <h2 className="text-xl font-bold text-gray-900 mb-4">Assign Staff</h2>
                        <form onSubmit={handleAssign}>
                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Select User *</label>
                                    <select
                                        required
                                        value={selectedUser}
                                        onChange={(e) => setSelectedUser(e.target.value)}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    >
                                        <option value="">Select a user...</option>
                                        {getAvailableUsers().map((u) => (
                                            <option key={u.id} value={u.id}>
                                                {u.firstName} {u.lastName} ({u.email})
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">Role *</label>
                                    <select
                                        required
                                        value={selectedRole}
                                        onChange={(e) => setSelectedRole(e.target.value)}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    >
                                        <option value="DOCTOR">Doctor</option>
                                        <option value="STAFF">Staff</option>
                                        <option value="FACILITY_ADMIN">Facility Admin</option>
                                    </select>
                                </div>

                                {/* Conditional Department Selection if Role is DOCTOR */}
                                {selectedRole === 'DOCTOR' && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Department *</label>
                                        {departments && departments.length > 0 ? (
                                            <select
                                                required
                                                value={selectedDepartment}
                                                onChange={(e) => setSelectedDepartment(e.target.value)}
                                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                            >
                                                <option value="">Select a department...</option>
                                                {departments.map((dept) => (
                                                    <option key={dept.id} value={dept.id}>
                                                        {dept.name} ({dept.code})
                                                    </option>
                                                ))}
                                            </select>
                                        ) : (
                                            <div className="mt-1 p-3 bg-yellow-50 rounded-md border border-yellow-200">
                                                <p className="text-sm text-yellow-700">
                                                    ⚠️ No departments found for this facility. Please create one first.
                                                </p>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                            <div className="mt-6 flex space-x-3">
                                <button
                                    type="submit"
                                    className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                                >
                                    Assign
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setShowModal(false);
                                        setSelectedUser('');
                                        setSelectedDepartment('');
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