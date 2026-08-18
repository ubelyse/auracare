import { EventSourcePolyfill } from 'event-source-polyfill';

// ===== Types =====
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
    private reconnectTimeout: NodeJS.Timeout | null = null;

    // ==================== CONNECT TO TICKET ====================

    /**
     * Connect to SSE for a specific ticket
     * Returns an unsubscribe function that closes the connection
     */
    connectToTicket(
        ticketNumber: string,
        onUpdate: (data: TicketUpdate) => void,
        onEmergency?: (data: EmergencyAlert) => void
    ): () => void {
        // Clear any existing connection
        this.disconnect();

        try {
            const token = this.getToken();
            if (!token) {
                console.warn('SSE: No token found for ticket:', ticketNumber);
                return () => {};
            }

            console.log('📡 Connecting to SSE for ticket:', ticketNumber);

            this.eventSource = new EventSourcePolyfill(
                `/api/sse/ticket/${ticketNumber}?token=${token}`,
                { headers: {} }
            );

            this.eventSource.onopen = () => {
                this.ticketRetryCount = 0;
                console.log('✅ SSE connection established for ticket:', ticketNumber);
            };

            // 🔥 FIXED: Added explicit event type
            this.eventSource.addEventListener('ticket-update', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data) as TicketUpdate;
                    onUpdate(data);
                } catch (e) {
                    // Silent fail - parsing errors are not user-facing
                }
            });

            // 🔥 FIXED: Added explicit event type
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

            // 🔥 FIXED: Added explicit event type
            this.eventSource.addEventListener('error', (event: Event) => {
                console.warn('SSE error for ticket:', ticketNumber, event);
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    if (this.ticketRetryCount >= MAX_RETRIES) {
                        console.error('SSE: max retries reached for ticket:', ticketNumber);
                        return;
                    }
                    this.ticketRetryCount += 1;
                    const delay = BASE_RETRY_DELAY_MS * this.ticketRetryCount;
                    console.log(`SSE: retrying connection in ${delay}ms (attempt ${this.ticketRetryCount})`);

                    // Clear any existing timeout
                    if (this.reconnectTimeout) {
                        clearTimeout(this.reconnectTimeout);
                    }

                    this.reconnectTimeout = setTimeout(() => {
                        this.connectToTicket(ticketNumber, onUpdate, onEmergency);
                    }, delay);
                }
            });

        } catch (error) {
            console.error('SSE connection error:', error);
        }

        // Return an unsubscribe function
        return () => {
            this.disconnect();
        };
    }

    // ==================== CONNECT TO QUEUE ====================

    /**
     * Connect to SSE for a queue
     * Returns an unsubscribe function that closes the connection
     */
    connectToQueue(
        facilityId: string,
        departmentId: string,
        onUpdate: (data: any) => void,
        onEmergency: (data: any) => void
    ): () => void {
        // Clear any existing connection
        this.disconnect();

        try {
            const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
            if (!uuidRegex.test(facilityId) || !uuidRegex.test(departmentId)) {
                console.warn('SSE: invalid UUID format for queue connection');
                return () => {};
            }

            const token = this.getToken();
            if (!token) {
                console.warn('SSE: No token found for queue connection');
                return () => {};
            }

            const clientId = `${facilityId}-${departmentId}-${Date.now()}`;

            console.log('📡 Connecting to SSE queue:', facilityId, departmentId);

            this.eventSource = new EventSourcePolyfill(
                `/api/sse/queue/${facilityId}/${departmentId}?clientId=${clientId}&token=${token}`,
                { headers: {} }
            );

            this.eventSource.onopen = () => {
                this.queueRetryCount = 0;
                console.log('✅ SSE queue connection established for:', facilityId, departmentId);
            };

            // 🔥 FIXED: Added explicit event type
            this.eventSource.addEventListener('queue-update', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data);
                    onUpdate(data);
                } catch (e) {
                    // Silent fail
                }
            });

            // 🔥 FIXED: Added explicit event type
            this.eventSource.addEventListener('emergency-alert', (event: MessageEvent) => {
                try {
                    const data = JSON.parse(event.data);
                    onEmergency(data);
                } catch (e) {
                    // Silent fail
                }
            });

            // 🔥 FIXED: Added explicit event type
            this.eventSource.addEventListener('error', (event: Event) => {
                console.warn('SSE queue error:', event);
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    if (this.queueRetryCount >= MAX_RETRIES) {
                        console.error('SSE: max retries reached for queue connection');
                        return;
                    }
                    this.queueRetryCount += 1;
                    const delay = BASE_RETRY_DELAY_MS * this.queueRetryCount;
                    console.log(`SSE: retrying queue connection in ${delay}ms (attempt ${this.queueRetryCount})`);

                    if (this.reconnectTimeout) {
                        clearTimeout(this.reconnectTimeout);
                    }

                    this.reconnectTimeout = setTimeout(() => {
                        this.connectToQueue(facilityId, departmentId, onUpdate, onEmergency);
                    }, delay);
                }
            });

        } catch (error) {
            console.error('SSE queue connection error:', error);
        }

        // Return an unsubscribe function
        return () => {
            this.disconnect();
        };
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get the access token from localStorage
     */
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

    // ==================== DISCONNECT ====================

    /**
     * Disconnect from SSE and clean up all resources
     */
    disconnect(): void {
        // Clear any pending reconnect timeout
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

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

// ==================== EXPORT ====================

export const sseService = new SSEService();