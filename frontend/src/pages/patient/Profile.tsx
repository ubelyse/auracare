import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useAuthStore } from '../../stores/authStore';
import { profileService } from '../../services/profile';  // ===== ADD: Use service instead of direct API

export const PatientProfile: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [isEditing, setIsEditing] = useState(false);
    const [isLoading, setIsLoading] = useState(true);  // ===== ADD: Loading state
    const [formData, setFormData] = useState({
        firstName: user?.firstName || '',
        lastName: user?.lastName || '',
        email: user?.email || '',
        phone: user?.phone || '',
        dateOfBirth: '',
        gender: '',
        chronicConditions: [] as string[],
        allergies: '',
        emergencyContactName: '',
        emergencyContactPhone: '',
        bloodType: ''
    });

    const chronicConditions = [
        'Diabetes', 'Hypertension', 'Heart Disease', 'Asthma',
        'Kidney Disease', 'Cancer', 'HIV/AIDS', 'Tuberculosis'
    ];

    useEffect(() => {
        let isMounted = true;

        const loadProfile = async () => {
            if (!isMounted) return;

            try {
                const data = await profileService.getProfile();
                if (isMounted) {
                    setFormData({
                        firstName: data.firstName || '',
                        lastName: data.lastName || '',
                        email: data.email || '',
                        phone: data.phone || '',
                        dateOfBirth: data.dateOfBirth || '',
                        gender: data.gender || '',
                        chronicConditions: data.chronicConditions ? data.chronicConditions.split(',').filter(Boolean) : [],
                        allergies: data.allergies || '',
                        emergencyContactName: data.emergencyContactName || '',
                        emergencyContactPhone: data.emergencyContactPhone || '',
                        bloodType: data.bloodType || ''
                    });
                }
            } catch (error) {
                if (isMounted) {
                    toast.error('Failed to load profile data');
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        loadProfile();

        return () => {
            isMounted = false;
        };
    }, []);

    const handleSave = async () => {
        try {
            await profileService.updateProfile({
                firstName: formData.firstName,
                lastName: formData.lastName,
                phone: formData.phone,
                dateOfBirth: formData.dateOfBirth || null,
                gender: formData.gender,
                chronicConditions: formData.chronicConditions.join(','),
                allergies: formData.allergies,
                emergencyContactName: formData.emergencyContactName,
                emergencyContactPhone: formData.emergencyContactPhone,
                bloodType: formData.bloodType
            });
            toast.success('Profile updated successfully!');
            setIsEditing(false);
        } catch (error: any) {
            toast.error(error.response?.data?.message || 'Failed to update profile');
        }
    };

    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading profile...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-3xl mx-auto p-6">
            <div className="bg-white rounded-lg shadow-lg p-8">
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900">👤 My Profile</h1>
                        <p className="text-sm text-gray-500">Manage your personal information</p>
                    </div>
                    <div className="flex gap-2">
                        <button
                            onClick={() => navigate('/patient/dashboard')}
                            className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                            ← Back
                        </button>
                        <button
                            onClick={() => setIsEditing(!isEditing)}
                            className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                        >
                            {isEditing ? 'Cancel' : '✏️ Edit Profile'}
                        </button>
                    </div>
                </div>

                <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700">First Name</label>
                            <input
                                type="text"
                                value={formData.firstName}
                                onChange={(e) => setFormData({...formData, firstName: e.target.value})}
                                disabled={!isEditing}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700">Last Name</label>
                            <input
                                type="text"
                                value={formData.lastName}
                                onChange={(e) => setFormData({...formData, lastName: e.target.value})}
                                disabled={!isEditing}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Email</label>
                        <input
                            type="email"
                            value={formData.email}
                            disabled
                            className="mt-1 block w-full rounded-md border-gray-300 bg-gray-100 shadow-sm"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Phone</label>
                        <input
                            type="tel"
                            value={formData.phone}
                            onChange={(e) => setFormData({...formData, phone: e.target.value})}
                            disabled={!isEditing}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700">Date of Birth</label>
                            <input
                                type="date"
                                value={formData.dateOfBirth}
                                onChange={(e) => setFormData({...formData, dateOfBirth: e.target.value})}
                                disabled={!isEditing}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700">Gender</label>
                            <select
                                value={formData.gender}
                                onChange={(e) => setFormData({...formData, gender: e.target.value})}
                                disabled={!isEditing}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                            >
                                <option value="">Select...</option>
                                <option value="MALE">Male</option>
                                <option value="FEMALE">Female</option>
                                <option value="OTHER">Other</option>
                            </select>
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Chronic Conditions</label>
                        <div className="mt-2 grid grid-cols-2 gap-2">
                            {chronicConditions.map((condition) => (
                                <label key={condition} className="flex items-center space-x-2">
                                    <input
                                        type="checkbox"
                                        checked={formData.chronicConditions.includes(condition)}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                setFormData({...formData, chronicConditions: [...formData.chronicConditions, condition]});
                                            } else {
                                                setFormData({...formData, chronicConditions: formData.chronicConditions.filter(c => c !== condition)});
                                            }
                                        }}
                                        disabled={!isEditing}
                                        className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                                    />
                                    <span className="text-sm text-gray-700">{condition}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Allergies</label>
                        <input
                            type="text"
                            value={formData.allergies}
                            onChange={(e) => setFormData({...formData, allergies: e.target.value})}
                            disabled={!isEditing}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                            placeholder="e.g., Penicillin, Peanuts"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700">Emergency Contact Name</label>
                            <input
                                type="text"
                                value={formData.emergencyContactName}
                                onChange={(e) => setFormData({...formData, emergencyContactName: e.target.value})}
                                disabled={!isEditing}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                                placeholder="Name"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700">Emergency Contact Phone</label>
                            <input
                                type="tel"
                                value={formData.emergencyContactPhone}
                                onChange={(e) => setFormData({...formData, emergencyContactPhone: e.target.value})}
                                disabled={!isEditing}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                                placeholder="Phone number"
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700">Blood Type</label>
                        <select
                            value={formData.bloodType}
                            onChange={(e) => setFormData({...formData, bloodType: e.target.value})}
                            disabled={!isEditing}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500 disabled:bg-gray-100"
                        >
                            <option value="">Select...</option>
                            <option value="A+">A+</option>
                            <option value="A-">A-</option>
                            <option value="B+">B+</option>
                            <option value="B-">B-</option>
                            <option value="AB+">AB+</option>
                            <option value="AB-">AB-</option>
                            <option value="O+">O+</option>
                            <option value="O-">O-</option>
                        </select>
                    </div>

                    {isEditing && (
                        <div className="pt-4 border-t">
                            <button
                                onClick={handleSave}
                                className="w-full py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700"
                            >
                                💾 Save Changes
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};