import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import toast from 'react-hot-toast';
import { ticketService } from '../../services/ticket';
import { useAuthStore } from '../../stores/authStore';
import { profileService } from '../../services/profile';  // ← ADD THIS

// ===== Facility type =====
interface Facility {
    id: string;
    name: string;
    code: string;
    active: boolean;
}

// ===== Queue Preview types =====
interface QueuePreview {
    facilityName: string;
    departmentName: string;
    totalPatientsAhead: number;
    bookedPatientsAhead: number;
    walkInPatientsAhead: number;
    estimatedPosition: number;
    estimatedWaitMinutes: number;
    patientPriority: string;
    isBooked: boolean;
    hasUpcomingBooking: boolean;
    patientsAhead: Array<{
        position: number;
        patientName: string;
        priority: string;
        estimatedWaitMinutes: number;
        isBooked: boolean;
        ticketNumber: string;
    }>;
    positionEstimates: Array<{
        position: number;
        estimatedWaitMinutes: number;
    }>;
    currentTime: string;
}

// Safe boolean transformer to prevent HTML radio strings like "false" from coercing to true
const booleanTransformer = z.preprocess((val) => {
    if (typeof val === 'string') {
        if (val.toLowerCase() === 'true') return true;
        if (val.toLowerCase() === 'false') return false;
    }
    return Boolean(val);
}, z.boolean().optional());

const checkInSchema = z.object({
    facilityId: z.string().uuid('Please select a facility'),
    departmentId: z.string().uuid('Please select a department'),
    doctorId: z.string().uuid('Please select a doctor'),
    // doctorId: z.string().optional().transform(val => val === '' ? undefined : val),
    symptoms: z.string().min(5, 'Please describe your symptoms'),
    insuranceType: z.string().optional(),
    isPregnant: booleanTransformer,
    healthChanges: z.string().optional(),
    hasRecentSurgery: booleanTransformer,
    recentSurgeryDetails: z.string().optional(),
    hasNewAllergies: booleanTransformer,
    newAllergiesDetails: z.string().optional(),
});

type CheckInFormData = z.infer<typeof checkInSchema>;

interface Department {
    id: string;
    name: string;
    code: string;
    description: string;
    active: boolean;
    availableDoctors: {
        id: string;
        firstName: string;
        lastName: string;
        email: string;
    }[];
}

