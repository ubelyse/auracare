// types/ticket.ts

export interface Ticket {
    id: string;
    ticketNumber: string;
    patientId: string;
    patient?: {                           // ← ADDED: Patient object
        id: string;
        firstName: string;
        lastName: string;
        email: string;
    };
    facilityId: string;
    facility?: {                          // ← ADDED: Facility object
        id: string;
        name: string;
        code: string;
    };
    departmentId: string;
    department?: {                        // ← ADDED: Department object
        id: string;
        name: string;
        code: string;
    };
    status: 'CHECKED_IN' | 'TRIAGED' | 'IN_CONSULTATION' | 'LAB_PENDING' | 'LAB_COMPLETED' | 'CONSULTATION_DONE' | 'PAYMENT_PENDING' | 'DISCHARGED' | 'CANCELLED';
    priority: 'EMERGENCY' | 'HIGH' | 'MEDIUM' | 'LOW';
    symptoms: string;
    sanitizedSymptoms: string;
    age?: number;
    gender?: 'MALE' | 'FEMALE' | 'OTHER';
    isPregnant?: boolean;
    temperature?: number;
    heartRate?: number;
    bloodPressureSystolic?: number;
    bloodPressureDiastolic?: number;
    insuranceType?: string;
    triageScore?: number;
    triageMethod?: string;
    aiConfidence?: number;
    estimatedWaitMinutes?: number;
    queuePosition?: number;
    checkedInAt: string;
    triagedAt?: string;
    assignedDoctorId?: string;
    assignedDoctor?: {                    // ← ADDED: Doctor object
        id: string;
        firstName: string;
        lastName: string;
        email: string;
    };
    // ===== ADDED: Health fields =====
    chronicConditions?: string;           // ← ADDED: Chronic conditions
    healthChanges?: string;               // ← ADDED: Health changes
    hasRecentSurgery?: boolean;           // ← ADDED: Recent surgery flag
    recentSurgeryDetails?: string;        // ← ADDED: Surgery details
    hasAllergies?: boolean;               // ← ADDED: Allergies flag
    allergiesDescription?: string;        // ← ADDED: Allergies description
    // ===========================
    active: boolean;
    createdAt?: string;                   // ← ADDED: Creation timestamp
    updatedAt?: string;                   // ← ADDED: Update timestamp
}

export interface CheckInRequest {
    facilityId: string;
    departmentId: string;
    doctorId?: string;                    // ← ADDED: Optional doctor selection
    symptoms: string;
    age?: number;
    gender?: 'MALE' | 'FEMALE' | 'OTHER';
    isPregnant?: boolean;
    temperature?: number;
    heartRate?: number;
    bloodPressureSystolic?: number;
    bloodPressureDiastolic?: number;
    insuranceType?: string;
    // ===== ADDED: Health fields =====
    healthChanges?: string;
    hasRecentSurgery?: boolean;
    recentSurgeryDetails?: string;
    hasNewAllergies?: boolean;
    newAllergiesDetails?: string;
    // ===========================
}

export type LabTestType =
    | 'COMPLETE_BLOOD_COUNT' | 'MALARIA_TEST' | 'URINALYSIS' | 'BLOOD_GLUCOSE'
    | 'HIV_TEST' | 'PREGNANCY_TEST' | 'LIVER_FUNCTION_TEST' | 'KIDNEY_FUNCTION_TEST'
    | 'STOOL_ANALYSIS' | 'COVID_19_TEST' | 'TYPHOID_TEST' | 'OTHER';

export const LAB_TEST_TYPE_LABELS: Record<LabTestType, string> = {
    COMPLETE_BLOOD_COUNT: 'Complete Blood Count',
    MALARIA_TEST: 'Malaria Test',
    URINALYSIS: 'Urinalysis',
    BLOOD_GLUCOSE: 'Blood Glucose',
    HIV_TEST: 'HIV Test',
    PREGNANCY_TEST: 'Pregnancy Test',
    LIVER_FUNCTION_TEST: 'Liver Function Test',
    KIDNEY_FUNCTION_TEST: 'Kidney Function Test',
    STOOL_ANALYSIS: 'Stool Analysis',
    COVID_19_TEST: 'COVID-19 Test',
    TYPHOID_TEST: 'Typhoid Test',
    OTHER: 'Other',
};