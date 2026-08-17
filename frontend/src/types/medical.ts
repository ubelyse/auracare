// types/medical.ts

export interface MedicalRecord {
    id: string;
    patientId: string;
    doctorId?: string;                         // ← ADDED: Doctor ID
    doctorName?: string;                       // ← ADDED: Doctor name
    recordType: 'CONSULTATION' | 'LAB_RESULT' | 'PRESCRIPTION' | 'BILLING' | 'SURGERY' | 'EMERGENCY' | 'ROUTINE';  // ← ADDED: More types
    recordDate: string;
    summary: string;
    details: string;
    diagnosis?: string;                        // ← ADDED: Diagnosis field
    treatment?: string;                        // ← ADDED: Treatment field
    notes?: string;                            // ← ADDED: Notes field
    metadata: string;
    createdAt: string;
    updatedAt?: string;                        // ← ADDED: Update timestamp
}

export interface Consultation {
    id: string;
    ticketId: string;
    doctorId: string;
    doctorName?: string;                       // ← ADDED: Doctor name
    diagnosis: string;
    notes: string;
    prescription: string;
    labOrders: string;
    labResults: string;
    symptoms: string;
    diagnosisCode: string;
    followUpDate?: string;                     // ← CHANGED: Optional
    startedAt: string;
    completedAt?: string;                      // ← CHANGED: Optional
    createdAt?: string;                        // ← ADDED: Creation timestamp
    updatedAt?: string;                        // ← ADDED: Update timestamp
}