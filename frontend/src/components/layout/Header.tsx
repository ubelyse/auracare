import React from 'react';
import { useAuthStore } from '../../stores/authStore';
import '../../styles/tokens.css';

interface HeaderProps {
    title?: string;
}

export const Header: React.FC<HeaderProps> = ({ title }) => {
    const { user } = useAuthStore();

    return (
        <header
            className="h-16 fixed right-0 top-0 flex items-center justify-between px-6 z-10"
            style={{
                width: 'calc(100% - 15rem)',
                background: 'var(--surface)',
                borderBottom: '1px solid var(--border)',
            }}
        >
            <h1 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                {title || 'Dashboard'}
            </h1>

            <div className="flex items-center gap-4">
                <div
                    className="flex items-center gap-2 px-3 py-1 rounded-full text-xs font-medium"
                    style={{ background: 'var(--success-tint)', color: 'var(--success)' }}
                >
                    <span className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: 'var(--success)' }} />
                    Live
                </div>

                <div className="flex items-center gap-3">
                    <span className="text-sm hidden md:block" style={{ color: 'var(--text-secondary)' }}>
                        {user?.firstName} {user?.lastName}
                    </span>
                    <div
                        className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold text-white"
                        style={{ background: 'var(--accent)' }}
                    >
                        {user?.firstName?.charAt(0)}
                        {user?.lastName?.charAt(0)}
                    </div>
                </div>
            </div>
        </header>
    );
};