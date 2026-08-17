import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminService, StaffUser, User, Department } from '../../services/admin';
import toast from 'react-hot-toast';

interface StaffManagerProps {
    facilityId: string;
    facilityName: string;
    onRefresh?: () => void;
}

export const StaffManager: React.FC<StaffManagerProps> = ({
                                                              facilityId,
                                                              facilityName,
                                                              onRefresh
                                                          }) => {
    const navigate = useNavigate();
    const [staff, setStaff] = useState<StaffUser[]>([]);
    const [allUsers, setAllUsers] = useState<User[]>([]);
    const [departments, setDepartments] = useState<Department[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [selectedUser, setSelectedUser] = useState('');
    const [selectedRole, setSelectedRole] = useState('DOCTOR');
    const [selectedDepartment, setSelectedDepartment] = useState('');
    const [selectedUserRole, setSelectedUserRole] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        loadData();

        return () => {
            isMounted.current = false;
        };
    }, [facilityId]);

    const loadData = async () => {
        setIsLoading(true);
        try {
            const [staffData, usersData, deptData] = await Promise.all([
                adminService.getStaffByFacility(facilityId),
                adminService.getUsers(),
                adminService.getDepartmentsByFacility(facilityId)
            ]);
            if (isMounted.current) {
                setStaff(staffData || []);
                setAllUsers(usersData || []);
                setDepartments(deptData || []);
            }
        } catch (error) {
            if (isMounted.current) {
                toast.error('Failed to load staff data');
            }
        } finally {
            if (isMounted.current) {
                setIsLoading(false);
            }
        }
    };

    const handleUserChange = (userId: string) => {
        setSelectedUser(userId);
        const user = allUsers.find(u => u.id === userId);
        if (user) {
            setSelectedUserRole(user.role);
            if (user.role === 'DOCTOR' || user.role === 'STAFF' || user.role === 'FACILITY_ADMIN') {
                setSelectedRole(user.role);
            } else {
                setSelectedRole('DOCTOR');
            }
        }
    };

    const handleAssign = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedUser) {
            toast.error('Please select a user');
            return;
        }
        setIsSubmitting(true);
        try {
            // 1. Update user role
            await adminService.updateUserRole(selectedUser, selectedRole);

            // 2. Assign user to facility
            await adminService.assignStaff(
                selectedUser,
                facilityId,
                selectedRole,
                true
            );

            // 3. If role is DOCTOR and department is selected, assign to department
            if (selectedRole === 'DOCTOR' && selectedDepartment) {
                await adminService.assignDoctorToDepartment(selectedUser, selectedDepartment);
                toast.success('Doctor assigned to department successfully!');
            }

            toast.success('Staff assigned successfully!');
            setShowModal(false);
            setSelectedUser('');
            setSelectedDepartment('');
            setSelectedUserRole('');
            await loadData();
            if (onRefresh) onRefresh();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Assignment failed');
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleRemove = async (userId: string) => {
        if (!confirm('Remove this staff member from the facility?')) return;
        try {
            await adminService.removeStaff(userId, facilityId);
            toast.success('Staff removed successfully');
            await loadData();
            if (onRefresh) onRefresh();
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Removal failed');
        }
    };

    const getAvailableUsers = () => {
        const assignedIds = staff.map(s => s.id);
        return allUsers.filter(u =>
            !assignedIds.includes(u.id) &&
            u.role !== 'DISTRICT_ADMIN'
        );
    };

    const getRoleBadge = (role: string) => {
        const colors: Record<string, string> = {
            DOCTOR: 'bg-blue-100 text-blue-800',
            STAFF: 'bg-gray-100 text-gray-800',
            FACILITY_ADMIN: 'bg-purple-100 text-purple-800'
        };
        return colors[role] || 'bg-gray-100 text-gray-800';
    };

    if (isLoading) {
        return <div className="text-center py-4">Loading staff...</div>;
    }

    return (
        <div>
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-semibold text-gray-900">
                    Staff - {facilityName}
                </h3>
                <button
                    onClick={() => {
                        setSelectedUser('');
                        setSelectedDepartment('');
                        setSelectedUserRole('');
                        setSelectedRole('DOCTOR');
                        setShowModal(true);
                    }}
                    className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                >
                    + Assign Staff
                </button>
            </div>

            <div className="space-y-2">
                {staff.length === 0 ? (
                    <p className="text-gray-500 text-center py-8">No staff assigned to this facility</p>
                ) : (
                    staff.map((person) => (
                        <div
                            key={person.id}
                            className="flex items-center justify-between p-3 border rounded-lg hover:bg-gray-50"
                        >
                            <div>
                                <div className="flex items-center space-x-2">
                                    <span className="font-medium text-gray-900">
                                        {person.firstName} {person.lastName}
                                    </span>
                                    <span className={`text-xs px-2 py-0.5 rounded-full ${getRoleBadge(person.role)}`}>
                                        {person.role}
                                    </span>
                                    <span className={`text-xs px-2 py-0.5 rounded-full ${
                                        person.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                    }`}>
                                        {person.isActive ? 'Active' : 'Inactive'}
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

            {/* Assign Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-xl font-bold text-gray-900">Assign Staff to {facilityName}</h2>
                            <button
                                type="button"
                                onClick={() => {
                                    setShowModal(false);
                                    setSelectedUser('');
                                    setSelectedDepartment('');
                                    setSelectedUserRole('');
                                }}
                                className="text-gray-400 hover:text-gray-600"
                            >
                                ✕
                            </button>
                        </div>

                        <form onSubmit={handleAssign}>
                            <div className="space-y-4">
                                {/* Select User */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">
                                        Select User <span className="text-red-500">*</span>
                                    </label>
                                    <select
                                        required
                                        value={selectedUser}
                                        onChange={(e) => handleUserChange(e.target.value)}
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

                                {/* Select Role */}
                                <div>
                                    <label className="block text-sm font-medium text-gray-700">
                                        Role <span className="text-red-500">*</span>
                                    </label>
                                    <select
                                        required
                                        value={selectedRole}
                                        onChange={(e) => {
                                            setSelectedRole(e.target.value);
                                            if (e.target.value !== 'DOCTOR') {
                                                setSelectedDepartment('');
                                            }
                                        }}
                                        className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    >
                                        <option value="DOCTOR">👨‍⚕️ Doctor</option>
                                        <option value="STAFF">👩‍💼 Staff</option>
                                        <option value="FACILITY_ADMIN">🏢 Facility Admin</option>
                                    </select>
                                    {selectedUserRole && selectedUserRole !== selectedRole && (
                                        <p className="text-xs text-yellow-600 mt-1">
                                            ⚠️ User's role will be updated from {selectedUserRole} to {selectedRole}
                                        </p>
                                    )}
                                </div>

                                {/* Department Section - ALWAYS SHOW WHEN ROLE IS DOCTOR */}
                                {selectedRole === 'DOCTOR' && (
                                    <div className="border-t border-gray-200 pt-4">
                                        <label className="block text-sm font-medium text-gray-700">
                                            Assign to Department <span className="text-xs text-gray-400">(optional)</span>
                                        </label>
                                        {departments.length > 0 ? (
                                            <select
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
                                                    ⚠️ No departments available. Please create a department first.
                                                </p>
                                                <button
                                                    type="button"
                                                    onClick={() => {
                                                        setShowModal(false);
                                                        navigate(`/admin/facility/${facilityId}/departments`);
                                                    }}
                                                    className="mt-2 text-sm text-primary-600 hover:text-primary-700 underline"
                                                >
                                                    + Create Department
                                                </button>
                                            </div>
                                        )}
                                        {selectedDepartment && departments.length > 0 && (
                                            <p className="text-xs text-green-600 mt-1">
                                                ✅ Will be assigned to: {departments.find(d => d.id === selectedDepartment)?.name}
                                            </p>
                                        )}
                                    </div>
                                )}

                                {/* Summary */}
                                <div className="p-3 bg-gray-50 rounded-md border border-gray-200">
                                    <p className="text-sm font-medium text-gray-700">Summary:</p>
                                    <ul className="text-sm text-gray-600 mt-1 space-y-1">
                                        <li>• User: {selectedUser ? allUsers.find(u => u.id === selectedUser)?.firstName + ' ' + allUsers.find(u => u.id === selectedUser)?.lastName : 'Not selected'}</li>
                                        <li>• Role: {selectedRole}</li>
                                        {selectedRole === 'DOCTOR' && (
                                            <li>• Department: {selectedDepartment ? departments.find(d => d.id === selectedDepartment)?.name : 'None selected'}</li>
                                        )}
                                        <li>• Facility: {facilityName}</li>
                                    </ul>
                                </div>
                            </div>

                            <div className="mt-6 flex space-x-3">
                                <button
                                    type="submit"
                                    disabled={isSubmitting || !selectedUser}
                                    className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    {isSubmitting ? 'Assigning...' : 'Assign Staff'}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setShowModal(false);
                                        setSelectedUser('');
                                        setSelectedDepartment('');
                                        setSelectedUserRole('');
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