// src/types/event-source-polyfill.d.ts

declare module 'event-source-polyfill' {
    export class EventSourcePolyfill {
        constructor(url: string, options?: any);
        onopen: ((event: Event) => void) | null;
        onerror: ((event: Event) => void) | null;
        onmessage: ((event: MessageEvent) => void) | null;
        addEventListener(type: string, listener: (event: any) => void): void;
        close(): void;
        readyState: number;
        static CLOSED: number;
        static CONNECTING: number;
        static OPEN: number;
    }
}