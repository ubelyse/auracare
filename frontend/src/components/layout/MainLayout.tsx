import React, { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { useTheme } from '../../context/ThemeContext';
import '../../styles/tokens.css';

// ===== SIDEBAR_WIDTH constant =====
const SIDEBAR_WIDTH = '15rem';
const HEADER_HEIGHT = '4rem';

interface MainLayoutProps {
    children: ReactNode;
    title?: string;
}

export const MainLayout: React.FC<MainLayoutProps> = ({ children, title }) => {
    const { themeClass } = useTheme();

    return (
        <div className={themeClass} style={{ background: 'var(--bg)', minHeight: '100vh' }}>
            <div className="min-h-screen">
                <Sidebar />
                <Header title={title} />
                <main
                    className="min-h-screen"
                    style={{
                        marginLeft: SIDEBAR_WIDTH,
                        paddingTop: HEADER_HEIGHT,
                    }}
                >
                    <div className="max-w-6xl mx-auto p-6">{children}</div>
                </main>
            </div>
        </div>
    );
};