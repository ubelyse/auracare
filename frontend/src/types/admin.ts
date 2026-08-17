// types/admin.ts

export interface FacilityTelemetry {
    id: string;
    name: string;
    code: string;
    address?: string;                    // ← ADDED: Optional address
    phone?: string;                      // ← ADDED: Optional phone
    email?: string;                      // ← ADDED: Optional email
    activePatients: number;
    doctorCount: number;                 // ← ADDED: Doctor count
    staffCount: number;
    avgWaitMinutes: number;
    doctorToPatientRatio: string;        // ← ADDED: Ratio string
    isActive: boolean;
    priorityDistribution: Record<string, number>;  // ← ADDED: Priority distribution
    departments: {
        id?: string;                     // ← ADDED: Optional id
        name: string;
        code: string;
        description?: string;            // ← ADDED: Optional description
        patients: number;
        active: boolean;
        doctorCount?: number;            // ← ADDED: Optional doctor count
    }[];
}

export interface TelemetryResponse {
    facilities: FacilityTelemetry[];
    totalPatients: number;
    totalStaff: number;
    activeFacilities: number;            // ← ADDED: Active facilities count
    averageWaitTime: number;             // ← ADDED: Average wait time
    updatedAt: string;
}