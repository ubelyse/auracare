// types/billing.ts

export interface BillingItem {
    serviceCode: string;
    description: string;
    amount: number;
}

export interface Billing {
    id: string;
    ticketId: string;
    patientId: string;
    facilityId: string;
    invoiceNumber: string;
    totalAmount: number;
    insuranceAmount: number;
    patientAmount: number;
    insuranceType: string;
    coPayPercentage: number;
    status: 'PENDING' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'REFUNDED';
    paymentMethod?: string;
    transactionId?: string;
    paymentReference?: string;
    items: BillingItem[];
    issuedAt: string;
    dueDate: string;
    paidAt?: string;
    createdAt: string;      // ← ADDED: Creation timestamp
    updatedAt?: string;     // ← ADDED: Last update timestamp
}

export interface PaymentRequest {
    billingId: string;
    paymentMethod: string;
    transactionId?: string;
}

export interface PaymentResult {
    success: boolean;
    message: string;
    transactionId?: string;
}