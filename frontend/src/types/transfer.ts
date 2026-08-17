// types/transfer.ts

export interface FacilityTransfer {
    id: string;
    ticketId: string;
    ticketNumber?: string;                    // ← ADDED: Ticket number for display
    fromFacilityId: string;
    toFacilityId: string;
    fromDepartmentId: string;
    toDepartmentId: string;
    transferReason: string;
    transferType: 'EMERGENCY' | 'ROUTINE' | 'SPECIALIST_REFERRAL' | 'PATIENT_REQUEST';
    status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED';
    initiatedBy: string;
    initiatedByName?: string;                 // ← ADDED: Name of who initiated
    approvedBy?: string;
    approvedByName?: string;                  // ← ADDED: Name of who approved
    approvedAt?: string;
    completedAt?: string;
    notes?: string;
    createdAt: string;
    fromFacilityName?: string;
    toFacilityName?: string;
    fromDepartmentName?: string;              // ← ADDED: From department name
    toDepartmentName?: string;                // ← ADDED: To department name
}