import { EventSourcePolyfill } from 'event-source-polyfill';

// ===== ADD: Types =====
interface TicketUpdate {
    ticketNumber: string;
    status: string;
    priority: string;
    queuePosition: number;
    estimatedWaitMinutes: number;
}

interface EmergencyAlert {
    message: string;
    ticketNumber: string;
    priority: string;
    options: {
        wait: boolean;
        internalTransfer: boolean;
        externalTransfer: boolean;
    };
}

const MAX_RETRIES = 5;
const BASE_RETRY_DELAY_MS = 5000;

class SSEService {
    private eventSource: EventSourcePolyfill | null = null;
    private ticketRetryCount = 0;
    private queueRetryCount = 0;

    connectToTicket(
        ticketNumber: string,
        onUpdate: (data: TicketUpdate) => void,
        onEmergency?: (data: EmergencyAlert) => void
    ): EventSourcePolyfill | null {
        try {
            const token = this.getToken();
            if (!token) {
                console.warn('SSE: No token found for ticket:', ticketNumber);
                return null;
            }

            console.log('🔴🔴🔴 Connecting to SSE for ticket:', ticketNumber);

            // ===== FIX: Send token as URL parameter =====
            this.eventSource = new EventSourcePolyfill(
                `/api/sse/ticket/${ticketNumber}?token=${token}`,
                {
                    headers: {}
                }
            );
            // ==========================================

            this.eventSource.onopen = () => {
                this.ticketRetryCount = 0;
                console.log('✅ SSE connection established for ticket:', ticketNumber);
            };

            this.eventSource.addEventListener('ticket-update', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data) as TicketUpdate;
                    onUpdate(data);
                } catch (e) {
                    // Silent fail - parsing errors are not user-facing
                }
            });

            this.eventSource.addEventListener('emergency-alert', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data) as EmergencyAlert;
                    if (onEmergency) {
                        console.log('🚨 SSE emergency-alert received:', data);
                        onEmergency(data);
                    }
                } catch (e) {
                    console.error('SSE: error parsing emergency alert:', e);
                }
            });

            this.eventSource.addEventListener('error', (event) => {
                console.warn('SSE error for ticket:', ticketNumber, event);
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    if (this.ticketRetryCount >= MAX_RETRIES) {
                        console.error('SSE: max retries reached for ticket:', ticketNumber);
                        return;
                    }
                    this.ticketRetryCount += 1;
                    const delay = BASE_RETRY_DELAY_MS * this.ticketRetryCount;
                    console.log(`SSE: retrying connection in ${delay}ms (attempt ${this.ticketRetryCount})`);
                    setTimeout(() => {
                        this.connectToTicket(ticketNumber, onUpdate, onEmergency);
                    }, delay);
                }
            });

            return this.eventSource;
        } catch (error) {
            console.error('SSE connection error:', error);
            return null;
        }
    }

    connectToQueue(
        facilityId: string,
        departmentId: string,
        onUpdate: (data: any) => void,
        onEmergency: (data: any) => void
    ): EventSourcePolyfill | null {
        try {
            const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
            if (!uuidRegex.test(facilityId) || !uuidRegex.test(departmentId)) {
                console.warn('SSE: invalid UUID format for queue connection');
                return null;
            }

            const token = this.getToken();
            if (!token) {
                console.warn('SSE: No token found for queue connection');
                return null;
            }

            const clientId = `${facilityId}-${departmentId}-${Date.now()}`;

            // ===== FIX: Send token as URL parameter for queue too =====
            this.eventSource = new EventSourcePolyfill(
                `/api/sse/queue/${facilityId}/${departmentId}?clientId=${clientId}&token=${token}`,
                {
                    headers: {}
                }
            );
            // ========================================================

            this.eventSource.onopen = () => {
                this.queueRetryCount = 0;
                console.log('✅ SSE queue connection established for:', facilityId, departmentId);
            };

            this.eventSource.addEventListener('queue-update', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data);
                    onUpdate(data);
                } catch (e) {
                    // Silent fail
                }
            });

            this.eventSource.addEventListener('emergency-alert', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data);
                    onEmergency(data);
                } catch (e) {
                    // Silent fail
                }
            });

            this.eventSource.addEventListener('error', (event) => {
                console.warn('SSE queue error:', event);
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    if (this.queueRetryCount >= MAX_RETRIES) {
                        console.error('SSE: max retries reached for queue connection');
                        return;
                    }
                    this.queueRetryCount += 1;
                    const delay = BASE_RETRY_DELAY_MS * this.queueRetryCount;
                    console.log(`SSE: retrying queue connection in ${delay}ms (attempt ${this.queueRetryCount})`);
                    setTimeout(() => {
                        this.connectToQueue(facilityId, departmentId, onUpdate, onEmergency);
                    }, delay);
                }
            });

            return this.eventSource;
        } catch (error) {
            console.error('SSE queue connection error:', error);
            return null;
        }
    }

    // ===== Helper method to get token =====
    private getToken(): string | null {
        let token = localStorage.getItem('accessToken');

        if (!token) {
            try {
                const authStorage = localStorage.getItem('auth-storage');
                if (authStorage) {
                    const parsed = JSON.parse(authStorage);
                    if (parsed.state?.accessToken) {
                        token = parsed.state.accessToken;
                    }
                }
            } catch (e) {
                // Silent fail
            }
        }

        if (!token) {
            console.warn('SSE: no access token found');
        }

        return token;
    }

    disconnect(): void {
        if (this.eventSource) {
            try {
                this.eventSource.close();
            } catch (e) {
                // Silent fail
            }
            this.eventSource = null;
        }
        this.ticketRetryCount = 0;
        this.queueRetryCount = 0;
        console.log('SSE: disconnected');
    }
}

export const sseService = new SSEService();