export const CheckIn: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuthStore();
    const [isLoading, setIsLoading] = useState(false);
    const [facilities, setFacilities] = useState<Facility[]>([]);
    const [departments, setDepartments] = useState<Department[]>([]);
    const [showPregnant, setShowPregnant] = useState(false);
    const [showSurgeryDetails, setShowSurgeryDetails] = useState(false);
    const [showAllergyDetails, setShowAllergyDetails] = useState(false);

    // ===== User profile state =====
    const [userProfile, setUserProfile] = useState<any>(null);
    const [isProfileLoading, setIsProfileLoading] = useState(true);

    // ===== Queue preview state =====
    const [queuePreview, setQueuePreview] = useState<QueuePreview | null>(null);
    const [isLoadingPreview, setIsLoadingPreview] = useState(false);

    const { register, handleSubmit, watch, setValue, formState: { errors } } = useForm<CheckInFormData>({
        resolver: zodResolver(checkInSchema),
        mode: 'onChange',
        defaultValues: {
            doctorId: '',  // No longer optional
        }
    });

    const selectedFacility = watch('facilityId');
    const selectedDepartment = watch('departmentId');
    const selectedDoctor = watch('doctorId');  // ← ADD THIS
    const hasRecentSurgery = watch('hasRecentSurgery');
    const hasNewAllergies = watch('hasNewAllergies');

    // ===== Use gender from profile, fallback to user =====
    const gender = userProfile?.gender || user?.gender || '';

    // ===== Load user profile =====
    useEffect(() => {
        const loadProfile = async () => {
            try {
                const profile = await profileService.getProfile();
                setUserProfile(profile);
            } catch (error) {
                console.error('Failed to load profile:', error);
            } finally {
                setIsProfileLoading(false);
            }
        };
        loadProfile();
    }, []);

    // ===== Check if user is logged in =====
    useEffect(() => {
        if (!user) {
            navigate('/login');
            return;
        }
        loadFacilities();
    }, [user, navigate]);

    // ===== Fetch queue preview when facility and department are selected =====
    // ===== Fetch queue preview when facility, department, and doctor are selected =====
    useEffect(() => {
        if (selectedFacility && selectedDepartment && selectedDoctor) {
            fetchQueuePreview();
        } else {
            setQueuePreview(null);
        }
    }, [selectedFacility, selectedDepartment, selectedDoctor]);

    // FIX: race condition. If a user changed facilityId twice in quick
    // succession, the two loadDepartments() network calls could resolve
    // out of order -- an older request landing after a newer one would
    // silently overwrite `departments` with the WRONG facility's list,
    // while `departmentId`/`doctorId` had already been reset for the
    // CURRENT facility. That made it possible to select a doctor who
    // belonged to a previously-selected facility, not the one actually
    // submitted -- which is what produced "Selected doctor is not
    // available in this department" server-side, since two facilities
    // can have same-named departments (e.g. "General Medicine") with
    // different IDs and different doctor rosters.
    //
    // The `cancelled` flag below ensures only the response matching the
    // facility currently selected at resolution time is applied.
    useEffect(() => {
        if (selectedFacility) {
            let cancelled = false;

            (async () => {
                try {
                    const data = await ticketService.getDepartmentsWithDoctors(selectedFacility);
                    if (!cancelled) {
                        setDepartments(Array.isArray(data) ? data : []);
                    }
                } catch (error) {
                    if (!cancelled) {
                        toast.error('Failed to load departments');
                        setDepartments([]);
                    }
                }
            })();

            setValue('departmentId', '');
            setValue('doctorId', '');

            return () => {
                cancelled = true;
            };
        } else {
            setDepartments([]);
        }
    }, [selectedFacility, setValue]);

    useEffect(() => {
        setValue('doctorId', '');
    }, [selectedDepartment, setValue]);

    // ===== Pregnancy field logic - NOW WORKS WITH PROFILE =====
    useEffect(() => {
        if (gender === 'FEMALE') {
            setShowPregnant(true);
        } else {
            setShowPregnant(false);
        }
    }, [gender]);

    useEffect(() => {
        if (hasRecentSurgery !== true) {
            setValue('recentSurgeryDetails', '');
        }
        setShowSurgeryDetails(hasRecentSurgery === true);
    }, [hasRecentSurgery, setValue]);

    useEffect(() => {
        if (hasNewAllergies !== true) {
            setValue('newAllergiesDetails', '');
        }
        setShowAllergyDetails(hasNewAllergies === true);
    }, [hasNewAllergies, setValue]);

    const loadFacilities = async () => {
        try {
            const data = await ticketService.getFacilities();
            setFacilities(Array.isArray(data) ? data : []);
        } catch (error) {
            toast.error('Failed to load facilities');
            setFacilities([]);
        }
    };

    // ===== Fetch queue preview =====
    // ===== Fetch queue preview =====
    const fetchQueuePreview = async () => {
        if (!selectedFacility || !selectedDepartment || !selectedDoctor) return;  // ← Added doctor check

        setIsLoadingPreview(true);
        try {
            const token = localStorage.getItem('token') || localStorage.getItem('accessToken');

            if (!token) {
                console.warn('No token found for queue preview');
                setQueuePreview(null);
                return;
            }

            const params = new URLSearchParams({
                facilityId: selectedFacility,
                departmentId: selectedDepartment,
                doctorId: selectedDoctor  // ← ADD THIS
            });

            const response = await fetch(`/api/checkin/preview?${params}`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to fetch queue preview');
            }

            const data = await response.json();
            setQueuePreview(data);
        } catch (error) {
            console.error('Failed to fetch queue preview:', error);
            setQueuePreview(null);
        } finally {
            setIsLoadingPreview(false);
        }
    };

    const getSelectedDepartment = () => {
        return departments.find(d => d.id === selectedDepartment);
    };

    const getDoctorsForDepartment = () => {
        const dept = getSelectedDepartment();
        return dept?.availableDoctors || [];
    };

    const onSubmit = async (data: CheckInFormData) => {
        if (!user) {
            toast.error('Please login first');
            return;
        }

        if (!data.doctorId || data.doctorId.trim() === '') {
            toast.error('Please select a doctor');
            return;
        }

        setIsLoading(true);
        try {
            const requestData: any = {
                facilityId: data.facilityId,
                departmentId: data.departmentId,
                symptoms: data.symptoms,
                insuranceType: data.insuranceType || 'MUTUELLE',
                isPregnant: Boolean(data.isPregnant),
                healthChanges: data.healthChanges || '',
                hasRecentSurgery: Boolean(data.hasRecentSurgery),
                recentSurgeryDetails: data.recentSurgeryDetails || '',
                hasNewAllergies: Boolean(data.hasNewAllergies),
                newAllergiesDetails: data.newAllergiesDetails || '',
            };

            if (data.doctorId && data.doctorId.trim() !== '') {
                // requestData.doctorId = data.doctorId;
                requestData.doctorId = data.doctorId;
            } else {
                requestData.doctorId = null;
            }

            const ticket = await ticketService.initiateCheckIn(requestData);

            if (!ticket || !ticket.ticketNumber) {
                toast.error('Check-in failed: Invalid response from server');
                return;
            }

            toast.success(`Check-in successful! Ticket: ${ticket.ticketNumber}`);
            navigate(`/patient/queue/${ticket.ticketNumber}`);
        } catch (error: any) {
            toast.error(error.response?.data?.message || error.response?.data?.error || 'Check-in failed');
        } finally {
            setIsLoading(false);
        }
    };

    const selectedDept = getSelectedDepartment();
    const availableDoctors = getDoctorsForDepartment();

    if (isProfileLoading) {
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
                <h1 className="text-2xl font-bold text-gray-900 mb-6">Patient Check-In</h1>

                <div className="mb-4 p-4 bg-blue-50 rounded-lg">
                    <p className="text-sm text-blue-800">
                        <span className="font-medium">Welcome back, {user?.firstName}!</span>
                        <br />
                        Please select your preferred department and doctor.
                    </p>
                </div>

                {/* ===== Queue Preview Section ===== */}
                {isLoadingPreview ? (
                    <div className="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
                        <div className="flex items-center justify-center">
                            <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600 mr-2"></div>
                            <span className="text-sm text-gray-600">Loading queue status...</span>
                        </div>
                    </div>
                ) : queuePreview ? (
                    <div className="mb-6 p-4 bg-blue-50 rounded-lg border border-blue-200">
                        <div className="flex items-start justify-between">
                            <div className="flex-1">
                                <h4 className="font-semibold text-blue-800 flex items-center">
                                    <span className="text-lg mr-2">📋</span>
                                    Queue Status Preview
                                </h4>

                                {/* Stats Grid */}
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-3">
                                    <div className="bg-white rounded-md p-2 text-center">
                                        <p className="text-xs text-gray-500">Your Position</p>
                                        <p className="text-xl font-bold text-blue-600">
                                            #{queuePreview.estimatedPosition}
                                        </p>
                                    </div>
                                    <div className="bg-white rounded-md p-2 text-center">
                                        <p className="text-xs text-gray-500">Est. Wait Time</p>
                                        <p className="text-xl font-bold text-blue-600">
                                            {queuePreview.estimatedWaitMinutes} min
                                        </p>
                                    </div>
                                    <div className="bg-white rounded-md p-2 text-center">
                                        <p className="text-xs text-gray-500">Patients Ahead</p>
                                        <p className="text-xl font-bold text-blue-600">
                                            {queuePreview.totalPatientsAhead}
                                        </p>
                                    </div>
                                    <div className="bg-white rounded-md p-2 text-center">
                                        <p className="text-xs text-gray-500">Priority</p>
                                        <p className={`text-xl font-bold ${
                                            queuePreview.patientPriority === 'HIGH'
                                                ? 'text-green-600'
                                                : 'text-gray-600'
                                        }`}>
                                            {queuePreview.patientPriority === 'HIGH' ? '🟢 HIGH' : '🟠 LOW'}
                                        </p>
                                    </div>
                                </div>

                                {/* ===== PATIENT-FRIENDLY MESSAGE FOR POSITION #1 ===== */}
                                {queuePreview.estimatedPosition === 1 && (
                                    <div className="mt-3 p-3 bg-green-50 rounded-md border border-green-200">
                                        <p className="text-sm text-green-800 flex items-center">
                                            <span className="text-lg mr-2">🎯</span>
                                            <span>
                                                <strong>You're next in line!</strong> If you check in now, you'll be seen in about {queuePreview.estimatedWaitMinutes} minutes.
                                                {queuePreview.totalPatientsAhead === 0 && (
                                                    <span className="block text-xs text-green-600 mt-1">
                                                        ✅ No patients ahead of you!
                                                    </span>
                                                )}
                                            </span>
                                        </p>
                                    </div>
                                )}

                                {/* ===== PATIENT-FRIENDLY MESSAGE FOR POSITION 2-3 ===== */}
                                {queuePreview.estimatedPosition >= 2 && queuePreview.estimatedPosition <= 3 && (
                                    <div className="mt-3 p-3 bg-blue-50 rounded-md border border-blue-200">
                                        <p className="text-sm text-blue-800 flex items-center">
                                            <span className="text-lg mr-2">📋</span>
                                            <span>
                                                You're #{queuePreview.estimatedPosition} in line with about {queuePreview.estimatedWaitMinutes} minutes wait.
                                                <span className="block text-xs text-blue-600 mt-1">
                                                    ⏱️ Please stay nearby - your turn is coming soon!
                                                </span>
                                            </span>
                                        </p>
                                    </div>
                                )}

                                {/* ===== PATIENT-FRIENDLY MESSAGE FOR POSITION 4+ ===== */}
                                {queuePreview.estimatedPosition >= 4 && (
                                    <div className="mt-3 p-3 bg-gray-50 rounded-md border border-gray-200">
                                        <p className="text-sm text-gray-700 flex items-center">
                                            <span className="text-lg mr-2">💡</span>
                                            <span>
                                                You're #{queuePreview.estimatedPosition} in line. Estimated wait: {queuePreview.estimatedWaitMinutes} minutes.
                                                <span className="block text-xs text-gray-500 mt-1">
                                                    ☕ You have time to relax, get a drink, or check your phone.
                                                </span>
                                            </span>
                                        </p>
                                    </div>
                                )}

                                {/* Show breakdown */}
                                {queuePreview.totalPatientsAhead > 0 && (
                                    <div className="mt-3 text-sm text-blue-700">
                                        <span className="font-medium">Breakdown:</span>
                                        <span className="ml-2">
                                            {queuePreview.bookedPatientsAhead} booked patients
                                            {queuePreview.walkInPatientsAhead > 0 &&
                                                `, ${queuePreview.walkInPatientsAhead} walk-in patients`
                                            }
                                        </span>
                                        {queuePreview.hasUpcomingBooking && (
                                            <span className="ml-2 text-yellow-600">
                                                ⚠️ Upcoming booking detected
                                            </span>
                                        )}
                                    </div>
                                )}

                                {/* Show patients ahead if any */}
                                {queuePreview.patientsAhead && queuePreview.patientsAhead.length > 0 && (
                                    <div className="mt-3">
                                        <p className="text-xs font-medium text-blue-700">Patients ahead:</p>
                                        <div className="mt-1 space-y-1">
                                            {queuePreview.patientsAhead.slice(0, 5).map((patient) => (
                                                <div key={patient.position} className="flex items-center justify-between text-xs">
                                                    <span className="text-gray-600">
                                                        #{patient.position} - {patient.patientName}
                                                    </span>
                                                    <span className={`px-2 py-0.5 rounded-full text-xs ${
                                                        patient.priority === 'HIGH'
                                                            ? 'bg-green-100 text-green-700'
                                                            : 'bg-gray-100 text-gray-600'
                                                    }`}>
                                                        {patient.priority === 'HIGH' ? '🟢 Booked' : '🟠 Walk-in'}
                                                    </span>
                                                    <span className="text-gray-500">
                                                        {patient.estimatedWaitMinutes} min
                                                    </span>
                                                </div>
                                            ))}
                                            {queuePreview.patientsAhead.length > 5 && (
                                                <p className="text-xs text-gray-400">
                                                    +{queuePreview.patientsAhead.length - 5} more patients
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                )}

                                {/* Wait time chart */}
                                {queuePreview.positionEstimates && queuePreview.positionEstimates.length > 0 && (
                                    <div className="mt-3">
                                        <p className="text-xs font-medium text-blue-700">Wait time by position:</p>
                                        <div className="mt-1 space-y-1">
                                            {queuePreview.positionEstimates.slice(0, 5).map((pos) => (
                                                <div key={pos.position} className="flex items-center">
                                                    <span className="text-xs text-gray-500 w-20">
                                                        #{pos.position}
                                                    </span>
                                                    <div className="flex-1 mx-2">
                                                        <div
                                                            className={`h-4 rounded-md transition-all ${
                                                                pos.position === queuePreview.estimatedPosition
                                                                    ? 'bg-blue-500'
                                                                    : pos.position <= 2
                                                                        ? 'bg-green-400'
                                                                        : pos.position <= 4
                                                                            ? 'bg-yellow-400'
                                                                            : 'bg-gray-300'
                                                            }`}
                                                            style={{
                                                                width: `${Math.min((pos.estimatedWaitMinutes / 60) * 100, 100)}%`
                                                            }}
                                                        />
                                                    </div>
                                                    <span className="text-xs text-gray-500 w-16 text-right">
                                                        {pos.estimatedWaitMinutes} min
                                                    </span>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                ) : selectedFacility && selectedDepartment ? (
                    <div className="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
                        <p className="text-sm text-gray-500 text-center">
                            Unable to load queue status. Please continue with check-in.
                        </p>
                    </div>
                ) : null}

                <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                    {/* Facility Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Select Facility *
                        </label>
                        <select
                            {...register('facilityId')}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={isLoading}
                        >
                            <option value="">Select a facility</option>
                            {Array.isArray(facilities) && facilities.map((facility) => (
                                <option key={facility.id} value={facility.id}>
                                    {facility.name}
                                </option>
                            ))}
                        </select>
                        {errors.facilityId && (
                            <p className="text-red-500 text-xs mt-1">{errors.facilityId.message}</p>
                        )}
                    </div>

                    {/* Department Selection */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Select Department *
                        </label>
                        <select
                            {...register('departmentId')}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={!selectedFacility || isLoading}
                        >
                            <option value="">{selectedFacility ? 'Select a department...' : 'Please select a facility first'}</option>
                            {Array.isArray(departments) && departments.map((dept) => (
                                <option key={dept.id} value={dept.id}>
                                    {dept.name} ({dept.availableDoctors?.length || 0} doctors available)
                                </option>
                            ))}
                        </select>
                        {errors.departmentId && (
                            <p className="text-red-500 text-xs mt-1">{errors.departmentId.message}</p>
                        )}
                    </div>

                    {/* Doctor Selection */}
                    {/* Doctor Selection - Now MANDATORY */}
                    {selectedDepartment && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700">
                                Select Doctor *
                            </label>
                            {availableDoctors.length > 0 ? (
                                <select
                                    {...register('doctorId')}
                                    className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                    disabled={isLoading}
                                >
                                    <option value="">Select a doctor...</option>  {/* ← Changed */}
                                    {availableDoctors.map((doctor) => (
                                        <option key={doctor.id} value={doctor.id}>
                                            Dr. {doctor.firstName} {doctor.lastName}
                                        </option>
                                    ))}
                                </select>
                            ) : (
                                <div className="mt-1 p-3 bg-yellow-50 rounded-md border border-yellow-200">
                                    <p className="text-sm text-yellow-700">
                                        ⚠️ No doctors currently available in this department.
                                    </p>
                                </div>
                            )}
                            {errors.doctorId && (
                                <p className="text-red-500 text-xs mt-1">{errors.doctorId.message}</p>
                            )}
                        </div>
                    )}

                    {/* Insurance */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Insurance Type
                        </label>
                        <select
                            {...register('insuranceType')}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            disabled={isLoading}
                        >
                            <option value="">Select insurance</option>
                            <option value="MUTUELLE">Mutuelle de Santé</option>
                            <option value="RSSB">RSSB</option>
                            <option value="MMI">MMI</option>
                            <option value="Uninsured">None</option>
                        </select>
                    </div>

                    <hr className="my-6" />
                    <h2 className="text-lg font-semibold text-gray-900">Current Health Status</h2>

                    {/* Symptoms */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Describe Your Symptoms *
                        </label>
                        <textarea
                            {...register('symptoms')}
                            rows={4}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            placeholder="e.g., High fever, severe headache, chills for two days..."
                            disabled={isLoading}
                        />
                        {errors.symptoms && (
                            <p className="text-red-500 text-xs mt-1">{errors.symptoms.message}</p>
                        )}
                    </div>

                    {/* ===== PREGNANCY - NOW WORKS ===== */}
                    {showPregnant && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700">
                                Are you currently pregnant?
                            </label>
                            <div className="mt-1 flex space-x-4">
                                <label className="inline-flex items-center">
                                    <input
                                        type="radio"
                                        {...register('isPregnant')}
                                        value="true"
                                        className="rounded-full border-gray-300 text-primary-600 focus:ring-primary-500"
                                        disabled={isLoading}
                                    />
                                    <span className="ml-2 text-sm text-gray-600">Yes</span>
                                </label>
                                <label className="inline-flex items-center">
                                    <input
                                        type="radio"
                                        {...register('isPregnant')}
                                        value="false"
                                        className="rounded-full border-gray-300 text-primary-600 focus:ring-primary-500"
                                        disabled={isLoading}
                                    />
                                    <span className="ml-2 text-sm text-gray-600">No</span>
                                </label>
                            </div>
                        </div>
                    )}

                    {/* Health Changes */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Any changes to your health since your last visit?
                        </label>
                        <textarea
                            {...register('healthChanges')}
                            rows={2}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            placeholder="e.g., New symptoms, change in medication..."
                            disabled={isLoading}
                        />
                    </div>

                    {/* Recent Surgery */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Have you had any recent surgery?
                        </label>
                        <div className="mt-1 flex space-x-4">
                            <label className="inline-flex items-center">
                                <input
                                    type="radio"
                                    {...register('hasRecentSurgery')}
                                    value="true"
                                    className="rounded-full border-gray-300 text-primary-600 focus:ring-primary-500"
                                    disabled={isLoading}
                                />
                                <span className="ml-2 text-sm text-gray-600">Yes</span>
                            </label>
                            <label className="inline-flex items-center">
                                <input
                                    type="radio"
                                    {...register('hasRecentSurgery')}
                                    value="false"
                                    className="rounded-full border-gray-300 text-primary-600 focus:ring-primary-500"
                                    disabled={isLoading}
                                />
                                <span className="ml-2 text-sm text-gray-600">No</span>
                            </label>
                        </div>
                    </div>

                    {/* Surgery Details */}
                    {showSurgeryDetails && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700">
                                Please provide details about the surgery
                            </label>
                            <input
                                type="text"
                                {...register('recentSurgeryDetails')}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="What surgery? When?"
                                disabled={isLoading}
                            />
                        </div>
                    )}

                    {/* New Allergies */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700">
                            Do you have any new allergies?
                        </label>
                        <div className="mt-1 flex space-x-4">
                            <label className="inline-flex items-center">
                                <input
                                    type="radio"
                                    {...register('hasNewAllergies')}
                                    value="true"
                                    className="rounded-full border-gray-300 text-primary-600 focus:ring-primary-500"
                                    disabled={isLoading}
                                />
                                <span className="ml-2 text-sm text-gray-600">Yes</span>
                            </label>
                            <label className="inline-flex items-center">
                                <input
                                    type="radio"
                                    {...register('hasNewAllergies')}
                                    value="false"
                                    className="rounded-full border-gray-300 text-primary-600 focus:ring-primary-500"
                                    disabled={isLoading}
                                />
                                <span className="ml-2 text-sm text-gray-600">No</span>
                            </label>
                        </div>
                    </div>

                    {/* Allergy Details */}
                    {showAllergyDetails && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700">
                                Please describe your new allergies
                            </label>
                            <input
                                type="text"
                                {...register('newAllergiesDetails')}
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                                placeholder="e.g., Penicillin, Peanuts..."
                                disabled={isLoading}
                            />
                        </div>
                    )}

                    {/* Submit */}
                    <div className="pt-4">
                        <button
                            type="submit"
                            disabled={isLoading}
                            className="w-full flex justify-center py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {isLoading ? 'Processing Check-In...' : 'Check In'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};